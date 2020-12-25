package nachos.network;

import java.util.HashMap;

import nachos.machine.*;
import nachos.threads.*;
import nachos.vm.*;

/**
 * A kernel with network support.
 */
public class NetKernel extends VMKernel {
    /**
     * Allocate a new networking kernel.
     */
    public NetKernel() {
        super();
    }

    /**
     * Initialize this kernel.
     */
    public void initialize(String[] args) {
        super.initialize(args);
        postOffice = new SocketPostOffice();
    }

    @Override
    protected OpenFile openSwapFile() {
        return fileSystem.open("swapfile" + Machine.networkLink().getLinkAddress(), true);
    }

    /**
     * Start running user programs.
     */
    public void run() {
        //打印改用户程序的网络地址
        System.out.println(Machine.networkLink().getLinkAddress());
        super.run();
    }

    /**
     * Terminate this kernel. Never returns.
     */
    public void terminate() {
        postOffice.shutdown();
        super.terminate();
    }

    static SocketPostOffice postOffice;


    /**
     * 现有连接的映射（要处理哈希冲突）
     */
    static class ConnectionMap {
        void retransmitAll() {
            lock.acquire();
            for (Connection c : map.values())
                c.retransmit();
            lock.release();
        }

        Connection remove(Connection conn) {
            return remove(conn.srcPort, conn.destAddress, conn.destPort);
        }

        boolean isEmpty() {
            lock.acquire();
            boolean b = map.isEmpty();
            lock.release();
            return b;
        }

        /**
         * 关闭所有连接，并将其从此映射中删除。
         */
        void shutdown() {
            lock.acquire();
            for (Connection c : map.values())
                c.close();
            lock.release();
        }

        Connection get(int sourcePort, int destinationAddress, int destinationPort) {
            lock.acquire();
            Connection c = map.get(new SocketKey(sourcePort,destinationAddress,destinationPort));
            lock.release();
            return c;
        }

        void put(Connection c) {
            lock.acquire();
            map.put(new SocketKey(c.srcPort,c.destAddress,c.destPort),c);
            lock.release();
        }

        Connection remove(int sourcePort, int destinationAddress, int destinationPort) {
            lock.acquire();
            Connection c = map.remove(new SocketKey(sourcePort,destinationAddress,destinationPort));
            lock.release();
            return c;
        }

        private HashMap<SocketKey, Connection> map = new HashMap<>();

        private Lock lock = new Lock();
    }

    /**
     * 等待被接受的连接的映射
     */
    static class AwaitingConnectionMap {
        /**
         * 将连接添加到等待连接中
         * @param c
         * @return true
         */
        boolean addWaiting(Connection c) {
            boolean returnBool = false;
            lock.acquire();
            if (!map.containsKey(c.srcPort))
                map.put(c.srcPort, new HashMap<>());

            if (map.get(c.srcPort).containsKey(null))
                returnBool = false;//连接已经存在
            else {
                map.get(c.srcPort).put(new SocketKey(c.srcPort,c.destAddress,c.destPort), c);
                returnBool = true;
            }
            lock.release();
            return returnBool;
        }

        /**
         * 关闭所有连接，并将其从此map中删除。
         */
        void shutdown() {
            lock.acquire();
            map.clear();
            lock.release();
        }

        void retransmitAll() {
            lock.acquire();
            for (HashMap<SocketKey,Connection> hm : map.values())
                for (Connection c : hm.values())
                    c.retransmit();
            lock.release();
        }

        /**
         * 检索指定端口的连接，并从该map删除。 如果不存在，则返回null。
         * @param port
         * @return 端口上的连接（如果存在）。
         */
        Connection retrieve(int port) {
            Connection c = null;
            lock.acquire();
            if (map.containsKey(port)) {
                HashMap<SocketKey,Connection> mp = map.get(port);

                c = mp.remove(mp.keySet().iterator().next());

                //如果是空的就把它从集合中删去
                if (mp.isEmpty())
                    map.remove(port);
            }
            lock.release();

            return c;
        }

        private HashMap<Integer,HashMap<SocketKey,Connection>> map = new HashMap<>();

        private Lock lock = new Lock();
    }

    private static class SocketKey {
        SocketKey(int srcPrt, int destAddr, int destPrt) {
            sourcePort = srcPrt;
            destAddress = destAddr;
            destPort = destPrt;
            hashcode = Long.valueOf(((long) sourcePort) + ((long) destAddress) + ((long) destPort)).hashCode();
        }

        @Override
        public int hashCode() {
            return hashcode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            else if (o instanceof SocketKey) {
                SocketKey oC = (SocketKey) o;
                return sourcePort == oC.sourcePort &&
                        destAddress == oC.destAddress &&
                        destPort == oC.destPort;
            } else
                return false;
        }

        private int sourcePort, destAddress, destPort, hashcode;
    }
}
