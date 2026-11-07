package com.ambition.neoworld.client.gui.widget;

import com.ambition.neoworld.client.gui.common.NeoPopupTextures;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Shared close action, retaining vanilla focus, keyboard input and narration. */
public final class NeoCloseButton {
    private NeoCloseButton() {}
    public static NeoButton create(int x, int y, int size, Runnable close) {
        return create(x, y, size, size, Component.literal("×"), Component.literal("ปิดหน้าต่าง (Esc)"), close);
    }

    /** Retain feature labels, rectangular hitboxes and callbacks; callers may keep their PNG skin. */
    public static NeoButton create(int x, int y, int width, int height, Component label, Component tooltip, Runnable close) {
        NeoButton button = new NeoButton(Button.builder(label, ignored -> close.run())
                .bounds(x, y, width, height)
                .createNarration(ignored -> tooltip.copy()));
        button.setTooltip(Tooltip.create(tooltip));
        // Neutral at rest; reserve the red treatment for hover/focus.
        return button.frameRenderer((graphics, current) -> {
            current.style(current.active && current.isHoveredOrFocused()
                    ? NeoButton.Style.DESTRUCTIVE : NeoButton.Style.STANDARD);
            NeoPopupTextures.button(graphics, current);
        });
    }
}
