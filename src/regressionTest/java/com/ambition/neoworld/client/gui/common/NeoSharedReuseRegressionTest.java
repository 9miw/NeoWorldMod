package com.ambition.neoworld.client.gui.common;

import java.util.HashSet;
import net.minecraft.network.chat.Component;

public final class NeoSharedReuseRegressionTest {
    public static void main(String[] args) {
        var ids = new HashSet<String>();
        for (var icon : NeoBitmapIcon.values()) {
            if (!ids.add(icon.font() + "/" + icon.codepoint())) throw new AssertionError("Duplicate icon identity");
            if (!icon.component().getStyle().getFont().equals(icon.font())) throw new AssertionError("Wrong font");
        }
        if (NeoBitmapIcon.GUI_ICONS_MEMBER.font().equals(NeoBitmapIcon.DUNGEON_HUD_MEMBER.font()))
            throw new AssertionError("HUD and control metrics must remain separate");
        var first = NeoEconomyText.balances("1", "3", NeoEconomyText.Order.SILVER_FIRST);
        var reverse = NeoEconomyText.balances("1", "3", NeoEconomyText.Order.DIAMOND_FIRST);
        check(first, "\uE0461 \uE0483");
        check(reverse, "\uE0483 \uE0461");
        check(NeoEconomyText.balances("0", "0", NeoEconomyText.Order.SILVER_FIRST), "\uE0460 \uE0480");
        var parts = first.getSiblings();
        if (!parts.getFirst().getSiblings().getFirst().getStyle().getFont().equals(NeoBitmapFonts.CURRENCY))
            throw new AssertionError("Currency icon lost explicit font");
        if (parts.getFirst().getSiblings().get(1).getStyle().getFont().equals(NeoBitmapFonts.CURRENCY))
            throw new AssertionError("Amount inherited bitmap font");
        if (parts.getFirst().getSiblings().get(1).getStyle().getColor().getValue() != 0xFFFFFF)
            throw new AssertionError("Silver amount changed color");
        if (parts.get(2).getSiblings().get(1).getStyle().getColor().getValue() != 0x55FFFF)
            throw new AssertionError("Diamond amount changed color");
        int[] closed = {0};
        var close = com.ambition.neoworld.client.gui.widget.NeoCloseButton.create(10, 20, 18, 16,
                Component.literal("X"), Component.literal("cancel trade"), () -> closed[0]++);
        if (closed[0] != 0 || close.getX() != 10 || close.getY() != 20 || close.getWidth() != 18 || close.getHeight() != 16)
            throw new AssertionError("Close control moved or fired early");
        close.onPress();
        if (closed[0] != 1) throw new AssertionError("Close callback must fire exactly once");
        System.out.println("Shared GUI reuse regression passed: " + ids.size() + " icon variants");
    }
    private static void check(Component actual, String expected) {
        if (!actual.getString().equals(expected)) throw new AssertionError(actual.getString());
    }
}
