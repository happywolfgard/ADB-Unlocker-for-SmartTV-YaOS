$ErrorActionPreference = "Stop"

$root = "c:\Users\happy\Desktop\DevelopV2\Android"
$appDir = "$root\AdbEnabler\app"
$buildDir = "$root\AdbEnabler\build"

# Tools
$jdk = Get-ChildItem "C:\Program Files\Microsoft\jdk-17*" | Select-Object -First 1 -ExpandProperty FullName
$javac = "$jdk\bin\javac.exe"
$keytool = "$jdk\bin\keytool.exe"
$aapt2 = "C:\Users\happy\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe"
$d8 = "C:\Users\happy\AppData\Local\Android\Sdk\build-tools\36.0.0\d8.bat"
$zipalign = "C:\Users\happy\AppData\Local\Android\Sdk\build-tools\36.0.0\zipalign.exe"
$apksigner = "C:\Users\happy\AppData\Local\Android\Sdk\build-tools\36.0.0\apksigner.bat"
$androidJar = "C:\Users\happy\AppData\Local\Android\Sdk\platforms\android-37.0\android.jar"

$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;" + $env:Path

Write-Host "=== 1. Cleaning & Creating build directories ==="
if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Path "$buildDir\compiled_res" -Force | Out-Null
New-Item -ItemType Directory -Path "$buildDir\gen" -Force | Out-Null
New-Item -ItemType Directory -Path "$buildDir\obj" -Force | Out-Null
New-Item -ItemType Directory -Path "$buildDir\dex" -Force | Out-Null

Write-Host "=== 2. Compiling resources with aapt2 ==="
& $aapt2 compile --dir "$appDir\src\main\res" -o "$buildDir\compiled_res"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "=== 3. Linking resources with aapt2 ==="
$compiledResFiles = Get-ChildItem "$buildDir\compiled_res\*.flat" | ForEach-Object { $_.FullName }
& $aapt2 link -I $androidJar `
    --manifest "$appDir\src\main\AndroidManifest.xml" `
    --java "$buildDir\gen" `
    -o "$buildDir\app-unaligned.apk" `
    $compiledResFiles `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "=== 4. Compiling Java sources with javac ==="
$javaSources = @(
    "$appDir\src\main\java\com\adbenabler\tv\MainActivity.java",
    "$buildDir\gen\com\adbenabler\tv\R.java"
)
& $javac -encoding UTF-8 -cp $androidJar -d "$buildDir\obj" -source 1.8 -target 1.8 $javaSources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "=== 5. Converting bytecode to DEX with d8 ==="
$classFiles = Get-ChildItem "$buildDir\obj" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& $d8 --output "$buildDir\dex" --lib $androidJar $classFiles
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "=== 6. Packaging classes.dex into APK ==="
Copy-Item "$buildDir\app-unaligned.apk" "$buildDir\app-with-dex.apk"
# Use tar or powershell Compress-Archive / aapt or zip
# Python can add classes.dex into zip cleanly without external zip tool:
$pyScript = @"
import zipfile
with zipfile.ZipFile(r'$buildDir\app-with-dex.apk', 'a', compression=zipfile.ZIP_DEFLATED) as z:
    z.write(r'$buildDir\dex\classes.dex', 'classes.dex')
print('classes.dex added to APK successfully')
"@
& "C:\Python314\python.exe" -c $pyScript

Write-Host "=== 7. Zipalign APK ==="
& $zipalign -f -v 4 "$buildDir\app-with-dex.apk" "$buildDir\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "=== 8. Signing APK ==="
$keystore = "$buildDir\debug.keystore"
if (-not (Test-Path $keystore)) {
    & $keytool -genkeypair -v -keystore $keystore -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass android -keypass android -dname "CN=Android Debug,O=Android,C=US"
}

$finalApk = "$root\AdbTvEnabler.apk"
& $apksigner sign --ks $keystore --ks-pass pass:android --key-pass pass:android --out $finalApk "$buildDir\app-aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "=== 9. Verifying signed APK ==="
& $apksigner verify -v $finalApk

Write-Host "SUCCESS! Final APK created at: $finalApk"
Get-Item $finalApk | Select-Object Name, Length, LastWriteTime
