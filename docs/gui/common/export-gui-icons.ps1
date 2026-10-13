param(
    [Parameter(Mandatory = $true)][string]$Source,
    [Parameter(Mandatory = $true)][string]$Destination
)

Add-Type -AssemblyName System.Drawing
$names = @('leader', 'member', 'invite', 'kick', 'ready', 'leave', 'disband', 'refresh')
$bitmap = [System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Source).Path)
try {
    New-Item -ItemType Directory -Path $Destination -Force | Out-Null
    for ($index = 0; $index -lt $names.Count; $index++) {
        $column = $index % 4
        $row = [Math]::Floor($index / 4)
        $left = [int][Math]::Floor($column * $bitmap.Width / 4)
        $top = [int][Math]::Floor($row * $bitmap.Height / 2)
        $cellWidth = [int][Math]::Floor(($column + 1) * $bitmap.Width / 4) - $left
        $cellHeight = [int][Math]::Floor(($row + 1) * $bitmap.Height / 2) - $top
        $minX = $cellWidth
        $minY = $cellHeight
        $maxX = -1
        $maxY = -1
        $transparent = 0
        for ($py = 0; $py -lt $cellHeight; $py++) {
            for ($px = 0; $px -lt $cellWidth; $px++) {
                $alpha = $bitmap.GetPixel($left + $px, $top + $py).A
                if ($alpha -eq 0) { $transparent++ }
                if ($alpha -ge 128) {
                    $minX = [Math]::Min($minX, $px)
                    $minY = [Math]::Min($minY, $py)
                    $maxX = [Math]::Max($maxX, $px)
                    $maxY = [Math]::Max($maxY, $py)
                }
            }
        }
        if ($maxX -lt 0 -or $transparent -eq 0) { throw "Missing sprite or true alpha: $($names[$index])" }
        $sourceWidth = $maxX - $minX + 1
        $sourceHeight = $maxY - $minY + 1
        $scale = 28.0 / [Math]::Max($sourceWidth, $sourceHeight)
        $targetWidth = [int][Math]::Round($sourceWidth * $scale)
        $targetHeight = [int][Math]::Round($sourceHeight * $scale)
        $output = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [System.Drawing.Graphics]::FromImage($output)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
            $target = [System.Drawing.Rectangle]::new([int][Math]::Floor((32 - $targetWidth) / 2),
                [int][Math]::Floor((32 - $targetHeight) / 2), $targetWidth, $targetHeight)
            $graphics.DrawImage($bitmap, $target, $left + $minX, $top + $minY,
                $sourceWidth, $sourceHeight, [System.Drawing.GraphicsUnit]::Pixel)
            $output.Save((Join-Path $Destination ($names[$index] + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $graphics.Dispose()
            $output.Dispose()
        }
        Write-Output "$($names[$index]): 32x32, true alpha, source $sourceWidth x $sourceHeight"
    }
} finally {
    $bitmap.Dispose()
}
