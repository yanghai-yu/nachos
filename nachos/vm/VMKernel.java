package nachos.vm;

import nachos.machine.Machine;
import nachos.machine.OpenFile;
import nachos.machine.Processor;
import nachos.threads.Lock;
import nachos.threads.SynchList;
import nachos.userprog.UserKernel;

import java.util.HashMap;
import java.util.Random;

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
    @Override
    public void initialize(String[] args) {
        super.initialize(args);
        invertedPageTable = new InvertedTranslationEntry[Machine.processor().getNumPhysPages()];
        invertedPageTableLock = new Lock();

        /* 实验三问题二 */
        swapSpaceFile = UserKernel.fileSystem.open("SwapSpace.bin", true);//初始化的时候，新开一个交换区文件
        freeSwapSpacePage = new SynchList();//刚开始可以不往里面写空闲页，有需要的时候直接往后造一个空闲页并给出即可，反正文件向后是无穷大的
        swapSpacePageTableHashMap = new HashMap<>();//初始化交换区页表
        swapSpaceLock = new Lock();//初始化交换区访问锁
    }

    /**
     * Test this kernel.
     */
    @Override
    public void selfTest() {
        super.selfTest();
    }

    /**
     * Start running user programs.
     */
    @Override
    public void run() {
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    @Override
    public void terminate() {
        System.out.println(test);
        //close and delete swapFile
        swapSpaceFile.close();
        fileSystem.remove("SwapSpace.bin");
        super.terminate();
    }
private static int test = 0;
    @Override
    /**
     * 从空闲页链表中获取一页物理的页号
     *
     * @param vPageNum 申请页的进程将这一页用于虚拟页页号
     */
    public Integer getOneFreePage(int pid, int vPageNum) {

//        int freePageNum = super.getOneFreePage(pid, vPageNum);//申请一页空闲页
        /**
         * @cn 第二问修改
         */
        Integer freePageNum = super.getOneFreePage(pid, vPageNum);//申请一页空闲页

        if (freePageNum == null) {
            invertedPageTableLock.acquire();
            //判断没有空闲页了，选择一个牺牲者
            test++;
            freePageNum = chooseByClock();
//            freePageNum = chooseByRandom();
            InvertedTranslationEntry invertedTranslationEntry = invertedPageTable[freePageNum];
            invertedPageTable[freePageNum] = null;
            invertedPageTableLock.release();

            int toSwapPid = invertedTranslationEntry.pid;
            int toSwapVpn = invertedTranslationEntry.vpn;
            VMProcess theProcess = (VMProcess) getUserProcess(invertedTranslationEntry.pid);//准备换出的页所属的进程

            boolean isDirty = theProcess.swapOut(invertedTranslationEntry.vpn, pid == toSwapPid);//通知这个线程它的这一页将要被换出去
            if (isDirty) {
                //如果是脏的就需要重写回去
                int swapPpn = swapSpacePageTableHashMap.get(toSwapPid + ":" + toSwapVpn);//确定要切换出去的页在swap中的位置
                swapSpaceFile.write(swapPpn * pageSize,
                        Machine.processor().getMemory(),
                        freePageNum * pageSize,
                        pageSize);
            }

        }
        return freePageNum;
    }


    private int chooseByClock() {
        int freePageNum = nextInvertedToCheck;
        boolean find = false;
        while (!find) {
            if (invertedPageTable[nextInvertedToCheck] != null) {
                InvertedTranslationEntry translationEntry = invertedPageTable[nextInvertedToCheck];
                boolean used = ((VMProcess) getUserProcess(translationEntry.pid)).checkUsed(translationEntry.vpn);
                if (!used) {
                    freePageNum = nextInvertedToCheck;
                    find = true;
                }
            }
            nextInvertedToCheck = (nextInvertedToCheck + 1) % Machine.processor().getNumPhysPages();
        }
        return freePageNum;
    }

    private int chooseByRandom() {
        Random random = new Random();
        int num = random.nextInt(invertedPageTable.length);
        while (invertedPageTable[num] == null) {
            num = random.nextInt(invertedPageTable.length);
        }
        return num;
    }


    @Override
    /** 释放一页物理页到空闲页表中 */
    public void releaseOnePage(int ppn) {
        invertedPageTableLock.acquire();
//        invertedPageTable.remove(ppn);//将该物理页使用信息删除
        invertedPageTable[ppn] = null;
        invertedPageTableLock.release();

        super.releaseOnePage(ppn);//将该页物理页作为空闲页添加到空闲物理页表中
    }

    /**
     * 在将页正式换入内存后调用
     *
     * @param invertedPageNum 反向页表号
     * @param pid             进程号
     * @param vPageNum        进程的虚拟页号
     */
    public void recordOneInvertedPageUsage(int invertedPageNum, int pid, int vPageNum) {
        invertedPageTableLock.acquire();
        invertedPageTable[invertedPageNum] = new InvertedTranslationEntry(pid, vPageNum);//将物理页使用情况记录下来
        invertedPageTableLock.release();
    }

    /**
     * @param ppn
     * @return
     */
    public InvertedTranslationEntry getInvertedEntryAt(int ppn) {
        return invertedPageTable[ppn];
    }

    /**
     * @return 交换空间文件
     */
    public static OpenFile getSwapSpaceFile() {
        return swapSpaceFile;//TODO 这样写会不会不太好，要不要加锁
    }

    /**
     * 获得已经分配给这个进程虚拟页的交换空间位置
     *
     * @param pid 进程号
     * @param vpn 虚拟页号
     * @return 返回对应的交换空间的页号
     */
    public static Integer getSwapPageOf(int pid, int vpn) {
        return swapSpacePageTableHashMap.get(pid + ":" + vpn);
    }

    /**
     * 为一个进程的虚拟页分配一个交换空间
     * 如果已经开辟的交换空间中已经没有空闲的，那就增加
     *
     * @param pid 进程号
     * @param vpn 虚拟页号
     * @return 对应的交换空间页号
     */
    public static Integer getOneFreeSwapPage(int pid, int vpn) {
        swapSpaceLock.acquire();
        Integer swapPpn = (Integer) freeSwapSpacePage.pop();
        if (swapPpn == null) {//先查看是否有空闲的交换区页
            swapPpn = swapSpacePageNum++;//如果没有则新增一页返回
        }
        swapSpacePageTableHashMap.put(pid + ":" + vpn, swapPpn);
        swapSpaceLock.release();
        return swapPpn;
    }

    /**
     * 释放一个交换空间，即放到free里
     *
     * @param freePage 交换空间页号
     */
    public static void releaseOneSwapPage(Integer freePage) {
        swapSpaceLock.acquire();
        freeSwapSpacePage.add(freePage);
        swapSpaceLock.release();
    }

    /**
     * 释放进程虚拟页对应的交换空间
     *
     * @param pid 进程号
     * @param vpn 虚拟页号
     */
    public static void releaseOneSwapPage(int pid, int vpn) {
        int freePage = swapSpacePageTableHashMap.get(pid + ":" + vpn);
        releaseOneSwapPage(freePage);
    }

    // dummy variables to make javac smarter
    private static VMProcess dummy1 = null;

    private static final char dbgVM = 'v';

    //实验三 反向页表：用于记录已经分配出去的物理页归属于哪一个进程并且作为哪一个虚拟页使用
    private static InvertedTranslationEntry[] invertedPageTable;

    //维护一个环形队列，用于时钟算法。直接利用反向页表，只要记下一个索引就好了
    private static int nextInvertedToCheck = 0;

    private static Lock invertedPageTableLock;

    /**
     * 交换空间文件，用于存储修改过但是要从物理内存换出的页面,所有进程都可以访问，因此是静态变量
     */
    private static OpenFile swapSpaceFile;
    /**
     * 用于保存交换空间中的空闲页
     */
    private static SynchList freeSwapSpacePage;
    /**
     * 保存交换空间大小
     */
    private static int swapSpacePageNum = 0;
    /**
     * 用于利用进程号和虚拟页号查找在交换空间的页号的交换空间页表
     * key:"pid:vpn" //为了简单起见,我直接利用进程号+:+虚拟页号拼成一个字符串作为查找在交换空间文件中页的位置的标志
     * value:虚拟页在交换空间的位置，用页号标出
     */
    private static HashMap<String, Integer> swapSpacePageTableHashMap;
    /**
     * 用于访问交换区各个对象的锁（swapSpaceFile，freeSwapSpacePage，swapSpacePageTableHashMap）
     * 在访问这一些资源之前需要获得该锁以进行同步
     */
    private static Lock swapSpaceLock;
    /**
     * 大概是用来做交换空间文件分页大小
     */
    private static final int pageSize = Processor.pageSize;

}
