#!/usr/bin/env bash
#
# release.sh — release console for YTDLnis.
#
# Picks the next version interactively, then hands the actual work to
# .github/workflows/release.yml. Nothing is built, signed or pushed locally:
# once the run is dispatched this script is a viewer, and closing the terminal
# (or losing power) has no effect on the release.
#
# Run from ANY branch, on ANY device.
#   1. Read the current version from origin/main — the authoritative copy
#   2. Prompt for release type, bump kind and notes
#   3. Preview + confirm, then dispatch the cloud pipeline
#   4. Stream the run (Ctrl-C is safe — it detaches, it does not cancel)
#
# Usage:
#   ./scripts/release.sh
#
# Requirements: gh (authenticated) and jq. No keystore, no Android SDK.

set -euo pipefail

# ── Bootstrap ───────────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$ROOT"

source "${SCRIPT_DIR}/lib/version.sh"
source "${SCRIPT_DIR}/lib/git.sh"

GRADLE_FILE="app/build.gradle"
MAIN_BRANCH="main"
WORKFLOW="release.yml"

# ── Preconditions ───────────────────────────────────────────────────────────────
for cmd in gh jq; do
    command -v "$cmd" >/dev/null || { echo "✗ Required tool not found: ${cmd}"; exit 1; }
done
gh auth status >/dev/null 2>&1 || { echo "✗ gh not authenticated — run: gh auth login"; exit 1; }

REPO="$(git::repo_slug)"
SOURCE_BRANCH="$(git::current_branch)"

# The pipeline builds from origin, so local-only work would silently be left out.
git::ensure_clean
git::ensure_pushed "$SOURCE_BRANCH"

# ── Step 1: Read current version from origin/main ───────────────────────────────
git fetch origin "$MAIN_BRANCH" --quiet
MAIN_GRADLE="$(mktemp)"
trap 'rm -f "$MAIN_GRADLE"' EXIT
git show "origin/${MAIN_BRANCH}:${GRADLE_FILE}" > "$MAIN_GRADLE"

version::read "$MAIN_GRADLE"
CURRENT_NAME="$(version::name "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD" "$V_BETA")"
CURRENT_KIND="stable"; [[ "$V_BETA" == "true" ]] && CURRENT_KIND="beta"

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│           YTDLnis Release Pipeline           │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Current version : ${CURRENT_NAME}  (${CURRENT_KIND})"
echo "  Source branch   : ${SOURCE_BRANCH}"
echo "  Target branch   : ${MAIN_BRANCH}"
echo "  Runs on         : GitHub Actions"
echo ""

# ── Step 2: Choose release type ─────────────────────────────────────────────────
echo "  Release type:"
echo "    1) Stable  (e.g. 1.9.0)"
echo "    2) Beta    (e.g. 1.9.0.1-beta)"
echo ""
read -rp "  Select [1-2, default 1]: " TYPE_CHOICE
TYPE_CHOICE="${TYPE_CHOICE:-1}"

case "$TYPE_CHOICE" in
    1) RELEASE_TYPE="Stable" ;;
    2) RELEASE_TYPE="Beta"   ;;
    *) echo "✗ Invalid choice"; exit 1 ;;
esac

# ── Step 3: Choose bump kind ────────────────────────────────────────────────────
# A beta carries the version of the stable it precedes, so users can move to
# stable without a downgrade — which leaves `build` as its only bump.
echo ""
if [[ "$RELEASE_TYPE" == "Beta" ]]; then
    BUMP_KIND="build"
    echo "  Version bump: build — betas keep the stable version they precede."
else
    echo "  Version bump:"
    echo "    1) patch  — bug fixes        (x.y.Z)"
    echo "    2) minor  — new features     (x.Y.0)"
    echo "    3) major  — breaking changes (X.0.0)"
    echo "    4) build  — another iteration of the same x.y.z"
    echo ""
    read -rp "  Select [1-4, default 1]: " BUMP_CHOICE
    BUMP_CHOICE="${BUMP_CHOICE:-1}"
    case "$BUMP_CHOICE" in
        1) BUMP_KIND="patch" ;;
        2) BUMP_KIND="minor" ;;
        3) BUMP_KIND="major" ;;
        4) BUMP_KIND="build" ;;
        *) echo "✗ Invalid choice"; exit 1 ;;
    esac
fi

NEW_BETA="false"; [[ "$RELEASE_TYPE" == "Beta" ]] && NEW_BETA="true"
version::bump "$BUMP_KIND"
NEW_NAME="$(version::name "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD" "$NEW_BETA")"
NEW_CODE="$(version::code "$V_MAJOR" "$V_MINOR" "$V_PATCH" "$V_BUILD")"
TAG="v${NEW_NAME}"

# ── Step 4: Release notes ────────────────────────────────────────────────────────
echo ""
read -rp "  Release notes [Bug fixes and improvements.]: " NOTES
NOTES="${NOTES:-Bug fixes and improvements.}"

# ── Step 5: Preview + confirm ────────────────────────────────────────────────────
echo ""
echo "  ┌── Release Preview ────────────────────────────┐"
echo "  │  ${CURRENT_NAME}  →  ${NEW_NAME}"
echo "  │  Tag    : ${TAG}"
echo "  │  Code   : ${NEW_CODE}"
echo "  │  Type   : ${RELEASE_TYPE}"
echo "  │  Notes  : ${NOTES}"
echo "  │  Repo   : ${REPO}"
echo "  └───────────────────────────────────────────────┘"
echo ""

if git::release_published "$TAG"; then
    echo "✗ Release ${TAG} is already published — bump to a different version."; exit 1
fi

read -rp "  Proceed? [y/N]: " CONFIRM
[[ "${CONFIRM,,}" == "y" ]] || { echo "  Aborted."; exit 0; }

# ── Step 6: Dispatch the cloud pipeline ──────────────────────────────────────────
# Remember the newest run id first, so we can identify the one we just created —
# `gh workflow run` does not return it.
echo ""
echo "▶ Dispatching ${WORKFLOW}…"
PREV_RUN="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"

# --ref picks which branch's *workflow definition* runs, not which code is built:
# the pipeline always checks out main and merges the source branch itself. Using
# the source branch keeps "the workflow I can see on my branch is the workflow
# that runs" true — with --ref main, pipeline edits made on dev could never take
# effect until a release had already shipped them.
gh workflow run "$WORKFLOW" \
    --ref "$SOURCE_BRANCH" \
    -f release_type="$RELEASE_TYPE" \
    -f bump="$BUMP_KIND" \
    -f notes="$NOTES" \
    -f source_branch="$SOURCE_BRANCH"

RUN_ID=""
for _ in $(seq 1 20); do
    sleep 2
    RUN_ID="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId // 0')"
    [[ "$RUN_ID" != "$PREV_RUN" && "$RUN_ID" != "0" ]] && break
    RUN_ID=""
done

if [[ -z "$RUN_ID" ]]; then
    echo "  Dispatched, but the run id did not appear in time."
    echo "  Follow it at: https://github.com/${REPO}/actions/workflows/${WORKFLOW}"
    exit 0
fi

echo ""
echo "┌─────────────────────────────────────────────┐"
echo "│        ✓ Release running in the cloud        │"
echo "└─────────────────────────────────────────────┘"
echo ""
echo "  Version : ${NEW_NAME}"
echo "  Run     : https://github.com/${REPO}/actions/runs/${RUN_ID}"
echo ""
echo "  Streaming below — Ctrl-C only detaches this terminal,"
echo "  the release finishes on GitHub either way."
echo ""

gh run watch "$RUN_ID" --exit-status || true
