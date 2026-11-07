package com.ambition.neoworld.pvp.arena;

/** Compatibility adapter to the shared, rollback-safe archive implementation. */
public final class PvpMapFiles {
    private PvpMapFiles() {}
    public static void archive(java.util.List<java.nio.file.Path> files,java.nio.file.Path root,java.nio.file.Path backup) throws java.io.IOException {
        com.ambition.neoworld.world.structure.StructureMapFiles.archive(files,root,backup);
    }
}
