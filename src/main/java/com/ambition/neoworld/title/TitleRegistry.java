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
    /** Read migration input without creating or changing the legacy file. */
    public static List<TitleDefinition> readCatalogue(Path path) throws IOException {
        String json;
        if (!Files.notExists(path)) {
            if (Files.size(path) > 1024 * 1024) throw new IOException("Title catalogue exceeds 1 MiB");
            json = Files.readString(path, StandardCharsets.UTF_8);
        } else {
            try (var defaults = TitleRegistry.class.getResourceAsStream("/data/neoworld/default_titles.json")) {
                if (defaults == null) throw new IOException("Missing default title catalogue");
                json = new String(defaults.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        try {
            var parsed = new Gson().fromJson(json, TitleDefinition[].class);
            if (parsed == null) throw new IllegalArgumentException("Missing catalogue");
            return List.copyOf(validated(java.util.Arrays.asList(parsed)).values());
        } catch (RuntimeException exception) {
            throw new IOException("Invalid title catalogue", exception);
        }
    }
    public static void replace(List<TitleDefinition> titles) {
        definitions = java.util.Collections.unmodifiableMap(validated(titles));
    }
    private static Map<String, TitleDefinition> validated(List<TitleDefinition> titles) {
        if (titles.size() > 1024) throw new IllegalArgumentException("Invalid title count");
        Map<String, TitleDefinition> result = new LinkedHashMap<>();
        for (var title : titles) {
            if (title == null || result.putIfAbsent(title.id(), title) != null)
                throw new IllegalArgumentException("Null or duplicate title ID");
        }
        return result;
    }
    public static List<TitleDefinition> all() { return List.copyOf(definitions.values()); }
    public static Optional<TitleDefinition> find(String id) { return Optional.ofNullable(definitions.get(id)); }
}
