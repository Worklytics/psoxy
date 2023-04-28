package co.worklytics.psoxy.gateway.impl;

import co.worklytics.psoxy.gateway.LockService;
import lombok.NoArgsConstructor;

import javax.inject.Inject;

/**
 * Use where you don't need real locking (eg, know only one process will run at a time)
 *
 *
 */
@NoArgsConstructor(onConstructor_ = @Inject)
public class BlindlyOptimisticLockService implements LockService {
    @Override
    public boolean acquire(String lockId) {
        return true;
    }

    @Override
    public void release(String lockId) {

    }
}
