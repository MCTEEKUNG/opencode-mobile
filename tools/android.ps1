param(
    [switch]$Install,
    [string]$Serial,
    [string]$Jdk = 'C:/Program Files/Java/jdk-17',
    [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk"
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
if (!(Test-Path "$Jdk/bin/java.exe")) { throw 'Pass -Jdk with the path to JDK 17.' }
if (!(Test-Path "$Sdk/platform-tools/adb.exe")) { throw 'Pass -Sdk with the Android SDK path.' }
$previousJava = $env:JAVA_HOME
$previousSdk = $env:ANDROID_HOME
try {
    $env:JAVA_HOME = $Jdk
    $env:ANDROID_HOME = $Sdk
    Push-Location "$project/android"
    try {
        & ./gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Android verification/build failed.' }
        if ($Install) {
            $adb = "$Sdk/platform-tools/adb.exe"
            $deviceArgs = @()
            if ($Serial) { $deviceArgs = @('-s', $Serial) }
            & $adb @deviceArgs install -r app/build/outputs/apk/debug/app-debug.apk
            if ($LASTEXITCODE -ne 0) { throw 'APK installation failed. Check adb devices and USB debugging.' }
            & $adb @deviceArgs shell am start -W -n dev.opencode.mobile/.MainActivity
            if ($LASTEXITCODE -ne 0) { throw 'Activity launch failed.' }
        }
    } finally { Pop-Location }
} finally {
    $env:JAVA_HOME = $previousJava
    $env:ANDROID_HOME = $previousSdk
}
