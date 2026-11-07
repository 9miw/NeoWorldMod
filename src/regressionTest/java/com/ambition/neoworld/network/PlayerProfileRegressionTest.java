package com.ambition.neoworld.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import com.ambition.neoworld.network.player.ProfileDetails;

public final class PlayerProfileRegressionTest {
    public static void main(String[] args) {
        var stats = new ArrayList<>(List.of(new PlayerProfilePayload.Stat("attribute.name.generic.attack_damage", 1, 12.5, true)));
        var effects = List.of(new PlayerProfilePayload.Effect("effect.minecraft.speed", 1, -1));
        var payload = new PlayerProfilePayload(UUID.randomUUID(), UUID.randomUUID(), "Player", 35,
                100, 1000, 17.5F, 20, 4.5F, 10, stats, effects, "item.minecraft.diamond_sword");
        stats.clear();
        check(payload.stats().size() == 1, "Snapshot must copy mutable stat list");
        check(roundTrip(payload).equals(payload), "Codec must preserve identity, fractional stats, EXP and infinite effects");
        var empty = new PlayerProfilePayload(UUID.randomUUID(), UUID.randomUUID(), "Player", 100,
                Long.MAX_VALUE, Long.MAX_VALUE, 20, 20, -1, -1, List.of(), List.of(), "");
        check(roundTrip(empty).equals(empty), "Codec must preserve unavailable Epic Fight and max-level EXP");
        var redProfile = new PlayerProfilePayload(UUID.randomUUID(), UUID.randomUUID(), "Killer", 50,
                500, 2000, 20, 20, 10, 10, List.of(), List.of(), "item.minecraft.netherite_sword", 200);
        check(roundTrip(redProfile).killerSin() == 200, "Codec must preserve killer sin");
        var skills = new ArrayList<>(List.of(new ProfileDetails.EquippedSkill("wom:critical_knowledge",
                "skill.wom.critical_knowledge", "wom:textures/gui/skills/critical_knowledge.png", "passive1", true,
                List.of("โอกาสคริติคอล: 20%"))));
        var details = new ProfileDetails(true, 123456, "Public title", 0xAABBCC,
                new ProfileDetails.Pvp(true, 1250, 18, 24, 16), List.of(), skills);
        skills.clear();
        check(details.skills().size() == 1, "Skill snapshot must not alias the input list");
        check(Math.abs(details.pvp().winRate() - 60) < 0.001, "Win rate must be calculated from actual matches");
        check(new ProfileDetails.Pvp(true, 1000, 0, 0, 0).winRate() == 0, "Unranked players have no fabricated win rate");
        var detailed = payload.withDetails(details);
        check(roundTrip(detailed).equals(detailed), "Codec must preserve real skill and PvP metadata");
        check(roundTrip(detailed.withDetails(details.pvp(ProfileDetails.Pvp.LOADING))).details().pvp().loading(),
                "Loading and failed PvP must be distinct states");
        check(detailed.sameSnapshot(roundTrip(detailed)), "PvP replies must match the opened request snapshot");
        check(!detailed.sameSnapshot(detailed.withDetails(new ProfileDetails(true,123457,"",0,
                ProfileDetails.Pvp.UNAVAILABLE,List.of(),List.of()))), "Late replies from an older snapshot must not match");
        var publicProfile = detailed.forViewer(false);
        check(publicProfile.exp() == 0 && publicProfile.requiredExp() == 0 && !publicProfile.details().owner(),
                "Public projection must remove EXP on the server");
        check(publicProfile.weapon().isEmpty() && publicProfile.effects().isEmpty(), "Removed sections must not be broadcast");
        check(detailed.forViewer(true).exp() == payload.exp(), "Owner retains EXP");
        rejects(() -> new ProfileDetails.Pvp(true, 1000, 0, -1, 0));
        rejects(() -> new ProfileDetails.Pvp(true, 1000, 0, 0, 0, true));
        rejects(() -> ProfileDetails.validateGearSlots(List.of("mainhand")));
        rejects(() -> ProfileDetails.validateGearSlots(List.of("offhand")));
        rejects(() -> ProfileDetails.validateGearSlots(List.of("head","head")));
        ProfileDetails.validateGearSlots(List.of("head","chest","legs","feet","fashion_head","fashion_face",
                "fashion_back","fashion_body","fashion_legs","fashion_feet"));
        rejects(() -> new ProfileDetails(true, 1, "", 0, ProfileDetails.Pvp.UNAVAILABLE, List.of(),
                Collections.nCopies(65, details.skills().getFirst())));
        rejects(() -> new PlayerProfilePayload(payload.requestId(), payload.targetId(), "Player", 1, 0, 100,
                20, 20, 1, 1, Collections.nCopies(33, payload.stats().getFirst()), effects, ""));
        rejects(() -> new PlayerProfilePayload(payload.requestId(), payload.targetId(), "Player", 1, 0, 100,
                20, 20, 1, 1, List.of(), Collections.nCopies(65, effects.getFirst()), ""));
        for (int count : new int[] {-1, 33}) {
            var b = buffer();
            try {
                header(b); b.writeVarInt(count);
                rejects(() -> PlayerProfilePayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
        for (int count : new int[] {-1, 65}) {
            var b = buffer();
            try {
                header(b); b.writeVarInt(0); b.writeVarInt(count);
                rejects(() -> PlayerProfilePayload.STREAM_CODEC.decode(b));
            } finally { b.release(); }
        }
        System.out.println("Player profile codec regression checks passed");
    }
    private static void header(RegistryFriendlyByteBuf b) {
        b.writeUUID(UUID.randomUUID()); b.writeUUID(UUID.randomUUID()); b.writeUtf("Player", 16);
        b.writeVarInt(1); b.writeVarLong(0); b.writeVarLong(100);
        b.writeFloat(20); b.writeFloat(20); b.writeFloat(1); b.writeFloat(1);
    }
    private static RegistryFriendlyByteBuf buffer() { return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY); }
    private static PlayerProfilePayload roundTrip(PlayerProfilePayload p) {
        var b = buffer();
        try { PlayerProfilePayload.STREAM_CODEC.encode(b, p); return PlayerProfilePayload.STREAM_CODEC.decode(b); }
        finally { b.release(); }
    }
    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Oversized or negative count must be rejected");
    }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
}
