package com.ambition.neoworld.world.structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;

/** Remove generated templates from service without destroying the recoverable originals. */
public final class StructureMapFiles {
    private StructureMapFiles() {}

    public static void archive(List<Path> files, Path generatedRoot, Path backup) throws IOException {
        Path root = generatedRoot.toRealPath();
        var targets = new LinkedHashMap<Path, Path>();
        for (Path file : files) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !file.toRealPath().startsWith(root)) {
                throw new IOException("Not an owned generated template: " + file);
            }
            Path destination = backup.resolve(root.relativize(file.toRealPath()));
            if (targets.put(file, destination) != null) throw new IOException("Duplicate template path");
            if (Files.exists(destination)) throw new IOException("Backup path already exists");
        }
        var moved = new LinkedHashMap<Path, Path>();
        try {
            for (var entry : targets.entrySet()) {
                Files.createDirectories(entry.getValue().getParent());
                Files.move(entry.getKey(), entry.getValue());
                moved.put(entry.getKey(), entry.getValue());
            }
        } catch (IOException failure) {
            for (var entry : moved.entrySet()) {
                try { Files.move(entry.getValue(), entry.getKey()); }
                catch (IOException rollback) { failure.addSuppressed(rollback); }
            }
            throw failure;
        }
    }
}
