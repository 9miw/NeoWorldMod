package com.ambition.neoworld.dungeon.structure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Loads, validates, and places a complete dungeon layout into an instance slot. */
public final class DungeonStructurePlacer {
    private DungeonStructurePlacer() {
    }

    public static DungeonPlacementResult place(
            ServerLevel level,
            DungeonTemplateLayout layout,
            BlockPos slotOrigin
    ) {
        Preparation preparation = prepare(level, layout, slotOrigin);
        if (preparation.error() != null) {
            return DungeonPlacementResult.failure(preparation.error());
        }

        RandomSource random = level.getRandom();
        int placedCount = 0;
        for (LoadedPiece piece : preparation.loadedPieces()) {
            StructurePlaceSettings placementSettings = new StructurePlaceSettings()
                    .setIgnoreEntities(true)
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
            if (!piece.template().placeInWorld(
                    level,
                    piece.origin(),
                    piece.origin(),
                    placementSettings,
                    random,
                    2
            )) {
                return DungeonPlacementResult.failure(
                        "Failed to place structure template at " + piece.origin().toShortString()
                );
            }
            placedCount++;
        }

        return DungeonPlacementResult.success(placedCount, preparation.markers());
    }

    public static DungeonPlacementResult validate(ServerLevel level, DungeonTemplateLayout layout) {
        Preparation preparation = prepare(level, layout, BlockPos.ZERO);
        return preparation.error() == null
                ? DungeonPlacementResult.success(0, preparation.markers())
                : DungeonPlacementResult.failure(preparation.error());
    }

    private static Preparation prepare(
            ServerLevel level,
            DungeonTemplateLayout layout,
            BlockPos slotOrigin
    ) {
        List<LoadedPiece> loadedPieces = new ArrayList<>();
        Map<String, List<BlockPos>> markers = new HashMap<>();

        for (DungeonTemplatePiece piece : layout.pieces()) {
            StructureTemplate template = level.getStructureManager().get(piece.templateId()).orElse(null);
            if (template == null) {
                return Preparation.failure("Missing structure template: " + piece.templateId());
            }

            BlockPos pieceOrigin = slotOrigin.offset(piece.offset());
            StructurePlaceSettings markerSettings = new StructurePlaceSettings();
            collectMarkers(template, pieceOrigin, markerSettings, markers);
            loadedPieces.add(new LoadedPiece(template, pieceOrigin));
        }

        List<String> missingMarkers = layout.requiredMarkers().stream()
                .filter(marker -> !markers.containsKey(marker))
                .sorted()
                .toList();
        if (!missingMarkers.isEmpty()) {
            return Preparation.failure(
                    "Missing required DATA markers: " + String.join(", ", missingMarkers)
            );
        }
        return Preparation.success(loadedPieces, markers);
    }

    private static void collectMarkers(
            StructureTemplate template,
            BlockPos origin,
            StructurePlaceSettings settings,
            Map<String, List<BlockPos>> markers
    ) {
        for (StructureTemplate.StructureBlockInfo info
                : template.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK)) {
            if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) {
                continue;
            }
            String markerName = info.nbt().getString("metadata").trim();
            if (!markerName.isEmpty()) {
                markers.computeIfAbsent(markerName, ignored -> new ArrayList<>()).add(info.pos());
            }
        }
    }

    private record LoadedPiece(StructureTemplate template, BlockPos origin) {
    }

    private record Preparation(
            List<LoadedPiece> loadedPieces,
            Map<String, List<BlockPos>> markers,
            String error
    ) {
        private static Preparation success(
                List<LoadedPiece> loadedPieces,
                Map<String, List<BlockPos>> markers
        ) {
            return new Preparation(loadedPieces, markers, null);
        }

        private static Preparation failure(String error) {
            return new Preparation(List.of(), Map.of(), error);
        }
    }
}
