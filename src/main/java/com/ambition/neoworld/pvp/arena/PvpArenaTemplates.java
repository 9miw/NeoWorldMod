package com.ambition.neoworld.pvp.arena;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** Reads PvP DATA markers without depending on dungeon code. */
public final class PvpArenaTemplates {
    public static final List<String> MARKERS = List.of(
            "pvp_spawn_a", "pvp_spawn_b");

    private PvpArenaTemplates() { }

    public record ResolvedPiece(PvpArenaPiece piece,StructureTemplate template,List<StructureTemplate.StructureBlockInfo> blocks,List<StructureTemplate.StructureBlockInfo> markerBlocks) { }
    private static final class PinnedPalette extends StructurePlaceSettings {
        private StructureTemplate.Palette palette;
        @Override public StructureTemplate.Palette getRandomPalette(List<StructureTemplate.Palette> palettes,BlockPos pos) {
            if(palette==null) palette=super.getRandomPalette(palettes,pos);
            return palette;
        }
    }
    public record Layout(List<ResolvedPiece> pieces,BlockPos size,Map<String, BlockPos> markers) {
        public Layout { pieces=List.copyOf(pieces); markers = Map.copyOf(markers); }
        public BlockPos point(String name, BlockPos origin) { return origin.offset(markers.get(name)); }
        public PvpArenaBounds bounds() { return new PvpArenaBounds(size.getX(),size.getY(),size.getZ()); }
        public boolean contains(BlockPos origin,double x,double y,double z) { return bounds().contains(x-origin.getX(),y-origin.getY(),z-origin.getZ()); }
    }

    public static Layout inspect(ServerLevel level, ResourceLocation id) {
        return inspect(level,List.of(new PvpArenaPiece(id,BlockPos.ZERO)));
    }
    public static Layout inspect(ServerLevel level,List<PvpArenaPiece> pieces) {
        if(pieces.isEmpty() || pieces.size()>512) throw new IllegalArgumentException("Arena needs 1..512 structure pieces");
        var resolved=new java.util.ArrayList<ResolvedPiece>();
        for(var piece:pieces) resolved.add(resolve(level,piece));
        return assemble(resolved);
    }
    /** Load and inspect one piece, allowing callers to spread disk reads over ticks. */
    public static ResolvedPiece resolve(ServerLevel level,PvpArenaPiece piece) {
            var template=level.getStructureManager().get(piece.template()).orElseThrow(() -> new IllegalArgumentException("Missing structure: "+piece.template()));
            PvpCapturePlan.checkSize(template.getSize().getX(),template.getSize().getY(),template.getSize().getZ());
            var settings=new PinnedPalette();
            settings.setRandom(net.minecraft.util.RandomSource.create(31L*piece.template().hashCode()+piece.offset().asLong()));
            var markerBlocks=template.filterBlocks(BlockPos.ZERO,settings,Blocks.STRUCTURE_BLOCK);
            if(settings.palette==null) throw new IllegalArgumentException("Structure has no palette: "+piece.template());
            return new ResolvedPiece(piece,template,java.util.Collections.unmodifiableList(settings.palette.blocks()),markerBlocks);
    }
    public static Layout assemble(List<ResolvedPiece> resolved) {
        var size=PvpArenaGeometry.size(resolved.stream().map(p -> new PvpArenaGeometry.Part(p.piece().offset(),p.template().getSize())).toList());
        Map<String, BlockPos> markers = new LinkedHashMap<>();
        for(var part:resolved) for (var info : part.markerBlocks()) {
            if (info.nbt() == null || !"DATA".equals(info.nbt().getString("mode"))) continue;
            String name = info.nbt().getString("metadata").trim();
            if (!MARKERS.contains(name)) continue;
            var partSize=part.template().getSize();
            if(!new PvpArenaBounds(partSize.getX(),partSize.getY(),partSize.getZ()).contains(info.pos().getX(),info.pos().getY(),info.pos().getZ()))
                throw new IllegalArgumentException("DATA marker outside its structure: "+name);
            if (markers.putIfAbsent(name, part.piece().offset().offset(info.pos()).immutable()) != null) {
                throw new IllegalArgumentException("Duplicate DATA marker: " + name);
            }
        }
        PvpArenaGeometry.markers(size,markers);
        return new Layout(resolved,size,markers);
    }

    public static boolean resourcesPresent(ServerLevel level,List<PvpArenaPiece> pieces) {
        if(pieces.isEmpty()) return false;
        for(var piece:pieces) {
            var resource=ResourceLocation.fromNamespaceAndPath(piece.template().getNamespace(),"structure/"+piece.template().getPath()+".nbt");
            if(!java.nio.file.Files.isRegularFile(level.getStructureManager().createAndValidatePathToGeneratedStructure(piece.template(),".nbt"))
                    && level.getServer().getResourceManager().getResource(resource).isEmpty()) return false;
        }
        return true;
    }
}
