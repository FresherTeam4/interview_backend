param(
    [Parameter(Mandatory = $true)][string]$Title,
    [Parameter(Mandatory = $true)][string]$JobDescriptionText,
    [string]$BaseUrl = 'http://localhost:8080',
    [ValidateRange(1, 1800)][int]$WaitSeconds = 600,
    [switch]$Publish
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:INTERVIEW_API_TOKEN)) {
    throw 'Set INTERVIEW_API_TOKEN to a valid access token before running the demo.'
}

$baseApi = $BaseUrl.TrimEnd('/') + '/api'
$headers = @{ Authorization = 'Bearer ' + $env:INTERVIEW_API_TOKEN }
$body = @{ title = $Title; text = $JobDescriptionText } | ConvertTo-Json
$document = Invoke-RestMethod -Method Post -Uri ($baseApi + '/job-descriptions') `
    -Headers $headers -ContentType 'application/json; charset=utf-8' `
    -Body ([Text.Encoding]::UTF8.GetBytes($body))
Write-Output ('Job description ID: ' + $document.id)

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($WaitSeconds)
while ($document.status -in @('UPLOADED', 'EXTRACTING', 'ANALYZING')) {
    if ([DateTimeOffset]::UtcNow -ge $deadline) {
        throw ('Processing is still running for job description ' + $document.id + '.')
    }
    Start-Sleep -Seconds 2
    $document = Invoke-RestMethod -Method Get `
        -Uri ($baseApi + '/job-descriptions/' + $document.id) -Headers $headers
}
if ($document.status -ne 'READY') {
    throw ('Processing failed: ' + $document.errorCode + ' - ' + $document.statusMessage)
}

$templateApi = $baseApi + '/interview-templates/' + $document.templateId
$template = Invoke-RestMethod -Method Get -Uri $templateApi -Headers $headers
$confirmBody = @{ expectedVersion = $template.version } | ConvertTo-Json
$confirmed = Invoke-RestMethod -Method Post -Uri ($templateApi + '/confirm') `
    -Headers $headers -ContentType 'application/json' -Body $confirmBody
Write-Output ('Confirmed template ID: ' + $confirmed.id)

if ($Publish) {
    $publishBody = @{ expectedVersion = $confirmed.version } | ConvertTo-Json
    $published = Invoke-RestMethod -Method Post -Uri ($templateApi + '/publish') `
        -Headers $headers -ContentType 'application/json' -Body $publishBody
    Write-Output ('Published template ID: ' + $published.id)
}
