package io.github.dschonas04.tntarsenal.nuclear.explosion;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;

/**
 * The job queue: work that is spread over many ticks — the explosion, delayed
 * sounds — runs here at the end of each level tick until it reports that it is done.
 */
public final class Jobs {
    private Jobs() {
    }

    public interface Job {
        ServerLevel level();

        /** One tick of work. Returns true when finished. */
        boolean tick();

        /** The server is stopping; release whatever the job holds (forced chunks). */
        default void abort() {
        }
    }

    private static final List<Job> RUNNING = new ArrayList<>();

    public static void add(Job job) {
        RUNNING.add(job);
    }

    public static void register() {
        ServerTickEvents.END_LEVEL_TICK.register(level -> {
            // a copy, because a job may add another
            for (Job job : List.copyOf(RUNNING)) {
                if (job.level() != level) continue;
                boolean done;
                try {
                    done = job.tick();
                } catch (RuntimeException e) {
                    io.github.dschonas04.tntarsenal.nuclear.NuclearTnt.LOG.error("A nuclear TNT job failed and was stopped", e);
                    job.abort();
                    done = true;
                }
                if (done) RUNNING.remove(job);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            RUNNING.forEach(Job::abort);
            RUNNING.clear();
        });
    }
}
