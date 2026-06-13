param(
    [Parameter(Mandatory = $true)]
    [string]$PublicBaseUrl
)

$ErrorActionPreference = "Stop"

if (-not $PublicBaseUrl.StartsWith("https://")) {
    throw "PublicBaseUrl must start with https://"
}

$normalizedBaseUrl = $PublicBaseUrl.TrimEnd("/")
$googleClientId = $env:OAUTH2_GOOGLE_CLIENT_ID
$googleClientSecretConfigured = -not [string]::IsNullOrWhiteSpace($env:OAUTH2_GOOGLE_CLIENT_SECRET)

$env:APP_PUBLIC_BASE_URL = $normalizedBaseUrl

Write-Host ""
Write-Host "Ngrok OAuth dev mode"
Write-Host "APP_PUBLIC_BASE_URL = $env:APP_PUBLIC_BASE_URL"
Write-Host ""
Write-Host "Authorized redirect URIs to register:"
Write-Host "Google   : $normalizedBaseUrl/login/oauth2/code/google"
Write-Host ""
Write-Host "Detected credentials:"
Write-Host ("Google   : client-id set = {0}, client-secret set = {1}" -f (-not [string]::IsNullOrWhiteSpace($googleClientId)), $googleClientSecretConfigured)
Write-Host ""

& ".\mvnw.cmd" spring-boot:run
