@echo off
REM Re-enables everything the privacy step turned off, and switches off the Home takeover.
set /p IP=Fire TV IP address:
adb connect %IP%:5555
for %%P in (com.amazon.device.crashmanager com.amazon.client.metrics com.amazon.client.metrics.api com.amazon.tv.ads) do adb -s %IP%:5555 shell pm enable %%P
adb -s %IP%:5555 shell settings put secure limit_ad_tracking 0
adb -s %IP%:5555 shell settings delete secure enabled_accessibility_services
echo Restored.
pause
