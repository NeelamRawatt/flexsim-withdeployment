# =====================================================================
# test-db-restore.ps1   (folder: infra\scripts)
# PROVES the database backups work: restores the PostgreSQL server to a point in
# time (10 minutes ago) as a NEW, temporary server, checks it, then deletes it.
# "A backup you never restored is only a hope." Run it before go-live, then every few months.
#
# Your real database is NOT touched. The temporary server costs money only while
# it exists (about 15-30 minutes).
#
# Run (from the project root):
#   powershell -ExecutionPolicy Bypass -File infra\scripts\test-db-restore.ps1
#   (prod: add  -ResourceGroup <prod-rg> -Server <prod-server>)
# =====================================================================
param(
    [string]$ResourceGroup = "rg-flexsim-dev",
    [string]$Server        = "psql-flexsim-dev-neelam",
    [int]$MinutesAgo       = 10
)
$ErrorActionPreference = "Stop"
$TestServer = "$Server-restoretest"
$expected   = "auth_db", "experiment_db", "file_db", "simulation_db", "kpi_db", "ai_db"

# 1) Read the source server's private network settings (the copy must live in the same private network).
Write-Host "1) Reading source server $Server" -ForegroundColor Cyan
$src = az postgres flexible-server show -g $ResourceGroup -n $Server | ConvertFrom-Json
if (-not $src) { throw "Server $Server not found in $ResourceGroup" }
$subnetId = $src.network.delegatedSubnetResourceId
$dnsZone  = $src.network.privateDnsZoneArmResourceId

# 2) Restore = make a NEW server from the backups, as it was $MinutesAgo minutes ago.
$restoreTime = (Get-Date).ToUniversalTime().AddMinutes(-$MinutesAgo).ToString("yyyy-MM-ddTHH:mm:ssZ")
Write-Host "2) Restoring to point in time $restoreTime UTC as $TestServer (about 10-20 minutes)..." -ForegroundColor Cyan
$start = Get-Date
az postgres flexible-server restore -g $ResourceGroup --name $TestServer --source-server $Server `
    --restore-time $restoreTime --subnet $subnetId --private-dns-zone $dnsZone --output none
if ($LASTEXITCODE -ne 0) { throw "Restore failed (see the message above)" }
$minutes = [math]::Round(((Get-Date) - $start).TotalMinutes, 1)

try {
    # 3) Check: the copy is Ready and has all 6 service databases.
    Write-Host "3) Checking the restored copy" -ForegroundColor Cyan
    $state = az postgres flexible-server show -g $ResourceGroup -n $TestServer --query state -o tsv
    $dbs   = az postgres flexible-server db list -g $ResourceGroup -s $TestServer --query "[].name" -o tsv
    $missing = $expected | Where-Object { $dbs -notcontains $_ }
    Write-Host "   state: $state"
    Write-Host "   databases: $($dbs -join ', ')"
    if ($state -ne "Ready" -or $missing) { throw "Restore test FAILED. Missing databases: $($missing -join ', ')" }
    Write-Host "RESTORE TEST PASSED in $minutes minutes (write this time down: it is your real 'time to recover')." -ForegroundColor Green
}
finally {
    # 4) Always delete the temporary copy (it costs money).
    Write-Host "4) Deleting the temporary server $TestServer" -ForegroundColor Cyan
    az postgres flexible-server delete -g $ResourceGroup -n $TestServer --yes --output none
    Write-Host "   deleted."
}
