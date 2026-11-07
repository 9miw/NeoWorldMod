package com.ambition.neoworld.pvp.room;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Arena Gold titles and inline rating arrows; scores and chat remain ordinary text. */
public final class PvpTitles {
    public static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("neoworld","pvp_titles");
    public static final ResourceLocation RATING_FONT=ResourceLocation.fromNamespaceAndPath("neoworld","pvp_rating");
    private PvpTitles() {}
    private static Component glyph(int codepoint) {
        return Component.literal(Character.toString(codepoint)).withStyle(style -> style.withFont(FONT).withColor(0xFFFFFF));
    }
    public static Component result(boolean won) { return glyph(won ? 0xE400 : 0xE401); }
    public static Component countdown(int seconds) {
        if(seconds<1 || seconds>5) return Component.literal(Integer.toString(seconds));
        return glyph(0xE402+5-seconds);
    }
    public static Component fight() { return glyph(0xE407); }
    public static Component ratingChange(int before,int after) {
        int change=after-before;
        var result=Component.literal("Rating ");
        if(change!=0) {
            result.append(Component.literal(change>0 ? "\uE408" : "\uE409")
                    .withStyle(style -> style.withFont(RATING_FONT).withColor(0xFFFFFF)));
            result.append(" ");
        }
        String amount=change==0 ? "±0" : String.format(java.util.Locale.ROOT,"%+,d",change);
        return result.append(Component.literal(amount).withStyle(change>0 ? net.minecraft.ChatFormatting.GREEN
                : change<0 ? net.minecraft.ChatFormatting.RED : net.minecraft.ChatFormatting.GRAY))
                .append(String.format(java.util.Locale.ROOT," (%,d)",after));
    }
}
