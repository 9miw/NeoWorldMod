param([switch]$Slots)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Resolve-Path "$PSScriptRoot/../../.."
$names = if ($Slots) { @('slot_head', 'slot_face', 'slot_back', 'slot_body', 'slot_legs', 'slot_feet') }
    else { @('fashion', 'back', 'right_click', 'wardrobe', 'unlock', 'consume', 'owned', 'info') }
$preview = [Drawing.Bitmap]::new(($names.Count * 80), 200)
$g = [Drawing.Graphics]::FromImage($preview)
$font = [Drawing.Font]::new('Segoe UI', 9)
try {
    $g.Clear([Drawing.Color]::FromArgb(25, 31, 35))
    $g.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
    for ($i = 0; $i -lt $names.Count; $i++) {
        $icon = [Drawing.Bitmap]::new((Join-Path $root "src/main/resources/assets/neoworld/textures/gui/fashion/tooltip/$($names[$i]).png"))
        try {
            $x = $i * 80
            $g.DrawImage($icon, [Drawing.Rectangle]::new($x + 8, 12, 64, 64))
            $g.DrawImage($icon, [Drawing.Rectangle]::new($x + 24, 90, 32, 32))
            $g.DrawImage($icon, [Drawing.Rectangle]::new($x + 34, 138, 10, 10))
            $g.DrawString($names[$i], $font, [Drawing.Brushes]::White, $x + 3, 168)
        } finally { $icon.Dispose() }
    }
    $previewName = if ($Slots) { 'tooltip-slot-icons-preview.png' } else { 'tooltip-icons-preview.png' }
    $preview.Save("$PSScriptRoot/$previewName", [Drawing.Imaging.ImageFormat]::Png)
} finally { $font.Dispose(); $g.Dispose(); $preview.Dispose() }
