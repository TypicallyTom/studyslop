$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$buildRoot = Join-Path $repoRoot 'build\installer'
$classesDir = Join-Path $buildRoot 'classes'
$inputDir = Join-Path $buildRoot 'input'
$jarPath = Join-Path $inputDir 'CourseCompanion.jar'
$releaseDir = Join-Path $repoRoot 'release'
$sourcePath = Join-Path $repoRoot 'src\AssignmentTracker.java'
$iconPath = Join-Path $repoRoot 'assets\CourseCompanion.ico'
$finalInstaller = Join-Path $releaseDir 'Course-Companion-Setup-0.2.0.exe'

foreach ($tool in @('javac', 'jar', 'jpackage', 'candle.exe', 'light.exe')) {
    if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) {
        throw "Required tool '$tool' was not found. Install a JDK and WiX Toolset, then add their bin folders to PATH."
    }
}

New-Item -ItemType Directory -Force -Path $classesDir, $inputDir, $releaseDir | Out-Null
javac -encoding UTF-8 -d $classesDir $sourcePath
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }

jar --create --file $jarPath --main-class AssignmentTracker -C $classesDir .
if ($LASTEXITCODE -ne 0) { throw 'Creating the application JAR failed.' }

jpackage --type exe --input $inputDir --main-jar 'CourseCompanion.jar' --main-class AssignmentTracker `
    --name 'Course Companion' --app-version '0.2.0' `
    --description 'College assignment planner and study helper' --vendor 'Course Companion' `
    --icon $iconPath --dest $releaseDir --win-per-user-install --win-menu `
    --win-menu-group 'Course Companion' --win-shortcut
if ($LASTEXITCODE -ne 0) { throw 'Windows installer packaging failed.' }

$generatedInstaller = Join-Path $releaseDir 'Course Companion-0.2.0.exe'
if (-not (Test-Path $generatedInstaller)) { throw 'The Windows installer was not created.' }
Move-Item -LiteralPath $generatedInstaller -Destination $finalInstaller -Force
Write-Output "Created $finalInstaller"
