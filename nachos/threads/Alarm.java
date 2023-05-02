package nachos.threads;

import nachos.machine.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
	List<Pair> blockedThreadList = new ArrayList<Pair>();
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

		// Original Code
		// KThread.currentThread().yield();

		//New Implementation
		// There is no requirement that threads start running immediately after waking
		// up; just put them on the ready queue in the timer interrupt handler after
		// they have waited for at least the right amount of time.
		Iterator<Pair> iter = blockedThreadList.iterator();
		while (iter.hasNext()) {
			Pair pair = iter.next();
			if (pair.getwakeTime() < Machine.timer().getTime()) {
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
		// Original Code
		// for now, cheat just to get something working (busy waiting is bad)
		// long wakeTime = Machine.timer().getTime() + x;
		// while (wakeTime > Machine.timer().getTime())
		// 	KThread.yield();

		// while (wakeTime > Machine.timer().getTime()) //busy wait will not give up CPU
		// KThread.yield();

		if (x <= 0) {
			return;
		}
		boolean state = Machine.interrupt().disable();
		long wakeTime = Machine.timer().getTime() + x;
		KThread.currentThread().alarmWakeTime = wakeTime;
		System.out.println("KThread.currentThread().alarmWakeTime: " + KThread.currentThread().alarmWakeTime);
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
		} 
		
		return false;
	}

	// Add Alarm testing code to the Alarm class
	// Implement more test methods here ...

	// Implement more test methods here ...
	public static void alarmTest1_single_Thread() {
		int durations[] = {0, 1, 999, 1000, 1001,  10*10000, 100*100000};
		long t0, t1;
	
		for (int i = 0; i < durations.length; i++) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (durations[i]);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest2-"+ i + " EXPECTED at least " + durations[i] + " ticks");
			System.out.println("alarmTest2-"+ i +"  waited for " + (t1 - t0) + " ticks");
			// System.out.println(d);

			Lib.assertTrue ((t1 - t0) >= durations[i]);
		}

		System.out.println();
	}

	public static void alarmTest2_WAIT_random_duration(int num_Duration, int range_Duration) {

		ArrayList<Integer> durations = new ArrayList<Integer>(); 
		long t0, t1;

		Random rn = new Random();

		for (int i=0; i < num_Duration; i++) {
			Integer duration = new Integer(rn.nextInt(range_Duration));
			durations.add(duration);
		}

		Iterator<Integer> it_durations = durations.iterator();

		int i = 0;

		while (it_durations.hasNext()) {
			Integer currDuration = it_durations.next();
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (currDuration);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest3_duration_random-"+ i + " EXPECTED at least " + currDuration.intValue() + " ticks");
			System.out.println("alarmTest3_duration_random-"+ i +"  waited for " + (t1 - t0) + " ticks");
			Lib.assertTrue ((t1 - t0) >= currDuration.intValue());
			i++;
		}

		System.out.println();
	
	}

	public static void alarmTest3_single_Thread_WAIT_long_duration(){
		int durations[] = {2147483647};

		// int durations[] = {0, 1, 999, 1001, 2147483647};
		long t0, t1;
	
		for (int i = 0; i < durations.length; i++) {
			t0 = Machine.timer().getTime();
			ThreadedKernel.alarm.waitUntil (durations[i]);
			t1 = Machine.timer().getTime();
			System.out.println("alarmTest4-"+ i + " EXPECTED at least " + durations[i] + " ticks");
			System.out.println("alarmTest4-"+ i +"  waited for " + (t1 - t0) + " ticks");
			// System.out.println(d);

			Lib.assertTrue ((t1 - t0) >= durations[i]);
		}

		System.out.println();
	}

	public static void alarmTest4_Thread_cancel(int numKThread, int numDuration, int range_Duration){

		ArrayList<KThread> thread_arrlist1 = new ArrayList<KThread>();

		for (int i=0; i<numKThread; i++) {
			KThread thread = new KThread();
			thread_arrlist1.add(thread);
		}

		ArrayList<Integer> durations = new ArrayList<Integer>(); 
		
		Random rn = new Random();

		for (int i=0; i < numDuration; i++) {
			Integer duration = new Integer(100+rn.nextInt(range_Duration));
			durations.add(duration);
		}
		
		Iterator<Integer> it_durations = durations.iterator();
		Iterator<KThread> it_thread_arrlist1 =  thread_arrlist1.iterator();
		Integer currDuration;
		KThread currThread;

		while (it_durations.hasNext()) {

			currDuration = it_durations.next();



			if (!it_thread_arrlist1.hasNext()) {
				it_thread_arrlist1 = thread_arrlist1.iterator();

			}

			currThread = it_thread_arrlist1.next();

			boolean state = Machine.interrupt().disable();
			ThreadedKernel.alarm.waitUntil(currDuration);

			//System.out.println("alarmTest5-1");
			Machine.interrupt().restore(state);
			
			//System.out.println("alarmTest5-2");
			
			boolean test_cancel = ThreadedKernel.alarm.cancel(currThread);
			
			System.out.println("Cancelled - " + test_cancel);
			
			System.out.println("Removed from alarm queue - " +ThreadedKernel.alarm.removedFromAlarmQueue);



		}


		
	}

	// public static void alarmTest5_WAIT_
		
	

	// Invoke Alarm.selfTest() from ThreadedKernel.selfTest()
	public static void selfTest() {
		// Invoke your other test methods here ...
		alarmTest1_single_Thread();
		alarmTest2_WAIT_random_duration(10, 100000);
		// alarmTest4_WAIT_long_duration();
		alarmTest4_Thread_cancel(5, 6, 10000000);


	}
}
