package com.ambition.neoworld.dungeon.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;

/** Static possible item types only: never rolls loot or invokes loot functions/modifiers. */
public final class DungeonLootPreview {
    public static final int MAX_ITEMS = 64;
    public record Catalog(List<String> items, boolean incomplete) {}
    public record Preview(List<ItemStack> items, boolean incomplete) {}
    private DungeonLootPreview() {}

    public static Preview read(MinecraftServer server, ResourceLocation id) {
        try {
            Function<String, JsonElement> tables = name -> {
                ResourceLocation location = ResourceLocation.tryParse(name);
                if (location == null) return null;
                LootTable table = server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, location));
                return LootTable.DIRECT_CODEC.encodeStart(server.reloadableRegistries().get().createSerializationContext(JsonOps.INSTANCE), table)
                        .result().orElse(null);
            };
            Catalog catalog = collect(tables.apply(id.toString()), tables, name -> {
                ResourceLocation tag = ResourceLocation.tryParse(name);
                if (tag == null) return List.of();
                List<String> items = new ArrayList<>();
                BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tag)).ifPresent(values ->
                        values.forEach(holder -> items.add(BuiltInRegistries.ITEM.getKey(holder.value()).toString())));
                return items;
            });
            List<ItemStack> stacks = new ArrayList<>();
            for (String item : catalog.items()) {
                ResourceLocation key = ResourceLocation.tryParse(item);
                if (key != null) BuiltInRegistries.ITEM.getOptional(key).ifPresent(value -> {
                    ItemStack stack = new ItemStack(value);
                    if (!stack.isEmpty()) stacks.add(stack);
                });
            }
            return new Preview(List.copyOf(stacks), catalog.incomplete());
        } catch (RuntimeException exception) {
            return new Preview(List.of(), true);
        }
    }

    public static Catalog collect(JsonElement root, Function<String, JsonElement> tables,
                                   Function<String, List<String>> tags) {
        Walker walker = new Walker(tables, tags);
        walker.visit(root, 0);
        return new Catalog(List.copyOf(walker.items), walker.incomplete);
    }

    private static final class Walker {
        final Function<String, JsonElement> tables;
        final Function<String, List<String>> tags;
        final Set<String> visited = new HashSet<>();
        final LinkedHashSet<String> items = new LinkedHashSet<>();
        int nodes;
        boolean incomplete;
        Walker(Function<String, JsonElement> tables, Function<String, List<String>> tags) {
            this.tables = tables; this.tags = tags;
        }
        void add(String id) {
            if (items.contains(id)) return;
            if (items.size() >= MAX_ITEMS) { incomplete = true; return; }
            items.add(id);
        }
        void visit(JsonElement element, int depth) {
            if (element == null || depth > 16 || ++nodes > 4096) { incomplete = true; return; }
            if (element.isJsonArray()) {
                for (JsonElement child : element.getAsJsonArray()) {
                    if (nodes >= 4096) { incomplete = true; break; }
                    visit(child, depth + 1);
                }
                return;
            }
            if (!element.isJsonObject()) return;
            JsonObject object = element.getAsJsonObject();
            String type = object.has("type") ? object.get("type").getAsString() : "";
            if (object.has("functions") && !object.getAsJsonArray("functions").isEmpty()) incomplete = true;
            switch (type) {
                case "minecraft:item", "item" -> { if (object.has("name")) add(object.get("name").getAsString()); }
                case "minecraft:tag", "tag" -> {
                    if (object.has("name")) {
                        for (String id : tags.apply(object.get("name").getAsString())) {
                            add(id);
                            if (items.size() >= MAX_ITEMS) { incomplete = true; break; }
                        }
                    }
                }
                case "minecraft:loot_table", "loot_table" -> {
                    JsonElement value = object.has("value") ? object.get("value") : object.get("name");
                    if (value != null && value.isJsonPrimitive()) {
                        String id = value.getAsString();
                        if (visited.add(id)) visit(tables.apply(id), depth + 1);
                    } else if (value != null) visit(value, depth + 1);
                    else incomplete = true;
                }
                default -> {
                    if (!type.isEmpty() && !type.equals("minecraft:chest") && !type.equals("minecraft:empty")
                            && !type.equals("minecraft:alternatives") && !type.equals("minecraft:group")
                            && !type.equals("minecraft:sequence")) incomplete = true;
                }
            }
            for (String child : List.of("pools", "entries", "children"))
                if (object.has(child)) visit(object.get(child), depth + 1);
        }
    }
}
