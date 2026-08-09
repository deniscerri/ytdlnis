#!/usr/bin/env bash
# lib/git.sh — git helpers for the release console.
# Source this file; do not execute directly.
#
# The branch mechanics (sync / merge / commit / fast-forward) live in
# .github/workflows/release.yml, where they run against a fresh checkout.
# What remains here is what the local console still needs: identify the repo,
# and refuse to dispatch a release that would miss local work.

# git::repo_slug -> prints "owner/repo" from the remote URL (supports https and ssh)
git::repo_slug() {
    git remote get-url origin \
        | sed -E 's|.*github\.com[:/]||; s|\.git$||'
}

# git::current_branch -> prints current branch name
git::current_branch() {
    git rev-parse --abbrev-ref HEAD
}

# git::ensure_clean
# Aborts if there are uncommitted changes (staged or unstaged).
git::ensure_clean() {
    if ! git diff --quiet || ! git diff --cached --quiet; then
        echo "✗ Working tree has uncommitted changes — stash or commit them first."
        exit 1
    fi
}

# git::ensure_pushed <branch>
# Aborts if the branch has commits that origin does not. The pipeline builds
# from origin, so unpushed commits would be silently absent from the release.
git::ensure_pushed() {
    local branch="$1"
    git fetch origin "$branch" --quiet 2>/dev/null || {
        echo "✗ Branch ${branch} does not exist on origin — push it first."
        exit 1
    }
    local ahead
    ahead="$(git rev-list --count "origin/${branch}..${branch}")"
    if [[ "$ahead" -gt 0 ]]; then
        echo "✗ ${branch} is ${ahead} commit(s) ahead of origin — push them first."
        exit 1
    fi
}

# git::release_published <tag>
# True only for a published release. A leftover draft from a failed run is
# reclaimed by the pipeline and must not block a retry.
git::release_published() {
    [[ "$(gh release view "$1" --json isDraft --jq .isDraft 2>/dev/null || echo absent)" == "false" ]]
}
