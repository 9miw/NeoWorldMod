param([Parameter(Mandatory=$true)][string]$Shell,
      [Parameter(Mandatory=$true)][string]$Mockup)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$destination=Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/dungeon/hud'
function Export-Part($image,$name,$source,$width,$height) {
    $bitmap=[Drawing.Bitmap]::new($width,$height,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics=[Drawing.Graphics]::FromImage($bitmap)
    try {
        $graphics.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
        $graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $graphics.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
        $graphics.DrawImage($image,[Drawing.Rectangle]::new(0,0,$width,$height),$source,[Drawing.GraphicsUnit]::Pixel)
        $bitmap.Save((Join-Path $destination "$name.png"),[Drawing.Imaging.ImageFormat]::Png)
    } finally { $graphics.Dispose(); $bitmap.Dispose() }
}
$image=[Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Shell).Path)
try {
    $left=$image.Width; $top=$image.Height; $right=-1; $bottom=-1
    for($y=0;$y -lt $image.Height;$y++) {
        for($x=0;$x -lt $image.Width;$x++) {
            if($image.GetPixel($x,$y).A -gt 8) {
                $left=[Math]::Min($left,$x); $top=[Math]::Min($top,$y)
                $right=[Math]::Max($right,$x); $bottom=[Math]::Max($bottom,$y)
            }
        }
    }
    if($right -lt 0){throw 'Empty shell'}
    $bounds=[Drawing.Rectangle]::new($left,$top,$right-$left+1,$bottom-$top+1)
    # Empty surfaces and straight rules have no ornamental proportions to preserve.
    Export-Part $image 'minimal_strip' $bounds 440 224
    "Shell alpha bounds: $bounds -> 440x224"
} finally {$image.Dispose()}
$reference=[Drawing.Bitmap]::new((Resolve-Path -LiteralPath $Mockup).Path)
try {
    if($reference.Width -ne 1672 -or $reference.Height -ne 941){throw 'Expected approved 1672x941 mockup'}
    Export-Part $reference 'minimal_track' ([Drawing.Rectangle]::new(1580,451,55,3)) 376 4
    Export-Part $reference 'minimal_fill' ([Drawing.Rectangle]::new(1370,451,130,3)) 376 4
} finally {$reference.Dispose()}
