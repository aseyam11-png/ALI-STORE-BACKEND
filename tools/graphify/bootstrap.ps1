$ErrorActionPreference = "Stop"

if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
  throw "uv is required. Install uv first: https://docs.astral.sh/uv/"
}

Write-Host "Installing Graphify 0.9.72..."
uv tool install --upgrade "graphifyy==0.9.72"

Write-Host "Graphify version:"
graphify --version

Write-Host "Installing the project-scoped Claude/Windows skill..."
graphify install --project --platform windows

Write-Host "Installing the local graph refresh hook..."
graphify hook install

Write-Host "Building the initial code graph..."
graphify extract . --code-only

Write-Host "Graphify setup completed."
