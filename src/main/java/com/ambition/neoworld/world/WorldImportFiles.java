package com.ambition.neoworld.world;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

/** Copies only dimension chunk storage, never player files, server settings or datapacks. */
public final class WorldImportFiles {
    public record Part(Path relative, long size, FileTime modified) {}
    public record Snapshot(Path source, List<Part> parts, long bytes, long chunks) {}

    private WorldImportFiles() {}

    public static void requireDirectPath(Path root, Path path) throws IOException {
        Path base = root.toRealPath();
        Path expected = base.resolve(root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize()));
        if (!expected.startsWith(base) || Files.isSymbolicLink(path) || !path.toRealPath().equals(expected)) {
            throw new IOException("Refusing redirected or external import path: " + path);
        }
    }

    public static Snapshot scan(Path source) throws IOException {
        requireDirectPath(source.getParent(), source);
        List<Part> parts = new ArrayList<>();
        long bytes = 0;
        long chunks = 0;
        for (String name : List.of("region", "entities", "poi")) {
            Path directory = source.resolve(name);
            if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                if (name.equals("region")) throw new IOException("ไม่พบโฟลเดอร์ region ของโลกต้นทาง");
                continue;
            }
            requireDirectPath(source, directory);
            try (var files = Files.list(directory)) {
                for (Path file : files.sorted().toList()) {
                    requireDirectPath(source, file);
                    BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    if (!attributes.isRegularFile()) throw new IOException("ไฟล์โลกต้องไม่มีโฟลเดอร์ย่อยหรือไฟล์พิเศษ: " + file);
                    String filename = file.getFileName().toString();
                    if (!filename.matches("(?:r\\.-?\\d+\\.-?\\d+\\.mca|c\\.-?\\d+\\.-?\\d+\\.mcc)")) continue;
                    if (filename.endsWith(".mca")) {
                        long count = countChunks(file, attributes.size());
                        if (name.equals("region")) chunks += count;
                    }
                    parts.add(new Part(source.relativize(file), attributes.size(), attributes.lastModifiedTime()));
                    bytes = Math.addExact(bytes, attributes.size());
                }
            }
        }
        if (chunks == 0) throw new IOException("ไม่พบ chunk ในโลกต้นทาง");
        return new Snapshot(source, List.copyOf(parts), bytes, chunks);
    }

    private static long countChunks(Path file, long size) throws IOException {
        if (size < 8192) return 0;
        long count = 0;
        try (var input = new DataInputStream(Files.newInputStream(file))) {
            for (int index = 0; index < 1024; index++) {
                int location = input.readInt();
                if (location == 0) continue;
                long offset = Integer.toUnsignedLong(location >>> 8);
                int sectors = location & 255;
                if (offset < 2 || sectors == 0 || (offset + sectors) * 4096 > size) {
                    continue;
                }
                count++;
            }
        }
        return count;
    }

    public static void copy(Snapshot snapshot, Path destination) throws IOException {
        Files.createDirectory(destination); // Refuse existing destinations, including empty directories.
        for (Part part : snapshot.parts) {
            Path source = snapshot.source.resolve(part.relative);
            requireDirectPath(snapshot.source, source);
            BasicFileAttributes before = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (before.size() != part.size || !before.lastModifiedTime().equals(part.modified)) {
                throw new IOException("โลกต้นทางเปลี่ยนระหว่างนำเข้า กรุณาใช้สำเนาโลกที่ปิดแล้ว");
            }
            Path target = destination.resolve(part.relative);
            Files.createDirectories(target.getParent());
            Files.copy(source, target);
            BasicFileAttributes after = Files.readAttributes(source, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (after.size() != part.size || !after.lastModifiedTime().equals(part.modified) || Files.size(target) != part.size) {
                throw new IOException("โลกต้นทางเปลี่ยนหรือคัดลอกไม่สมบูรณ์");
            }
        }
    }
}
