#!/bin/bash
# 시험: MONSIEUR 폴더 열고 앱 누르기 · 길게 누르기
A=com.kiouvici.monsieur/.MainActivity
mkdir -p shots
adb shell settings put system screen_off_timeout 1800000
adb shell am start -n $A; sleep 8
adb shell am start -n $A --ei theme 0 --ei page 0; sleep 4
adb shell am start -n $A --ei page 0 --ez drawer true; sleep 3
adb exec-out screencap -p > shots/a_drawer.png
# 서랍에서 첫 폴더 누르기 (실제 손가락처럼)
adb shell input tap 140 620; sleep 3
adb exec-out screencap -p > shots/b_folder.png
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml shots/ui.xml >/dev/null 2>&1
# 폴더 안 첫 앱 자리 누르기
for xy in "250 900" "450 900" "650 900" "250 1150"; do
  adb shell input tap $xy; sleep 3
  adb shell dumpsys activity activities | grep -m1 "mResumedActivity\|topResumedActivity" >> shots/resumed.txt
  adb exec-out screencap -p > shots/c_after_$(echo $xy|tr ' ' _).png
  adb shell input keyevent KEYCODE_BACK; sleep 2
  adb shell am start -n $A --ei page 0 --ez drawer true; sleep 2
  adb shell input tap 140 620; sleep 3
done
# 길게 누르기
adb shell input swipe 250 900 250 900 1200; sleep 2
adb exec-out screencap -p > shots/d_long.png
adb logcat -d -s AndroidRuntime:E MNS:D > shots/crash.txt || true
