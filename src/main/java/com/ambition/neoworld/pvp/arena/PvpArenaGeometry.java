package com.ambition.neoworld.pvp.arena;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Pure layout checks, shared by capture validation and arena placement. */
public final class PvpArenaGeometry {
    public record Part(BlockPos offset,net.minecraft.core.Vec3i size) { }
    private PvpArenaGeometry() { }
    public static BlockPos size(List<Part> parts) {
        if(parts.isEmpty() || parts.size()>512) throw new IllegalArgumentException("Arena needs 1..512 structure pieces");
        long x=0,y=0,z=0;
        for(var part:parts) {
            var o=part.offset(); var s=part.size();
            if(o.getX()<0 || o.getY()<0 || o.getZ()<0) throw new IllegalArgumentException("Negative structure offset");
            PvpCapturePlan.checkSize(s.getX(),s.getY(),s.getZ());
            x=Math.max(x,(long)o.getX()+s.getX()); y=Math.max(y,(long)o.getY()+s.getY()); z=Math.max(z,(long)o.getZ()+s.getZ());
            PvpCapturePlan.checkSize(x,y,z);
        }
        for(int i=0;i<parts.size();i++) for(int j=0;j<i;j++) {
            var a=parts.get(i); var b=parts.get(j);
            if(a.offset().getX()<(long)b.offset().getX()+b.size().getX() && b.offset().getX()<(long)a.offset().getX()+a.size().getX()
                    && a.offset().getY()<(long)b.offset().getY()+b.size().getY() && b.offset().getY()<(long)a.offset().getY()+a.size().getY()
                    && a.offset().getZ()<(long)b.offset().getZ()+b.size().getZ() && b.offset().getZ()<(long)a.offset().getZ()+a.size().getZ())
                throw new IllegalArgumentException("PvP structure pieces overlap");
        }
        return new BlockPos((int)x,(int)y,(int)z);
    }
    public static void markers(BlockPos size,Map<String,BlockPos> markers) {
        var bounds=new PvpArenaBounds(size.getX(),size.getY(),size.getZ());
        for(String name:List.of("pvp_spawn_a","pvp_spawn_b")) {
            var spawn=markers.get(name);
            if(spawn==null) throw new IllegalArgumentException("Missing DATA marker: "+name);
            if(!bounds.spawnFits(spawn.getX(),spawn.getY(),spawn.getZ())) throw new IllegalArgumentException("Spawn needs floor and head room inside the arena: "+name);
        }
        if(markers.get("pvp_spawn_a").equals(markers.get("pvp_spawn_b"))) throw new IllegalArgumentException("Spawn points must be different");
    }
}
