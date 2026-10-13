package com.ambition.neoworld.title;

import com.ambition.neoworld.title.model.TitleDefinition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

public final class TitleRegistry {
    private static volatile Map<String, TitleDefinition> definitions = Map.of();

    private TitleRegistry() {}
    public static void load(Path path) throws IOException {
        if (!Files.exists(path)) {
            Files.createDirectories(path.getParent());
            try (var defaults = TitleRegistry.class.getResourceAsStream("/data/neoworld/default_titles.json")) {
                if (defaults == null) throw new IOException("Missing default title catalogue");
                Files.copy(defaults, path);
            }
        }
        if (Files.size(path) > 1024 * 1024) throw new IOException("Title catalogue exceeds 1 MiB");
        try {
            var parsed = new Gson().fromJson(Files.readString(path, StandardCharsets.UTF_8), TitleDefinition[].class);
            if (parsed == null || parsed.length > 1024) throw new IllegalArgumentException("Invalid title count");
            Map<String, TitleDefinition> replacement = new LinkedHashMap<>();
            for (var title : parsed) {
                if (title == null || replacement.putIfAbsent(title.id(), title) != null)
                    throw new IllegalArgumentException("Null or duplicate title ID");
            }
            definitions = java.util.Collections.unmodifiableMap(replacement);
        } catch (RuntimeException exception) {
            throw new IOException("Invalid title catalogue: " + exception.getMessage(), exception);
        }
    }
    public static List<TitleDefinition> all() { return List.copyOf(definitions.values()); }
    public static Optional<TitleDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
}
