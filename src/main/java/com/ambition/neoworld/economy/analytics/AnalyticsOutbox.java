package com.ambition.neoworld.economy.analytics;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Single-consumer disk spool. Files are retained until the SQL transaction is acknowledged. */
public final class AnalyticsOutbox implements AutoCloseable {
    private static final Gson JSON = new Gson();
    private static final String READY = "[0-9]{20}-[a-f0-9-]{36}\\.json";
    private static final String TEMP = "[0-9]{20}-[a-f0-9-]{36}\\.tmp";
    private record Stored(int version, AnalyticsWork work, String checksum) {}
    public record Job(Path path, AnalyticsWork work) {}
    private final Path directory;
    private final FileChannel lockChannel;
    private final FileLock lock;
    private long sequence;
    private int pending;
    private boolean closed;

    public AnalyticsOutbox(Path directory) throws IOException {
        this.directory = directory.toAbsolutePath().normalize();
        Files.createDirectories(this.directory);
        lockChannel = FileChannel.open(this.directory.resolve("writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired = null;
        try {
            acquired = lockChannel.tryLock();
            if (acquired == null) throw new IOException("Analytics outbox is already in use");
            lock = acquired;
            recoverTemporaryFiles();
            List<Path> files = readyFiles();
            pending = files.size();
            for (Path file : files) sequence = Math.max(sequence, Long.parseLong(file.getFileName().toString().substring(0,20)));
        } catch (IOException | RuntimeException failure) {
            if (acquired != null) acquired.release();
            lockChannel.close();
            if (failure instanceof OverlappingFileLockException) throw new IOException("Analytics outbox is already in use", failure);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Cannot recover analytics outbox", failure);
        }
    }

    public synchronized void append(AnalyticsWork work) throws IOException {
        ensureOpen(); work.validate();
        sequence = Math.addExact(sequence, 1);
        String base = String.format(Locale.ROOT, "%020d-%s", sequence, work.id());
        Path temp = directory.resolve(base + ".tmp");
        byte[] bytes = JSON.toJson(new Stored(1, work, checksum(work))).getBytes(StandardCharsets.UTF_8);
        try (var file = FileChannel.open(temp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) file.write(buffer);
            file.force(true);
        }
        Files.move(temp, directory.resolve(base + ".json"), StandardCopyOption.ATOMIC_MOVE);
        pending++;
    }

    public synchronized List<Job> batch(int limit) throws IOException {
        ensureOpen();
        if (limit < 1) throw new IllegalArgumentException("Batch limit must be positive");
        recoverTemporaryFiles();
        List<Path> files = readyFiles(); pending = files.size();
        List<Job> jobs = new ArrayList<>();
        for (Path file : files.subList(0, Math.min(limit, files.size()))) jobs.add(new Job(file, read(file)));
        return List.copyOf(jobs);
    }

    public synchronized void acknowledge(List<Job> jobs) throws IOException {
        ensureOpen();
        for (Job job : jobs) {
            Path file = job.path().toAbsolutePath().normalize();
            if (!directory.equals(file.getParent()) || !file.getFileName().toString().matches(READY))
                throw new IOException("Invalid analytics acknowledgement path");
            if (Files.deleteIfExists(file)) pending--;
        }
    }

    public synchronized int pending() { return pending; }

    private void recoverTemporaryFiles() throws IOException {
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(p -> p.getFileName().toString().matches(TEMP)).sorted().toList()) {
                read(file); // Corrupt/incomplete files remain visible and block delivery; never silently discard them.
                String ready = file.getFileName().toString().replace(".tmp", ".json");
                Files.move(file, directory.resolve(ready), StandardCopyOption.ATOMIC_MOVE);
            }
        }
    }

    private List<Path> readyFiles() throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().matches(READY)).sorted().toList();
        }
    }

    private AnalyticsWork read(Path file) throws IOException {
        try {
            Stored stored = JSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), Stored.class);
            if (stored == null || stored.version() != 1 || stored.work() == null) throw new IllegalArgumentException("Unknown spool format");
            stored.work().validate();
            if (!checksum(stored.work()).equals(stored.checksum())) throw new IllegalArgumentException("Spool checksum mismatch");
            return stored.work();
        } catch (RuntimeException failure) { throw new IOException("Invalid analytics spool file: " + file.getFileName(), failure); }
    }

    private static String checksum(AnalyticsWork work) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.toJson(work).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private void ensureOpen() throws IOException { if (closed) throw new IOException("Analytics outbox is closed"); }

    @Override public synchronized void close() throws IOException {
        if (closed) return;
        closed = true;
        try { lock.release(); } finally { lockChannel.close(); }
    }
}
