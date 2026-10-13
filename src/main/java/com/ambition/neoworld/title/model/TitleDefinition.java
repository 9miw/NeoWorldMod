package com.ambition.neoworld.title.model;

/** Metadata only; unlock conditions are connected to gameplay in a later stage. */
public record TitleDefinition(String id, String name, String display, String description, Category category, Rarity rarity,
                              String requirement, String color, String effect, Condition condition) {
    public TitleDefinition {
        if (display == null) display = name; // Compatibility with catalogues written before display was introduced.
        if (id == null || !id.matches("[a-z][a-z0-9_]{0,63}") || name == null || name.isBlank()
                || name.length() > 64 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00A7)
                || display == null || display.isBlank() || display.length() > 64
                || display.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x00A7)
                || description == null || description.length() > 512 || category == null || rarity == null
                || requirement == null || requirement.length() > 512 || color == null
                || !color.matches("#[0-9a-fA-F]{6}") || effect == null
                || !effect.matches("[a-z][a-z0-9_]{0,63}") || condition == null)
            throw new IllegalArgumentException("Invalid title definition: " + id);
    }
    public int rgb() { return Integer.parseInt(color.substring(1), 16); }
    public record Condition(String metric, long target) {
        public Condition {
            if (metric == null || !metric.matches("[a-z][a-z0-9_]{0,63}") || target < 0
                    || (!metric.equals("manual") && target == 0))
                throw new IllegalArgumentException("Invalid title condition");
        }
    }
    public enum Category { GENERAL, HUMOROUS }
    public enum Rarity { COMMON, RARE }
}
