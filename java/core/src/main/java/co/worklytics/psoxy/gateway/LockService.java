package co.worklytics.psoxy.gateway;

import java.time.Duration;

public interface LockService {

    Duration DEFAULT_LOCK_DURATION = Duration.ofMinutes(2);

    /**
     * acquire a lock with the given id, that will expire after a default duration
     *
     * @param lockId to acquire
     * @return whether lock was successfully acquired
     */
    default boolean acquire(String lockId) {
        return acquire(lockId, DEFAULT_LOCK_DURATION);
    }

    /**
     * acquire a lock with the given id, that will expire after the given duration
     *
     * @param lockId to acquire; non-null, non-empty
     * @param expires for which to hold lock; non-null; nanosecond portion may be ignored
     * @return
     */
    boolean acquire(String lockId, Duration expires);

    void release(String lockId);
}
