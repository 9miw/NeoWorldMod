package com.ambition.neoworld.pvp.arena;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/** One bounded batch per tick, with chunk preparation and native block-entity placement. */
public final class PvpArenaPlacement implements AutoCloseable {
    private static final int CHUNKS_PER_TICK=4,MAX_TICKETS=64;
    private static final TicketType<ChunkPos> TICKET=TicketType.create("neoworld_pvp_build",Comparator.comparingLong(ChunkPos::toLong));
    private final ServerLevel level;
    private final PvpArenaTemplates.Layout layout;
    private final BlockPos origin;
    private final PvpPlacementCursor cursor;
    private final Set<ChunkPos> held=new LinkedHashSet<>();
    private Set<ChunkPos> needed;
    private Iterator<ChunkPos> chunks;
    private PvpPlacementCursor.Slice pending;
    private boolean finished,closed;
    public PvpArenaPlacement(ServerLevel level,PvpArenaTemplates.Layout layout,BlockPos origin) {
        this.level=level; this.layout=layout; this.origin=origin.immutable();
        if(origin.getY()<level.getMinBuildHeight() || (long)origin.getY()+layout.size().getY()>level.getMaxBuildHeight())
            throw new IllegalArgumentException("Arena exceeds dimension height");
        cursor=new PvpPlacementCursor(layout.pieces().stream().map(p -> p.blocks().size()).toList());
    }
    public int percent() { return cursor.total()==0 ? 100 : (int)(100L*cursor.completed()/cursor.total()); }
    public boolean tick() {
        if(closed) throw new IllegalStateException("Placement was closed");
        if(finished) return true;
        if(chunks==null) prepare();
        int loads=0;
        while(chunks.hasNext()) {
            var chunk=chunks.next(); hold(chunk);
            if(!level.hasChunk(chunk.x,chunk.z)) {
                level.getChunk(chunk.x,chunk.z);
                if(++loads>=CHUNKS_PER_TICK) return false;
            }
        }
        if(pending==null) { finished=true; return true; }
        place(pending); cursor.advance(pending);
        pending=null; chunks=null; needed=null;
        return false; // Spawn chunks are prepared on a later tick, before any teleport.
    }
    private void prepare() {
        needed=new LinkedHashSet<>();
        if(cursor.finished()) {
            for(var marker:layout.markers().values()) {
                var center=new ChunkPos(origin.offset(marker));
                for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) needed.add(new ChunkPos(center.x+x,center.z+z));
            }
        } else {
            var slice=cursor.next(); var piece=layout.pieces().get(slice.part());
            var destination=origin.offset(piece.piece().offset());
            int end=slice.from();
            for(;end<slice.to();end++) {
                var info=piece.blocks().get(end);
                var size=piece.template().getSize(); var pos=info.pos();
                if(pos.getX()<0 || pos.getX()>=size.getX() || pos.getY()<0 || pos.getY()>=size.getY() || pos.getZ()<0 || pos.getZ()>=size.getZ())
                    throw new IllegalArgumentException("Structure block is outside its piece bounds");
                if(info.state().is(Blocks.STRUCTURE_BLOCK) || info.state().is(Blocks.STRUCTURE_VOID)) continue;
                var chunk=new ChunkPos(destination.offset(info.pos()));
                if(!needed.contains(chunk) && needed.size()==MAX_TICKETS) break;
                needed.add(chunk);
            }
            pending=new PvpPlacementCursor.Slice(slice.part(),slice.from(),end);
        }
        chunks=needed.iterator();
    }
    private void hold(ChunkPos chunk) {
        if(held.contains(chunk)) return;
        if(held.size()>=MAX_TICKETS) {
            var iterator=held.iterator();
            while(iterator.hasNext()) {
                var previous=iterator.next();
                if(!needed.contains(previous)) {
                    level.getChunkSource().removeRegionTicket(TICKET,previous,0,previous); iterator.remove(); break;
                }
            }
        }
        level.getChunkSource().addRegionTicket(TICKET,chunk,0,chunk); held.add(chunk);
    }
    private void place(PvpPlacementCursor.Slice slice) {
        var piece=layout.pieces().get(slice.part());
        var palette=new ListTag(); var blocks=new ListTag(); Map<BlockState,Integer> states=new LinkedHashMap<>();
        for(var info:piece.blocks().subList(slice.from(),slice.to())) {
            if(info.state().is(Blocks.STRUCTURE_BLOCK) || info.state().is(Blocks.STRUCTURE_VOID)) continue;
            int state=states.computeIfAbsent(info.state(),key -> { int id=states.size(); palette.add(NbtUtils.writeBlockState(key)); return id; });
            var value=new CompoundTag(); value.put("pos",integers(info.pos().getX(),info.pos().getY(),info.pos().getZ())); value.putInt("state",state);
            if(info.nbt()!=null) value.put("nbt",info.nbt().copy());
            blocks.add(value);
        }
        if(blocks.isEmpty()) return;
        var data=new CompoundTag(); var size=piece.template().getSize();
        data.put("size",integers(size.getX(),size.getY(),size.getZ())); data.put("palette",palette); data.put("blocks",blocks); data.put("entities",new ListTag());
        var batch=new StructureTemplate(); batch.load(level.holderLookup(Registries.BLOCK),data);
        var destination=origin.offset(piece.piece().offset());
        if(!batch.placeInWorld(level,destination,destination,new StructurePlaceSettings().setIgnoreEntities(true),level.getRandom(),2))
            throw new IllegalStateException("Failed to place PvP batch: "+piece.piece().template());
    }
    private static ListTag integers(int x,int y,int z) {
        var list=new ListTag(); list.add(IntTag.valueOf(x)); list.add(IntTag.valueOf(y)); list.add(IntTag.valueOf(z)); return list;
    }
    @Override public void close() {
        if(closed) return; closed=true;
        for(var chunk:held) level.getChunkSource().removeRegionTicket(TICKET,chunk,0,chunk);
        held.clear();
    }
}
