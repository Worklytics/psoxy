package co.worklytics.psoxy.gateway;

public interface LockService {

    boolean acquire(String lockId);

    void release(String lockId);
}
