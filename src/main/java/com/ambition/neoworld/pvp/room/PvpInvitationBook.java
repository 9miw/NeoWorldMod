package com.ambition.neoworld.pvp.room;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Short-lived invitations; one pending invite per recipient and room. Server ticks only. */
public final class PvpInvitationBook {
    public record Invitation(UUID token,int room,UUID owner,UUID target,long expires) { }
    private final Map<UUID,Invitation> pending=new HashMap<>();
    public Invitation create(int room,UUID owner,UUID target,long now) {
        expire(now);
        if(room<1 || owner.equals(target) || pending.values().stream().anyMatch(i -> i.room()==room || i.target().equals(target)))
            throw new IllegalArgumentException("มีคำเชิญที่รอการตอบรับอยู่แล้ว หรือเชิญตัวเองไม่ได้");
        var invitation=new Invitation(UUID.randomUUID(),room,owner,target,now+600);
        pending.put(invitation.token(),invitation); return invitation;
    }
    public Invitation take(UUID token,UUID target,long now) {
        expire(now); var invitation=pending.get(token);
        if(invitation==null || !invitation.target().equals(target)) throw new IllegalArgumentException("คำเชิญหมดอายุหรือไม่ใช่คำเชิญของคุณ");
        pending.remove(token); return invitation;
    }
    public void revokeRoom(int room) { pending.values().removeIf(i -> i.room()==room); }
    public void revokePlayer(UUID player) { pending.values().removeIf(i -> i.owner().equals(player) || i.target().equals(player)); }
    public void expire(long now) { pending.values().removeIf(i -> now>=i.expires()); }
}
