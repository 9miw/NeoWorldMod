param([string]$SourceRoot = 'C:/Users/miw/.codex/generated_images/01a0e4c7-7a03-79f1-8f1d-fb268846aed8')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path "$PSScriptRoot/../../..").Path
$out = "$root/src/main/resources/assets/neoworld/textures/gui/dungeon/crystal"
New-Item -ItemType Directory -Force $out | Out-Null
$approved = [Drawing.Bitmap]::new("$SourceRoot/exec-f9c6ef3d-0282-4dd2-bf32-755924a15186.png")
$frame = [Drawing.Bitmap]::new("$SourceRoot/exec-52bb76c0-0ff5-4c3d-8685-e8b6e2db727a.png")
Copy-Item "$SourceRoot/exec-f9c6ef3d-0282-4dd2-bf32-755924a15186.png" "$PSScriptRoot/browser-crystal-approved.png"
Copy-Item "$SourceRoot/exec-facb755f-9c8f-4d8d-8742-3002a58225d9.png" "$out/background.png"
$manifest = [Collections.Generic.List[object]]::new()
function Save-Asset($bitmap, $name, $border = 0) {
    $bitmap.Save("$out/$name.png", [Drawing.Imaging.ImageFormat]::Png)
    [IO.File]::WriteAllText("$out/$name.png.mcmeta", '{"texture":{"blur":false,"clamp":true}}')
    $manifest.Add(@{name=$name; width=$bitmap.Width; height=$bitmap.Height; border=$border})
}
function Crop($image, $x, $y, $w, $h) {
    return $image.Clone([Drawing.Rectangle]::new($x,$y,$w,$h), [Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Clear-Inside($bitmap, $border, $color) {
    $g=[Drawing.Graphics]::FromImage($bitmap)
    $brush=[Drawing.SolidBrush]::new($color)
    $g.FillRectangle($brush,$border,$border,$bitmap.Width-2*$border,$bitmap.Height-2*$border)
    $brush.Dispose(); $g.Dispose()
}
# Export measured corner/edge regions. The renderer preserves corner proportions.
$regions = @{
    'corner_tl'=@(28,127,100,100); 'corner_tr'=@(1544,127,100,100)
    'corner_bl'=@(28,802,100,100); 'corner_br'=@(1544,802,100,100)
    'edge_top'=@(300,139,60,45); 'edge_bottom'=@(600,850,60,45)
    'edge_left'=@(45,300,55,40); 'edge_right'=@(1574,300,55,40)
    'surface'=@(720,260,64,64); 'title'=@(480,0,700,205)
}
foreach($name in $regions.Keys) {
    $r=$regions[$name]; $b=Crop $frame $r[0] $r[1] $r[2] $r[3]
    Save-Asset $b $name; $b.Dispose()
}
# All seams come from the same lower frame band, with matching 100-pixel insets.
$atlas=[Drawing.Bitmap]::new(72,72)
$ag=[Drawing.Graphics]::FromImage($atlas)
$ag.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$ag.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$tiles=@(
    @(28,802,100,100,0,0,1), @(700,802,80,100,24,0,1), @(1544,802,100,100,48,0,1),
    @(28,400,100,80,0,24,0), @(720,260,64,64,24,24,0), @(1544,400,100,80,48,24,0),
    @(28,802,100,100,0,48,0), @(700,802,80,100,24,48,0), @(1544,802,100,100,48,48,0))
foreach($t in $tiles){
    $b=Crop $frame $t[0] $t[1] $t[2] $t[3]
    if($t[6]){$b.RotateFlip([Drawing.RotateFlipType]::RotateNoneFlipY)}
    $ag.DrawImage($b,[Drawing.Rectangle]::new($t[4],$t[5],24,24),0,0,$b.Width,$b.Height,[Drawing.GraphicsUnit]::Pixel)
    $b.Dispose()
}
$ag.Dispose(); Save-Asset $atlas 'frame' 24; $atlas.Dispose()
$dark=[Drawing.Color]::FromArgb(255,25,32,33)
$row=Crop $approved 245 222 396 98
Clear-Inside $row 8 $dark
Save-Asset $row 'row_selected' 8
$normal=[Drawing.Bitmap]$row.Clone()
for($y=0;$y -lt $normal.Height;$y++){for($x=0;$x -lt $normal.Width;$x++){
    $c=$normal.GetPixel($x,$y)
    if($c.G -gt $c.R*1.15){$v=[Math]::Min(160,[int](($c.R+$c.G+$c.B)/3));$normal.SetPixel($x,$y,[Drawing.Color]::FromArgb($c.A,$v,$v,$v))}
}}
Save-Asset $normal 'row_normal' 8
$button=Crop $approved 1144 805 337 94
Clear-Inside $button 17 ([Drawing.Color]::FromArgb(255,0,91,54))
Save-Asset $button 'button' 17
$slot=Crop $approved 711 673 108 96
Clear-Inside $slot 8 $dark
Save-Asset $slot 'slot' 8
$icons=@{'door'=@(714,174,94,88);'star'=@(395,276,27,30);'swords'=@(1027,482,58,55);'skull'=@(1227,482,55,55);'chest'=@(706,618,48,43);'member'=@(714,319,38,37);'clock'=@(713,360,40,40)}
foreach($name in $icons.Keys){
    $r=$icons[$name];$b=Crop $approved $r[0] $r[1] $r[2] $r[3]
    # Remove only edge-connected dark panel pixels, retaining internal icon shading.
    $queue=[Collections.Generic.Queue[Drawing.Point]]::new()
    for($x=0;$x -lt $b.Width;$x++){$queue.Enqueue([Drawing.Point]::new($x,0));$queue.Enqueue([Drawing.Point]::new($x,$b.Height-1))}
    for($y=0;$y -lt $b.Height;$y++){$queue.Enqueue([Drawing.Point]::new(0,$y));$queue.Enqueue([Drawing.Point]::new($b.Width-1,$y))}
    $seen=[Collections.Generic.HashSet[int]]::new()
    while($queue.Count){
        $p=$queue.Dequeue();$key=$p.Y*$b.Width+$p.X
        if(-not $seen.Add($key)){continue};$c=$b.GetPixel($p.X,$p.Y)
        if([Math]::Max($c.R,[Math]::Max($c.G,$c.B)) -gt 85){continue}
        $b.SetPixel($p.X,$p.Y,[Drawing.Color]::Transparent)
        foreach($d in @(@(-1,0),@(1,0),@(0,-1),@(0,1))){$nx=$p.X+$d[0];$ny=$p.Y+$d[1];if($nx -ge 0 -and $ny -ge 0 -and $nx -lt $b.Width -and $ny -lt $b.Height){$queue.Enqueue([Drawing.Point]::new($nx,$ny))}}
    }
    $native=[Drawing.Bitmap]::new(16,16)
    $ng=[Drawing.Graphics]::FromImage($native)
    $ng.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $ng.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
    $scale=16.0/[Math]::Max($b.Width,$b.Height)
    $nw=[Math]::Max(1,[int][Math]::Round($b.Width*$scale));$nh=[Math]::Max(1,[int][Math]::Round($b.Height*$scale))
    $ng.DrawImage($b,[Drawing.Rectangle]::new([int]((16-$nw)/2),[int]((16-$nh)/2),$nw,$nh),0,0,$b.Width,$b.Height,[Drawing.GraphicsUnit]::Pixel)
    $ng.Dispose();Save-Asset $native $name;$native.Dispose();$b.Dispose()
}
$bg=[Drawing.Bitmap]::new("$out/background.png")
$manifest.Add(@{name='background';width=$bg.Width;height=$bg.Height;border=0});$bg.Dispose()
[IO.File]::WriteAllText("$out/background.png.mcmeta", '{"texture":{"blur":false,"clamp":true}}')
[IO.File]::WriteAllText("$out/assets.json",(ConvertTo-Json -InputObject @($manifest) -Depth 4))
$sheet=[Drawing.Bitmap]::new(1000,900);$g=[Drawing.Graphics]::FromImage($sheet)
for($y=0;$y -lt 900;$y+=20){for($x=0;$x -lt 1000;$x+=20){$color=if((($x+$y)/20)%2 -eq 0){[Drawing.Color]::Gray}else{[Drawing.Color]::LightGray};$brush=[Drawing.SolidBrush]::new($color);$g.FillRectangle($brush,$x,$y,20,20);$brush.Dispose()}}
$i=0
foreach($a in $manifest){if($a.name -eq 'background'){continue};$b=[Drawing.Bitmap]::new("$out/$($a.name).png");$x=($i%4)*250;$y=[Math]::Floor($i/4)*150;$scale=[Math]::Min(220.0/$b.Width,110.0/$b.Height);$g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$g.DrawImage($b,$x+10,$y+25,[int]($b.Width*$scale),[int]($b.Height*$scale));$g.DrawString($a.name,[Drawing.SystemFonts]::DefaultFont,[Drawing.Brushes]::Black,$x+10,$y+5);$b.Dispose();$i++}
$g.Dispose();$sheet.Save("$PSScriptRoot/crystal-assets-preview.png");$sheet.Dispose()
$approved.Dispose();$frame.Dispose();$row.Dispose();$normal.Dispose();$button.Dispose();$slot.Dispose()
Write-Output "Exported $($manifest.Count) crystal assets and measured manifest."
