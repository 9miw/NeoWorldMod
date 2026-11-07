$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = (Resolve-Path "$PSScriptRoot/../../..").Path
$out = "$root/src/main/resources/assets/neoworld/textures/gui/dungeon/browser"
$source = [Drawing.Bitmap]::new("$PSScriptRoot/browser-v5-approved.png")
$regions = @{'entrance'=@(589,160,82,85);'route_entrance'=@(670,509,70,67)}
foreach($name in $regions.Keys) {
    $r=$regions[$name]
    $image=$source.Clone([Drawing.Rectangle]::new($r[0],$r[1],$r[2],$r[3]),[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $queue=[Collections.Generic.Queue[Drawing.Point]]::new()
    $seen=[Collections.Generic.HashSet[int]]::new()
    for($x=0;$x -lt $image.Width;$x++){$queue.Enqueue([Drawing.Point]::new($x,0));$queue.Enqueue([Drawing.Point]::new($x,$image.Height-1))}
    for($y=0;$y -lt $image.Height;$y++){$queue.Enqueue([Drawing.Point]::new(0,$y));$queue.Enqueue([Drawing.Point]::new($image.Width-1,$y))}
    while($queue.Count) {
        $p=$queue.Dequeue()
        if(-not $seen.Add($p.Y*$image.Width+$p.X)){continue}
        $c=$image.GetPixel($p.X,$p.Y)
        if([Math]::Max($c.R,[Math]::Max($c.G,$c.B)) -gt 65){continue}
        $image.SetPixel($p.X,$p.Y,[Drawing.Color]::Transparent)
        foreach($d in @(@(-1,0),@(1,0),@(0,-1),@(0,1))) {
            $x=$p.X+$d[0];$y=$p.Y+$d[1]
            if($x -ge 0 -and $x -lt $image.Width -and $y -ge 0 -and $y -lt $image.Height){$queue.Enqueue([Drawing.Point]::new($x,$y))}
        }
    }
    $image.Save("$out/$name.png",[Drawing.Imaging.ImageFormat]::Png)
    [IO.File]::WriteAllText("$out/$name.png.mcmeta",'{"texture":{"blur":false,"clamp":true}}')
    $image.Dispose()
}
$source.Dispose()
