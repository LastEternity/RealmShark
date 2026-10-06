#!/usr/bin/env bash

# Build the consolidated PPE-Tomato project.
# The RealmShark v1.2.3 packet-sniffer library is vendored in src/main/java
# (assets, packets, realmshark, util), so no external repository is needed.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly OUTPUT_JAR="${PROJECT_ROOT}/build/libs/PPE-Tomato-v1.0.3.jar"

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

main() {
    cd "${PROJECT_ROOT}"
    select_gradle

    log "Building PPE-Tomato v1.0.3 with ${GRADLE_COMMAND[*]}"
    "${GRADLE_COMMAND[@]}" clean shadowJar --no-daemon

    [[ -f "${OUTPUT_JAR}" ]] || fail "Expected output was not created: ${OUTPUT_JAR}"
    log "Created ${OUTPUT_JAR}"
}

main "$@"
