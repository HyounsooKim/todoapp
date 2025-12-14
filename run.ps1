param(
  [switch]$NoBuild
)

$ErrorActionPreference = 'Stop'

function Resolve-JavaHome {
  # 1) If JAVA_HOME is already set and java.exe exists, trust it.
  if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    return $env:JAVA_HOME
  }

  # 2) Registry: JavaSoft JDK (most common for Oracle/Temurin MSI)
  $jdkKey = 'HKLM:\SOFTWARE\JavaSoft\JDK'
  $jdkProps = Get-ItemProperty -Path $jdkKey -ErrorAction SilentlyContinue
  if ($jdkProps -and $jdkProps.CurrentVersion) {
    $versionKey = Join-Path $jdkKey $jdkProps.CurrentVersion
    $versionProps = Get-ItemProperty -Path $versionKey -ErrorAction SilentlyContinue
    if ($versionProps -and $versionProps.JavaHome -and (Test-Path (Join-Path $versionProps.JavaHome 'bin\java.exe'))) {
      return $versionProps.JavaHome
    }
  }

  # 3) Common install location fallback
  $candidate = 'C:\Program Files\Java\jdk-21'
  if (Test-Path (Join-Path $candidate 'bin\java.exe')) {
    return $candidate
  }

  return $null
}

$javaHome = Resolve-JavaHome
if (-not $javaHome) {
  Write-Error 'JDK 21을 찾지 못했습니다. JDK 21 설치 후 JAVA_HOME을 설정하거나, 레지스트리에 JavaHome이 등록되어 있어야 합니다.'
}

$env:JAVA_HOME = $javaHome
if (-not ($env:Path -split ';' | Where-Object { $_ -ieq "$javaHome\bin" })) {
  $env:Path = "$javaHome\bin;" + $env:Path
}

Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host ("java: " + (where.exe java | Select-Object -First 1))

if (-not (Test-Path '.\mvnw.cmd')) {
  Write-Error 'mvnw.cmd가 없습니다. 프로젝트 루트에서 실행해주세요.'
}

$argsList = @('spring-boot:run')
if ($NoBuild) {
  $argsList += '-DskipTests'
}

& .\mvnw.cmd @argsList
exit $LASTEXITCODE
