package com.ambition.neoworld.client.gui.dialogue;

public final class DialogueNpcPreviewScopeRegressionTest {
    public static void main(String[] args) throws Exception {
        Object first = new Object(), second = new Object();
        check(!DialogueNpcPreviewScope.isRendering(first), "world rendering is not a preview");
        DialogueNpcPreviewScope.render(first, () -> {
            check(DialogueNpcPreviewScope.isRendering(first), "preview suppresses its own labels");
            check(!DialogueNpcPreviewScope.isRendering(second), "other NPCs retain labels");
            DialogueNpcPreviewScope.render(second, () -> {
                check(DialogueNpcPreviewScope.isRendering(second), "nested preview uses its own entity");
                check(!DialogueNpcPreviewScope.isRendering(first), "nested preview does not suppress outer entity");
            });
            check(DialogueNpcPreviewScope.isRendering(first), "outer scope restored after nested preview");
            Thread other = new Thread(() -> check(!DialogueNpcPreviewScope.isRendering(first), "scope is render-thread local"));
            other.setUncaughtExceptionHandler((t, error) -> failure = error);
            other.start();
            try { other.join(); } catch (InterruptedException error) { throw new AssertionError(error); }
            if (failure != null) throw new AssertionError(failure);
        });
        check(!DialogueNpcPreviewScope.isRendering(first), "world labels restored after preview");
        try {
            DialogueNpcPreviewScope.render(first, () -> { throw new IllegalStateException("render failure"); });
            throw new AssertionError("render failure must propagate");
        } catch (IllegalStateException expected) {
            check(!DialogueNpcPreviewScope.isRendering(first), "render failure cannot leak suppression");
        }
        check(!DialogueNpcPreviewScope.isRendering(null), "no scope must not match null");
        System.out.println("Dialogue NPC preview scope regression checks passed");
    }

    private static volatile Throwable failure;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
