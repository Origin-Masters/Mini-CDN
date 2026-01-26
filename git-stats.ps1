git log --numstat --pretty="%aN" |
ForEach-Object {
    if ($_ -match '^\d') {
        $parts = $_ -split "`t"
        [PSCustomObject]@{
            Author = $currentAuthor
            Added  = [int]$parts[0]
            Deleted= [int]$parts[1]
        }
    }
    elseif ($_ -ne "") {
        $currentAuthor = $_
    }
} |
Group-Object Author |
ForEach-Object {
    $added   = ($_.Group | Measure-Object Added   -Sum).Sum
    $deleted = ($_.Group | Measure-Object Deleted -Sum).Sum

    [PSCustomObject]@{
        Author  = $_.Name
        Added   = $added
        Deleted = $deleted
        Total   = $added - $deleted
    }
} |
Sort-Object Added -Descending |
Format-Table -AutoSize