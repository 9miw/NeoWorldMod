package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.NeoWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** A real ItemStack thumbnail beside wrapped, styled dynamic text. */
public final class NeoItemTooltipHeader implements ClientTooltipComponent {
    public record Data(ItemStack stack, FormattedText name, FormattedText type, int maxWidth) implements TooltipComponent {}
    private final ItemStack stack;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final int width;

    private NeoItemTooltipHeader(Data data) {
        stack = data.stack();
        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(1, data.maxWidth() - 24);
        lines.addAll(font.split(data.name(), textWidth));
        lines.addAll(font.split(data.type(), textWidth));
        width = 24 + lines.stream().mapToInt(font::width).max().orElse(0);
    }

    @Override public int getHeight() { return Math.max(20, lines.size() * 12) + 4; }
    @Override public int getWidth(Font font) { return width; }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f pose, MultiBufferSource.BufferSource buffers) {
        for (int i = 0; i < lines.size(); i++) {
            font.drawInBatch(lines.get(i), x + 24, y + i * 12, 0xFFFFFF, false, pose, buffers,
                    Font.DisplayMode.NORMAL, 0, 15728880);
        }
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        graphics.renderItem(stack, x+2, y + 2);
        graphics.fill(x, y + getHeight() - 2, x + width, y + getHeight() - 1, 0xFF59636A);
    }

    @EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterClientTooltipComponentFactoriesEvent event) {
            event.register(Data.class, NeoItemTooltipHeader::new);
        }
    }
}
