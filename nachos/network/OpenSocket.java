package nachos.network;

import nachos.machine.Lib;
import nachos.machine.OpenFile;

/**
 * 扩展OpenFile的套接字的类，因此它可以驻留在进程的页表中
 */
public class OpenSocket extends OpenFile {
    OpenSocket(Connection c) {
        super(null, c.srcPort + "," + c.destAddress + "," + c.destPort);
        connection = c;
    }

    /**
     * 关闭此套接字并释放所有关联的系统资源.
     */
    @Override
    public void close() {
        connection.close();
        connection = null;
    }

    @Override
    public int read(byte[] buf, int offset, int length) {
        Lib.assertTrue(offset < buf.length && length <= buf.length - offset);
        if (connection == null)
            return -1;
        else {
            byte[] receivedData = connection.receive(length);
            if (receivedData == null)
                return -1;
            else {
                System.arraycopy(receivedData, 0, buf, offset, receivedData.length);
                return receivedData.length;
            }
        }
    }

    @Override
    public int write(byte[] buf, int offset, int length) {
        if (connection == null)
            return -1;
        else
            return connection.send(buf, offset, length);
    }

    /** 此套接字持有的基础连接. */
    private Connection connection;
}
