package nachos.network;

import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;
import nachos.threads.Condition;
import nachos.threads.KThread;
import nachos.threads.Lock;
import nachos.threads.Semaphore;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static nachos.threads.ThreadedKernel.alarm;

/**
 * 仿造PostOffice实现的采用多线程管理nachos传输协议的类
 */
public class SocketPostOffice {
    SocketPostOffice() {
        nothingToSend = new Condition(sendLock);
        terminationCondition = new Condition(terminationLock);

        //设置线程中断处理程序
        Machine.networkLink().setInterruptHandlers(this::receiveInterrupt,
                this::sendInterrupt);

        //设置工作线程
        KThread postalDeliveryThread = new KThread(
                this::postalDelivery
        ), postalSendThread = new KThread(
                this::send
        ), timerInterruptThread = new KThread(
                this::timerRoutine
        );

        postalDeliveryThread.fork();
        postalSendThread.fork();
        timerInterruptThread.fork();
    }

    Connection accept(int port) {
        Connection c = awaitingConnectionMap.retrieve(port);

        if (c != null)
            c.accept();

        return c;
    }

    Connection connect(int host, int port) {
        Connection connection = null;
        boolean found = false;
        int srcPort, tries = 0;
        while (connection == null) {
            //连接的源端口
            srcPort = portGenerator.nextInt(MailMessage.portLimit);
            tries = 0;

            while (!(found = (connectionMap.get(srcPort, host, port) == null)) && tries++ < MailMessage.portLimit)
                srcPort = (srcPort+1) % MailMessage.portLimit;

            if (found) {
                connection = new Connection(host, port, srcPort);
                connectionMap.put(connection);
                if (!connection.connect()) {
                    connectionMap.remove(connection);
                    connection = null;
                }
            }//else port saturation, so randomize and try again
        }

        return connection;
    }

    private Random portGenerator = new Random();

    /**
     * 关闭连接，将其从connectionMap中删除（如果存在）。
     * 仅应在关闭“实时”连接时从内核调用此方法
     * @param connection (not null)
     */
    void close(Connection connection) {
        if (connectionMap.remove(connection.srcPort, connection.destAddress, connection.destPort) != null)
            connection.close();
    }

    /**
     * 关闭在两个connectionMap和awaitingConnectionMap中所有的连接，删除连接实例
     */
    void shutdown() {
        connectionMap.shutdown();
        awaitingConnectionMap.shutdown();

        terminationLock.acquire();

        while (!connectionMap.isEmpty())
            terminationCondition.sleep();

        terminationLock.release();

    }

    private Lock terminationLock = new Lock();
    private Condition terminationCondition;

    /**
     * 当Connection实例完全关闭并用尽时，由Connection实例调用。 这将导致NetKernel将其从其连接映射中删除
     */
    void finished(Connection c) {
        if (connectionMap.remove(c.srcPort, c.destAddress, c.destPort) != null) {
            terminationLock.acquire();
            terminationCondition.wake();
            terminationLock.release();
        }
    }

    /**
     * 使要通过网络发送的数据包入队。
     * @param p
     */
    void enqueue(Packet p) {
        sendLock.acquire();
        sendQueue.add(p);
        nothingToSend.wake();
        sendLock.release();
    }

    /**
     * 使列表中有序的数据包序列入队。
     */
    void enqueue(List<Packet> ps) {
        sendLock.acquire();
        sendQueue.addAll(ps);
        nothingToSend.wake();
        sendLock.release();
    }

    /**
     * 将数据包传递到适当的Socket.
     */
    private void postalDelivery() {
        MailMessage pktMsg = null;
        Connection connection = null;
        while (true) {
            messageReceived.P();

            try {
                pktMsg = new MailMessage(Machine.networkLink().receive());
            } catch (MalformedPacketException e) {
                continue;//丢弃包
            }

            if ((connection = connectionMap.get(pktMsg.dstPort, pktMsg.packet.srcLink, pktMsg.srcPort)) != null)
                connection.packet(pktMsg);
            else if (pktMsg.flags == MailMessage.SYN) {
                connection = new Connection(pktMsg.packet.srcLink, pktMsg.srcPort, pktMsg.dstPort);
                connection.packet(pktMsg);

                //放入connectionMap
                connectionMap.put(connection);

                //放入awaitingConnectionMap
                awaitingConnectionMap.addWaiting(connection);
            } else if (pktMsg.flags == MailMessage.FIN) {
                try {
                    enqueue(new MailMessage(pktMsg.packet.srcLink, pktMsg.srcPort, pktMsg.packet.dstLink, pktMsg.dstPort, MailMessage.FIN | MailMessage.ACK, 0, MailMessage.EMPTY_CONTENT).packet);
                } catch (MalformedPacketException e) {
                }
            }
        }
    }

    /**
     * 在数据包到达并可以从网络链接出队时调用
     */
    private void receiveInterrupt() {
        messageReceived.V();
    }

    /**
     * 通过网络链接从队列发送数据包。
     */
    private void send() {
        Packet p = null;
        while (true) {
            sendLock.acquire();

            while (sendQueue.isEmpty())
                nothingToSend.sleep();

            //使数据包出队
            p = sendQueue.poll();
            sendLock.release();

            //现在开始发送数据包
            Machine.networkLink().send(p);
            messageSent.P();
        }
    }

    /**
     * Called when a packet has been sent and another can be queued to the
     * network link. Note that this is called even if the previous packet was
     * dropped.
     */
    private void sendInterrupt() {
        messageSent.V();
    }

    /**
     * 中断处理程序的例程。 触发一个事件，以在需要计时器中断的所有套接字上调用retransmit方法
     */
    private void timerRoutine() {
        while (true) {
            alarm.waitUntil(20000);

            //在所有连接上调用重传方法
            connectionMap.retransmitAll();
            awaitingConnectionMap.retransmitAll();//FIXME: This may not be necessary
        }
    }

    private NetKernel.ConnectionMap connectionMap = new NetKernel.ConnectionMap();
    private NetKernel.AwaitingConnectionMap awaitingConnectionMap = new NetKernel.AwaitingConnectionMap();

    private Semaphore messageReceived = new Semaphore(0);
    private Semaphore messageSent = new Semaphore(0);
    private Lock sendLock = new Lock();

    /** 如果没有东西要发送，那么该条件变量sleep */
    private Condition nothingToSend;

    /** 发送的包的列表*/
    private LinkedList<Packet> sendQueue = new LinkedList<Packet>();
}
