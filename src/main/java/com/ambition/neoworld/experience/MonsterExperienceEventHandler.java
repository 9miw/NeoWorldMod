package com.ambition.neoworld.experience;

import com.ambition.neoworld.NeoWorld;
import com.ambition.neoworld.item.ExperienceItem;
import com.ambition.neoworld.level.PlayerLevelManager;
import com.ambition.neoworld.party.PartyManager;
import com.ambition.neoworld.registry.ModDimensionRegistries;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingConversionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.MobSplitEvent;

/** Server-only combat credit and EXP rewards. */
@EventBusSubscriber(modid = NeoWorld.MODID)
public final class MonsterExperienceEventHandler {
    private static final String SPAWNER_ORIGIN = "neoworld:exp_spawner_origin";
    private static final double PARTY_SHARE_RANGE_SQUARED = 32.0 * 32.0;
    // Weak keys do not keep despawned/unloaded entities or their levels alive.
    private static final Map<LivingEntity, ArrayDeque<Float>> HEALTH_BEFORE = new WeakHashMap<>();
    private static final Map<LivingEntity, MonsterExperienceRules.Combat> COMBAT = new WeakHashMap<>();

    private MonsterExperienceEventHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeDamage(LivingDamageEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide() || !reward(entity).hasReward()) return;
        HEALTH_BEFORE.computeIfAbsent(entity, ignored -> new ArrayDeque<>()).push(entity.getHealth());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterDamage(LivingDamageEvent.Post event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        ArrayDeque<Float> snapshots = HEALTH_BEFORE.get(entity);
        if (snapshots == null || snapshots.isEmpty()) return;
        float before = snapshots.pop();
        if (snapshots.isEmpty()) HEALTH_BEFORE.remove(entity);

        // DamageSource#getEntity resolves projectile ownership; pets and machines are not players.
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || event.getNewDamage() <= 0
                || event.getSource().is(DamageTypes.THORNS)
                || entity.getHealth() >= before) return;
        COMBAT.computeIfAbsent(entity, ignored -> new MonsterExperienceRules.Combat())
                .recordDamage(before, entity.getHealth(), entity.level().getGameTime(), MonsterExperienceConfig.settings());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        MonsterExperienceRules.Combat combat = COMBAT.remove(entity);
        if (combat == null || !combat.isEligible(entity.getMaxHealth(), entity.level().getGameTime(), MonsterExperienceConfig.settings())
                || !entity.level().getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)) return;
        MonsterExperienceRules.Reward reward = reward(entity);
        if (!reward.hasReward()) return;

        long total = ExperienceRoll.roll(reward.minimum(), reward.maximum());
        if (sharePartyExperience(event, total)) return;
        ItemEntity drop = new ItemEntity(entity.level(), entity.getX(), entity.getY() + 0.3,
                entity.getZ(), ExperienceItem.createStack(total));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    private static boolean sharePartyExperience(LivingDropsEvent event, long total) {
        LivingEntity mob = event.getEntity();
        if (!(mob.level() instanceof ServerLevel level)
                || level.dimension().equals(ModDimensionRegistries.DUNGEON_LEVEL)
                || !MonsterExperienceConfig.sharesPartyExperience(level.dimension().location())
                || !(event.getSource().getEntity() instanceof ServerPlayer killer)
                || event.getSource().is(DamageTypes.THORNS)
                || !eligiblePartyRecipient(killer, level, mob)) return false;

        var server = level.getServer();
        var party = PartyManager.get(server).findForPlayer(killer.getUUID()).orElse(null);
        if (party == null) return false;

        var recipients = new ArrayList<ServerPlayer>();
        recipients.add(killer);
        for (var member : party.members()) {
            if (member.playerId().equals(killer.getUUID())) continue;
            var player = server.getPlayerList().getPlayer(member.playerId());
            if (eligiblePartyRecipient(player, level, mob)) recipients.add(player);
        }
        if (recipients.size() < 2) return false;

        for (int i = 0; i < recipients.size(); i++) {
            long share = MonsterExperienceRules.partyShare(total, recipients.size(), i);
            if (share <= 0) continue;
            ServerPlayer recipient = recipients.get(i);
            PlayerLevelManager.addExp(recipient, share);
            recipient.displayClientMessage(Component.literal("+" + share + " Party EXP"), true);
        }
        return true;
    }

    private static boolean eligiblePartyRecipient(ServerPlayer player, ServerLevel level, LivingEntity mob) {
        return player != null && player.serverLevel() == level && player.isAlive()
                && !player.isCreative() && !player.isSpectator()
                && player.distanceToSqr(mob) <= PARTY_SHARE_RANGE_SQUARED;
    }

    @SubscribeEvent
    public static void onConversion(LivingConversionEvent.Post event) {
        if (event.getEntity().level().isClientSide()) return;
        if (isSpawner(event.getEntity())) event.getOutcome().getPersistentData().putBoolean(SPAWNER_ORIGIN, true);
        // Transformations start a new fight with new health; do not carry combat credit across.
        COMBAT.remove(event.getEntity());
        HEALTH_BEFORE.remove(event.getEntity());
    }

    @SubscribeEvent
    public static void onSplit(MobSplitEvent event) {
        if (event.getParent().level().isClientSide() || !isSpawner(event.getParent())) return;
        for (Mob child : event.getChildren()) child.getPersistentData().putBoolean(SPAWNER_ORIGIN, true);
    }

    private static boolean isSpawner(LivingEntity entity) {
        // NeoForge saves Mob#getSpawnType in entity NBT, including across world reloads.
        return entity.getPersistentData().getBoolean(SPAWNER_ORIGIN)
                || entity instanceof Mob mob && MobSpawnType.isSpawner(mob.getSpawnType());
    }

    private static MonsterExperienceRules.Reward reward(LivingEntity entity) {
        return MonsterExperienceConfig.settings().reward(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity instanceof Slime slime ? slime.getSize() : 1, isSpawner(entity));
    }
}
