package com.ambition.neoworld.client.gui.pvp;

import java.util.HashMap;
import java.util.Map;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Optional resource-pack arena screenshots, with an explicitly illustrative fallback. */
final class PvpArenaImages {
    record Image(ResourceLocation texture,int width,int height,boolean illustrative) { }
    private static final ResourceLocation FALLBACK=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/pvp/arena_preview.png");
    private final Map<String,Image> images=new HashMap<>();
    void clear() { images.clear(); }
    Image get(String arena) {
        return images.computeIfAbsent(arena,id -> {
            if(id.matches("[a-z0-9_]{1,64}")) {
                var path=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/pvp/arenas/"+id+".png");
                var resource=Minecraft.getInstance().getResourceManager().getResource(path);
                if(resource.isPresent()) try(var stream=resource.get().open(); var image=NativeImage.read(stream)) {
                    return new Image(path,image.getWidth(),image.getHeight(),false);
                } catch(java.io.IOException ignored) { }
            }
            return new Image(FALLBACK,1672,941,true);
        });
    }
    static void draw(GuiGraphics graphics,Image image,int x,int y,int width,int height) {
        // Center-crop rather than distorting a screenshot to fit a differently shaped card.
        double ratio=width/(double)Math.max(1,height);
        int sw=image.width(),sh=image.height();
        if(sw/(double)sh>ratio) sw=Math.max(1,(int)Math.round(sh*ratio));
        else sh=Math.max(1,(int)Math.round(sw/ratio));
        graphics.blit(image.texture(),x,y,width,height,(image.width()-sw)/2F,(image.height()-sh)/2F,sw,sh,image.width(),image.height());
    }
}
