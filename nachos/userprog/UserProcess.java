package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.EOFException;
import java.io.FileDescriptor;
import java.nio.Buffer;
import java.util.HashMap;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 * 
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 * 
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
		pageTable[i] = new TranslationEntry(i, i, true, false, false, false);


		this.fileDescriptors = new OpenFile[MAX_NUM_FILE]; // FIXME
		this.fileDescriptors[0] = UserKernel.console.openForReading();
		this.fileDescriptors[1] = UserKernel.console.openForWriting();
		this.PID = globalPID++; 
	}
// allocate PID for every new process created
	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		String name = Machine.getProcessClassName();

		// If Lib.constructObject is used, it quickly runs out
		// of file descriptors and throws an exception in
		// createClassLoader. Hack around it by hard-coding
		// creating new processes of the appropriate type.

		if (name.equals("nachos.userprog.UserProcess")) {
			return new UserProcess();
		} else if (name.equals("nachos.vm.VMProcess")) {
			return new VMProcess();
		} else {
			return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
		}
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		thread = new UThread(this);
		thread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 *         found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		// Lib.assertTrue(offset >= 0 && length >= 0
		// 		&& offset + length <= data.length);

		// byte[] memory = Machine.processor().getMemory();

		// int amount = 0;
		// // for now, just assume that virtual addresses equal physical addresses
		// if (vaddr < 0 || vaddr >= memory.length)
		// 	return 0;

		// // while (length > 0) {
		// // 	int vpn = Processor.pageFromAddress(vaddr);
		// // 	int vpnOffset = Processor.offsetFromAddress(vaddr);
		// // 	if (vpn < 0 || vpn >= pageTable.length) {
		// // 		return 0;
		// // 	}
		// // 	int phyAddr = pageTable[vpn].ppn * pageSize + vpnOffset;
		// // 	pageTable[vpn].used = true; // TODO not sure!!

		// // 	int minLength = Math.min(length, pageSize - vpnOffset);
		// // 	amount += minLength;
		// // 	vaddr += minLength;
		// // 	offset += minLength; // FIXME: what happend if the offset pass the pageSize
		// // 	System.arraycopy(memory, phyAddr, data, offset, minLength);
		// // 	length -= minLength;
		// // }

		// amount = Math.min(length, memory.length - vaddr);
		// System.arraycopy(memory, vaddr, data, offset, amount);

		// return amount;
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(memory, vaddr, data, offset, amount);

		return amount;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= memory.length)
			return 0;

		int amount = 0;
		amount = Math.min(length, memory.length - vaddr);
		System.arraycopy(data, offset, memory, vaddr, amount);
		// while (length > 0) {
		// 	int vpn = Processor.pageFromAddress(vaddr);
		// 	int vpnOffset = Processor.offsetFromAddress(vaddr);
		// 	if (vpn < 0 || vpn >= pageTable.length) {
		// 		return amount;
		// 	}

		// 	if (pageTable[vpn].readOnly) {
		// 		return amount;
		// 	}

		// 	int phyAddr = pageTable[vpn].ppn * pageSize + vpnOffset;
		// 	int minLength = Math.min(length, pageSize - vpnOffset);

		// 	pageTable[vpn].used = true;
		// 	pageTable[vpn].dirty = true;

		// 	amount += minLength;
		// 	vaddr += minLength;
		// 	offset += minLength; // TODO: check
		// 	System.arraycopy(data, offset, memory, phyAddr, minLength);

		// 	length -= minLength;
		// }

		return amount;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
		// if (numPages > UserKernel.freePhysicalPages.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		// lock.acquire(); // TODO: check the usage of lock
		// pageTable = new TranslationEntry[numPages];
		// // initial pageTable with numPages
		// for (int i = 0; i < numPages; i++) {
		// 	int ppn = UserKernel.freePhysicalPages.removeFirst();
		// 	pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		// }

		

		// // load sections
		// for (int s = 0; s < coff.getNumSections(); s++) {
		// 	CoffSection section = coff.getSection(s);

		// 	// Lib.debug(dbgProcess, "\tinitializing " + sectihandleExecon.getName()
		// 	// 		+ " section (" + section.getLength() + " pages)");

		// 	for (int i = 0; i < section.getLength(); i++) {
		// 		int vpn = section.getFirstVPN() + i;

		// 		// pageTable[count] = new TranslationEntry(count, ppn, true,
		// 		// section.isReadOnly(), false, false);
		// 		// for now, just assume virtual addresses=physical addresses
		// 		// // section.loadPage(i, vpn);
		// 		section.loadPage(i, pageTable[vpn].ppn); // TODO check
		// 		//count++;
		// 		pageTable[i].vpn = vpn;
		// 		pageTable[i].readOnly = section.isReadOnly();
		// 		section.loadPage(i, pageTable[vpn].ppn);

		// 	}
		// }
		// lock.release();
		// load sections
		
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess, "\tinitializing " + section.getName()
					+ " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, vpn);
			}
		}
	
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// TODO free the pages associated with this process
		// lock.acquire();
		// for (int i = 0; i < numPages; i++) {
		// 	UserKernel.freePhysicalPages.add(pageTable[i].ppn);
		// }
		// lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the create() system call.
	 * Attempt to open the named disk file, creating it if it does not exist,
	 * and return a file descriptor that can be used to access the file. If
	 * the file already exists, creat truncates it.
	 *
	 * Note that creat() can only be used to create files on disk; creat() will
	 * never return a file descriptor referring to a stream.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
	// private int handleCreate(int vaName) {
	// String fileName = readVirtualMemoryString(vaName, 256);
	// return -1;
	// }

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {
		if(this.PID!=0){ //TODO: check for root process (will this be its pid)?
			return -1;
		}
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	/**
	 * Handle the exit() system call.
	 */
	private int handleExit(int status) {
		// Do not remove this call to the autoGrader...
		Machine.autoGrader().finishingCurrentProcess(status);
		// ...and leave it as the top of handleExit so that we
		// can grade your implementation.

		Lib.debug(dbgProcess, "UserProcess.handleExit (" + status + ")");
		// for now, unconditionally terminate with just one process

		//Kernel.kernel.terminate(); //commented out by me

		//return 0; //commented out by me


		////////////////// PART 3 BELOW: 


		 for(int i = 0; i<fileDescriptors.length; i++){
		 	if(fileDescriptors[i] != null){
		 		handleClose(i);
				fileDescriptors[i].close();
			  fileDescriptors[i] = null;
		 	} 
		 } 
		unloadSections(); //free memory used by process
			if(status==0){
				statusMap.put(this.PID, status);
			}
			else if (status!=0){
				statusMap.put(this.PID, null);
			}
			if(this.parent!=null){
				map.remove(this.parent.PID); //remove child from map that accesses all the child by parent pid (remove child from the parent's children map essentially)
				this.parent = null;
			}
			for(UserProcess child: map.values()){ //is this structure ideal? perhaps Map<Integer, List<UserProcess>> map = new HashMap<>(); would be better 
				child.parent = null; //remove the parenthood of the child if it has children
			} 
			childMap.clear();
			if(this.PID != 0){//or this ? : if(!(map.isEmpty())){
				thread.finish(); 
			}
			
				Machine.halt();
			
			if (status!=0) { 
				return 0; 
			} 
			else { 
				return status; 
			}

	}

	private int handleExec(int name, int argc, int argv) {

		String filename = readVirtualMemoryString(name, MAX_FILE_NAME_LENGTH);
		if(filename == null || filename.length() == 0){
			return -1;
		}

		if((!filename.endsWith(".coff"))){ //should I impl. upper/lower case check?
			return -1;
		}

		if(argc < 0 || argv < 0){
			return -1;
		}

		//TODO need lock ?

		String[] args = new String[argc];
		byte[] bytes = new byte[4]; //TODO: ask
		for(int i = 0; i<argc; i++){
			int readBytes = readVirtualMemory(argv+4*i, bytes);
			if(readBytes != 4){
				return -1;
			}
			int arg_addr = Lib.bytesToInt(bytes, 0); //converts byte array to int
			 //reading in the arguments from argv and putting in String array
			args[i] = readVirtualMemoryString(arg_addr,MAX_FILE_NAME_LENGTH); //set to maximum possible size of a string? not sure if this is correct
			if(args[i] == null || args[i].isEmpty()){
				return -1;
			}
		}
		

		UserProcess child = UserProcess.newUserProcess();
		child.parent = this; 
		if(!(child.execute(filename, args))){
			return -1; //returns if program unable to be loaded
		}

		//child.parent = this; 

		//is forking a solution here?
		//not sure the child process needs to be or should be added to this process's children; if so we may need to use a Hashmap right?
		//UPDATE need a hashmap or appropriate data structure to track processes, as per tips, we need to track the children

		//do we need to incr. pid here instead?

		map.put(this.PID, child); //here's a sample of it for now
		childMap.put(child.PID, child);

		return child.PID; //need to update current user process class to account for PID tracking

	}


	private int handleJoin(int childPID, int status_addr) {
		if(childPID < 0 || status_addr < 0){
			return -1;
		} 
		UserProcess child = childMap.get(childPID);//check if this is childMap or regular map 
		//this depends on seeting up the hashmap system. We can simply call get here if it is a hashmap
		if(child == null || child.parent == this || child.parent == null){ //remove == this?
			return -1; //can go through hashmap to find child process, if doesn't find return error
		}


		child.thread.join();//USE JOIN IMPLEMENTED IN PART1 WITH THE HELP OF THREAD IN USERPROCESS.EXECUTE
		Integer childPIDD = Integer.valueOf(childPID);
		if(statusMap.get(childPIDD) == null){
		 	return 0; //unable to retrieve status of process
		 }



		//byte[] statuses = Lib.bytesFromInt(status_addr);
		byte[] statuses = Lib.bytesFromInt(statusMap.get(childPIDD));
		int written = writeVirtualMemory(status_addr, statuses);
		if(written!=4){
			return -1; //error handling case: check if necessary in exec and join; what if not 4? 
		}

		child.parent = null; 
		childMap.remove(childPID); //rWhen the current process resumes, it disowns the child process?
		statusMap.remove(childPID);


		return 1;

		//return 0;
	}

	// private int handleCreate(int name){
	// String name1 = readVirtualMemoryString(name, MAX_FILE_NAME_LENGTH);
	// if(name1 == null){
	// return -1;
	// }
	// OpenFile file = ThreadedKernel.fileSystem.open(name1, true);

	// if(file == null){ return -1;}

	// int filedesc = fileDescriptor(); //not sure how to get fd yet, I think it may
	// depend on impl. of others
	// //is checkname method req here?
	// if(filedesc==-1){
	// file.close();
	// return -1;
	// }

	// fdtable[filedesc] = file;
	// return filedesc;
	// }

	/**
	 * Handle the creat() system call.
	 */
	private int handleCreate(int name) {

		if (name < 0) {
			return -1;
		}

		String fileName = this.readVirtualMemoryString(name, MAX_FILE_NAME_LENGTH);
		if (fileName == null) {
			return -1;
		}
		// Attempt to open the named disk file, creating it if it does not exist
		OpenFile disk_file = ThreadedKernel.fileSystem.open(fileName, true);
		if (disk_file == null) {
			return -1;
		}

		int fd = -1;
		for (int i = 0; i < fileDescriptors.length; i++) {
			if (fileDescriptors[i] == null) {
				fileDescriptors[i] = disk_file;
				fd = i;
				return fd;
			}
		}
		return -1;
	}

	private int handleOpen(int name) {

		if (name < 0) {
			return -1;
		}

		String fileName = this.readVirtualMemoryString(name, 256);
		if (fileName == null) {
			return -1;
		}
		// Attempt to open the named disk file, creating it if it does not exist
		OpenFile disk_file = ThreadedKernel.fileSystem.open(fileName, false);
		if (disk_file == null) {
			return -1;
		}

		int fd = -1;
		for (int i = 0; i < fileDescriptors.length; i++) {
			if (fileDescriptors[i] == null) {
				fileDescriptors[i] = disk_file;
				fd = i;
				return fd;
			}
		}
		return -1;

	}

	/**
	 * Handle the open() system call.
	 */

	/**
	 * Handle the read() system call.
	 */
	private int handleRead(int fileDescriptor, int bufAddr, int count) {

		if (bufAddr < 0 || count < 0) {
			return -1;
		}
		if (count == 0) {
			return 0;
		}

		if (!(fileDescriptor >= 0 && fileDescriptor < fileDescriptors.length)) {
			return -1;
		}

		OpenFile file = fileDescriptors[fileDescriptor];
		if (file == null) {
			return -1;
		}

		byte[] buffer = new byte[count];
		// FIXME: what is the maxium size of a buffer in the address? may need a while
		// loop

		int byteRead = file.read(buffer, 0, count); // should the buffer smaller than count???
		if (byteRead == -1) {
			return -1;
		}

		// number of bytes read is number of bytes written to bufAddr
		int numByteRead = writeVirtualMemory(bufAddr, buffer);

		// ************************************
		// should we check byteTransferred == 0?
		// TO DO:
		// if (byteTransfered == 0) {
		// return -1;
		// }

		// refers to syscall.h. Number of bytes read CAN be smaller than
		// count
		return numByteRead;
	}

	/**
	 * Handle the write() system call.
	 */
	private int handleWrite(int fileDescriptor, int bufferAddr, int count) {
		if (bufferAddr < 0 || count < 0) {
			return -1;
		}
		if (count == 0) {
			return 0;
		}

		if (!(fileDescriptor >= 0 && fileDescriptor < fileDescriptors.length)) {
			return -1;
		}

		OpenFile file = fileDescriptors[fileDescriptor];
		if (file == null) {
			return -1;
		}

		// Check if part of the buffer is invalid
		if (!validUserAddress(bufferAddr, count)) {
			return -1;
		}

		byte[] buffer = new byte[count];
		int bytesRead = readVirtualMemory(bufferAddr, buffer, 0, count);
		if (bytesRead == 0) {
			// zero indicates nothing was written
			return 0;
		}

		int byteTransfered = file.write(buffer, 0, count);

		// it is an error if this number is smaller than the number of bytes requested
		// On error, -1 is returned, and the new file position is undefined
		if (byteTransfered <= bytesRead || byteTransfered == -1) {
			fileDescriptors[fileDescriptor] = null;
			return -1;
		}
		return byteTransfered;
	}

	/**
	 * Handle the close() system call.
	 */
	private int handleClose(int fileDescriptor) {
		if (!(fileDescriptor >= 0 && fileDescriptor < fileDescriptors.length)) {
			return -1;
		}
		OpenFile file = fileDescriptors[fileDescriptor];
		if (file == null) {
			return -1;
		}
		file.close();
		fileDescriptors[fileDescriptor] = null;
		return 0;
	}

	/**
	 * Handle the unlink() system call.
	 */
	private int handleUnlink(int name) {
		if (name < 0) {
			return -1;
		}
		// Delete a file from the file system.
		String fileName = this.readVirtualMemoryString(name, MAX_FILE_NAME_LENGTH);
		if (fileName == null) {
			return -1;
		}
		// this process will ask the file system to remove the file,
		// but the file will not actually be deleted by the file system until all other
		// processes are done with the file
		boolean removedFile = ThreadedKernel.fileSystem.remove(fileName);
		return removedFile ? 0 : -1;
	}

	private boolean validUserAddress(int startAddr, int length) {
		if (startAddr < 0 || length < 0) {
			return false;
		}

		int startVPN = Processor.pageFromAddress(startAddr);
		int endVPN = Processor.pageFromAddress(startAddr + length - 1);

		return startVPN >= 0 && endVPN < pageTable.length;

	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 * 
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {

		switch (syscall) {
			case syscallHalt:
				return handleHalt();

			// changed REMEMBER TO DO SANITY CHECKS FOR EVERY HANDLER!!!!!!!!!!!!!!!!
			case syscallExit:
				// a0: status
				return handleExit(a0);
			case syscallExec:
				// a0: name
				// a1: argc
				// a2: argv
				return handleExec(a0, a1, a2);
			case syscallJoin:
				// a0: 
				// a1: int status
				return handleJoin(a0, a1);
			case syscallCreate:
				// a0: name
				return handleCreate(a0);
			case syscallOpen:
				// a0: name
				return handleOpen(a0);
			case syscallRead:
				// a0: fileDescriptor
				// a1: bufAddr
				// a2: count
				return handleRead(a0, a1, a2);
			case syscallWrite:
				// a0: fileDescriptor
				// a1: bufAddr
				// a2: count
				return handleWrite(a0, a1, a2);
			case syscallClose:
				// a0: fileDescriptor
				return handleClose(a0);
			case syscallUnlink:
				// a0: name
				return handleUnlink(a0);

			default:
				Lib.debug(dbgProcess, "Unknown syscall " + syscall);
				Lib.assertNotReached("Unknown system call!");
		}

		return 0;

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
			case Processor.exceptionSyscall:
				int result = handleSyscall(processor.readRegister(Processor.regV0),
						processor.readRegister(Processor.regA0),
						processor.readRegister(Processor.regA1),
						processor.readRegister(Processor.regA2),
						processor.readRegister(Processor.regA3));
				processor.writeRegister(Processor.regV0, result);
				processor.advancePC();
				break;

			default:
				Lib.debug(dbgProcess, "Unexpected exception: "
						+ Processor.exceptionNames[cause]);
				System.out.println(Processor.exceptionNames[cause]);
				Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	protected OpenFile[] fileDescriptors;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	/** The thread that executes the user-level program. */
	protected UThread thread;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private static final int MAX_NUM_FILE = 16;

	private static final int MAX_FILE_NAME_LENGTH = 256;

	public static int globalPID = 0;

	private int PID;

	// added lock
	private Lock lock = new Lock();

	HashMap<Integer, UserProcess> map = new HashMap<Integer, UserProcess>(); //parent pid gives children is Map<Integer, List<UserProcess>> map = new HashMap<>(); better?

	HashMap<Integer, UserProcess> childMap = new HashMap<Integer, UserProcess>(); //process PID with content

	HashMap<Integer, Integer> statusMap = new HashMap<Integer, Integer>(); //statusMap

	private UserProcess parent = null;


}