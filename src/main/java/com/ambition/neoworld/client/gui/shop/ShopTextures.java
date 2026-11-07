package com.ambition.neoworld.client.gui.shop;

import com.ambition.neoworld.client.gui.widget.NeoButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Separated Medieval Shop sprites. Source geometry is measured in the asset manifest. */
final class ShopTextures {
    private record Sprite(ResourceLocation texture, int textureWidth, int textureHeight,
                          int x, int y, int width, int height) {
        Sprite(String path, int tw, int th, int x, int y, int width, int height) {
            this(ResourceLocation.fromNamespaceAndPath("neoworld",
                    "textures/gui/shop/medieval/" + path + ".png"), tw, th, x, y, width, height);
        }
    }
    private static final Sprite FRAME = new Sprite("frame", 748, 281, 2, 2, 744, 277);
    private static final Sprite PANELS_TITLE = new Sprite("panels/title", 592, 262, 2, 2, 588, 258);
    private static final Sprite PANELS_PRODUCTS = new Sprite("panels/products", 902, 325, 2, 2, 898, 321);
    private static final Sprite PANELS_DETAILS = new Sprite("panels/details", 405, 461, 2, 2, 401, 457);
    private static final Sprite PANELS_INVENTORY = new Sprite("panels/inventory", 903, 261, 2, 2, 899, 257);
    private static final Sprite PANELS_SECTION = new Sprite("panels/section", 393, 107, 2, 2, 389, 103);
    private static final Sprite TABS_NORMAL = new Sprite("tabs/normal", 281, 84, 2, 2, 277, 80);
    private static final Sprite TABS_HOVER = new Sprite("tabs/hover", 281, 84, 2, 2, 277, 80);
    private static final Sprite TABS_PRESSED = new Sprite("tabs/pressed", 281, 84, 2, 2, 277, 80);
    private static final Sprite TABS_SELECTED = new Sprite("tabs/selected", 281, 84, 2, 2, 277, 80);
    private static final Sprite TABS_DISABLED = new Sprite("tabs/disabled", 281, 84, 2, 2, 277, 80);
    private static final Sprite BUTTONS_BUY_NORMAL = new Sprite("buttons/buy/normal", 280, 78, 2, 2, 276, 74);
    private static final Sprite BUTTONS_BUY_HOVER = new Sprite("buttons/buy/hover", 280, 78, 2, 2, 276, 74);
    private static final Sprite BUTTONS_BUY_PRESSED = new Sprite("buttons/buy/pressed", 280, 78, 2, 2, 276, 74);
    private static final Sprite BUTTONS_BUY_SELECTED = new Sprite("buttons/buy/selected", 280, 78, 2, 2, 276, 74);
    private static final Sprite BUTTONS_BUY_DISABLED = new Sprite("buttons/buy/disabled", 280, 78, 2, 2, 276, 74);
    private static final Sprite BUTTONS_SELL_NORMAL = new Sprite("buttons/sell/normal", 281, 80, 2, 2, 277, 76);
    private static final Sprite BUTTONS_SELL_HOVER = new Sprite("buttons/sell/hover", 281, 80, 2, 2, 277, 76);
    private static final Sprite BUTTONS_SELL_PRESSED = new Sprite("buttons/sell/pressed", 281, 80, 2, 2, 277, 76);
    private static final Sprite BUTTONS_SELL_SELECTED = new Sprite("buttons/sell/selected", 281, 80, 2, 2, 277, 76);
    private static final Sprite BUTTONS_SELL_DISABLED = new Sprite("buttons/sell/disabled", 281, 80, 2, 2, 277, 76);
    private static final Sprite BUTTONS_UTILITY_NORMAL = new Sprite("buttons/utility/normal", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_UTILITY_HOVER = new Sprite("buttons/utility/hover", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_UTILITY_PRESSED = new Sprite("buttons/utility/pressed", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_UTILITY_SELECTED = new Sprite("buttons/utility/selected", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_UTILITY_DISABLED = new Sprite("buttons/utility/disabled", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_CLOSE_NORMAL = new Sprite("buttons/close/normal", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_CLOSE_HOVER = new Sprite("buttons/close/hover", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_CLOSE_PRESSED = new Sprite("buttons/close/pressed", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_CLOSE_SELECTED = new Sprite("buttons/close/selected", 152, 152, 2, 2, 148, 148);
    private static final Sprite BUTTONS_CLOSE_DISABLED = new Sprite("buttons/close/disabled", 152, 152, 2, 2, 148, 148);
    private static final Sprite SLOTS_NORMAL = new Sprite("slots/normal", 165, 165, 2, 2, 161, 161);
    private static final Sprite SLOTS_HOVER = new Sprite("slots/hover", 165, 165, 2, 2, 161, 161);
    private static final Sprite SLOTS_PRESSED = new Sprite("slots/pressed", 165, 165, 2, 2, 161, 161);
    private static final Sprite SLOTS_SELECTED = new Sprite("slots/selected", 165, 165, 2, 2, 161, 161);
    private static final Sprite SLOTS_DISABLED = new Sprite("slots/disabled", 165, 165, 2, 2, 161, 161);
    private static final Sprite ICONS_LEFT = new Sprite("icons/left", 33, 33, 9, 4, 15, 25);
    private static final Sprite ICONS_RIGHT = new Sprite("icons/right", 33, 33, 9, 4, 14, 25);
    private static final Sprite ICONS_CLOSE = new Sprite("icons/close", 33, 33, 4, 4, 25, 25);
    private static final Sprite[] SLOTS = {SLOTS_NORMAL, SLOTS_HOVER, SLOTS_SELECTED, SLOTS_DISABLED, SLOTS_PRESSED};
    private static final Sprite[] STANDARD = {BUTTONS_UTILITY_NORMAL, BUTTONS_UTILITY_HOVER, BUTTONS_UTILITY_SELECTED, BUTTONS_UTILITY_DISABLED, BUTTONS_UTILITY_PRESSED};
    private static final Sprite[] PRIMARY = {BUTTONS_BUY_NORMAL, BUTTONS_BUY_HOVER, BUTTONS_BUY_SELECTED, BUTTONS_BUY_DISABLED, BUTTONS_BUY_PRESSED};
    private static final Sprite[] SELL = {BUTTONS_SELL_NORMAL, BUTTONS_SELL_HOVER, BUTTONS_SELL_SELECTED, BUTTONS_SELL_DISABLED, BUTTONS_SELL_PRESSED};
    private static final Sprite[] CLOSE = {BUTTONS_CLOSE_NORMAL, BUTTONS_CLOSE_HOVER, BUTTONS_CLOSE_SELECTED, BUTTONS_CLOSE_DISABLED, BUTTONS_CLOSE_PRESSED};
    private static final Sprite[] TABS = {TABS_NORMAL, TABS_HOVER, TABS_SELECTED, TABS_DISABLED, TABS_PRESSED};
    private ShopTextures() {}

    static void frame(GuiGraphics g, int x, int y, int width, int height) {
        backing(g, x+8, y+20, width-16, height-24);
        slice(g, FRAME, x+4, y+16, width-8, height-16, 80, 7, false);
        slice(g, PANELS_PRODUCTS, x+18, y+45, 173, 86, 32, 3, false);
        slice(g, PANELS_DETAILS, x+199, y+45, 88, 176, 32, 3, false);
        slice(g, PANELS_INVENTORY, x+18, y+133, 173, 88, 32, 3, false);
        title(g, x+85, y);
        fit(g, PANELS_SECTION, x+20, y+46, 45, 13);
        fit(g, PANELS_SECTION, x+20, y+132, 61, 13);
    }
    private static void backing(GuiGraphics g, int x, int y, int width, int height) {
        // Reuse the opaque dark wood inside the socket, excluding all bevels.
        // Tile this material as a separate layer beneath the transparent outer frame.
        Sprite s = SLOTS_NORMAL;
        int u = s.x + (s.width-32)/2, v = s.y + (s.height-32)/2;
        for (int dy=0; dy<height; dy+=16) {
            for (int dx=0; dx<width; dx+=16) {
                int w=Math.min(16,width-dx), h=Math.min(16,height-dy);
                blit(g,s,x+dx,y+dy,w,h,u,v,w*2,h*2);
            }
        }
    }
    static void slot(GuiGraphics g, int x, int y, int state, int size) {
        // An 18px socket must leave the full native 16px item interior clear.
        slice(g, SLOTS[state], x, y, size, size, 24, 1, false);
    }
    static void emptyNotice(GuiGraphics g, int x, int y, int width, int height) {
        slice(g, PANELS_SECTION, x, y, width, height, 42, 7, false);
    }
    private static void title(GuiGraphics g, int x, int y) {
        // Separate short chains from the board; keep every pixel inside the logical window.
        // The central blank board gets the flexible width; cloth ends keep fixed widths.
        Sprite s = PANELS_TITLE;
        int[] sw = {120, 348, 120}, dw = {26, 78, 26};
        int[] sh = {118, 140}, dh = {5, 22};
        int sy = s.y, dy = y;
        for (int row = 0; row < sh.length; row++) {
            int sx = s.x, dx = x;
            for (int col = 0; col < sw.length; col++) {
                blit(g,s,dx,dy,dw[col],dh[row],sx,sy,sw[col],sh[row]);
                sx += sw[col]; dx += dw[col];
            }
            sy += sh[row]; dy += dh[row];
        }
    }
    static void button(GuiGraphics g, NeoButton button, boolean close) {
        int state = state(button);
        Sprite sprite = (close ? CLOSE : button.getStyle() == NeoButton.Style.PRIMARY ? PRIMARY : button.getStyle() == NeoButton.Style.DESTRUCTIVE ? SELL : STANDARD)[state];
        control(g, sprite, button);
    }
    private static int state(NeoButton button) {
        if (!button.active) return 3;
        if (button.isHovered() && org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                net.minecraft.client.Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == org.lwjgl.glfw.GLFW.GLFW_PRESS) return 4;
        return button.isSelected() ? 2 : button.isHoveredOrFocused() ? 1 : 0;
    }
    static void tab(GuiGraphics g, NeoButton button) {
        control(g, TABS[state(button)], button);
    }
    private static void control(GuiGraphics g, Sprite s, NeoButton button) {
        int x=button.getX(), y=button.getY(), w=button.getWidth(), h=button.getHeight();
        if (w == h) {
            fit(g,s,x,y,w,h);
            return;
        }
        // Full-height end caps scale uniformly; only the undecorated middle grows in width.
        int cap = Math.min(s.width/3, s.height);
        int edge = Math.min(w/2, Math.max(1, Math.round(cap*h/(float)s.height)));
        blit(g,s,x,y,edge,h,s.x,s.y,cap,s.height);
        blit(g,s,x+edge,y,w-2*edge,h,s.x+cap,s.y,s.width-2*cap,s.height);
        blit(g,s,x+w-edge,y,edge,h,s.x+s.width-cap,s.y,cap,s.height);
    }
    static void iconButton(GuiGraphics g, NeoButton button, String icon) {
        button(g, button, icon.equals("close"));
        Sprite sprite = switch (icon) {
            case "left" -> ICONS_LEFT;
            case "right" -> ICONS_RIGHT;
            case "close" -> ICONS_CLOSE;
            default -> throw new IllegalArgumentException("Unknown shop icon: " + icon);
        };
        if (!button.active) g.setColor(0.4F, 0.4F, 0.4F, 1F);
        fit(g, sprite, button.getX()+3, button.getY()+3, button.getWidth()-6, button.getHeight()-6);
        g.setColor(1F, 1F, 1F, 1F);
    }
    private static void fit(GuiGraphics g, Sprite s, int x, int y, int width, int height) {
        float scale = Math.min(width/(float)s.width, height/(float)s.height);
        int w = Math.max(1, Math.round(s.width*scale)), h = Math.max(1, Math.round(s.height*scale));
        blit(g,s,x+(width-w)/2,y+(height-h)/2,w,h,s.x,s.y,s.width,s.height);
    }
    private static void blit(GuiGraphics g, Sprite s, int x, int y, int w, int h, int u, int v, int sw, int sh) {
        if (w <= 0 || h <= 0) return;
        RenderSystem.enableBlend();
        g.blit(s.texture,x,y,w,h,u,v,sw,sh,s.textureWidth,s.textureHeight);
    }
    private static void slice(GuiGraphics g, Sprite s, int x, int y, int width, int height,
                              int sourceEdge, int edge, boolean centerOrnament) {
        edge = Math.min(edge, Math.min(width,height)/2);
        int[] sw = centerOrnament
                ? new int[]{sourceEdge, (s.width-sourceEdge*3)/2, sourceEdge,
                    s.width-sourceEdge*3-(s.width-sourceEdge*3)/2, sourceEdge}
                : new int[]{sourceEdge,s.width-sourceEdge*2,sourceEdge};
        int[] dw = centerOrnament
                ? new int[]{edge,(width-edge*3)/2,edge,width-edge*3-(width-edge*3)/2,edge}
                : new int[]{edge,width-edge*2,edge};
        int[] sh = {sourceEdge,s.height-sourceEdge*2,sourceEdge};
        int[] dh = {edge,height-edge*2,edge};
        int sy=s.y,dy=y;
        for (int row=0;row<3;row++) {
            int sx=s.x,dx=x;
            for (int col=0;col<sw.length;col++) {
                blit(g,s,dx,dy,dw[col],dh[row],sx,sy,sw[col],sh[row]);
                dx+=dw[col]; sx+=sw[col];
            }
            dy+=dh[row]; sy+=sh[row];
        }
    }
}
