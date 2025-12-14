param(
  [string]$Version = '1.0.0',
  [ValidateSet('msi','exe','app-image','zip')]
  [string]$Type = 'msi',
  [string]$WinUpgradeUuid = '5831de84-f04c-4356-8a8b-2f5347d71dd1',
  [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

function Resolve-JavaHome {
  if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    return $env:JAVA_HOME
  }

  $jdkKey = 'HKLM:\SOFTWARE\JavaSoft\JDK'
  $jdkProps = Get-ItemProperty -Path $jdkKey -ErrorAction SilentlyContinue
  if ($jdkProps -and $jdkProps.CurrentVersion) {
    $versionKey = Join-Path $jdkKey $jdkProps.CurrentVersion
    $versionProps = Get-ItemProperty -Path $versionKey -ErrorAction SilentlyContinue
    if ($versionProps -and $versionProps.JavaHome -and (Test-Path (Join-Path $versionProps.JavaHome 'bin\java.exe'))) {
      return $versionProps.JavaHome
    }
  }

  $candidate = 'C:\Program Files\Java\jdk-21'
  if (Test-Path (Join-Path $candidate 'bin\java.exe')) {
    return $candidate
  }

  return $null
}

function Resolve-WixBin {
  $programFilesX86 = [Environment]::GetFolderPath('ProgramFilesX86')
  $programFiles = [Environment]::GetFolderPath('ProgramFiles')

  $candidates = @(
    (Join-Path $programFilesX86 'WiX Toolset v3.14\bin'),
    (Join-Path $programFilesX86 'WiX Toolset v3.11\bin'),
    (Join-Path $programFiles 'WiX Toolset v3.14\bin'),
    (Join-Path $programFiles 'WiX Toolset v3.11\bin'),
    'C:\Program Files (x86)\WiX Toolset v3.14\bin',
    'C:\Program Files (x86)\WiX Toolset v3.11\bin'
  ) | Where-Object { $_ -and (Test-Path $_) }

  foreach ($dir in $candidates) {
    if (Test-Path (Join-Path $dir 'candle.exe')) {
      return $dir
    }
  }

  return $null
}

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
  Write-Error 'JDK 21을 찾지 못했습니다. (빌드 머신에는 JDK 21 + jpackage 필요) JAVA_HOME 설정 또는 레지스트리(JavaSoft JDK) 등록을 확인해주세요.'
}

$env:JAVA_HOME = $javaHome
if (-not ($env:Path -split ';' | Where-Object { $_ -ieq "$javaHome\bin" })) {
  $env:Path = "$javaHome\bin;" + $env:Path
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host ("java: " + (where.exe java | Select-Object -First 1))
Write-Host ("jpackage: " + (where.exe jpackage | Select-Object -First 1))
Write-Host ("jlink: " + (where.exe jlink | Select-Object -First 1))

if (-not (Test-Path '.\mvnw.cmd')) {
  Write-Error 'mvnw.cmd가 없습니다. 프로젝트 루트에서 실행해주세요.'
}

# 1) Build jar (Spring Boot plugin will create an executable jar and keep the thin jar as *.jar.original)
$buildArgs = @('clean', 'package')
if ($SkipTests) {
  $buildArgs += '-DskipTests'
}
& .\mvnw.cmd @buildArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$artifactId = 'todoapp'
$artifactVersion = '1.0'
$targetDir = Join-Path $PSScriptRoot 'target'

$bootJar = Join-Path $targetDir "$artifactId-$artifactVersion.jar"
$thinJar = Join-Path $targetDir "$artifactId-$artifactVersion.jar.original"

if (-not (Test-Path $thinJar)) {
  Write-Error "thin jar를 찾지 못했습니다: $thinJar (spring-boot repackage 결과로 *.jar.original 이 있어야 합니다)"
}

# 2) Prepare jpackage input
$jpackRoot = Join-Path $targetDir 'jpackage'
$inputDir = Join-Path $jpackRoot 'input'
$runtimeDir = Join-Path $jpackRoot 'runtime'
$destDir = Join-Path $targetDir 'installer'

if (Test-Path $jpackRoot) { Remove-Item -Recurse -Force $jpackRoot }
if (Test-Path $destDir) { Remove-Item -Recurse -Force $destDir }

New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
New-Item -ItemType Directory -Force -Path $destDir | Out-Null

$appJarName = 'app.jar'
Copy-Item -Force $thinJar (Join-Path $inputDir $appJarName)

# Copy runtime deps to input/ (jpackage for classpath apps picks up jars from the input directory)
& .\mvnw.cmd -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory="$inputDir"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 3) Create a runtime image (simple/safe: include all JDK modules)
# NOTE: JavaFX is shipped as external jars (in input/lib), so it is NOT part of the runtime image.
if (Test-Path $runtimeDir) { Remove-Item -Recurse -Force $runtimeDir }
& jlink --add-modules ALL-MODULE-PATH --output "$runtimeDir" --strip-debug --no-man-pages --no-header-files
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# 4) Create MSI installer
$mainClass = 'com.example.todoapp.DesktopLauncher'
$appName = 'TodoApp'

if ($Type -in @('msi','exe')) {
  $candle = Get-Command 'candle.exe' -ErrorAction SilentlyContinue
  $light = Get-Command 'light.exe' -ErrorAction SilentlyContinue

  if (-not $candle -or -not $light) {
    $wixBin = Resolve-WixBin
    if ($wixBin -and -not ($env:Path -split ';' | Where-Object { $_ -ieq $wixBin })) {
      $env:Path = "$wixBin;" + $env:Path
      $candle = Get-Command 'candle.exe' -ErrorAction SilentlyContinue
      $light = Get-Command 'light.exe' -ErrorAction SilentlyContinue
    }
  }

  if (-not $candle -or -not $light) {
    Write-Error @(
      "WiX Toolset이 필요합니다 (jpackage --type $Type).",
      ' - candle.exe / light.exe 가 PATH에 있어야 합니다.',
      ' - 설치: https://wixtoolset.org/ (WiX Toolset v3.11+ 권장)',
      '설치 후 새 터미널을 열고 다시 실행하세요.',
      '대안: WiX 없이도 배포하려면 -Type zip 또는 -Type app-image 를 사용하세요.'
    )
  }
}

$jpackageArgs = @(
  '--type', $Type,
  '--name', $appName,
  '--app-version', $Version,
  '--vendor', 'example',
  '--description', 'JavaFX desktop ToDo app',
  '--input', $inputDir,
  '--dest', $destDir,
  '--main-jar', $appJarName,
  '--main-class', $mainClass,
  '--runtime-image', $runtimeDir,
  '--java-options', '-Dfile.encoding=UTF-8',
  '--java-options', '-Dtodoapp.installed=true'
)

if ($Type -in @('msi','exe')) {
  $jpackageArgs += @('--win-menu', '--win-shortcut')
  $jpackageArgs += @('--win-upgrade-uuid', $WinUpgradeUuid)
}

& jpackage @jpackageArgs

if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ''
Write-Host '✅ MSI 생성 완료:'
Get-ChildItem -Path $destDir -Filter '*.msi' | Select-Object FullName, Length, LastWriteTime
