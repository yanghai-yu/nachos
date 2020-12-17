package nachos.vm;

import nachos.machine.*;
import nachos.userprog.UserProcess;

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
        //super.saveState();
        //将TLB中valid==true的页表项全部保存到该进程的页表中，主要是保存used和dirty位
        Processor processor = Machine.processor();
        int tlbSize = processor.getTLBSize();//得到TLB的大小
        //把所有valid==true的页表项写回进程的页表中
        for (int i = 0; i < tlbSize; i++) {
            TranslationEntry tEntry = processor.readTLBEntry(i);
            if (tEntry.valid == true) {
                pageTable[tEntry.vpn].used = tEntry.used;//写回
                pageTable[tEntry.vpn].dirty = tEntry.dirty;
            }
        }
    }


    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
        //super.restoreState();//不再使用UserProcess的restoreState()以更换上进程页表的形式来切换TLB
        //而是通过将所有TLB设置为不可用，然后再从进程页表中读取对应页表项来放入TLB中
        Processor processor = Machine.processor();
        int tlbSize = processor.getTLBSize();//得到TLB的大小
        //把所有的TLB设置为不可用
        for (int i = 0; i < tlbSize; i++) {
            TranslationEntry tEntry = processor.readTLBEntry(i);
            tEntry.valid = false;
            processor.writeTLBEntry(i, tEntry);
        }
    }

    /**
     * 加载某一进程的某一虚拟页到物理内存中
     *
     * @param pid 想要换进来页属于的进程的进程号
     * @param vpn 想要换进来的页的虚拟页号
     */
    public boolean loadOnePage(int pid, int vpn) {
        Integer swapPpn = VMKernel.getSwapPageOf(pid, vpn);// 先找到每个vpn对应的swap地址
        if (swapPpn == null) {
            //给的pid和vpn无效
            return false;
        }
        int saddr = swapPpn * pageSize;//在swap中的地址
        byte[] memory = Machine.processor().getMemory();
        Integer ppn = ((VMKernel) Kernel.kernel).getOneFreePage(pid, vpn);//获取空现页，如果没有空闲的，会换出去一个
        int readNum = VMKernel.getSwapSpaceFile().read(saddr, memory, ppn * pageSize, pageSize);//读到内存
        if (readNum == -1) {
            return false;
        }
        swapIn(vpn, ppn);
        ((VMKernel) Kernel.kernel).recordOneInvertedPageUsage(ppn, pid, vpn);
        return true;
    }

    /**
     * 查看页表项的used情况，并置为false
     *
     * @param vpn
     * @return
     */
    public boolean checkUsed(int vpn) {
        boolean beforeUsed = pageTable[vpn].used;
        pageTable[vpn].used = false;
        return beforeUsed;
    }

    /**
     * 通知process要将vpn换出
     * 更新valid，dirty
     * 如果这一页是属于正在运行的当前进程的，还要判断该页在TLB中是否有副本
     *
     * @param vpn       准备换出的虚拟页号
     * @param isCurrent 是不是当前正在运行的进程
     * @return entry.Dirty表示是否需要把这一页重新写回
     */
    public boolean swapOut(int vpn, boolean isCurrent) {
        TranslationEntry page = pageTable[vpn];
        if (isCurrent) {
            for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
                TranslationEntry tlbEntry = Machine.processor().readTLBEntry(i);
                if (tlbEntry.valid && tlbEntry.vpn == vpn) {
                    page.used = tlbEntry.used;
                    page.dirty = tlbEntry.dirty;
                    tlbEntry.valid = false;
                }
            }
        }

        boolean isDirty = page.dirty;
        page.dirty = false;
        page.valid = false;
        page.ppn = -1;
        return isDirty;
    }


    public void swapIn(int vpn, int ppn) {
        //TODO 如果不是从swap里load，怎么判断是在哪个section的哪一页
        TranslationEntry page = pageTable[vpn];
        page.valid = true;
        page.ppn = ppn;
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     * 修改为lazyLoad,一开始不会分配任何物理页，随后通过一系列pageFault来加载到物理页中
     *
     * @return <tt>true</tt> if successful.
     */
    protected boolean loadSections() {
//        if (numPages > Machine.processor().getNumPhysPages()) {
//            coff.close();
//            Lib.debug(dbgProcess, "\tinsufficient physical memory");
//            return false;
//        }
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
                // 不需要分配实际物理页面,等页错误自然会分配物理页
                pageTable[pageIndex] = new TranslationEntry(vpn, -1, false, section.isReadOnly(), false, false);//ppn随便给了个-1，valid由于没有分配物理页设置为false

                //将程序内容载入buffer
                byte[] buffer = section.loadPageToBuffer(i);
                //获取一个空闲交换区
                Integer swapPpn = VMKernel.getOneFreeSwapPage(pid, pageIndex++);
                writeToSwap(buffer, swapPpn);
            }
        }
        // 为剩下的stack和参数所占的页数分配内存
        while (pageIndex < numPages) {
            // 若有空闲的物理页,则进行页表更新
            pageTable[pageIndex] = new TranslationEntry(pageIndex, -1, false, false, false, false);//ppn随便给了个-1，valid由于没有分配物理页设置为false
            byte[] buffer = new byte[pageSize];
            //获取一个空闲交换区
            Integer swapPpn = VMKernel.getOneFreeSwapPage(pid, pageIndex++);
            writeToSwap(buffer, swapPpn);
        }
        return true;
    }

    private void writeToSwap(byte[] buffer, int swapPpn) {
        Lib.assertTrue(swapPpn >= 0);

        int spaddr = swapPpn * pageSize;
        VMKernel.getSwapSpaceFile().write(spaddr, buffer, 0, pageSize);
    }

    protected void releaseRes() {
        //释放swap表
        for (TranslationEntry page : pageTable) {
            VMKernel.releaseOneSwapPage(pid, page.vpn);
        }
        super.releaseRes();
    }


    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
        super.unloadSections();
    }
    //新增了将页表使用位的修改措施，在读的时候会将被读的页的used置为ture

    /**
     * Transfer data from this process's virtual memory to the specified array. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to read.
     * @param data   the array where the data will be stored.
     * @param offset the first byte to write in the array.
     * @param length the number of bytes to transfer from virtual memory to the
     *               array.
     * @return the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, assume that virtual addresses not equal physical addresses
        if (vaddr < 0 || vaddr >= numPages * Processor.pageSize)
            return 0;

        int vpn = Processor.pageFromAddress(vaddr);
        int amount = 0;// 已经读取的字节数
        while (amount != length) {
            // 如果进入循环说明已经读取的字节数小于length,但如果此时vpn已经超过页数,说明length+vaddr的长度超出进程的内存边界,需要立即返回
            if (vpn >= numPages)
                break;

            /**
             * @cn 当页不在内存中，load进来
             */
            if (!pageTable[vpn].valid)
                loadOnePage(pid, vpn);
            // 当前页的页内偏移，只有包含起始地址的page的页内偏移不为0
            int pageOffset = 0;
            if (amount == 0)
                pageOffset = Processor.offsetFromAddress(vaddr);
            // 在当前页应读取的字节数
            int pageLength = Processor.pageSize - pageOffset;
            if (length - amount < pageLength)
                pageLength = length - amount;
            // 得到当前虚拟页的实际物理页数
            int ppn = pageTable[vpn].ppn;
            // 得到实际在内存内的物理地址
            int paddr = Processor.makeAddress(ppn, pageOffset);

            System.arraycopy(memory, paddr, data, offset, pageLength);

            /*
             * 实验三新增的更新 @dpf
             */
            pageTable[vpn].used = true;
            //同时也要保证TLB中的页表项也要被修改，否则的话在TLB写回的时候可能会出现问题
            Processor processor = Machine.processor();
            int tlbSize = processor.getTLBSize();//得到TLB的大小
            for (int i = 0; i < tlbSize; i++) {
                //不仅要将页表中页表项更新，还要将TLB中的副本更新
                TranslationEntry tEntry = processor.readTLBEntry(i);
                if (tEntry.valid == true && tEntry.vpn == vpn) {
                    tEntry.used = true;
                    processor.writeTLBEntry(i, tEntry);//写回TLB
                }
            }

            offset += pageLength;
            amount += pageLength;
            vpn++;
        }

        return amount;
    }

