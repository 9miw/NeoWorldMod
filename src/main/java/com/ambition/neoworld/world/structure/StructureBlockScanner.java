package com.ambition.neoworld.world.structure;

import java.util.*;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.StructureBlockEntity;

/** Reads numbered template controllers without interpreting feature DATA markers. */
public final class StructureBlockScanner {
    private StructureBlockScanner() {}
    public static List<StructurePiece> scan(ServerLevel level,BlockPos first,int radius) {
        if(radius<1 || radius>256)throw new IllegalArgumentException("Scan radius must be 1..256");
        if(!(level.getBlockEntity(first) instanceof StructureBlockEntity controller))throw new IllegalArgumentException("เล็ง Structure Block _01 ก่อน");
        var id=ResourceLocation.tryParse(controller.getStructureName().trim());
        var match=Pattern.compile("^(.*_)(\\d+)$").matcher(id==null?"":id.getPath());
        if(id==null || !match.matches() || !match.group(2).equals("01"))throw new IllegalArgumentException("Structure Block ตัวแรกต้องลงท้าย _01");
        var pattern=Pattern.compile("^"+Pattern.quote(match.group(1))+"(\\d+)$");
        Map<Integer,StructurePiece> numbered=new TreeMap<>();
        for(int x=(first.getX()-radius)>>4;x<=(first.getX()+radius)>>4;x++)
            for(int z=(first.getZ()-radius)>>4;z<=(first.getZ()+radius)>>4;z++)
                for(var entity:level.getChunk(x,z).getBlockEntities().values()) {
                    if(!(entity instanceof StructureBlockEntity block))continue;
                    var pos=block.getBlockPos();
                    if(Math.abs((long)pos.getX()-first.getX())>radius || Math.abs((long)pos.getZ()-first.getZ())>radius)continue;
                    var template=ResourceLocation.tryParse(block.getStructureName().trim());
                    if(template==null || !template.getNamespace().equals(id.getNamespace()))continue;
                    var suffix=pattern.matcher(template.getPath());if(!suffix.matches())continue;
                    int index=Integer.parseInt(suffix.group(1));
                    if(index<1)continue;
                    if(index>512)throw new IllegalArgumentException("Structure index exceeds 512: "+template);
                    if(level.getStructureManager().get(template).isEmpty())throw new IllegalArgumentException("Missing structure template: "+template);
                    if(numbered.putIfAbsent(index,new StructurePiece(template,pos.subtract(first)))!=null)
                        throw new IllegalArgumentException("Duplicate Structure index: "+index);
                }
        if(numbered.isEmpty() || !numbered.containsKey(1))throw new IllegalArgumentException("ไม่พบ Structure _01");
        int last=numbered.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        for(int i=1;i<=last;i++)if(!numbered.containsKey(i))throw new IllegalArgumentException("Missing Structure index: "+i);
        return List.copyOf(numbered.values());
    }
}
