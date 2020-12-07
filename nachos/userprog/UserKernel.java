package nachos.userprog;

import nachos.machine.Coff;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.SynchList;
import nachos.threads.ThreadedKernel;

import java.util.HashMap;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
    /**
     * Allocate a new user kernel.
     */
    public UserKernel() {
        super();
    }

    /**
     * Initialize this kernel. Creates a synchronized console and sets the
     * processor's exception handler.
     */
    public void initialize(String[] args) {
        super.initialize(args);

        console = new SynchConsole(Machine.console());

        allProcesses = new HashMap<>();//初始化全局进程表
        processesLock = new Lock();//初始化访问用户进程表的锁

        //空闲帧表
        freePages=new SynchList();
        for(int i=0;i<Machine.processor().getNumPhysPages();i++) {
            freePages.add(i);
        }

        Machine.processor().setExceptionHandler(() -> exceptionHandler());
    }

    /**
     * Test the console device.
     */
    public void selfTest() {
        super.selfTest();

//        System.out.println("Testing the console device. Typed characters");
//        System.out.println("will be echoed until q is typed.");
//
//        char c;
//
//        do {
//            c = (char) console.readByte(true);
//            console.writeByte(c);
//        }
//        while (c != 'q');
//
//        System.out.println("");
    }

    /**
     * Returns the current process.
     *
     * @return the current process, or <tt>null</tt> if no process is current.
     */
    public static UserProcess currentProcess() {
        if (!(KThread.currentThread() instanceof UThread))
            return null;

        return ((UThread) KThread.currentThread()).process;
    }

    /**
     * The exception handler. This handler is called by the processor whenever
     * a user instruction causes a processor exception.
     *
     * <p>
     * When the exception handler is invoked, interrupts are enabled, and the
     * processor's cause register contains an integer identifying the cause of
     * the exception (see the <tt>exceptionZZZ</tt> constants in the
     * <tt>Processor</tt> class). If the exception involves a bad virtual
     * address (e.g. page fault, TLB miss, read-only, bus error, or address
     * error), the processor's BadVAddr register identifies the virtual address
     * that caused the exception.
     */
    public void exceptionHandler() {
        Lib.assertTrue(KThread.currentThread() instanceof UThread);

        UserProcess process = ((UThread) KThread.currentThread()).process;
        int cause = Machine.processor().readRegister(Processor.regCause);
        process.handleException(cause);
    }

    /**
     * Start running user programs, by creating a process and running a shell
     * program in it. The name of the shell program it must run is returned by
     * <tt>Machine.getShellProgramName()</tt>.
     *
     * @see    nachos.machine.Machine#getShellProgramName
     */
    public void run() {
        super.run();

        UserProcess process = UserProcess.newUserProcess();

        String shellProgram = Machine.getShellProgramName();
        Lib.assertTrue(process.execute(shellProgram, new String[]{}),"运行失败");
        KThread.currentThread().finish();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        super.terminate();
    }

    /**
     * 判断是否该用户进程为最后一个进程，如果是，返回true；否则false
     * @return
     * @author dpf
     */
    public static boolean isLastProcess() {
        return getProcessNum()==1;
    }

    /**
     * 用于获取正在执行的用户进程的数目
     * @return 还未exit的用户进程的数目
     * @author dpf
     */
    public static int getProcessNum() {
        int pNum;
        processesLock.acquire();//获得锁
        pNum = allProcesses.size();
        processesLock.release();//释放锁
        return pNum;
    }

    /**
     * 增加一个用户进程需要调用的函数，将该进程加入到全局的进程表中
     * @author dpf
     */
    public static void addOneProcess(int pid,UserProcess uProcess) {
        processesLock.acquire();
        Lib.assertTrue(!allProcesses.containsKey(pid));//确认该pid并不在全局进程表中存在
        allProcesses.put(pid, uProcess);//加入到全局的进程表
        processesLock.release();
    }

    /**
     * exit一个用户进程需要调用的函数，将该进程从全局的进程表中删除
     * @param pid
     * @author dpf
     */
    public static void exitOneProcess(int pid) {
        processesLock.acquire();
        allProcesses.remove(pid);
        processesLock.release();
    }

    /**
     * 根据pid获取一个用户进程
     * @param pid
     * @return
     * @author dpf
     */
    public static UserProcess getUserProcess(int pid) {
        return allProcesses.get(pid);
    }
    /**
     * 从空闲页链表中获取一页物理的页号
     * @param pid 申请页的进程的进程号
     * @param vPageNum 申请页的进程将这一页用于虚拟页页号
     * @author dpf
     */
    public Integer getOneFreePage(int pid,int vPageNum) {
        return (Integer)freePages.pop();
    }

    /**
     * 释放一页物理页到空闲页表中
     * @param ppn 物理页号
     * @author dpf
     */
    public void releaseOnePage(int ppn) {
        freePages.add(ppn);
    }


    /**
     * Globally accessible reference to the synchronized console.
     */
    public static SynchConsole console;

    // dummy variables to make javac smarter
    private static Coff dummy1 = null;

    private static SynchList freePages;
    /**
     * 访问正在执行的用户进程的锁
     * @author dpf
     */
    private static Lock processesLock;
    /**
     * 储存所有的进程，根据pid找到对应的UserProcess
     * @author dpf
     */
    private static HashMap<Integer, UserProcess> allProcesses;

}
