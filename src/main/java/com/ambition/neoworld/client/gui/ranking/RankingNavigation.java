package com.ambition.neoworld.client.gui.ranking;

import java.util.List;
import com.ambition.neoworld.ranking.RankingTable.Category;

/** Client navigation includes planned tabs without adding unsupported wire categories. */
public final class RankingNavigation {
    public record Tab(String title,Category category) { }
    public static final int VISIBLE_ROWS=5;
    public static final List<Tab> TABS=List.of(
            new Tab("อันดับ PvP",Category.PVP),new Tab("อันดับ Lv.",Category.LEVEL),new Tab("อันดับเงิน",Category.SILVER),
            new Tab("ดันเจี้ยน",null),new Tab("เควสต์",null),new Tab("นักสะสม",null),
            new Tab("ค้าขาย",null),new Tab("ปราบบอส",null),new Tab("กิลด์",null));
    public static int clampOffset(int offset) { return Math.clamp(offset,0,TABS.size()-VISIBLE_ROWS); }
    public static boolean visible(int index,int offset) { return index>=offset && index<offset+VISIBLE_ROWS; }
    private RankingNavigation() { }
}
