package nachos.vm;

public class ReverseTranslationEntry {
    public ReverseTranslationEntry() {
        this.used = false;
    }

    public ReverseTranslationEntry(int pid, int vpn) {
        this.pid = pid;
        this.vpn = vpn;
        this.used = true;
    }

    /**
     * 标志该物理页是否被分配出去
     */
    public boolean used;

    /**
     * 进程号：标志该物理页分配给了哪个进程
     */
    public int pid;

    /**
     * 虚拟页号：标志该物理页在充当进程的第几个虚拟页
     */
    public int vpn;
}

