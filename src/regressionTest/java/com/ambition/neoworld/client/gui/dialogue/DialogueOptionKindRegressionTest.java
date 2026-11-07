package com.ambition.neoworld.client.gui.dialogue;

public final class DialogueOptionKindRegressionTest {
    public static void main(String[] args) {
        check(DialogueOptionKind.fromType(0) == DialogueOptionKind.CLOSE, "0 closes dialogue");
        check(DialogueOptionKind.fromType(1) == DialogueOptionKind.DIALOGUE, "1 links dialogue");
        check(DialogueOptionKind.fromType(2) == DialogueOptionKind.DISABLED, "2 is disabled");
        check(DialogueOptionKind.fromType(3) == DialogueOptionKind.ROLE, "3 opens the NPC role, not necessarily a shop");
        check(DialogueOptionKind.fromType(4) == DialogueOptionKind.COMMAND, "4 executes a command");
        for (int unknown : new int[] {-1, 5, 99, Integer.MIN_VALUE, Integer.MAX_VALUE})
            check(DialogueOptionKind.fromType(unknown) == DialogueOptionKind.UNKNOWN,
                    "unrecognized types must not claim a known action");
        check(DialogueOptionKind.fromType(1, 42) == DialogueOptionKind.QUEST, "linked quest overrides speech icon");
        check(DialogueOptionKind.fromType(1, 0) == DialogueOptionKind.QUEST, "zero is a bound quest ID, not the unset sentinel");
        check(DialogueOptionKind.fromType(1, -1) == DialogueOptionKind.DIALOGUE, "unset quest keeps speech icon");
        check(DialogueOptionKind.fromType(1, null) == DialogueOptionKind.DIALOGUE, "missing target metadata is not guessed");
        for (int nativeType : new int[] {0, 2, 3, 4, 99})
            check(DialogueOptionKind.fromType(nativeType, 42) == DialogueOptionKind.fromType(nativeType),
                    "quest metadata cannot replace non-dialogue action icons");
        System.out.println("Dialogue option kind regression checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
