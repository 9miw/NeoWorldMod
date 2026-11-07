import java.nio.file.Path;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import com.ambition.neoworld.world.WorldImportFiles;
import com.ambition.neoworld.world.WorldImportPalette;

/** Headless validation with Minecraft's native NBT and Anvil reader; no client/server is launched. */
public class VerifyRuinedCastle {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Path world = Path.of(args[0]).toAbsolutePath().normalize();
        CompoundTag level = NbtIo.readCompressed(world.resolve("level.dat"), NbtAccounter.create(64L*1024*1024)).getCompound("Data");
        check(level.getInt("DataVersion") == SharedConstants.getCurrentVersion().getDataVersion().getVersion(), "wrong target version");
        boolean composition=Files.exists(world.resolve("composition.json")) || Files.exists(world.resolve("zones.json"));
        int size=composition?(int)level.getDouble("BorderSize"):150, span=(size+15)/16;
        check(size>0&&size<=512,"unsupported footprint");
        int spawnX=level.getInt("SpawnX"),spawnY=level.getInt("SpawnY"),spawnZ=level.getInt("SpawnZ");
        check(spawnX>=0&&spawnX<size&&spawnZ>=0&&spawnZ<size&&spawnY>=-63&&spawnY<319,"spawn outside map");
        if(!composition)check(spawnX==75&&spawnY==65&&spawnZ==127,"wrong spawn");
        CompoundTag stem=level.getCompound("WorldGenSettings").getCompound("dimensions").getCompound("minecraft:overworld");
        check(stem.getString("type").equals("minecraft:overworld")&&stem.getCompound("generator").getString("type").equals("minecraft:flat"),"wrong dimension generator");
        CompoundTag settings=stem.getCompound("generator").getCompound("settings");
        check(settings.getString("biome").equals("minecraft:the_void")&&settings.contains("layers",Tag.TAG_LIST)&&settings.getList("layers",Tag.TAG_COMPOUND).isEmpty(),"wrong void generation settings");
        Map<String,CompoundTag> paletteCache = new HashMap<>();
        long blocks=0, chunks=0, tiles=0;
        try (RegionFile region = new RegionFile(new RegionStorageInfo("verify", ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("verify","castle")), "chunk"),
                world.resolve("region/r.0.0.mca"), world.resolve("region"), false)) {
            for(int cz=0;cz<32;cz++)for(int cx=0;cx<32;cx++) {
                ChunkPos pos=new ChunkPos(cx,cz);
                if(cx>=span||cz>=span) { check(!region.hasChunk(pos), "unexpected extra chunk"); continue; }
                check(region.hasChunk(pos),"missing chunk "+pos);
                CompoundTag chunk;
                try(var stream=region.getChunkDataInputStream(pos)){check(stream!=null,"missing stream");chunk=NbtIo.read(stream,NbtAccounter.create(64L*1024*1024));}
                check(chunk.getInt("DataVersion")==3955 && chunk.getInt("xPos")==cx && chunk.getInt("zPos")==cz,"chunk metadata");
                check(chunk.getString("Status").equals("minecraft:full"),"chunk status");
                ListTag sections=chunk.getList("sections",Tag.TAG_COMPOUND);
                check(sections.size()==24,"section coverage");
                Map<Integer,CompoundTag[]> decoded=new HashMap<>();
                for(int s=0;s<sections.size();s++) {
                    CompoundTag section=sections.getCompound(s), states=section.getCompound("block_states");
                    ListTag palette=states.getList("palette",Tag.TAG_COMPOUND);
                    CompoundTag[] entries=new CompoundTag[palette.size()];
                    for(int p=0;p<palette.size();p++) {
                        CompoundTag entry=palette.getCompound(p);String key=entry.toString();
                        if(!paletteCache.containsKey(key)) {
                            ResourceLocation id=ResourceLocation.parse(entry.getString("Name"));
                            check(id.getNamespace().equals("minecraft"),"non-vanilla block "+id);
                            CompoundTag props=entry.getCompound("Properties");
                            for(String name:props.getAllKeys())check(props.contains(name,Tag.TAG_STRING),"non-string block property");
                            paletteCache.put(key,entry);
                        }
                        entries[p]=paletteCache.get(key);
                    }
                    CompoundTag[] cells=new CompoundTag[4096];long[] data=states.getLongArray("data");
                    for(int i=0;i<4096;i++) { cells[i]=entries[WorldImportPalette.index(entries.length,data,4096,4,i)];if(!cells[i].getString("Name").equals("minecraft:air")){blocks++;check(cx*16+(i&15)<size&&cz*16+((i>>4)&15)<size,"blocks outside footprint");} }
                    decoded.put((int)section.getByte("Y"),cells);
                }
                ListTag entities=chunk.getList("block_entities",Tag.TAG_COMPOUND);
                for(int t=0;t<entities.size();t++) {
                    CompoundTag entity=entities.getCompound(t);int x=entity.getInt("x"),y=entity.getInt("y"),z=entity.getInt("z");
                    check((x>>4)==cx&&(z>>4)==cz,"block entity in wrong chunk");
                    String tileId=entity.getString("id"),blockId=decoded.get(Math.floorDiv(y,16))[((y&15)<<8)|((z&15)<<4)|(x&15)].getString("Name");
                    boolean validSign=tileId.equals("minecraft:sign")&&blockId.startsWith("minecraft:")&&(blockId.endsWith("_wall_sign")||blockId.endsWith("_sign"))&&!blockId.endsWith("_hanging_sign");
                    check(tileId.equals(blockId)||validSign,"block entity mismatches block: "+tileId+" / "+blockId);
                    check(entity.getList("Items",Tag.TAG_COMPOUND).isEmpty(),"unexpected rewards");tiles++;
                }
                if(cx==(spawnX>>4)&&cz==(spawnZ>>4)) {
                    for(int y=spawnY;y<=spawnY+1;y++)check(decoded.get(Math.floorDiv(y,16))[((y&15)<<8)|((spawnZ&15)<<4)|(spawnX&15)].getString("Name").equals("minecraft:air"),"spawn obstructed");
                    int floor=spawnY-1;
                    check(!decoded.get(Math.floorDiv(floor,16))[((floor&15)<<8)|((spawnZ&15)<<4)|(spawnX&15)].getString("Name").equals("minecraft:air"),"spawn has no floor");
                }
                chunks++;
            }
        }
        check(WorldImportFiles.scan(world).chunks()==span*span,"importer header check");
        check(chunks==span*span&&(composition||tiles==37),"chunk/container totals");
        if(Files.isDirectory(world.resolve("structures"))) {
            int structures=0;
            try(var files=Files.list(world.resolve("structures"))) {
                for(Path file:files.filter(p->p.toString().endsWith(".nbt")).toList()) {
                    CompoundTag structure=NbtIo.readCompressed(file,NbtAccounter.create(64L*1024*1024));
                    ListTag dimensions=structure.getList("size",Tag.TAG_INT),palette=structure.getList("palette",Tag.TAG_COMPOUND),entries=structure.getList("blocks",Tag.TAG_COMPOUND);
                    check(dimensions.size()==3&&!palette.isEmpty()&&!entries.isEmpty(),"invalid structure "+file);
                    check(structure.getInt("DataVersion")==3955,"wrong structure version");
                    for(int axis=0;axis<3;axis++)check(dimensions.getInt(axis)>0&&dimensions.getInt(axis)<=48,"structure too large");
                    for(int b=0;b<entries.size();b++) {
                        CompoundTag entry=entries.getCompound(b);ListTag position=entry.getList("pos",Tag.TAG_INT);
                        check(position.size()==3&&entry.getInt("state")>=0&&entry.getInt("state")<palette.size(),"invalid structure block");
                        for(int axis=0;axis<3;axis++)check(position.getInt(axis)>=0&&position.getInt(axis)<dimensions.getInt(axis),"structure block outside bounds");
                    }
                    structures++;
                }
            }
            System.out.println("STRUCTURES VERIFIED: "+structures+" compressed Minecraft structure files.");
        }
        System.out.println("MAP VERIFIED: native Minecraft 1.21.1 NBT/Anvil reader, void-generator fields, "+chunks+" chunks, "+blocks+" blocks, "+paletteCache.size()+" named block states, "+tiles+" empty containers, clear spawn, "+size+"x"+size+" footprint. In-game rendering is not tested.");
    }
}
