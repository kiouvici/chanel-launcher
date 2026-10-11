#!/bin/bash
# 에뮬레이터에서 테마 5종 × (표지·미디어·서랍·열린 폴더) 화면을 찍음
set -x
APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
adb install -r "$APK"
adb shell cmd package set-home-activity com.kiouvici.monsieur/.MainActivity
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
mkdir -p shots
A=com.kiouvici.monsieur/.MainActivity
adb shell am start -n $A; sleep 8
for t in 0 1 2 3 4; do
  adb shell am start -n $A --ei theme $t --ei page 0; sleep 4
  adb exec-out screencap -p > shots/t${t}_0_cover.png
  adb shell am start -n $A --ei page 1; sleep 3
  adb exec-out screencap -p > shots/t${t}_1_media.png
  adb shell am start -n $A --ei page 0 --ez drawer true; sleep 3
  adb exec-out screencap -p > shots/t${t}_2_drawer.png
  adb shell am start -n $A --ei page 0 --ez drawer true --ei folder 3; sleep 4
  adb exec-out screencap -p > shots/t${t}_3_open.png
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
adb logcat -d -s AndroidRuntime:E > shots/crash.txt || true
ls -la shots
adb shell am start -n $A --ei theme 0 --ei page 0 --es sheet settings; sleep 4
adb exec-out screencap -p > shots/s_settings.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei page 0 --es sheet display; sleep 4
adb exec-out screencap -p > shots/s_display.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei page 0 --es sheet collection; sleep 4
adb exec-out screencap -p > shots/s_collection.png
adb logcat -d -s AndroidRuntime:E > shots/crash.txt || true
