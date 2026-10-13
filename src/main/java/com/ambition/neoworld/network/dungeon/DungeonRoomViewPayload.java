package com.ambition.neoworld.network.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record DungeonRoomViewPayload(boolean open, int roomId, String title, String structure,
        int capacity, int minimumLevel, int seconds, int waves, boolean waiting, List<Member> members)
        implements CustomPacketPayload {
    public static final Type<DungeonRoomViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld", "dungeon_room_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DungeonRoomViewPayload> STREAM_CODEC = StreamCodec.of(DungeonRoomViewPayload::encode, DungeonRoomViewPayload::decode);
    public DungeonRoomViewPayload {
        members = List.copyOf(members);
        if (members.size() > 6) throw new IllegalArgumentException("Too many room members");
    }
    private static void encode(RegistryFriendlyByteBuf b, DungeonRoomViewPayload p) {
        b.writeBoolean(p.open); b.writeVarInt(p.roomId); b.writeUtf(p.title, 32767); b.writeUtf(p.structure, 32767);
        b.writeVarInt(p.capacity); b.writeVarInt(p.minimumLevel); b.writeVarInt(p.seconds); b.writeVarInt(p.waves);
        b.writeBoolean(p.waiting); b.writeVarInt(p.members.size());
        for (Member m : p.members) {
            b.writeUUID(m.id); b.writeUtf(m.name, 64); b.writeVarInt(m.level);
            b.writeBoolean(m.owner); b.writeBoolean(m.ready); b.writeBoolean(m.online);
        }
    }
    private static DungeonRoomViewPayload decode(RegistryFriendlyByteBuf b) {
        boolean open = b.readBoolean(); int id = b.readVarInt(); String title = b.readUtf(32767), structure = b.readUtf(32767);
        int capacity = b.readVarInt(), level = b.readVarInt(), seconds = b.readVarInt(), waves = b.readVarInt();
        boolean waiting = b.readBoolean(); int count = b.readVarInt();
        if (count < 0 || count > 6) throw new IllegalArgumentException("Invalid room size");
        List<Member> members = new ArrayList<>(count);
        for (int i = 0; i < count; i++) members.add(new Member(b.readUUID(), b.readUtf(64), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
        return new DungeonRoomViewPayload(open, id, title, structure, capacity, level, seconds, waves, waiting, members);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public record Member(UUID id, String name, int level, boolean owner, boolean ready, boolean online) {}
}
