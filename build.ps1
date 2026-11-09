$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
Push-Location frontend
try {
    & npm.cmd ci
    if ($LASTEXITCODE -ne 0) { throw 'npm ci failed' }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw 'Angular build failed' }
} finally { Pop-Location }
New-Item -ItemType Directory -Force backend/src/main/resources/static | Out-Null
Copy-Item frontend/dist/commonground/browser/* backend/src/main/resources/static/ -Recurse -Force
Push-Location backend
try {
    & .\mvnw.cmd clean package
    if ($LASTEXITCODE -ne 0) { throw 'Java build or tests failed' }
} finally { Pop-Location }
