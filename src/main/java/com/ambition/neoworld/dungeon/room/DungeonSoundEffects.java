package com.ambition.neoworld.dungeon.room;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Vanilla assets, private recipient playback; no physical fireworks or client registry. */
public final class DungeonSoundEffects {
    public record Step(int delay, ResourceLocation sound, float volume, float pitch) {
        public Step {
            if (delay < 0 || delay > 40 || !Float.isFinite(volume) || volume < 0 || volume > 1
                    || !Float.isFinite(pitch) || pitch < .5F || pitch > 2) throw new IllegalArgumentException("Invalid dungeon sound");
        }
    }

    public enum Cue {
        CREATED(List.of(step(0, "entity.experience_orb.pickup", .3F, 1.2F))),
        MEMBER_JOIN(List.of(step(0, "block.note_block.chime", .35F, 1.1F))),
        READY(List.of(step(0, "block.note_block.pling", .3F, 1.4F))),
        UNREADY(List.of(step(0, "block.note_block.bass", .3F, .8F))),
        ALL_READY(List.of(step(0, "block.note_block.chime", .45F, 1.6F))),
        START(List.of(step(0, "block.end_portal_frame.fill", .35F, 1.1F))),
        WAVE(List.of(step(0, "block.note_block.bell", .4F, 1.25F))),
        BOSS(List.of(step(0, "entity.ender_dragon.growl", .22F, 1.25F))),
        VICTORY(List.of(step(0, "entity.firework_rocket.launch", .3F, 1),
                step(6, "entity.firework_rocket.blast", .4F, 1),
                step(12, "entity.firework_rocket.blast", .35F, 1.25F),
                step(18, "block.note_block.chime", .4F, 1.6F))),
        DEFEAT(List.of(step(0, "block.note_block.bass", .45F, .6F)));

        private final List<Step> steps;
        Cue(List<Step> steps) { this.steps = List.copyOf(steps); }
        public List<Step> steps() { return steps; }
    }

    private DungeonSoundEffects() {}
    private static Step step(int delay, String id, float volume, float pitch) {
        return new Step(delay, ResourceLocation.withDefaultNamespace(id), volume, pitch);
    }

    static void play(ServerPlayer player, Step step) {
        var sound = BuiltInRegistries.SOUND_EVENT.get(step.sound());
        if (sound != null) player.playNotifySound(sound, SoundSource.PLAYERS, step.volume(), step.pitch());
    }
}
