@echo off
setlocal

set "APK=%~dp0TheMusicPlayer.apk"
if not exist "%APK%" (
    echo APK not found: %APK%
    exit /b 1
)

set "ADB="
if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if defined ANDROID_SDK_ROOT if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB (
    for /f "tokens=*" %%i in ('where adb 2^>nul') do set "ADB=%%i"
)
if not defined ADB (
    echo adb not found. Install Android platform-tools or set ANDROID_HOME.
    exit /b 1
)

"%ADB%" get-state >nul 2>&1
if errorlevel 1 (
    echo No Android device or emulator connected. Enable USB debugging, connect your phone, then run this again.
    exit /b 1
)

echo Installing %APK% ...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo Install failed. If this is a signature mismatch with an older build of the app,
    echo uninstall the existing app first: adb uninstall com.themusicplayer.app
    exit /b 1
)

echo Done.
endlocal
