@echo off
REM ===================================================================
REM  Slate setup for Fire TV (Windows)
REM  1. On the Fire TV: Settings > My Fire TV > Developer options
REM     turn ON "ADB debugging" and "Apps from Unknown Sources".
REM     (No Developer options? Settings > My Fire TV > About, press OK
REM      on the device name 7 times.)
REM  2. Find the Fire TV IP: Settings > My Fire TV > About > Network.
REM  3. Put adb.exe (Android platform-tools) and slate.apk next to this file.
REM  4. Double-click this file.
REM ===================================================================
setlocal
set /p IP=Fire TV IP address (e.g. 192.168.1.50):
adb connect %IP%:5555
echo.
echo If the TV shows "Allow USB debugging?", tick "Always allow" and press OK, then
pause

if exist slate.apk (
  echo Installing Slate...
  adb -s %IP%:5555 install -r slate.apk
)

echo Turning on the Home-button takeover...
adb -s %IP%:5555 shell settings put secure enabled_accessibility_services com.ayush.slate/com.ayush.slate.HomeWatcher
adb -s %IP%:5555 shell settings put secure accessibility_enabled 1

echo.
set /p PRIV=Also apply privacy tweaks (ad ID off, disable Amazon metrics/crash reporting)? [y/N]:
if /i "%PRIV%"=="y" call :privacy

echo Opening Slate...
adb -s %IP%:5555 shell am start -n com.ayush.slate/.HomeActivity
echo Done. Press Home on the remote to try it.
pause
exit /b

:privacy
adb -s %IP%:5555 shell settings put secure limit_ad_tracking 1
for %%P in (com.amazon.device.crashmanager com.amazon.client.metrics com.amazon.client.metrics.api com.amazon.tv.ads) do (
  echo  - disabling %%P
  adb -s %IP%:5555 shell pm disable-user --user 0 %%P
)
echo (Errors above just mean that package isn't on your Fire OS version or is protected. That's fine.)
exit /b
