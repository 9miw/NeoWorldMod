package com.ambition.neoworld.client.gui.player;

import java.util.EnumMap;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.network.PlayerProfilePayload;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;

/** Detached snapshot model. Never mutates the live player or displays either hand slot. */
final class ProfilePlayerPreview extends RemotePlayer {
    private final EnumMap<FashionSlot,ItemStack> fashion=new EnumMap<>(FashionSlot.class);
    ProfilePlayerPreview(ClientLevel level,GameProfile profile,PlayerProfilePayload snapshot) {
        super(level,profile);
        for(var gear:snapshot.details().gear()) {
            if(gear.slot().startsWith("fashion_")) {
                if(!gear.stack().isEmpty()) FashionSlot.fromName(gear.slot().substring(8)).ifPresent(slot -> fashion.put(slot,gear.stack()));
            } else {
                EquipmentSlot slot=switch(gear.slot()) {case "head" -> EquipmentSlot.HEAD;case "chest" -> EquipmentSlot.CHEST;
                    case "legs" -> EquipmentSlot.LEGS;case "feet" -> EquipmentSlot.FEET;default -> null;};
                if(slot!=null) setItemSlot(slot,gear.stack());
            }
        }
    }
    @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }
    void render(GuiGraphics g,float mx,float my) {
        ClientFashionData.withPreview(getId(),fashion,() ->
                InventoryScreen.renderEntityInInventoryFollowsMouse(g,24,54,148,245,
                        fashion.containsKey(FashionSlot.BACK) ? 48 : 62,0.0625F,mx,my,this));
    }
}
