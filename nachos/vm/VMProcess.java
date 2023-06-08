package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		super.saveState();
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	@Override
	protected boolean loadSections() {

		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(-1, -1, false, false, false, false);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	@Override
	protected void unloadSections() {
		super.unloadSections();
	}

	private void handlePageFault(int faultAddr) {
		int vpn = Processor.pageFromAddress(faultAddr);
		System.out.println(vpn);
		int ppn = VMKernel.freePhysicalPages.removeFirst();
		int paddr = Processor.makeAddress(ppn, 0);

		//set pageTable
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		//pageTable[vpn].used = false;

		//find the corresponding seciton
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if(section.getFirstVPN() <= vpn) {
				for (int i = 0; i < section.getLength(); i++) {
					int currvpn = section.getFirstVPN() + i;
					if (currvpn == vpn) {
						pageTable[vpn].readOnly = section.isReadOnly();
						section.loadPage(i, ppn);
						return;
					}
				}
			}
		}
		//zero fill
		byte[] data = new byte[pageSize]/**
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
		ThreadedKernel.fileSystem.remove("swap");
		super.terminate();
	}
		}
		byte[] memory = Machine.processor().getMemory();
		System.arraycopy(data, 0, memory, paddr, pageSize);
	}

	private int evict() {
		//when there is no page to evict, keep waiting 
		while (VMKernel.pinnedPageNum == VMKernel.invertedPT.length) {
			VMKernel.pinCondition.sleep();
		}

		// run clock algorithm
		int toEvict;
		while(true) {
			VMKernel.victim = (VMKernel.victim + 1) % VMKernel.invertedPT.length;
			if (VMKernel.invertedPT[VMKernel.victim].isPinned) {
				continue;
			}

			if (VMKernel.invertedPT[VMKernel.victim].te.used) {
				VMKernel.invertedPT[VMKernel.victim].te.used = false;
			}
			else {
				break;
			}
		}
		toEvict = VMKernel.victim;
		VMKernel.victim = (VMKernel.victim + 1) % VMKernel.invertedPT.length;
		int vpn = VMKernel.invertedPT[toEvict].te.vpn;



		VMProcess processThatLosePage = VMKernel.invertedPT[toEvict].Vprocess;
		TranslationEntry processTE = processThatLosePage.pageTable[vpn];
		if (processTE.dirty == true) {
			VMKernel.swapTrackerLock.acquire();
			int spn;
			//swapout
			if (VMKernel.swapTracker.isEmpty()) {
				//need to grow swap file
				spn = VMKernel.swapFIleCounter;
				VMKernel.swapFIleCounter++;
			} else {
				spn = VMKernel.swapTracker.removeFirst();
			}
			VMKernel.swap.write(spn * pageSize, Machine.processor().getMemory(), processTE.ppn, pageSize);
			processTE.ppn = spn;
			VMKernel.swapTrackerLock.release();
		} else {
			processTE.ppn = -1;
		}
		processTE.valid = false;

		return toEvict;
	}

	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length) {
			return 0;
		}

		int numBytesLeft = length;    
		int currVaddr = vaddr;
		int currDataOffset = offset;
		int currVpn = 0;
		int currVpnOffset = 0;
		int currPhysAddr = 0;
		int currNumToCopy = 0;
		int numBytesCopied = 0;


		while (numBytesLeft  > 0 && currVaddr < numPages * pageSize) {
			currVpn = Processor.pageFromAddress(currVaddr);
			currVpnOffset = Processor.offsetFromAddress(currVaddr);
			currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;

			if (currVpn < 0 || currVpn >= pageTable.length) {
				return numBytesCopied;
			}
			
			if (!pageTable[currVpn].valid) {
				System.out.println("enter the page fault process");
				handlePageFault(currVaddr);
			}
			
			currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			System.arraycopy(memory, currPhysAddr , data, currDataOffset, currNumToCopy);
			numBytesCopied  += currNumToCopy;
			numBytesLeft -= currNumToCopy;
			currDataOffset += currNumToCopy;
			currVaddr+= currNumToCopy;
		}

		return numBytesCopied;
	}

	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 
			&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0 || vaddr >= memory.length) {
			return 0;
		}

		int numBytesLeft = length;    
		int currVaddr = vaddr;
		int currDataOffset = offset;
		int currVpn = 0;
		int currVpnOffset = 0;
		int currPhysAddr = 0;
		int currNumToCopy = 0;
		int numBytesCopied = 0;

		while (numBytesLeft > 0 && currVaddr < numPages * pageSize) {
			System.out.println("write looping...");
			// if (!pageTable[currVpn].valid || pageTable[currVpn].readOnly) {
			currVpn = Processor.pageFromAddress(currVaddr);

			if (pageTable[currVpn].readOnly) {
				return numBytesCopied;
			}

			if (!pageTable[currVpn].valid) {
				System.out.println("enter write Virtual Memory pagefault");
				handlePageFault(currVaddr);
			}

			currVpnOffset = Processor.offsetFromAddress(currVaddr);
			currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;

			currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			if (currPhysAddr + currNumToCopy>= memory.length) {
				return numBytesCopied;
			}

			System.arraycopy(data, currDataOffset, memory, currPhysAddr, currNumToCopy);

			numBytesCopied += currNumToCopy;
			numBytesLeft -= currNumToCopy;
			currDataOffset += currNumToCopy;

			currVaddr += currNumToCopy;
		}
	
		//    System.out.println("UserProcess.writeVirtualMemory #8");
		return numBytesCopied;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
			case Processor.exceptionPageFault:
				int badAddr = processor.readRegister(Processor.regBadVAddr);
				handlePageFault(badAddr);
				break;
			default:
				super.handleException(cause);
				break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
