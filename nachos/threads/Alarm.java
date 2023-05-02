package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	List<Pair> blockedThreadList = new ArrayList<>();
	boolean removedFromAlarmQueue = false;

	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		// There is no requirement that threads start running immediately after waking
		// up; just put them on the ready queue in the timer interrupt handler after
		// they have waited for at least the right amount of time.
		Iterator<Pair> iter = blockedThreadList.iterator();
		while (iter.hasNext()) {
			Pair pair = iter.next();
			if (pair.getwakeTime() <= Machine.timer().getTime()) {
				pair.getCurrentThread().ready();
				iter.remove();
				removedFromAlarmQueue = true;
			}
		}
		// for (Pair pair : blockedThreadList) {
		// if (pair.getwakeTime() > Machine.timer().getTime()) {
		// pair.getCurrentThread().ready();
		// }
		// }

		KThread.currentThread().yield();
	}

	static class Pair {
		private KThread currentThread;
		private long wakeTime;

		public Pair(KThread currentThread, long wakeTime) {
			this.currentThread = currentThread;
			this.wakeTime = wakeTime;
		}

		public KThread getCurrentThread() {
			return currentThread;
		}

		public long getwakeTime() {
			return wakeTime;
		}
	}

	    // Add Alarm testing code to the Alarm class


    // Implement more test methods here ...
	public static void alarmTest1() {
		int durations[] = {1000, 10*1000, 100*1000};
		long t0, t1;
	
		for (int d : durations) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (d);
			t1 = Machine.timer().getTime();
			System.out.println ("alarmTest1: waited for " + (t1 - t0) + " ticks");
			System.out.println(d);
		}
		}

    // Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
    public static void selfTest() {
	alarmTest1();

	// Invoke your other test methods here ...
    }

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
		// long wakeTime = Machine.timer().getTime() + x;
		// while (wakeTime > Machine.timer().getTime()) //busy wait will not give up CPU
		// KThread.yield();

		if (x <= 0) {
			return;
		}
		boolean state = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		KThread.currentThread().alarmWakeTime = wakeTime;
		blockedThreadList.add(new Pair(KThread.currentThread(), wakeTime));
		KThread.currentThread().sleep();
		Machine.interrupt().restore(state);
	}

	/**
	 * Cancel any timer set by <i>thread</i>, effectively waking
	 * up the thread immediately (placing it in the scheduler
	 * ready set) and returning true. If <i>thread</i> has no
	 * timer set, return false.
	 * 
	 * <p>
	 * 
	 * @param thread the thread whose timer should be cancelled.
	 */
	public boolean cancel(KThread thread) { 
		if (thread.alarmWakeTime != null){
			boolean state = Machine.interrupt().disable();
			thread.alarmWakeTime = null;
			thread.ready();
			Machine.interrupt().restore(state);
			return true;
		} else{
			return false;
		}
	}
}
