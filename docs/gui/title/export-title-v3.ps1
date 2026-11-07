$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$probe = [System.Drawing.Bitmap]::new(1,1)
$probe.Dispose()
$references = [AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { -not $_.IsDynamic -and $_.Location } | ForEach-Object { $_.Location }
Add-Type -ReferencedAssemblies $references -TypeDefinition @'
using System.Drawing;
using System.Drawing.Imaging;
public static class TitleV3Export {
    public static void Crop(string source,string target,int x,int y,int w,int h) {
        using(var input=new Bitmap(source)) {
            if(w==0) {
                int left=input.Width,top=input.Height,right=0,bottom=0;
                for(int j=0;j<input.Height;j++) for(int i=0;i<input.Width;i++)
                    if(input.GetPixel(i,j).A>=245) {left=System.Math.Min(left,i);top=System.Math.Min(top,j);right=System.Math.Max(right,i);bottom=System.Math.Max(bottom,j);}
                x=left;y=top;w=right-left+1;h=bottom-top+1;
            }
            using(var output=input.Clone(new Rectangle(x,y,w,h),PixelFormat.Format32bppArgb)) {
                for(int j=0;j<h;j++) for(int i=0;i<w;i++) {
                    var pixel=output.GetPixel(i,j);
                    if(pixel.A<245) output.SetPixel(i,j,Color.Transparent);
                    else if(pixel.A<255) output.SetPixel(i,j,Color.FromArgb(255,pixel.R,pixel.G,pixel.B));
                }
                output.Save(target,ImageFormat.Png);
                System.Console.WriteLine(System.IO.Path.GetFileName(target)+" "+w+"x"+h);
            }
        }
    }
}
'@
$target = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/title'))
[TitleV3Export]::Crop((Join-Path $PSScriptRoot 'sources/title-v3-frame.png'), (Join-Path $target 'title_v3_frame.png'),0,0,0,0)
$states=@('normal','hover','selected','disabled')
for($i=0;$i -lt 4;$i++) {
    [TitleV3Export]::Crop((Join-Path $PSScriptRoot 'sources/title-v3-rows.png'), (Join-Path $target "title_v3_row_$($states[$i]).png"),46,(90+211*$i),1444,180)
}
foreach($name in @('title_v3_frame') + ($states | ForEach-Object {"title_v3_row_$_"})) {
    '{"texture":{"blur":false,"clamp":true}}' | Set-Content -Encoding ascii (Join-Path $target "$name.png.mcmeta")
}
