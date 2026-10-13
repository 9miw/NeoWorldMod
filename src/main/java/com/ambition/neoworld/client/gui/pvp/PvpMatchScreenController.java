package com.ambition.neoworld.client.gui.pvp;

import com.ambition.neoworld.network.pvp.PvpMatchOfferPayload;
import net.minecraft.client.Minecraft;
public final class PvpMatchScreenController {
    private static PvpMatchOfferPayload current;
    private PvpMatchScreenController() { }
    public static boolean openCurrent() {
        if(current==null || !current.active()) return false;
        Minecraft.getInstance().setScreen(new PvpMatchConfirmScreen(current)); return true;
    }
    public static void receive(PvpMatchOfferPayload payload) {
        var mc=Minecraft.getInstance();
        if(!payload.active()) {
            if(current!=null && current.offer().equals(payload.offer())) current=null;
            if(mc.screen instanceof PvpMatchConfirmScreen screen && screen.offerId().equals(payload.offer())) mc.setScreen(null);
            return;
        }
        boolean newOffer=current==null || !current.offer().equals(payload.offer()); current=payload;
        if(mc.player==null || mc.level==null) return;
        if(mc.screen instanceof PvpMatchConfirmScreen screen && screen.offerId().equals(payload.offer())) screen.update(payload);
        else if(newOffer) mc.setScreen(new PvpMatchConfirmScreen(payload));
    }
    public static void reset() { current=null; }
}
