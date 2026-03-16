#Requires -Version 5.1
<#
.SYNOPSIS
  Fancy Git Contributor Statistics — added / deleted / net lines per author.

.DESCRIPTION
  Aggregates added/deleted lines per author using `git log --numstat`.
  Compatible with Windows PowerShell 5.1 and PowerShell 7+.

  Features:
    - Per-author Added / Deleted / Net line counts
    - Colorized, ranked table with ASCII progress bars
    - Automatic binary-file filtering
    - Date-range, branch, path and exclude-pattern filters
    - Top-N display and multi-criteria sorting
    - Author name normalization / alias mapping
    - CSV and JSON export
    - Commit count, peak-day, and streaks in the summary
    - Fun achievements per contributor

.EXAMPLE  .\git-stats.ps1
.EXAMPLE  .\git-stats.ps1 -Since "2026-01-01" -Until "2026-02-19" -Top 10
.EXAMPLE  .\git-stats.ps1 -Ref main -Since "30 days ago" -SortBy Net
.EXAMPLE  .\git-stats.ps1 -Paths src,lib -ExcludePatterns "*.test.*","*.spec.*"
.EXAMPLE  .\git-stats.ps1 -CsvOut stats.csv -JsonOut stats.json
.EXAMPLE  .\git-stats.ps1 -NormalizeAuthors -AuthorMap @{ "chris"="Christian Stehle" }
.EXAMPLE  .\git-stats.ps1 -NoBars -NoColor
#>

