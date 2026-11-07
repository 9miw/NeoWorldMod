package com.ambition.neoworld.pvp.arena;

import com.ambition.neoworld.world.structure.StructureCaptureService;
import com.ambition.neoworld.integration.WorldStructureIntegration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Legacy PvP commands share the World queue, selections, reservations and catalog. */
public final class PvpCaptureService {
    private final StructureCaptureService service;
    private PvpCaptureService(MinecraftServer server) { WorldStructureIntegration.ensure(server);service=StructureCaptureService.get(server); }
    public static PvpCaptureService get(MinecraftServer server) { return new PvpCaptureService(server); }
    public static boolean allowed(CommandSourceStack source) { return source.hasPermission(2); }
    public static boolean isSaving(MinecraftServer server,String arena) {
        WorldStructureIntegration.ensure(server);
        return StructureCaptureService.get(server).isUpdating(PvpArenaData.worldPieces(PvpArenaData.get(server).pieces(arena)));
    }
    public static ItemStack tool() { return StructureCaptureService.tool(); }
    public static boolean isTool(ItemStack stack) { return StructureCaptureService.isTool(stack); }
    public static ResourceLocation mapId(String name) { return ResourceLocation.parse(name.contains(":")?name:"neoworld:pvp/"+name); }
    public void select(ServerPlayer player,boolean first,BlockPos point) { if(allowed(player.createCommandSourceStack()))service.select(player,first,point); }
    public String selectionInfo(ServerPlayer player) { return service.selectionInfo(player); }
    private String capture(ServerPlayer player,String name,boolean update) {
        if(!allowed(player.createCommandSourceStack()))return "ไม่มีสิทธิ์";
        try {
            var plan=service.selectionPlan(player);
            PvpCapturePlan.checkSize((long)plan.maximum().getX()-plan.minimum().getX()+1,
                    (long)plan.maximum().getY()-plan.minimum().getY()+1,(long)plan.maximum().getZ()-plan.minimum().getZ()+1);
            var destination=player.getServer().getLevel(com.ambition.neoworld.pvp.PvpDimensions.LEVEL);
            if(destination==null)return "ไม่พบมิติ PvP";
            if(32<destination.getMinBuildHeight() || 32L+plan.maximum().getY()-plan.minimum().getY()+1>destination.getMaxBuildHeight())
                return "ความสูงเกินพื้นที่วางในมิติ PvP";
            return update?service.update(player,mapId(name).toString()):service.start(player,mapId(name).toString());
        } catch(IllegalArgumentException exception) { return exception.getMessage(); }
    }
    public String start(ServerPlayer player,String name) { return capture(player,name,false); }
    public String update(ServerPlayer player,String name) { return capture(player,name,true); }
    public String requestDelete(ServerPlayer player,String name) { return allowed(player.createCommandSourceStack())?service.requestDelete(player,mapId(name).toString(),"/neow pvp admin structure"):"ไม่มีสิทธิ์"; }
    public String confirmDelete(ServerPlayer player,String name,String token) { return allowed(player.createCommandSourceStack())?service.confirmDelete(player,mapId(name).toString(),token):"ไม่มีสิทธิ์"; }
    public void cancel(java.util.UUID player) { service.cancel(player); }
    public void tick() { /* Driven exclusively by the shared World lifecycle. */ }
    public void stop() { /* Driven exclusively by the shared World lifecycle. */ }
}
