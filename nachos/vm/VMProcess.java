package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;
import nachos.vm.VMKernel.IVT;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
		pinSleepLock = new Lock();
		pinCondition = new Condition2(pinSleepLock);
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
		// initial pageTable with numPages
		for (int i = 0; i < numPages; i++) {
			//System.out.println("UserProcess.loadSections #1 ppn: "+ppn);

			// In project 3, according to Task 1-1, set it to be INVALID
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
			//System.out.println("UserProcess.loadSections #2 pageTable[i].vpn: " + pageTable[i].vpn);
		}
		VMKernel.processCountLock.acquire();
		VMKernel.processCount++;
		VMKernel.processCountLock.release();

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {

		super.unloadSections();
	}

	
	
	private void print() {
		System.out.println("starting print page table");
		for (int i = 0; i < pageTable.length; i ++) {
			System.out.println("vpn:" + pageTable[i].vpn + " ppn:" + pageTable[i].ppn + " valid:" + pageTable[i].valid);
		}
		System.out.println("finish printing pageTable");

		System.out.println("starting print Inverted page table");
		for (int i = 0; i < VMKernel.invertedPT.length; i ++) {
			System.out.println("vpn:" +  VMKernel.invertedPT[i].vpn + " ppn:" + i );
		}
		System.out.println("finish printing Inverted pageTable");
	}

	private void handlePageFault(int faultAddr) {
		//System.out.println("entered page fault");
		VMKernel.IVTLock.acquire();
		int vpn = Processor.pageFromAddress(faultAddr);
		int ppn;
		VMKernel.freePhysicalPageLock.acquire();//protect freePhysical page list
		// System.out.println("print before evicting");
		// print();

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

		// if (vpn < 0 || vpn >= pageTable.length) {
		// 	System.out.println("vpn < 0: "+ (vpn < 0));
		// 	System.out.println("vpn >= pageTable.length: "+ (vpn >= pageTable.length));
		// }
		
		//has been swapped out before
		if (pageTable[vpn].ppn != -1) {//swap from file
			// System.out.println("entered swap ---------------------------------------------------------------");
			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[ppn].isPinned = true;
			VMKernel.pinLock.release();
			//swapin
			int spn = pageTable[vpn].ppn;
			int size = pageSize;
			byte[] pageContent = new byte[size];
			int written = 0;
			while (written < size) {
				written += VMKernel.swap.read(spn*size+written, pageContent, written, size-written);
			}
			byte[] mem = Machine.processor().getMemory();
			System.arraycopy(pageContent,0,mem, ppn*size,size);
			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[ppn].isPinned = false;
			VMKernel.pinLock.release();
			//VMKernel.swap.read(spn*pageSize, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);
			VMKernel.swapTrackerLock.acquire();
			VMKernel.swapTracker.add(spn);
			VMKernel.swapTrackerLock.release();
		}
		else {
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
			// still cannot find the section, meaning that it is stack or argument page
			if (sectionToLoad == null) {
				//zero fill
				byte[] data = new byte[pageSize];
				System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);
			} else {
				// load from section
				sectionToLoad.loadPage(sectionPageNum, ppn);
			}
		}

		//set pageTable
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		pageTable[vpn].used = true;
		//set inverted pageTable
		
		VMKernel.invertedPT[ppn].te = pageTable[vpn];
		VMKernel.invertedPT[ppn].vpn = vpn;
		VMKernel.invertedPT[ppn].Vprocess = this;
		
		// System.out.println("print after evicting");
		// print();
		VMKernel.IVTLock.release();

	}

	private int evict() {
		//System.out.println(VMKernel.isPinnedPageNum);
		//System.out.println(VMKernel.invertedPT.length);

		// pinning -----------------------------
		//when there is no page to evict, keep waiting 
		while (VMKernel.pinnedPageNum == VMKernel.invertedPT.length) {
			// System.out.println("waiting");
			pinSleepLock.acquire();
			pinCondition.sleep();
			pinSleepLock.release();
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

		int vpn = VMKernel.invertedPT[toEvict].vpn;
		VMProcess processThatLosePage = VMKernel.invertedPT[toEvict].Vprocess;
		TranslationEntry processTE = processThatLosePage.pageTable[vpn];
		if (processTE.dirty == true) {
			//System.out.println("entered swap in evict ---------------------------------------------------------------");
			
			VMKernel.swapTrackerLock.acquire();
			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[toEvict].isPinned = true;
			VMKernel.pinLock.release();
			int spn;
			//swapout
			if (VMKernel.swapTracker.isEmpty()) {
				//need to grow swap file
				spn = VMKernel.swapFIleCounter;
				VMKernel.swapFIleCounter++;
			} else {
				spn = VMKernel.swapTracker.removeFirst();
			}
			//System.out.println("swap:" + processTE.vpn + "to spn" + spn);
			int size = pageSize;
			byte[] mem = Machine.processor().getMemory();
			
			int written = 0;
			
			while (written < size) {
				written += VMKernel.swap.write(spn*size+written, mem, processTE.ppn*size+written, size-written);
			}
			// VMKernel.swap.write(spn * pageSize, Machine.processor().getMemory(), processTE.ppn, pageSize);
			processTE.ppn = spn;

			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[toEvict].isPinned = false;
			VMKernel.pinLock.release();
			VMKernel.swapTrackerLock.release();
		} 
		else {
			processTE.ppn = -1;
		}
		processTE.valid = false;

		return toEvict;
	}

	@Override
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// //System.out.println("enter read");
		// Lib.assertTrue(offset >= 0 && length >= 0
		// 		&& offset + length <= data.length);

		// byte[] memory = Machine.processor().getMemory();


		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length) {
			
		// 	return 0;
		// }

		// int numBytesLeft = length;    
		// int currVaddr = vaddr;
		// int currDataOffset = offset;
		// int currVpn = 0;
		// int currVpnOffset = 0;
		// int currPhysAddr = 0;
		// int currNumToCopy = 0;
		// int numBytesCopied = 0;


		// while (numBytesLeft  > 0 && currVaddr < numPages * pageSize) {
		// 	currVpn = Processor.pageFromAddress(currVaddr);
		// 	currVpnOffset = Processor.offsetFromAddress(currVaddr);
		// 	currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;
			

		// 	if (currVpn < 0 || currVpn >= pageTable.length) {
			
		// 		return numBytesCopied;
		// 	}

		// 	if (!pageTable[currVpn].valid) {
		// 		//***********************Should this be Vaddr?
		// 		handlePageFault(currVaddr);
				
		// 	}
			

		// 	currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			
		// 	//************************Do we need that check? */
			
		// 	if (currPhysAddr + currNumToCopy>= memory.length) {
		// 		return numBytesCopied;
		// 	}

		// 	System.arraycopy(memory, currPhysAddr , data, currDataOffset, currNumToCopy);
		// 	pageTable[currVpn].used = true;

		// 	numBytesCopied  += currNumToCopy;
		// 	numBytesLeft -= currNumToCopy;
		// 	currDataOffset += currNumToCopy;

		// 	currVaddr+= currNumToCopy;
		// }

		// //System.out.println("Actually Read:" + numBytesCopied);
		// return numBytesCopied;




		// pinning -----------------------------
		//System.out.println("enter read");
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
				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = false;
				VMKernel.pinnedPageNum--;
				pinSleepLock.acquire();
				pinCondition.wake();
				pinSleepLock.release();
				VMKernel.pinLock.release();
				return numBytesCopied;
			}

			if (!pageTable[currVpn].valid) {
				handlePageFault(currVaddr);
				currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;
				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = true;
				VMKernel.pinnedPageNum++;
				VMKernel.pinLock.release();
			}
			else {
				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = true;
				VMKernel.pinnedPageNum++;
				VMKernel.pinLock.release();
			}

			currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			currNumToCopy = Math.min(currNumToCopy, memory.length - currPhysAddr);
			
			System.arraycopy(memory, currPhysAddr , data, currDataOffset, currNumToCopy);

			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = false;
			VMKernel.pinnedPageNum--;
			pinSleepLock.acquire();
			pinCondition.wake();
			pinSleepLock.release();
			VMKernel.pinLock.release();

			numBytesCopied  += currNumToCopy;
			numBytesLeft -= currNumToCopy;
			currDataOffset += currNumToCopy;

			currVaddr+= currNumToCopy;
		}

		//System.out.println("Actually Read:" + numBytesCopied);
		return numBytesCopied;
	}


	@Override
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// Lib.assertTrue(offset >= 0 && length >= 0 
		// 	&& offset + length <= data.length);

		// //System.out.println("UserProcess.writeVirtualMemory #2");
		// byte[] memory = Machine.processor().getMemory();

		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length) { 
		// 	return 0;
		// }

		// int numBytesLeft = length;    
		// int currVaddr = vaddr;
		// int currDataOffset = offset;
		// int currVpn = 0;
		// int currVpnOffset = 0;
		// int currPhysAddr = 0;
		// int currNumToCopy = 0;
		// int numBytesCopied = 0;

		
	
		// while (numBytesLeft > 0 && currVaddr < numPages * pageSize) {

		// 	//System.out.println("UserProcess.writeVirtualMemory #4 currVaddr: " + currVaddr);
		// 	// if (!pageTable[currVpn].valid || pageTable[currVpn].readOnly) {
		// 	currVpn = Processor.pageFromAddress(currVaddr);

		// 	if (pageTable[currVpn].readOnly) {
		// 		return numBytesCopied;
		// 	}

		// 	if (!pageTable[currVpn].valid) {
		// 		//***********************Should this be Vaddr?
		// 		handlePageFault(currVaddr);
		// 	}


		// 	//System.out.println("UserProcess.writeVirtualMemory #5");
		// 	currVpnOffset = Processor.offsetFromAddress(currVaddr);
		// 	currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;


		
		// 	System.out.println("VMProcess.writeVirtualMemory#6 memory.length: " + memory.length);
		// 	// pageSize - currVpnOffset does NOT have to -1
		// 	currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);

		// 	System.out.println("VMProcess.writeVirtualMemory#6 currPhysAddr: " + currPhysAddr);
		// 	System.out.println("VMProcess.writeVirtualMemory#6 currNumToCopy: " + currNumToCopy);
		// 	System.out.println("VMProcess.writeVirtualMemory#6 memory.length: " + currNumToCopy);
		// 	if (currPhysAddr + currNumToCopy>= memory.length) {
		// 		return numBytesCopied;
		// 	}

		// 	System.arraycopy(data, currDataOffset, memory, currPhysAddr, currNumToCopy);
		// 	pageTable[currVpn].used = true;
		// 	pageTable[currVpn].dirty = true;

		// 	numBytesCopied += currNumToCopy;
		// 	numBytesLeft -= currNumToCopy;
		// 	currDataOffset += currNumToCopy;

		// 	currVaddr += currNumToCopy;
		// }
		
		// System.out.println("VMProcess.writeVirtualMemory #8");
		// return numBytesCopied;

		// pinning -----------------------------
		Lib.assertTrue(offset >= 0 && length >= 0 
			&& offset + length <= data.length);

		//System.out.println("UserProcess.writeVirtualMemory #2");
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

		
		while (numBytesLeft > 0 && currVaddr < numPages * pageSize) {

			currVpn = Processor.pageFromAddress(currVaddr);

			if (currVpn <0 || currVpn >= pageTable.length) {
				return numBytesCopied;
			}

			if (pageTable[currVpn].readOnly) {
				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = false;
				VMKernel.pinnedPageNum--;
				pinSleepLock.acquire();
				pinCondition.wake();
				pinSleepLock.release();
				VMKernel.pinLock.release();
				return numBytesCopied;
			}

			if (!pageTable[currVpn].valid) {
				// vaddr = Processor.makeAddress(currVaddr, 0);
				// handlePageFault(vaddr);
				handlePageFault(currVaddr);

				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = true;
				VMKernel.pinnedPageNum++;
				VMKernel.pinLock.release();
			} else {
				VMKernel.pinLock.acquire();
				VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = true;
				VMKernel.pinnedPageNum++;
				VMKernel.pinLock.release();
			}
			
			currVpnOffset = Processor.offsetFromAddress(currVaddr);
			currPhysAddr = pageTable[currVpn].ppn * pageSize + currVpnOffset;
			currNumToCopy = Math.min(numBytesLeft, pageSize - currVpnOffset);
			// System.out.println("writeVirtualMemory#6 currNumToCopy: " + currNumToCopy);
			// if (currPhysAddr + currNumToCopy>= memory.length) {
			// 	VMKernel.pinLock.acquire();
			// 	VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = false;
			// 	VMKernel.pinnedPageNum--;
			// 	pinSleepLock.acquire();
			// 	pinCondition.wake();
			// 	pinSleepLock.release();
			// 	VMKernel.pinLock.release();
			// 	return numBytesCopied;
			// }
			currNumToCopy = Math.min(currNumToCopy, memory.length - currPhysAddr);

			System.arraycopy(data, currDataOffset, memory, currPhysAddr, currNumToCopy);

			VMKernel.pinLock.acquire();
			VMKernel.invertedPT[pageTable[currVpn].ppn].isPinned = false;
			VMKernel.pinnedPageNum--;
			pinSleepLock.acquire();
			pinCondition.wake();
			pinSleepLock.release();
			VMKernel.pinLock.release();


			numBytesCopied += currNumToCopy;
			numBytesLeft -= currNumToCopy;
			currDataOffset += currNumToCopy;

			currVaddr += currNumToCopy;
		}

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

	private static Lock pinSleepLock;
	private static Condition2 pinCondition;
}
