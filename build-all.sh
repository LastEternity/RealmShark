#!/usr/bin/env bash

# Build the consolidated PPE-Tomato project introduced by upstream PR #78.
# The packet-sniffer library is intentionally an external dependency.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly DEPENDENCY_JAR="${PROJECT_ROOT}/libs/RealmShark-v1.2.3.jar"
readonly OUTPUT_JAR="${PROJECT_ROOT}/build/libs/PPE-Tomato-v1.0.3.jar"
readonly REALMSHARK_REPOSITORY="${REALMSHARK_REPOSITORY:-https://github.com/X-com/RealmShark.git}"
readonly REALMSHARK_REF="${REALMSHARK_REF:-realmshark}"

log() {
    printf '[build] %s\n' "$*"
}

fail() {
    printf '[build] error: %s\n' "$*" >&2
    exit 1
}

select_gradle() {
    if [[ -x "${PROJECT_ROOT}/gradlew" && -f "${PROJECT_ROOT}/gradle/wrapper/gradle-wrapper.jar" ]]; then
        GRADLE_COMMAND=("${PROJECT_ROOT}/gradlew")
    elif command -v gradle >/dev/null 2>&1; then
        GRADLE_COMMAND=("$(command -v gradle)")
    else
        fail "Gradle is unavailable. Run ./setup-gradle.sh or install Gradle 8.5+."
    fi
}

build_realmshark_dependency() (
    command -v git >/dev/null 2>&1 || fail "Git is required to bootstrap the RealmShark dependency."

    local temporary_directory
    temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/ppe-realmshark.XXXXXX")"
    trap 'rm -rf -- "${temporary_directory}"' EXIT

    log "Bootstrapping RealmShark v1.2.3 from ${REALMSHARK_REPOSITORY} (${REALMSHARK_REF})"
    git clone --quiet --depth 1 --branch "${REALMSHARK_REF}" "${REALMSHARK_REPOSITORY}" "${temporary_directory}/source"
    "${GRADLE_COMMAND[@]}" -p "${temporary_directory}/source" clean shadowJar --no-daemon

    local built_jar="${temporary_directory}/source/build/libs/RealmShark-v1.2.3.jar"
    [[ -f "${built_jar}" ]] || fail "RealmShark build did not create ${built_jar}."

    mkdir -p "$(dirname "${DEPENDENCY_JAR}")"
    cp "${built_jar}" "${DEPENDENCY_JAR}"
    log "Installed dependency at ${DEPENDENCY_JAR}"
)

main() {
    cd "${PROJECT_ROOT}"
    select_gradle

    [[ -f "${DEPENDENCY_JAR}" ]] || build_realmshark_dependency

    log "Building PPE-Tomato v1.0.3 with ${GRADLE_COMMAND[*]}"
    "${GRADLE_COMMAND[@]}" clean shadowJar --no-daemon

    [[ -f "${OUTPUT_JAR}" ]] || fail "Expected output was not created: ${OUTPUT_JAR}"
    log "Created ${OUTPUT_JAR}"
}

main "$@"
