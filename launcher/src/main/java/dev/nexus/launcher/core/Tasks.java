package dev.nexus.launcher.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** Background executors. Every thread is a daemon so the launcher always exits when its window closes. */
public final class Tasks {
    private static final ExecutorService IO = Executors.newCachedThreadPool(factory("nexus-io"));

    private Tasks() {}

    public static ExecutorService io() {
        return IO;
    }

    public static ThreadFactory factory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
