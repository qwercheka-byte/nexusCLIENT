package dev.nexus.launcher.net;

import java.io.InterruptedIOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe progress of a long operation. Worker threads write to it, the UI polls it on a timer
 * (no event per downloaded chunk reaches the FX thread).
 */
public final class Progress {
    private volatile String stage = "";
    private volatile String detail = "";
    private final AtomicLong done = new AtomicLong();
    private final AtomicLong total = new AtomicLong();
    private volatile boolean cancelled;
    private volatile Thread worker;

    public void stage(String text) {
        stage = text;
        detail = "";
        done.set(0);
        total.set(0);
    }

    public void detail(String text) { detail = text; }

    public void total(long t) { total.set(t); }

    public void add(long n) { done.addAndGet(n); }

    public void set(long d) { done.set(d); }

    public String stage() { return stage; }

    public String detail() { return detail; }

    public long done() { return done.get(); }

    public long total() { return total.get(); }

    /** 0..1, or -1 when the amount of work is unknown. */
    public double fraction() {
        long t = total.get();
        return t <= 0 ? -1 : Math.min(1.0, done.get() / (double) t);
    }

    public void bindWorker(Thread t) { worker = t; }

    public void cancel() {
        cancelled = true;
        Thread t = worker;
        if (t != null) t.interrupt();
    }

    public boolean isCancelled() { return cancelled; }

    public void checkCancelled() throws InterruptedIOException {
        if (cancelled) throw new InterruptedIOException("Cancelled");
    }
}
