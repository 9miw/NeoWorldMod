package com.ambition.neoworld.world;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;

/** Run with Java source-file mode and build/classes/java/main on the classpath. */
public final class WorldImportFilesRegressionTest {
    private interface Checked { void run() throws IOException; }

    public static void main(String[] args) throws Exception {
        Path base = Path.of("build/tmp").toAbsolutePath().normalize();
        Files.createDirectories(base);
        Path root = Files.createTempDirectory(base, "world-import-test-");
        try {
            Path source = Files.createDirectories(root.resolve("source/region")).getParent();
            byte[] region = new byte[12288];
            ByteBuffer.wrap(region).putInt(0x201); // One chunk occupying sector 2.
            Path terrain = source.resolve("region/r.0.0.mca");
            Files.write(terrain, region);
            Files.writeString(source.resolve("level.dat"), "metadata must not be copied");
            Files.createDirectories(source.resolve("playerdata"));
            Files.writeString(source.resolve("playerdata/player.dat"), "player inventory must not be copied");
            var snapshot = WorldImportFiles.scan(source);
            check(snapshot.chunks() == 1 && snapshot.bytes() == region.length, "chunk count and copy size");
            Path copied = root.resolve("copied");
            WorldImportFiles.copy(snapshot, copied);
            check(Files.mismatch(terrain, copied.resolve("region/r.0.0.mca")) == -1, "terrain copied without byte changes");
            check(!Files.exists(copied.resolve("level.dat")) && !Files.exists(copied.resolve("playerdata")), "exclude global and player data");
            fails(() -> WorldImportFiles.copy(snapshot, copied));
            check(Files.mismatch(terrain, copied.resolve("region/r.0.0.mca")) == -1, "existing target remains untouched");
            Files.setLastModifiedTime(terrain, FileTime.fromMillis(snapshot.parts().getFirst().modified().toMillis() + 2000));
            fails(() -> WorldImportFiles.copy(snapshot, root.resolve("changed-source")));
            Path outside = Files.createDirectory(root.resolve("outside"));
            fails(() -> WorldImportFiles.requireDirectPath(source, outside));
            Files.write(terrain, new byte[8192]);
            fails(() -> WorldImportFiles.scan(source)); // Empty region has no chunks.
            region[0] = 127; // Header now points beyond the physical region file.
            Files.write(terrain, region);
            fails(() -> WorldImportFiles.scan(source));
            System.out.println("World import filesystem checks passed: copy integrity, no overwrite, excluded data, source changes, path boundary, empty/corrupt regions.");
        } finally {
            if (!root.getParent().equals(base)) throw new IllegalStateException("Unsafe test cleanup path");
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    if (!path.toAbsolutePath().normalize().startsWith(root)) throw new IllegalStateException("Unsafe cleanup child");
                    Files.delete(path);
                }
            }
        }
    }

    private static void fails(Checked action) throws IOException {
        try { action.run(); } catch (IOException expected) { return; }
        throw new AssertionError("Operation should have been refused");
    }

    private static void check(boolean passed, String label) {
        if (!passed) throw new AssertionError(label);
    }
}
