$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$destination = Join-Path $PSScriptRoot '../../../src/main/resources/assets/neoworld/textures/gui/title'
$source = [System.Drawing.Bitmap]::FromFile((Join-Path $PSScriptRoot 'sources/title-buttons-v2.png'))
try {
    $states = @('normal', 'hover', 'primary', 'disabled')
    for ($i = 0; $i -lt 4; $i++) {
        $rect = [System.Drawing.Rectangle]::new(48, (64 + 228 * $i), 1440, 200)
        $sprite = $source.Clone($rect, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        try {
            $sprite.Save((Join-Path $destination "title_button_$($states[$i]).png"), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally { $sprite.Dispose() }
        '{"texture":{"blur":false,"clamp":true}}' | Set-Content -Encoding ascii (Join-Path $destination "title_button_$($states[$i]).png.mcmeta")
    }
} finally { $source.Dispose() }
