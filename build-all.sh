#!/bin/bash

################################################################################
#                      REALMSHARK ↔ TOMATO BUILD SCRIPT                        #
#                     Unified compilation for both projects                    #
################################################################################

set -e  # Exit on error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'  # No Color

# Paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$SCRIPT_DIR"
REALMSHARK_DIR="$PROJECT_ROOT/RealmShark"
TOMATO_DIR="$PROJECT_ROOT/Tomato"
BUILD_LOG="$PROJECT_ROOT/build.log"

# Timing
START_TIME=$(date +%s)

################################################################################
#                              UTILITY FUNCTIONS                               #
################################################################################

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$BUILD_LOG"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $1" | tee -a "$BUILD_LOG"
}

log_error() {
    echo -e "${RED}[✗]${NC} $1" | tee -a "$BUILD_LOG"
}

log_warn() {
    echo -e "${YELLOW}[!]${NC} $1" | tee -a "$BUILD_LOG"
}

print_header() {
    echo "" | tee -a "$BUILD_LOG"
    echo "════════════════════════════════════════════════════════════════" | tee -a "$BUILD_LOG"
    echo "  $1" | tee -a "$BUILD_LOG"
    echo "════════════════════════════════════════════════════════════════" | tee -a "$BUILD_LOG"
    echo "" | tee -a "$BUILD_LOG"
}

check_gradle() {
    local dir="$1"
    local gradle_cmd

    gradle_cmd=$(get_gradle_cmd "$dir")
    if [ -z "$gradle_cmd" ]; then
        log_error "No Gradle command found for $dir"
        log_info "Expected executable gradlew in project, or 'gradle' in PATH"
        return 1
    fi

    if ! "$gradle_cmd" --version > /dev/null 2>&1; then
        log_error "Gradle command failed for $dir: $gradle_cmd"
        return 1
    fi

    return 0
}

get_gradle_cmd() {
    local dir="$1"

    if [ -x "$dir/gradlew" ]; then
        echo "$dir/gradlew"
        return
    fi

    if command -v gradle >/dev/null 2>&1; then
        echo "$(command -v gradle)"
        return
    fi

    echo ""
}

