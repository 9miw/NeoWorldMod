package com.ambition.neoworld.navigation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public final class NavigationRibbonRegressionTest {
    public static void run() {
        var points=List.of(new Vec3(0,64,0),new Vec3(0,64,4),new Vec3(2,64.5,6));
        var ribbon=NavigationRibbonGeometry.edges(points,.1,false);
        check(ribbon.size()==3 && ribbon.get(0).left().distanceTo(ribbon.get(0).right())>.19,"Ribbon has visible width");
        check(ribbon.get(1).distance()==4 && ribbon.get(2).distance()>6,"Pulse distance follows route length, including stairs");
        var duplicates=NavigationRibbonGeometry.edges(List.of(points.getFirst(),points.getFirst(),points.getLast()),.1,false);
        check(duplicates.stream().allMatch(e->Double.isFinite(e.left().x)&&Double.isFinite(e.right().z)),"Duplicate points do not produce NaN geometry");
        var steps=NavigationRibbonGeometry.steps(List.of(new Vec3(0,64,0),new Vec3(0,64.5,.25),new Vec3(0,64,.5)));
        check(steps.equals(List.of(new Vec3(0,64,0),new Vec3(0,64.5,0),new Vec3(0,64.5,.25),new Vec3(0,64.5,.5),new Vec3(0,64,.5))),
                "Stair transitions rise before crossing the tread and descend after crossing it");
        check(NavigationRibbonGeometry.edges(steps,.1,false).stream().allMatch(e->e.left().distanceTo(e.right())>.19),
                "Vertical risers retain nonzero ribbon width");
        var circle=List.of(new Vec3(1,64,0),new Vec3(0,64,1),new Vec3(-1,64,0),new Vec3(0,64,-1),new Vec3(1,64,0));
        var ring=NavigationRibbonGeometry.edges(circle,.05,true);
        check(ring.getFirst().left().equals(ring.getLast().left()),"Destination ring closes without a seam");
        check(Math.abs(NavigationRibbonGeometry.pulse(0,0)-NavigationRibbonGeometry.pulse(2.5,1))<.00001,
                "Light pulses travel toward the destination at 2.5 blocks per second");
        check(NavigationRibbonGeometry.pulse(0,0)>NavigationRibbonGeometry.pulse(2.5,0),"Flow has visible contrast along the line");

        var dim=ResourceLocation.parse("minecraft:overworld");
        var payload=new NavigationTrailView(dim,points,circle);
        reject(()->new NavigationTrailView(dim,java.util.Collections.nCopies(129,Vec3.ZERO),List.of()));
        reject(()->new NavigationTrailView(dim,List.of(new Vec3(Double.NaN,0,0)),List.of()));
        check(payload.path().equals(points)&&payload.ring().equals(circle),"Local geometry preserves ribbon and destination points without network serialization");
        System.out.println("Navigation light ribbon regression checks passed");
    }
    private static void check(boolean value,String text) { if(!value) throw new AssertionError(text); }
    private static void reject(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new AssertionError("Invalid ribbon data accepted");
    }
}
