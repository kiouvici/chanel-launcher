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
  adb shell am start -n $A --ei page 1 --ei demo 1; sleep 4
  adb exec-out screencap -p > shots/t${t}_1_media.png
  adb shell am start -n $A --ei page 1 --ei demo 2; sleep 3
  adb exec-out screencap -p > shots/t${t}_1m_media.png
  adb shell am start -n $A --ei page 1 --ei demo 3; sleep 3
  adb exec-out screencap -p > shots/t${t}_1l_media.png
  adb shell am start -n $A --ei page 1 --ei demo 4; sleep 3
  adb exec-out screencap -p > shots/t${t}_1h_media.png
  adb shell am start -n $A --ei page 0 --ez drawer true; sleep 3
  adb exec-out screencap -p > shots/t${t}_2_drawer.png
  adb shell am start -n $A --ei page 0 --ez drawer true --ei folder 3; sleep 4
  adb exec-out screencap -p > shots/t${t}_3_open.png
  adb shell input keyevent KEYCODE_BACK; sleep 1
done
adb logcat -d -s AndroidRuntime:E MNS:D > shots/crash.txt || true
ls -la shots
adb shell am start -n $A --ei theme 2 --ei page 0 --es sheet settings; sleep 4
adb exec-out screencap -p > shots/s_settings.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei page 0 --es sheet display; sleep 4
adb exec-out screencap -p > shots/s_display.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei theme 2 --ei page 1 --ei demo 2 --es sheet sound; sleep 4
adb exec-out screencap -p > shots/s_sound.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei page 0 --es sheet dial; sleep 4
adb exec-out screencap -p > shots/s_dial.png
adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -n $A --ei page 0 --es sheet collection; sleep 4
adb exec-out screencap -p > shots/s_collection.png
adb logcat -d -s AndroidRuntime:E MNS:D > shots/crash.txt || true
# 손동작 점검: 표지에서 짧게 위로 → 서랍 · 서랍 맨 위 빈자리에서 아래로 → 닫힘 · 표지 왼쪽/오른쪽 아래로 → 알림/빠른 설정
adb shell am start -n $A --ei theme 0 --ei page 0; sleep 6
adb shell input swipe 540 1900 540 1650 150; sleep 2
adb exec-out screencap -p > shots/g1_open_short.png
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1900 540 1650 150; sleep 2
adb exec-out screencap -p > shots/g1b_open_short.png
adb shell input keyevent KEYCODE_HOME; sleep 2
adb shell input swipe 540 1900 540 1300 300; sleep 2
adb exec-out screencap -p > shots/g1c_open_long.png
adb shell input swipe 540 330 540 1000 200; sleep 2
adb exec-out screencap -p > shots/g2_close_top.png
adb shell input swipe 250 900 250 1500 200; sleep 2
adb exec-out screencap -p > shots/g3_shade_left.png
adb shell cmd statusbar collapse; sleep 2
adb shell input swipe 850 900 850 1500 200; sleep 2
adb exec-out screencap -p > shots/g4_shade_right.png
adb shell cmd statusbar collapse; sleep 1
adb shell am start -n $A --ei page 2; sleep 6
adb shell input swipe 540 1200 540 1800 200; sleep 2
adb exec-out screencap -p > shots/g5_ha_mid.png
adb shell cmd statusbar collapse; sleep 1
# 다른 앱 갔다가 돌아오기 (시간 기록)
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
adb shell am start -a android.settings.SETTINGS; sleep 3
adb shell input keyevent KEYCODE_HOME; sleep 3
adb logcat -d -s AndroidRuntime:E MNS:D > shots/crash.txt || true
