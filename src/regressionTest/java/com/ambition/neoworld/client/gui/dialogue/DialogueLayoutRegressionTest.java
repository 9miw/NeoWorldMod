package com.ambition.neoworld.client.gui.dialogue;

/** Pure layout checks: no game, rendering context or external test dependency required. */
public final class DialogueLayoutRegressionTest {
    public static void main(String[] args) {
        int[] six = {17, 17, 17, 17, 17, 17};
        for (int[] size : new int[][] {{320, 180}, {426, 240}, {640, 360}, {1280, 720}}) {
            DialogueLayout layout = DialogueLayout.create(size[0], size[1], 9, six, false, 0);
            check(layout.text().height() == 42, "NPC speech viewport must fit three enlarged lines");
            check(layout.panel().x() >= 0 && layout.panel().right() <= size[0], "horizontal panel bounds");
            check(layout.panel().y() >= 0 && layout.panel().bottom() <= size[1], "vertical panel bounds");
            check(layout.text().bottom() < layout.choices().y(), "history and options must not overlap");
            check(layout.choices().bottom() <= layout.footerY(), "options cannot overlap footer");
            check(layout.portrait().right() < layout.text().x(), "NPC cannot overlap text");
            // Thai bitmap ascent is 9; Minecraft's draw origin is 7, so ink can
            // extend two logical pixels above the first line's draw position.
            check(layout.textClip().contains(layout.text().x() + 2, layout.text().y() - 2),
                    "first-line Thai upper marks must be inside the speech clip");
            check(layout.textClip().bottom() == layout.text().bottom(),
                    "upper-mark allowance cannot extend into response controls");
        }
        DialogueLayout normal = DialogueLayout.create(426, 240, 9, six, false, 0);
        for (int row = 0; row < 3; row++)
            check(normal.speechLineVisible(normal.text().y() + row * 14, 14), "three speech lines remain visible");
        check(!normal.speechLineVisible(normal.text().y() - 14, 14),
                "previous line must not leave glyph fragments in upper-mark allowance");
        check(!normal.speechLineVisible(normal.text().bottom(), 14),
                "fourth line must not leak upper marks at the bottom");
        for (int i = 0; i < 3; i++) {
            DialogueLayout.Box row = normal.row(i);
            check(row.y() >= normal.choices().y() && row.bottom() <= normal.choices().bottom(),
                    "three ordinary choices must be fully visible");
            check(normal.choiceAt(row.x() + 2, row.y() + 2) == i, "three rows visible and clickable");
            check(normal.choiceAt(row.right(), row.y() + 2) == -1, "right edge is exclusive");
            check(normal.choiceAt(row.x() - 1, row.y() + 2) == -1, "outside row cannot select");
        }
        check(normal.row(3).y() >= normal.choices().bottom(), "fourth choice must start outside viewport");
        check(normal.choiceAt(normal.row(3).x() + 2, normal.row(3).y() + 2) == -1,
                "fourth choice cannot be clicked before scrolling");
        int nextPage = normal.scrollToReveal(3);
        DialogueLayout fourth = DialogueLayout.create(426, 240, 9, six, false, nextPage);
        check(fourth.choiceAt(fourth.row(3).x() + 2, fourth.row(3).y() + 2) == 3,
                "scrolling reveals the actual fourth option");
        for (int count : new int[] {0, 1, 3}) {
            int[] rows = new int[count];
            java.util.Arrays.fill(rows, 17);
            check(DialogueLayout.create(426, 240, 9, rows, false, 0).maxScroll() == 0,
                    "up to three ordinary responses must not require scrolling");
        }
        DialogueLayout hidden = DialogueLayout.create(426, 240, 9, six, true, 0);
        check(hidden.portrait().width() == 0, "hideNPC removes preview");
        check(hidden.text().width() > normal.text().width(), "hideNPC returns space to content");
        DialogueLayout empty = DialogueLayout.create(320, 180, 9, new int[0], true, 900);
        check(empty.choiceAt(200, 140) == -1 && empty.maxScroll() == 0, "empty list is safe");
        int[] longRows = new int[25];
        java.util.Arrays.fill(longRows, 26);
        DialogueLayout many = DialogueLayout.create(320, 180, 9, longRows, false, 0);
        check(many.maxScroll() > 0, "long lists scroll");
        int endScroll = many.scrollToReveal(24);
        DialogueLayout last = DialogueLayout.create(320, 180, 9, longRows, false, endScroll);
        DialogueLayout.Box lastRow = last.row(24);
        check(lastRow.y() >= last.choices().y() && lastRow.bottom() <= last.choices().bottom(), "keyboard reveals last row");
        check(last.choiceAt(lastRow.x() + 2, lastRow.y() + 2) == 24, "scrolled hitbox maps to actual index");
        check(last.choiceAt(lastRow.x() + 2, last.choices().y() - 1) == -1, "clipped rows cannot be clicked");
        check(DialogueLayout.create(320, 180, 9, longRows, false, 99999).scroll() == many.maxScroll(), "clamp overscroll");
        DialogueLayout oversized = DialogueLayout.create(320, 180, 9, new int[] {15, 250, 15}, false, 0);
        int oversizedScroll = oversized.scrollToReveal(1);
        DialogueLayout revealed = DialogueLayout.create(320, 180, 9, new int[] {15, 250, 15}, false, oversizedScroll);
        check(revealed.row(1).y() == revealed.choices().y(), "oversized answer must reveal its beginning");
        check(revealed.scrollToReveal(1) == oversizedScroll, "repeated reveal cannot jump between ends");
        System.out.println("Dialogue layout regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
