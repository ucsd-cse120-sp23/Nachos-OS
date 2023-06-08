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
		// initial pageTable with numPages
		for (int i = 0; i < numPages; i++) {

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
	

	private void handlePageFault(int faultAddr) {
		int vpn = Processor.pageFromAddress(faultAddr);
		//System.out.println(vpn);
		int ppn = VMKernel.freePhysicalPages.removeFirst();

		//set pageTable
		pageTable[vpn].valid = true;
		pageTable[vpn].ppn = ppn;
		//pageTable[vpn].used = false;

		//find the corresponding seciton
		for (int s = 0; s < coff.getNumSections(); s++) {
			System.out.println("here");
			CoffSection section = coff.getSection(s);
			if(section.getFirstVPN() <= vpn) {
				for (int i = 0; i < section.getLength(); i++) {
					int currvpn = section.getFirstVPN() + i;
					if (currvpn == vpn) {
						pageTable[vpn].readOnly = section.isReadOnly();
						section.loadPage(i, ppn);
						System.out.println("entered");
						return;
					}
				}
			}
		}
		//zero fill
		byte[] data = new byte[pageSize];
		System.out.println("Zero fill start");
		System.arraycopy(data, 0, Machine.processor().getMemory(), Processor.makeAddress(ppn, 0), pageSize);
		System.out.println("Zero fill end");
	}

	/**
	 * Handle a user exception. Called by <tt>VMKernel.exceptionHandler()</tt>
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