//新增将页表使用位和修改位置为的操作，在写的时候会把used置为true并且把dirty置为true

    /**
     * Transfer data from the specified array to this process's virtual memory. This
     * method handles address translation details. This method must <i>not</i>
     * destroy the current process if an error occurs, but instead should return the
     * number of bytes successfully copied (or zero if no data could be copied).
     *
     * @param vaddr  the first byte of virtual memory to write.
     * @param data   the array containing the data to transfer.
     * @param offset the first byte to transfer from the array.
     * @param length the number of bytes to transfer from the array to virtual
     *               memory.
     * @return the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
        Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

        byte[] memory = Machine.processor().getMemory();

        // for now, assume that virtual addresses not equal physical addresses
        if (vaddr < 0 || vaddr >= numPages * Processor.pageSize)
            return 0;

        int vpn = Processor.pageFromAddress(vaddr);
        int amount = 0;// 已经写入的字节数
        // 进入循环说明已经写入的字节数小于length
        while (amount != length) {
            // 如果此时的vpn已经超过页数,说明length+vaddr的长度超出进程的内存边界,需要立即返回
            if (vpn >= numPages)
                break;

            /**
             * @cn 查看是否在内存中
             */
            if (!pageTable[vpn].valid)
                loadOnePage(pid, vpn);
            // 如果此时的vpn标识为只读,则不应该改写它,需要立即返回
            if (pageTable[vpn].readOnly)
                break;
            // 当前页的页内偏移，只有包含起始地址的page的页内偏移不为0
            int pageOffset = 0;
            if (amount == 0)
                pageOffset = Processor.offsetFromAddress(vaddr);
            // 在当前页应写入的字节数
            int pageLength = Processor.pageSize - pageOffset;
            if (length - amount < pageLength)
                pageLength = length - amount;
            // 得到当前虚拟页的实际物理页数
            int ppn = pageTable[vpn].ppn;
            // 得到实际在内存内的物理地址
            int paddr = Processor.makeAddress(ppn, pageOffset);

            System.arraycopy(data, offset, memory, paddr, pageLength);
            /*
             * 实验三新增的更新 @dpf
             */
            pageTable[vpn].used = true;
            pageTable[vpn].dirty = true;
            Processor processor = Machine.processor();
            int tlbSize = processor.getTLBSize();//得到TLB的大小
            for (int i = 0; i < tlbSize; i++) {
                //不仅要将页表中页表项更新，还要将TLB中的副本更新
                TranslationEntry tEntry = processor.readTLBEntry(i);
                if (tEntry.valid == true && tEntry.vpn == vpn) {
                    tEntry.used = true;
                    tEntry.dirty = true;
                    processor.writeTLBEntry(i, tEntry);//写回TLB
                }
            }

            offset += pageLength;
            amount += pageLength;
            vpn++;
        }

        return amount;
    }

    /**
     * 系统用于处理TLBMiss的方法
     *
     * @param badVAddr 导致TLBmiss的地址
     */
    public void handleTLBMiss(int badVAddr) {
        // 判断地址是否合法，如果非法，应当杀死进程，释放资源
        if (!validAddress(badVAddr)) {//没问题，这里页表数量就是进程的页表数量
            terminate();
        }
        Processor processor = Machine.processor();
        int vPageNum = processor.pageFromAddress(badVAddr);//得到TLB miss地址对应的虚拟地址的页号

        TranslationEntry pageEntry = pageTable[vPageNum];
        if (pageEntry.valid == false) {
            //如果该页表项对应页不在内存中,则需要将缺页对应的页从硬盘中加载到内存中来,并且将valid置为true
            //而换出去的那一页需要在页表中valid位置为false，并且由于存在TLB中的页表项副本的可能性
            //所以需要判断换出去的页原来的拥有进程是否为当前进程，如果为当前进程则需要检查TLB中是否有该页的副本并且valid为True
            //如果有则将TLB中副本valid也置为false，并且还需要将TLB副本中的状态（used，dirty）写回进程页表，否则可能会发生错误
            //这样一来就保证了不会出现TLB中页表项valid为true但是实际进程页表中页表项valid为false的情况了
            loadOnePage(pid, vPageNum);
            pageEntry.valid = true;//将该页表项设置为有效，即再在内存中
        }

        //在这里该页表项一定变成valid==true了

        //随机选择一个TLB里的项换成badVAddr对应页表项
        int outTLBId = getOutTLBId();//得到需要替换出去的TLB的号码
        TranslationEntry outTLBEntry = processor.readTLBEntry(outTLBId);
        //其次在processor中进行读写的时候，修改的used和dirty位只是TLB对象中的，所以要在换掉一个页表项的时候要将原来的页表项写回进程页表
        if (outTLBEntry.valid == true) {
            /*
             * 如果读出来的页表项valid是false的，说明有3种情况：
             * （1）该页表项是初始化processor的时候new的，不属于该进程的页表，因此不需要写回
             * （2）该页表项是该进程的页表项，但是valid确实为false，used和dirty不可能发生改变，因此也不需要写回
             * （3）该页表项是上一个（也可能是上几个进程的页表项），已经在saveState()方法中写回了那个进程的页表，因此也不需要写回
             * 而valid==true的情况一定是该进程的，可能被修改过的页表项，所以一定要写回。
             * 并且由于保证了TLB中页表项为true页表中也为true，所以不用担心
             */
            pageTable[outTLBEntry.vpn].used = outTLBEntry.used;//将TLB中的页表项写回进程页表
            pageTable[outTLBEntry.vpn].dirty = outTLBEntry.dirty;
        }

        processor.writeTLBEntry(outTLBId, pageTable[vPageNum]);//将这个随机TLB项替换为要访问的地址的页表项
        //由于抛出异常，pc并没有+1，所以可以pc还是维持在触发TLBmiss的指令，不用动pc，他会再把这条指令再跑一遍
    }


    /**
     * 用于在TLB miss时，获取从TLB中替换出去的TLB项的号
     *
     * @return
     */
    private int getOutTLBId() {
        //实现把valid为false的TLB项替换出去，因为valid为false的并不会在查找TLB的时候被查找出来，哪怕pageNum是一样的
        Processor processor = Machine.processor();
        int tlbSize = processor.getTLBSize();
        int outId = -1;
        //从里面找看看有没有valid为false的页表项，如果有就换出他
        for (int i = 0; i < tlbSize; i++) {
            TranslationEntry tEntry = processor.readTLBEntry(i);
            if (tEntry.valid == false) {
                outId = i;
                break;
            }
        }
        if (outId == -1) {
            //如果没找到valid为false的页表项，就从tlb中随机选择一个页表项踢出
            outId = Lib.random(tlbSize);
        }
        return outId;
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
            case Processor.exceptionTLBMiss:
                handleTLBMiss(processor.readRegister(Processor.regBadVAddr));
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
