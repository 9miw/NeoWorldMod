package com.ambition.neoworld.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility tool to automatically scan font textures and generate assets/minecraft/font/default.json.
 * 
 * Features:
 * - Starts from Unicode 0xE001 sequentially.
 * - Formats characters in JSON as explicit Unicode escape codes for human readability.
 * - Folder-Agnostic: Works with any subfolder or file structure inside textures/font/.
 * - Preserves existing Unicode mappings across runs.
 * - Prevents Unicode collisions/duplicates by tracking used codes.
 */
public class FontBitmapGenerator {

    private static final String MOD_ID = "neoworld";
    private static final int BASE_UNICODE = 0xE001;

    public static void main(String[] args) {
        try {
            generateFontJson();
        } catch (Exception e) {
            System.err.println("Error generating font JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void generateFontJson() throws Exception {
        Path projectRoot = findProjectRoot();
        Path fontTexturesDir = projectRoot.resolve("src/main/resources/assets/" + MOD_ID + "/textures/font");
        Path defaultJsonPath = projectRoot.resolve("src/main/resources/assets/minecraft/font/default.json");

        if (!Files.exists(fontTexturesDir)) {
            System.err.println("Font textures directory does not exist: " + fontTexturesDir.toAbsolutePath());
            return;
        }

        // 1. Load existing mappings to preserve unicodes and prevent duplicates
        Map<String, String> existingFileToChar = new LinkedHashMap<>();
        Set<String> usedChars = new HashSet<>();

        if (Files.exists(defaultJsonPath)) {
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(defaultJsonPath.toFile()), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("providers") && root.get("providers").isJsonArray()) {
                    JsonArray providers = root.getAsJsonArray("providers");
                    for (JsonElement element : providers) {
                        if (element.isJsonObject()) {
                            JsonObject provider = element.getAsJsonObject();
                            if (provider.has("file") && provider.has("chars")) {
                                String file = provider.get("file").getAsString();
                                JsonArray chars = provider.getAsJsonArray("chars");
                                if (chars.size() > 0) {
                                    String character = chars.get(0).getAsString();
                                    existingFileToChar.put(file, character);
                                    usedChars.add(character);
                                }
                            }
                        }
                    }
                }
            }
            System.out.println("Loaded " + existingFileToChar.size() + " existing font mappings from default.json");
        }

        // 2. Scan all PNG files in font texture directory (any subfolders)
        List<Path> pngFiles;
        try (Stream<Path> stream = Files.walk(fontTexturesDir)) {
            pngFiles = stream.filter(p -> Files.isRegularFile(p) && p.toString().toLowerCase().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::toString))
                    .collect(Collectors.toList());
        }

        System.out.println("Found " + pngFiles.size() + " PNG files in " + fontTexturesDir.toAbsolutePath());

        // 3. Process mappings
        Map<String, BitmapEntry> finalEntries = new LinkedHashMap<>();
        int keptCount = 0;
        int newCount = 0;

        for (Path pngPath : pngFiles) {
            Path relativePath = fontTexturesDir.relativize(pngPath);
            String normalizedRelative = relativePath.toString().replace('\\', '/');
            String resourcePath = MOD_ID + ":font/" + normalizedRelative;

            if (existingFileToChar.containsKey(resourcePath)) {
                // Preserve existing character
                String assignedChar = existingFileToChar.get(resourcePath);
                finalEntries.put(resourcePath, new BitmapEntry(resourcePath, assignedChar, 8, 9));
                keptCount++;
            } else {
                // Allocate next free unique unicode character
                String newChar = allocateNextFreeChar(usedChars);
                usedChars.add(newChar);
                finalEntries.put(resourcePath, new BitmapEntry(resourcePath, newChar, 8, 9));
                newCount++;
                System.out.printf("  + [NEW] %s -> Unicode U+%04X%n", resourcePath, (int) newChar.charAt(0));
            }
        }

        // 4. Build JSON with human-readable unicode escape format
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"providers\": [\n");

        int index = 0;
        for (BitmapEntry entry : finalEntries.values()) {
            sb.append("    {\n");
            sb.append("      \"type\": \"bitmap\",\n");
            sb.append("      \"file\": \"").append(entry.file).append("\",\n");
            sb.append("      \"ascent\": ").append(entry.ascent).append(",\n");
            sb.append("      \"height\": ").append(entry.height).append(",\n");
            
            String unicodeHex = String.format("\\u%04X", (int) entry.character.charAt(0));
            sb.append("      \"chars\": [\n");
            sb.append("        \"").append(unicodeHex).append("\"\n");
            sb.append("      ]\n");
            sb.append("    }");
            if (index < finalEntries.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
            index++;
        }

        sb.append("  ]\n");
        sb.append("}\n");

        // 5. Write to default.json with UTF-8
        Files.createDirectories(defaultJsonPath.getParent());
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(defaultJsonPath.toFile()), StandardCharsets.UTF_8)) {
            writer.write(sb.toString());
        }

        System.out.println("==================================================");
        System.out.println("Font generation complete!");
        System.out.println("Total entries: " + finalEntries.size());
        System.out.println("Preserved:     " + keptCount);
        System.out.println("New added:     " + newCount);
        System.out.println("Saved to:      " + defaultJsonPath.toAbsolutePath());
        System.out.println("==================================================");
    }

    private static String allocateNextFreeChar(Set<String> usedChars) {
        int currentCode = BASE_UNICODE;
        while (true) {
            String candidate = String.valueOf((char) currentCode);
            if (!usedChars.contains(candidate)) {
                return candidate;
            }
            currentCode++;
        }
    }

    private static Path findProjectRoot() {
        Path current = Paths.get("").toAbsolutePath();
        if (Files.exists(current.resolve("src/main/resources"))) {
            return current;
        }
        if (Files.exists(current.resolve("build.gradle")) || Files.exists(current.resolve("settings.gradle"))) {
            return current;
        }
        return current;
    }

    private static class BitmapEntry {
        final String file;
        final String character;
        final int ascent;
        final int height;

        BitmapEntry(String file, String character, int ascent, int height) {
            this.file = file;
            this.character = character;
            this.ascent = ascent;
            this.height = height;
        }
    }
}