[CmdletBinding()]
param(
    [string]   $Ref              = "HEAD",
    [string]   $Since,
    [string]   $Until,
    [string[]] $Paths,
    [string[]] $ExcludePatterns  = @(
    "*.db","*.pdf","*.png","*.jpg","*.jpeg","*.gif","*.ico","*.svg",
    "*.zip","*.tar","*.gz","*.jar","*.class","*.exe","*.dll","*.so",
    "*.bin","*.dat","*.mp3","*.mp4","*.mov","*.woff","*.woff2","*.ttf",
    "repomix-output.xml", "*.xml"),
    [int]      $Top               = 0,

    [ValidateSet("Added","Deleted","Net","Name","Commits")]
    [string]   $SortBy            = "Added",

    [switch]   $NoBars,
    [int]      $BarWidth          = 30,
    [switch]   $NoColor,
    [switch]   $NormalizeAuthors,
    [hashtable]$AuthorMap         = @{},
    [string]   $CsvOut,
    [string]   $JsonOut
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ─────────────────────────────────────────────
#  CONSOLE ENCODING
# ─────────────────────────────────────────────
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

# ─────────────────────────────────────────────
#  COLOR HELPERS  (degrade gracefully with -NoColor)
# ─────────────────────────────────────────────
function cW([string]$text, [System.ConsoleColor]$color) {
    if ($NoColor) { Write-Host $text -NoNewline }
    else          { Write-Host $text -NoNewline -ForegroundColor $color }
}
function cWL([string]$text, [System.ConsoleColor]$color) {
    if ($NoColor) { Write-Host $text }
    else          { Write-Host $text -ForegroundColor $color }
}

function Write-GradientLine([string]$line, [System.ConsoleColor[]]$palette) {
    if ($NoColor -or -not $palette -or $palette.Count -eq 0) {
        Write-Host $line
        return
    }

    $chars = $line.ToCharArray()
    for ($i = 0; $i -lt $chars.Length; $i++) {
        $colorIndex = [Math]::Floor(($i / [Math]::Max(1, $chars.Length - 1)) * ($palette.Count - 1))
        cW $chars[$i] $palette[$colorIndex]
    }
    Write-Host ""
}

# ─────────────────────────────────────────────
#  BANNER
# ─────────────────────────────────────────────
function Write-Banner {
    $lines = @(
        "  ▄████  ██▓▄▄▄█████▓    ██████ ▄▄▄█████▓ ▄▄▄     ▄▄▄█████▓  ██████ ",
        " ██▒ ▀█▒▓██▒▓  ██▒ ▓▒  ▒██    ▒ ▓  ██▒ ▓▒▒████▄   ▓  ██▒ ▓▒▒██    ▒ ",
        "▒██░▄▄▄░▒██▒▒ ▓██░ ▒░  ░ ▓██▄   ▒ ▓██░ ▒░▒██  ▀█▄ ▒ ▓██░ ▒░░ ▓██▄   ",
        "░▓█  ██▓░██░░ ▓██▓ ░     ▒   ██▒░ ▓██▓ ░ ░██▄▄▄▄██░ ▓██▓ ░   ▒   ██▒",
        "░▒▓███▀▒░██░  ▒██▒ ░   ▒██████▒▒  ▒██▒ ░  ▓█   ▓██▒ ▒██▒ ░ ▒██████▒▒",
        " ░▒   ▒ ░▓    ▒ ░░     ▒ ▒▓▒ ▒ ░  ▒ ░░    ▒▒   ▓▒█░ ▒ ░░   ▒ ▒▓▒ ▒ ░",
        "  ░   ░  ▒ ░    ░      ░ ░▒  ░ ░    ░      ▒   ▒▒ ░   ░    ░ ░▒  ░ ░",
        "░ ░   ░  ▒ ░  ░        ░  ░  ░    ░        ░   ▒    ░      ░  ░  ░  ",
        "      ░  ░                   ░                  ░  ░              ░  "
    )

    $synthWave = @(
        "           .-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-.",
        "           |      ✦  Contributor Analytics Command Center  ✦      |",
        "           '-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-'"
    )

    Write-Host ""
    $palette = @(
        [ConsoleColor]::Magenta,
        [ConsoleColor]::DarkMagenta,
        [ConsoleColor]::Blue,
        [ConsoleColor]::DarkBlue,
        [ConsoleColor]::Cyan,
        [ConsoleColor]::DarkCyan
    )

    foreach ($line in $lines) { Write-GradientLine $line $palette }
    foreach ($line in $synthWave) { cWL $line DarkGray }
    cWL "     Contributor Line-Change Analytics  |  PowerShell Neon Edition" White
    Write-Host ""
}

# ─────────────────────────────────────────────
#  SECTION HEADERS
# ─────────────────────────────────────────────
function Write-Section([string]$title) {
    $pad   = 1
    $inner = " " * $pad + "✦ " + $title + " ✦" + " " * $pad
    $width = $inner.Length + 4
    $top   = [char]0x256D + ([string][char]0x2500 * ($width - 2)) + [char]0x256E
    $mid   = [char]0x2502 + " " + $inner + " " + [char]0x2502
    $bot   = [char]0x2570 + ([string][char]0x2500 * ($width - 2)) + [char]0x256F
    Write-Host ""
    cWL $top  DarkCyan
    cWL $mid  White
    cWL $bot  Cyan
    Write-Host ""
}

# ─────────────────────────────────────────────
#  HORIZONTAL RULE
# ─────────────────────────────────────────────
function Write-HR([int]$width = 72, [string]$char = [string][char]0x2500) {
    if ($NoColor) {
        Write-Host ($char * $width)
        return
    }

    $line = $char * $width
    Write-GradientLine $line @([ConsoleColor]::DarkGray,[ConsoleColor]::Gray,[ConsoleColor]::DarkGray)
}

# ─────────────────────────────────────────────
#  ASSERT GIT REPO
# ─────────────────────────────────────────────
function Assert-GitRepo {
    $result = & git rev-parse --is-inside-work-tree 2>&1
    if ($LASTEXITCODE -ne 0) {
        cWL "" Red
        cWL "  ERROR: Not inside a git repository (or git is not installed)." Red
        cWL "  Please run this script from within your project folder." Red
        Write-Host ""
        exit 1
    }
}

# ─────────────────────────────────────────────
#  BUILD GIT ARGS
# ─────────────────────────────────────────────
function Build-GitArgs {
    $a = [System.Collections.Generic.List[string]]::new()
    $a.Add("log")
    $a.Add($Ref)
    $a.Add("--numstat")
    $a.Add("--pretty=format:AUTHOR:%aN|DATE:%ad")
    $a.Add("--date=short")

    if ($Since) { $a.Add("--since=$Since") }
    if ($Until) { $a.Add("--until=$Until") }

    $hasExcludes = ($ExcludePatterns -and $ExcludePatterns.Count -gt 0)
    $hasPaths    = ($Paths           -and $Paths.Count           -gt 0)

    if ($hasPaths -or $hasExcludes) {
        $a.Add("--")
        if ($hasPaths) { foreach ($p in $Paths) { $a.Add($p) } }
        if ($hasExcludes) { foreach ($p in $ExcludePatterns) { $a.Add(":(exclude)$p") } }
    }

    return , $a.ToArray()
}

# ─────────────────────────────────────────────
#  AUTHOR NORMALIZATION (SMART MERGE)
# ─────────────────────────────────────────────
function Normalize-Author([string]$name)
{
    if (-not $name) { return "" }

    # Grundnormalisierung
    $n = $name.Trim().ToLower()

    # Entferne doppelte Leerzeichen
    $n = ($n -replace "\s+", " ")

    # Entferne Sonderzeichen für Vergleich
    $simple = ($n -replace '[^a-z0-9]', '')

    # ─── HARDCODED ALIAS REGELN (dein Team hier eintragen)
    switch ($simple)
    {
        { $_ -match "christianstehle" } { return "Christian Stehle" }
        { $_ -eq "christian" }         { return "Christian Stehle" }

        { $_ -match "valentin" }       { return "Valentin S." }
        { $_ -eq "vast00005" }         { return "Valentin S." }

        { $_ -match "xudong" }         { return "Xudong" }

        { $_ -match "sophia" }         { return "sophiasarkhovska" }

        { $_ -match "markus" }         { return "Markus.Esch" }

        { $_ -match "michal" }         { return "Michał Roziel" }

    }

    # ─── Falls kein Alias gefunden → Titel-Case zurückgeben

    return (Get-Culture).TextInfo.ToTitleCase($n)
}



# ─────────────────────────────────────────────
#  ASCII BAR
# ─────────────────────────────────────────────
function New-Bar([long]$value, [long]$max, [int]$width) {
    if ($width -le 0 -or $max -le 0) { return "" }
    $ratio  = [Math]::Max(0, [Math]::Min(1, [double]$value / [double]$max))
    $filled = [int][Math]::Round($ratio * $width)
    $empty  = $width - $filled
    return ([string][char]0x2588 * $filled) + ([string][char]0x2591 * $empty)
}

# ─────────────────────────────────────────────
#  NUMBER FORMATTING
# ─────────────────────────────────────────────
function fmt([long]$n) { return "{0:N0}" -f $n }

# ─────────────────────────────────────────────
#  MEDAL / RANK EMOJI
# ─────────────────────────────────────────────
function Get-Medal([int]$rank) {
    switch ($rank) {
        1 { return "  #1 " }
        2 { return "  #2 " }
        3 { return "  #3 " }
        default { return ("  #{0,-2}" -f $rank) }
    }
}

# ─────────────────────────────────────────────
#  MAIN
# ─────────────────────────────────────────────
Write-Banner
Assert-GitRepo

Write-Section "Configuration"
cW  "  Ref      : " DarkGray ; cWL $Ref Cyan
if ($Since) { cW  "  Since    : " DarkGray ; cWL $Since Yellow }
if ($Until) { cW  "  Until    : " DarkGray ; cWL $Until Yellow }
if ($Paths -and $Paths.Count -gt 0) {
    cW  "  Paths    : " DarkGray ; cWL ($Paths -join ", ") Green
}
cW  "  Sort by  : " DarkGray ; cWL $SortBy Magenta
$topLabel = if ($Top -gt 0) { [string]$Top } else { "all" }
cW  "  Top N    : " DarkGray ; cWL $topLabel Magenta
cW  "  Excludes : " DarkGray ; cWL ($ExcludePatterns -join ", ") DarkGray
Write-Host ""

# ─── Run git ───────────────────────────────────
cWL "  Running git log..." DarkGray
$gitArgs = Build-GitArgs
$rawLines = & git @gitArgs 2>&1

if ($LASTEXITCODE -ne 0) {
    cWL "  ERROR: git log failed. Check your -Ref value or repository state." Red
    Write-Host ($rawLines -join "`n")
    exit 1
}

cWL "  Parsing output..." DarkGray

# ─── Parse ─────────────────────────────────────
$currentAuthor = ""
$currentDate   = ""

# per-author aggregation table
$authorAdded    = @{}
$authorDeleted  = @{}
$authorCommits  = @{}
$authorDates    = @{}   # list of commit dates
$authorFiles    = @{}   # list of files touched per author

foreach ($line in $rawLines) {
    if ([string]::IsNullOrWhiteSpace($line)) { continue }
    $lineStr = [string]$line

    # Author / date marker line
    if ($lineStr -match '^AUTHOR:(.+)\|DATE:(.+)$') {
        $currentAuthor = Normalize-Author $Matches[1]
        $currentDate   = $Matches[2].Trim()

        if (-not $authorAdded.ContainsKey($currentAuthor)) {
            $authorAdded[$currentAuthor]   = [long]0
            $authorDeleted[$currentAuthor] = [long]0
            $authorCommits[$currentAuthor] = [int]0
            $authorDates[$currentAuthor]   = [System.Collections.Generic.List[string]]::new()
            $authorFiles[$currentAuthor]   = [System.Collections.Generic.List[string]]::new()
        }
        $authorCommits[$currentAuthor]++
        if ($currentDate) { $authorDates[$currentAuthor].Add($currentDate) }
        continue
    }

    # Skip binary lines ("- \t - \t file")
    if ($lineStr -match '^-\s+-\s+') { continue }

    # Numstat line: "added<TAB>deleted<TAB>file"
    if ($lineStr -match '^(\d+)\t(\d+)\t(.+)$') {
        if ($currentAuthor -eq "") { continue }
        $authorAdded[$currentAuthor]   += [long]$Matches[1]
        $authorDeleted[$currentAuthor] += [long]$Matches[2]
        $authorFiles[$currentAuthor].Add($Matches[3])
        continue
    }
}

if ($authorAdded.Count -eq 0) {
    Write-Section "Result"
    cWL "  No text-file changes found with the current filters." Yellow
    cWL "  Try loosening -Since / -Until / -Paths, or check -Ref." DarkGray
    Write-Host ""
    exit 0
}

# ─── Build result objects ─────────────────────
$results = foreach ($author in $authorAdded.Keys) {
    $add = $authorAdded[$author]
    $del = $authorDeleted[$author]
    [PSCustomObject]@{
        Contributor = $author
        Added       = $add
        Deleted     = $del
        Net         = $add - $del
        Commits     = $authorCommits[$author]
        Dates       = $authorDates[$author]
        Files       = $authorFiles[$author]
    }
}

# ─── Sort ─────────────────────────────────────
$results = switch ($SortBy) {
    "Added"   { $results | Sort-Object Added   -Descending }
    "Deleted" { $results | Sort-Object Deleted -Descending }
    "Net"     { $results | Sort-Object Net     -Descending }
    "Name"    { $results | Sort-Object Contributor }
    "Commits" { $results | Sort-Object Commits -Descending }
}

# ─── Top N ────────────────────────────────────
if ($Top -gt 0) { $results = $results | Select-Object -First $Top }

# ─── Metrics for bars ─────────────────────────
$maxAdded   = [long](($results | Measure-Object Added -Maximum).Maximum)
$maxDeleted = ($results | Measure-Object Deleted -Maximum).Maximum
$maxNet = [long][Math]::Abs(
    (
        $results |
        Sort-Object { [Math]::Abs($_.Net) } -Descending |
        Select-Object -First 1
    ).Net
)

$totalAdded = [long](($results | Measure-Object Added -Sum).Sum)
$totalDeleted = ($results | Measure-Object Deleted -Sum).Sum
$totalNet     = ($results | Measure-Object Net     -Sum).Sum
$totalCommits = ($results | Measure-Object Commits -Sum).Sum

# ─────────────────────────────────────────────
#  CONTRIBUTOR TABLE
# ─────────────────────────────────────────────
Write-Section "Contributor Breakdown"

# Column widths
$nameWidth = [Math]::Max(12, ($results | ForEach-Object { $_.Contributor.Length } | Measure-Object -Maximum).Maximum)
$addW  = 10 ; $delW = 10 ; $netW = 11 ; $cmtW = 7

# Header row
$hRank = "  Rank"
$hName = "Contributor".PadRight($nameWidth)
$hAdd  = "  Added".PadLeft($addW)
$hDel  = "Deleted".PadLeft($delW)
$hNet  = "Net".PadLeft($netW)
$hCmt  = "Commits".PadLeft($cmtW)
$hBar  = if (-not $NoBars) { "  Bar (Added)" } else { "" }

cW $hRank DarkGray
cW "  " DarkGray
cW $hName White
cW $hAdd  Green
cW "  " DarkGray
cW $hDel  Red
cW "  " DarkGray
cW $hNet  Cyan
cW "  " DarkGray
cW $hCmt  Yellow
if (-not $NoBars) { cW $hBar DarkGray }
Write-Host ""

$barExtra = 0
if (-not $NoBars) {
    $barExtra = 4 + $BarWidth
}

Write-HR ($hRank.Length + 2 + $nameWidth + $addW + 2 + $delW + 2 + $netW + 2 + $cmtW + $barExtra + 4)


$rank = 0
foreach ($r in $results) {
    $rank++

    $medal     = Get-Medal $rank
    $namePad   = $r.Contributor.PadRight($nameWidth)
    $addStr    = (fmt $r.Added).PadLeft($addW)
    $delStr    = (fmt $r.Deleted).PadLeft($delW)
    $netRaw    = if ($r.Net -ge 0) { "+" + (fmt $r.Net) } else { fmt $r.Net }
    $netStr    = $netRaw.PadLeft($netW)
    $cmtStr    = ([string]$r.Commits).PadLeft($cmtW)
    $bar       = if (-not $NoBars) { "  " + (New-Bar $r.Added $maxAdded $BarWidth) } else { "" }

    $medalColor = switch ($rank) { 1 { "Yellow" } 2 { "Gray" } 3 { "DarkYellow" } default { "DarkGray" } }
    $netColor   = if ($r.Net -ge 0) { "Cyan" } else { "Magenta" }
    $barColor   = switch ($rank) { 1 { "Yellow" } 2 { "White" } 3 { "Gray" } default { "DarkGray" } }

    cW $medal $medalColor
    cW "  " DarkGray
    cW $namePad White
    cW $addStr  Green
    cW "  " DarkGray
    cW $delStr  Red
    cW "  " DarkGray
    cW $netStr  $netColor
    cW "  " DarkGray
    cW $cmtStr  Yellow
    if (-not $NoBars) { cW $bar $barColor }
    Write-Host ""
}

Write-HR

# ─────────────────────────────────────────────
#  SUMMARY
# ─────────────────────────────────────────────
Write-Section "Summary"

$summaryNetColor = if ($totalNet -ge 0) { "Cyan" } else { "Magenta" }
$netLabel = if ($totalNet -ge 0) { "+" + (fmt $totalNet) } else { fmt $totalNet }

cW  "  Total Added   : " DarkGray ; cWL ("+" + (fmt $totalAdded))   Green
cW  "  Total Deleted : " DarkGray ; cWL ("-" + (fmt $totalDeleted)) Red
cW  "  Total Net     : " DarkGray ; cWL $netLabel                   $summaryNetColor
cW  "  Total Commits : " DarkGray ; cWL (fmt $totalCommits)         Yellow
cW  "  Contributors  : " DarkGray ; cWL ([string]$results.Count)    White
Write-Host ""

# ─── Per-author mini summary ──────────────────
Write-HR
Write-Host ""
cWL "  Per-contributor share of total added lines:" DarkGray
Write-Host ""
foreach ($r in $results) {
    $pct  = if ($totalAdded -gt 0) { [Math]::Round(100 * $r.Added / $totalAdded, 1) } else { 0 }
    $bar  = New-Bar $r.Added $maxAdded 20
    $name = $r.Contributor.PadRight($nameWidth)
    $pctS = ("{0,5:N1}%" -f $pct)
    cW  "  $name  " DarkGray
    cW  $bar       Cyan
    cW  "  "        DarkGray
    cWL $pctS      Yellow
}
Write-Host ""

# ─── Active days ─────────────────────────────
Write-HR
Write-Host ""
cWL "  Active coding days per contributor:" DarkGray
Write-Host ""
foreach ($r in $results) {
    $days = @($r.Dates | Sort-Object -Unique).Count
    $name = $r.Contributor.PadRight($nameWidth)
    $maxDays = ($results | ForEach-Object {
    @($_.Dates | Sort-Object -Unique).Count
} | Measure-Object -Maximum).Maximum

$bar  = New-Bar $days $maxDays 20
    cW  "  $name  " DarkGray
    cW  $bar        Magenta
    cW  "  "         DarkGray
    cWL ("{0,3} day(s)" -f $days) Yellow
}
Write-Host ""

# ─── Most productive single day ───────────────
Write-HR
Write-Host ""
cWL "  Most active commit day (by commit count):" DarkGray
Write-Host ""

# Flatten all dates
$allDates = @(
    foreach ($r in $results) {
        $r.Dates
    }
)

if ($allDates) {
    $dayGroups = @($allDates) | Group-Object | Sort-Object Count -Descending | Select-Object -First 5
    foreach ($dg in $dayGroups) {
        $bar = New-Bar $dg.Count @($dayGroups | Select-Object -First 1).Count 20
        cW  "  $($dg.Name)  " DarkGray
        cW  $bar              Yellow
        cWL ("  {0} commit(s)" -f $dg.Count) White
    }
}
Write-Host ""

# ─────────────────────────────────────────────
#  EXPORTS
# ─────────────────────────────────────────────
$exportData = $results | Select-Object Contributor, Added, Deleted, Net, Commits

if ($CsvOut) {
    $exportData | Export-Csv -NoTypeInformation -Encoding UTF8 -Path $CsvOut
    cW  "  CSV exported : " DarkGray
    cWL $CsvOut Green
}

if ($JsonOut) {
    $exportData | ConvertTo-Json -Depth 3 | Out-File -Encoding UTF8 -FilePath $JsonOut
    cW  "  JSON exported: " DarkGray
    cWL $JsonOut Green
}

if ($CsvOut -or $JsonOut) { Write-Host "" }

# ─────────────────────────────────────────────
#  MODULE CONTRIBUTION
# ─────────────────────────────────────────────

Write-Section "Module Contribution"

$moduleStats = @{}

foreach ($line in $rawLines)
{
    if ($line -match '^AUTHOR:(.+)\|DATE:')
    {
        $currentAuthor = Normalize-Author $Matches[1]
        continue
    }

    if ($line -match '^(\d+)\t(\d+)\t(.+)$')
    {
        $file = $Matches[3]

        $clean = ($file -replace '{.+ => ', '' -replace '}', '')
        $module = ($clean -split '[\\/]' )[0]
        if ($module -match '\.') { continue }

        if (-not $moduleStats.ContainsKey($module))
        {
            $moduleStats[$module] = @{}
        }

        if (-not $moduleStats[$module].ContainsKey($currentAuthor))
        {
            $moduleStats[$module][$currentAuthor] = 0
        }

        $moduleStats[$module][$currentAuthor] += [int]$Matches[1]
    }
}

foreach ($module in $moduleStats.Keys)
{
    cWL "  $module" Cyan

    $moduleStats[$module].GetEnumerator() |
    Sort-Object Value -Descending |
    Select-Object -First 5 |
    ForEach-Object {

        $bar = New-Bar $_.Value 5000 20

        cW  "    $($_.Key.PadRight(20)) " DarkGray
        cW  $bar Yellow
        cWL "  +$($_.Value)" Green
    }

    Write-Host ""
}

# ─────────────────────────────────────────────
#  CONTRIBUTION TIMELINE
# ─────────────────────────────────────────────

Write-Section "Contribution Timeline"

$timeline = @{}

foreach ($line in $rawLines)
{
    if ($line -match 'DATE:(\d{4}-\d{2})')
    {
        $month = $Matches[1]

        if (-not $timeline.ContainsKey($month))
        {
            $timeline[$month] = 0
        }

        $timeline[$month]++
    }
}

$max = ($timeline.Values | Measure-Object -Maximum).Maximum

$timeline.Keys |
Sort-Object |
ForEach-Object {

    $bar = New-Bar $timeline[$_] $max 30

    cW  "  $_ " DarkGray
    cW  $bar Cyan
    cWL " $($timeline[$_]) commits" Yellow
}

# ─────────────────────────────────────────────
#  WEEKDAY HEATMAP
# ─────────────────────────────────────────────

Write-Section "Weekly Heatmap"

$heat = @{}

foreach ($line in $rawLines)
{
    if ($line -match 'DATE:(.+)')
    {
        $day = (Get-Date $Matches[1]).DayOfWeek

        if (-not $heat.ContainsKey($day))
        {
            $heat[$day] = 0
        }

        $heat[$day]++
    }
}

$max = ($heat.Values | Measure-Object -Maximum).Maximum

$heat.Keys |
Sort-Object |
ForEach-Object {

    $bar = New-Bar $heat[$_] $max 20

    cW  "  $_ " DarkGray
    cW  $bar Magenta
    cWL " $($heat[$_])" Yellow
}

# ─────────────────────────────────────────────
#  CODING STREAKS
# ─────────────────────────────────────────────

Write-Section "Coding Streaks"

foreach ($r in $results)
{
    $dates = @($r.Dates | Sort-Object -Unique)

    $maxStreak = 0
    $current = 1

    for ($i=1; $i -lt $dates.Count; $i++)
    {
        $diff = (Get-Date $dates[$i]) - (Get-Date $dates[$i-1])

        if ($diff.Days -eq 1)
        {
            $current++
            if ($current -gt $maxStreak) { $maxStreak = $current }
        }
        else
        {
            $current = 1
        }
    }

    $bar = New-Bar $maxStreak 10 20

    cW  "  $($r.Contributor.PadRight(20)) " DarkGray
    cW  $bar Red
    cWL " $maxStreak days" Yellow
}

# ─────────────────────────────────────────────
#  MOST MODIFIED FILES
# ─────────────────────────────────────────────

Write-Section "Most Modified Files"

$fileStats = @{}

foreach ($line in $rawLines)
{
    if ($line -match '^(\d+)\t(\d+)\t(.+)$')
    {
        $file = $Matches[3]

        if (-not $fileStats.ContainsKey($file))
        {
            $fileStats[$file] = 0
        }

        $fileStats[$file] += [int]$Matches[1]
    }
}

$fileStats.GetEnumerator() |
Sort Value -Descending |
Select -First 10 |
ForEach-Object {

    $bar = New-Bar $_.Value 5000 20

    cW  "  $($_.Key.PadRight(40)) " DarkGray
    cW  $bar Cyan
    cWL " +$($_.Value)" Yellow
}

# ─────────────────────────────────────────────
#  ACHIEVEMENTS
# ─────────────────────────────────────────────

Write-Section "Achievements"

function Write-Achievement([string]$icon, [string]$title, [string]$who, [string]$detail) {
    cW  "  $icon " Yellow
    cW  $title   White
    cW  " → "    DarkGray
    cW  $who     Cyan
    cW  "  "     DarkGray
    cWL $detail  DarkGray
}

# ─── Pre-compute per-author stats for achievements ───

# Streak data (recompute to have per-author)
$authorStreaks = @{}
foreach ($r in $results) {
    $dates = @($r.Dates | Sort-Object -Unique)
    $maxStrk = 0; $cur = 1
    for ($i = 1; $i -lt $dates.Count; $i++) {
        $diff = (Get-Date $dates[$i]) - (Get-Date $dates[$i-1])
        if ($diff.Days -eq 1) { $cur++; if ($cur -gt $maxStrk) { $maxStrk = $cur } }
        else { $cur = 1 }
    }
    $authorStreaks[$r.Contributor] = $maxStrk
}

# Unique files per author
$authorUniqueFiles = @{}
foreach ($r in $results) {
    $authorUniqueFiles[$r.Contributor] = @($r.Files | Sort-Object -Unique).Count
}

# Weekend commits per author
$authorWeekendCommits = @{}
foreach ($r in $results) {
    $wknd = 0
    foreach ($d in $r.Dates) {
        try {
            $dow = (Get-Date $d).DayOfWeek
            if ($dow -eq 'Saturday' -or $dow -eq 'Sunday') { $wknd++ }
        } catch {}
    }
    $authorWeekendCommits[$r.Contributor] = $wknd
}

# Delete ratio per author (deleted / (added + deleted))
$authorDeleteRatio = @{}
foreach ($r in $results) {
    $total = $r.Added + $r.Deleted
    if ($total -gt 0) { $authorDeleteRatio[$r.Contributor] = [Math]::Round($r.Deleted / $total * 100, 1) }
    else              { $authorDeleteRatio[$r.Contributor] = 0 }
}

# Average lines per commit
$authorAvgLinesPerCommit = @{}
foreach ($r in $results) {
    if ($r.Commits -gt 0) { $authorAvgLinesPerCommit[$r.Contributor] = [Math]::Round($r.Added / $r.Commits, 1) }
    else                  { $authorAvgLinesPerCommit[$r.Contributor] = 0 }
}

# Unique active days per author
$authorActiveDays = @{}
foreach ($r in $results) {
    $authorActiveDays[$r.Contributor] = @($r.Dates | Sort-Object -Unique).Count
}

# Commits per active day
$authorCommitsPerDay = @{}
foreach ($r in $results) {
    $days = @($r.Dates | Sort-Object -Unique).Count
    if ($days -gt 0) { $authorCommitsPerDay[$r.Contributor] = [Math]::Round($r.Commits / $days, 1) }
    else             { $authorCommitsPerDay[$r.Contributor] = 0 }
}

# File extensions per author (for polyglot detection)
$authorExtensions = @{}
foreach ($r in $results) {
    $exts = @($r.Files | ForEach-Object {
        # Clean git rename syntax like "{old => new}/file.txt" and illegal path chars
        $clean = ($_ -replace '\{[^}]*=> *', '' -replace '\}', '').Trim()
        try {
            $ext = [System.IO.Path]::GetExtension($clean)
            if ($ext) { $ext.ToLower() }
        } catch { $null }
    } | Where-Object { $_ } | Sort-Object -Unique)
    $authorExtensions[$r.Contributor] = $exts
}

Write-Host ""

# ── 1. The Machine ── most commits
$theMachine = $results | Sort-Object Commits -Descending | Select-Object -First 1
if ($theMachine) {
    Write-Achievement ">>>" "The Machine" $theMachine.Contributor `
        ("Most commits: {0} — Does this person even sleep?" -f $theMachine.Commits)
}

# ── 2. The Novelist ── most lines added
$theNovelist = $results | Sort-Object Added -Descending | Select-Object -First 1
if ($theNovelist) {
    Write-Achievement "+++" "The Novelist" $theNovelist.Contributor `
        ("+" + (fmt $theNovelist.Added) + " lines — Writing code like it's NaNoWriMo")
}

# ── 3. The Janitor ── most lines deleted
$theJanitor = $results | Sort-Object Deleted -Descending | Select-Object -First 1
if ($theJanitor) {
    Write-Achievement "---" "The Janitor" $theJanitor.Contributor `
        ("-" + (fmt $theJanitor.Deleted) + " lines deleted — Cleaning up everyone's mess since day 1")
}

# ── 4. The Surgeon ── highest delete ratio (min 100 lines touched)
$surgeonCandidates = $results | Where-Object { ($_.Added + $_.Deleted) -ge 100 }
if ($surgeonCandidates) {
    $theSurgeon = $surgeonCandidates | Sort-Object { $authorDeleteRatio[$_.Contributor] } -Descending | Select-Object -First 1
    if ($theSurgeon) {
        Write-Achievement "/%\" "The Surgeon" $theSurgeon.Contributor `
            ("{0}% delete ratio — Cuts code with surgical precision" -f $authorDeleteRatio[$theSurgeon.Contributor])
    }
}

