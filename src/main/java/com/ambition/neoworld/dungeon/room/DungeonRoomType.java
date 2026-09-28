package com.ambition.neoworld.dungeon.room;

import java.util.Locale;
import java.util.Optional;

public enum DungeonRoomType {
    SOLO(1),
    TRIO(3),
    RAID(6);

    private final int capacity;

    DungeonRoomType(int capacity) {
        this.capacity = capacity;
    }

    public int capacity() {
        return capacity;
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<DungeonRoomType> parse(String value) {
        for (DungeonRoomType type : values()) {
            if (type.commandName().equalsIgnoreCase(value)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
