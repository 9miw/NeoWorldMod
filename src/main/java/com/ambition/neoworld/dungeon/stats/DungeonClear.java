package com.ambition.neoworld.dungeon.stats;

import com.ambition.neoworld.dungeon.room.DungeonRoomType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Immutable snapshot captured on the server thread, before rewards or returning spectators. */
public record DungeonClear(UUID runId, String dungeonId, DungeonRoomType mode, long clearTicks,
        long completedAtMillis, List<Member> members) {
    public record Member(UUID playerId, boolean credited) {
        public Member { Objects.requireNonNull(playerId); }
    }

    public DungeonClear {
        Objects.requireNonNull(runId);
        Objects.requireNonNull(mode);
        if (dungeonId == null || dungeonId.isBlank() || dungeonId.length() > 191
                || clearTicks < 0 || completedAtMillis <= 0) {
            throw new IllegalArgumentException("Invalid dungeon completion");
        }
        members = members.stream().sorted(Comparator.comparing(m -> m.playerId().toString())).toList();
        if (members.isEmpty() || members.size() > mode.capacity()
                || members.stream().map(Member::playerId).distinct().count() != members.size()) {
            throw new IllegalArgumentException("Invalid dungeon participant roster");
        }
    }

    public static DungeonClear completed(UUID runId, String dungeonId, DungeonRoomType mode,
            long startedTick, long finishedTick, long completedAtMillis,
            List<UUID> participants, Set<UUID> returned) {
        if (startedTick < 0 || finishedTick < startedTick) throw new IllegalArgumentException("Invalid clear clock");
        return new DungeonClear(runId, dungeonId, mode, finishedTick - startedTick, completedAtMillis,
                participants.stream().map(id -> new Member(id, !returned.contains(id))).toList());
    }

    public Set<UUID> creditedPlayers() {
        return members.stream().filter(Member::credited).map(Member::playerId).collect(Collectors.toUnmodifiableSet());
    }
}
