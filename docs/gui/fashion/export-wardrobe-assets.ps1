param([string]$Destination = "$PSScriptRoot/../../../src/main/resources/assets/neoworld/textures/gui/fashion")
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

function Export-Part([string]$Source, [string]$Name, [System.Drawing.Rectangle]$Crop, [int]$Width, [int]$Height) {
    $inputImage = [System.Drawing.Bitmap]::new((Join-Path $PSScriptRoot $Source))
    $outputImage = [System.Drawing.Bitmap]::new($Width, $Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [System.Drawing.Graphics]::FromImage($outputImage)
    try {
        if ($Crop.Right -gt $inputImage.Width -or $Crop.Bottom -gt $inputImage.Height) { throw "Crop outside $Source" }
        $graphics.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($inputImage, [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
            $Crop.X, $Crop.Y, $Crop.Width, $Crop.Height, [System.Drawing.GraphicsUnit]::Pixel)
        $outputImage.Save((Join-Path $Destination $Name), [System.Drawing.Imaging.ImageFormat]::Png)
        Write-Output "$Name ${Width}x${Height} from $Crop"
    } finally { $graphics.Dispose(); $outputImage.Dispose(); $inputImage.Dispose() }
}

# Explicit frame bounds exclude sheet gutters and external light halos.
Export-Part 'wardrobe-panel-source.png' 'fashion_wardrobe_panel.png' ([System.Drawing.Rectangle]::new(46, 33, 1030, 1339)) 576 728
Export-Part 'fashion-wardrobe-approved-mockup.png' 'fashion_search_icon.png' ([System.Drawing.Rectangle]::new(990, 218, 28, 28)) 16 16
Export-Part 'wardrobe-controls-source.png' 'fashion_search_normal.png' ([System.Drawing.Rectangle]::new(31, 84, 1847, 121)) 512 48
Export-Part 'wardrobe-controls-source.png' 'fashion_search_focus.png' ([System.Drawing.Rectangle]::new(31, 266, 1847, 118)) 512 48
Export-Part 'wardrobe-controls-source.png' 'fashion_wardrobe_detail.png' ([System.Drawing.Rectangle]::new(31, 450, 1847, 282)) 512 100
Export-Part 'wardrobe-slots-source.png' 'fashion_slot_normal.png' ([System.Drawing.Rectangle]::new(293, 67, 438, 402)) 128 128
Export-Part 'wardrobe-slots-source.png' 'fashion_slot_hover.png' ([System.Drawing.Rectangle]::new(808, 67, 438, 402)) 128 128
Export-Part 'wardrobe-slots-source.png' 'fashion_slot_selected.png' ([System.Drawing.Rectangle]::new(293, 529, 438, 410)) 128 128
Export-Part 'wardrobe-slots-source.png' 'fashion_slot_equipped.png' ([System.Drawing.Rectangle]::new(808, 529, 438, 410)) 128 128
