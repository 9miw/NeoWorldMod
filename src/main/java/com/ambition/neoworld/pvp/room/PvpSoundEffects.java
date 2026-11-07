package com.ambition.neoworld.pvp.room;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Recipient-only vanilla sounds; short sequences run on the existing server tick. */
public final class PvpSoundEffects {
    private record Step(int delay,String sound,float volume,float pitch) {}
    private record Pending(UUID player,long due,Step step) {}
    public enum Cue {
        QUEUED(new Step(0,"block.note_block.chime",.35F,1.1F)),
        CANCELLED(new Step(0,"block.note_block.hat",.3F,.8F)),
        FOUND(new Step(0,"block.note_block.bell",.55F,1),new Step(4,"block.note_block.bell",.55F,1.4F)),
        ACCEPTED(new Step(0,"block.note_block.pling",.35F,1.4F)),
        BOTH_READY(new Step(0,"entity.player.levelup",.25F,1.8F)),
        TICK(new Step(0,"block.note_block.hat",.3F,1)),
        FIGHT(new Step(0,"block.note_block.pling",.5F,1.8F)),
        ROUND_WIN(new Step(0,"entity.experience_orb.pickup",.4F,1.2F)),
        ROUND_LOSS(new Step(0,"block.note_block.bass",.3F,.8F)),
        VICTORY(new Step(0,"ui.toast.challenge_complete",.4F,1)),
        DEFEAT(new Step(0,"block.note_block.didgeridoo",.3F,.8F)),
        ERROR(new Step(0,"block.note_block.bass",.3F,.7F)),
        INVITED(new Step(0,"block.note_block.chime",.35F,1),new Step(4,"block.note_block.chime",.35F,1.3F));
        private final List<Step> steps;
        Cue(Step... steps) { this.steps=List.of(steps); }
    }
    private final List<Pending> pending=new ArrayList<>();
    public void emit(ServerPlayer player,Cue cue,long now) {
        for(var step:cue.steps) {
            if(step.delay()==0) play(player,step);
            else pending.add(new Pending(player.getUUID(),now+step.delay(),step));
        }
    }
    public void tick(MinecraftServer server,long now) {
        var iterator=pending.iterator();
        while(iterator.hasNext()) {
            var note=iterator.next(); if(note.due()>now) continue;
            iterator.remove(); var player=server.getPlayerList().getPlayer(note.player());
            if(player!=null) play(player,note.step());
        }
    }
    public void forget(UUID player) { pending.removeIf(note -> note.player().equals(player)); }
    public void clear() { pending.clear(); }
    public static void error(ServerPlayer player) { play(player,Cue.ERROR.steps.getFirst()); }
    private static void play(ServerPlayer player,Step step) {
        var sound=BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace(step.sound()));
        if(sound!=null) player.playNotifySound(sound,SoundSource.PLAYERS,step.volume(),step.pitch());
    }
}
