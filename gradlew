#!/usr/bin/env sh

##############################################################################
# Gradle wrapper startup script (auto-downloads wrapper jar if missing)
##############################################################################

APP_HOME="$(cd "$(dirname "$0")" && pwd)"
WRAPPER_JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

# Auto-download wrapper jar if missing (CI-friendly)
if [ ! -f "$WRAPPER_JAR" ]; then
    echo "Downloading gradle-wrapper.jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -fsSL --retry 3 -o "$WRAPPER_JAR" \
        "https://raw.githubusercontent.com/gradle/gradle/v8.7.0/gradle/wrapper/gradle-wrapper.jar" 2>/dev/null
    if [ ! -f "$WRAPPER_JAR" ]; then
        curl -fsSL --retry 3 -o "$WRAPPER_JAR" \
            "https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar"
    fi
fi

if [ ! -f "$WRAPPER_JAR" ]; then
    echo "ERROR: Could not obtain gradle-wrapper.jar" >&2
    exit 1
fi

exec java -jar "$WRAPPER_JAR" "$@"
