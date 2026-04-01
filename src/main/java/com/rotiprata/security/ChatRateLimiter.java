package com.rotiprata.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {
    private static final int MAX_PROMPTS_PER_HOUR = 5;
    private static final Duration WINDOW = Duration.ofHours(1);
    private static final Duration STALE_BUCKET_TTL = Duration.ofHours(2);
    private static final int CLEANUP_INTERVAL = 64;

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();
    private final AtomicInteger operationCounter = new AtomicInteger();

    public void consumeOrThrow(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        cleanupIfNeeded();

        long now = System.currentTimeMillis();
        BucketEntry entry = buckets.compute(userId, (key, existing) -> {
            if (existing == null || existing.lastAccessedAtMillis < now - STALE_BUCKET_TTL.toMillis()) {
                return new BucketEntry(newBucket(), now);
            }
            existing.lastAccessedAtMillis = now;
            return existing;
        });

        ConsumptionProbe probe = entry.bucket.tryConsumeAndReturnRemaining(1);
        entry.lastAccessedAtMillis = now;
        if (probe.isConsumed()) {
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        throw new RateLimitExceededException("Too many chat prompts. Try again later.", retryAfterSeconds);
    }

    private void cleanupIfNeeded() {
        if (operationCounter.incrementAndGet() % CLEANUP_INTERVAL != 0) {
            return;
        }
        long cutoff = System.currentTimeMillis() - STALE_BUCKET_TTL.toMillis();
        buckets.entrySet().removeIf(entry -> entry.getValue().lastAccessedAtMillis < cutoff);
    }

    private Bucket newBucket() {
        Bandwidth limit = Bandwidth.classic(
            MAX_PROMPTS_PER_HOUR,
            Refill.intervally(MAX_PROMPTS_PER_HOUR, WINDOW)
        );
        return Bucket.builder().addLimit(limit).build();
    }

    private static final class BucketEntry {
        private final Bucket bucket;
        private volatile long lastAccessedAtMillis;

        private BucketEntry(Bucket bucket, long lastAccessedAtMillis) {
            this.bucket = bucket;
            this.lastAccessedAtMillis = lastAccessedAtMillis;
        }
    }
}