# ── 5. The Architect ── most net lines (biggest codebase grower)
$theArchitect = $results | Sort-Object Net -Descending | Select-Object -First 1
if ($theArchitect -and $theArchitect.Net -gt 0) {
    Write-Achievement "^^^" "The Architect" $theArchitect.Contributor `
        ("Net +" + (fmt $theArchitect.Net) + " lines — Building empires, one function at a time")
}

# ── 6. The Black Hole ── most negative net lines
$theBlackHole = $results | Sort-Object Net | Select-Object -First 1
if ($theBlackHole -and $theBlackHole.Net -lt 0) {
    Write-Achievement "(o)" "The Black Hole" $theBlackHole.Contributor `
        ("Net " + (fmt $theBlackHole.Net) + " lines — Code goes in, nothing comes out")
}

# ── 7. The Marathon Runner ── longest streak
$marathonRunner = $results | Sort-Object { $authorStreaks[$_.Contributor] } -Descending | Select-Object -First 1
if ($marathonRunner -and $authorStreaks[$marathonRunner.Contributor] -gt 1) {
    Write-Achievement "~~~" "Marathon Runner" $marathonRunner.Contributor `
        ("{0}-day streak — Commits every single day like it's a religion" -f $authorStreaks[$marathonRunner.Contributor])
}

# ── 8. The Explorer ── most unique files touched
$theExplorer = $results | Sort-Object { $authorUniqueFiles[$_.Contributor] } -Descending | Select-Object -First 1
if ($theExplorer) {
    Write-Achievement "***" "The Explorer" $theExplorer.Contributor `
        ("{0} unique files — Knows where all the bodies are buried" -f $authorUniqueFiles[$theExplorer.Contributor])
}

