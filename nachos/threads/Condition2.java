package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;
import java.util.LinkedList;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 * 
 * <p>
 * You must implement this.
 * 
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 * 
	 * @param conditionLock the lock associated with this condition variable.
	 *                      The current thread must hold this lock whenever it uses
	 *                      <tt>sleep()</tt>,
	 *                      <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		this.waitQueue = new ArrayList<KThread>();
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		
		boolean intStatus = Machine.interrupt().disable();
		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		KThread.currentThread().sleep();
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		if (waitQueue.size() > 0) {
			KThread kthread = waitQueue.remove(0);
			boolean cancelStatus = ThreadedKernel.alarm.cancel(kthread);
			if (!cancelStatus) {
				kthread.ready();
			}
		}

		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		while (waitQueue.size() > 0) {
			KThread kthread = waitQueue.remove(0);
			kthread.ready();
		}
		Machine.interrupt().restore(intStatus);
	}

	/**
	 * Atomically release the associated lock and go to sleep on
	 * this condition variable until either (1) another thread
	 * wakes it using <tt>wake()</tt>, or (2) the specified
	 * <i>timeout</i> elapses. The current thread must hold the
	 * associated lock. The thread will automatically reacquire
	 * the lock before <tt>sleep()</tt> returns.
	 */
	public void sleepFor(long timeout) {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		boolean intStatus = Machine.interrupt().disable();
		conditionLock.release();
		waitQueue.add(KThread.currentThread());
		
		ThreadedKernel.alarm.waitUntil(timeout);
		if (ThreadedKernel.alarm.removedFromAlarmQueue == true) {
			waitQueue.remove(KThread.currentThread());
		} 
		//TODO: do we need alarm.cancel in wakeAll() too?
		conditionLock.acquire();
		Machine.interrupt().restore(intStatus);
	}

	private Lock conditionLock;
	private ArrayList<KThread> waitQueue;


	// Test for condition2
	// Place Condition2 testing code in the Condition2 class.

    // Example of the "interlock" pattern where two threads strictly
    // alternate their execution with each other using a condition
    // variable.  (Also see the slide showing this pattern at the end
    // of Lecture 6.)


	private static void sleepForTest1 () {
		Lock lock = new Lock();
		Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		// no other thread will wake us up, so we should time out
		cv.sleepFor(2000);
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}

	private static void sleepForTest2 () {
		final Lock lock = new Lock();
		final Condition2 cv = new Condition2(lock);
	
		lock.acquire();
		long t0 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() + " sleeping");
		KThread child1 = new KThread( new Runnable () {
			public void run() {
				System.out.println("I (heart) Nachos!");
			}
			});
		child1.setName("child1").fork();

		KThread child2 = new KThread( new Runnable () {
			public void run() {
				Lock lock2 = new Lock();
				Condition2 cv2 = new Condition2(lock2);
				cv2.sleepFor(2000000);
			}
			});
		child2.setName("child2").fork();



		//TODO how to implement wake()
		cv.sleepFor(2000);

		
		long t1 = Machine.timer().getTime();
		System.out.println (KThread.currentThread().getName() +
					" woke up, slept for " + (t1 - t0) + " ticks");
		lock.release();
	}
	


    private static class InterlockTest {
        private static Lock lock;
        private static Condition2 cv;

        private static class Interlocker implements Runnable {
            public void run () {
                lock.acquire();
                for (int i = 0; i < 10; i++) {
                    System.out.println(KThread.currentThread().getName());
                    cv.wake();   // signal
                    cv.sleep();  // wait
                }
                lock.release();
            }
        }

        public InterlockTest () {
            lock = new Lock();
            cv = new Condition2(lock);

            KThread ping = new KThread(new Interlocker());
            ping.setName("ping");
            KThread pong = new KThread(new Interlocker());
            pong.setName("pong");

            ping.fork();
            pong.fork();

            // We need to wait for ping to finish, and the proper way
            // to do so is to join on ping.  (Note that, when ping is
            // done, pong is sleeping on the condition variable; if we
            // were also to join on pong, we would block forever.)
            // For this to work, join must be implemented.  If you
            // have not implemented join yet, then comment out the
            // call to join and instead uncomment the loop with
            // yields; the loop has the same effect, but is a kludgy
            // way to do it.
            ping.join();
            // for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
        }
    }

	private static void cvTest5() {
        final Lock lock = new Lock();
        // final Condition empty = new Condition(lock);
        final Condition2 empty = new Condition2(lock);
        final LinkedList<Integer> list = new LinkedList<>();

        KThread consumer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    while(list.isEmpty()){
                        empty.sleep();
                    }
                    Lib.assertTrue(list.size() == 5, "List should have 5 values.");
                    while(!list.isEmpty()) {
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                        System.out.println("Removed " + list.removeFirst());
                    }
                    lock.release();
                }
            });

        KThread producer = new KThread( new Runnable () {
                public void run() {
                    lock.acquire();
                    for (int i = 0; i < 5; i++) {
                        list.add(i);
                        System.out.println("Added " + i);
                        // context swith for the fun of it
                        KThread.currentThread().yield();
                    }
                    empty.wake();
                    lock.release();
                }
            });

        consumer.setName("Consumer");
        producer.setName("Producer");
        consumer.fork();
        producer.fork();

        // We need to wait for the consumer and producer to finish,
        // and the proper way to do so is to join on them.  For this
        // to work, join must be implemented.  If you have not
        // implemented join yet, then comment out the calls to join
        // and instead uncomment the loop with yield; the loop has the
        // same effect, but is a kludgy way to do it.
        consumer.join();
        producer.join();
        //for (int i = 0; i < 50; i++) { KThread.currentThread().yield(); }
    }

    // Invoke Condition2.selfTest() from ThreadedKernel.selfTest()

    // public static void selfTest() {
    //     new InterlockTest();
    // }


	public static void selfTest() {
		sleepForTest1();
		cvTest5();

		InterlockTest InterlockTest1 = new InterlockTest();
		
	}
}
