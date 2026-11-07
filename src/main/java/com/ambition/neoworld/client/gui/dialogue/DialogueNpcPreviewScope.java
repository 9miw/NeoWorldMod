package com.ambition.neoworld.client.gui.dialogue;

import java.util.Objects;

/** Identifies only the entity currently being drawn inside this dialogue's preview. */
public final class DialogueNpcPreviewScope {
    private static final ThreadLocal<Object> CURRENT = new ThreadLocal<>();

    private DialogueNpcPreviewScope() {}

    public static boolean isRendering(Object entity) {
        return entity != null && CURRENT.get() == entity;
    }

    public static void render(Object entity, Runnable draw) {
        Objects.requireNonNull(entity);
        Objects.requireNonNull(draw);
        Object previous = CURRENT.get();
        CURRENT.set(entity);
        try { draw.run(); }
        finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
