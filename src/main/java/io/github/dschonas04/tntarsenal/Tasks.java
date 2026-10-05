package io.github.dschonas04.tntarsenal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detonations that play out over time — a tunnel that is dug slice by slice, a
 * black hole that pulls for five seconds — register a task here. The server
 * ticks every task once at the end of each server tick until it says it is done.
 * Spreading work over ticks also keeps big jobs from stalling the server.
 */
final class Tasks {
    private Tasks() {
    }

    @FunctionalInterface
    interface Task {
        /** Runs once per tick; {@code age} counts from 0. Returns true when finished. */
        boolean tick(int age);
    }

    private static final Logger LOG = LoggerFactory.getLogger(TntArsenal.MOD_ID);
    private static final List<Running> RUNNING = new ArrayList<>();
    private static final List<Running> STARTED = new ArrayList<>();

    private static final class Running {
        final Task task;
        final Runnable onAbort;
        int age;

        Running(Task task, Runnable onAbort) {
            this.task = task;
            this.onAbort = onAbort;
        }

        void abort() {
            try {
                onAbort.run();
            } catch (RuntimeException e) {
                LOG.error("Cleaning up after a TNT Arsenal detonation failed", e);
            }
        }
    }

    static void start(Task task) {
        start(task, () -> { });
    }

    /** Like {@link #start(Task)}, but runs {@code onAbort} if the task fails or the server stops before it is done. */
    static void start(Task task, Runnable onAbort) {
        STARTED.add(new Running(task, onAbort));
    }

    /** Called from the server tick. Tasks started during this tick run from the next one. */
    static void tickAll() {
        RUNNING.addAll(STARTED);
        STARTED.clear();
        for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
            Running running = it.next();
            boolean done;
            try {
                done = running.task.tick(running.age++);
            } catch (RuntimeException e) {
                LOG.error("A TNT Arsenal detonation failed and was stopped", e);
                running.abort();
                done = true;
            }
            if (done) it.remove();
        }
    }

    /** Drops everything when the server stops, so no task outlives its world. */
    static void clear() {
        RUNNING.forEach(Running::abort);
        STARTED.forEach(Running::abort);
        RUNNING.clear();
        STARTED.clear();
    }
}
