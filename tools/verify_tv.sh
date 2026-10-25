#!/usr/bin/env bash
set -euo pipefail
mkdir -p dist/qa
adb shell wm size 1280x720
test_status=0
gradle --no-daemon connectedDebugAndroidTest -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true || test_status=$?
adb pull /sdcard/Android/data/com.b5solucoes.resultadostv/files/qa dist/qa/
adb logcat -d -b crash > dist/qa/crash-log.txt
exit "$test_status"
