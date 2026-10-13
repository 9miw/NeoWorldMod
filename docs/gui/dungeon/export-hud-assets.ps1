param(
    [Parameter(Mandatory = $true)][string]$Source,
    [string]$Destination = "$PSScriptRoot/../../../src/main/resources/assets/neoworld/textures/gui/dungeon/hud"
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$sheet = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Source).Path)
try {
    if ($sheet.Width -ne 1536 -or $sheet.Height -ne 1024) { throw 'Expected approved 1536x1024 HUD sheet.' }
    # Measured regions of the approved sheet, not inferred equal atlas cells.
    $parts = @(
        @{Name='frame'; X=40; Y=40; W=810; H=600; Width=440; Height=320},
        @{Name='background'; X=875; Y=80; W=625; H=530; Width=424; Height=290},
        @{Name='divider'; X=40; Y=665; W=840; H=65; Width=400; Height=20},
        @{Name='bar_track'; X=40; Y=735; W=840; H=90; Width=400; Height=36},
        @{Name='wave_fill'; X=59; Y=842; W=806; H=37; Width=384; Height=16; Fixed=$true},
        @{Name='boss_fill'; X=59; Y=908; W=806; H=37; Width=384; Height=16; Fixed=$true},
        @{Name='clock'; X=960; Y=665; W=255; H=255; Width=32; Height=32}
    )
    foreach ($part in $parts) {
        $left=$part.X+$part.W; $top=$part.Y+$part.H; $right=-1; $bottom=-1
        for ($y=$part.Y; $y -lt $part.Y+$part.H; $y++) {
            for ($x=$part.X; $x -lt $part.X+$part.W; $x++) {
                if ($sheet.GetPixel($x,$y).A -gt 8) {
                    $left=[Math]::Min($left,$x); $top=[Math]::Min($top,$y)
                    $right=[Math]::Max($right,$x); $bottom=[Math]::Max($bottom,$y)
                }
            }
        }
        if ($right -lt 0) { throw "Empty asset: $($part.Name)" }
        if ($part.Fixed) {
            $left=$part.X; $top=$part.Y; $right=$left+$part.W-1; $bottom=$top+$part.H-1
        }
        $output=[System.Drawing.Bitmap]::new($part.Width,$part.Height,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics=[System.Drawing.Graphics]::FromImage($output)
        try {
            $graphics.CompositingMode=[System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $target=[System.Drawing.Rectangle]::new(0,0,$part.Width,$part.Height)
            if ($part.Name -eq 'clock') { $target=[System.Drawing.Rectangle]::new(2,2,28,28) }
            $graphics.DrawImage($sheet,$target,$left,$top,$right-$left+1,$bottom-$top+1,[System.Drawing.GraphicsUnit]::Pixel)
            $output.Save((Join-Path $Destination "$($part.Name).png"),[System.Drawing.Imaging.ImageFormat]::Png)
            Write-Output "$($part.Name): source [$left,$top,$($right-$left+1),$($bottom-$top+1)] -> $($part.Width)x$($part.Height)"
        } finally { $graphics.Dispose(); $output.Dispose() }
    }
} finally { $sheet.Dispose() }
