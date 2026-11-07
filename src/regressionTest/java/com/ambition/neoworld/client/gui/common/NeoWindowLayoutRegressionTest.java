package com.ambition.neoworld.client.gui.common;

public final class NeoWindowLayoutRegressionTest {
    public static void main(String[] args) {
        for (int[] size : new int[][] {{1280,720},{640,360},{427,240},{320,180},{160,90},{24,24},{1,1}}) {
            var b = NeoWindowLayout.centered(size[0], size[1], 360, 240);
            if (b.x() < 0 || b.y() < 0 || b.x() + b.width() > size[0]
                    || b.y() + b.height() > size[1]) throw new AssertionError("Window outside viewport");
            if (b.closeX() < b.x() || b.closeY() < b.y()
                    || b.closeX() + b.closeSize() > b.x() + b.width()
                    || b.closeY() + b.closeSize() > b.y() + b.height())
                throw new AssertionError("Close button outside window");
        }
        var normal = NeoWindowLayout.centered(640, 360, 360, 240);
        if (normal.x() != 140 || normal.y() != 60 || normal.closeSize() != 20)
            throw new AssertionError("Unexpected centered bounds");
        System.out.println("Shared window layout regression passed");
    }
}
