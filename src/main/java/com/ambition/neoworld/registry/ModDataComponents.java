package com.ambition.neoworld.registry;

import com.ambition.neoworld.NeoWorld;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

// คลาสลงทะเบียน Data Component Type สำหรับเก็บข้อมูลพิเศษใน ItemStack เช่น มูลค่าของเหรียญ
public class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, NeoWorld.MODID);

    // มูลค่าของเหรียญต่อ 1 ชิ้น (Default ถ้าไม่มีคือ 1)
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> COIN_VALUE =
            DATA_COMPONENTS.registerComponentType("coin_value",
                    builder -> builder.persistent(Codec.LONG.validate(value -> value > 0
                            ? DataResult.success(value)
                            : DataResult.error(() -> "Coin value must be positive")))
                            .networkSynchronized(ByteBufCodecs.VAR_LONG));
}
