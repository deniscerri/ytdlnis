#!/usr/bin/env bash
# lib/version.sh — version read/bump/write helpers for app/build.gradle.
# Source this file; do not execute directly.
#
# YTDLnis declares its version as five plain Groovy locals at the top of
# app/build.gradle. This library is the single place that knows their names, so
# the release console and the cloud pipeline can never disagree about what the
# current version is or how the next one is spelled.
#
#   def versionMajor = 1
#   def versionMinor = 8
#   def versionPatch = 9
#   def versionBuild = 2      // 0 for a plain x.y.z release
#   def isBeta = false

# ── Patterns ────────────────────────────────────────────────────────────────────
_MAJOR_RE='^def[[:space:]]+versionMajor[[:space:]]*='
_MINOR_RE='^def[[:space:]]+versionMinor[[:space:]]*='
_PATCH_RE='^def[[:space:]]+versionPatch[[:space:]]*='
_BUILD_RE='^def[[:space:]]+versionBuild[[:space:]]*='
_BETA_RE='^def[[:space:]]+isBeta[[:space:]]*='

# _field <file> <regex> -> prints the value on the right of the '='
# Trailing `// comments` are stripped, so `def versionBuild = 2 // bump for…`
# still reads as 2.
_field() {
    grep -E "$2" "$1" | head -1 | sed -E 's/^[^=]*=[[:space:]]*//; s|[[:space:]]*//.*$||; s/[[:space:]]*$//'
}

# version::read <gradle_file>
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_BUILD, V_BETA ("true"/"false")
version::read() {
    local file="$1"
    [[ -f "$file" ]] || { echo "✗ version::read — no such file: ${file}" >&2; exit 1; }

    V_MAJOR="$(_field "$file" "$_MAJOR_RE")"
    V_MINOR="$(_field "$file" "$_MINOR_RE")"
    V_PATCH="$(_field "$file" "$_PATCH_RE")"
    V_BUILD="$(_field "$file" "$_BUILD_RE")"
    V_BETA="$(_field "$file" "$_BETA_RE")"
    V_BUILD="${V_BUILD:-0}"
    V_BETA="${V_BETA:-false}"

    # Fail loudly rather than proceed with empty numbers that would silently
    # produce a nonsense version.
    if [[ ! "$V_MAJOR$V_MINOR$V_PATCH$V_BUILD" =~ ^[0-9]+$ ]]; then
        echo "✗ version::read — failed to parse the version block in ${file}" >&2
        echo "  major='${V_MAJOR}' minor='${V_MINOR}' patch='${V_PATCH}' build='${V_BUILD}'" >&2
        exit 1
    fi
}

# version::name <major> <minor> <patch> <build> <beta>
# Prints the version name exactly as app/build.gradle composes it.
version::name() {
    local major="$1" minor="$2" patch="$3" build="${4:-0}" beta="${5:-false}"
    local name="${major}.${minor}.${patch}"
    [[ "$build" -gt 0 ]] && name+=".${build}"
    [[ "$beta" == "true" ]] && name+="-beta"
    echo "$name"
}

# version::code <major> <minor> <patch> <build>
# Prints defaultConfig.versionCode — the base code, before AGP's per-ABI offset.
# This is what version.json publishes and what BuildConfig.BASE_VERSION_CODE
# holds, so the app compares like for like on every ABI.
version::code() {
    echo $(( $1 * 1000000 + $2 * 10000 + $3 * 100 + ${4:-0} ))
}

# version::bump <kind> <beta>
# kind: major | minor | patch | build (build = another iteration of the same x.y.z)
# Sets: V_MAJOR, V_MINOR, V_PATCH, V_BUILD in place.
#
# A beta keeps the version of the stable it precedes, so users can move back to
# stable without a downgrade — hence a beta only ever takes a `build` bump.
version::bump() {
    local kind="$1"
    case "$kind" in
        major) V_MAJOR=$(( V_MAJOR + 1 )); V_MINOR=0; V_PATCH=0; V_BUILD=0 ;;
        minor) V_MINOR=$(( V_MINOR + 1 )); V_PATCH=0; V_BUILD=0             ;;
        patch) V_PATCH=$(( V_PATCH + 1 )); V_BUILD=0                        ;;
        build) V_BUILD=$(( V_BUILD + 1 ))                                   ;;
        *)     echo "✗ version::bump — unknown bump kind: ${kind}" >&2; exit 1 ;;
    esac
}

# version::write <gradle_file> <major> <minor> <patch> <build> <beta>
# Rewrites the five declarations in place, leaving their trailing comments alone.
# Values reach perl through the environment, never through interpolation into
# the program text.
version::write() {
    local file="$1"
    V_W_MAJOR="$2" V_W_MINOR="$3" V_W_PATCH="$4" V_W_BUILD="${5:-0}" V_W_BETA="${6:-false}" \
    perl -i -pe '
        s/^(def\s+versionMajor\s*=\s*)\S+/$1$ENV{V_W_MAJOR}/;
        s/^(def\s+versionMinor\s*=\s*)\S+/$1$ENV{V_W_MINOR}/;
        s/^(def\s+versionPatch\s*=\s*)\S+/$1$ENV{V_W_PATCH}/;
        s/^(def\s+versionBuild\s*=\s*)\S+/$1$ENV{V_W_BUILD}/;
        s/^(def\s+isBeta\s*=\s*)\S+/$1$ENV{V_W_BETA}/;
    ' "$file" || { echo "✗ version::write — perl substitution failed on ${file}" >&2; exit 1; }
}
