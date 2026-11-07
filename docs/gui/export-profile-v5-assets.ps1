param([Parameter(Mandatory=$true)][string]$ShellSource,[Parameter(Mandatory=$true)][string]$ComponentsSource)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$output=Join-Path $root 'src/main/resources/assets/neoworld/textures/gui/player/v5'
New-Item -ItemType Directory -Path $output -Force | Out-Null
Copy-Item -LiteralPath $ShellSource -Destination (Join-Path $PSScriptRoot 'player-profile-v5-shell-source.png')
Copy-Item -LiteralPath $ComponentsSource -Destination (Join-Path $PSScriptRoot 'player-profile-v5-components-source.png')
$atlas=[Drawing.Bitmap]::new($ComponentsSource)
if($atlas.Width-ne1536 -or $atlas.Height-ne1024) { throw 'Expected 1536x1024 component sheet; measure new regions before export.' }
# Measured isolated regions, not the nominal generated grid: several generated sprites cross cell edges.
$regions=@(
 @('tab_normal',15,80,365,125,320,80),@('tab_hover',395,80,370,125,320,80),
 @('tab_selected',775,80,370,125,320,80),@('tab_disabled',1155,80,375,125,320,80),
 @('panel',15,285,410,255,320,192),@('row_normal',435,350,445,120,320,64),
 @('card',890,275,255,265,160,192),@('portrait',1230,255,220,305,160,256),
 @('button_normal',15,620,365,130,320,80),@('button_hover',395,620,370,130,320,80),
 @('button_selected',775,620,370,130,320,80),@('button_disabled',1155,620,375,130,320,80),
 @('section',15,820,375,110,320,56),@('row_hover',395,820,375,110,320,64),
 @('progress_track',780,830,380,90,320,40),@('progress_fill',1175,840,350,75,300,16)
)
$manifest=@()
foreach($r in $regions) {
 $name=[string]$r[0];$left=[int]$r[1];$top=[int]$r[2];$right=$left+[int]$r[3];$bottom=$top+[int]$r[4]
 $minX=$right;$minY=$bottom;$maxX=-1;$maxY=-1
 for($y=$top;$y-lt$bottom;$y++) { for($x=$left;$x-lt$right;$x++) {
  if($atlas.GetPixel($x,$y).A-ge160) { $minX=[Math]::Min($minX,$x);$minY=[Math]::Min($minY,$y);$maxX=[Math]::Max($maxX,$x);$maxY=[Math]::Max($maxY,$y) }
 }}
 if($maxX-lt0) { throw "No opaque sprite: $name" }
 $rect=[Drawing.Rectangle]::new($minX,$minY,$maxX-$minX+1,$maxY-$minY+1)
 $crop=$atlas.Clone($rect,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
 # Hard-alpha runtime silhouettes remove extraction fringes. Normalize sibling states to identical geometry.
 for($y=0;$y-lt$crop.Height;$y++) { for($x=0;$x-lt$crop.Width;$x++) {
  $p=$crop.GetPixel($x,$y)
  if($p.A-lt160) { $crop.SetPixel($x,$y,[Drawing.Color]::Transparent) }
  else { $crop.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,$p.R,$p.G,$p.B)) }
 }}
 $sprite=[Drawing.Bitmap]::new([int]$r[5],[int]$r[6],[Drawing.Imaging.PixelFormat]::Format32bppArgb)
 $g=[Drawing.Graphics]::FromImage($sprite);$g.CompositingMode=[Drawing.Drawing2D.CompositingMode]::SourceCopy
 $g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
 $g.DrawImage($crop,[Drawing.Rectangle]::new(0,0,$sprite.Width,$sprite.Height),0,0,$crop.Width,$crop.Height,[Drawing.GraphicsUnit]::Pixel)
 $g.Dispose();$crop.Dispose()
 $file=Join-Path $output ($name+'.png');$sprite.Save($file,[Drawing.Imaging.ImageFormat]::Png);$sprite.Dispose()
 Set-Content -LiteralPath ($file+'.mcmeta') -Value '{"texture":{"blur":false,"clamp":true}}'
 $manifest+=[pscustomobject]@{name=$name;source_x=$rect.X;source_y=$rect.Y;source_width=$rect.Width;source_height=$rect.Height;width=[int]$r[5];height=[int]$r[6];file='src/main/resources/assets/neoworld/textures/gui/player/v5/'+$name+'.png'}
}
$atlas.Dispose()
$shell=[Drawing.Bitmap]::new($ShellSource)
for($y=0;$y-lt$shell.Height;$y++) { for($x=0;$x-lt$shell.Width;$x++) {
 $p=$shell.GetPixel($x,$y)
 if($p.A-lt160) { $shell.SetPixel($x,$y,[Drawing.Color]::Transparent) }
 else { $shell.SetPixel($x,$y,[Drawing.Color]::FromArgb(255,$p.R,$p.G,$p.B)) }
}}
$shell.Save((Join-Path $output 'frame.png'),[Drawing.Imaging.ImageFormat]::Png)
$shellSize="$($shell.Width)x$($shell.Height)";$shell.Dispose()
Set-Content -LiteralPath (Join-Path $output 'frame.png.mcmeta') -Value '{"texture":{"blur":false,"clamp":true}}'
[pscustomobject]@{approved_reference='docs/gui/player-profile-v5-approved.png';generator='OpenAI ImageGen';shell_size=$shellSize;alpha_policy='binary threshold160; nearest-neighbor normalization';components=$manifest;source_sha256=(Get-FileHash -LiteralPath $ComponentsSource -Algorithm SHA256).Hash} |
 ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'player-profile-v5-assets.json')
# Checkerboard preview uses actual installed PNGs; it is not an in-game verification image.
$sheet=[Drawing.Bitmap]::new(1400,960);$g=[Drawing.Graphics]::FromImage($sheet)
for($y=0;$y-lt960;$y+=16) { for($x=0;$x-lt1400;$x+=16) {
 $brush=if((($x/16)+($y/16))%2-eq0){[Drawing.Brushes]::DimGray}else{[Drawing.Brushes]::Gray}
 $g.FillRectangle($brush,$x,$y,16,16)
}}
$g.InterpolationMode=[Drawing.Drawing2D.InterpolationMode]::NearestNeighbor;$g.PixelOffsetMode=[Drawing.Drawing2D.PixelOffsetMode]::Half
$font=[Drawing.Font]::new('Segoe UI',10)
for($i=0;$i-lt$manifest.Count;$i++) {
 $m=$manifest[$i];$sprite=[Drawing.Bitmap]::new((Join-Path $root $m.file))
 $factor=[Math]::Min(300.0/$sprite.Width,185.0/$sprite.Height)
 $w=[int]($sprite.Width*$factor);$h=[int]($sprite.Height*$factor);$cx=($i%4)*350;$cy=[int][Math]::Floor($i/4)*240
 $g.DrawImage($sprite,[Drawing.Rectangle]::new($cx+[int]((350-$w)/2),$cy+20,$w,$h))
 $g.DrawString($m.name,$font,[Drawing.Brushes]::White,$cx+12,$cy+215);$sprite.Dispose()
}
$font.Dispose();$g.Dispose();$sheet.Save((Join-Path $PSScriptRoot 'player-profile-v5-assets-checkerboard.png'),[Drawing.Imaging.ImageFormat]::Png);$sheet.Dispose()
Write-Output "Exported $($manifest.Count) normalized components and $shellSize shell; checkerboard preview saved."
