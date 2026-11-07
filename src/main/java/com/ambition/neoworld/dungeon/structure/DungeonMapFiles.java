package com.ambition.neoworld.dungeon.structure;

/** Compatibility adapter to the shared, rollback-safe archive implementation. */
public final class DungeonMapFiles {
    private DungeonMapFiles() {}
    public static void archive(java.util.List<java.nio.file.Path> files,java.nio.file.Path root,java.nio.file.Path backup) throws java.io.IOException {
        com.ambition.neoworld.world.structure.StructureMapFiles.archive(files,root,backup);
    }
}
