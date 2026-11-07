package com.ambition.neoworld.client.gui.dialogue;

/** Scaled GUI coordinates shared by drawing, clipping and input. No Minecraft dependencies. */
public record DialogueLayout(Box panel, Box portrait, Box text, Box choices, Box close,
                             int footerY, int[] rowHeights, int scroll, int maxScroll) {
    public static final float SPEECH_SCALE = 1.25F;

    public static int speechLineHeight(int fontHeight) {
        return (int) Math.ceil((fontHeight + 2) * SPEECH_SCALE);
    }

    public static int speechTopOverhang() { return (int) Math.ceil(3 * SPEECH_SCALE); }

    /** Choose rows by nominal bounds; the larger ink clip is only for those rows' Thai marks. */
    public boolean speechLineVisible(int originY, int lineHeight) {
        return originY + lineHeight > text.y() && originY < text.bottom();
    }

    /** Thai bitmap marks extend above the draw origin; allow ink without shifting line positions. */
    public Box textClip() {
        int top = Math.max(0, text.y() - speechTopOverhang());
        return new Box(text.x(), top, text.width(), text.bottom() - top);
    }

    public record Box(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    public static DialogueLayout create(int width, int height, int fontHeight, int[] heights,
                                        boolean hideNpc, int scroll) {
        int margin = width < 400 ? 6 : 12;
        int line = Math.max(9, fontHeight + 2);
        int rowTotal = 0;
        for (int h : heights) rowTotal += Math.max(1, h) + 2;
        int speechHeight = speechLineHeight(fontHeight) * 3;
        // One wrapped line uses fontHeight+8 pixels, with two-pixel gaps between rows.
        int wantedChoices = Math.min(Math.max(0, rowTotal - 2), 3 * (fontHeight + 8) + 4);
        int requiredHeight = line + 12 + speechHeight + 7 + wantedChoices + 4 + line + 7;
        int panelHeight = Math.min(height - 12, Math.max(hideNpc ? 0 : 140, requiredHeight));
        Box panel = new Box(margin, height - panelHeight - 6, width - margin * 2, panelHeight);
        int previewWidth = hideNpc ? 0 : Math.min(100, panel.width() * 20 / 100);
        Box portrait = new Box(panel.x() + 5, panel.y() + 6, previewWidth, panel.height() - 12);
        int contentX = hideNpc ? panel.x() + 12 : portrait.right() + 12;
        int contentWidth = Math.max(20, panel.right() - contentX - 14);
        int footerY = panel.bottom() - line - 7;
        int textY = panel.y() + line + 12;
        int choiceHeight = Math.min(wantedChoices, Math.max(0, footerY - 4 - textY - speechHeight - 7));
        Box choices = new Box(contentX, footerY - choiceHeight - 4, contentWidth, choiceHeight);
        Box text = new Box(contentX + 2, textY, contentWidth - 4,
                Math.min(speechHeight, Math.max(1, choices.y() - textY - 7)));
        Box close = new Box(panel.right() - 21, panel.y() + 8, 13, 13);
        int maxScroll = Math.max(0, rowTotal - 2 - choices.height());
        return new DialogueLayout(panel, portrait, text, choices, close, footerY,
                heights.clone(), Math.max(0, Math.min(scroll, maxScroll)), maxScroll);
    }

    public Box row(int index) {
        int y = choices.y() - scroll;
        for (int i = 0; i < index; i++) y += Math.max(1, rowHeights[i]) + 2;
        return new Box(choices.x(), y, choices.width(), Math.max(1, rowHeights[index]));
    }

    public int choiceAt(double x, double y) {
        if (!choices.contains(x, y)) return -1;
        for (int i = 0; i < rowHeights.length; i++) if (row(i).contains(x, y)) return i;
        return -1;
    }

    public int scrollToReveal(int index) {
        if (index < 0 || index >= rowHeights.length) return scroll;
        Box row = row(index);
        int next = scroll;
        if (row.height() > choices.height()) next += row.y() - choices.y();
        else if (row.y() < choices.y()) next -= choices.y() - row.y();
        else if (row.bottom() > choices.bottom()) next += row.bottom() - choices.bottom();
        return Math.max(0, Math.min(next, maxScroll));
    }
}
