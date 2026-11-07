package com.ambition.neoworld.dungeon.definition;

public record DungeonMonsterWave(int cloneTab, String cloneName, int count, SpawnMode spawnMode) {
    public enum SpawnMode {
        ROUND_ROBIN, EACH, RANDOM;

        public String id() { return name().toLowerCase(java.util.Locale.ROOT); }

        public static SpawnMode fromSaved(String value) {
            for (SpawnMode mode : values()) if (mode.id().equals(value)) return mode;
            return ROUND_ROBIN;
        }
    }

    public DungeonMonsterWave(int cloneTab, String cloneName, int count) {
        this(cloneTab, cloneName, count, SpawnMode.ROUND_ROBIN);
    }

    public DungeonMonsterWave {
        if (count < 1 || spawnMode == null) throw new IllegalArgumentException("Invalid wave spawn configuration");
    }

    public int totalCount(int markerCount) {
        if (markerCount < 1) throw new IllegalArgumentException("No mob spawn markers");
        long total = spawnMode == SpawnMode.EACH ? (long) count * markerCount : count;
        if (spawnMode != SpawnMode.ROUND_ROBIN && total > 1024) {
            throw new IllegalArgumentException("Wave spawn limit is 1024 entities in each/random mode");
        }
        return (int) total;
    }

    public int markerIndex(int entityIndex, int markerCount, net.minecraft.util.RandomSource random) {
        return switch (spawnMode) {
            case ROUND_ROBIN -> entityIndex % markerCount;
            case EACH -> entityIndex / count;
            case RANDOM -> random.nextInt(markerCount);
        };
    }
}
