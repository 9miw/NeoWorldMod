package com.ambition.neoworld.network.player;

import com.ambition.neoworld.network.PlayerProfilePayload;

import java.util.ArrayList;
import com.ambition.neoworld.level.LevelFormula;
import com.ambition.neoworld.registry.ModAttachments;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import yesman.epicfight.registry.entries.EpicFightAttributes;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

public final class PlayerProfileSnapshot {
    private PlayerProfileSnapshot() {}
    public static PlayerProfilePayload capture(PlayerActionPayload request, ServerPlayer player) {
        var stats = new ArrayList<PlayerProfilePayload.Stat>();
        var patch = EpicFightCapabilities.getServerPlayerPatch(player);
        for (Holder<Attribute> attribute : java.util.List.<Holder<Attribute>>of(Attributes.ATTACK_DAMAGE, Attributes.ATTACK_SPEED,
                Attributes.ARMOR, Attributes.ARMOR_TOUGHNESS, Attributes.MOVEMENT_SPEED,
                Attributes.KNOCKBACK_RESISTANCE, EpicFightAttributes.ARMOR_NEGATION,
                EpicFightAttributes.IMPACT, EpicFightAttributes.WEIGHT, EpicFightAttributes.STUN_ARMOR,
                EpicFightAttributes.MAX_STRIKES, EpicFightAttributes.STAMINA_REGEN)) {
            var instance = player.getAttribute(attribute);
            if (instance == null) continue;
            boolean weapon = patch != null && (attribute == Attributes.ATTACK_DAMAGE || attribute == EpicFightAttributes.IMPACT
                    || attribute == EpicFightAttributes.ARMOR_NEGATION || attribute == EpicFightAttributes.MAX_STRIKES);
            double value = weapon ? patch.getWeaponAttribute(attribute, player.getMainHandItem()) : instance.getValue();
            if (patch != null && attribute == Attributes.ATTACK_SPEED) value = patch.getAttackSpeed(net.minecraft.world.InteractionHand.MAIN_HAND);
            stats.add(new PlayerProfilePayload.Stat(attribute.value().getDescriptionId(), instance.getBaseValue(), value,
                    weapon || (patch != null && attribute == Attributes.ATTACK_SPEED)));
        }
        var effects = player.getActiveEffects().stream().limit(64)
                .map(e -> new PlayerProfilePayload.Effect(e.getDescriptionId(), e.getAmplifier(), e.getDuration())).toList();
        var data = player.getData(ModAttachments.PLAYER_DATA);
        return new PlayerProfilePayload(request.requestId(), player.getUUID(), player.getGameProfile().getName(),
                data.getLevel(), data.getExp(), LevelFormula.getRequiredExpForNextLevel(data.getLevel()),
                player.getHealth(), player.getMaxHealth(), patch == null ? -1 : patch.getStamina(),
                patch == null ? -1 : patch.getMaxStamina(), stats, effects,
                player.getMainHandItem().isEmpty() ? "" : player.getMainHandItem().getItem().getDescriptionId());
    }
}
