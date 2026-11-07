package com.ambition.neoworld.world.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Loads, validates, and places a complete structure layout into an instance slot. */
public final class StructurePlacer {
    private StructurePlacer() {
    }

    public static StructurePlacementResult place(
            ServerLevel level,
            StructureLayout layout,
            BlockPos slotOrigin
    ) {
        Preparation preparation = prepare(level, layout, slotOrigin, true);
        if (preparation.error() != null) {
            return StructurePlacementResult.failure(preparation.error());
        }

        RandomSource random = level.getRandom();
        int placedCount = 0;
        for (int index = 0; index < preparation.loadedPieces().size(); index++) {
            LoadedPiece piece = preparation.loadedPieces().get(index);
            StructurePlaceSettings placementSettings = piece.settings().setIgnoreEntities(true)
                    .addProcessor(new OverlapProcessor(preparation.overlap(), index));
            if (!piece.template().placeInWorld(
                    level,
                    piece.origin(),
                    piece.origin(),
                    placementSettings,
                    random,
                    2
            )) {
                return StructurePlacementResult.failure(
                        "Failed to place structure template at " + piece.origin().toShortString()
                );
            }
            placedCount++;
        }

        return StructurePlacementResult.success(placedCount, preparation.markers());
    }

    public static StructurePlacementResult validate(ServerLevel level, StructureLayout layout) {
        Preparation preparation = prepare(level, layout, BlockPos.ZERO, false);
        return preparation.error() == null
                ? StructurePlacementResult.success(0, preparation.markers())
                : StructurePlacementResult.failure(preparation.error());
    }

    private static Preparation prepare(
            ServerLevel level,
            StructureLayout layout,
            BlockPos slotOrigin, boolean checkHeight
    ) {
        List<LoadedPiece> loadedPieces = new ArrayList<>();
        StructureOverlapPlan<BlockState> overlap = new StructureOverlapPlan<>();

        for (StructurePiece piece : layout.pieces()) {
            StructureTemplate template = level.getStructureManager().get(piece.templateId()).orElse(null);
            if (template == null) {
                return Preparation.failure("Missing structure template: " + piece.templateId());
            }

            BlockPos pieceOrigin = slotOrigin.offset(piece.offset());
            if (template.getSize().getX() < 1 || template.getSize().getY() < 1 || template.getSize().getZ() < 1)
                return Preparation.failure("Empty structure template: " + piece.templateId());
            if(checkHeight && (pieceOrigin.getY()<level.getMinBuildHeight() || (long)pieceOrigin.getY()+template.getSize().getY()>level.getMaxBuildHeight()))
                return Preparation.failure("Structure exceeds destination dimension height: " + piece.templateId());
            PinnedPaletteSettings settings = new PinnedPaletteSettings();
            // Keep validation and placement on the same palette, independent of instance slot.
            settings.setRandom(RandomSource.create(31L * piece.templateId().hashCode() + piece.offset().asLong()));
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(pieceOrigin, settings, Blocks.STRUCTURE_BLOCK)) {
                if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
                String error = overlap.addMarker(piece.templateId().toString(), info.pos(), info.nbt().getString("metadata").trim());
                if (error != null) return Preparation.failure(error);
            }
            if (settings.palette == null || settings.palette.blocks().isEmpty())
                return Preparation.failure("Empty structure palette: " + piece.templateId());
            loadedPieces.add(new LoadedPiece(template, pieceOrigin, piece.templateId().toString(), settings,
                    template.getBoundingBox(settings, pieceOrigin)));
        }

        for (int index = 0; index < loadedPieces.size(); index++) {
            LoadedPiece piece = loadedPieces.get(index);
            List<BoundingBox> intersections = new ArrayList<>();
            for (int other = 0; other < loadedPieces.size(); other++) {
                if (other == index) continue;
                BoundingBox box = loadedPieces.get(other).bounds();
                if (piece.bounds().intersects(box)) intersections.add(box);
            }
            if (intersections.isEmpty()) continue;
            for (StructureTemplate.StructureBlockInfo info : piece.settings().palette.blocks()) {
                if (info.state().is(Blocks.STRUCTURE_BLOCK) || info.state().is(Blocks.STRUCTURE_VOID)) continue;
                BlockPos position = info.pos().offset(piece.origin());
                if (intersections.stream().noneMatch(box -> box.isInside(position))) continue;
                String error = overlap.addBlock(index, piece.name(), position, info.state(), info.state().isAir(), info.nbt());
                if (error != null) return Preparation.failure(error);
            }
        }
        Map<String, List<BlockPos>> markers = overlap.markers();

        List<String> missingMarkers = layout.requiredMarkers().stream()
                .filter(marker -> !markers.containsKey(marker))
                .sorted()
                .toList();
        if (!missingMarkers.isEmpty()) {
            return Preparation.failure(
                    "Missing required DATA markers: " + String.join(", ", missingMarkers)
            );
        }
        return new Preparation(loadedPieces, markers, overlap, null);
    }

    private static final class PinnedPaletteSettings extends StructurePlaceSettings {
        private StructureTemplate.Palette palette;
        @Override
        public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> palettes, BlockPos position) {
            if (palette == null) palette = super.getRandomPalette(palettes, position);
            return palette;
        }
    }

    private static final class OverlapProcessor extends StructureProcessor {
        private final StructureOverlapPlan<BlockState> plan;
        private final int piece;
        private OverlapProcessor(StructureOverlapPlan<BlockState> plan, int piece) { this.plan = plan; this.piece = piece; }
        @Override
        public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos origin, BlockPos pivot,
                StructureTemplate.StructureBlockInfo local, StructureTemplate.StructureBlockInfo world, StructurePlaceSettings settings) {
            if (world.state().is(Blocks.STRUCTURE_BLOCK) || world.state().is(Blocks.STRUCTURE_VOID)) return null;
            return plan.allows(piece, world.pos()) ? world : null;
        }
        @Override
        protected StructureProcessorType<?> getType() {
            throw new UnsupportedOperationException("Runtime structure overlap processor is not serializable");
        }
    }

    private record LoadedPiece(StructureTemplate template, BlockPos origin, String name,
                               PinnedPaletteSettings settings, BoundingBox bounds) {}

    private record Preparation(
            List<LoadedPiece> loadedPieces,
            Map<String, List<BlockPos>> markers,
            StructureOverlapPlan<BlockState> overlap,
            String error
    ) {
        private static Preparation failure(String error) {
            return new Preparation(List.of(), Map.of(), new StructureOverlapPlan<>(), error);
        }
    }
}
