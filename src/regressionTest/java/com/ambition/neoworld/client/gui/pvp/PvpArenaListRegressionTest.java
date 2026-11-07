package com.ambition.neoworld.client.gui.pvp;

import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class PvpArenaListRegressionTest {
    public static void run() {
        var arenas=IntStream.rangeClosed(1,128).mapToObj(i -> "arena_"+i).toList();
        var selected=new AtomicReference<>(arenas.getFirst());
        var scrollbar=new NeoScrollbar(110,20,6,60,Component.literal("สนาม"));
        var list=new PvpArenaList(10,20,90,60,18,2,arenas,scrollbar,selected::get,selected::set,new PvpArenaImages());
        check(list.indexAt(20,20)==0 && list.indexAt(20,39)==-1 && list.indexAt(20,40)==1,"Card gaps cannot select adjacent arenas");
        check(list.indexAt(20,19)==-1 && list.indexAt(20,80)==-1 && list.indexAt(101,30)==-1,"Hidden content and outside clicks are rejected");
        check(list.indexAt(Double.NaN,25)==-1,"Non-finite input is rejected");
        check(!list.mouseScrolled(0,0,0,-1) && scrollbar.getValue()==0,"Wheel outside list does not scroll it");
        check(list.mouseScrolled(20,30,0,-1) && scrollbar.getValue()==10,"Wheel over list scrolls content");
        check(list.indexAt(20,20)==0 && list.indexAt(20,29)==-1 && list.indexAt(20,30)==1,"Hitboxes follow partially scrolled cards");
        list.setFocused(true);
        check(list.keyPressed(GLFW.GLFW_KEY_END,0,0) && selected.get().equals("arena_128"),"End selects the last registered map");
        check(scrollbar.getValue()==scrollbar.getMaxValue() && list.indexAt(20,79)==127,"Last map is reachable at the scroll limit");
        scrollbar.value(Double.MAX_VALUE); check(scrollbar.getValue()==scrollbar.getMaxValue(),"Drag beyond bottom clamps to content");
        scrollbar.value(0); scrollbar.onClick(112,scrollbar.getThumbY()+1);
        scrollbar.mouseDragged(112,1000,0,0,1000);
        check(scrollbar.getValue()==scrollbar.getMaxValue(),"Dragging the shared thumb reaches the last arena");
        scrollbar.onRelease(112,1000);
        list.keyPressed(GLFW.GLFW_KEY_HOME,0,0); check(scrollbar.getValue()==0 && selected.get().equals("arena_1"),"Home returns to the first map");
        list.active=false; check(!list.mouseScrolled(20,30,0,-1) && !list.keyPressed(GLFW.GLFW_KEY_END,0,0),"Pending requests disable list interaction");
        var shortBar=new NeoScrollbar(110,20,6,60,Component.literal("สนาม"));
        var shortList=new PvpArenaList(10,20,90,60,18,2,arenas.subList(0,3),shortBar,selected::get,selected::set,new PvpArenaImages());
        check(!shortBar.isScrollable() && !shortList.mouseScrolled(20,30,0,-1),"Three fitting maps need no scrolling");
        System.out.println("PvP arena list scrolling and clipped-input regression checks passed");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
