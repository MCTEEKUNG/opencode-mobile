param([string]$Serial, [string]$Jdk = 'C:/Program Files/Java/jdk-17', [string]$Sdk = "$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
function Get-SourceStamp {
    $sources = @(Get-ChildItem "$project/android/app/src" -Recurse -File) + @(Get-ChildItem "$project/android" -File -Filter '*.kts') + @(Get-Item "$project/android/app/build.gradle.kts", "$project/android/gradle.properties")
    ($sources | Sort-Object FullName | ForEach-Object { "$($_.FullName):$($_.LastWriteTimeUtc.Ticks):$($_.Length)" }) -join '|'
}
Write-Output 'Android development mode: watching source files. Press Ctrl+C to stop. Keep USB debugging connected.'
$stamp = Get-SourceStamp
while ($true) {
    try {
        $buildArgs = @{ Install = $true; Jdk = $Jdk; Sdk = $Sdk }
        if ($Serial) { $buildArgs.Serial = $Serial }
        & "$PSScriptRoot/android.ps1" @buildArgs
    } catch { Write-Warning $_ }
    do {
        Start-Sleep -Seconds 2
        $next = Get-SourceStamp
    } while ($next -eq $stamp)
    $stamp = $next
    Write-Output 'Source changed; verifying and deploying Android app...'
}
