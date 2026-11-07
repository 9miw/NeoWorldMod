package com.ambition.neoworld.client.gui.pvp;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Rotates preview copies only; never accepts a live local player. */
final class PvpPlayerPreview {
    private PvpPlayerPreview() { }

    static void render(GuiGraphics graphics,RemotePlayer player,int left,int top,int right,int bottom,int size,float ticks) {
        float angle=(float)Math.toRadians(180F+(ticks%400F)*0.9F); // One revolution per 20 seconds.
        // Keep the preview's head/body aligned. Rotate its entire render pose, including modded armor and held items.
        player.yBodyRot=player.yBodyRotO=0;
        player.yHeadRot=player.yHeadRotO=0;
        player.setYRot(0); player.yRotO=0;
        player.setXRot(0); player.xRotO=0;
        float entityScale=player.getScale();
        graphics.enableScissor(left,top,right,bottom);
        try {
            InventoryScreen.renderEntityInInventory(graphics,(left+right)/2F,(top+bottom)/2F,size/entityScale,
                    new Vector3f(0,player.getBbHeight()/2F+0.0625F*entityScale,0),
                    new Quaternionf().rotateZ((float)Math.PI).rotateY(angle),new Quaternionf(),player);
        } finally {
            graphics.disableScissor();
        }
    }
}
