# build-and-push.ps1 - build and push image to Docker Hub.
# Run from project root. Requires: docker login (malexple account) done beforehand.
#
# NOTE: kept ASCII-only on purpose. Windows PowerShell 5.1 reads .ps1 files
# without a BOM using the system codepage, not UTF-8 - any non-ASCII
# character (e.g. Cyrillic comments) can get mangled and break string
# parsing ("TerminatorExpectedAtEndOfString"). PowerShell 7+ doesn't have
# this problem, but this script targets whatever ships with Windows 10.

$ErrorActionPreference = "Stop"

$Image = "malexple/book-metadata-service"

$GitSha = (git rev-parse --short HEAD 2>$null)
if (-not $GitSha) { $GitSha = "nogit" }

Write-Host "Building image..."
docker build -t "${Image}:latest" -t "${Image}:${GitSha}" .

Write-Host "Pushing ${Image}:latest and ${Image}:${GitSha}..."
docker push "${Image}:latest"
docker push "${Image}:${GitSha}"

Write-Host "Done. To roll back to this exact version later: docker pull ${Image}:${GitSha}"