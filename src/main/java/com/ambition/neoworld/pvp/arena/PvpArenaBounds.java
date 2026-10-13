package com.ambition.neoworld.pvp.arena;

/** Half-open local bounds inferred from the saved template size. */
public record PvpArenaBounds(int width,int height,int depth) {
    public PvpArenaBounds { if(width<=0 || height<=0 || depth<=0) throw new IllegalArgumentException("Arena is empty"); }
    public boolean contains(double x,double y,double z) { return x>=0 && x<width && y>=0 && y<height && z>=0 && z<depth; }
    public boolean spawnFits(int x,int y,int z) { return contains(x,y,z) && y>=1 && y+1<height; }
}
