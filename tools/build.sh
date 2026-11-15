#!/usr/bin/env bash
# Runs Gradle on this PC (Git Bash): ./tools/build.sh assembleRelease
#
# Uses the JBR that ships with Android Studio as the JDK. When the user folder name contains non-ASCII letters, Java
# cannot open its local socket files in the temp folder and Gradle fails with "Unable to establish loopback
# connection". The sockets are therefore redirected to the project's ASCII-named .tmp folder.
#
# After the build, a freshly built release package is checked once more: It must ask for exactly the two allowed
# permissions. This does not depend on the Gradle task wiring of ManifestCheck, so a change in AGP's task names cannot
# let a package with other permissions slip through.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [ -z "${JAVA_HOME:-}" ]; then
    export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
fi
mkdir -p "$ROOT/.tmp"
export JAVA_TOOL_OPTIONS="-Djdk.net.unixdomain.tmpdir=$(cygpath -m "$ROOT/.tmp") ${JAVA_TOOL_OPTIONS:-}"
cd "$ROOT"
touch "$ROOT/.tmp/build-start"
./gradlew "$@"

APK="$ROOT/app/build/outputs/apk/release/app-release.apk"
if [ -f "$APK" ] && [ "$APK" -nt "$ROOT/.tmp/build-start" ]; then
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-C:/Android/sdk}}"
    AAPT2="$(ls "$SDK"/build-tools/*/aapt2* 2>/dev/null | sort -V | tail -1)"
    if [ -z "$AAPT2" ]; then
        echo "Permission check skipped: aapt2 not found under $SDK/build-tools." >&2
        exit 1
    fi
    FOUND="$("$AAPT2" dump permissions "$APK" | grep -v '^package:' | tr -d '\r' | sort)"
    EXPECTED="uses-permission: name='android.permission.POST_NOTIFICATIONS'
uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED'"
    if [ "$FOUND" != "$EXPECTED" ]; then
        echo "Permission check failed for $APK. Found:" >&2
        echo "$FOUND" >&2
        exit 1
    fi
    echo "Permission check passed: The release package asks only for POST_NOTIFICATIONS and RECEIVE_BOOT_COMPLETED."
fi
