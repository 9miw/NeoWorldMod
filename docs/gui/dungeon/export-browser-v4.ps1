param([Parameter(Mandatory=$true)][string]$Mockup,
      [Parameter(Mandatory=$true)][string]$Shell,
      [Parameter(Mandatory=$true)][string]$Widgets)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$destination=Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/dungeon/browser'
New-Item -ItemType Directory -Force -Path $destination | Out-Null
function Export-Part($path,$name,$x,$y,$w,$h) {
    $image=[System.Drawing.Bitmap]::new((Resolve-Path -LiteralPath $path).Path)
    try {
        $rect=[System.Drawing.Rectangle]::new($x,$y,$w,$h)
        $part=$image.Clone($rect,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try { $part.Save((Join-Path $destination "$name.png"),[System.Drawing.Imaging.ImageFormat]::Png) }
        finally { $part.Dispose() }
        Write-Output "$name [$x,$y,$w,$h]"
    } finally { $image.Dispose() }
}
# Measured rectangles; none contains dynamic text or item data.
Export-Part $Shell 'shell' 104 44 1468 834
Export-Part $Widgets 'primary' 202 165 1134 280
Export-Part $Widgets 'selected' 166 614 1206 246
Export-Part $Mockup 'preview' 1074 202 428 234
Export-Part $Mockup 'divider' 620 461 884 20
Export-Part $Mockup 'scroll_up' 532 215 26 24
Export-Part $Mockup 'scroll_down' 532 761 26 24
Export-Part $Mockup 'scroll_thumb' 535 244 24 76
Export-Part $Mockup 'scroll_track' 536 323 20 435