# ── 9. Weekend Warrior ── most weekend commits
$weekendWarrior = $results | Sort-Object { $authorWeekendCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($weekendWarrior -and $authorWeekendCommits[$weekendWarrior.Contributor] -gt 0) {
    Write-Achievement "!!!" "Weekend Warrior" $weekendWarrior.Contributor `
        ("{0} weekend commits — What is work-life balance?" -f $authorWeekendCommits[$weekendWarrior.Contributor])
}

# ── 10. The Sniper ── lowest avg lines per commit (min 5 commits)
$sniperCandidates = $results | Where-Object { $_.Commits -ge 5 -and $_.Added -gt 0 }
if ($sniperCandidates) {
    $theSniper = $sniperCandidates | Sort-Object { $authorAvgLinesPerCommit[$_.Contributor] } | Select-Object -First 1
    if ($theSniper) {
        Write-Achievement "..." "The Sniper" $theSniper.Contributor `
            ("{0} avg lines/commit — Small, precise, deadly accurate" -f $authorAvgLinesPerCommit[$theSniper.Contributor])
    }
}

# ── 11. The Bulldozer ── highest avg lines per commit
$bulldozerCandidates = $results | Where-Object { $_.Commits -ge 3 }
if ($bulldozerCandidates) {
    $theBulldozer = $bulldozerCandidates | Sort-Object { $authorAvgLinesPerCommit[$_.Contributor] } -Descending | Select-Object -First 1
    if ($theBulldozer -and $authorAvgLinesPerCommit[$theBulldozer.Contributor] -gt 50) {
        Write-Achievement "###" "The Bulldozer" $theBulldozer.Contributor `
            ("{0} avg lines/commit — Why make 10 commits when 1 YOLO push works?" -f $authorAvgLinesPerCommit[$theBulldozer.Contributor])
    }
}

# ── 12. The Caffeine Addict ── most commits per active day
$caffeineCandidates = $results | Where-Object { $authorActiveDays[$_.Contributor] -ge 3 }
if ($caffeineCandidates) {
    $theCaffeine = $caffeineCandidates | Sort-Object { $authorCommitsPerDay[$_.Contributor] } -Descending | Select-Object -First 1
    if ($theCaffeine -and $authorCommitsPerDay[$theCaffeine.Contributor] -gt 2) {
        Write-Achievement "@@@" "Caffeine Addict" $theCaffeine.Contributor `
            ("{0} commits/active day — Probably has an IV drip of espresso" -f $authorCommitsPerDay[$theCaffeine.Contributor])
    }
}

