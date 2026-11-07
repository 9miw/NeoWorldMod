param([string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$sourcePath = Join-Path $ProjectRoot 'src/main/resources/assets/neoworld/textures/gui/menu/menu_icons.png'
# Measured disjoint regions with sampling gutters, not equal atlas cells.
$regions = @(
    @(52,74,239,233), @(349,61,251,258), @(660,74,242,245),
    @(971,78,251,235), @(20,399,319,179), @(370,362,231,258),
    @(692,382,198,233), @(964,359,259,264), @(43,660,251,263),
    @(335,691,283,206), @(666,681,227,226), @(962,662,261,239), @(59,975,249,230)
)
$paths = @(
    'font/icons/menu_profile.png', 'font/icons/menu_title.png', 'font/icons/chest.png',
    'gui/fashion/tooltip/fashion.png', 'font/icons/menu_party.png', 'font/icons/menu_dungeon.png',
    'font/icons/menu_resume.png', 'font/icons/cog.png', 'font/icons/menu_mods.png',
    'font/icons/menu_advancements.png', 'font/icons/menu_statistics.png',
    'font/icons/menu_lan.png', 'font/icons/menu_exit.png'
)
$source = [Drawing.Bitmap]::new($sourcePath)
try {
    if ($source.Width -ne 1254 -or $source.Height -ne 1254) { throw 'Unexpected menu atlas dimensions' }
    for ($i = 0; $i -lt $regions.Count; $i++) {
        $r = $regions[$i]
        $dest = Join-Path $ProjectRoot ('src/main/resources/assets/neoworld/textures/' + $paths[$i])
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest)) | Out-Null
        $image = [Drawing.Bitmap]::new(64,64,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $graphics = [Drawing.Graphics]::FromImage($image)
        try {
            $graphics.Clear([Drawing.Color]::Transparent)
            $graphics.CompositingMode = [Drawing.Drawing2D.CompositingMode]::SourceCopy
            $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
            $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::Half
            $scale = 60.0 / [Math]::Max($r[2],$r[3])
            $w = [Math]::Max(1,[int][Math]::Round($r[2]*$scale))
            $h = [Math]::Max(1,[int][Math]::Round($r[3]*$scale))
            $graphics.DrawImage($source,[Drawing.Rectangle]::new([int][Math]::Floor((64-$w)/2),[int][Math]::Floor((64-$h)/2),$w,$h),
                $r[0],$r[1],$r[2],$r[3],[Drawing.GraphicsUnit]::Pixel)
            $image.Save($dest,[Drawing.Imaging.ImageFormat]::Png)
            [IO.File]::WriteAllText($dest+'.mcmeta',"{`n  `"texture`": { `"blur`": false, `"clamp`": true }`n}`n")
        } finally { $graphics.Dispose(); $image.Dispose() }
    }
} finally { $source.Dispose() }
Write-Output 'Exported 13 menu glyphs, preserving aspect ratio on transparent 64x64 canvases.'
