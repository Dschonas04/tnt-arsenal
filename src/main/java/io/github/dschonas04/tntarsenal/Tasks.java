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
        int age;

        Running(Task task) {
            this.task = task;
        }
    }

    static void start(Task task) {
        STARTED.add(new Running(task));
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
                done = true;
            }
            if (done) it.remove();
        }
    }

    /** Drops everything when the server stops, so no task outlives its world. */
    static void clear() {
        RUNNING.clear();
        STARTED.clear();
    }
}