# ── 13. One-Hit Wonder ── only 1 commit total
$oneHitWonders = @($results | Where-Object { $_.Commits -eq 1 })
if ($oneHitWonders.Count -gt 0) {
    $names = ($oneHitWonders | ForEach-Object { $_.Contributor }) -join ", "
    Write-Achievement "[1]" "One-Hit Wonder" $names `
        "1 commit and gone — Came, saw, committed, vanished"
}

# ── 14. The Balanced One ── closest added/deleted ratio to 1:1 (min 100 lines)
$balancedCandidates = $results | Where-Object { $_.Added -ge 100 -and $_.Deleted -ge 100 }
if ($balancedCandidates) {
    $theBalanced = $balancedCandidates | Sort-Object { [Math]::Abs($_.Added - $_.Deleted) / [Math]::Max(1, $_.Added + $_.Deleted) } | Select-Object -First 1
    if ($theBalanced) {
        $ratio = [Math]::Round($theBalanced.Added / [Math]::Max(1, $theBalanced.Deleted), 2)
        Write-Achievement "<=>" "The Balanced One" $theBalanced.Contributor `
            ("Add/Del ratio {0}:1 — Perfectly balanced, as all things should be" -f $ratio)
    }
}

# ── 15. The Lone Wolf ── fewest active days but still significant output
$loneWolfCandidates = $results | Where-Object { $_.Added -ge 500 }
if ($loneWolfCandidates -and @($loneWolfCandidates).Count -gt 1) {
    $theLoneWolf = $loneWolfCandidates | Sort-Object { $authorActiveDays[$_.Contributor] } | Select-Object -First 1
    if ($theLoneWolf -and $authorActiveDays[$theLoneWolf.Contributor] -le 3) {
        Write-Achievement ">|<" "The Lone Wolf" $theLoneWolf.Contributor `
            ("{0} active day(s), {1}+ lines — Shows up rarely but drops nukes" -f $authorActiveDays[$theLoneWolf.Contributor], (fmt $theLoneWolf.Added))
    }
}

# ── 16. The Polyglot ── most different file extensions
$polyglotWinner = $results | Sort-Object { $authorExtensions[$_.Contributor].Count } -Descending | Select-Object -First 1
if ($polyglotWinner -and $authorExtensions[$polyglotWinner.Contributor].Count -ge 4) {
    Write-Achievement "<*>" "The Polyglot" $polyglotWinner.Contributor `
        ("{0} file types — Speaks more languages than a UN interpreter" -f $authorExtensions[$polyglotWinner.Contributor].Count)
}

# ── 17. Copy-Paste Suspect ── single commit with huge added lines and zero deletes
$copypasteSuspect = $results | Where-Object { $_.Commits -le 3 -and $_.Added -gt 1000 -and $_.Deleted -lt 50 }
if ($copypasteSuspect) {
    $cpWinner = $copypasteSuspect | Sort-Object Added -Descending | Select-Object -First 1
    if ($cpWinner) {
        Write-Achievement "^C^" "Copy-Paste Suspect" $cpWinner.Contributor `
            ("+" + (fmt $cpWinner.Added) + " added, -" + (fmt $cpWinner.Deleted) + " deleted in {0} commit(s) — Ctrl+C, Ctrl+V, Ctrl+Profit" -f $cpWinner.Commits)
    }
}

# ── 18. The Phantom ── many commits but barely any lines changed
$phantomCandidates = $results | Where-Object { $_.Commits -ge 5 -and ($_.Added + $_.Deleted) -lt 50 }
if ($phantomCandidates) {
    $thePhantom = $phantomCandidates | Sort-Object Commits -Descending | Select-Object -First 1
    if ($thePhantom) {
        Write-Achievement "<?>" "The Phantom" $thePhantom.Contributor `
            ("{0} commits but only {1} lines touched — Commits whitespace for fun?" -f $thePhantom.Commits, ($thePhantom.Added + $thePhantom.Deleted))
    }
}

# ── 19. The Night Owl / Early Bird ── based on most active day being weekend
# (This one is a fun fallback if weekend commits are high)
$totalWeekendAll = ($authorWeekendCommits.Values | Measure-Object -Sum).Sum
$totalWeekdayAll = $totalCommits - $totalWeekendAll
if ($totalWeekendAll -gt 0 -and $totalCommits -gt 0) {
    $weekendPct = [Math]::Round($totalWeekendAll / $totalCommits * 100, 1)
    if ($weekendPct -gt 30) {
        Write-Achievement "zzZ" "Team: No Chill" "Everyone" `
            ("{0}% of all commits on weekends — HR would like a word" -f $weekendPct)
    }
}

# ── 20. The Refactorer ── high churn (added + deleted) but low net
$refactorCandidates = $results | Where-Object { ($_.Added + $_.Deleted) -ge 500 -and [Math]::Abs($_.Net) -lt ($_.Added * 0.2) }
if ($refactorCandidates) {
    $theRefactorer = $refactorCandidates | Sort-Object { $_.Added + $_.Deleted } -Descending | Select-Object -First 1
    if ($theRefactorer) {
        $churn = $theRefactorer.Added + $theRefactorer.Deleted
        Write-Achievement "<~>" "The Refactorer" $theRefactorer.Contributor `
            ("{0} lines churned, net {1} — Moves code around like furniture on a Saturday" -f (fmt $churn), (fmt $theRefactorer.Net))
    }
}

# ── 21. The Hermit ── only touches 1 module/folder
$authorModules = @{}
foreach ($r in $results) {
    $mods = @($r.Files | ForEach-Object {
        $clean = ($_ -replace '\{[^}]*=> *', '' -replace '\}', '').Trim()
        $parts = $clean -split '[\\/]'
        if ($parts.Count -gt 1 -and $parts[0] -notmatch '\.') { $parts[0] }
    } | Where-Object { $_ } | Sort-Object -Unique)
    $authorModules[$r.Contributor] = $mods
}
$hermitCandidates = $results | Where-Object { $authorModules[$_.Contributor].Count -eq 1 -and $_.Commits -ge 3 }
if ($hermitCandidates) {
    $theHermit = $hermitCandidates | Sort-Object Commits -Descending | Select-Object -First 1
    if ($theHermit) {
        Write-Achievement "[H]" "The Hermit" $theHermit.Contributor `
            ("Only touches '$($authorModules[$theHermit.Contributor][0])' — This is my house, nobody else comes in")
    }
}

# ── 22. The Globetrotter ── touches the most different modules
$globetrotter = $results | Where-Object { $authorModules[$_.Contributor].Count -ge 1 } |
    Sort-Object { $authorModules[$_.Contributor].Count } -Descending | Select-Object -First 1
if ($globetrotter -and $authorModules[$globetrotter.Contributor].Count -ge 5) {
    Write-Achievement "{G}" "The Globetrotter" $globetrotter.Contributor `
        ("{0} modules — Has a commit in every folder like a tourist leaving selfies" -f $authorModules[$globetrotter.Contributor].Count)
}

# ── 23. The Closer ── most deletions with positive net (cleans AND ships)
$closerCandidates = $results | Where-Object { $_.Deleted -ge 200 -and $_.Net -gt 0 }
if ($closerCandidates) {
    $theCloser = $closerCandidates | Sort-Object Deleted -Descending | Select-Object -First 1
    if ($theCloser) {
        Write-Achievement "[X]" "The Closer" $theCloser.Contributor `
            ("-" + (fmt $theCloser.Deleted) + " deleted but still net positive — Cleans up AND ships features")
    }
}

# ── 24. The Sprinter ── all commits within a very short date range (max 3 days) but lots of output
foreach ($r in $results) {
    if ($r.Commits -ge 5) {
        $sortedDates = @($r.Dates | Sort-Object -Unique)
        if ($sortedDates.Count -ge 2) {
            $span = ((Get-Date $sortedDates[-1]) - (Get-Date $sortedDates[0])).Days
            if ($span -le 3 -and $r.Added -ge 300) {
                Write-Achievement "/!\" "The Sprinter" $r.Contributor `
                    ("{0} commits, {1}+ lines in just {2} day(s) — Hackathon energy detected" -f $r.Commits, (fmt $r.Added), $span)
                break
            }
        }
    }
}

