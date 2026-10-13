package com.ambition.neoworld.mixin;

import com.ambition.neoworld.client.font.ThaiBitmapGlyphProvider;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.BitmapProvider;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;

@Mixin(BitmapProvider.Definition.class)
public class ThaiBitmapDefinitionMixin {
    @Inject(method = "load", at = @At("RETURN"), cancellable = true)
    private void neoworld$restoreThaiMetrics(ResourceManager resources,
                                            CallbackInfoReturnable<GlyphProvider> callback) throws IOException {
        BitmapProvider.Definition definition = (BitmapProvider.Definition) (Object) this;
        callback.setReturnValue(ThaiBitmapGlyphProvider.wrap(
                callback.getReturnValue(), resources, definition.file()));
    }
}
