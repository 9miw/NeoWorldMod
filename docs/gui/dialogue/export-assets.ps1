param([string]$ProjectRoot = (Resolve-Path "$PSScriptRoot/../../..").Path)
Add-Type -AssemblyName System.Drawing
$source = [Drawing.Bitmap]::FromFile("$PSScriptRoot/component-source.png")
$destination = Join-Path $ProjectRoot 'src/main/resources/assets/neoworld/textures/gui/dialogue'
New-Item -ItemType Directory -Force $destination | Out-Null
$parts = @(
    @{Name='frame'; X=12; Y=48; W=1514; H=408},
    @{Name='portrait'; X=12; Y=495; W=390; H=464},
    @{Name='response'; X=504; Y=834; W=946; H=98},
    @{Name='diamond'; X=8; Y=207; W=88; H=88}
)
$metrics = @()
try {
    foreach ($part in $parts) {
        $rect = [Drawing.Rectangle]::new($part.X,$part.Y,$part.W,$part.H)
        $image = $source.Clone($rect, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $minX=$image.Width; $minY=$image.Height; $maxX=-1; $maxY=-1; $visible=0
        for ($y=0; $y -lt $image.Height; $y++) {
            for ($x=0; $x -lt $image.Width; $x++) {
                $pixel=$image.GetPixel($x,$y)
                if ($part.Name -eq 'diamond' -and ([Math]::Abs(($x+$part.X)-50)+[Math]::Abs(($y+$part.Y)-251)) -gt 38) {
                    $image.SetPixel($x,$y,[Drawing.Color]::Transparent); continue
                }
                if ($pixel.A -lt 32) { $image.SetPixel($x,$y,[Drawing.Color]::Transparent); continue }
                $visible++
                $minX=[Math]::Min($minX,$x); $minY=[Math]::Min($minY,$y)
                $maxX=[Math]::Max($maxX,$x); $maxY=[Math]::Max($maxY,$y)
            }
        }
        $image.Save((Join-Path $destination ($part.Name+'.png')), [Drawing.Imaging.ImageFormat]::Png)
        '{"texture":{"blur":false,"clamp":true}}' | Set-Content -Encoding utf8 (Join-Path $destination ($part.Name+'.png.mcmeta'))
        $metrics += [pscustomobject]@{name=$part.Name; source=@($part.X,$part.Y,$part.W,$part.H); size=@($image.Width,$image.Height); alphaBounds=@($minX,$minY,$maxX,$maxY); nontransparent=$visible}
        $image.Dispose()
    }
} finally { $source.Dispose() }
$metrics | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 "$PSScriptRoot/asset-metrics.json"
$preview = [Drawing.Bitmap]::new(1000,700)
$graphics = [Drawing.Graphics]::FromImage($preview)
try {
    for ($y=0; $y -lt 700; $y+=20) {
        for ($x=0; $x -lt 1000; $x+=20) {
            $color=if ((($x/20)+($y/20)) % 2 -eq 0) {[Drawing.Color]::FromArgb(95,100,108)} else {[Drawing.Color]::FromArgb(160,165,173)}
            $brush=[Drawing.SolidBrush]::new($color)
            $graphics.FillRectangle($brush,$x,$y,20,20)
            $brush.Dispose()
        }
    }
    $graphics.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    foreach ($entry in @(@('frame',20,20,960,259),@('portrait',20,320,250,297),@('response',320,345,650,68),@('diamond',580,480,88,88))) {
        $partImage=[Drawing.Bitmap]::FromFile((Join-Path $destination ($entry[0]+'.png')))
        $graphics.DrawImage($partImage,[Drawing.Rectangle]::new($entry[1],$entry[2],$entry[3],$entry[4]))
        $partImage.Dispose()
    }
    $preview.Save("$PSScriptRoot/exported-assets-checkerboard.png",[Drawing.Imaging.ImageFormat]::Png)
} finally {$graphics.Dispose();$preview.Dispose()}
$metrics | Format-Table name,size,alphaBounds