# ── 25. Mr./Ms. Monday ── most commits on Mondays
$authorMondayCommits = @{}
foreach ($r in $results) {
    $mondays = 0
    foreach ($d in $r.Dates) {
        try { if ((Get-Date $d).DayOfWeek -eq 'Monday') { $mondays++ } } catch {}
    }
    $authorMondayCommits[$r.Contributor] = $mondays
}
$mondayKing = $results | Sort-Object { $authorMondayCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($mondayKing -and $authorMondayCommits[$mondayKing.Contributor] -ge 3) {
    Write-Achievement "[M]" "Monday Motivator" $mondayKing.Contributor `
        ("{0} Monday commits — Actually likes Mondays. Psychopath confirmed." -f $authorMondayCommits[$mondayKing.Contributor])
}

# ── 26. TGIF Coder ── most commits on Fridays
$authorFridayCommits = @{}
foreach ($r in $results) {
    $fridays = 0
    foreach ($d in $r.Dates) {
        try { if ((Get-Date $d).DayOfWeek -eq 'Friday') { $fridays++ } } catch {}
    }
    $authorFridayCommits[$r.Contributor] = $fridays
}
$fridayKing = $results | Sort-Object { $authorFridayCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($fridayKing -and $authorFridayCommits[$fridayKing.Contributor] -ge 3) {
    Write-Achievement "[F]" "TGIF Deployer" $fridayKing.Contributor `
        ("{0} Friday commits — Deploying on Friday? Living dangerously." -f $authorFridayCommits[$fridayKing.Contributor])
}

# ── 27. The Deleter ── deleted more than added (net negative), min 200 deleted
$pureDeleters = $results | Where-Object { $_.Net -lt 0 -and $_.Deleted -ge 200 } | Sort-Object Net | Select-Object -First 1
if ($pureDeleters) {
    $delPct = [Math]::Round($pureDeleters.Deleted / [Math]::Max(1, $pureDeleters.Added + $pureDeleters.Deleted) * 100, 0)
    Write-Achievement "DEL" "The Demolisher" $pureDeleters.Contributor `
        ("{0}% of touches are deletions — Professional codebase weight loss coach" -f $delPct)
}

# ── 28. The Steady Eddie ── most consistent (lowest variance in commits/day across active days)
$steadyCandidates = $results | Where-Object { $authorActiveDays[$_.Contributor] -ge 5 }
if ($steadyCandidates -and @($steadyCandidates).Count -ge 2) {
    $steadyStats = @{}
    foreach ($r in $steadyCandidates) {
        $dayCommitCounts = @($r.Dates | Group-Object | ForEach-Object { $_.Count })
        if ($dayCommitCounts.Count -ge 2) {
            $avg = ($dayCommitCounts | Measure-Object -Average).Average
            $variance = ($dayCommitCounts | ForEach-Object { [Math]::Pow($_ - $avg, 2) } | Measure-Object -Average).Average
            $steadyStats[$r.Contributor] = [Math]::Round([Math]::Sqrt($variance), 2)
        }
    }
    if ($steadyStats.Count -gt 0) {
        $steadyWinner = $steadyStats.GetEnumerator() | Sort-Object Value | Select-Object -First 1
        Write-Achievement "=.=" "Steady Eddie" $steadyWinner.Key `
            ("StdDev {0} commits/day — Consistent like a metronome" -f $steadyWinner.Value)
    }
}

# ── 29. The Bus Factor ── contributor with highest % of total added lines
$busFactor = $results | Sort-Object Added -Descending | Select-Object -First 1
if ($busFactor -and $totalAdded -gt 0) {
    $busPct = [Math]::Round(100 * $busFactor.Added / $totalAdded, 1)
    if ($busPct -ge 40) {
        Write-Achievement "BUS" "Bus Factor Risk" $busFactor.Contributor `
            ("{0}% of all code — If this person leaves, we're all doomed" -f $busPct)
    }
}

# ── 30. The Duo ── two contributors who have both touched the most same files
if ($results.Count -ge 2) {
    $bestOverlap = 0; $bestPair = @("","")
    $authorFilesets = @{}
    foreach ($r in $results) {
        $authorFilesets[$r.Contributor] = [System.Collections.Generic.HashSet[string]]::new(
            [string[]]@($r.Files | Sort-Object -Unique)
        )
    }
    $contribList = @($results | ForEach-Object { $_.Contributor })
    for ($i = 0; $i -lt $contribList.Count; $i++) {
        for ($j = $i + 1; $j -lt $contribList.Count; $j++) {
            $a = $contribList[$i]; $b = $contribList[$j]
            $setA = $authorFilesets[$a]; $setB = $authorFilesets[$b]
            $overlap = 0
            foreach ($f in $setA) { if ($setB.Contains($f)) { $overlap++ } }
            if ($overlap -gt $bestOverlap) {
                $bestOverlap = $overlap; $bestPair = @($a, $b)
            }
        }
    }
    if ($bestOverlap -ge 5) {
        Write-Achievement "<3>" "The Dynamic Duo" ($bestPair -join " & ") `
            ("{0} shared files — Either pair-programming or stepping on each other's toes" -f $bestOverlap)
    }
}

