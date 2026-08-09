# show-folder-tree.ps1
# Displays a tree-like structure of all files and folders in a given directory
#
# Usage: .\scripts\show-folder-tree.ps1 -Path "C:\Path\To\Directory" [-ShowHidden]
#
# Run:
#   .\scripts\show-folder-tree.ps1 -Path "C:\Users\lhacenmed\AndroidStudioProjects\YTDLnis\"

param (
    [Parameter(Mandatory = $false)]
    [string]$Path = ".",

    [Parameter(Mandatory = $false)]
    [switch]$ShowHidden
)

# Define box-drawing characters via Unicode code points
# so the script file stays pure ASCII and encoding never matters
$BRANCH_MID  = [char]0x251C + [char]0x2500 + [char]0x2500 + " "  # ├──
$BRANCH_LAST = [char]0x2514 + [char]0x2500 + [char]0x2500 + " "  # └──
$PIPE        = [char]0x2502 + "   "                               # │
$BLANK       = "    "

# --- Ignore lists ------------------------------------------------------------
$IGNORE = @{
    Dirs  = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            'node_modules', '.git', '.svn', '.hg',
            'dist', 'build', 'out', 'bin', 'obj',
            '.next', '.nuxt', '.vite', '.turbo',
            '__pycache__', '.pytest_cache', '.mypy_cache',
            'venv', '.venv', 'env', '.env',
            'coverage', '.nyc_output',
            '.idea', '.vscode', '.vs',
            'vendor', 'packages', 'bower_components',
            'Migrations', 'migrations',
            'logs', 'tmp', 'temp', '.cache', '.agents', '.claude', '.expo',
            '.gradle', '.kotlin', 'release', '.cxx', 'androidTest', 'test', '.oldgit'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
    Exts  = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            '.user'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
    Files = [System.Collections.Generic.HashSet[string]]::new(
        [string[]]@(
            'Cargo.lock'
        ),
        [StringComparer]::OrdinalIgnoreCase
    )
}

function Show-Tree {
    param (
        [string]$FolderPath,
        [string]$Indent = ""
    )

    $getChildParams = @{
        LiteralPath = $FolderPath
        Force       = $ShowHidden.IsPresent
        ErrorAction = "SilentlyContinue"
    }

    # Folders first, then files — both sorted alphabetically
    $items = Get-ChildItem @getChildParams |
            Sort-Object @{ Expression = { $_.PSIsContainer }; Descending = $true }, Name

    # Filter ignored items before rendering — avoids wasted index/branch logic
    $items = @($items | Where-Object {
        if ($_.PSIsContainer) {
            -not $IGNORE.Dirs.Contains($_.Name)
        } else {
            if ($IGNORE.Files.Contains($_.Name)) { return $false }
            $ext = [IO.Path]::GetExtension($_.Name)
            -not ($ext -and $IGNORE.Exts.Contains($ext))
        }
    })

    for ($i = 0; $i -lt $items.Count; $i++) {
        $item   = $items[$i]
        $isLast = ($i -eq $items.Count - 1)

        $branch      = if ($isLast) { $BRANCH_LAST } else { $BRANCH_MID }
        $childIndent = if ($isLast) { "$Indent$BLANK" } else { "$Indent$PIPE" }

        if ($item.PSIsContainer) {
            Write-Host "$Indent$branch" -NoNewline
            Write-Host "$($item.Name)/" -ForegroundColor Cyan
            Show-Tree -FolderPath $item.FullName -Indent $childIndent
        } else {
            Write-Host "$Indent$branch" -NoNewline
            Write-Host $item.Name -ForegroundColor White
        }
    }
}

# --- Entry point -------------------------------------------------------------

$resolvedPath = Resolve-Path -LiteralPath $Path -ErrorAction SilentlyContinue

if (-not $resolvedPath) {
    Write-Error "Path not found: $Path"
    exit 1
}

$rootName = Split-Path -Leaf $resolvedPath.Path
Write-Host "$rootName/" -ForegroundColor Yellow

Show-Tree -FolderPath $resolvedPath.Path
