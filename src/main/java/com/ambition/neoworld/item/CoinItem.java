package com.ambition.neoworld.item;

import java.util.List;
import com.ambition.neoworld.economy.CurrencyMath;

import com.ambition.neoworld.registry.ModDataComponents;
import com.ambition.neoworld.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// คลาสสำหรับไอเทมเหรียญสกุลเงิน (Silver, Gold, Diamond)
public class CoinItem extends Item {
    public enum CoinType {
        SILVER("Silver", ChatFormatting.GRAY),
        GOLD("Gold", ChatFormatting.GOLD),
        DIAMOND("Diamond", ChatFormatting.AQUA);

        private final String displayName;
        private final ChatFormatting format;

        CoinType(String displayName, ChatFormatting format) {
            this.displayName = displayName;
            this.format = format;
        }

        public String getDisplayName() {
            return displayName;
        }

        public ChatFormatting getFormat() {
            return format;
        }
    }

    private final CoinType coinType;

    public CoinItem(CoinType coinType, Properties properties) {
        super(properties);
        this.coinType = coinType;
    }

    public CoinType getCoinType() {
        return this.coinType;
    }

    /**
     * ดึงมูลค่าของเหรียญต่อ 1 ชิ้นจาก ItemStack (ถ้าไม่ได้กำหนดจะคืนค่า 1L)
     */
    public static long getCoinValue(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 1L;
        return stack.getOrDefault(ModDataComponents.COIN_VALUE.get(), 1L);
    }

    /**
     * คืนมูลค่ารวม หรือ -1 เมื่อค่าผิดปกติหรือเกินขอบเขต long
     */
    public static long getTotalValue(ItemStack stack) {
        return stack == null || stack.isEmpty() ? -1L : CurrencyMath.total(getCoinValue(stack), stack.getCount());
    }

    // กำหนดมูลค่าต่อชิ้น โดยผู้รับเหรียญยังต้องตรวจมูลค่ารวมก่อนทำรายการ
    public static void setCoinValue(ItemStack stack, long value) {
        if (stack != null && !stack.isEmpty()) {
            stack.set(ModDataComponents.COIN_VALUE.get(), Math.max(1L, value));
        }
    }

    /**
     * สร้าง ItemStack ของเหรียญพร้อมกำหนดมูลค่าต่อชิ้นและจำนวน
     */
    public static ItemStack createStack(CoinType type, long valuePerCoin, int count) {
        Item item = switch (type) {
            case SILVER -> ModItems.SILVER_COIN.get();
            case GOLD -> ModItems.GOLD_COIN.get();
            case DIAMOND -> ModItems.DIAMOND_COIN.get();
        };

        ItemStack stack = new ItemStack(item, Math.max(1, count));
        if (valuePerCoin > 1) {
            setCoinValue(stack, valuePerCoin);
        }
        return stack;
    }

    // แสดงคำอธิบาย (Tooltip) ใต้ชื่อไอเทม
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        long value = getCoinValue(stack);

        // แสดงมูลค่าเหรียญ
        if (value > 1) {
            tooltipComponents.add(Component.literal("§eมูลค่า: " + this.coinType.getFormat() + String.format("%,d", value) + " " + this.coinType.getDisplayName())
                    .withStyle(ChatFormatting.BOLD));
            if (stack.getCount() > 1) {
                tooltipComponents.add(Component.literal("§7(รวมทั้งกอง: " + this.coinType.getFormat() + (getTotalValue(stack) < 0 ? "INVALID" : String.format("%,d", getTotalValue(stack))) + " " + this.coinType.getDisplayName() + "§7)"));
            }
        }

        switch (this.coinType) {
            case SILVER -> {
                tooltipComponents.add(Component.translatable("tooltip.neoworld.silver_coin.desc").withStyle(ChatFormatting.GRAY));
            }
            case GOLD -> {
                tooltipComponents.add(Component.translatable("tooltip.neoworld.gold_coin.desc").withStyle(ChatFormatting.GOLD));
            }
            case DIAMOND -> {
                tooltipComponents.add(Component.translatable("tooltip.neoworld.diamond_coin.desc").withStyle(ChatFormatting.AQUA));
            }
        }
    }
}

