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
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		//System.out.println("UserProcess.loadSections #1 numpages: "+numPages);
		//System.out.println("UserProcess.loadSections #1.5 numpages: "+numPages);


		pageTable = new TranslationEntry[numPages];
		// initial pageTable with numPages
		for (int i = 0; i < numPages; i++) {
			//System.out.println("UserProcess.loadSections #1 ppn: "+ppn);

			// In project 3, according to Task 1-1, set it to be INVALID
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
			//System.out.println("UserProcess.loadSections #2 pageTable[i].vpn: " + pageTable[i].vpn);
		}
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		super.unloadSections();
	}

	private void handlePageFault(int faultAddr) {
		VMKernel.IVTLock.acquire();
		int vpn = Processor.pageFromAddress(faultAddr);
		int ppn;
		VMKernel.freePhysicalPageLock.acquire();//protect freePhysical page list
		//evict and swapping 
		if (VMKernel.freePhysicalPages.isEmpty()) { // if no more free pages
			//run clock algorithm to find victim page;
			ppn = evict();
		} else {
			ppn = VMKernel.freePhysicalPages.removeFirst();
		}
		VMKernel.freePhysicalPageLock.release();

		
		int sectionPageNum = 0;
		CoffSection sectionToLoad = null;
		//find the corresponding seciton
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			for (int i = 0; i < section.getLength(); i++) {
				int currvpn = section.getFirstVPN() + i;
				if (currvpn == vpn) {
					sectionToLoad = section;
					sectionPageNum = i;
				}
			}
		}
		
		//has been swapped out before
		if (pageTable[vpn].ppn != -1) {//swap from file
			//swapin
			int spn = pageTable[vpn].ppn;
			VMKernel.swap.read(spn*pageSize, Machine.processor().getMemory(), ppn, pageSize);
			VMKernel.swapTrackerLock.acquire();
			VMKernel.swapTracker.add(spn);
			VMKernel.swapTrackerLock.release();
		}
		else {
			// still cannot find the section, meaning that it is stack or argument page
			if (sectionToLoad == null) {
				//zero fill
				byte[] data = new byte[pageSize];
				// ----------------------------------Need to make sure this is correct zero fill --------------------------------
				System.arraycopy(data, 0, Machine.processor().getMemory(), ppn, pageSize);
			} else {
				// load from section
				sectionToLoad.loadPage(sectionPageNum, ppn);
			}
		}

		//set pageTable
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		pageTable[vpn].used = true;
		pageTable[vpn].readOnly = sectionToLoad.isReadOnly();
		// --------------------------Not sure whether is true or false -------------------------------------------------------
		pageTable[vpn].dirty = true;
		
		//set inverted pageTable
		VMKernel.invertedPT[ppn].te = new TranslationEntry(vpn, ppn, true, sectionToLoad.isReadOnly(), true, true);
		VMKernel.invertedPT[ppn].Vprocess = this;
		// --------------------------Not sure whether should set it to true -------------------------------------------------------
		VMKernel.pinLock.acquire();
		VMKernel.invertedPT[ppn].isPinned = true;
		VMKernel.pinnedPageNum ++;
		VMKernel.pinLock.release();
		
		VMKernel.IVTLock.release();

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


	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {

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


		while (numBytesLeft  > 0 && currVaddr < numPages * pageSize) {
			currVpn = Processor.pageFromAddress(currVaddr);
			currVpnOffset = Processor.offsetFromAddress(currVaddr);
			currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;


			if (currVpn < 0 || currVpn >= pageTable.length) {
				return numBytesCopied;
			}

			if (!pageTable[currVpn].valid) {
				//***********************Should this be Vaddr?
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

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
    
		// System.out.println("UserProcess.writeVirtualMemory #3");
		Lib.assertTrue(offset >= 0 && length >= 0 
			&& offset + length <= data.length);

		//System.out.println("UserProcess.writeVirtualMemory #2");
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

			//System.out.println("UserProcess.writeVirtualMemory #4 currVaddr: " + currVaddr);
			// if (!pageTable[currVpn].valid || pageTable[currVpn].readOnly) {
			currVpn = Processor.pageFromAddress(currVaddr);

			if (pageTable[currVpn].readOnly) {
				return numBytesCopied;
			}

			if (!pageTable[currVpn].valid) {
				//***********************Should this be Vaddr?
				handlePageFault(currVaddr);
			}


			//System.out.println("UserProcess.writeVirtualMemory #5");
			currVpnOffset = Processor.offsetFromAddress(currVaddr);
			currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;


		
			//System.out.println("writeVirtualMemory#6 memory.length: " + memory.length);
			// pageSize - currVpnOffset does NOT have to -1
			currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			// System.out.println("writeVirtualMemory#6 currNumToCopy: " + currNumToCopy);
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
			default:
				super.handleException(cause);
				break;
		}
	}

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final char dbgVM = 'v';
}
