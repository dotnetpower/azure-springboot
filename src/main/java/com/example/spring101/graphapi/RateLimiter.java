package com.example.spring101.graphapi;



import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import org.springframework.stereotype.Component;


@Component
public class RateLimiter {

    public static final Logger logger = Logger.getLogger(RateLimiter.class.getName());

    private static final String RATELIMIT_LIMIT = "RateLimit-Limit";
    private static final String RATELIMIT_REMAINING = "RateLimit-Remaining";
    private static final String RATELIMIT_RESET = "RateLimit-Reset";

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private final AtomicInteger limit = new AtomicInteger(-1);
    private final AtomicInteger reset = new AtomicInteger(-1);
    private final AtomicInteger remaining = new AtomicInteger(-1);
    private final AtomicLong nextReset = new AtomicLong(Instant.now().toEpochMilli());

    private int minimumCapacityLeft = 10; // default 10%

    public void setMinimumCapacityLeft(int value) {
        this.minimumCapacityLeft = value;
    }

    public void waitIfNeeded(String apiType) throws InterruptedException {
        if (minimumCapacityLeft == 0) {
            return;
        }

        long delayMillis = 0;
        float capacityLeft = 0f;

        lock.readLock().lock();
        try {
            if (limit.get() > 0 && remaining.get() > 0) {
                capacityLeft = Math.round(((float) remaining.get() / limit.get()) * 10000) / 100f;

                if (capacityLeft <= minimumCapacityLeft) {
                    delayMillis = nextReset.get() - Instant.now().toEpochMilli();
                }
            }
        } finally {
            lock.readLock().unlock();
        }

        if (delayMillis > 0) {
            logger.warning(String.format("Delaying %s request for %d seconds, capacity left: %.2f%%", apiType, delayMillis / 1000, capacityLeft));
            TimeUnit.MILLISECONDS.sleep(delayMillis);
        }
    }

    public void updateWindow(HttpResponseWrapper response, String apiType) {
        if (minimumCapacityLeft == 0) {
            return;
        }

        if (response != null) {
            int rateLimit = response.getHeaderAsInt(RATELIMIT_LIMIT, -1);
            int rateRemaining = response.getHeaderAsInt(RATELIMIT_REMAINING, -1);
            int rateReset = response.getHeaderAsInt(RATELIMIT_RESET, -1);

            lock.writeLock().lock();
            try {
                limit.set(rateLimit);
                remaining.set(rateRemaining);
                reset.set(rateReset);

                if (rateReset > -1) {
                    nextReset.set(Instant.now().toEpochMilli() + TimeUnit.SECONDS.toMillis(rateReset));
                }
            } finally {
                lock.writeLock().unlock();
            }

            if (rateReset > -1) {
                logger.info(String.format("%s request. RateLimit-Limit: %d, RateLimit-Remaining: %d, RateLimit-Reset: %d",
                        apiType, rateLimit, rateRemaining, rateReset));
            }
        }
    }
}
