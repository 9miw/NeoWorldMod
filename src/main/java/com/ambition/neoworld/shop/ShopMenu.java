package com.ambition.neoworld.shop;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.registry.ModMenus;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemLore;
import java.util.*;

/** Vanilla inventory synchronization; preview slots never yield or accept items. */
public final class ShopMenu extends AbstractContainerMenu {
    public static final int WIDTH = 300, HEIGHT = 225, PAGE_SIZE = 27;
    public static final int PRODUCT_X = 23, PRODUCT_Y = 60, SLOT_PITCH = 18;
    public static final int INVENTORY_PITCH = 18;
    public static final int INVENTORY_X = PRODUCT_X;
    public static final int INVENTORY_Y = 145, HOTBAR_Y = 201;
    private final Player owner;
    private final SimpleContainer previews = new SimpleContainer(PAGE_SIZE);
    private final ContainerData state = new SimpleContainerData(16);
    private long shopId;
    private List<ShopRepository.Shop> stores = List.of();
    private List<ShopRepository.Offer> offers = List.of();
    private String category = "";
    private List<String> categories = List.of();
    private UUID armedOperation;
    private long lastActionTick = Long.MIN_VALUE;

    public ShopMenu(int id, Inventory inventory) {
        super(ModMenus.SHOP.get(), id); owner = inventory.player;
        state.set(0, -1); state.set(1, 1);
        for (int i = 0; i < PAGE_SIZE; i++) addSlot(new Slot(previews, i,
                PRODUCT_X + i % 9 * SLOT_PITCH, PRODUCT_Y + i / 9 * SLOT_PITCH) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col,
                    INVENTORY_X + col * INVENTORY_PITCH, INVENTORY_Y + row * INVENTORY_PITCH));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, INVENTORY_X + col * INVENTORY_PITCH, HOTBAR_Y));
        addDataSlots(state);
    }

    public static void open(ServerPlayer player, long shop) {
        if (!player.isAlive() || player.isSpectator()) return;
        Component title = Component.literal("ร้านทั้งหมด");
        if (shop != 0) {
            try {
                var store = ShopRepository.shop(shop);
                if (store == null) { player.sendSystemMessage(Component.literal("ไม่พบร้านค้า")); return; }
                title = Component.literal(store.name());
            } catch (Exception e) {
                NeoWorld.LOGGER.error("Could not open shop", e);
                player.sendSystemMessage(Component.literal("เปิดร้านค้าไม่สำเร็จ")); return;
            }
        }
        com.ambition.neoworld.economy.PlayerEconomyManager.syncToClient(player);
        player.openMenu(new SimpleMenuProvider((id, inv, p) -> {
            ShopMenu menu = new ShopMenu(id, inv); menu.shopId = shop; menu.refresh(); return menu;
        }, title));
    }

    public int selected() { return state.get(0); }
    public int quantity() { return state.get(1); }
    public int page() { return state.get(2); }
    public int pages() { return state.get(3); }
    public boolean inShop() { return state.get(4) == 1; }
    public int armed() { return state.get(5); }
    public long buyPrice() { return price(6); }
    public long sellPrice() { return price(10); }
    public ShopView view() { return ShopView.fromId(state.get(15)); }
    public ShopCurrency currency() { return ShopCurrency.fromId(state.get(14)); }
    private long price(int start) {
        long price = 0;
        for (int i = 0; i < 4; i++) price |= (long) (state.get(start + i) & 65535) << (i * 16);
        return price;
    }
    private void price(int start, long value) { for (int i = 0; i < 4; i++) state.set(start + i, (int) (value >>> (i * 16)) & 65535); }

    private void refresh() {
        if (!(owner instanceof ServerPlayer player)) return;
        try {
            stores = ShopRepository.shops();
            offers = shopId == 0 ? List.of() : ShopRepository.offers(shopId, player.registryAccess());
            categories = offers.stream().map(ShopRepository.Offer::category).distinct().toList();
            if (!category.isEmpty() && !categories.contains(category)) category = "";
            if (!category.isEmpty()) offers = offers.stream().filter(o -> o.category().equals(category)).toList();
            offers = offers.stream().filter(o -> view().includes(o.buy(), o.sell())).toList();
            int size = shopId == 0 ? stores.size() : offers.size();
            state.set(3, Math.max(1, (size + PAGE_SIZE - 1) / PAGE_SIZE));
            state.set(2, Math.clamp(page(), 0, pages() - 1));
            state.set(4, shopId == 0 ? 0 : 1); state.set(0, -1); state.set(5, 0); armedOperation = null;
            price(6, 0); price(10, 0);
            state.set(14, ShopCurrency.SILVER.id());
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = page() * PAGE_SIZE + i;
                ItemStack preview = ItemStack.EMPTY;
                if (index < size && shopId == 0) {
                    var shop = stores.get(index); preview = new ItemStack(Items.CHEST);
                    preview.set(DataComponents.CUSTOM_NAME, Component.literal(shop.name()));
                    preview.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(ShopRules.code(shop.id())), Component.literal("คลิกเพื่อเปิดร้าน"))));
                } else if (index < size) {
                    var offer = offers.get(index); preview = offer.item().copy();
                    List<Component> lore = new ArrayList<>(preview.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines());
                    lore.add(Component.literal("หมวด: " + offer.category() + " | สินค้า #" + offer.id()));
                    lore.add(Component.literal(offer.buy() == 0 ? "ไม่จำหน่าย" : "ซื้อ: " + offer.buy() + " " + offer.currency().label() + " / ชิ้น"));
                    lore.add(Component.literal(offer.sell() == 0 ? "ไม่รับซื้อ" : "ขายคืน: " + offer.sell() + " " + offer.currency().label() + " / ชิ้น"));
                    preview.set(DataComponents.LORE, new ItemLore(lore));
                }
                previews.setItem(i, preview);
            }
            broadcastChanges();
        } catch (Exception e) { NeoWorld.LOGGER.error("Could not load shop catalog", e); player.sendSystemMessage(Component.literal("โหลดร้านค้าไม่สำเร็จ กรุณาติดต่อผู้ดูแล")); }
    }

    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (slot >= slots.size() || (slot < 0 && slot != -999 && slot != -1)) return;
        if (slot >= 0 && slot < PAGE_SIZE) {
            if (player != owner || player.level().isClientSide() || type != ClickType.PICKUP || button != 0 || !getCarried().isEmpty()) return;
            int index = page() * PAGE_SIZE + slot;
            if (shopId == 0 && index < stores.size()) {
                open((ServerPlayer) player, stores.get(index).id());
            } else if (shopId != 0 && index < offers.size()) {
                state.set(0, slot); state.set(5, 0);
                price(6, offers.get(index).buy()); price(10, offers.get(index).sell());
                state.set(14, offers.get(index).currency().id()); broadcastChanges();
            }
            return;
        }
        // Never let a client snapshot substitute for the owner or use preview stacks in pick-all.
        if (player == owner) { state.set(5, 0); super.clicked(slot, button, type, player); }
    }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return slot.container != previews && super.canTakeItemForPickAll(stack, slot); }
    @Override public boolean canDragTo(Slot slot) { return slot.container != previews && super.canDragTo(slot); }
    @Override public boolean stillValid(Player player) { return player == owner && player.isAlive(); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < PAGE_SIZE || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); ItemStack stack = slot.getItem(), copy = stack.copy();
        if (stack.isEmpty() || !moveItemStackTo(stack, index < 54 ? 54 : 27, index < 54 ? 63 : 54, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }

    @Override public boolean clickMenuButton(Player player, int button) {
        if (!(player instanceof ServerPlayer serverPlayer) || player != owner || player.containerMenu != this || !stillValid(player) || !getCarried().isEmpty()) return false;
        if (button >= 12 && button <= 14) {
            if (shopId == 0) return false;
            state.set(15, button - 12);
            state.set(2, 0);
            refresh();
            return true;
        }
        if (button == 0) { open(serverPlayer, 0); return true; }
        if (button == 9) { refresh(); return true; }
        if (button == 1 || button == 2) { state.set(2, Math.clamp(page() + (button == 1 ? -1 : 1), 0, pages() - 1)); refresh(); return true; }
        if (button >= 3 && button <= 5) { state.set(1, button == 3 ? 1 : button == 4 ? 16 : 64); state.set(5, 0); broadcastChanges(); return true; }
        if (button == 10 || button == 11) {
            if (shopId == 0 || selected() < 0) return false;
            state.set(1, Math.clamp(quantity() + (button == 10 ? -1 : 1), 1, 64));
            state.set(5, 0); // Quantity changes require a fresh trade confirmation.
            broadcastChanges();
            return true;
        }
        if (button == 8 && shopId != 0) {
            int index = category.isEmpty() ? -1 : categories.indexOf(category);
            category = index + 1 >= categories.size() ? "" : categories.get(index + 1);
            state.set(2, 0); refresh(); player.sendSystemMessage(Component.literal("หมวด: " + (category.isEmpty() ? "ทั้งหมด" : category))); return true;
        }
        if ((button != 6 && button != 7) || selected() < 0 || shopId == 0) return false;
        int index = page() * PAGE_SIZE + selected();
        if (index >= offers.size()) return false;
        var offer = offers.get(index); boolean sell = button == 7;
        if ((sell && view() == ShopView.STORE_SELLS) || (!sell && view() == ShopView.STORE_BUYS)) return false;
        if ((sell ? offer.sell() : offer.buy()) <= 0) return false;
        if (armed() != button) {
            try {
                long total = ShopRules.total(sell ? offer.sell() : offer.buy(), quantity());
                armedOperation = UUID.randomUUID();
                state.set(5, button); broadcastChanges();
                player.sendSystemMessage(Component.literal((sell ? "ขาย " : "ซื้อ ") + offer.item().getHoverName().getString() + " × " + quantity() + " รวม " + total + " " + offer.currency().label() + " — กดปุ่มยืนยันอีกครั้ง"));
            } catch (RuntimeException e) { player.sendSystemMessage(Component.literal("ยอดรวมเกินขอบเขตที่รองรับ")); }
            return true;
        }
        long tick = serverPlayer.serverLevel().getGameTime();
        if (lastActionTick != Long.MIN_VALUE && tick - lastActionTick < 5) return false;
        if (armedOperation == null) return false;
        UUID operation = armedOperation; armedOperation = null;
        lastActionTick = tick; state.set(5, 0);
        player.sendSystemMessage(Component.literal(ShopService.trade(serverPlayer, operation, offer, quantity(), sell)));
        broadcastChanges(); return true;
    }
}
