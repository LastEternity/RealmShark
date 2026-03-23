#!/bin/bash

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
REALMSHARK_DIR="$PROJECT_ROOT/RealmShark"
TOMATO_DIR="$PROJECT_ROOT/Tomato"

################################################################################
#                    SETUP GRADLE - Initialize build environment              #
#                     Run this once to set up gradle properly                  #
################################################################################

echo "Setting up Gradle build environment..."
echo ""

# Download gradle 8.5 if not available
GRADLE_VERSION="8.5"
GRADLE_HOME="$HOME/.gradle/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"

if [ ! -f "$GRADLE_BIN" ]; then
    echo "Downloading Gradle ${GRADLE_VERSION}..."
    mkdir -p "$GRADLE_HOME"
    
    # Download and extract
    cd /tmp
    curl -sL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o "gradle-${GRADLE_VERSION}-bin.zip"
    unzip -q "gradle-${GRADLE_VERSION}-bin.zip"
    mv "gradle-${GRADLE_VERSION}"/* "$GRADLE_HOME"
    rm -rf "gradle-${GRADLE_VERSION}" "gradle-${GRADLE_VERSION}-bin.zip"
    chmod +x "$GRADLE_BIN"
    echo "✅ Gradle installed to $GRADLE_HOME"
fi

# Optional: remove old system gradle if passwordless sudo is available
if command -v gradle >/dev/null 2>&1; then
    SYS_GRADLE_PATH="$(command -v gradle)"
    if [ "$SYS_GRADLE_PATH" = "/usr/bin/gradle" ]; then
        if sudo -n true >/dev/null 2>&1; then
            echo "Removing old system Gradle package..."
            sudo -n apt remove -y gradle >/dev/null 2>&1 || true
        else
            echo "⚠️ Could not remove /usr/bin/gradle automatically (sudo password required)."
            echo "   If you want, run manually: sudo apt remove gradle"
        fi
    fi
fi

# Make gradle 8.5 available as the default user-level gradle
mkdir -p "$HOME/.local/bin"
ln -sf "$GRADLE_BIN" "$HOME/.local/bin/gradle"

# Create wrapper scripts in each project
for DIR in "$REALMSHARK_DIR" "$TOMATO_DIR"; do
    if [ ! -d "$DIR" ]; then
        echo "⚠️ Skipping missing directory: $DIR"
        continue
    fi

    echo "Configuring $DIR..."
    mkdir -p "$DIR/gradle/wrapper"
    
    cat > "$DIR/gradle/wrapper/gradle-wrapper.properties" << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip
networkTimeout=10000
validateDistributionUrl=true
EOF
    
    # Create a simple wrapper script that always uses Gradle 8.5
    cat > "$DIR/gradlew" << 'GRADLEW_EOF'
#!/usr/bin/env bash

APP_NAME="Gradle"
APP_BASE_NAME=$(basename "$0")

# Find Java
if [ -z "$JAVA_HOME" ]; then
    JAVA_CMD="java"
else
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Use wrapper jar if available, otherwise download
GRADLE_WRAPPER_JAR="$(cd "$(dirname "$0")" && pwd)/gradle/wrapper/gradle-wrapper.jar"
GRADLE_HOME="${HOME}/.gradle/gradle-8.5"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"

if [ -f "$GRADLE_BIN" ]; then
    exec "$GRADLE_BIN" "$@"
else
    echo "Gradle not found. Run setup-gradle.sh first."
    exit 1
fi
GRADLEW_EOF
    
    chmod +x "$DIR/gradlew"
    echo "✅ Configured $DIR"
done

echo ""
echo "✅ Setup complete!"
echo ""
echo "Gradle command shim: $HOME/.local/bin/gradle"
echo "Tip: ensure \"$HOME/.local/bin\" is in PATH if you want plain 'gradle' to use 8.5."
echo ""
echo "You can now run:"
echo "  cd $PROJECT_ROOT"
echo "  ./build-all.sh"
