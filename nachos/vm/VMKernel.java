package nachos.vm;

import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);
		swap = ThreadedKernel.fileSystem.open("swap", true);
		swapTracker = new LinkedList<Integer>();
		invertedPT = new IVT[Machine.processor().getNumPhysPages()];
		for (int i = 0; i < invertedPT.length; i++) {
			invertedPT[i] = new IVT(new TranslationEntry(-1, i, false, false, false, false), null, false);
		}
		swapTrackerLock = new Lock();
		pinLock = new Lock();
		pinnedPageNum = 0;
		IVTLock = new Lock();
		pinCondition = new Condition(pinLock);
		victim = 0;
		freePhysicalPageLock = new Lock();
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swap.close();
		ThreadedKernel.fileSystem.remove("swap");
		super.terminate();
	}

	// //clock algorithm
	// public static int findVictim() {
	// 	while (frames[victim].pageTable[ppnToVPN[victim]].used == true) {
	// 		// unset used bit 
	// 		frames[victim].pageTable[ppnToVPN[victim]].used = false; 
	// 		victim = (victim + 1) % frames.length;
	// 	}
	// 	int toEvict = victim;
	// 	// move to next so that the next run of clock algorithm starts from the nexrt position in the clock cycle
	// 	victim = (victim + 1) % frames.length; 
	// 	return toEvict;
	// }

	

	static class IVT {
		public TranslationEntry te;
		public VMProcess Vprocess;
		public boolean isPinned;

		public IVT(TranslationEntry te, VMProcess Vprocess, boolean pinned) {
			this.te = te;
			this.Vprocess = Vprocess;
			this.isPinned = pinned;
		}
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static OpenFile swap = null;

	//linkedlist is used to track the gap of swap file
	public static LinkedList<Integer> swapTracker = null;
	//counter is to track the size of the swap file
	public static int swapFIleCounter = 0;
	public static Lock swapTrackerLock;

	public static IVT[] invertedPT;
	public static Condition pinCondition;
	public static Lock pinLock;
	public static int pinnedPageNum;
	public static Lock IVTLock;
	
	//use for clock algorithm
	public static int victim;
	public static Lock freePhysicalPageLock;
}
