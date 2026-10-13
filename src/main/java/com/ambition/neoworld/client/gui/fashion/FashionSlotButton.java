package com.ambition.neoworld.client.gui.fashion;

import com.ambition.neoworld.NeoWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.resources.ResourceLocation;

/** Read-only wardrobe selection; never an inventory container slot. */
final class FashionSlotButton extends Button {
    private static final ResourceLocation NORMAL = texture("normal");
    private static final ResourceLocation HOVER = texture("hover");
    private static final ResourceLocation SELECTED = texture("selected");
    private static final ResourceLocation EQUIPPED = texture("equipped");
    private boolean selected;
    private boolean equipped;

    FashionSlotButton(Builder builder) { super(builder); }

    void state(boolean selected, boolean equipped) {
        this.selected = selected;
        this.equipped = equipped;
    }

    private static ResourceLocation texture(String state) {
        return ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "textures/gui/fashion/fashion_slot_" + state + ".png");
    }

    static void renderEmpty(GuiGraphics graphics, int x, int y, int size) {
        graphics.blit(NORMAL, x, y, size, size, 0, 0, 128, 128, 128, 128);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ResourceLocation texture = selected ? SELECTED : isHoveredOrFocused() ? HOVER : equipped ? EQUIPPED : NORMAL;
        RenderSystem.enableBlend();
        graphics.blit(texture, getX(), getY(), getWidth(), getHeight(), 0, 0, 128, 128, 128, 128);
        if (equipped && texture != EQUIPPED) {
            int inset = Math.max(2, getWidth() / 16);
            int badgeHeight = Math.max(3, getHeight() * 18 / 128);
            graphics.blit(EQUIPPED, getX() + inset, getY() + getHeight() - inset - badgeHeight,
                    getWidth() - inset * 2, badgeHeight, 8, 100, 112, 18, 128, 128);
        }
    }
}
