package com.ambition.neoworld.dungeon.structure;

import com.ambition.neoworld.world.structure.StructureCaptureService;
import com.ambition.neoworld.integration.WorldStructureIntegration;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Legacy command adapter; selection, jobs and catalog belong to World. */
public final class DungeonCaptureService {
    private final StructureCaptureService service;
    private DungeonCaptureService(MinecraftServer server) { WorldStructureIntegration.ensure(server);service=StructureCaptureService.get(server); }
    public static DungeonCaptureService get(MinecraftServer server) { return new DungeonCaptureService(server); }
    public static boolean allowed(CommandSourceStack source) { return com.ambition.neoworld.permission.ModPermissions.hasPermission(source,com.ambition.neoworld.permission.ModPermissions.COMMAND_DUNGEON_ADMIN,2); }
    public static ItemStack tool() { return StructureCaptureService.tool(); }
    public static boolean isTool(ItemStack stack) { return StructureCaptureService.isTool(stack); }
    public static ResourceLocation mapId(String name) { return ResourceLocation.parse(name.contains(":")?name:"neoworld:dungeon/"+name); }
    public void select(ServerPlayer player,boolean first,BlockPos point) { if(allowed(player.createCommandSourceStack()))service.select(player,first,point); }
    public String selectionInfo(ServerPlayer player) { return service.selectionInfo(player); }
    private String capture(ServerPlayer player,String name,boolean update) {
        if(!allowed(player.createCommandSourceStack()))return "ไม่มีสิทธิ์";
        try {
            var plan=service.selectionPlan(player);
            if((long)plan.maximum().getX()-plan.minimum().getX()+1>com.ambition.neoworld.dungeon.instance.DungeonInstanceManager.SLOT_SPACING_BLOCKS)
                return "ขนาดแกน X เกินระยะห่าง slot ดันเจี้ยน";
            var destination=player.getServer().getLevel(com.ambition.neoworld.registry.ModDimensionRegistries.DUNGEON_LEVEL);
            if(destination!=null && (64<destination.getMinBuildHeight() || 64L+plan.maximum().getY()-plan.minimum().getY()+1>destination.getMaxBuildHeight()))
                return "ความสูงเกินพื้นที่วางในมิติดันเจี้ยน";
            return update?service.update(player,mapId(name).toString()):service.start(player,mapId(name).toString());
        } catch(IllegalArgumentException exception) { return exception.getMessage(); }
    }
    public String start(ServerPlayer player,String name) { return capture(player,name,false); }
    public String update(ServerPlayer player,String name) { return capture(player,name,true); }
    public String requestDelete(ServerPlayer player,String name) { return allowed(player.createCommandSourceStack())?service.requestDelete(player,mapId(name).toString(),"/neow dungeon admin structure"):"ไม่มีสิทธิ์"; }
    public String confirmDelete(ServerPlayer player,String name,String token) { return allowed(player.createCommandSourceStack())?service.confirmDelete(player,mapId(name).toString(),token):"ไม่มีสิทธิ์"; }
    public void cancel(java.util.UUID player) { service.cancel(player); }
    public void clear(ServerPlayer player) { service.clear(player); }
    public void tick() { /* Driven exclusively by the shared World lifecycle. */ }
    public void stop() { /* Driven exclusively by the shared World lifecycle. */ }
}
