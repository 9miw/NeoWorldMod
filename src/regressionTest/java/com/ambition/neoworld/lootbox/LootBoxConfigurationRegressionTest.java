package com.ambition.neoworld.lootbox;

import com.ambition.neoworld.command.LootBoxCommand;
import com.ambition.neoworld.network.lootbox.LootBoxOpenedPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;

/** Real ItemStack/NBT and packet codecs, using the project's lightweight bootstrap convention. */
public final class LootBoxConfigurationRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true); bootstrapped.setBoolean(null, true);
        net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        // The real mod loader normally marks these registries as network-synchronized.
        var setSync = Class.forName("net.neoforged.neoforge.registries.BaseMappedRegistry").getDeclaredMethod("setSync", boolean.class);
        setSync.setAccessible(true);
        setSync.invoke(BuiltInRegistries.ITEM, true);
        setSync.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE, true);
        Item item = Registry.register(BuiltInRegistries.ITEM,
                ResourceLocation.fromNamespaceAndPath("neoworld_test", "loot_reward"), new Item(new Item.Properties()));
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var data = new LootBoxDefinitions(); data.create("starter");
        reject(() -> data.create("starter"));
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            var stack = new ItemStack(item, i + 1);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("Reward " + i));
            items.add(stack); data.add("starter", stack, 10 + i);
        }
        data.setCount("starter", 3);
        var saved = data.save(new CompoundTag(), registries);
        var restored = LootBoxDefinitions.load(saved, registries);
        check(restored.count("starter") == 3 && restored.entries("starter").size() == 8, "draw count and eight entries survive save/load");
        for (int i = 0; i < 8; i++) check(ItemStack.matches(restored.entries("starter").get(i).item(), items.get(i)), "reward count and components survive save/load");
        items.getFirst().setCount(50);
        check(data.entries("starter").getFirst().item().getCount() == 1, "configuration owns a copy of held item");
        var copy = data.entries("starter").getFirst().item(); copy.setCount(51);
        check(data.entries("starter").getFirst().item().getCount() == 1, "callers cannot mutate saved rewards");
        reject(() -> data.setCount("starter", 9));
        reject(() -> data.add("starter", data.entries("starter").getFirst().item(), 20));
        var invalid = saved.copy(); invalid.getCompound("counts").putInt("starter", 0);
        var blocked = LootBoxDefinitions.load(invalid, registries);
        reject(() -> blocked.entries("starter"));
        reject(() -> blocked.create("replacement"));
        check(blocked.save(new CompoundTag(), registries).equals(invalid), "malformed definitions retain original data instead of becoming an empty writable catalog");
        var legacy = saved.copy(); legacy.putInt("format", 1); legacy.remove("counts");
        check(LootBoxDefinitions.load(legacy, registries).count("starter") == 1, "older definitions default to one draw");
        data.remove("starter", 1);
        check(data.entries("starter").size() == 7 && data.entries("starter").getFirst().item().getCount() == 2, "remove uses one-based displayed indices");
        reject(() -> data.remove("starter", 0));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            var payload = new LootBoxOpenedPayload(items.subList(1, 4));
            LootBoxOpenedPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = LootBoxOpenedPayload.STREAM_CODEC.decode(buffer);
            check(decoded.opening().equals(payload.opening()) && !decoded.automatic(), "result token and automatic flag survive codec");
            check(decoded.rewards().size() == 3 && ItemStack.matches(decoded.rewards().getFirst(), items.get(1)), "multi-reward packet preserves stack components/counts");
            buffer.clear(); buffer.writeUUID(java.util.UUID.randomUUID()); buffer.writeBoolean(false); buffer.writeVarInt(10);
            reject(() -> LootBoxOpenedPayload.STREAM_CODEC.decode(buffer));
            buffer.clear();
            var request = new com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload(java.util.UUID.randomUUID(), 10, true);
            com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload.STREAM_CODEC.encode(buffer, request);
            check(com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload.STREAM_CODEC.decode(buffer).equals(request), "bounded auto request codec roundtrip");
            reject(() -> new com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload(java.util.UUID.randomUUID(), 65));
            reject(() -> new com.ambition.neoworld.network.lootbox.LootBoxAutoRequestPayload(java.util.UUID.randomUUID(), -1));
        } finally { buffer.release(); }
        var command = LootBoxCommand.buildCommand().build();
        for (String name : List.of("create", "add", "count", "list", "remove", "give"))
            check(command.getChild(name) != null, "command exists: " + name);
        check(command.getChild("count").getChild("count") != null, "count accepts amount directly without an id");
        check(command.getChild("add").getChild("weight") != null, "add accepts weight directly without an id");
        check(command.getChild("list").getCommand() != null && command.getChild("list").getChildren().isEmpty(), "list operates on held box");
        check(command.getChild("create").getCommand() != null, "create can initialize held box without an id");
        check(command.getChild("give").getChild("players").getChild("count") != null, "give clones held box without an id");
        System.out.println("Loot box configuration checks passed: " + checks);
    }
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { checks++; return; }
        throw new AssertionError("Expected rejection");
    }
}
