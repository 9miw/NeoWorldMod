package com.ambition.neoworld.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/** Conservative 0.6-wide, 1.8-tall walking corridor. Does not request chunk loads. */
public final class NavigationWalk {
    private NavigationWalk() {}
    public static boolean loaded(Level level,NavigationGraph.Node a,NavigationGraph.Node b) {
        // ClientLevel.hasChunk() is always true; query ChunkSource so unloaded chunks stay unknown.
        return loaded(a,b,(x,z)->level.getChunkSource().hasChunk(x,z));
    }
    public static boolean loaded(NavigationGraph.Node a,NavigationGraph.Node b,java.util.function.BiPredicate<Integer,Integer> chunkLoaded) {
        int steps=Math.max(1,(int)Math.ceil(a.distance(b)*4));
        if(steps>256) return false;
        for(int i=0;i<=steps;i++) {
            double t=(double)i/steps;
            double x=a.x()+(b.x()-a.x())*t,z=a.z()+(b.z()-a.z())*t;
            for(double dx:new double[]{-.31,.31}) for(double dz:new double[]{-.31,.31})
                if(!chunkLoaded.test((int)Math.floor(x+dx)>>4,(int)Math.floor(z+dz)>>4)) return false;
        }
        return true;
    }
    public static boolean between(Level level,NavigationGraph.Node a,NavigationGraph.Node b) {
        if(!a.dimension().equals(b.dimension())||!loaded(level,a,b)) return false;
        int steps=Math.max(1,(int)Math.ceil(a.distance(b)*4)); Vec3 previous=null;
        for(int i=0;i<=steps;i++) {
            double t=(double)i/steps;
            Vec3 point=surface(level,a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);
            if(point==null || (previous!=null && Math.abs(point.y-previous.y)>.61)) return false;
            previous=point;
        }
        return true;
    }
    public static Vec3 surface(Level level,double x,double y,double z) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)) return null;
        var collisions=new java.util.ArrayList<AABB>();
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(x-.3,y-2,z-.3),BlockPos.containing(x+.3,y+3,z+.3))) {
            if(!chunkLoaded(level,pos)) return null;
            var shape=level.getBlockState(pos).getCollisionShape(level,pos);
            for(var box:shape.toAabbs()) collisions.add(box.move(pos));
        }
        var feet=NavigationFooting.height(x,y,z,collisions);
        if(feet.isEmpty()||!clearOfHazards(level,x,feet.getAsDouble(),z)) return null;
        return new Vec3(x,feet.getAsDouble(),z);
    }
    private static boolean clearOfHazards(Level level,double x,double feet,double z) {
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(x-.3,feet-.05,z-.3),BlockPos.containing(x+.3,feet+1.8,z+.3))) {
            if(!chunkLoaded(level,pos)) return false;
            var state=level.getBlockState(pos);
            if(!state.getFluidState().isEmpty()||state.is(BlockTags.FIRE)||state.is(BlockTags.CAMPFIRES)
                    ||state.is(Blocks.MAGMA_BLOCK)||state.is(Blocks.SWEET_BERRY_BUSH)||state.is(Blocks.WITHER_ROSE)
                    ||state.is(Blocks.POWDER_SNOW)||state.is(Blocks.CACTUS)) return false;
        }
        return true;
    }
    public static NavigationGraph.Node point(String id,String dimension,Vec3 point) {
        return new NavigationGraph.Node(id,dimension,point.x,point.y,point.z);
    }
    private static boolean chunkLoaded(Level level,BlockPos pos) {
        return level.getChunkSource().hasChunk(pos.getX()>>4,pos.getZ()>>4);
    }
}
