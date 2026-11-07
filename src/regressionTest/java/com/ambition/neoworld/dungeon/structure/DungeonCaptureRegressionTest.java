package com.ambition.neoworld.dungeon.structure;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class DungeonCaptureRegressionTest {
    public static void main(String[] args) throws Exception {
        com.ambition.neoworld.world.WorldStructureRegressionTest.run();
        savedMaps();
        commandTree();
        archiveFiles();
        ResourceLocation name = ResourceLocation.parse("neoworld:dungeon/mine");
        var point = DungeonCapturePlan.create(BlockPos.ZERO, BlockPos.ZERO, name);
        check(point.volume() == 1 && point.parts().size() == 1 && point.parts().getFirst().size().equals(new BlockPos(1, 1, 1)),
                "Single selected block is inclusive");
        check(point.parts().getFirst().id().toString().equals("neoworld:dungeon/mine_01"), "Names use expected _01 suffix");
        var exact = DungeonCapturePlan.create(BlockPos.ZERO, new BlockPos(47, 47, 47), name);
        check(exact.parts().size() == 1 && exact.volume() == 48L * 48 * 48, "Exact template edge has no empty extra part");
        var boundary = DungeonCapturePlan.create(BlockPos.ZERO, new BlockPos(48, 0, 0), name);
        check(boundary.parts().size() == 2 && boundary.parts().get(1).offset().getX() == 48
                && boundary.parts().get(1).size().getX() == 1, "Partial final piece keeps its final block");
        var plan = DungeonCapturePlan.create(new BlockPos(100, 80, -50), new BlockPos(4, 31, -2), name);
        var reversed = DungeonCapturePlan.create(new BlockPos(4, 31, -2), new BlockPos(100, 80, -50), name);
        check(plan.equals(reversed), "Reversing corner order gives the identical layout");
        check(plan.parts().size() == 12, "All three axes are partitioned");
        long volume = 0;
        var names = new HashSet<ResourceLocation>();
        for (var part : plan.parts()) {
            check(names.add(part.id()), "Each file name is unique");
            check(part.origin().subtract(plan.minimum()).equals(part.offset()), "Offsets are relative to selection minimum");
            for (int size : new int[] {part.size().getX(), part.size().getY(), part.size().getZ()})
                check(size >= 1 && size <= 48, "Every part fits standard bounds");
            check(part.origin().getX() >= plan.minimum().getX() && part.origin().getY() >= plan.minimum().getY()
                    && part.origin().getZ() >= plan.minimum().getZ(), "Part begins within selection");
            BlockPos last = part.origin().offset(part.size()).offset(-1, -1, -1);
            check(last.getX() <= plan.maximum().getX() && last.getY() <= plan.maximum().getY()
                    && last.getZ() <= plan.maximum().getZ(), "Part ends within selection");
            volume += (long) part.size().getX() * part.size().getY() * part.size().getZ();
        }
        check(volume == plan.volume() && volume == 97L * 50 * 49, "Partition covers the exact selection volume");
        for (int i = 0; i < plan.parts().size(); i++) for (int j = i + 1; j < plan.parts().size(); j++)
            check(!intersects(plan.parts().get(i), plan.parts().get(j)), "Generated pieces never overlap");
        rejects(() -> DungeonCapturePlan.create(BlockPos.ZERO, new BlockPos(4095, 4095, 4095), name));
        rejects(() -> DungeonCapturePlan.create(BlockPos.ZERO, new BlockPos(0, 0, 30_000), name));
        rejects(() -> DungeonCapturePlan.create(BlockPos.ZERO, BlockPos.ZERO, ResourceLocation.parse("test:../escape")));
        rejects(() -> DungeonCapturePlan.create(BlockPos.ZERO, BlockPos.ZERO, ResourceLocation.parse("test:mine.nbt")));
        System.out.println("Dungeon capture partition regression checks passed");
    }
    private static boolean intersects(DungeonCapturePlan.Part a, DungeonCapturePlan.Part b) {
        return a.origin().getX() < b.origin().getX() + b.size().getX() && b.origin().getX() < a.origin().getX() + a.size().getX()
                && a.origin().getY() < b.origin().getY() + b.size().getY() && b.origin().getY() < a.origin().getY() + a.size().getY()
                && a.origin().getZ() < b.origin().getZ() + b.size().getZ() && b.origin().getZ() < a.origin().getZ() + a.size().getZ();
    }

    private static void commandTree() {
        var root = com.ambition.neoworld.command.dungeon.DungeonCommand.buildCommand().build();
        check(root.getChild("room") != null && root.getChild("info") != null && root.getChild("exit") != null,
                "Player commands stay outside admin");
        var admin = root.getChild("admin");
        check(admin != null && admin.getChild("help") != null, "Admin commands have a separate help entry");
        for (String command : new String[] {"create", "definitions", "set", "structure", "validate", "enable",
                "disable", "instances", "build", "clear"}) {
            check(root.getChild(command) == null && admin.getChild(command) != null,
                    "Management command must exist only under admin: " + command);
        }
        var save = admin.getChild("structure").getChild("save");
        check(save.getChild("id") == null && save.getChild("name").getCommand() != null,
                "Save accepts only a map name, without dungeon identity");
        for (String operation : new String[] {"save", "update", "delete"}) {
            var argument = (com.mojang.brigadier.tree.ArgumentCommandNode<?, ?>) admin.getChild("structure")
                    .getChild(operation).getChild("name");
            var reader = new com.mojang.brigadier.StringReader("neoworld:dungeon/silme_map");
            try { argument.getType().parse(reader); }
            catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) { throw new AssertionError(exception); }
            check(!reader.canRead(), operation + " must accept the entire namespaced resource");
        }
        check(DungeonCaptureService.mapId("silme_map").equals(DungeonCaptureService.mapId("neoworld:dungeon/silme_map")),
                "Short and namespaced map names resolve identically");
        check(admin.getChild("structure").getChild("delete").getChild("name").getChild("confirm") != null,
                "Deletion has an explicit confirmation command");
        check(admin.getChild("set").getChild("id").getChild("structure").getChild("resource").getCommand() != null,
                "Map linking retains a separate command");
    }

    private static void savedMaps() throws Exception {
        var data = new com.ambition.neoworld.dungeon.definition.DungeonDefinitionData();
        var name = ResourceLocation.parse("neoworld:dungeon/silme_map");
        var plan = DungeonCapturePlan.create(new BlockPos(12, 64, -30), new BlockPos(108, 112, 20), name);
        var pieces = plan.parts().stream().map(part -> new DungeonTemplatePiece(part.id(), part.offset())).toList();
        data.addMap(name, pieces);
        check(data.all().isEmpty(), "Saving a map does not create or require a dungeon");
        rejects(() -> data.addMap(name, pieces));
        rejects(() -> data.addMap(ResourceLocation.parse("test:empty"), java.util.List.of()));
        var load = com.ambition.neoworld.dungeon.definition.DungeonDefinitionData.class.getDeclaredMethod("load",
                net.minecraft.nbt.CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
        load.setAccessible(true);
        var saved = data.save(new net.minecraft.nbt.CompoundTag(), null);
        var restored = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(restored.map(name).orElseThrow().equals(pieces), "Independent map names and offsets survive reload");
        var definition = restored.create("silme").orElseThrow();
        check(definition.structurePieces().isEmpty(), "Creating a dungeon does not automatically link a saved map");
        restored.updateStructurePieces(definition, restored.map(name).orElseThrow());
        check(definition.structurePieces().equals(pieces), "Linking copies the complete multi-file layout");
        var replacement = java.util.List.of(new DungeonTemplatePiece(ResourceLocation.parse("neoworld:dungeon/new_01"), BlockPos.ZERO));
        var backupId = ResourceLocation.parse("neoworld:dungeon/backup");
        restored.updateStatus(definition, com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.ENABLED);
        rejects(() -> restored.removeMap(name, pieces));
        restored.replaceMap(name, pieces, replacement, backupId);
        check(restored.map(backupId).orElseThrow().equals(pieces), "Updates preserve the complete previous layout");
        check(definition.structurePieces().equals(replacement) && definition.status()
                == com.ambition.neoworld.dungeon.definition.DungeonDefinitionStatus.DRAFT,
                "Updating a linked map switches every part and resets readiness");
        rejects(() -> restored.replaceMap(name, pieces, replacement, ResourceLocation.parse("test:stale")));
        restored.removeMap(backupId, pieces);
        check(restored.map(backupId).isEmpty(), "Unlinked backups can be removed independently");
        saved.remove("maps");
        var legacy = (com.ambition.neoworld.dungeon.definition.DungeonDefinitionData) load.invoke(null, saved, null);
        check(legacy.mapIds().isEmpty(), "Old saves without independent maps load safely");
    }

    private static void archiveFiles() throws Exception {
        var temp = java.nio.file.Files.createTempDirectory("dungeon-map-archive-");
        try {
            var root = java.nio.file.Files.createDirectory(temp.resolve("generated"));
            var first = root.resolve("first.nbt");
            var second = root.resolve("nested/second.nbt");
            java.nio.file.Files.createDirectories(second.getParent());
            java.nio.file.Files.writeString(first, "first");
            java.nio.file.Files.writeString(second, "second");
            var failedBackup = java.nio.file.Files.createDirectory(temp.resolve("failed"));
            java.nio.file.Files.writeString(failedBackup.resolve("nested"), "blocked parent");
            try {
                DungeonMapFiles.archive(java.util.List.of(first, second), root, failedBackup);
                throw new AssertionError("Invalid backup parent must fail");
            } catch (java.io.IOException expected) {
                check(java.nio.file.Files.readString(first).equals("first") && java.nio.file.Files.exists(second),
                        "Partial file moves must roll back");
            }
            var outside = temp.resolve("external.nbt");
            java.nio.file.Files.writeString(outside, "outside");
            try {
                DungeonMapFiles.archive(java.util.List.of(outside), root, temp.resolve("unsafe"));
                throw new AssertionError("Outside paths must not be archived");
            } catch (java.io.IOException expected) { check(java.nio.file.Files.exists(outside), "External file stays untouched"); }
            var backup = temp.resolve("backup");
            DungeonMapFiles.archive(java.util.List.of(first, second), root, backup);
            check(!java.nio.file.Files.exists(first) && !java.nio.file.Files.exists(second)
                    && java.nio.file.Files.readString(backup.resolve("first.nbt")).equals("first")
                    && java.nio.file.Files.readString(backup.resolve("nested/second.nbt")).equals("second"),
                    "Deleting removes active files but retains recoverable backups");
        } finally {
            try (var paths = java.nio.file.Files.walk(temp)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid capture must be rejected");
    }
}
