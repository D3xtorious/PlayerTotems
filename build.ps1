# Build script for Player Totems (Fabric Minecraft 26.3 / Java 25)
param(
    [string]$ClasspathFile = "cp.txt"
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

Write-Host "==> Building Player Totems (1.2.1+26.3)..." -ForegroundColor Cyan

# Ensure clean target/classes directory
if (Test-Path "target/classes") {
    Remove-Item -Recurse -Force "target/classes"
}
New-Item -ItemType Directory -Force -Path "target/classes" | Out-Null

# 1. Compile Java sources
Write-Host "==> Compiling Java sources from sources.txt..." -ForegroundColor Cyan
if (Test-Path $ClasspathFile) {
    & javac -encoding UTF-8 -cp "@$ClasspathFile" -d "target/classes" "@sources.txt"
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Compilation failed with exit code $LASTEXITCODE"
        exit $LASTEXITCODE
    }
} else {
    Write-Error "Classpath file '$ClasspathFile' not found. Please provide a valid classpath argument file."
    exit 1
}

# 2. Copy resources
Write-Host "==> Copying resources to target/classes..." -ForegroundColor Cyan
Copy-Item -Path "src/main/resources/*" -Destination "target/classes" -Recurse -Force

# 3. Package JAR
Write-Host "==> Packaging target/playertotems-1.2.1+26.3.jar..." -ForegroundColor Cyan
& jar -cf "target/playertotems-1.2.1+26.3.jar" -C "target/classes" .
Copy-Item "target/playertotems-1.2.1+26.3.jar" -Destination "..\playertotems-1.2.1+26.3.jar" -Force

Write-Host "==> Build succeeded: playertotems-1.2.1+26.3.jar ready for Modrinth release!" -ForegroundColor Green