get_jar_name() {
    local dir="$1"
    ls "$dir"/build/libs/*.jar 2>/dev/null | head -1 | xargs basename
}

get_jar_size() {
    local file="$1"
    if [ -f "$file" ]; then
        ls -lh "$file" | awk '{print $5}'
    else
        echo "N/A"
    fi
}

################################################################################
#                              MAIN BUILD PROCESS                              #
################################################################################

main() {
    # Clear/create build log
    > "$BUILD_LOG"

    log_info "RealmShark ↔ Tomato Build System"
    log_info "Project Root: $PROJECT_ROOT"
    log_info "Start Time: $(date)"
    echo ""

    # Verify folder structure
    print_header "STEP 1: Verifying Folder Structure"
    
    if [ ! -d "$REALMSHARK_DIR" ]; then
        log_error "RealmShark folder not found at $REALMSHARK_DIR"
        return 1
    fi
    log_success "RealmShark folder found"

    if [ ! -d "$TOMATO_DIR" ]; then
        log_error "Tomato folder not found at $TOMATO_DIR"
        return 1
    fi
    log_success "Tomato folder found"

    # Check gradle setup
    print_header "STEP 2: Checking Gradle"
    
    if ! check_gradle "$REALMSHARK_DIR"; then
        return 1
    fi
    log_success "Gradle is available for RealmShark"

    if ! check_gradle "$TOMATO_DIR"; then
        return 1
    fi
    log_success "Gradle is available for Tomato"

    # Build RealmShark
    print_header "STEP 3: Building RealmShark (Backend)"
    
    log_info "Compiling RealmShark..."
    cd "$REALMSHARK_DIR"
    
    RS_GRADLE=$(get_gradle_cmd "$REALMSHARK_DIR")
    log_info "Using: $RS_GRADLE"
    
    START_RS=$(date +%s)
    if ! "$RS_GRADLE" clean shadowJar 2>&1 | tee -a "$BUILD_LOG"; then
        log_error "RealmShark build failed!"
        return 1
    fi
    END_RS=$(date +%s)
    RS_TIME=$((END_RS - START_RS))
    
    RS_JAR=$(get_jar_name "$REALMSHARK_DIR")
    RS_SIZE=$(get_jar_size "$REALMSHARK_DIR/build/libs/$RS_JAR")
    
    if [ -z "$RS_JAR" ]; then
        log_error "RealmShark JAR not found after build"
        return 1
    fi
    
    log_success "RealmShark build successful"
    log_info "JAR: $RS_JAR (Size: $RS_SIZE, Build Time: ${RS_TIME}s)"

    # Copy RealmShark jar to libs folder
    print_header "STEP 4: Setting Up Dependencies"
    
    log_info "Creating libs folder..."
    mkdir -p "$REALMSHARK_DIR/libs"
    mkdir -p "$TOMATO_DIR/libs"
    
    log_info "Copying $RS_JAR to both projects..."
    cp "$REALMSHARK_DIR/build/libs/$RS_JAR" "$REALMSHARK_DIR/libs/"
    cp "$REALMSHARK_DIR/build/libs/$RS_JAR" "$TOMATO_DIR/libs/"
    
    RS_LIB_SIZE=$(get_jar_size "$TOMATO_DIR/libs/$RS_JAR")
    log_success "Dependency JAR copied (Size: $RS_LIB_SIZE)"

    # Build Tomato
    print_header "STEP 5: Building Tomato (Frontend with UI Integration)"
    
    log_info "Compiling Tomato..."
    cd "$TOMATO_DIR"
    
    TOMATO_GRADLE=$(get_gradle_cmd "$TOMATO_DIR")
    log_info "Using: $TOMATO_GRADLE"
    
    START_TOM=$(date +%s)
    if ! "$TOMATO_GRADLE" clean shadowJar 2>&1 | tee -a "$BUILD_LOG"; then
        log_error "Tomato build failed!"
        return 1
    fi
    END_TOM=$(date +%s)
    TOM_TIME=$((END_TOM - START_TOM))
    
    TOMATO_JAR=$(get_jar_name "$TOMATO_DIR")
    TOMATO_SIZE=$(get_jar_size "$TOMATO_DIR/build/libs/$TOMATO_JAR")
    
    if [ -z "$TOMATO_JAR" ]; then
        log_error "Tomato JAR not found after build"
        return 1
    fi
    
    log_success "Tomato build successful"
    log_info "JAR: $TOMATO_JAR (Size: $TOMATO_SIZE, Build Time: ${TOM_TIME}s)"

    # Summary
    print_header "STEP 6: Build Summary"
    
    END_TIME=$(date +%s)
    TOTAL_TIME=$((END_TIME - START_TIME))
    
    echo "RealmShark:" | tee -a "$BUILD_LOG"
    echo "  ✓ JAR: $RS_JAR" | tee -a "$BUILD_LOG"
    echo "  ✓ Location: RealmShark/build/libs/" | tee -a "$BUILD_LOG"
    echo "  ✓ Size: $RS_SIZE" | tee -a "$BUILD_LOG"
    echo "  ✓ Build Time: ${RS_TIME}s" | tee -a "$BUILD_LOG"
    echo "" | tee -a "$BUILD_LOG"
    
    echo "Tomato:" | tee -a "$BUILD_LOG"
    echo "  ✓ JAR: $TOMATO_JAR" | tee -a "$BUILD_LOG"
    echo "  ✓ Location: Tomato/build/libs/" | tee -a "$BUILD_LOG"
    echo "  ✓ Size: $TOMATO_SIZE" | tee -a "$BUILD_LOG"
    echo "  ✓ Build Time: ${TOM_TIME}s" | tee -a "$BUILD_LOG"
    echo "" | tee -a "$BUILD_LOG"
    
    echo "Dependencies:" | tee -a "$BUILD_LOG"
    echo "  ✓ RealmShark JAR copied to: RealmShark/libs/$RS_JAR" | tee -a "$BUILD_LOG"
    echo "  ✓ RealmShark JAR copied to: Tomato/libs/$RS_JAR" | tee -a "$BUILD_LOG"
    echo "" | tee -a "$BUILD_LOG"
    
    print_header "BUILD COMPLETE"
    
    echo "Total Build Time: ${TOTAL_TIME}s" | tee -a "$BUILD_LOG"
    echo "Build Log: $BUILD_LOG" | tee -a "$BUILD_LOG"
    echo "" | tee -a "$BUILD_LOG"
    
    log_success "All builds completed successfully!"
    
    return 0
}

# Run main function
main
EXIT_CODE=$?

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    log_success "Build process finished successfully"
    echo ""
    echo "Next steps:"
    echo "  • Test RealmShark: java -jar RealmShark/build/libs/RealmShark-v*.jar"
    echo "  • Test Tomato: java -jar Tomato/build/libs/PPE-Sniffer-v*.jar"
    echo "  • Check build log: cat $BUILD_LOG"
else
    log_error "Build process failed. Check $BUILD_LOG for details."
fi

exit $EXIT_CODE
