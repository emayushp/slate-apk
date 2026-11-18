#!/usr/bin/env bash
# Slate setup for Fire TV (Mac/Linux). See setup-firetv.bat for the Fire TV steps.
set -u
read -rp "Fire TV IP address: " IP
T="$IP:5555"
adb connect "$T"
read -rp "Accept the 'Allow USB debugging?' prompt on the TV, then press Enter… " _
[ -f slate.apk ] && adb -s "$T" install -r slate.apk
adb -s "$T" shell settings put secure enabled_accessibility_services com.ayush.slate/com.ayush.slate.HomeWatcher
adb -s "$T" shell settings put secure accessibility_enabled 1
read -rp "Apply privacy tweaks? [y/N] " P
if [[ "$P" =~ ^[Yy]$ ]]; then
  adb -s "$T" shell settings put secure limit_ad_tracking 1
  for p in com.amazon.device.crashmanager com.amazon.client.metrics com.amazon.client.metrics.api com.amazon.tv.ads; do
    echo " - disabling $p"; adb -s "$T" shell pm disable-user --user 0 "$p" || true
  done
fi
adb -s "$T" shell am start -n com.ayush.slate/.HomeActivity
echo "Done. Press Home on the remote."
