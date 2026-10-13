package com.ambition.neoworld.network;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.trade.TradePlan.Offer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record TradeStatePayload(UUID sessionId, int revision, Phase phase, String partner,
        boolean requester, List<Offer> mine, List<Offer> theirs, long mySilver, long theirSilver,
        long balance, boolean myConfirmed, boolean theirConfirmed, int remainingTicks,
        List<ItemStack> inventory, String message) implements CustomPacketPayload {
    public enum Phase { REQUESTED, EDITING, COUNTDOWN, COMMITTED, CANCELLED }
    public static final Type<TradeStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NeoWorld.MODID, "trade_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TradeStatePayload> STREAM_CODEC = StreamCodec.of(TradeStatePayload::encode, TradeStatePayload::decode);

    public TradeStatePayload {
        mine = mine.stream().map(o -> new Offer(o.slot(), o.item())).toList();
        theirs = theirs.stream().map(o -> new Offer(o.slot(), o.item())).toList();
        inventory = inventory.stream().map(ItemStack::copy).toList();
    }
    private static void offers(RegistryFriendlyByteBuf b, List<Offer> offers) {
        b.writeVarInt(offers.size());
        for (Offer o : offers) { b.writeVarInt(o.slot()); ItemStack.STREAM_CODEC.encode(b, o.item()); }
    }
    private static List<Offer> offers(RegistryFriendlyByteBuf b) {
        int size = b.readVarInt();
        if (size < 0 || size > 9) throw new IllegalArgumentException("Invalid trade offer size");
        List<Offer> result = new ArrayList<>();
        for (int i = 0; i < size; i++) result.add(new Offer(b.readVarInt(), ItemStack.STREAM_CODEC.decode(b)));
        return result;
    }
    private static void encode(RegistryFriendlyByteBuf b, TradeStatePayload p) {
        b.writeUUID(p.sessionId); b.writeVarInt(p.revision); b.writeEnum(p.phase); b.writeUtf(p.partner, 16);
        b.writeBoolean(p.requester); offers(b, p.mine); offers(b, p.theirs);
        b.writeLong(p.mySilver); b.writeLong(p.theirSilver); b.writeLong(p.balance);
        b.writeBoolean(p.myConfirmed); b.writeBoolean(p.theirConfirmed); b.writeVarInt(p.remainingTicks);
        b.writeVarInt(p.inventory.size());
        for (ItemStack item : p.inventory) ItemStack.OPTIONAL_STREAM_CODEC.encode(b, item);
        b.writeUtf(p.message, 256);
    }
    private static TradeStatePayload decode(RegistryFriendlyByteBuf b) {
        UUID id = b.readUUID(); int revision = b.readVarInt(); Phase phase = b.readEnum(Phase.class);
        String partner = b.readUtf(16); boolean requester = b.readBoolean();
        List<Offer> mine = offers(b), theirs = offers(b);
        long mySilver = b.readLong(), theirSilver = b.readLong(), balance = b.readLong();
        boolean myConfirmed = b.readBoolean(), theirConfirmed = b.readBoolean(); int ticks = b.readVarInt();
        int size = b.readVarInt();
        if (size != 36) throw new IllegalArgumentException("Invalid trade inventory size");
        List<ItemStack> inventory = new ArrayList<>();
        for (int i = 0; i < size; i++) inventory.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(b));
        return new TradeStatePayload(id, revision, phase, partner, requester, mine, theirs, mySilver,
                theirSilver, balance, myConfirmed, theirConfirmed, ticks, inventory, b.readUtf(256));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
