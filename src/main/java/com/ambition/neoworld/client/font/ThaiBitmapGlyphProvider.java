package com.ambition.neoworld.client.font;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Restores the built-in Thai and Roboto bitmap metrics without loading TTFs in Minecraft. */
public final class ThaiBitmapGlyphProvider implements GlyphProvider {
    private final GlyphProvider delegate;
    private final Map<Integer, Metrics> metrics;

    private ThaiBitmapGlyphProvider(GlyphProvider delegate, Map<Integer, Metrics> metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    public static GlyphProvider wrap(GlyphProvider delegate, ResourceManager resources,
                                    ResourceLocation texture) throws IOException {
        if (!texture.getNamespace().equals("neoworld")) return delegate;
        String metricsPath;
        if (texture.getPath().startsWith("font/thai/")) {
            metricsPath = "thaifix_metrics.json";
        } else if (texture.getPath().equals("font/roboto/regular.png")) {
            metricsPath = "roboto_metrics.json";
        } else {
            return delegate;
        }
        Map<Integer, Metrics> metrics = new HashMap<>();
        try (var reader = new InputStreamReader(resources.open(ResourceLocation.fromNamespaceAndPath(
                "neoworld", metricsPath)), StandardCharsets.UTF_8)) {
            var data = JsonParser.parseReader(reader).getAsJsonObject();
            for (var entry : data.entrySet()) {
                var value = entry.getValue().getAsJsonObject();
                metrics.put(Integer.parseInt(entry.getKey()), new Metrics(
                        value.get("advance").getAsFloat(), value.get("bearingLeft").getAsFloat()));
            }
        } catch (IOException | RuntimeException failure) {
            delegate.close();
            throw failure;
        }
        return new ThaiBitmapGlyphProvider(delegate, metrics);
    }

    @Override
    public GlyphInfo getGlyph(int character) {
        GlyphInfo glyph = delegate.getGlyph(character);
        Metrics metric = metrics.get(character);
        return glyph == null || metric == null ? glyph : new PositionedGlyph(glyph, metric);
    }

    @Override
    public IntSet getSupportedGlyphs() { return delegate.getSupportedGlyphs(); }

    @Override
    public void close() { delegate.close(); }

    private record Metrics(float advance, float bearingLeft) {}

    private record PositionedGlyph(GlyphInfo delegate, Metrics metrics) implements GlyphInfo {
        @Override
        public float getAdvance() { return metrics.advance(); }

        @Override
        public float getBoldOffset() { return metrics.advance() == 0 ? 0 : delegate.getBoldOffset(); }

        @Override
        public float getShadowOffset() { return delegate.getShadowOffset(); }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> baker) {
            return delegate.bake(sheet -> baker.apply(new SheetGlyphInfo() {
                @Override public int getPixelWidth() { return sheet.getPixelWidth(); }
                @Override public int getPixelHeight() { return sheet.getPixelHeight(); }
                @Override public void upload(int x, int y) { sheet.upload(x, y); }
                @Override public boolean isColored() { return sheet.isColored(); }
                @Override public float getOversample() { return sheet.getOversample(); }
                @Override public float getBearingTop() { return sheet.getBearingTop(); }
                @Override public float getBearingLeft() { return metrics.bearingLeft(); }
            }));
        }
    }
}
