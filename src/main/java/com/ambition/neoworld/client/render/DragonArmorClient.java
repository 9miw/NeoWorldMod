package com.ambition.neoworld.client.render;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.item.DragonArmorItem;
import com.ambition.neoworld.registry.ModItems;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid=NeoWorld.MODID, value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
public final class DragonArmorClient {
    private static final Map<String, DragonArmorModel> MODELS = new HashMap<>();

    @SubscribeEvent
    public static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                                                          EquipmentSlot slot, HumanoidModel<?> original) {
                String part=((DragonArmorItem)stack.getItem()).modelPart();
                return MODELS.computeIfAbsent(part,DragonArmorModel::new);
            }
        }, ModItems.DRAGON_HELMET.get(), ModItems.DRAGON_CHESTPLATE.get(),
            ModItems.DRAGON_LEGGINGS.get(), ModItems.DRAGON_BOOTS.get());
    }

    @SubscribeEvent
    public static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)manager -> MODELS.clear());
    }
}
