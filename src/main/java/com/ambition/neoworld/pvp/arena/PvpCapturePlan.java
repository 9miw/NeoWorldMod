package com.ambition.neoworld.pvp.arena;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Inclusive PvP selection split into standard structures, independent of dungeons. */
public record PvpCapturePlan(BlockPos minimum,BlockPos maximum,long volume,List<Part> parts) {
    public static final int EDGE=48,MAX_PARTS=512;
    public static final long MAX_VOLUME=8_388_608L;
    public record Part(ResourceLocation id,BlockPos origin,BlockPos size,BlockPos offset) { }
    public PvpCapturePlan { parts=List.copyOf(parts); }
    public static void checkSize(long x,long y,long z) {
        if(x<=0 || y<=0 || z<=0 || x>512 || y>224 || z>512 || x*y*z>MAX_VOLUME)
            throw new IllegalArgumentException("PvP arena limit: 512 x 224 x 512, at most 8388608 blocks");
    }
    public static PvpCapturePlan create(BlockPos first,BlockPos second,ResourceLocation prefix) {
        if(prefix.getPath().contains("..") || prefix.getPath().contains("//") || prefix.getPath().endsWith(".nbt") || prefix.toString().length()>230)
            throw new IllegalArgumentException("Invalid structure prefix");
        var min=new BlockPos(Math.min(first.getX(),second.getX()),Math.min(first.getY(),second.getY()),Math.min(first.getZ(),second.getZ()));
        var max=new BlockPos(Math.max(first.getX(),second.getX()),Math.max(first.getY(),second.getY()),Math.max(first.getZ(),second.getZ()));
        long sx=(long)max.getX()-min.getX()+1,sy=(long)max.getY()-min.getY()+1,sz=(long)max.getZ()-min.getZ()+1;
        checkSize(sx,sy,sz);
        var parts=new ArrayList<Part>();
        for(int x=0;x<sx;x+=EDGE) for(int y=0;y<sy;y+=EDGE) for(int z=0;z<sz;z+=EDGE) {
            var offset=new BlockPos(x,y,z);
            var id=ResourceLocation.fromNamespaceAndPath(prefix.getNamespace(),prefix.getPath()+String.format(java.util.Locale.ROOT,"_%02d",parts.size()+1));
            parts.add(new Part(id,min.offset(offset),new BlockPos((int)Math.min(EDGE,sx-x),(int)Math.min(EDGE,sy-y),(int)Math.min(EDGE,sz-z)),offset));
        }
        if(parts.size()>MAX_PARTS) throw new IllegalArgumentException("Too many PvP pieces");
        return new PvpCapturePlan(min,max,sx*sy*sz,parts);
    }
}
