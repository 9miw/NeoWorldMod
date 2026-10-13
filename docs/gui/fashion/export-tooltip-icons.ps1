param([switch]$Slots)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Resolve-Path "$PSScriptRoot/../../.."
$output = Join-Path $root 'src/main/resources/assets/neoworld/textures/gui/fashion/tooltip'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$sourceName = if ($Slots) { 'tooltip-slot-icons-source.png' } else { 'tooltip-icons-source.png' }
$source = [Drawing.Bitmap]::new("$PSScriptRoot/$sourceName")
$names = if ($Slots) { @('slot_head', 'slot_face', 'slot_back', 'slot_body', 'slot_legs', 'slot_feet') }
    else { @('fashion', 'back', 'right_click', 'wardrobe', 'unlock', 'consume', 'owned', 'info') }
$columns = if ($Slots) { 3 } else { 4 }
try {
    for ($i = 0; $i -lt $names.Count; $i++) {
        $left = [int][Math]::Floor(($i % $columns) * $source.Width / $columns)
        $top = [int][Math]::Floor([Math]::Floor($i / $columns) * $source.Height / 2)
        $right = [int][Math]::Floor((($i % $columns) + 1) * $source.Width / $columns)
        $bottom = [int][Math]::Floor(([Math]::Floor($i / $columns) + 1) * $source.Height / 2)
        $minX = $right; $minY = $bottom; $maxX = $left; $maxY = $top
        for ($y = $top; $y -lt $bottom; $y++) {
            for ($x = $left; $x -lt $right; $x++) {
                if ($source.GetPixel($x, $y).A -gt 32) {
                    $minX = [Math]::Min($minX, $x); $maxX = [Math]::Max($maxX, $x)
                    $minY = [Math]::Min($minY, $y); $maxY = [Math]::Max($maxY, $y)
                }
            }
        }
        if ($maxX -lt $minX) { throw "Empty icon: $($names[$i])" }
        $w = $maxX - $minX + 1; $h = $maxY - $minY + 1
        $scale = 28.0 / [Math]::Max($w, $h)
        $dw = [int][Math]::Round($w * $scale); $dh = [int][Math]::Round($h * $scale)
        $bitmap = [Drawing.Bitmap]::new(32, 32)
        $g = [Drawing.Graphics]::FromImage($bitmap)
        try {
            $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $g.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
            $g.DrawImage($source, [Drawing.Rectangle]::new([int]((32-$dw)/2), [int]((32-$dh)/2), $dw, $dh),
                $minX, $minY, $w, $h, [Drawing.GraphicsUnit]::Pixel)
            $bitmap.Save((Join-Path $output ($names[$i] + '.png')), [Drawing.Imaging.ImageFormat]::Png)
        } finally { $g.Dispose(); $bitmap.Dispose() }
    }
} finally { $source.Dispose() }
