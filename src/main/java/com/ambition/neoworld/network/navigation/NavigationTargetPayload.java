package com.ambition.neoworld.network.navigation;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Quest-owned destination only; never contains a computed route or particle points. */
public record NavigationTargetPayload(int questId,boolean turnIn,Goal goal) implements CustomPacketPayload {
    public record Goal(ResourceLocation dimension,Vec3 position,double radius) {
        public Goal {
            if(dimension==null||position==null||!Double.isFinite(position.x)||!Double.isFinite(position.y)||!Double.isFinite(position.z)
                    ||Math.abs(position.x)>30_000_000||Math.abs(position.y)>30_000_000||Math.abs(position.z)>30_000_000
                    ||!Double.isFinite(radius)||radius<1||radius>32)throw new IllegalArgumentException("Invalid quest target");
        }
    }
    public static final NavigationTargetPayload EMPTY=new NavigationTargetPayload(-1,false,null);
    public static final Type<NavigationTargetPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("neoworld","navigation_target"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NavigationTargetPayload> STREAM_CODEC=StreamCodec.of(
            (b,p)->{b.writeVarInt(p.questId);b.writeBoolean(p.turnIn);b.writeBoolean(p.goal!=null);if(p.goal!=null){b.writeResourceLocation(p.goal.dimension());
                b.writeDouble(p.goal.position().x);b.writeDouble(p.goal.position().y);b.writeDouble(p.goal.position().z);b.writeDouble(p.goal.radius());}},
            b->{int id=b.readVarInt();boolean turnIn=b.readBoolean();if(!b.readBoolean())return new NavigationTargetPayload(id,turnIn,null);
                return new NavigationTargetPayload(id,turnIn,new Goal(b.readResourceLocation(),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readDouble()));});
    public NavigationTargetPayload { if(questId < -1 || (questId==-1&&(goal!=null||turnIn)))throw new IllegalArgumentException("Invalid target state"); }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
