package com.ambition.neoworld.client.gui.ranking;

import java.util.function.Supplier;
import com.ambition.neoworld.client.state.ClientFashionData;
import com.ambition.neoworld.fashion.data.FashionSlot;
import com.ambition.neoworld.ranking.RankingAppearance;
import com.ambition.neoworld.ranking.RankingTable.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.PlayerModelPart;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Detached display copy with recorded skin, equipment and a scoped fashion render context. */
final class RankingPlayerPreview extends RemotePlayer {
    private final RankingAppearance appearance;
    private final Supplier<PlayerSkin> skin;
    RankingPlayerPreview(ClientLevel level,Entry entry,RankingAppearance appearance) {
        super(level,appearance.profile(entry.id(),entry.name()));
        this.appearance=appearance;
        skin=Minecraft.getInstance().getSkinManager().lookupInsecure(getGameProfile());
        for(int i=0;i<RankingAppearance.EQUIPMENT.length;i++) setItemSlot(RankingAppearance.EQUIPMENT[i],appearance.equipment().get(i).copy());
    }
    @Override public PlayerSkin getSkin() { return skin==null ? super.getSkin() : skin.get(); }
    @Override public boolean isModelPartShown(PlayerModelPart part) { return true; }

    void renderPreview(GuiGraphics g,RankingLayout.Region region,int age) {
        tickCount=age;
        yBodyRot=yBodyRotO=yHeadRot=yHeadRotO=0;
        setYRot(0); yRotO=0; setXRot(0); xRotO=0;
        boolean wide=appearance.fashion().containsKey(FashionSlot.BACK);
        float size=region.fit(wide ? 3.2F : 1.5F,2.15F);
        var clip=region.screenBounds(g.pose().last().pose());
        g.enableScissor(clip.x(),clip.y(),clip.x()+clip.width(),clip.y()+clip.height());
        try {
            ClientFashionData.withPreview(getId(),appearance.fashion(),() ->
                    InventoryScreen.renderEntityInInventory(g,region.x()+region.width()/2F,region.y()+region.height()/2F,
                            size/getScale(),new Vector3f(0,getBbHeight()/2F+0.0625F*getScale(),0),
                            new Quaternionf().rotateZ((float)Math.PI).rotateY(0.22F),new Quaternionf(),this));
        } finally { g.disableScissor(); }
    }
}
