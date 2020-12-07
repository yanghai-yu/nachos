package nachos.userprog;

import nachos.machine.*;
import nachos.threads.Condition;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.ThreadedKernel;

import java.io.EOFException;
import java.util.HashSet;

/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
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
        /**
         *在构造器中对pid进行初始化，对nextPid自增
         * 值得注意的是应当用锁来保护这个过程
         */
        staticResourceLock.acquire();
        pid = nextPid++;
        staticResourceLock.release();
        /**
         * openfiles的0和1应该分别是stdin and stdout
         */
        openFiles[0] = UserKernel.console.openForReading();
        openFiles[1] = UserKernel.console.openForWriting();

        this.childProcesses = new HashSet<Integer>();//用于储存该进程下的子进程pid
        this.processExitStatus = NOT_EXIT;//初始化该进程退出状态为未退出状态
        this.processStatusLock = new Lock();//初始化访问进程状态锁
        this.joinCondition = new Condition(this.processStatusLock);//初始化访问进程状态的条件变量

        int numPhysPages = Machine.processor().getNumPhysPages();
        //修改
//        pageTable = new TranslationEntry[numPhysPages];
//        for (int i = 0; i < numPhysPages; i++)
//            pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
    }

    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
        return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
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
        UserKernel.addOneProcess(this.pid, this);//向UserKernel报告新加了一个用户进程
        new UThread(this).setName(name).fork();

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
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param vaddr     the starting virtual address of the null-terminated
     *                  string.
     * @param maxLength the maximum number of characters in the string,
     *                  not including the null terminator.
     * @return the string read, or <tt>null</tt> if no null terminator was
     * found.
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
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to
     *               the array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
                                 int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, assume that virtual addresses not equal physical addresses
        if (vaddr < 0 || vaddr >= numPages * Processor.pageSize)
            return 0;

        int vpn = Processor.pageFromAddress(vaddr);
        int amount = 0;//已经读取的字节数
        while (amount != length) {
            //如果进入循环说明已经读取的字节数小于length,但如果此时vpn已经超过页数,说明length+vaddr的长度超出进程的内存边界,需要立即返回
            if (vpn >= numPages) break;
            //当前页的页内偏移，只有包含起始地址的page的页内偏移不为0
            int pageOffset = 0;
            if (amount == 0) pageOffset = Processor.offsetFromAddress(vaddr);
            //在当前页应读取的字节数
            int pageLength = Processor.pageSize - pageOffset;
            if (length - amount < pageLength) pageLength = length - amount;
            //得到当前虚拟页的实际物理页数
            int ppn = pageTable[vpn].ppn;
            //得到实际在内存内的物理地址
            int paddr = Processor.makeAddress(ppn, pageOffset);

            System.arraycopy(memory, paddr, data, offset, pageLength);
            offset += pageLength;
            amount += pageLength;
            vpn++;
        }

        return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
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
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to
     *               virtual memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
                                  int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, assume that virtual addresses not equal physical addresses
        if (vaddr < 0 || vaddr >= numPages * Processor.pageSize)
            return 0;

        int vpn = Processor.pageFromAddress(vaddr);
        int amount = 0;//已经写入的字节数
        //进入循环说明已经写入的字节数小于length
        while (amount != length) {
            //如果此时的vpn已经超过页数,说明length+vaddr的长度超出进程的内存边界,需要立即返回
            if (vpn >= numPages) break;
            //如果此时的vpn标识为只读,则不应该改写它,需要立即返回
            if (pageTable[vpn].readOnly) break;
            //当前页的页内偏移，只有包含起始地址的page的页内偏移不为0
            int pageOffset = 0;
            if (amount == 0) pageOffset = Processor.offsetFromAddress(vaddr);
            //在当前页应写入的字节数
            int pageLength = Processor.pageSize - pageOffset;
            if (length - amount < pageLength) pageLength = length - amount;
            //得到当前虚拟页的实际物理页数
            int ppn = pageTable[vpn].ppn;
            //得到实际在内存内的物理地址
            int paddr = Processor.makeAddress(ppn, pageOffset);

            System.arraycopy(data, offset, memory, paddr, pageLength);
            offset += pageLength;
            amount += pageLength;
            vpn++;
        }

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
            Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
                    argv[i].length);
            stringOffset += argv[i].length;
            Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[]{0}) == 1);
            stringOffset += 1;
        }

        return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return <tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
        if (numPages > Machine.processor().getNumPhysPages()) {
            coff.close();
            Lib.debug(dbgProcess, "\tinsufficient physical memory");
            return false;
        }
        // 修改
        pageTable = new TranslationEntry[numPages];
        // 这个index用来指示pageTable从哪开始还是null。因为前面的load()方法已经验证过section,stack,arg所占的pages，所以只需要在每次分配entry后自加即可。
        int pageIndex = 0;
        // load sections
        for (int s = 0; s < coff.getNumSections(); s++) {
            CoffSection section = coff.getSection(s);

            Lib.debug(dbgProcess,
                    "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

            for (int i = 0; i < section.getLength(); i++) {
                int vpn = section.getFirstVPN() + i;
                // 为上面的vpn分配物理页,若没有空闲的物理页可用则返回异常
                Integer ppn = ((UserKernel)Kernel.kernel).getOneFreePage(pid, vpn);//实验三dpf修改
                if (ppn == null) {
                    releaseRes();// 释放资源
                    return false;
                }
                // 若有空闲的物理页,则进行页表更新
                pageTable[pageIndex++] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
                // for now,virtual addresses!=physical addresses
                section.loadPage(i, ppn);
            }
        }
        // 为剩下的stack和参数所占的页数分配内存
        while (pageIndex < numPages) {
            // 分配物理页,若没有空闲的物理页可用则返回异常
            Integer ppn = ((UserKernel)Kernel.kernel).getOneFreePage(pid, pageIndex);
            if (ppn == null) {
                releaseRes();// 释放资源
                return false;
            }
            // 若有空闲的物理页,则进行页表更新
            pageTable[pageIndex] = new TranslationEntry(pageIndex, ppn, true, false, false, false);
            pageIndex++;
        }
        return true;
    }


    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
    }

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
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
     * 为了确保只有root（第一个进程）才能调用某些方法，为每个用户进程声明一个pid,来标识一个进程
     * pid应当在UserProcess的构造器中赋值
     * 为了给pid赋值,在UserProcess中应当维护一个静态变量,称为nextPid
     * 每当产生一个UserProcess,nextPid应当将自身的值赋给pid,然后自增
     * 考虑一个问题,nextPid是静态资源,同一时间应当只能有一个进程访问或修改它
     * 于是定义一个锁来保证静态资源的同步，注意：锁应当也是静态的，否则没有作用
     */
    protected int pid;
    private static int nextPid = 0;
    private static Lock staticResourceLock = new Lock();
    /**
     * 题目中要求,一个进程最多能打开16个文件,且每个文件都有唯一的标识符
     * 用一个数组存储来存储这最多16个文件，那么它在数组中的位置就可以当做它唯一的标识符
     */
    private OpenFile[] openFiles = new OpenFile[16];

    /**
     * Handle the halt() system call.
     */
    private int handleHalt() {
        /**
         * 对于不是root的进程，应当返回
         */
        if (pid != 0) return 0;
        Machine.halt();

        Lib.assertNotReached("Machine.halt() did not halt machine!");
        return 0;
    }

    /**
     * 功能：创建文件
     *
     * @param filePtr 文件名的首地址
     * @return 文件状态描述符
     * 首先读虚拟内存，取得文件名，接着判断有无可用的文件描述符
     * 有的话调用文件系统的创建文件的方法，创建文件
     * 返回文件描述符
     * 否则，返回-1
     */
    private int handleCreate(int filePtr) {
        //判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(filePtr)) {
            return terminate();
        }
        //从虚拟内存中获取文件名，文件名长度应小于等于256字节,若大于则返回null
        String fileName = readVirtualMemoryString(filePtr, 256);
        if (fileName == null) return -1;
        //判断文件是否已经打开，若已经打开，则返回其文件描述符
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] != null) {
                if (openFiles[i].getName().equals(fileName)) {
                    return i;
                }
            }
        }
        //文件获取文件描述符
        int fileDes = getFileDescriptor();
        //没有空闲的文件描述符，返回-1
        if (fileDes == -1) {
            return -1;
        } else {
            /* 调用文件系统的open方法，使其参数create为true，创建文件
             * open()返回null是因为文件名不符合规范或者到达StubFileSystem的最大打开文件数,这时应该报错
             */
            OpenFile file = UserKernel.fileSystem.open(fileName, true);
            if (file == null) return -1;
            //记录文件
            openFiles[fileDes] = file;
            return fileDes;
        }
    }

    /**
     * 功能：打开文件
     *
     * @param filePtr 文件名首地址
     * @return 文件描述符
     * 根据文件名，打开文件，与handleCreat类似，只不过改了一下调用文件系统的open方法时的参数
     */
    private int handleOpen(int filePtr) {
        //判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(filePtr)) {
            return terminate();
        }
        //从虚拟内存中获取文件名，文件名长度应<=256字节,若大于则返回null
        String fileName = readVirtualMemoryString(filePtr, 256);
        if (fileName == null) return -1;
        //判断文件是否已经打开，若已经打开，则返回其文件描述符
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] != null) {
                if (openFiles[i].getName().equals(fileName)) {
                    return i;
                }
            }
        }
        //文件获取文件描述符
        int fileDes = getFileDescriptor();
        //没有空闲的文件描述符，返回-1
        if (fileDes == -1) {
            return -1;
        } else {
            /* 调用文件系统的open方法，使其参数create为false，仅打开文件
             * open()返回null是因为文件名不符合规范或者到达StubFileSystem的最大打开文件数,这时应该报错
             */
            OpenFile file = UserKernel.fileSystem.open(fileName, false);
            if (file == null) return -1;
            //记录文件
            openFiles[fileDes] = file;
            return fileDes;
        }
    }

    /**
     * 功能：读取文件
     *
     * @param fd        文件描述符
     * @param bufferPtr 文件内容要存在虚拟内存的首地址
     * @param size      文件内容的大小（字节数组的长度）
     * @return 正常进行返回0, 异常返回-1
     * 通过文件系统的read方法，读取文件的内容，将其写入虚拟内存中
     */
    private int handleRead(int fd, int bufferPtr, int size) {
        //判断文件描述符的合法性
        if (fd < 0 || fd >= 16 || openFiles[fd] == null) {
            return -1;
        }
        //判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(bufferPtr)) {
            return terminate();
        }
        //创建size大小的byte数组，用来存放从文件呢中读取的内容
        byte[] buffer = new byte[size];
        //从文件中读取，内容放到buffer，bytesRead为成功读取的长度
        int bytesRead = openFiles[fd].read(buffer, 0, size);
        //bytesRead为-1表示读取失败，返回-1
        if (bytesRead == -1) return -1;
        //写入虚拟内存，bytesWritten为成功写入的长度
        int bytesWritten = writeVirtualMemory(bufferPtr, buffer, 0, bytesRead);
        //如果写入的长度与读取的长度不一样，这意味着bufferPtr不合法或者，返回-1
        if (bytesWritten != bytesRead) return -1;
        return bytesWritten;
    }

    /**
     * 功能：写入文件
     *
     * @param fd        文件描述符
     * @param bufferPtr 存在虚拟内存中的内容的首地址
     * @param size      内容的大小
     * @return 返回值
     * 从虚拟内存中取得要写入文件的内容，调用文件系统的方法，写入文件
     */
    private int handleWrite(int fd, int bufferPtr, int size) {
        //判断文件描述符的合法性
        if (fd < 0 || fd >= 16 || openFiles[fd] == null) {
            return -1;
        }
        //判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(bufferPtr)) {
            return terminate();
        }
        //新建长度为size的byte数组，来存储要写入的内容
        byte[] buffer = new byte[size];
        //从虚拟内存中读取要写入的内容，bytesRead是成功读取的长度
        int bytesRead = readVirtualMemory(bufferPtr, buffer);
        //如果bytesRead为-1说明bufferPtr地址非法，返回-1
        if (bytesRead == -1) return -1;
        //调用文件系统的方法，向文件中写入内容，bytesWritten为成功写入的字节数
        int bytesWritten = openFiles[fd].write(buffer, 0, bytesRead);
        //若write()返回-1，说明写入文件失败(可能由于磁盘已满,细节由java的file类处理)
        if (bytesWritten == -1) return -1;
        return bytesWritten;
    }

    /**
     * 功能：关闭文件
     *
     * @param fd 文件描述符
     * @return 调用文件的关闭功能，并释放文件描述符
     */
    private int handleClose(int fd) {
        if (fd < 0 || fd >= 16) {
            return -1;
        }
        openFiles[fd].close();
        openFiles[fd] = null;
        return 0;
    }

    /**
     * 功能：删除文件
     *
     * @param filePtr 存储文件名的首地址
     * @return
     */
    private int handleUnlink(int filePtr) {
        //判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(filePtr)) {
            return terminate();
        }
        //从虚拟内存中读取文件名
        String fileName = readVirtualMemoryString(filePtr, 256);
        //文件名为空，返回-1
        if (fileName == null || fileName == "") {
            return -1;
        } else {
            //调用文件系统的remove方法，删除文件
            return UserKernel.fileSystem.remove(fileName) ? 0 : -1;
        }
    }

    /**
     * 判断虚拟内存地址是否合法，保证安全性
     *
     * @param vaddr
     * @return
     */
    protected boolean validAddress(int vaddr) {
        int vpn = Processor.pageFromAddress(vaddr);
        return vpn < numPages && vpn >= 0;
    }

    /**
     * 产生文件描述符，0-15，如果所有的文件描述符都被占用，返回-1
     *
     * @return
     */
    private int getFileDescriptor() {
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] == null)
                return i;
        }
        return -1;
    }

    /**
     * 提取handleExit和terminate共同的部份
     *
     * @author ning
     */
    private void processExit() {
        if (UserKernel.isLastProcess()) {
            //如果是最后一个用户进程，则调用Kernel.kernel.terminate();
            UserKernel.exitOneProcess(this.pid);
            Kernel.kernel.terminate();
        } else {
            //否则不为最后一个用户进程，正在运行进程数-1
            UserKernel.exitOneProcess(this.pid);
            KThread.finish();//完成当前线程
        }
    }

    /**
     * 用于处理系统异常退出
     */
    protected int terminate() {
        this.processStatusLock.acquire();//获得访问自己进程状态的锁

        //在这里释放内存，关闭打开的文件
        this.releaseRes();//进程清除文件

        this.processExitStatus = ABNORMAL_EXIT;//将进程状态设置为非正常退出
        this.exitReturnStatus = -1;//将进程退出状态设置为用户设置的系统退出状态号
        this.joinCondition.wakeAll();//唤醒所有join进来的进程
        this.processStatusLock.release();//释放掉访问自己进程状态的锁
        processExit();
        return -1;
    }


    // 释放文件和内存等资源
    protected void releaseRes() {
        // 释放内存资源
        for (int i = 0; i < numPages; i++) {
            if (pageTable[i] != null && pageTable[i].valid == true) {
                // 如果该页表存在,并且实际分配了物理页,那么就将其释放
                ((UserKernel)Kernel.kernel).releaseOnePage(pageTable[i].ppn);// 将该页表项对应的物理页号释放到空闲页表列表中
            }
        }
        // 释放文件资源
        for (int i = 0; i < openFiles.length; i++) {
            if (openFiles[i] != null) {
                openFiles[i].close();
            }
        }
    }


    /**
     * 进程退出后调用的方法，用于释放进程的内存，关闭进程打开的文件
     *
     * @param status
     * @return status is returned to the parent process as this process's exit status and
     * can be collected using the join syscall. A process exiting normally should          //join怎么collect
     * (but is not required to) set status to 0.
     * exit() never returns.
     * @author dpf
     */
    private int handleExit(int status) {
        this.processStatusLock.acquire();//获得访问自己进程状态的锁

        //在这里释放内存，关闭打开的文件
        this.releaseRes();//进程清除文件

        this.processExitStatus = NORMAL_EXIT;//将进程状态设置为正常退出
        this.exitReturnStatus = status;//将进程退出状态设置为用户设置的系统退出状态号
        this.joinCondition.wakeAll();//唤醒所有join进来的进程
        this.processStatusLock.release();//释放掉访问自己进程状态的锁

        processExit();
        return 0;//一定要返回点啥东西，exit又不会返回，就随便返回了个0
    }

    /**
     * @param nameAddr  可执行文件的名字字符串的首地址
     * @param argc      exec()传递的参数个数
     * @param argvsAddr 参数字符串数组指针首地址
     * @return
     */
    private int handleExec(int nameAddr, int argc, int argvsAddr) {
        Lib.assertTrue(this == UserKernel.currentProcess());//确保该进程是当前进程

        String[] args = new String[argc];//创建参数字符串数组
        String name = readVirtualMemoryString(nameAddr, maxArgStrLen);//读可执行文件的字符串名字
        byte[] argvByte = new byte[4];//一个int大小的byte数组，用来装参数字符串指针
        for (int i = 0; i < argc; i++) {
            //读各个传递参数的字符串
            readVirtualMemory(argvsAddr + 4 * i, argvByte);//读取第i个参数字符串的指针
            int argvPoint = Lib.bytesToInt(argvByte, 0);//将指针由byte数组的形式转化为int形式
            args[i] = readVirtualMemoryString(argvPoint, maxArgStrLen);//根据指针读取相应的字符串
        }

        UserProcess process = UserProcess.newUserProcess();//new出新的UserProcess对象
        if (process.execute(name, args)) {
            //如果执行成功，正常返回，返回子进程进程号
            Lib.assertTrue(!this.childProcesses.contains(process.pid));//确保该子进程号原来并不在父进程子进程列表里面出现

            this.childProcesses.add(process.pid);//将该新的子进程加入到父进程的子进程列表中去
            return process.pid;
        } else {
            //如果失败则返回失败的-1
            return -1;
        }
    }

    /**
     * 系统调用join()
     *
     * @param pid           子进程的进程号
     * @param statusPointer 子进程结束后需要返回给父进程的子进程退出状态存储位置的地址
     * @return
     */
    private int handleJoin(int pid, int statusPointer) {
        Lib.assertTrue(this == UserKernel.currentProcess());//确保该进程是当前进程

        if (!this.childProcesses.contains(pid)) {
            //如果传来的pid不在该进程的子进程列表中，则返回-1
            return -1;
        }

        UserProcess childProcess = UserKernel.getUserProcess(pid);//获取子进程

        childProcess.processStatusLock.acquire();//获取子进程的状态访问锁
        while (childProcess.processExitStatus == NOT_EXIT) {
            //如果该子进程还未结束,那就让当前正在执行的线程(当前进程的线程),sleep到子进程的条件变量中
            childProcess.joinCondition.sleep();//睡在子进程的join条件变量里面
        }

        //醒来之后子进程一定是结束了的
        if (childProcess.processExitStatus == NORMAL_EXIT) {
            //如果是正常退出,则返回1,并且向目标地址写入子进程的退出状态
            byte[] childExitStatusByte = Lib.bytesFromInt(childProcess.exitReturnStatus);//将子进程正常退出状态由int转为byte数组
            writeVirtualMemory(statusPointer, childExitStatusByte);//将子进程正常退出状态写入到statusPointer指向的空间
            if (this.childProcesses.contains(pid)) {
                //如果子进程列表中还存在着该子进程的话，那就把它删除；不在子进程列表的原因是其他线程可能已经删除了
                this.childProcesses.remove(pid);//将子进程从该进程的子进程列表中删除
            }
            childProcess.processStatusLock.release();//释放掉子线程状态访问锁（访问完了）
            return 1;
        } else {
            //否则子进程是非正常退出的,则返回0，并且要求上说子进程退出状态由我决定，那就不写了。
            if (this.childProcesses.contains(pid)) {
                //如果子进程列表中还存在着该子进程的话，那就把它删除；不在子进程列表的原因是其他线程可能已经删除了
                this.childProcesses.remove(pid);//将子进程从该进程的子进程列表中删除
            }
            childProcess.processStatusLock.release();//释放掉子线程状态访问锁（访问完了）
            return 0;
        }
    }

    private static final int
            syscallHalt = 0,
            syscallExit = 1,
            syscallExec = 2,
            syscallJoin = 3,
            syscallCreate = 4,
            syscallOpen = 5,
            syscallRead = 6,
            syscallWrite = 7,
            syscallClose = 8,
            syscallUnlink = 9;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     * 								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
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
            case syscallCreate:
                return handleCreate(a0);
            case syscallOpen:
                return handleOpen(a0);
            case syscallRead:
                return handleRead(a0, a1, a2);
            case syscallWrite:
                return handleWrite(a0, a1, a2);
            case syscallClose:
                return handleClose(a0);
            case syscallUnlink:
                return handleUnlink(a0);
            case syscallExit:
                return handleExit(a0);
            case syscallExec:
                return handleExec(a0, a1, a2);
            case syscallJoin:
                return handleJoin(a0, a1);
            default:
                Lib.debug(dbgProcess, "Unknown syscall " + syscall);
                Lib.assertNotReached("Unknown system call!");
        }
        return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
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
                        processor.readRegister(Processor.regA3)
                );
                processor.writeRegister(Processor.regV0, result);
                processor.advancePC();
                break;
            default:
                Lib.debug(dbgProcess, "Unexpected exception: " +
                        Processor.exceptionNames[cause]);
                Lib.assertNotReached("Unexpected exception");
        }
    }

    /**
     * The program being run by this process.
     */
    protected Coff coff;

    /**
     * This process's page table.
     */
    protected TranslationEntry[] pageTable;
    /**
     * The number of contiguous pages occupied by the program.
     */
    protected int numPages;

    /**
     * The number of pages in the program's stack.
     */
    protected final int stackPages = 8;

    private int initialPC, initialSP;
    private int argc, argv;

    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';

    private HashSet<Integer> childProcesses;//用于储存该用户进程下的子进程pid
    private int processExitStatus;//用于标志该进程状态(notExit,normalExit,abnormalExit)
    private int exitReturnStatus;//用于标志进程退出状态（一定是正常退出return返回的值）
    private Lock processStatusLock;//用于访问进程状态的锁
    private Condition joinCondition;//用于访问进程状态的条件变量
    private static final int maxArgStrLen = 256;//系统调用传递参数最大字符串的长度

    private static final int NOT_EXIT = -1;//进程未退出状态
    private static final int NORMAL_EXIT = 1;//进程正常退出状态
    private static final int ABNORMAL_EXIT = 0;//进程非正常退出状态

}
