# Downloads, verifies and unpacks the Gradle distribution declared in gradle-wrapper.properties.
# Prints the Gradle home directory on stdout (all progress goes to stderr). Used by gradlew.bat.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$props = Join-Path $PSScriptRoot 'gradle-wrapper.properties'
$map = @{}
foreach ($line in Get-Content -LiteralPath $props) {
    if ($line -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $map[$Matches[1]] = $Matches[2] -replace '\\:', ':' }
}
$url = $map['distributionUrl']
$sha = $map['distributionSha256Sum']
if (-not $url) { throw "distributionUrl missing in $props" }
if (-not $sha) { throw "distributionSha256Sum missing in $props" }

$zipName = [IO.Path]::GetFileName($url)                      # gradle-9.2.1-bin.zip
$distName = [IO.Path]::GetFileNameWithoutExtension($zipName) # gradle-9.2.1-bin
$versionDir = $distName -replace '-(bin|all)$', ''           # gradle-9.2.1
$userHome = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE '.gradle' }
$base = Join-Path (Join-Path $userHome 'nexus-dists') $distName
$gradleHome = Join-Path $base $versionDir

if (-not (Test-Path -LiteralPath (Join-Path $gradleHome 'bin\gradle.bat'))) {
    New-Item -ItemType Directory -Force -Path $base | Out-Null
    $tmp = Join-Path $base "$zipName.part"
    [Console]::Error.WriteLine("Downloading $url")
    Invoke-WebRequest -Uri $url -OutFile $tmp -UseBasicParsing
    $actual = (Get-FileHash -LiteralPath $tmp -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $sha.ToLowerInvariant()) {
        Remove-Item -LiteralPath $tmp -Force
        throw "SHA-256 mismatch for $zipName (expected $sha, got $actual)"
    }
    if (Test-Path -LiteralPath $gradleHome) { Remove-Item -LiteralPath $gradleHome -Recurse -Force }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory($tmp, $base)
    Remove-Item -LiteralPath $tmp -Force
}
Write-Output $gradleHome
