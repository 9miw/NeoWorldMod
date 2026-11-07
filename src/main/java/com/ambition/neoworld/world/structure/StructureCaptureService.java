package com.ambition.neoworld.world.structure;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Temporary selections and a server-wide one-template-per-tick capture queue. */
public final class StructureCaptureService {
    private static final String TOOL = "neoworld_world_structure_tool";
    private static final Map<MinecraftServer, StructureCaptureService> SERVICES = new WeakHashMap<>();
    private record Selection(ServerLevel level, BlockPos first, BlockPos second) {}
    private final MinecraftServer server;
    private final Map<UUID, Selection> selections = new HashMap<>();
    private final Map<UUID, Job> jobs = new LinkedHashMap<>();
    private final Set<ResourceLocation> reserved = new HashSet<>();
    private record DeleteRequest(ResourceLocation name, List<StructurePiece> pieces, String token, long expires) {}
    private final Map<UUID, DeleteRequest> deletions = new HashMap<>();
    private StructureCaptureService(MinecraftServer server) { this.server = server; }
    public static StructureCaptureService get(MinecraftServer server) { return SERVICES.computeIfAbsent(server, StructureCaptureService::new); }
    public static boolean allowed(CommandSourceStack source) { return StructureConsumers.authorized(source); }
    public static boolean isTool(ItemStack stack) {
        if(!stack.is(Items.STICK))return false;
        var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        return tag.getBoolean(TOOL) || tag.getBoolean("neoworld_dungeon_capture_tool") || tag.getBoolean("neoworld_pvp_capture_tool");
    }
    public static ItemStack tool() {
        ItemStack stack = new ItemStack(Items.STICK);
        CompoundTag tag = new CompoundTag(); tag.putBoolean(TOOL, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("World Structure Tool").withStyle(net.minecraft.ChatFormatting.GOLD));
        return stack;
    }

    public void select(ServerPlayer player, boolean first, BlockPos position) {
        if (!allowed(player.createCommandSourceStack())) return;
        Selection old = selections.get(player.getUUID());
        if (old == null || old.level != player.serverLevel()) old = new Selection(player.serverLevel(), null, null);
        BlockPos point = position.immutable();
        Selection next = new Selection(player.serverLevel(), first ? point : old.first, first ? old.second : point);
        if (next.equals(old)) return;
        selections.put(player.getUUID(), next);
        player.sendSystemMessage(Component.literal("§b[Structure] มุม " + (first ? 1 : 2) + ": " + point.toShortString()));
    }

    public String selectionInfo(ServerPlayer player) {
        Selection selection = selections.get(player.getUUID());
        if (selection == null) return "ยังไม่ได้เลือกพื้นที่";
        String result = selection.level.dimension().location() + " | มุม 1: " + point(selection.first) + " | มุม 2: " + point(selection.second);
        if (selection.first != null && selection.second != null) {
            try {
                var plan = StructureCapturePlan.create(selection.first, selection.second, ResourceLocation.fromNamespaceAndPath("neoworld", "selection"));
                result += " | " + plan.volume() + " บล็อก / " + plan.parts().size() + " ชิ้น";
            } catch (IllegalArgumentException exception) { result += " | " + exception.getMessage(); }
        }
        return result;
    }
    private static String point(BlockPos pos) { return pos == null ? "ยังไม่เลือก" : pos.toShortString(); }
    public void clear(ServerPlayer player) { selections.remove(player.getUUID()); }
    public StructureCapturePlan selectionPlan(ServerPlayer player) {
        Selection selection=selections.get(player.getUUID());
        if(selection==null || selection.first==null || selection.second==null || selection.level!=player.serverLevel())
            throw new IllegalArgumentException("เลือกมุมทั้ง 2 ในโลกปัจจุบันก่อน");
        return StructureCapturePlan.create(selection.first,selection.second,ResourceLocation.fromNamespaceAndPath("neoworld","selection"));
    }
    public boolean isReserved(List<StructurePiece> pieces) { return pieces.stream().anyMatch(p -> reserved.contains(p.templateId())); }

    public String start(ServerPlayer player, String name) {
        return start(player, name, false);
    }

    public String update(ServerPlayer player, String name) {
        return start(player, name, true);
    }

    public static ResourceLocation mapId(String name) {
        return ResourceLocation.parse(name.contains(":") ? name : "neoworld:structures/" + name);
    }

