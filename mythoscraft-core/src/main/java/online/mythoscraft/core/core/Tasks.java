package online.mythoscraft.core.core;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Owns all repeating tasks of the plugin. Every system registers its tasks under itself as owner and cancels them
 * before re-creating them on reload, so a reload can never leave duplicate schedulers behind.
 */
public final class Tasks {

    private final Plugin plugin;
    private final Map<Object, List<BukkitTask>> owned = new IdentityHashMap<>();
    private final ExecutorService io;

    public Tasks(Plugin plugin) {
        this.plugin = plugin;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "MythosCraft-IO");
            t.setDaemon(true);
            return t;
        });
    }

    public BukkitTask repeat(Object owner, long delayTicks, long periodTicks, String name, Runnable task) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, guard(name, task), Math.max(0, delayTicks), Math.max(1, periodTicks));
        owned.computeIfAbsent(owner, o -> new ArrayList<>()).add(t);
        return t;
    }

    public BukkitTask later(long delayTicks, String name, Runnable task) {
        return Bukkit.getScheduler().runTaskLater(plugin, guard(name, task), Math.max(1, delayTicks));
    }

    /** Runs on the main thread. Safe to call from any thread; silently dropped once the plugin is disabled. */
    public void sync(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (Bukkit.isPrimaryThread()) {
            guard("sync", task).run();
        } else {
            Bukkit.getScheduler().runTask(plugin, guard("sync", task));
        }
    }

    /** Ordered single-thread executor for file writes. */
    public void io(String name, Runnable task) {
        try {
            io.execute(guard(name, task));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            guard(name, task).run();
        }
    }

    public void cancel(Object owner) {
        List<BukkitTask> list = owned.remove(owner);
        if (list != null) {
            list.forEach(BukkitTask::cancel);
        }
    }

    public int activeCount() {
        int n = 0;
        for (List<BukkitTask> list : owned.values()) {
            for (BukkitTask t : list) {
                if (!t.isCancelled()) {
                    n++;
                }
            }
        }
        return n;
    }

    public void shutdown() {
        owned.values().forEach(list -> list.forEach(BukkitTask::cancel));
        owned.clear();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("File writes did not finish within 10 seconds.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Runnable guard(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Task '" + name + "' failed: " + t.getMessage(), t);
            }
        };
    }
}
