param(
    [string]$SourceDirectory = 'docs/gui/title/sources',
    [string]$Destination = 'src/main/resources/assets/neoworld/textures/gui/title'
)

# Mechanical sprite extraction from ImageGen art; no text or player content is baked into assets.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$titleAssemblyReferences = [AppDomain]::CurrentDomain.GetAssemblies() | Where-Object { -not $_.IsDynamic -and $_.Location } | ForEach-Object { $_.Location }
Add-Type -ReferencedAssemblies $titleAssemblyReferences -TypeDefinition @'
using System;
using System.Drawing;
using System.Drawing.Imaging;
public static class TitleAssetExporter {
    public static void Export(string input, string output, Rectangle? region) {
        using var source = new Bitmap(input);
        var bounds = region ?? OpaqueBounds(source);
        using var sprite = source.Clone(bounds, PixelFormat.Format32bppArgb);
        sprite.Save(output, ImageFormat.Png);
        Console.WriteLine(System.IO.Path.GetFileName(output) + " " + sprite.Width + "x" + sprite.Height);
    }
    private static Rectangle OpaqueBounds(Bitmap bitmap) {
        int left = bitmap.Width, top = bitmap.Height, right = -1, bottom = -1;
        for (int y = 0; y < bitmap.Height; y++) for (int x = 0; x < bitmap.Width; x++) {
            if (bitmap.GetPixel(x, y).A < 240) continue;
            left = Math.Min(left, x); top = Math.Min(top, y);
            right = Math.Max(right, x); bottom = Math.Max(bottom, y);
        }
        if (right < left) throw new InvalidOperationException("Asset has no opaque pixels");
        return Rectangle.FromLTRB(left, top, right + 1, bottom + 1);
    }
}
'@

New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$titleExports = @(
    @('exec-47a8a95f-0f95-47d5-862d-0fd85e167959.png', 'title_frame.png'),
    @('exec-e809473a-ff2c-4b4c-97a0-b44b68cee548.png', 'title_left_panel.png'),
    @('exec-6b377a69-da1f-400f-bdec-81aac2de6a9a.png', 'title_right_panel.png')
)
foreach ($titleExport in $titleExports) {
    [TitleAssetExporter]::Export((Join-Path $SourceDirectory $titleExport[0]), (Join-Path $Destination $titleExport[1]), $null)
}
$titleRowSource = Join-Path $SourceDirectory 'exec-1bb5cfcc-ebb3-425d-9c1d-ee10648dafff.png'
$titleRowNames = @('normal', 'hover', 'selected', 'disabled')
for ($titleIndex = 0; $titleIndex -lt 4; $titleIndex++) {
    $titleRowRegion = [Drawing.Rectangle]::new(44, 58 + 168 * $titleIndex, 1960, 144)
    [TitleAssetExporter]::Export($titleRowSource, (Join-Path $Destination ('title_row_' + $titleRowNames[$titleIndex] + '.png')), $titleRowRegion)
}
Get-ChildItem -LiteralPath $Destination -Filter '*.png' | ForEach-Object {
    [IO.File]::WriteAllText($_.FullName + '.mcmeta', '{"texture":{"blur":false,"clamp":true}}', [Text.UTF8Encoding]::new($false))
}
