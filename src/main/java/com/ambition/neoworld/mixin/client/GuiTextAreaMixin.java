package com.ambition.neoworld.mixin.client;

import com.ambition.neoworld.client.gui.integration.NpcTextAreaFont;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import noppes.npcs.shared.client.gui.components.GuiTextArea;
import noppes.npcs.shared.client.util.TrueTypeFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiTextArea.class, remap = false)
public abstract class GuiTextAreaMixin {
    @Shadow private static TrueTypeFont font;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void neoworld$useMinecraftFont(CallbackInfo ci) {
        font = new NpcTextAreaFont();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/shared/client/util/TrueTypeFont;draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)V"))
    private void neoworld$drawMinecraftText(TrueTypeFont original, PoseStack pose,
                                           String text, float x, float y, int color,
                                           GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(Minecraft.getInstance().font, NpcTextAreaFont.format(text),
                (int) x, (int) y, color, false);
    }
}
