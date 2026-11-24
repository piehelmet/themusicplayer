$ErrorActionPreference = 'Stop'

$project = Split-Path -Parent $MyInvocation.MyCommand.Path
$sdk     = 'C:\Users\justinkelly\AppData\Local\Android\Sdk'
$bt      = Join-Path $sdk 'build-tools\34.0.0'
$jar     = Join-Path $sdk 'platforms\android-35\android.jar'
$jdk     = 'C:\Program Files\Microsoft\jdk-17.0.21.102-hotspot'

$env:JAVA_HOME = $jdk
$env:PATH = "$jdk\bin;$bt;$env:PATH"

$out     = Join-Path $project 'build'
$classes = Join-Path $out 'classes'
$gen     = Join-Path $out 'gen'
$dex     = Join-Path $out 'dex'
foreach ($d in @($out, $classes, $gen, $dex)) {
    if (Test-Path $d) { Remove-Item $d -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $d | Out-Null
}

Write-Host '== 1/7 Compile resources =='
& (Join-Path $bt 'aapt2.exe') compile --dir (Join-Path $project 'res') -o (Join-Path $out 'res.flata')
if ($LASTEXITCODE) { throw 'aapt2 compile failed' }

Write-Host '== 2/7 Link resources / manifest =='
& (Join-Path $bt 'aapt2.exe') link `
    -o (Join-Path $out 'base.apk') `
    -I $jar `
    --manifest (Join-Path $project 'AndroidManifest.xml') `
    --java $gen `
    -R (Join-Path $out 'res.flata') `
    --min-sdk-version 21 --target-sdk-version 34 `
    --version-code 1 --version-name '1.0' --auto-add-overlay
if ($LASTEXITCODE) { throw 'aapt2 link failed' }

Write-Host '== 3/7 Compile Java =='
$sources = @(Get-ChildItem (Join-Path $project 'src') -Recurse -Filter '*.java' |
             ForEach-Object { $_.FullName })
$sources += (Get-ChildItem $gen -Recurse -Filter 'R.java' | ForEach-Object { $_.FullName })
& (Join-Path $jdk 'bin\javac.exe') -encoding UTF-8 -source 11 -target 11 `
    -classpath $jar -d $classes $sources
if ($LASTEXITCODE) { throw 'javac failed' }

Write-Host '== 4/7 Package classes and convert to DEX =='
& (Join-Path $jdk 'bin\jar.exe') cf (Join-Path $out 'classes.jar') -C $classes .
if ($LASTEXITCODE) { throw 'jar failed' }
& (Join-Path $bt 'd8.bat') --lib $jar --output $dex (Join-Path $out 'classes.jar')
if ($LASTEXITCODE) { throw 'd8 failed' }
Copy-Item (Join-Path $dex 'classes.dex') (Join-Path $out 'classes.dex') -Force

Write-Host '== 5/7 Add DEX to APK =='
Push-Location $out
& (Join-Path $jdk 'bin\jar.exe') uf 'base.apk' 'classes.dex'
Pop-Location
if ($LASTEXITCODE) { throw 'jar update failed' }

Write-Host '== 6/7 Zipalign =='
& (Join-Path $bt 'zipalign.exe') -f 4 (Join-Path $out 'base.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE) { throw 'zipalign failed' }

Write-Host '== 7/7 Sign =='
$ks = 'C:\Users\justinkelly\.android\debug.keystore'
if (-not (Test-Path $ks)) {
    New-Item -ItemType Directory -Force -Path (Split-Path $ks) | Out-Null
    & (Join-Path $jdk 'bin\keytool.exe') -genkeypair -v `
        -keystore $ks -storepass android -keypass android `
        -alias androiddebugkey -dname 'CN=Android Debug,O=Android,C=US' `
        -keyalg RSA -keysize 2048 -validity 10950
}
& (Join-Path $bt 'apksigner.bat') sign `
    --ks $ks --ks-pass pass:android --key-pass pass:android `
    --out (Join-Path $project 'TheMusicPlayer.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE) { throw 'apksigner failed' }

& (Join-Path $bt 'apksigner.bat') verify --print-certs (Join-Path $project 'TheMusicPlayer.apk')
$apk = Get-Item (Join-Path $project 'TheMusicPlayer.apk')
Write-Host ""
Write-Host "Built: $($apk.FullName)"
Write-Host "Size:  $($apk.Length) bytes"
Write-Host "SHA256: $((Get-FileHash $apk.FullName -Algorithm SHA256).Hash)"