    private String start(ServerPlayer player, String name, boolean update) {
        if (!allowed(player.createCommandSourceStack())) return "ไม่มีสิทธิ์จัดการ Structure";
        if (jobs.containsKey(player.getUUID())) return "คุณมีงานบันทึกกำลังทำอยู่";
        Selection selection = selections.get(player.getUUID());
        if (selection == null || selection.first == null || selection.second == null) return "กรุณาเลือกมุมทั้ง 2 ก่อน";
        if (selection.level != player.serverLevel()) return "กรุณากลับไปโลกที่เลือกพื้นที่";
        try {
            ResourceLocation mapName = mapId(name);
            List<StructurePiece> original = StructureCatalogData.get(server).map(mapName).orElse(null);
            if (update && original == null) return "ไม่พบแผนที่ " + name;
            if (update) {
                String error = checkUpdate(original);
                if (error != null) return error;
            }
            ResourceLocation prefix = update ? ResourceLocation.fromNamespaceAndPath(mapName.getNamespace(),
                    mapName.getPath() + "/rev_" + UUID.randomUUID().toString().replace("-", "")) : mapName;
            StructureCapturePlan plan = StructureCapturePlan.create(selection.first, selection.second, prefix);
            if (!update && original != null) return "มีแผนที่ชื่อนี้แล้ว ใช้ structure update";
            if (busy(mapName)) return "แผนที่ชื่อนี้กำลังบันทึก";
            if (plan.minimum().getY() < selection.level.getMinBuildHeight() || plan.maximum().getY() >= selection.level.getMaxBuildHeight())
                return "พื้นที่อยู่นอกความสูงที่โลกสร้างได้";
            if (!loaded(selection.level, plan.minimum(), plan.maximum())) return "พื้นที่บางส่วนยังไม่โหลด กรุณาโหลดพื้นที่ก่อน";
            var manager = selection.level.getStructureManager();
            for (var part : plan.parts()) {
                if (reserved.contains(part.id()) || Files.exists(manager.createAndValidatePathToGeneratedStructure(part.id(), ".nbt"))
                        || manager.get(part.id()).isPresent()) return "ชื่อไฟล์มีอยู่แล้ว/กำลังบันทึก: " + part.id() + " กรุณาใช้ชื่อใหม่";
            }
            jobs.put(player.getUUID(), new Job(player.getUUID(), selection.level, mapName, plan, original));
            plan.parts().forEach(part -> reserved.add(part.id()));
            player.sendSystemMessage(Component.literal("§e[Structure] เริ่มบันทึก " + plan.volume() + " บล็อก เป็น " + plan.parts().size() + " ชิ้น กรุณาอย่าแก้พื้นที่ระหว่างบันทึก"));
            return null;
        } catch (RuntimeException exception) { return exception.getMessage() == null ? "ชื่อไฟล์หรือพื้นที่ไม่ถูกต้อง" : exception.getMessage(); }
    }

    private static boolean loaded(ServerLevel level, BlockPos first, BlockPos second) {
        for (int x = first.getX() >> 4; x <= second.getX() >> 4; x++)
            for (int z = first.getZ() >> 4; z <= second.getZ() >> 4; z++) if (!level.hasChunk(x, z)) return false;
        return true;
    }

    private boolean busy(ResourceLocation name) {
        return jobs.values().stream().anyMatch(job -> job.name.equals(name));
    }

    private String checkUpdate(List<StructurePiece> pieces) {
        return StructureConsumers.checkUpdate(server, pieces);
    }

    private String checkDelete(ResourceLocation name, List<StructurePiece> pieces) {
        if (busy(name)) return "แผนที่นี้กำลังบันทึก";
        String linked = StructureConsumers.checkDelete(server, pieces);
        if (linked != null) return linked;
        var data = StructureCatalogData.get(server);
        var ids = pieces.stream().map(StructurePiece::templateId).collect(java.util.stream.Collectors.toSet());
        if (data.mapIds().stream().filter(id -> !id.equals(name)).anyMatch(id -> data.map(id).orElseThrow().stream()
                .anyMatch(piece -> ids.contains(piece.templateId())))) return "ไฟล์ยังถูกใช้โดยแผนที่ชุดอื่น";
        var manager = server.overworld().getStructureManager();
        for (var piece : pieces) {
            if (reserved.contains(piece.templateId())) return "ไฟล์กำลังถูกบันทึก";
            if (!Files.isRegularFile(manager.createAndValidatePathToGeneratedStructure(piece.templateId(), ".nbt"),
                    java.nio.file.LinkOption.NOFOLLOW_LINKS)) return "ลบได้เฉพาะไฟล์ที่บันทึกในโลก: " + piece.templateId();
        }
        return null;
    }

