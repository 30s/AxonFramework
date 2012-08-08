package org.axonframework.util.lock;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Locking mechanism that allows multiple threads to hold a lock, as long as the identifier of the lock they hold is
 * not equal. When released, locks entries are automatically cleaned up.
 * <p/>
 * The lock is re-entrant, meaning each thread can hold the same lock multiple times. The lock will only be released
 * for other threads when the lock has been released as many times as it was obtained.
 * <p/>
 * This lock can be used to ensure thread safe access to a number of objects, such as Aggregates and Sagas.
 *
 * @author Allard Buijze
 * @since 1.3
 */
public class IdentifierBasedLock {

    private final ConcurrentHashMap<String, DisposableLock> locks = new ConcurrentHashMap<String, DisposableLock>();

    private Set<Thread> threadsWaitingForMyLocks(Thread owner) {
        Set<Thread> waitingThreads = new HashSet<Thread>();
        for (DisposableLock disposableLock : locks.values()) {
            if (disposableLock.isHeldBy(owner)) {
                final Collection<Thread> c = disposableLock.queuedThreads();
                for (Thread thread : c) {
                    if (waitingThreads.add(thread)) {
                        waitingThreads.addAll(threadsWaitingForMyLocks(thread));
                    }
                }
            }
        }
        return waitingThreads;
    }

    /**
     * Indicates whether the current thread hold a lock for the given <code>identifier</code>.
     *
     * @param identifier The identifier of the lock to verify
     * @return <code>true</code> if the current thread holds a lock, otherwise <code>false</code>
     */
    public boolean hasLock(String identifier) {
        return isLockAvailableFor(identifier)
                && lockFor(identifier).isHeldByCurrentThread();
    }

    /**
     * Obtain a lock on the given <code>identifier</code>. This method will block until a lock was successfully
     * obtained.
     * <p/>
     * Note: when an exception occurs during the locking process, the lock may or may not have been allocated.
     *
     * @param identifier the identifier of the lock to obtain.
     */
    public void obtainLock(String identifier) {
        boolean lockObtained = false;
        while (!lockObtained) {
            DisposableLock lock = lockFor(identifier);
            lockObtained = lock.lock();
            if (!lockObtained) {
                locks.remove(identifier, lock);
            }
        }
    }

    /**
     * Release the lock held on the given <code>identifier</code>. If no valid lock is held by the current thread, an
     * exception is thrown.
     *
     * @param identifier the identifier to release the lock for.
     * @throws IllegalStateException        if no lock was ever obtained for this aggregate
     * @throws IllegalMonitorStateException if a lock was obtained, but is not currently held by the current thread
     */
    public void releaseLock(String identifier) {
        if (!locks.containsKey(identifier)) {
            throw new LockAcquisitionFailedException("No lock for this identifier was ever obtained");
        }
        DisposableLock lock = lockFor(identifier);
        try {
            lock.unlock(identifier);
        } catch (IllegalMonitorStateException e) {
            throw new LockAcquisitionFailedException("Could not release this lock", e);
        }
    }

    private boolean isLockAvailableFor(String identifier) {
        return locks.containsKey(identifier);
    }

    private DisposableLock lockFor(String identifier) {
        DisposableLock lock = locks.get(identifier);
        while (lock == null) {
            locks.putIfAbsent(identifier, new DisposableLock());
            lock = locks.get(identifier);
        }
        return lock;
    }

    private final class DisposableLock {

        private final PubliclyOwnedReentrantLock lock;
        // guarded by "lock"
        private volatile boolean isClosed = false;

        private DisposableLock() {
            this.lock = new PubliclyOwnedReentrantLock();
        }

        private boolean isHeldByCurrentThread() {
            return lock.isHeldByCurrentThread();
        }

        private void unlock(String identifier) {
            try {
                lock.unlock();
            } finally {
                disposeIfUnused(identifier);
            }
        }

        private boolean lock() {
            try {
                do {
                    checkForDeadlock();
                } while (!lock.tryLock(100, TimeUnit.MILLISECONDS));
            } catch (InterruptedException e) {
                throw new LockAcquisitionFailedException("Thread was interrupted", e);
            }
            if (isClosed) {
                lock.unlock();
                return false;
            }
            return true;
        }

        private void checkForDeadlock() {
            if (!lock.isHeldByCurrentThread() && lock.isLocked()) {
                for (Thread thread : threadsWaitingForMyLocks(Thread.currentThread())) {
                    if (lock.isHeldBy(thread)) {
                        throw new DeadlockException(
                                "An imminent deadlock was detected while attempting to acquire a lock");
                    }
                }
            }
        }

        private void disposeIfUnused(String identifier) {
            if (lock.tryLock()) {
                try {
                    if (lock.getHoldCount() == 1) {
                        // we now have a lock. We can shut it down.
                        isClosed = true;
                        locks.remove(identifier, this);
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        public Collection<Thread> queuedThreads() {
            return lock.getQueuedThreads();
        }

        public boolean isHeldBy(Thread owner) {
            return lock.isHeldBy(owner);
        }
    }

    private final class PubliclyOwnedReentrantLock extends ReentrantLock {

        @Override
        public Collection<Thread> getQueuedThreads() {
            return super.getQueuedThreads();
        }

        public boolean isHeldBy(Thread thread) {
            return thread.equals(getOwner());
        }
    }
}