# ── 31. The Hoarder ── added way more than deleted, ratio >= 5:1 (min 500 added)
$hoarderCandidates = $results | Where-Object { $_.Added -ge 500 -and $_.Deleted -gt 0 }
if ($hoarderCandidates) {
    $theHoarder = $hoarderCandidates | Sort-Object { $_.Added / [Math]::Max(1, $_.Deleted) } -Descending | Select-Object -First 1
    if ($theHoarder) {
        $hoardRatio = [Math]::Round($theHoarder.Added / [Math]::Max(1, $theHoarder.Deleted), 1)
        if ($hoardRatio -ge 5) {
            Write-Achievement "[+]" "The Hoarder" $theHoarder.Contributor `
                ("{0}:1 add/del ratio — Never deletes anything. 'I might need that later!'" -f $hoardRatio)
        }
    }
}

# ── 32. README Warrior ── most changes in README/docs files
$authorReadmeLines = @{}
foreach ($r in $results) {
    $readmeLines = 0
    # re-parse from rawLines to count README touches per author - use Files list
    foreach ($f in $r.Files) {
        $fLower = $f.ToLower()
        if ($fLower -match 'readme' -or $fLower -match '\.md$' -or $fLower -match 'docs[\\/]') {
            $readmeLines++
        }
    }
    $authorReadmeLines[$r.Contributor] = $readmeLines
}
$readmeKing = $results | Sort-Object { $authorReadmeLines[$_.Contributor] } -Descending | Select-Object -First 1
if ($readmeKing -and $authorReadmeLines[$readmeKing.Contributor] -ge 5) {
    Write-Achievement "DOC" "README Warrior" $readmeKing.Contributor `
        ("{0} doc file touches — The hero we don't deserve but desperately need" -f $authorReadmeLines[$readmeKing.Contributor])
}

# ── 33. The Test Writer ── most changes in test files
$authorTestLines = @{}
foreach ($r in $results) {
    $testTouches = 0
    foreach ($f in $r.Files) {
        $fLower = $f.ToLower()
        if ($fLower -match '\.test\.' -or $fLower -match '\.spec\.' -or $fLower -match 'tests[\\/]' -or $fLower -match '__tests__') {
            $testTouches++
        }
    }
    $authorTestLines[$r.Contributor] = $testTouches
}
$testKing = $results | Sort-Object { $authorTestLines[$_.Contributor] } -Descending | Select-Object -First 1
if ($testKing -and $authorTestLines[$testKing.Contributor] -ge 5) {
    Write-Achievement "TST" "Test Enthusiast" $testKing.Contributor `
        ("{0} test file touches — 'Works on my machine' is not a test strategy" -f $authorTestLines[$testKing.Contributor])
}

# ── 34. Config Wizard ── most changes in config/dotfiles
$authorConfigTouches = @{}
foreach ($r in $results) {
    $cfgCount = 0
    foreach ($f in $r.Files) {
        $fLower = $f.ToLower()
        if ($fLower -match '\.config' -or $fLower -match '\.env' -or $fLower -match '\.yml$' -or
            $fLower -match '\.yaml$' -or $fLower -match '\.json$' -or $fLower -match '\.toml$' -or
            $fLower -match 'dockerfile' -or $fLower -match '\.properties$' -or $fLower -match '\.ini$') {
            $cfgCount++
        }
    }
    $authorConfigTouches[$r.Contributor] = $cfgCount
}
$configWizard = $results | Sort-Object { $authorConfigTouches[$_.Contributor] } -Descending | Select-Object -First 1
if ($configWizard -and $authorConfigTouches[$configWizard.Contributor] -ge 8) {
    Write-Achievement "CFG" "Config Wizard" $configWizard.Contributor `
        ("{0} config file touches — Speaks fluent YAML, JSON, and .env" -f $authorConfigTouches[$configWizard.Contributor])
}

# ── 35. The Night Owl ── most commits on Sundays specifically
$authorSundayCommits = @{}
foreach ($r in $results) {
    $sundays = 0
    foreach ($d in $r.Dates) {
        try { if ((Get-Date $d).DayOfWeek -eq 'Sunday') { $sundays++ } } catch {}
    }
    $authorSundayCommits[$r.Contributor] = $sundays
}
$sundayKing = $results | Sort-Object { $authorSundayCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($sundayKing -and $authorSundayCommits[$sundayKing.Contributor] -ge 3) {
    Write-Achievement "zzz" "The Night Owl" $sundayKing.Contributor `
        ("{0} Sunday commits — God rested on Sunday. This person didn't." -f $authorSundayCommits[$sundayKing.Contributor])
}

# ── 36. The Carry ── one person has more commits than the rest of the team combined
if ($results.Count -ge 3) {
    $sorted = @($results | Sort-Object Commits -Descending)
    $topGuy = $sorted[0]
    $restSum = ($sorted | Select-Object -Skip 1 | Measure-Object Commits -Sum).Sum
    if ($topGuy.Commits -gt $restSum) {
        $factor = [Math]::Round($topGuy.Commits / [Math]::Max(1, $restSum), 1)
        Write-Achievement "GG!" "The Carry" $topGuy.Contributor `
            ("{0} commits vs {1} from everyone else — 1v{2}, carrying the whole team" -f $topGuy.Commits, $restSum, ($results.Count - 1))
    }
}

# ── 37. The Shadow ── 2nd place by commits, always behind #1
if ($results.Count -ge 2) {
    $byCommits = @($results | Sort-Object Commits -Descending)
    $first = $byCommits[0]; $second = $byCommits[1]
    if ($second.Commits -ge 10) {
        $gap = $first.Commits - $second.Commits
        Write-Achievement "v2v" "The Shadow" $second.Contributor `
            ("{0} commits, {1} behind #1 — Always the bridesmaid, never the bride" -f $second.Commits, $gap)
    }
}

# ── 38. The Specialist ── highest lines-per-file ratio (deep work on few files)
$authorLinesPerFile = @{}
foreach ($r in $results) {
    $uniqueFiles = @($r.Files | Sort-Object -Unique).Count
    if ($uniqueFiles -gt 0) {
        $authorLinesPerFile[$r.Contributor] = [Math]::Round($r.Added / $uniqueFiles, 1)
    } else {
        $authorLinesPerFile[$r.Contributor] = 0
    }
}
$specialist = $results | Where-Object { $_.Commits -ge 3 } |
    Sort-Object { $authorLinesPerFile[$_.Contributor] } -Descending | Select-Object -First 1
if ($specialist -and $authorLinesPerFile[$specialist.Contributor] -ge 30) {
    Write-Achievement "|=|" "The Specialist" $specialist.Contributor `
        ("{0} avg lines/file — Goes deep, not wide. Quality over quantity." -f $authorLinesPerFile[$specialist.Contributor])
}

# ── 39. The Sidekick ── contributor with most shared files with the #1 committer
if ($results.Count -ge 2) {
    $topCommitter = ($results | Sort-Object Commits -Descending | Select-Object -First 1).Contributor
    $topFiles = [System.Collections.Generic.HashSet[string]]::new([string[]]@($authorFiles[$topCommitter] | Sort-Object -Unique))
    $bestSidekick = ""; $bestShared = 0
    foreach ($r in $results) {
        if ($r.Contributor -eq $topCommitter) { continue }
        $shared = 0
        foreach ($f in @($r.Files | Sort-Object -Unique)) { if ($topFiles.Contains($f)) { $shared++ } }
        if ($shared -gt $bestShared) { $bestShared = $shared; $bestSidekick = $r.Contributor }
    }
    if ($bestSidekick -and $bestShared -ge 3) {
        Write-Achievement "<+>" "The Sidekick" $bestSidekick `
            ("{0} files shared with {1} — Robin to their Batman" -f $bestShared, $topCommitter)
    }
}

# ── 40. The Latecomers ── contributors whose first commit date is in the latest 25% of the project timeline
if ($allDates -and $allDates.Count -gt 0) {
    $sortedAllDates = @($allDates | Sort-Object -Unique)
    if ($sortedAllDates.Count -ge 4) {
        $projectStart = Get-Date $sortedAllDates[0]
        $projectEnd = Get-Date $sortedAllDates[-1]
        $totalSpan = ($projectEnd - $projectStart).Days
        $lateThreshold = $projectStart.AddDays($totalSpan * 0.75)

        $latecomers = @()
        foreach ($r in $results) {
            $firstDate = Get-Date (@($r.Dates | Sort-Object)[0])
            if ($firstDate -ge $lateThreshold -and $r.Added -ge 100) {
                $latecomers += $r.Contributor
            }
        }
        if ($latecomers.Count -gt 0) {
            Write-Achievement "NEW" "Late to the Party" ($latecomers -join ", ") `
                ("Joined in the last 25% of the timeline — Better late than never!")
        }
    }
}

# ── 41. The Veteran ── contributor active on the most different calendar weeks
$authorWeeks = @{}
foreach ($r in $results) {
    $weeks = @($r.Dates | ForEach-Object {
        try { $d = Get-Date $_; "{0}-W{1:D2}" -f $d.Year, [int][Math]::Ceiling($d.DayOfYear / 7) } catch {}
    } | Where-Object { $_ } | Sort-Object -Unique)
    $authorWeeks[$r.Contributor] = $weeks.Count
}
$veteran = $results | Sort-Object { $authorWeeks[$_.Contributor] } -Descending | Select-Object -First 1
if ($veteran -and $authorWeeks[$veteran.Contributor] -ge 4) {
    Write-Achievement "VET" "The Veteran" $veteran.Contributor `
        ("{0} different calendar weeks active — The backbone of this project" -f $authorWeeks[$veteran.Contributor])
}

# ── 42. The Burst Committer ── highest single-day commit count
$authorMaxDayCommits = @{}
foreach ($r in $results) {
    $dayCounts = @($r.Dates | Group-Object | ForEach-Object { $_.Count })
    if ($dayCounts.Count -gt 0) {
        $authorMaxDayCommits[$r.Contributor] = ($dayCounts | Measure-Object -Maximum).Maximum
    } else {
        $authorMaxDayCommits[$r.Contributor] = 0
    }
}
$burstKing = $results | Sort-Object { $authorMaxDayCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($burstKing -and $authorMaxDayCommits[$burstKing.Contributor] -ge 5) {
    Write-Achievement "!!!" "The Burst Committer" $burstKing.Contributor `
        ("{0} commits in a single day — 'git commit' is basically a nervous tic" -f $authorMaxDayCommits[$burstKing.Contributor])
}

# ── 43. Silent Contributor ── fewest commits but still meaningful lines (min 100 added)
$silentCandidates = $results | Where-Object { $_.Added -ge 100 } | Sort-Object Commits | Select-Object -First 1
if ($silentCandidates -and $results.Count -ge 3) {
    $avgCommits = [Math]::Round(($results | Measure-Object Commits -Average).Average, 0)
    if ($silentCandidates.Commits -lt ($avgCommits * 0.5)) {
        Write-Achievement "..." "Silent Contributor" $silentCandidates.Contributor `
            ("{0} commits but {1}+ lines — Speaks softly but carries a big diff" -f $silentCandidates.Commits, (fmt $silentCandidates.Added))
    }
}

# ── 44. The Midweek Monster ── most commits Tue-Thu
$authorMidweekCommits = @{}
foreach ($r in $results) {
    $mid = 0
    foreach ($d in $r.Dates) {
        try {
            $dow = (Get-Date $d).DayOfWeek
            if ($dow -eq 'Tuesday' -or $dow -eq 'Wednesday' -or $dow -eq 'Thursday') { $mid++ }
        } catch {}
    }
    $authorMidweekCommits[$r.Contributor] = $mid
}
$midweekKing = $results | Sort-Object { $authorMidweekCommits[$_.Contributor] } -Descending | Select-Object -First 1
if ($midweekKing -and $authorMidweekCommits[$midweekKing.Contributor] -ge 5) {
    $midPct = [Math]::Round(100 * $authorMidweekCommits[$midweekKing.Contributor] / [Math]::Max(1, $midweekKing.Commits), 0)
    Write-Achievement "MID" "Midweek Monster" $midweekKing.Contributor `
        ("{0}% of commits Tue-Thu — Peak performance in peak hours" -f $midPct)
}

# ── 45. The Entropy Generator ── touches many files but adds few lines per file (scattered changes)
$entropyCandidates = $results | Where-Object { $authorUniqueFiles[$_.Contributor] -ge 20 -and $authorLinesPerFile[$_.Contributor] -lt 15 }
if ($entropyCandidates) {
    $entropyKing = $entropyCandidates | Sort-Object { $authorUniqueFiles[$_.Contributor] } -Descending | Select-Object -First 1
    if ($entropyKing) {
        Write-Achievement "~?~" "Entropy Generator" $entropyKing.Contributor `
            ("{0} files, only {1} lines/file — Sprinkles changes everywhere like confetti" -f $authorUniqueFiles[$entropyKing.Contributor], $authorLinesPerFile[$entropyKing.Contributor])
    }
}

# ── 46. The Iron Man ── active on both weekdays AND weekends (min 3 each)
$ironManCandidates = $results | Where-Object {
    $authorWeekendCommits[$_.Contributor] -ge 3 -and
    ($_.Commits - $authorWeekendCommits[$_.Contributor]) -ge 5
}
if ($ironManCandidates) {
    $ironMan = $ironManCandidates | Sort-Object Commits -Descending | Select-Object -First 1
    if ($ironMan) {
        $wkday = $ironMan.Commits - $authorWeekendCommits[$ironMan.Contributor]
        Write-Achievement "Fe!" "Iron Man" $ironMan.Contributor `
            ("{0} weekday + {1} weekend commits — Doesn't know the concept of 'days off'" -f $wkday, $authorWeekendCommits[$ironMan.Contributor])
    }
}

# ── 47. The Cleanup Crew ── deleted at least 30% of what they added (min 200 added)
$cleanupCandidates = $results | Where-Object { $_.Added -ge 200 -and $_.Deleted -ge ($_.Added * 0.3) } |
    Sort-Object { $_.Deleted / [Math]::Max(1, $_.Added) } -Descending
if ($cleanupCandidates) {
    $cleanupCrew = @($cleanupCandidates | Select-Object -First 3)
    if ($cleanupCrew.Count -ge 2) {
        $names = ($cleanupCrew | ForEach-Object { $_.Contributor }) -join ", "
        Write-Achievement "[-]" "The Cleanup Crew" $names `
            "Delete 30%+ of what they add — Keeping the codebase lean and mean"
    }
}

# ── 48. The Tenner ── exactly 10 commits (or closest round number, 20, 30, etc.)
$tenners = @($results | Where-Object { $_.Commits -gt 0 -and $_.Commits % 10 -eq 0 })
if ($tenners.Count -gt 0) {
    foreach ($t in $tenners) {
        Write-Achievement " X " "The Round Number" $t.Contributor `
            ("Exactly {0} commits — Oddly satisfying. OCD-approved commit history." -f $t.Commits)
    }
}

# ── 49. The Underdog ── lowest rank by commits but highest lines/commit ratio
if ($results.Count -ge 3) {
    $byCommitsAsc = @($results | Sort-Object Commits)
    $bottomHalf = @($byCommitsAsc | Select-Object -First ([Math]::Ceiling($results.Count / 2)))
    $underdog = $bottomHalf | Where-Object { $_.Commits -ge 2 } |
        Sort-Object { $authorAvgLinesPerCommit[$_.Contributor] } -Descending | Select-Object -First 1
    if ($underdog -and $authorAvgLinesPerCommit[$underdog.Contributor] -ge 50) {
        Write-Achievement "<^>" "The Underdog" $underdog.Contributor `
            ("Bottom half by commits but {0} lines/commit — Few commits, big impact" -f $authorAvgLinesPerCommit[$underdog.Contributor])
    }
}

# ── 50. The Locksmith ── most changes in auth/security/login related files
$authorSecurityTouches = @{}
foreach ($r in $results) {
    $secCount = 0
    foreach ($f in $r.Files) {
        $fLower = $f.ToLower()
        if ($fLower -match 'auth' -or $fLower -match 'login' -or $fLower -match 'security' -or
            $fLower -match 'password' -or $fLower -match 'token' -or $fLower -match 'crypt' -or
            $fLower -match 'permission' -or $fLower -match 'role' -or $fLower -match 'guard' -or
            $fLower -match 'session' -or $fLower -match 'jwt' -or $fLower -match 'oauth') {
            $secCount++
        }
    }
    $authorSecurityTouches[$r.Contributor] = $secCount
}
$locksmith = $results | Sort-Object { $authorSecurityTouches[$_.Contributor] } -Descending | Select-Object -First 1
if ($locksmith -and $authorSecurityTouches[$locksmith.Contributor] -ge 3) {
    Write-Achievement "KEY" "The Locksmith" $locksmith.Contributor `
        ("{0} auth/security file touches — Guardian of the gates" -f $authorSecurityTouches[$locksmith.Contributor])
}

Write-Host ""
Write-HR
cWL "  Achievements are auto-detected from git history. No feelings were harmed." DarkGray
Write-Host ""

# ─────────────────────────────────────────────
#  FOOTER
# ─────────────────────────────────────────────
Write-HR 72 ([string][char]0x2550)
cWL "  git-stats.ps1  |  All stats sourced from: git log --numstat" DarkGray
cWL "  Tip: run with -NoBars or -NoColor if your terminal has issues." DarkGray
Write-HR 72 ([string][char]0x2550)
Write-Host ""