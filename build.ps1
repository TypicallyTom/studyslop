$ErrorActionPreference = 'Stop'

$repoRoot = $PSScriptRoot
$classesDir = Join-Path $repoRoot 'build\classes'
$jarPath = Join-Path $repoRoot 'build\CourseCompanion.jar'
$sourcePath = Join-Path $repoRoot 'src\AssignmentTracker.java'

New-Item -ItemType Directory -Force -Path $classesDir | Out-Null
javac -encoding UTF-8 -d $classesDir $sourcePath
if ($LASTEXITCODE -ne 0) {
    throw 'Java compilation failed.'
}

jar --create --file $jarPath --main-class AssignmentTracker -C $classesDir .
if ($LASTEXITCODE -ne 0) {
    throw 'Creating the runnable JAR failed.'
}

Write-Output "Created $jarPath"
