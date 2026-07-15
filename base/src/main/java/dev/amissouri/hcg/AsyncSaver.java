package dev.amissouri.hcg;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

public final class AsyncSaver<T> {

    private final HcgScheduler scheduler;
    private final long periodTicks;
    private final Supplier<T> snapshot;
    private final Consumer<T> write;

    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicLong sequence = new AtomicLong();
    private final Object writeLock = new Object();
    private long lastWritten;

    private ScheduledTask driver;

    public AsyncSaver(HcgScheduler scheduler, long periodTicks, Supplier<T> snapshot, Consumer<T> write) {
        this.scheduler = scheduler;
        this.periodTicks = periodTicks;
        this.snapshot = snapshot;
        this.write = write;
    }

    public void start() {
        driver = scheduler.globalTimer(this::tick, periodTicks, periodTicks);
    }

    public void markDirty() {
        dirty.set(true);
    }

    private void tick() {
        if (!dirty.compareAndSet(true, false)) {
            return;
        }
        T data = snapshot.get();
        long mine = sequence.incrementAndGet();
        scheduler.async(() -> writeIfNewest(mine, data));
    }

    private void writeIfNewest(long mine, T data) {
        synchronized (writeLock) {
            if (mine > lastWritten) {
                lastWritten = mine;
                write.accept(data);
            }
        }
    }

    public void flushNow() {
        HcgScheduler.cancel(driver);
        driver = null;
        dirty.set(false);
        T data = snapshot.get();
        writeIfNewest(sequence.incrementAndGet(), data);
    }
}
