#!/usr/bin/env sh

##############################################################################
# Gradle wrapper startup script
# Downloads gradle-wrapper.jar from the official Gradle distribution if missing
##############################################################################

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
GRADLE_VERSION="8.7"

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "gradle-wrapper.jar not found. Downloading from Gradle distribution..."
    mkdir -p "$APP_HOME/gradle/wrapper"

    GRADLE_ZIP="/tmp/gradle-${GRADLE_VERSION}-bin.zip"
    GRADLE_EXTRACT="/tmp/gradle-dist-${GRADLE_VERSION}"

    # Download official Gradle distribution
    curl -fsSL \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
        -o "$GRADLE_ZIP"

    # Extract just the wrapper jar
    mkdir -p "$GRADLE_EXTRACT"
    unzip -q -o "$GRADLE_ZIP" -d "$GRADLE_EXTRACT"

    # Find and copy the wrapper jar
    FOUND_JAR=$(find "$GRADLE_EXTRACT" -name "gradle-wrapper.jar" 2>/dev/null | head -1)
    if [ -n "$FOUND_JAR" ]; then
        cp "$FOUND_JAR" "$WRAPPER_JAR"
        echo "gradle-wrapper.jar installed successfully."
    else
        echo "ERROR: gradle-wrapper.jar not found in distribution." >&2
        exit 1
    fi
fi

exec java -jar "$WRAPPER_JAR" "$@"
