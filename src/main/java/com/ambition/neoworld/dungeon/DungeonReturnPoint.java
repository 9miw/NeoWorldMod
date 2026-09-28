package com.ambition.neoworld.dungeon;

/** Location used to return a player after an instance ends or recovery runs. */
public record DungeonReturnPoint(
        String dimensionId,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public DungeonReturnPoint {
        if (dimensionId == null || dimensionId.isBlank()) {
            throw new IllegalArgumentException("Dimension id cannot be blank");
        }
    }
}
