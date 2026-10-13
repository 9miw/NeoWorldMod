param(
    [string]$PanelSource = "$PSScriptRoot/category-panel-source.png",
    [string]$RowSource = "$PSScriptRoot/category-rows-source.png",
    [string]$Destination = "$PSScriptRoot/../../../src/main/resources/assets/neoworld/textures/gui/fashion"
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
New-Item -ItemType Directory -Force -Path $Destination | Out-Null

function Get-AlphaBounds([System.Drawing.Bitmap]$Bitmap, [int]$StartY, [int]$EndY) {
    $minX = $Bitmap.Width
    $maxX = -1
    $minY = $Bitmap.Height
    $maxY = -1
    for ($py = $StartY; $py -le $EndY; $py++) {
        for ($px = 0; $px -lt $Bitmap.Width; $px++) {
            if ($Bitmap.GetPixel($px, $py).A -lt 128) { continue }
            $minX = [Math]::Min($minX, $px)
            $maxX = [Math]::Max($maxX, $px)
            $minY = [Math]::Min($minY, $py)
            $maxY = [Math]::Max($maxY, $py)
        }
    }
    if ($maxX -lt 0) { throw 'No visible pixels found in sprite.' }
    return [System.Drawing.Rectangle]::new($minX, $minY, $maxX - $minX + 1, $maxY - $minY + 1)
}

function Export-Sprite($Bitmap, $Bounds, [int]$Width, [int]$Height, [string]$Name) {
    $output = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($output)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($Bitmap, [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
            $Bounds.X, $Bounds.Y, $Bounds.Width, $Bounds.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $output.Save((Join-Path $Destination $Name), [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "$Name ${Width}x${Height}; source bounds $Bounds"
    } finally {
        $graphics.Dispose()
        $output.Dispose()
    }
}

$panel = [System.Drawing.Bitmap]::new((Resolve-Path $PanelSource).Path)
$sheet = [System.Drawing.Bitmap]::new((Resolve-Path $RowSource).Path)
try {
    Export-Sprite $panel (Get-AlphaBounds $panel 0 ($panel.Height - 1)) 384 728 'fashion_category_panel.png'
    $bands = [System.Collections.Generic.List[System.Drawing.Rectangle]]::new()
    $start = -1
    for ($py = 0; $py -le $sheet.Height; $py++) {
        $visible = $false
        if ($py -lt $sheet.Height) {
            for ($px = 0; $px -lt $sheet.Width; $px += 8) {
                if ($sheet.GetPixel($px, $py).A -ge 128) { $visible = $true; break }
            }
        }
        if ($visible -and $start -lt 0) { $start = $py }
        if (!$visible -and $start -ge 0) {
            if ($py - $start -gt 20) { $bands.Add((Get-AlphaBounds $sheet $start ($py - 1))) }
            $start = -1
        }
    }
    if ($bands.Count -ne 4) { throw "Expected exactly four row sprites; found $($bands.Count)." }
    $states = @('normal', 'hover', 'selected', 'disabled')
    for ($index = 0; $index -lt $states.Count; $index++) {
        Export-Sprite $sheet $bands[$index] 384 104 "fashion_category_row_$($states[$index]).png"
    }
} finally {
    $panel.Dispose()
    $sheet.Dispose()
}

# Review composition only; labels and sample icons are not baked into runtime assets.
$preview = [System.Drawing.Bitmap]::new(448, 760, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$previewGraphics = [System.Drawing.Graphics]::FromImage($preview)
$previewPanel = [System.Drawing.Bitmap]::new((Join-Path $Destination 'fashion_category_panel.png'))
$previewNormal = [System.Drawing.Bitmap]::new((Join-Path $Destination 'fashion_category_row_normal.png'))
$previewSelected = [System.Drawing.Bitmap]::new((Join-Path $Destination 'fashion_category_row_selected.png'))
$mockup = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot 'fashion-wardrobe-approved-mockup.png'))
$titleFont = [System.Drawing.Font]::new('Tahoma', 28, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$labelFont = [System.Drawing.Font]::new('Tahoma', 26, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$detailFont = [System.Drawing.Font]::new('Tahoma', 22, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$textBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#E7ECEF'))
$mutedBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#ABB4BA'))
$goldBrush = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#F2C14E'))
try {
    $previewGraphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $previewGraphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $previewGraphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $previewGraphics.DrawImage($previewPanel, [System.Drawing.Rectangle]::new(0, 0, 448, 760))
    $previewGraphics.DrawString('หมวดแฟชั่น', $titleFont, $textBrush, 24, 13)
    $labels = @('หัว', 'ใบหน้า', 'หลัง', 'ลำตัว', 'ขา', 'เท้า')
    $iconRows = @(225, 338, 439, 545, 662, 761)
    for ($index = 0; $index -lt 6; $index++) {
        $targetY = 88 + $index * 108
        $rowImage = if ($index -eq 0) { $previewSelected } else { $previewNormal }
        $previewGraphics.DrawImage($rowImage, [System.Drawing.Rectangle]::new(24, $targetY, 400, 100))
        $previewGraphics.DrawImage($mockup, [System.Drawing.Rectangle]::new(36, $targetY + 18, 64, 64),
            150, $iconRows[$index], 90, 72, [System.Drawing.GraphicsUnit]::Pixel)
        $previewGraphics.DrawString($labels[$index], $labelFont, $textBrush, 116, $targetY + 8)
        $detail = if ($index -eq 0) { 'หมวกนักเดินทาง' } else { 'ว่าง' }
        $previewGraphics.DrawString($detail, $detailFont, $mutedBrush, 116, $targetY + 49)
        $arrowBrush = if ($index -eq 0) { $goldBrush } else { $mutedBrush }
        $previewGraphics.DrawString('>', $titleFont, $arrowBrush, 384, $targetY + 29)
    }
    $preview.Save((Join-Path $PSScriptRoot 'fashion-category-assets-preview.png'), [System.Drawing.Imaging.ImageFormat]::Png)
} finally {
    foreach ($resource in @($previewGraphics, $preview, $previewPanel, $previewNormal, $previewSelected,
            $mockup, $titleFont, $labelFont, $detailFont, $textBrush, $mutedBrush, $goldBrush)) {
        $resource.Dispose()
    }
}
