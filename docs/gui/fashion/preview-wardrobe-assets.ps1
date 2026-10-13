param([string]$TextureDirectory = "$PSScriptRoot/../../../src/main/resources/assets/neoworld/textures/gui/fashion")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$images = @{}
foreach ($name in @('wardrobe_panel', 'search_normal', 'search_icon', 'wardrobe_detail',
        'slot_normal', 'slot_selected', 'slot_equipped', 'slot_hover')) {
    $images[$name] = [System.Drawing.Bitmap]::new((Join-Path $TextureDirectory "fashion_$name.png"))
}
$mockup = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'fashion-wardrobe-approved-mockup.png'))
$canvas = [System.Drawing.Bitmap]::new(712, 760)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$font = [System.Drawing.Font]::new('Tahoma', 27, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$heading = [System.Drawing.Font]::new('Tahoma', 30, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#ABB4BA'))
$white = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#E7ECEF'))
$teal = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#2FB5A7'))

function Draw-Image([string]$Name, [int]$X, [int]$Y, [int]$Width, [int]$Height) {
    $graphics.DrawImage($images[$Name], [System.Drawing.Rectangle]::new($X, $Y, $Width, $Height))
}
function Draw-Button([string]$Text, [int]$X, [int]$Y, [int]$Width, [int]$Height, [bool]$Primary, [bool]$Enabled = $true) {
    $brightness = if ($Enabled) { 1.0 } else { 0.65 }
    $matrix = [System.Drawing.Imaging.ColorMatrix]::new()
    $matrix.Matrix00 = $(if ($Primary) { 0.3 } else { 1.0 }) * $brightness
    $matrix.Matrix11 = $(if ($Primary) { 0.82 } else { 1.0 }) * $brightness
    $matrix.Matrix22 = $(if ($Primary) { 0.86 } else { 1.0 }) * $brightness
    $attributes = [System.Drawing.Imaging.ImageAttributes]::new()
    $attributes.SetColorMatrix($matrix)
    try {
        $widths = @(12, ($Width - 24), 12)
        $heights = @(12, ($Height - 24), 12)
        $sourceWidths = @(8, 496, 8)
        $sourceHeights = @(8, 32, 8)
        $targetY = $Y
        $sourceY = 0
        for ($row = 0; $row -lt 3; $row++) {
            $targetX = $X
            $sourceX = 0
            for ($column = 0; $column -lt 3; $column++) {
                $graphics.DrawImage($images['search_normal'],
                    [System.Drawing.Rectangle]::new($targetX, $targetY, $widths[$column], $heights[$row]),
                    $sourceX, $sourceY, $sourceWidths[$column], $sourceHeights[$row],
                    [System.Drawing.GraphicsUnit]::Pixel, $attributes)
                $targetX += $widths[$column]
                $sourceX += $sourceWidths[$column]
            }
            $targetY += $heights[$row]
            $sourceY += $sourceHeights[$row]
        }
    } finally { $attributes.Dispose() }
    $textSize = $graphics.MeasureString($Text, $font)
    $brush = if ($Enabled) { $white } else { $muted }
    $graphics.DrawString($Text, $font, $brush, $X + ($Width - $textSize.Width) / 2, $Y + ($Height - $textSize.Height) / 2)
}

try {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    Draw-Image 'wardrobe_panel' 0 0 712 760
    $graphics.DrawString('คลังแฟชั่น', $heading, $white, 24, 14)
    Draw-Image 'search_normal' 24 80 664 56
    Draw-Image 'search_icon' 40 88 40 40
    $graphics.DrawString('ค้นหา...', $font, $muted, 96, 90)
    for ($index = 0; $index -lt 15; $index++) {
        $tileX = 60 + ($index % 5) * 120
        $tileY = 148 + [Math]::Floor($index / 5) * 120
        $state = if ($index -eq 0) { 'slot_selected' } else { 'slot_normal' }
        Draw-Image $state $tileX $tileY 112 112
        if ($index -eq 0) {
            $graphics.DrawImage($images['slot_equipped'], [System.Drawing.Rectangle]::new($tileX + 8, $tileY + 92, 96, 12),
                8, 100, 112, 18, [System.Drawing.GraphicsUnit]::Pixel)
        }
        if ($index -lt 9) {
            $sourceX = 984 + ($index % 5) * 112
            $sourceY = if ($index -lt 5) { 291 } else { 411 }
            $graphics.DrawImage($mockup, [System.Drawing.Rectangle]::new($tileX + 24, $tileY + 20, 64, 64),
                $sourceX, $sourceY, 81, 61, [System.Drawing.GraphicsUnit]::Pixel)
        }
    }
    Draw-Button '<' 184 512 80 64 $false $false
    Draw-Button '>' 448 512 80 64 $false $false
    $graphics.DrawString('1 / 1', $font, $muted, 317, 526)
    Draw-Image 'wardrobe_detail' 24 588 664 96
    Draw-Image 'slot_normal' 36 596 80 80
    $graphics.DrawImage($mockup, [System.Drawing.Rectangle]::new(44, 604, 64, 64),
        984, 291, 81, 61, [System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawString('หมวกนักเดินทาง', $font, $white, 132, 592)
    $graphics.DrawString('กำลังใส่', $font, $teal, 132, 637)
    $graphics.DrawString('9', $font, $muted, 650, 637)
    Draw-Button 'ใส่อยู่' 24 692 432 64 $true $false
    Draw-Button 'ถอด' 472 692 216 64 $false
    $canvas.Save((Join-Path $PSScriptRoot 'fashion-wardrobe-assets-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($resource in @($graphics, $canvas, $mockup, $font, $heading, $muted, $white, $teal)) { $resource.Dispose() }
    foreach ($bitmap in $images.Values) { $bitmap.Dispose() }
}
