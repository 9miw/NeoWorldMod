package com.ambition.neoworld.level;

import com.ambition.neoworld.experience.ExperienceRoll;
import java.util.Random;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class LevelSafetyRegressionTest {
    public static void main(String[] args) throws Exception {
        Random sequence = new Random(1) {
            private int index;
            @Override public long nextLong(long bound) { return index++ % bound; }
        };
        check(ExperienceRoll.total(1, 3, 3, sequence) == 6, "Ranged legacy stacks must roll each item independently");
        Random upper = new Random(1) {
            @Override public long nextLong(long bound) { return bound - 1; }
        };
        check(ExperienceRoll.roll(Long.MAX_VALUE - 1, Long.MAX_VALUE, upper) == Long.MAX_VALUE,
                "Inclusive upper bound must not overflow");
        check(ExperienceRoll.total(Long.MAX_VALUE, Long.MAX_VALUE, 64, upper) == Long.MAX_VALUE,
                "Fixed stacks must saturate safely");
        check(ExperienceRoll.roll(5, 5, upper) == 5 && ExperienceRoll.total(5, 5, 4, upper) == 20,
                "Already-rolled drops must keep exactly their saved value when stacked");

        var bootstrapped = net.minecraft.server.Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        Item item = Registry.register(BuiltInRegistries.ITEM, ResourceLocation.fromNamespaceAndPath("neoworld_test", "recovery"),
                new Item(new Item.Properties()));
        ItemStack original = new ItemStack(item, 3);
        original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Preserved armor"));
        // No player is needed: the insertion policy must not consult Creative abilities or discard leftovers.
        var inventory = new net.minecraft.world.entity.player.Inventory(null);
        for (int slot = 0; slot < inventory.items.size(); slot++) inventory.setItem(slot, new ItemStack(item, 64));
        ItemStack offered = original.copy();
        UnderLevelArmorRecovery.insertIntoInventory(inventory, offered);
        check(offered.getCount() == 3, "A full inventory must keep all unplaced armor regardless of game mode");
        inventory.setItem(0, original.copyWithCount(63));
        UnderLevelArmorRecovery.insertIntoInventory(inventory, offered);
        check(offered.getCount() == 2 && inventory.getItem(0).getCount() == 64,
                "Only the amount actually inserted may leave recovery");
        inventory.setItem(1, ItemStack.EMPTY);
        UnderLevelArmorRecovery.insertIntoInventory(inventory, offered);
        check(offered.isEmpty() && inventory.getItem(1).getCount() == 2,
                "Free inventory slots must accept the entire remaining amount");
        var recovery = new UnderLevelArmorRecovery();
        recovery.retain(original);
        original.setCount(1);
        recovery.restore(stack -> check(stack.getCount() == 3, "Full inventory must retain the entire copied stack"));
        var ops = RegistryOps.create(NbtOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        var saved = UnderLevelArmorRecovery.CODEC.encodeStart(ops, recovery).getOrThrow();
        var restored = UnderLevelArmorRecovery.CODEC.parse(ops, saved).getOrThrow();
        restored.restore(stack -> stack.shrink(1));
        restored.restore(stack -> {
            check(stack.getCount() == 2 && stack.getHoverName().getString().equals("Preserved armor"),
                    "Partial delivery must preserve remainder and metadata across save/load");
            stack.setCount(0);
        });
        restored.restore(stack -> { throw new AssertionError("Delivered armor must not be returned twice"); });
        System.out.println("Level safety: armor recovery and EXP roll regression checks passed");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
