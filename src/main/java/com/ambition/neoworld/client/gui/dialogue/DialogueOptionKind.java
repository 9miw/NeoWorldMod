package com.ambition.neoworld.client.gui.dialogue;

/** Installed CustomNPCs optionType values; labels and command contents never determine the icon. */
public enum DialogueOptionKind {
    CLOSE, DIALOGUE, QUEST, DISABLED, ROLE, COMMAND, UNKNOWN;

    public static DialogueOptionKind fromType(int type) {
        return fromType(type, null);
    }

    /** Null means the linked dialogue metadata is unavailable; -1 is CustomNPCs' unset quest ID. */
    public static DialogueOptionKind fromType(int type, Integer targetQuestId) {
        return switch (type) {
            case 0 -> CLOSE;
            case 1 -> targetQuestId != null && targetQuestId >= 0 ? QUEST : DIALOGUE;
            case 2 -> DISABLED;
            case 3 -> ROLE;
            case 4 -> COMMAND;
            default -> UNKNOWN;
        };
    }
}
