package org.axonframework.util.lock;

import org.junit.*;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * @author Allard Buijze
 */
public class IdentifierBasedLockTest {

    private String identifier = "mockId";

    @Test
    public void testLockReferenceCleanedUpAtUnlock() throws NoSuchFieldException, IllegalAccessException {
        IdentifierBasedLock manager = new IdentifierBasedLock();
        manager.obtainLock(identifier);
        manager.releaseLock(identifier);

        Field locksField = manager.getClass().getDeclaredField("locks");
        locksField.setAccessible(true);
        Map locks = (Map) locksField.get(manager);
        assertEquals("Expected lock to be cleaned up", 0, locks.size());
    }

    @Test
    public void testLockOnlyCleanedUpIfNoLocksAreHeld() {
        IdentifierBasedLock manager = new IdentifierBasedLock();

        assertFalse(manager.hasLock(identifier));

        manager.obtainLock(identifier);
        assertTrue(manager.hasLock(identifier));

        manager.obtainLock(identifier);
        assertTrue(manager.hasLock(identifier));

        manager.releaseLock(identifier);
        assertTrue(manager.hasLock(identifier));

        manager.releaseLock(identifier);
        assertFalse(manager.hasLock(identifier));
    }

    @Test(timeout = 5000)
    public void testDeadlockDetected_TwoThreadsInVector() throws InterruptedException {
        final IdentifierBasedLock lock = new IdentifierBasedLock();
        final CountDownLatch starter = new CountDownLatch(1);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        Thread t1 = createThread(lock, starter, cdl, deadlockInThread, "id1", "id2");
        t1.start();
        lock.obtainLock("id2");
        starter.await();
        cdl.countDown();
        try {
            lock.obtainLock("id1");
            assertTrue(deadlockInThread.get());
        } catch (DeadlockException e) {
            // this is ok!
        }
    }

    @Test(timeout = 5000)
    public void testDeadlockDetected_ThreeThreadsInVector() throws InterruptedException {
        final IdentifierBasedLock lock = new IdentifierBasedLock();
        final CountDownLatch starter = new CountDownLatch(3);
        final CountDownLatch cdl = new CountDownLatch(1);
        final AtomicBoolean deadlockInThread = new AtomicBoolean(false);
        Thread t1 = createThread(lock, starter, cdl, deadlockInThread, "id1", "id2");
        Thread t2 = createThread(lock, starter, cdl, deadlockInThread, "id2", "id3");
        Thread t3 = createThread(lock, starter, cdl, deadlockInThread, "id3", "id4");
        t1.start();
        t2.start();
        t3.start();
        lock.obtainLock("id4");
        starter.await();
        cdl.countDown();
        try {
            lock.obtainLock("id1");
            assertTrue(deadlockInThread.get());
        } catch (DeadlockException e) {
            // this is ok!
        }
    }

    private Thread createThread(final IdentifierBasedLock lock, final CountDownLatch starter, final CountDownLatch cdl,
                                final AtomicBoolean deadlockInThread, final String firstLock, final String secondLock) {
        return new Thread(new Runnable() {
            @Override
            public void run() {
                lock.obtainLock(firstLock);
                starter.countDown();
                try {
                    cdl.await();
                    lock.obtainLock(secondLock);
                    lock.releaseLock(secondLock);
                } catch (InterruptedException e) {
                    System.out.println("Thread 1 interrupted");
                } catch (DeadlockException e) {
                    deadlockInThread.set(true);
                } finally {
                    lock.releaseLock(firstLock);
                }
            }
        });
    }
}
