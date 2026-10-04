#!/usr/bin/env bash

# Recreate the Gradle wrapper when this source checkout does not include it.
# This script makes no system-wide changes and does not require sudo.
set -euo pipefail

readonly PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if ! command -v gradle >/dev/null 2>&1; then
    printf 'Gradle is unavailable. Install Gradle 8.5+ and run this script again.\n' >&2
    exit 1
fi

cd "${PROJECT_ROOT}"
gradle wrapper --gradle-version 8.5 --no-daemon
chmod +x gradlew
printf 'Gradle wrapper ready. Build with ./build-all.sh.\n'
