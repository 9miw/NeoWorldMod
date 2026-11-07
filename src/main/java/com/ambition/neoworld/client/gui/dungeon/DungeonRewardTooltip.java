package com.ambition.neoworld.client.gui.dungeon;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import com.ambition.neoworld.client.gui.common.NeoSystemIcons;
import com.ambition.neoworld.network.dungeon.DungeonRewardDetail;
import com.mojang.datafixers.util.Either;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Append after item-tooltip generation so attributes, mod headers and images stay intact. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class DungeonRewardTooltip {
    private DungeonRewardTooltip() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void append(RenderTooltipEvent.GatherComponents event) {
        if (!(Minecraft.getInstance().screen instanceof DungeonRoomScreen room)) return;
        var detail = room.rewardTooltipDetail(event.getItemStack());
        if (detail == null) return;
        for (Component line : lines(detail)) event.getTooltipElements().add(Either.left(line));
    }

    public static List<Component> lines(DungeonRewardDetail detail) {
        String chance = detail.exact() ? BigDecimal.valueOf(detail.chance()).stripTrailingZeros().toPlainString() + "% ต่อช่องรางวัล"
                : "ขึ้นกับเงื่อนไข Loot Table";
        String amount = !detail.exact() ? "ขึ้นกับ Loot Table"
                : (detail.minimum() == detail.maximum() ? "" + detail.minimum() : detail.minimum() + " - " + detail.maximum()) + " ชิ้น";
        return List.of(
                Component.literal("------------------------").withStyle(ChatFormatting.DARK_GRAY),
                NeoSystemIcons.label(NeoSystemIcons.Icon.MARKET, Component.literal("รางวัลดันเจี้ยน")).withStyle(ChatFormatting.GOLD),
                NeoGuiIcons.label(NeoGuiIcons.REFRESH, "โอกาสได้รับ: ").copy().withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(chance).withStyle(ChatFormatting.AQUA)),
                Component.literal("x ").withStyle(ChatFormatting.GOLD)
                        .append(Component.literal("จำนวนเมื่อได้รับ: ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(amount).withStyle(ChatFormatting.AQUA)),
                NeoGuiIcons.label(NeoGuiIcons.MEMBER, "แยกรายคน ไม่ต้องแย่งกัน").copy().withStyle(ChatFormatting.GREEN));
    }
}
