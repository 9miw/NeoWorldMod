package com.ambition.neoworld.ranking;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;

/** Visual-only daily snapshot. No stored item can enter an inventory. */
public record RankingAppearance(long day,String texture,String signature,List<ItemStack> equipment,Map<FashionSlot,ItemStack> fashion) {
    public static final EquipmentSlot[] EQUIPMENT={EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND,EquipmentSlot.FEET,
            EquipmentSlot.LEGS,EquipmentSlot.CHEST,EquipmentSlot.HEAD};
    private static final ZoneId ZONE=ZoneId.of("Asia/Bangkok");
    public static final Codec<RankingAppearance> CODEC=RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("day").forGetter(RankingAppearance::day),
            Codec.STRING.optionalFieldOf("texture","").forGetter(RankingAppearance::texture),
            Codec.STRING.optionalFieldOf("signature","").forGetter(RankingAppearance::signature),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("equipment").forGetter(RankingAppearance::equipment),
            Codec.unboundedMap(FashionSlot.CODEC,ItemStack.OPTIONAL_CODEC).fieldOf("fashion").forGetter(RankingAppearance::fashion)
    ).apply(i,RankingAppearance::new));
    public static final RankingAppearance EMPTY=new RankingAppearance(-1,"","",Collections.nCopies(6,ItemStack.EMPTY),Map.of());
    public static final StreamCodec<RegistryFriendlyByteBuf,RankingAppearance> STREAM_CODEC=StreamCodec.of((b,p) -> {
        b.writeLong(p.day); b.writeUtf(p.texture,16384); b.writeUtf(p.signature,2048);
        for(var stack:p.equipment) ItemStack.OPTIONAL_STREAM_CODEC.encode(b,stack);
        b.writeVarInt(p.fashion.size()); p.fashion.forEach((slot,stack) -> {
            b.writeEnum(slot); ItemStack.OPTIONAL_STREAM_CODEC.encode(b,stack);
        });
    },b -> {
        long day=b.readLong(); String texture=b.readUtf(16384),signature=b.readUtf(2048);
        var equipment=new java.util.ArrayList<ItemStack>(6);
        for(int i=0;i<6;i++) equipment.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
        int count=b.readVarInt(); if(count<0 || count>FashionSlot.values().length) throw new IllegalArgumentException("Invalid appearance slots");
        var fashion=new EnumMap<FashionSlot,ItemStack>(FashionSlot.class);
        for(int i=0;i<count;i++) {
            var slot=b.readEnum(FashionSlot.class);
            if(fashion.put(slot,ItemStack.OPTIONAL_STREAM_CODEC.decode(b))!=null) throw new IllegalArgumentException("Duplicate appearance slot");
        }
        return new RankingAppearance(day,texture,signature,equipment,fashion);
    });
    public RankingAppearance {
        if(day< -1 || texture==null || signature==null || texture.length()>16384 || signature.length()>2048
                || equipment.size()!=6 || fashion.size()>FashionSlot.values().length) throw new IllegalArgumentException("Invalid ranking appearance");
        equipment=equipment.stream().map(RankingAppearance::visualStack).toList();
        var copy=new EnumMap<FashionSlot,ItemStack>(FashionSlot.class);
        fashion.forEach((slot,stack) -> { if(!stack.isEmpty()) copy.put(slot,visualStack(stack)); });
        fashion=Collections.unmodifiableMap(copy);
    }
    public static long day(Instant now) { return now.atZone(ZONE).toLocalDate().toEpochDay(); }
    public boolean needsCapture(Instant now) { return day!=day(now); }
    /** Keep appearance only: never persist or broadcast container contents, books or arbitrary custom NBT. */
    private static ItemStack visualStack(ItemStack source) {
        if(source.isEmpty()) return ItemStack.EMPTY;
        var display=new ItemStack(source.getItem(),1);
        copy(source,display,DataComponents.CUSTOM_MODEL_DATA);
        copy(source,display,DataComponents.DYED_COLOR);
        copy(source,display,DataComponents.TRIM);
        copy(source,display,DataComponents.DAMAGE);
        display.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,source.hasFoil());
        if(com.ambition.neoworld.registry.ModDataComponents.UPGRADE_LEVEL.isBound()) {
            var type=com.ambition.neoworld.registry.ModDataComponents.UPGRADE_LEVEL.get();
            Integer level=source.get(type);
            if(level!=null) display.set(type,Math.clamp(level,0,15));
        }
        return display;
    }
    private static <T> void copy(ItemStack source,ItemStack target,net.minecraft.core.component.DataComponentType<T> type) {
        T value=source.get(type); if(value!=null) target.set(type,value);
    }
    public GameProfile profile(java.util.UUID id,String name) {
        var profile=new GameProfile(id,name);
        if(!texture.isEmpty()) profile.getProperties().put("textures",signature.isEmpty()
                ? new Property("textures",texture) : new Property("textures",texture,signature));
        return profile;
    }
}
