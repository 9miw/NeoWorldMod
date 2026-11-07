package com.ambition.neoworld.killer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Defers death effects until the event dispatch has finished; keys identify entity lives. */
final class KillerDeathQueue<T> {
    private record Pending<T>(BooleanSupplier canceled, Consumer<T> commit) {}
    private final Map<T, Pending<T>> pending = new LinkedHashMap<>();
    private final Set<T> completed = Collections.newSetFromMap(new WeakHashMap<>());

    void submit(T life, BooleanSupplier canceled, Runnable commit) {
        submit(life, canceled, ignored -> commit.run());
    }

    void submit(T life, BooleanSupplier canceled, Consumer<T> commit) {
        if (completed.contains(life)) return;
        pending.compute(life, (ignored, previous) -> previous == null || previous.canceled().getAsBoolean()
                ? new Pending<>(canceled, commit) : previous);
    }

    void flush() {
        // A callback may enqueue another death; that belongs to the next dispatch cycle.
        for (T life : java.util.List.copyOf(pending.keySet())) flush(life, life);
    }

    void flush(T life, T recipient) {
        Pending<T> death = pending.remove(life);
        if (death != null && !death.canceled().getAsBoolean() && completed.add(life))
            death.commit().accept(recipient);
    }

    void clear() {
        pending.clear();
        completed.clear();
    }
}
