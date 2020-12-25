package nachos.network;

import nachos.machine.*;
import nachos.vm.*;

import static nachos.machine.Kernel.kernel;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends VMProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
        super();
    }

    private static final int syscallConnect = 11, syscallAccept = 12;

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
     * <td>11</td>
     * <td><tt>int  connect(int host, int port);</tt></td>
     * </tr>
     * <tr>
     * <td>12</td>
     * <td><tt>int  accept(int port);</tt></td>
     * </tr>
     * </table>
     *
     * @param syscall
     *            the syscall number.
     * @param a0
     *            the first syscall argument.
     * @param a1
     *            the second syscall argument.
     * @param a2
     *            the third syscall argument.
     * @param a3
     *            the fourth syscall argument.
     * @return the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
        switch (syscall) {
            case syscallAccept:
                return handleAccept(a0);
            case syscallConnect:
                return handleConnect(a0,a1);
            default:
                return super.handleSyscall(syscall, a0, a1, a2, a3);
        }
    }

    /**
     * connect系统调用.
     * @param host
     * @param port
     */
    private int handleConnect(int host, int port) {
        Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);
        int fileDesc = getFileDescriptor();
        if (fileDesc != -1) {
            try {
                openFiles[fileDesc] = new OpenSocket(((NetKernel) kernel).postOffice.connect(host,port));
            } catch (ClassCastException cce) {
                Lib.assertNotReached("Error - kernel not of type NetKernel");
            }
        }

        return fileDesc;
    }

    /**
     * accept系统调用.
     * @param port
     */
    private int handleAccept(int port) {
        Lib.assertTrue(port >= 0 && port < Packet.linkAddressLimit);
        int fileDesc = getFileDescriptor();
        if (fileDesc != -1) {
            Connection c = null;
            try {
                // 尝试在文件表中获取一个条目
                c = ((NetKernel) kernel).postOffice.accept(port);
            } catch (ClassCastException cce) {
                Lib.assertNotReached("Error - kernel not of type NetKernel");
            }

            if (c != null) {
                openFiles[fileDesc] = new OpenSocket(c);
                return fileDesc;
            }
        }

        return -1;
    }

}
