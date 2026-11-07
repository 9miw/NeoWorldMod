package com.ambition.neoworld.network.player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import com.ambition.neoworld.registry.ModAttachments;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.title.service.TitleService;
import com.ambition.neoworld.title.TitleRegistry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

/** Capture only equipped public data, never inventory/container contents or arbitrary custom NBT. */
final class ProfileSnapshotDetails {
    private ProfileSnapshotDetails() {}
    static ProfileDetails capture(ServerPlayer p) {
        String title="";int color=0;
        if(TitleService.isRunning()) {
            var state=TitleService.get().cached(p.getUUID());
            if(state!=null && state.showTitle()) {
                var definition=TitleRegistry.find(state.equipped());
                if(definition.isPresent()) { title=definition.get().display();color=definition.get().rgb(); }
            }
        }
        var gear=new ArrayList<ProfileDetails.Gear>();
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        String[] names={"head","chest","legs","feet"};
        for(int i=0;i<slots.length;i++) gear.add(new ProfileDetails.Gear(names[i],visual(p.getItemBySlot(slots[i]))));
        var fashion=p.getData(ModAttachments.FASHION_DATA);
        for(var slot:FashionSlot.values()) gear.add(new ProfileDetails.Gear("fashion_"+slot.serializedName(),visual(fashion.asMap().getOrDefault(slot,ItemStack.EMPTY))));
        var skills=new ArrayList<ProfileDetails.EquippedSkill>();
        var patch=EpicFightCapabilities.getServerPlayerPatch(p);
        if(patch!=null) patch.getPlayerSkills().listSkillContainers().filter(c -> c.hasSkill()).limit(64).forEach(c -> {
            String slot=c.getSlot().toString().toLowerCase(Locale.ROOT);
            if(slot.equals("combo_attacks")) return;
            var skill=c.getSkill();String id=skill.getRegistryName().toString();
            skills.add(new ProfileDetails.EquippedSkill(id,skill.getTranslationKey(),skill.getSkillTexture().toString(),
                    slot,!c.isDisabled(),bonuses(p,id)));
        });
        return new ProfileDetails(false,System.currentTimeMillis(),title,color,ProfileDetails.Pvp.UNAVAILABLE,gear,skills);
    }
    private static List<String> bonuses(ServerPlayer p,String id) {
        if(id.equals("wom:critical_knowledge")) {
            int fire=0,blast=0;
            for(var stack:p.getArmorSlots()) {
                fire+=stack.getEnchantmentLevel(p.level().holderOrThrow(Enchantments.FIRE_PROTECTION));
                blast+=stack.getEnchantmentLevel(p.level().holderOrThrow(Enchantments.BLAST_PROTECTION));
            }
            // Installed WoM event: chance=20+5*Fire Protection; damage multiplier=2+0.15*Blast Protection.
            return List.of("โอกาสคริติคอลจากสกิล: "+Math.min(100,20+5*fire)+"%",
                    "โบนัสความเสียหายเมื่อคริติคอล: +"+(100+15*blast)+"%",
                    "อ้างอิง enchantment เกราะ ณ ตอนเปิดหน้า");
        }
        if(id.equals("wom:vampirize")) return List.of("ฟื้น HP จากการโจมตี: 20%",
                "อ้างอิงความเสียหายหลังโจมตีที่สกิลได้รับ");
        return List.of();
    }
    private static ItemStack visual(ItemStack source) {
        if(source.isEmpty()) return ItemStack.EMPTY;
        var out=new ItemStack(source.getItem(),1);
        copy(source,out,DataComponents.CUSTOM_MODEL_DATA);copy(source,out,DataComponents.DYED_COLOR);
        copy(source,out,DataComponents.TRIM);copy(source,out,DataComponents.DAMAGE);copy(source,out,DataComponents.MAX_DAMAGE);
        copy(source,out,DataComponents.ENCHANTMENTS);
        out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE,source.hasFoil());
        String name=source.getHoverName().getString();
        out.set(DataComponents.CUSTOM_NAME,Component.literal(name.substring(0,Math.min(name.length(),128))));
        return out;
    }
    private static <T> void copy(ItemStack from,ItemStack to,DataComponentType<T> type) { T value=from.get(type);if(value!=null) to.set(type,value); }
}
