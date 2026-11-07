# Build the APK without Gradle.
# Needs: JDK 17 (javac/keytool), Android SDK build-tools (aapt2, d8, zipalign, apksigner)
#        and platform android.jar. A jar list is read from ../libs/*.jar when present.
param(
  [string]$JavaHome   = $env:JAVA_HOME,
  [string]$BuildTools = $env:ANDROID_BUILD_TOOLS,
  [string]$Platform   = $env:ANDROID_PLATFORM,
  [string]$Out        = "$PSScriptRoot\..\dist\aerial-buddy.apk"
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$app  = Join-Path $root 'app\src\main'
$work = Join-Path $root 'build'
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $work, (Split-Path $Out) | Out-Null

$javac = Join-Path $JavaHome 'bin\javac.exe'
$aapt2 = Join-Path $BuildTools 'aapt2.exe'
$d8    = Join-Path $BuildTools 'd8.bat'
$zipalign = Join-Path $BuildTools 'zipalign.exe'
$apksigner = Join-Path $BuildTools 'apksigner.bat'
foreach ($p in @($javac, $aapt2, $d8, $zipalign, $apksigner, $Platform)) {
  if (-not (Test-Path $p)) { throw "missing: $p" }
}

$libs = @()
$libDir = Join-Path $root '..\libs'
if (Test-Path $libDir) { $libs = Get-ChildItem $libDir -Filter *.jar | ForEach-Object FullName }

Write-Host '1/6 compiling + linking resources'
$resZip = Join-Path $work 'res.zip'
& $aapt2 compile --dir (Join-Path $app 'res') -o $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 compile failed' }
$base = Join-Path $work 'base.apk'
& $aapt2 link -o $base --manifest (Join-Path $app 'AndroidManifest.xml') -I $Platform `
      --java (Join-Path $work 'gen') --auto-add-overlay $resZip
if ($LASTEXITCODE -ne 0) { throw 'aapt2 link failed' }

Write-Host '2/6 compiling java'
$cp = $Platform
foreach ($l in $libs) { $cp = "$cp;$l" }
$src = Get-ChildItem (Join-Path $app 'java') -Recurse -Filter *.java | ForEach-Object FullName
$classes = Join-Path $work 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& $javac -encoding UTF-8 -source 8 -target 8 -nowarn -classpath $cp -d $classes @src
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }

Write-Host '3/6 dexing'
$dex = Join-Path $work 'dex'
New-Item -ItemType Directory -Force -Path $dex | Out-Null
$inputs = @(Get-ChildItem $classes -Recurse -Filter *.class | ForEach-Object FullName)
$inputs += $libs
& $d8 --lib $Platform --min-api 21 --output $dex @inputs
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }

Write-Host '4/6 packaging'
Copy-Item $base (Join-Path $work 'unsigned.apk') -Force
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [IO.Compression.ZipFile]::Open((Join-Path $work 'unsigned.apk'), 'Update')
[IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null
$zip.Dispose()

Write-Host '5/6 zipalign'
& $zipalign -f -p 4 (Join-Path $work 'unsigned.apk') (Join-Path $work 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }

Write-Host '6/6 signing'
$ks = Join-Path $root 'keystore.jks'
if (-not (Test-Path $ks)) {
  & (Join-Path $JavaHome 'bin\keytool.exe') -genkeypair -v -keystore $ks -storepass aerial123 `
      -keypass aerial123 -alias aerial -keyalg RSA -keysize 2048 -validity 10000 `
      -dname "CN=Aerial Buddy, O=Home, C=CN"
}
& $apksigner sign --ks $ks --ks-pass pass:aerial123 --key-pass pass:aerial123 `
    --out $Out (Join-Path $work 'aligned.apk')
& $apksigner verify --print-certs $Out | Select-Object -First 2

Write-Host "APK: $Out"
