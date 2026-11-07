package com.ambition.neoworld.client.gui.pvp;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.ambition.neoworld.client.gui.widget.NeoScrollbar;
import com.ambition.neoworld.client.gui.common.NeoGuiIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/** Clipped arena cards; rendering and pointer input share the same scroll offset. */
final class PvpArenaList extends AbstractWidget {
    private static final ResourceLocation BUTTONS=ResourceLocation.fromNamespaceAndPath("neoworld","textures/gui/common/buttons.png");
    private final List<String> arenas;
    private final List<NeoButton> cards;
    private final NeoScrollbar scrollbar;
    private final Supplier<String> selected;
    private final Consumer<String> select;
    private final PvpArenaImages images;
    private final int cardHeight,gap;

    PvpArenaList(int x,int y,int width,int height,int cardHeight,int gap,List<String> arenas,
            NeoScrollbar scrollbar,Supplier<String> selected,Consumer<String> select,PvpArenaImages images) {
        super(x,y,width,height,Component.literal("เลือกสนาม"));
        this.arenas=List.copyOf(arenas); this.scrollbar=scrollbar; this.selected=selected; this.select=select;
        this.images=images; this.cardHeight=cardHeight; this.gap=gap;
        cards=arenas.stream().map(id -> new NeoButton(Button.builder(Component.empty(),ignored -> select.accept(id))
                .bounds(x,y,width,cardHeight)).style(NeoButton.Style.STANDARD).frameRenderer(PvpArenaList::cardFrame)).toList();
        scrollbar.range(height,Math.max(0,arenas.size()*(cardHeight+gap)-gap)).scrollStep((cardHeight+gap)/2D);
    }
    private int rowY(int index) { return getY()+index*(cardHeight+gap)-(int)Math.round(scrollbar.getValue()); }
    @Override protected void renderWidget(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        var font=Minecraft.getInstance().font;
        graphics.enableScissor(getX(),getY(),getX()+width,getY()+height);
        try {
            for(int i=0;i<cards.size();i++) {
                int y=rowY(i); if(y+cardHeight<=getY() || y>=getY()+height) continue;
                var card=cards.get(i); card.setY(y); card.active=active;
                boolean chosen=arenas.get(i).equals(selected.get()); card.selected(chosen).style(chosen ? NeoButton.Style.PRIMARY : NeoButton.Style.STANDARD);
                card.render(graphics,isMouseOver(mouseX,mouseY) ? mouseX : -1,isMouseOver(mouseX,mouseY) ? mouseY : -1,partialTick);
                int inset=cardCorner(width)+1,labelHeight=Math.max(font.lineHeight+4,cardHeight/4);
                PvpArenaImages.draw(graphics,images.get(arenas.get(i)),getX()+inset,y+inset,width-inset*2,Math.max(1,cardHeight-labelHeight-inset));
                String name=font.plainSubstrByWidth(arenas.get(i),Math.max(1,width-inset*4));
                graphics.drawString(font,name,getX()+inset*2,y+cardHeight-labelHeight+(labelHeight-font.lineHeight)/2,active ? 0xFFE7ECEF : 0xFF737B80,false);
                if(chosen) graphics.drawString(font,NeoGuiIcons.icon(NeoGuiIcons.READY),getX()+width-inset-16,y+inset,0xFF63D9CF,false);
            }
            if(arenas.isEmpty()) graphics.drawCenteredString(font,Component.literal("ยังไม่มีสนาม"),getX()+width/2,getY()+height/2,0xFFABB4BA);
        } finally { graphics.disableScissor(); }
    }
    private static int cardCorner(int width) { return Math.max(2,width/110); }
    private static void cardFrame(GuiGraphics graphics,NeoButton button) {
        // Use the shared PNG states with slim corners suitable for tall thumbnail cards.
        int state=!button.active ? 3 : button.isSelected() ? 2 : button.isHoveredOrFocused() ? 1 : 0;
        int[] sourceX={28,407,784,1162},sourceWidth={350,347,350,348};
        int corner=Math.min(cardCorner(button.getWidth()),Math.min(button.getWidth(),button.getHeight())/2);
        int[] widths={corner,button.getWidth()-corner*2,corner},heights={corner,button.getHeight()-corner*2,corner};
        int[] sw={44,sourceWidth[state]-88,44},sh={44,56,44};
        int y=button.getY(),v=button.getStyle()==NeoButton.Style.PRIMARY ? 441 : 168;
        for(int row=0;row<3;row++) {
            int x=button.getX(),u=sourceX[state];
            for(int col=0;col<3;col++) {
                if(widths[col]>0 && heights[row]>0) graphics.blit(BUTTONS,x,y,widths[col],heights[row],u,v,sw[col],sh[row],1536,1024);
                x+=widths[col]; u+=sw[col];
            }
            y+=heights[row]; v+=sh[row];
        }
    }
    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if(button!=0 || !active || !visible) return false;
        int index=indexAt(mouseX,mouseY); if(index<0) return false;
        var card=cards.get(index); card.setY(rowY(index)); card.active=active;
        return card.mouseClicked(mouseX,mouseY,button);
    }
    int indexAt(double mouseX,double mouseY) {
        if(!Double.isFinite(mouseX) || !Double.isFinite(mouseY) || !isMouseOver(mouseX,mouseY)) return -1;
        int index=(int)((mouseY-getY()+Math.round(scrollbar.getValue()))/(cardHeight+gap));
        return index<0 || index>=arenas.size() || mouseY>=rowY(index)+cardHeight ? -1 : index;
    }
    @Override public boolean mouseScrolled(double mouseX,double mouseY,double scrollX,double scrollY) {
        return active && isMouseOver(mouseX,mouseY) && scrollbar.scroll(scrollY);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        if(!active || !isFocused() || arenas.isEmpty()) return false;
        int index=Math.max(0,arenas.indexOf(selected.get()));
        int next=switch(key) {
            case GLFW.GLFW_KEY_UP -> index-1;
            case GLFW.GLFW_KEY_DOWN -> index+1;
            case GLFW.GLFW_KEY_HOME -> 0;
            case GLFW.GLFW_KEY_END -> arenas.size()-1;
            case GLFW.GLFW_KEY_PAGE_UP -> index-3;
            case GLFW.GLFW_KEY_PAGE_DOWN -> index+3;
            default -> -1;
        };
        if(next<0 && key!=GLFW.GLFW_KEY_UP && key!=GLFW.GLFW_KEY_PAGE_UP) return false;
        next=Math.clamp(next,0,arenas.size()-1); select.accept(arenas.get(next));
        int row=next*(cardHeight+gap);
        if(row<scrollbar.getValue()) scrollbar.value(row);
        else if(row+cardHeight>scrollbar.getValue()+height) scrollbar.value(row+cardHeight-height);
        return true;
    }
    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE,Component.literal("เลือกสนาม "+selected.get()));
        output.add(NarratedElementType.USAGE,Component.literal("ใช้ปุ่มขึ้นลงเพื่อเลือกสนาม"));
    }
}