    public boolean isUpdating(List<StructurePiece> pieces) {
        var ids = pieces.stream().map(StructurePiece::templateId).collect(java.util.stream.Collectors.toSet());
        return jobs.values().stream().anyMatch(job -> job.original != null && job.original.stream().anyMatch(p -> ids.contains(p.templateId())));
    }

    public String requestDelete(ServerPlayer player, String name) {
        return requestDelete(player,name,"/neow world structure");
    }
    public String requestDelete(ServerPlayer player,String name,String confirmationPrefix) {
        if (!allowed(player.createCommandSourceStack())) return "ไม่มีสิทธิ์";
        try {
            ResourceLocation id = mapId(name);
            var pieces = StructureCatalogData.get(server).map(id).orElse(null);
            if (pieces == null) return "ไม่พบแผนที่ " + name;
            String error = checkDelete(id, pieces);
            if (error != null) return error;
            String token = UUID.randomUUID().toString();
            deletions.put(player.getUUID(), new DeleteRequest(id, pieces, token, server.getTickCount() + 600L));
            String command = confirmationPrefix + " delete " + id + " confirm " + token;
            player.sendSystemMessage(Component.literal("§c[ยืนยันลบ " + id + " (" + pieces.size() + " ไฟล์)]")
                    .withStyle(style -> style.withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, command))
                            .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("ยืนยันภายใน 30 วินาที ไฟล์จะถูกย้ายไปสำรองก่อนลบออกจากรายการ")))));
            return null;
        } catch (RuntimeException exception) { return exception.getMessage(); }
    }

    public String confirmDelete(ServerPlayer player, String name, String token) {
        if (!allowed(player.createCommandSourceStack())) return "ไม่มีสิทธิ์";
        DeleteRequest request = deletions.remove(player.getUUID());
        try {
            ResourceLocation id = mapId(name);
            var data = StructureCatalogData.get(server);
            if (request == null || !request.name.equals(id) || !request.token.equals(token)
                    || request.expires < server.getTickCount() || !data.map(id).orElse(List.of()).equals(request.pieces)) {
                return "คำยืนยันหมดอายุหรือข้อมูลเปลี่ยน กรุณาใช้ structure delete ใหม่";
            }
            String error = checkDelete(id, request.pieces);
            if (error != null) return error;
            var manager = server.overworld().getStructureManager();
            var files = request.pieces.stream().map(piece -> manager.createAndValidatePathToGeneratedStructure(piece.templateId(), ".nbt")).toList();
            var generated = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.GENERATED_DIR);
            var backup = generated.resolveSibling("world_structure_backups").resolve(UUID.randomUUID().toString());
            StructureMapFiles.archive(files, generated, backup);
            data.removeMap(id, request.pieces);
            for (var level : server.getAllLevels()) for (var piece : request.pieces) level.getStructureManager().remove(piece.templateId());
            player.sendSystemMessage(Component.literal("§a[Structure] ลบแผนที่ " + id + " แล้ว | สำรองไฟล์ไว้ที่ " + backup));
            return null;
        } catch (java.io.IOException | RuntimeException exception) {
            com.ambition.neoworld.NeoWorld.LOGGER.error("Structure map deletion failed", exception);
            return "ลบไม่สำเร็จ กรุณาตรวจสอบ server log (ข้อมูลสำรองจะไม่ถูกลบ)";
        }
    }

    public void tick() {
        if (jobs.isEmpty()) return;
        Job job = jobs.values().iterator().next();
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner);
        if (player == null || !allowed(player.createCommandSourceStack())) { abort(job, "ผู้บันทึกออกจากเกมหรือไม่มีสิทธิ์แล้ว"); return; }
        try {
            if (!java.util.Objects.equals(StructureCatalogData.get(server).map(job.name).orElse(null), job.original))
                throw new IllegalStateException("แผนที่เปลี่ยนระหว่างบันทึก");
            if (job.original != null) {
                String error = checkUpdate(job.original);
                if (error != null) throw new IllegalStateException(error);
            }
            var part = job.plan.parts().get(job.completed);
            var manager = job.level.getStructureManager();
            if (!loaded(job.level, part.origin(), part.origin().offset(part.size()).offset(-1, -1, -1))) throw new IllegalStateException("พื้นที่บันทึกถูก unload");
            if (Files.exists(manager.createAndValidatePathToGeneratedStructure(part.id(), ".nbt")) || manager.get(part.id()).isPresent())
                throw new IllegalStateException("มีไฟล์เกิดขึ้นระหว่างบันทึก: " + part.id());
            job.attempted.add(part.id());
            var template = manager.getOrCreate(part.id());
            template.fillFromWorld(job.level, part.origin(), part.size(), false, null);
            if (!manager.save(part.id())) throw new IllegalStateException("เขียนไฟล์ไม่สำเร็จ: " + part.id());
            job.completed++;
            if (job.completed == job.plan.parts().size()) {
                List<StructurePiece> pieces = job.plan.parts().stream().map(p -> new StructurePiece(p.id(), p.offset())).toList();
                var data = StructureCatalogData.get(server);
                String backupNotice = null;
                if (job.original != null) {
                    String error = checkUpdate(job.original);
                    if (error != null) throw new IllegalStateException(error);
                    ResourceLocation backup = ResourceLocation.fromNamespaceAndPath(job.name.getNamespace(),
                            job.name.getPath() + "/backup_" + UUID.randomUUID().toString().replace("-", ""));
                    error = StructureConsumers.checkReplacement(server, job.original, pieces);
                    if (error != null) throw new IllegalStateException(error);
                    data.replaceMap(job.name, job.original, pieces, backup);
                    job.committed = true;
                    StructureConsumers.replaced(server, job.original, pieces);
                    backupNotice = "§e[Structure] เก็บชุดเก่าไว้ที่ " + backup
                            + " | ระบบที่เชื่อมต้องตรวจสอบความพร้อมใหม่";
                } else { data.addMap(job.name, pieces);job.committed = true; }
                release(job);
                if (backupNotice != null) player.sendSystemMessage(Component.literal(backupNotice));
                player.sendSystemMessage(Component.literal("§a[Structure] บันทึกแผนที่ " + job.name + " แล้ว (" + pieces.size()
                        + " ไฟล์) | ใช้ ID นี้เชื่อมกับระบบที่ต้องการ: " + job.name));
            } else if (job.completed % 8 == 0) {
                player.sendSystemMessage(Component.literal("§e[Structure] บันทึกแล้ว " + job.completed + "/" + job.plan.parts().size()));
            }
        } catch (RuntimeException exception) { abort(job, exception.getMessage()); }
    }

    public void cancel(UUID playerId) {
        deletions.remove(playerId);
        selections.remove(playerId);
        Job job = jobs.get(playerId);
        if (job != null) abort(job, "ยกเลิกการบันทึก");
    }
    public void stop() { for (Job job : List.copyOf(jobs.values())) abort(job, "เซิร์ฟเวอร์กำลังปิด"); selections.clear(); SERVICES.remove(server); }
    private void release(Job job) { jobs.remove(job.owner); job.plan.parts().forEach(p -> reserved.remove(p.id())); }
    private void abort(Job job, String reason) {
        if (jobs.get(job.owner) != job) return;
        if(job.committed) {
            release(job);
            com.ambition.neoworld.NeoWorld.LOGGER.error("Structure committed but consumer relink failed for {}: {}",job.name,reason);
            ServerPlayer owner=server.getPlayerList().getPlayer(job.owner);
            if(owner!=null)owner.sendSystemMessage(Component.literal("[Structure] ไฟล์บันทึกแล้ว แต่เชื่อมระบบไม่ครบ กรุณาตรวจ server log; ไฟล์และชุดสำรองยังอยู่"));
            return;
        }
        var manager = job.level.getStructureManager();
        for (ResourceLocation id : job.attempted) {
            try {
                // Only exact new files owned by this job are removed; source blocks are never edited.
                Files.deleteIfExists(manager.createAndValidatePathToGeneratedStructure(id, ".nbt"));
            } catch (java.io.IOException exception) { com.ambition.neoworld.NeoWorld.LOGGER.warn("Could not remove incomplete capture {}", id, exception); }
            manager.remove(id);
        }
        release(job);
        ServerPlayer player = server.getPlayerList().getPlayer(job.owner);
        if (player != null) player.sendSystemMessage(Component.literal("§c[Structure] บันทึกไม่สำเร็จ: " + reason));
    }

    private static final class Job {
        final UUID owner; final ServerLevel level; final ResourceLocation name; final StructureCapturePlan plan;
        final List<ResourceLocation> attempted = new ArrayList<>();
        final List<StructurePiece> original;
        int completed;
        boolean committed;
        Job(UUID owner, ServerLevel level, ResourceLocation name, StructureCapturePlan plan, List<StructurePiece> original) {
            this.owner = owner; this.level = level; this.name = name; this.plan = plan;
            this.original = original == null ? null : List.copyOf(original);
        }
    }
}
