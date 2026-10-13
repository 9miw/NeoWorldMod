$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/fashion'
function Export-Part($Source, $Name, $Crop, $Width, $Height) {
    $sourceImage = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot $Source))
    $target = [System.Drawing.Bitmap]::new($Width, $Height)
    $graphics = [System.Drawing.Graphics]::FromImage($target)
    try {
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($sourceImage, [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
            $Crop.X, $Crop.Y, $Crop.Width, $Crop.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $target.Save((Join-Path $destination $Name), [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "$Name ${Width}x${Height}"
    } finally { $graphics.Dispose(); $target.Dispose(); $sourceImage.Dispose() }
}
Export-Part 'fashion-shell-source.png' 'fashion_shell.png' ([System.Drawing.Rectangle]::new(24, 31, 1638, 873)) 860 444
Export-Part 'fashion-shell-controls-source.png' 'fashion_title_plaque.png' ([System.Drawing.Rectangle]::new(55, 75, 1665, 302)) 480 112
Export-Part 'fashion-shell-controls-source.png' 'fashion_close_normal.png' ([System.Drawing.Rectangle]::new(440, 467, 375, 370)) 64 64
Export-Part 'fashion-shell-controls-source.png' 'fashion_close_hover.png' ([System.Drawing.Rectangle]::new(958, 467, 375, 370)) 64 64
