package com.ambition.neoworld.client.gui.lootbox;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

/** Local cosmetic preference; no gameplay data or server settings. */
public final class LootBoxPreferences {
    private final Path path;
    private boolean effects = true;
    public LootBoxPreferences(Path path) throws IOException {
        this.path = path;
        if (Files.exists(path)) {
            var values = new Properties();
            try (var input = Files.newInputStream(path)) { values.load(input); }
            effects = !"false".equalsIgnoreCase(values.getProperty("effects"));
        }
    }
    public boolean effects() { return effects; }
    public void setEffects(boolean enabled) throws IOException {
        var values = new Properties(); values.setProperty("effects", Boolean.toString(enabled));
        Files.createDirectories(path.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(path.toAbsolutePath().getParent(), "lootbox-", ".tmp");
        try {
            try (var output = Files.newOutputStream(temporary)) { values.store(output, "NeoWorld loot box effects"); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            effects = enabled;
        } finally { Files.deleteIfExists(temporary); }
    }
}
