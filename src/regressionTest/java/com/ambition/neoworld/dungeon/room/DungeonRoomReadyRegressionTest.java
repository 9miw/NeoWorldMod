package com.ambition.neoworld.dungeon.room;

import java.util.List;
import java.util.UUID;

public final class DungeonRoomReadyRegressionTest {
    public static void main(String[] args) {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        var members = List.of(new DungeonRoomMember(owner, "Owner"), new DungeonRoomMember(member, "Member"),
                new DungeonRoomMember(other, "Other"));
        UUID partyId = UUID.randomUUID();
        DungeonRoom room = new DungeonRoom(1, "first", DungeonRoomType.TRIO, partyId, members, owner);
        check(room.isReady(owner), "Owner starts ready");
        room.setReady(owner, false);
        check(room.isReady(owner), "Owner cannot cancel ready");
        check(!room.allReady(), "Members start unready");
        room.setReady(member, true);
        room.setReady(other, true);
        check(room.allReady(), "All members ready");
        DungeonRoom next = new DungeonRoom(2, "second", DungeonRoomType.TRIO, partyId, members, owner);
        check(!next.isReady(member) && !next.allReady(), "Same party in another dungeon must start fresh");
        room.clearReady();
        check(room.isReady(owner) && !room.isReady(member), "Reset preserves only owner readiness");
        room.setReady(member, true);
        room.setReady(member, false);
        check(!room.isReady(member), "Member can cancel readiness or disconnect");
        room.setReady(other, true);
        room.removeMember(owner);
        check(room.ownerId().equals(member) && room.isReady(member), "New owner automatically ready");
        check(!room.isReady(other) && !room.isReady(owner), "Membership changes reset readiness");
        room.setReady(other, true);
        room.addMember(new DungeonRoomMember(owner, "Owner"));
        check(!room.isReady(other) && !room.isReady(owner), "New member resets other members");
        System.out.println("Dungeon room readiness regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
