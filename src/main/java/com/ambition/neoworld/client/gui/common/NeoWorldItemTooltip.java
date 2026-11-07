package com.ambition.neoworld.client.gui.common;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.item.CoinItem;
import com.ambition.neoworld.item.ExperienceItem;
import com.ambition.neoworld.item.fashion.SimpleFashionItem;
import com.ambition.neoworld.registry.ModItems;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;

/** Adds presentation only; gameplay restrictions remain in their server handlers. */
@EventBusSubscriber(modid = NeoWorld.MODID, value = Dist.CLIENT)
public final class NeoWorldItemTooltip {
    private static final int TEXT = 0xD4D9DE;
    private static final int MUTED = 0x9EA7AE;
    private static final int TEAL = 0x66DDD5;

    private NeoWorldItemTooltip() {}

    public static boolean applies(ItemStack stack) {
        return !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(NeoWorld.MODID);
    }

    private static Component label(String key, int color, Object... args) {
        return Component.translatable("tooltip.neoworld.shared." + key, args)
                .withStyle(style -> style.withColor(color));
    }

    private static boolean stone(Item item) {
        return item == ModItems.UPGRADE_STONE.get() || item == ModItems.ADVANCED_UPGRADE_STONE.get();
    }

    private static boolean scroll(Item item) {
        return item == ModItems.PROTECTION_SCROLL.get() || item == ModItems.SUCCESS_BOOSTER.get();
    }

    private static String type(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof CoinItem) return "type.currency";
        if (item instanceof ExperienceItem) return "type.exp";
        if (item instanceof ArmorItem) return "type.armor";
        if (stone(item) || scroll(item)) return "type.upgrade";
        if (item instanceof SwordItem || item instanceof TridentItem) return "type.weapon";
        if (item instanceof ShieldItem) return "type.shield";
        if (item instanceof DiggerItem) return "type.tool";
        if (item instanceof BlockItem) return "type.block";
        if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) return "type.food";
        return "type.item";
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void content(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!applies(stack) || event.getToolTip().isEmpty()) return;
        var tooltip = event.getToolTip();
        tooltip.set(0, tooltip.getFirst().copy().withStyle(style -> style.withColor(0xE8C46B)));
        // Fashion retains its established slot glyphs and synchronized owned-state content.
        if (stack.getItem() instanceof SimpleFashionItem) return;
        tooltip.add(1, label(type(stack), MUTED));
        Item item = stack.getItem();
        List<Component> details = new ArrayList<>();
        if (item instanceof ArmorItem armor) {
            details.add(label("armor.slot", TEXT, label("slot." + armor.getType().getSlot().getName(), TEXT)));
        }
        if (stack.isDamageableItem()) {
            details.add(label("durability", TEXT, Math.max(0, stack.getMaxDamage() - stack.getDamageValue()), stack.getMaxDamage()));
        }
        if (stone(item)) {
            boolean basic = item == ModItems.UPGRADE_STONE.get();
            details.add(label("stone.range", TEXT, basic ? 1 : 7, basic ? 6 : 15));
            details.add(label("stone.cost", MUTED));
        }
        if (scroll(item)) details.add(label(item == ModItems.PROTECTION_SCROLL.get()
                ? "scroll.protection" : "scroll.booster", TEXT));
        tooltip.addAll(2, details);
        String usage = null;
        if (item instanceof CoinItem) usage = "use.currency";
        else if (item instanceof ExperienceItem) usage = "use.exp";
        else if (item instanceof ArmorItem) usage = "use.armor";
        else if (stone(item) || scroll(item)) usage = "use.upgrade";
        else if (item instanceof TridentItem) usage = "use.trident";
        else if (item instanceof SwordItem) usage = "use.weapon";
        else if (item instanceof ShieldItem) usage = "use.shield";
        else if (item instanceof DiggerItem) usage = "use.tool";
        else if (item instanceof BlockItem) usage = "use.block";
        else if (stack.has(net.minecraft.core.component.DataComponents.FOOD)) usage = "use.food";
        if (usage != null) {
            tooltip.add(Component.empty());
            tooltip.add(label("usage", TEAL));
            tooltip.add(label(usage, TEXT));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void gather(RenderTooltipEvent.GatherComponents event) {
        if (!applies(event.getItemStack())) return;
        int limit = Math.max(24, Math.min(240, event.getScreenWidth() - NeoTooltipFrame.OUTER_MARGIN * 2));
        if (event.getMaxWidth() >= 0) limit = Math.min(limit, Math.max(24, event.getMaxWidth()));
        event.setMaxWidth(limit);
        var elements = event.getTooltipElements();
        // Leave image components supplied by the item or other mods untouched.
        if (elements.size() < 2 || elements.get(0).left().isEmpty() || elements.get(1).left().isEmpty()) return;
        var name = elements.get(0).left().orElseThrow();
        var type = elements.get(1).left().orElseThrow();
        elements.set(0, Either.right(new NeoItemTooltipHeader.Data(event.getItemStack().copy(), name, type, limit)));
        elements.remove(1);
    }

    @SubscribeEvent
    public static void position(RenderTooltipEvent.Pre event) {
        if (!applies(event.getItemStack()) || !(event.getTooltipPositioner() instanceof DefaultTooltipPositioner)) return;
        int width = 0, height = event.getComponents().size() == 1 ? -2 : 0;
        for (var component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        var position = event.getTooltipPositioner().positionTooltip(event.getScreenWidth(), event.getScreenHeight(),
                event.getX(), event.getY(), width, height);
        int marginX = Math.min(NeoTooltipFrame.OUTER_MARGIN, Math.max(0, (event.getScreenWidth() - width) / 2));
        int marginY = Math.min(NeoTooltipFrame.OUTER_MARGIN, Math.max(0, (event.getScreenHeight() - height) / 2));
        int x = Math.max(marginX, Math.min(position.x(), event.getScreenWidth() - width - marginX));
        int y = Math.max(marginY, Math.min(position.y(), event.getScreenHeight() - height - marginY));
        // Vanilla offsets the text origin +12/-12 from the anchor. Reserve the decorative rim too.
        if (x != position.x()) event.setX(x - 12);
        if (y != position.y()) event.setY(y + 12);
    }

    @SubscribeEvent
    public static void frame(RenderTooltipEvent.Color event) {
        if (!applies(event.getItemStack())) return;
        int width = 0;
        int height = event.getComponents().size() == 1 ? -2 : 0;
        for (var component : event.getComponents()) {
            width = Math.max(width, component.getWidth(event.getFont()));
            height += component.getHeight();
        }
        NeoTooltipFrame.render(event.getGraphics(), NeoTooltipRarity.of(event.getItemStack()),
                event.getX(), event.getY(), width, height);
        event.setBackground(0);
        event.setBorderStart(0);
        event.setBorderEnd(0);
    }
}
