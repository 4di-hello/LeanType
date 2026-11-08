#!/bin/bash
# Only in this fork: runs the end-to-end tests (app/src/androidTest) on the started emulator and collects
# screenshots, crash reports and the log in e2e-out. Called by .github/workflows/e2e-emulator.yml.
set -u
out=e2e-out
mkdir -p "$out"
pkg=com.leanbitlab.leantype.offline.debug
app=$(find app/build/outputs/apk/offline/debug -name '*.apk' | head -1)
tests=$(find app/build/outputs/apk/androidTest/offline/debug -name '*.apk' | head -1)
echo "app: $app, tests: $tests"
adb install -r -g "$app" || exit 1
adb install -r -g "$tests" || exit 1
adb shell settings put secure show_ime_with_hard_keyboard 1
# a freshly booted emulator is busy for a while, and "isn't responding" dialogs would cover the screen
adb shell settings put global hide_error_dialogs 1
sleep 45
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null
# LeanType has no built-in dictionaries: the English one, as if added by the user
adb push app/src/main/assets/dicts/main_en-US.dict /data/local/tmp/main_en-US.dict >/dev/null
adb shell "cat /data/local/tmp/main_en-US.dict | run-as $pkg sh -c 'mkdir -p files/dicts/en-US && cat > files/dicts/en-US/main_user.dict && ls -l files/dicts/en-US'"
adb logcat -c
echo "--- input methods before the tests"
adb shell ime list -s
adb shell settings get secure default_input_method
adb shell am instrument -w ${pkg}.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrument.txt"
echo "--- input method state after the tests"
adb shell settings get secure default_input_method
adb shell dumpsys input_method | grep -E "mCurMethodId|mInputShown|mCurFocusedWindow|mCurClient|mVisibleBound|mCurToken" | head -20
adb shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 && adb pull /sdcard/window.xml "$out/window.xml" >/dev/null 2>&1
adb exec-out run-as $pkg sh -c 'cd files && tar -cf - e2e crash_report* 2>/dev/null' > "$out/files.tar" || true
adb logcat -d > "$out/logcat.txt"
grep -E "LeanTypeE2E|FATAL EXCEPTION|AndroidRuntime|PluginTrust|CorrectionStats|LearnedTypos|TextLearner|InputMethodManagerService|LatinIME" "$out/logcat.txt" > "$out/logcat-relevant.txt" || true
echo "--- relevant log"
grep -E "LeanTypeE2E|FATAL EXCEPTION|AndroidRuntime" "$out/logcat.txt" | head -60
if grep -q "^OK (" "$out/instrument.txt"; then
  echo "::notice title=e2e::$(grep '^OK (' "$out/instrument.txt")"
  exit 0
fi
echo "::error title=e2e::tests failed or did not run"
exit 1
