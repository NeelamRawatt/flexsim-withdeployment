# =====================================================================
# import-parcel-data.ps1  (a .ps1 file = a PowerShell script: a list of Windows commands)
#
# Loads REAL parcel data (a file of "INSERT INTO public.parcel_details ..." lines, like the
# old app's parcel_details_*.sql) into experiment_db, table parcel_details.
#
# Run it from the project root (the folder that contains "infra"), with Docker running and
# experiment-service started at least ONCE (Flyway V4 must have created the table):
#
#   .\scripts\import-parcel-data.ps1 -SqlFile "C:\data\parcels\parcel_details_202608251302.sql"
#
# SAFETY:
#   - The data file is real company data: keep it OUTSIDE the project folder, never in Git.
#   - The file is checked first: it may ONLY insert into parcel_details. A file with DROP, DELETE,
#     UPDATE, ALTER, ... is refused, so a wrong or changed file can not damage the database.
#   - Everything is loaded in ONE transaction: all rows, or (on any error) none.
#   - It runs as experiment_user (the owner of experiment_db), not as the "postgres" super user.
#   - No password is typed or shown: inside the container psql uses the local socket.
#
# Load each file ONLY ONCE. The script shows the number of rows before and after.
# (In Azure the same file is loaded with psql from a machine inside the private network.)
# =====================================================================
param(
    [Parameter(Mandatory = $true)]
    [string]$SqlFile,
    [string]$Container = "flexsim-postgres",
    [string]$Database = "experiment_db",
    [string]$DbUser = "experiment_user"
)

$ErrorActionPreference = "Stop"   # stop at the first error

# ---- 1. check the file ----
if (-not (Test-Path $SqlFile -PathType Leaf)) {
    throw "File not found: $SqlFile"
}
$forbidden = Select-String -Path $SqlFile -Pattern '^\s*(DROP|DELETE|UPDATE|ALTER|TRUNCATE|CREATE|GRANT|REVOKE|COPY|DO|CALL)\b' -CaseSensitive:$false | Select-Object -First 1
if ($forbidden) {
    throw "Refused: line $($forbidden.LineNumber) is not an INSERT: $($forbidden.Line.Trim())"
}
$otherTable = Select-String -Path $SqlFile -Pattern '^\s*INSERT\s+INTO\s+(?!(public\.)?parcel_details\b)' -CaseSensitive:$false | Select-Object -First 1
if ($otherTable) {
    throw "Refused: line $($otherTable.LineNumber) inserts into another table"
}
Write-Host "File checked: only INSERTs into parcel_details."

# ---- 2. rows before ----
function Count-Parcels {
    (docker exec $Container psql -U $DbUser -d $Database -t -A -c "select count(*) from parcel_details").Trim()
}
$before = Count-Parcels
Write-Host "Rows before: $before"

# ---- 3. copy the file into the container and load it in ONE transaction ----
# docker cp = copy a file from your computer into the container.
# psql -1 = one transaction; ON_ERROR_STOP=1 = stop (and undo everything) at the first error.
$inside = "/tmp/parcel-import.sql"
docker cp $SqlFile "${Container}:$inside"
try {
    docker exec $Container psql -v ON_ERROR_STOP=1 -1 -q -U $DbUser -d $Database -f $inside
    if ($LASTEXITCODE -ne 0) { throw "psql failed (exit code $LASTEXITCODE). Nothing was saved." }
} finally {
    docker exec $Container rm -f $inside | Out-Null   # never leave the data file lying in the container
}

# ---- 4. rows after ----
$after = Count-Parcels
Write-Host "Rows after:  $after   (added $([int64]$after - [int64]$before))" -ForegroundColor Green
