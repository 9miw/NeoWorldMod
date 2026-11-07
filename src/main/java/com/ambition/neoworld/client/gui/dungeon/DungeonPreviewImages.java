package com.ambition.neoworld.client.gui.dungeon;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/** Reads PNG dimensions once per resource reload; screenshots keep their actual aspect ratio. */
public final class DungeonPreviewImages {
    private static final ResourceLocation DEFAULT = ResourceLocation.fromNamespaceAndPath("neoworld", "textures/gui/dungeon/browser/preview.png");
    private static final Map<ResourceLocation, Image> CACHE = new HashMap<>();
    private record Image(ResourceLocation texture, int width, int height) {}
    public record Crop(float u, float v, int width, int height) {}
    private DungeonPreviewImages() {}

    public static void clear(ResourceManager resources) { CACHE.clear(); }

    private static Image resolve(String name) {
        ResourceLocation id = ResourceLocation.tryParse(name);
        if (id == null || !id.getPath().startsWith("textures/") || !id.getPath().endsWith(".png")) id = DEFAULT;
        return CACHE.computeIfAbsent(id, key -> {
            var resource = Minecraft.getInstance().getResourceManager().getResource(key);
            if (resource.isPresent()) {
                try (var stream = resource.get().open(); var input = new MemoryCacheImageInputStream(stream)) {
                    var readers = ImageIO.getImageReadersByFormatName("png");
                    if (readers.hasNext()) {
                        var reader = readers.next();
                        try {
                            reader.setInput(input, true, true);
                            int width = reader.getWidth(0), height = reader.getHeight(0);
                            if (width > 0 && height > 0) return new Image(key, width, height);
                        } finally { reader.dispose(); }
                    }
                } catch (java.io.IOException | RuntimeException ignored) {
                    // Missing or unreadable images share the installed default illustration.
                }
            }
            return new Image(DEFAULT, 428, 234);
        });
    }

    public static Crop crop(int sourceWidth, int sourceHeight, int width, int height) {
        if (sourceWidth < 1 || sourceHeight < 1 || width < 1 || height < 1) throw new IllegalArgumentException("Positive image bounds required");
        double ratio = width / (double) height;
        int sw = Math.max(1, Math.min(sourceWidth, (int) Math.round(sourceHeight * ratio)));
        int sh = Math.max(1, Math.min(sourceHeight, (int) Math.round(sourceWidth / ratio)));
        return new Crop((sourceWidth - sw) / 2F, (sourceHeight - sh) / 2F, sw, sh);
    }

    public static void draw(GuiGraphics graphics, String id, int x, int y, int width, int height) {
        if (width < 1 || height < 1) return;
        Image image = resolve(id);
        Crop crop = crop(image.width, image.height, width, height);
        graphics.blit(image.texture, x, y, width, height, crop.u, crop.v, crop.width, crop.height, image.width, image.height);
    }
}
