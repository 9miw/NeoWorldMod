$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$textureDirectory = Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/fashion'
$images = @{}
foreach ($name in @('shell', 'title_plaque', 'close_normal')) {
    $images[$name] = [System.Drawing.Bitmap]::new((Join-Path $textureDirectory "fashion_$name.png"))
}
$left = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'fashion-category-assets-preview.png'))
$right = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'fashion-wardrobe-assets-preview.png'))
$reference = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'fashion-wardrobe-approved-mockup.png'))
$canvas = [System.Drawing.Bitmap]::new(1290, 708)
$graphics = [System.Drawing.Graphics]::FromImage($canvas)
$font = [System.Drawing.Font]::new('Tahoma', 25, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$white = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#E7ECEF'))
$background = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#191F23'))
try {
    $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $graphics.FillRectangle($background, 18, 60, 1254, 630)
    $widths = @(18, 1254, 18)
    $heights = @(18, 630, 18)
    $sw = @(24, 812, 24)
    $sh = @(24, 396, 24)
    $ty = 42
    $v = 0
    for ($row = 0; $row -lt 3; $row++) {
        $tx = 0
        $u = 0
        for ($col = 0; $col -lt 3; $col++) {
            if ($row -ne 1 -or $col -ne 1) {
                $graphics.DrawImage($images['shell'], [System.Drawing.Rectangle]::new($tx,$ty,$widths[$col],$heights[$row]),
                    $u,$v,$sw[$col],$sh[$row],[System.Drawing.GraphicsUnit]::Pixel)
            }
            $tx += $widths[$col]
            $u += $sw[$col]
        }
        $ty += $heights[$row]
        $v += $sh[$row]
    }
    $graphics.DrawImage($images['title_plaque'], [System.Drawing.Rectangle]::new(462,0,366,84))
    $title = 'คลังแฟชั่น'
    $size = $graphics.MeasureString($title,$font)
    $graphics.DrawString($title,$font,$white,645-$size.Width/2,42-$size.Height/2)
    $graphics.DrawImage($images['close_normal'], [System.Drawing.Rectangle]::new(1218,45,48,48))
    $graphics.DrawString('X',$font,$white,1232,53)
    $graphics.DrawImage($left,[System.Drawing.Rectangle]::new(24,102,336,570))
    $graphics.DrawImage($right,[System.Drawing.Rectangle]::new(732,102,534,570))
    # Reference character is illustrative; the game renders the live player separately.
    $graphics.DrawImage($reference,[System.Drawing.Rectangle]::new(408,168,306,438),
        534,207,403,577,[System.Drawing.GraphicsUnit]::Pixel)
    $graphics.DrawString('ตัวละคร',$font,$white,440,114)
    $graphics.DrawString('NeoPlayer',$font,$white,492,621)
    $canvas.Save((Join-Path $PSScriptRoot 'fashion-shell-assembled-preview.png'),[System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($resource in @($graphics,$canvas,$left,$right,$reference,$font,$white,$background)) { $resource.Dispose() }
    foreach ($bitmap in $images.Values) { $bitmap.Dispose() }
}
