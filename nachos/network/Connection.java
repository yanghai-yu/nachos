package nachos.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import nachos.machine.Kernel;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.MalformedPacketException;
import nachos.machine.Packet;
import nachos.threads.Condition;
import nachos.threads.Lock;

class Connection {

    private Lock stateLock = new Lock();//状态锁
    private Condition connectionEstablished;//是否建立连接
    private NTPState currentState = NTPState.CLOSED;//当前连接的状态，默认是CLOSED
    private boolean calledClose = false;

    private SendWindow sendWindow = new SendWindow();//发送的滑动窗口
    private ReceiveWindow receiveWindow = new ReceiveWindow();//接收的滑动窗口
    private ByteStream residualData = new ByteStream();//剩余数据
    private ByteStream sendBuffer = new ByteStream();//发送缓存

    int destAddress, destPort, srcPort;//目标地址、目标端口、源端口

    Connection(int _destAddress, int _destPort, int _srcPort) {
        destAddress = _destAddress;
        destPort = _destPort;
        srcPort = _srcPort;
        connectionEstablished = new Condition(stateLock);
    }

    /**
     * 连接到另一个Nachos实例
     * 如果它收到此连接的SYN数据包，则将返回false。 这表示潜在的协议死锁，我们可以通过尝试其他本地端口来处理
     * @return true if the connection was successful
     */
    boolean connect() {
        stateLock.acquire();
        currentState.connect(this);
        stateLock.release();

        //如果遇到死锁，则返回false
        if (currentState == NTPState.DEADLOCK) {
            currentState = NTPState.CLOSED;
            return false;
        }
        return true;
    }

    /**
     * 确定该连接已被NachOS的本地实例接受.
     */
    boolean accept() {
        stateLock.acquire();
        currentState.accept(this);
        stateLock.release();
        return currentState == NTPState.ESTABLISHED;
    }

    /**
     * 关闭连接
     */
    void close() {
        stateLock.acquire();
        calledClose = true;
        currentState.close(this);
        stateLock.release();
    }

    /** 当我们过渡到CLOSED状态时调用 */
    protected void finished() {
        // 完整性检查
        if (calledClose || exhausted()) {
            sendWindow.clear();
            receiveWindow.clear();
            ((NetKernel) Kernel.kernel).postOffice.finished(this);
        }
    }

    /**
     * 当收到此连接的消息时，由PostOffice调用
     */
    void packet(MailMessage msg) {
        stateLock.acquire();
        switch (msg.flags) {
            case MailMessage.SYN:
                Lib.debug(networkDebugFlag,"Receiving SYN");
                currentState.syn(this, msg);
                break;
            case MailMessage.SYN | MailMessage.ACK:
                Lib.debug(networkDebugFlag,"Receiving SYNACK");
                currentState.synack(this, msg);
                break;
            case MailMessage.DATA:
                Lib.debug(networkDebugFlag,"Receiving DATA: " + msg.sequence + " with content length " + msg.contents.length);
                currentState.data(this, msg);
                break;
            case MailMessage.ACK:
                Lib.debug(networkDebugFlag,"Receiving ack for " + msg.sequence);
                currentState.ack(this, msg);
                break;
            case MailMessage.STP:
                Lib.debug(networkDebugFlag,"Receiving STP with " + msg.sequence);
                receiveWindow.stopAt(msg.sequence);
                currentState.stp(this, msg);
                break;
            case MailMessage.FIN:
                Lib.debug(networkDebugFlag,"Receivin FIN");
                currentState.fin(this, msg);
                break;
            case MailMessage.FIN | MailMessage.ACK:
                Lib.debug(networkDebugFlag,"Receivin FINACK");
                currentState.finack(this, msg);
                break;
            default:
                // Drop invalid packet
                Lib.debug(networkDebugFlag,"OMG INVALID PACKET");
                break;
        }
        stateLock.release();
    }

    /**
     * 重传计时器到期时由PostOffice调用
     */
    void retransmit() {
        stateLock.acquire();
        currentState.timer(this);
        stateLock.release();
    }

    /**
     * 在缓冲区中排队发送数据
     */
    int send(byte[] buffer, int offset, int length) {
        byte[] toSend = new byte[length];
        if (length > buffer.length - offset)
            System.arraycopy(buffer, offset, toSend, 0, buffer.length - offset);
        else
            System.arraycopy(buffer, offset, toSend, 0, length);

        stateLock.acquire();
        int sent = currentState.send(this, toSend);
        stateLock.release();
        return sent;
    }

    /**
     * 从连接读取数据
     * @param bytes
     * 	读取的最大字节数
     * @return
     * 	接收到的数据
     */
    byte[] receive(int bytes) {
        stateLock.acquire();
        byte[] data = currentState.recv(this, bytes);
        stateLock.release();
        return data;
    }

    /**发送空/已标记的数据包 */
    private void transmit(int flags) {
        switch (flags) {
            case MailMessage.SYN:
                Lib.debug(networkDebugFlag,"Sending SYN");
                break;
            case MailMessage.SYN | MailMessage.ACK:
                Lib.debug(networkDebugFlag,"Sending SYNACK");
                break;
            case MailMessage.FIN:
                Lib.debug(networkDebugFlag,"Sending FIN");
                break;
            case MailMessage.FIN | MailMessage.ACK:
                Lib.debug(networkDebugFlag,"Sending FINACK");
                break;
            default:
                Lib.debug(networkDebugFlag,"Sending [" + flags + "]");//This should never happen
        }
        ((NetKernel) Kernel.kernel).postOffice.enqueue(makeMessage(flags,0,MailMessage.EMPTY_CONTENT).packet);
    }
    /** 发送一个Ack数据包 */
    private void transmitAck(int sequence) {
        Lib.debug(networkDebugFlag,"Sending ACK for " + sequence);
        ((NetKernel) Kernel.kernel).postOffice.enqueue(makeMessage(MailMessage.ACK, sequence, MailMessage.EMPTY_CONTENT).packet);
    }
    /** 发送一个STP数据包*/
    private void transmitStp() {
        ((NetKernel) Kernel.kernel).postOffice.enqueue(sendWindow.stopPacket(sendBuffer));
    }
    /** Flood the network with data! */
    private void transmitData() {
        while (sendBuffer.size() > 0 && !sendWindow.full()) {
            byte[] toSend = sendBuffer.dequeue(Math.min(MailMessage.maxContentsLength, sendBuffer.size()));
            MailMessage msg = sendWindow.add(toSend);
            if (msg != null) {
                Lib.debug(networkDebugFlag,"Sending DATA with sequence " + msg.sequence + " and length " + msg.contents.length);
                ((NetKernel) Kernel.kernel).postOffice.enqueue(msg.packet);
            }
            else {
                // We should never get here
                Lib.assertNotReached("Attempted to add packet to full send window");
                break;
            }
        }
    }

    /**
     * 构造一个指向连接另一端的新数据包
     */
    private MailMessage makeMessage(int flags, int sequence, byte[] contents) {
        try {
            return new MailMessage(destAddress, destPort, Machine.networkLink().getLinkAddress(), srcPort, flags, sequence, contents);
        } catch (MalformedPacketException e) {
            return null;
        }
    }

    private boolean exhausted() {
        return residualData.size() == 0 && receiveWindow.empty();
    }

    private enum NTPState {
        CLOSED {
            @Override
            void connect(Connection c) {
                // 建立
                c.transmit(MailMessage.SYN);
                // 立即过渡
                Lib.debug(networkDebugFlag,"Transition to SYN_SENT");
                c.currentState = SYN_SENT;
                // sleep直到连接建立
                c.connectionEstablished.sleep();
            }

            @Override
            byte[] recv(Connection c, int maxBytes) {
                byte[] data = super.recv(c, maxBytes);

                // 枯竭？
                if (c.exhausted())
                    c.finished();

                return (data.length == 0) ? (null) : (data);
            }

            @Override
            int send(Connection c, byte[] buffer) {
                return -1;
            }

            @Override
            void syn(Connection c, MailMessage msg) {
                // 过渡到SYN_RCVD
                Lib.debug(networkDebugFlag,"Tranition to SYN_RCVD");
                c.currentState = SYN_RCVD;
            }

            @Override
            void fin(Connection c, MailMessage msg) {
                // 发送FINACK
                c.transmit(MailMessage.FIN | MailMessage.ACK);
            }

        },

        SYN_SENT {
            @Override
            void timer(Connection c) {
                // 发送SYN
                c.transmit(MailMessage.SYN);
            }

            @Override
            void syn(Connection c, MailMessage msg) {
                // 死锁，将状态置为DEADLOCK
                Lib.debug(networkDebugFlag,"Transition to DEADLOCK");
                c.currentState = DEADLOCK;
                c.connectionEstablished.wake();
            }

            @Override
            void synack(Connection c, MailMessage msg) {
                // 将状态置为ESTABLISHED, 唤醒在connect()中等待的线程
                Lib.debug(networkDebugFlag,"Transition to ESTABLISHED");
                c.currentState = ESTABLISHED;
                c.connectionEstablished.wake();
            }

            @Override
            void data(Connection c, MailMessage msg) {
                // 发送SYN
                c.transmit(MailMessage.SYN);
            }
            @Override
            void stp(Connection c, MailMessage msg) {
                // 发送SYN
                c.transmit(MailMessage.SYN);
            }
            @Override
            void fin(Connection c, MailMessage msg) {
                // 发送SYN
                c.transmit(MailMessage.SYN);
            }
        },

        SYN_RCVD {
            @Override
            void accept(Connection c) {
                // 发送SYNACK, 将状态置为ESTABLISHED
                c.transmit(MailMessage.SYN | MailMessage.ACK);
                Lib.debug(networkDebugFlag,"Transition to ESTABLISHED");
                c.currentState = ESTABLISHED;
            }
        },

        ESTABLISHED {
            @Override
            void close(Connection c) {
                if (c.sendWindow.empty() && c.sendBuffer.size() == 0) {//No more data to send, either in queue or window
                    c.transmit(MailMessage.FIN);
                    Lib.debug(networkDebugFlag,"Transition to CLOSING");
                    c.currentState = CLOSING;
                }
                else {
                    c.transmitStp();
                    Lib.debug(networkDebugFlag,"Transition to STP_SENT");
                    c.currentState = STP_SENT;
                }
            }

            @Override
            void syn(Connection c, MailMessage msg) {
                // 发送SYNACK
                c.transmit(MailMessage.SYN | MailMessage.ACK);
            }

            @Override
            void data(Connection c, MailMessage msg) {
                if (c.receiveWindow.add(msg))
                    c.transmitAck(msg.sequence);
                else
                    Lib.debug(networkDebugFlag,"Dropped DATA packet " + msg.sequence);
            }

            @Override
            void ack(Connection c, MailMessage msg) {
                c.sendWindow.acked(msg.sequence);
                c.transmitData();
            }

            @Override
            void stp(Connection c, MailMessage msg) {
                c.sendWindow.clear();
                Lib.debug(networkDebugFlag,"Transition to STP_RCVD");
                c.currentState = STP_RCVD;
            }

            @Override
            void fin(Connection c, MailMessage msg) {
                c.sendWindow.clear();
                c.transmit(MailMessage.FIN | MailMessage.ACK);
                Lib.debug(networkDebugFlag,"Transition to CLOSED");
                c.currentState = CLOSED;
                c.finished();
            }
        },

        STP_SENT {
            @Override int send(Connection c, byte[] buffer) {
                // 无法在关闭的连接上发送更多数据
                return -1;
            }

            @Override
            void timer(Connection c) {
                if (c.sendWindow.empty())
                    c.transmit(MailMessage.FIN);
                else
                    c.transmitStp();

                // 重新发送未确认
                super.timer(c);
            }

            @Override
            void ack(Connection c, MailMessage msg) {
                c.sendWindow.acked(msg.sequence);
                c.transmitData();

                if (c.sendWindow.empty() && c.sendBuffer.size() == 0) {
                    c.transmit(MailMessage.FIN);
                    c.currentState = CLOSING;
                }
            }

            @Override
            void syn(Connection c, MailMessage msg) {
                c.transmit(MailMessage.SYN | MailMessage.ACK);
            }

            @Override
            void data(Connection c, MailMessage msg) {
                c.transmitStp();
            }

            @Override
            void stp(Connection c, MailMessage msg) {
                c.sendWindow.clear();
                c.transmit(MailMessage.FIN);
                c.currentState = CLOSING;
            }

            @Override
            void fin(Connection c, MailMessage msg) {
                c.transmit(MailMessage.FIN | MailMessage.ACK);
                c.currentState = CLOSED;
                c.finished();
            }

        },

        STP_RCVD {
            @Override
            int send(Connection c, byte[] buffer) {
                // 无法在关闭的连接上发送更多数据
                return -1;
            }

            @Override
            void close(Connection c) {
                c.transmit(MailMessage.FIN);
                Lib.debug(networkDebugFlag,"Transition to CLOSING");
                c.currentState = CLOSING;
            }

            @Override
            void data(Connection c, MailMessage msg) {
                if (c.receiveWindow.add(msg))
                    c.transmitAck(msg.sequence);
                else
                    Lib.debug(networkDebugFlag,"Dropped DATA packet " + msg.sequence);
            }

            @Override
            void fin(Connection c, MailMessage msg) {
                c.transmit(MailMessage.FIN | MailMessage.ACK);
                Lib.debug(networkDebugFlag,"Transition to CLOSED");
                c.currentState = CLOSED;
                c.finished();
            }
        },
        CLOSING {
            @Override
            int send(Connection c, byte[] buffer) {
                // 无法在关闭的连接上发送更多数据
                return -1;
            }

            @Override
            void timer(Connection c) {
                c.transmit(MailMessage.FIN);
            }

            @Override
            void syn(Connection c, MailMessage msg) {
                c.transmit(MailMessage.SYN | MailMessage.ACK);
            }

            @Override
            void data(Connection c, MailMessage msg) {
                c.transmit(MailMessage.FIN);
            }

            @Override
            void stp(Connection c, MailMessage msg) {
                c.transmit(MailMessage.FIN);
            }

            @Override
            void fin(Connection c, MailMessage msg) {
                c.transmit(MailMessage.FIN | MailMessage.ACK);
                Lib.debug(networkDebugFlag,"Transition to CLOSED");
                c.currentState = CLOSED;
                c.finished();
            }

            @Override
            void finack(Connection c, MailMessage msg) {
                Lib.debug(networkDebugFlag,"Transition to CLOSED");
                c.currentState = CLOSED;
                c.finished();
            }
        },

        DEADLOCK {};

        /** 一个名为connect（）的应用程序 */
        void connect(Connection c) {}
        /** 一个名为accept（）的应用程序 */
        void accept(Connection c) {}
        /** 一个名为read（）的应用程序 */
        byte[] recv(Connection c, int maxBytes) {
            while (c.residualData.size() < maxBytes) {
                MailMessage msg = c.receiveWindow.remove();
                if (msg == null)
                    break;
                try {
                    c.residualData.write(msg.contents);
                } catch (IOException e) {}
            }

            return c.residualData.dequeue(Math.min(c.residualData.size(), maxBytes));
        }
        /** 一个名为write（）的应用程序. */
        int send(Connection c, byte[] buffer) {
            try {
                c.sendBuffer.write(buffer);
            } catch (IOException e) {}
            c.transmitData();
            return buffer.length;
        }
        /** 一个名为close（）的应用程序. */
        void close(Connection c) {}
        /** 重传计时器 ticked. */
        void timer(Connection c) {
            Lib.debug(networkDebugFlag,"Retransmitting unacknowledged packets");
            ((NetKernel) Kernel.kernel).postOffice.enqueue(c.sendWindow.packets());
        }
        /** 接收到SYN数据包（SYN位置1的数据包）. */
        void syn(Connection c, MailMessage msg) {}
        /** 接收到SYN / ACK数据包（已设置SYN和ACK位的数据包）. */
        void synack(Connection c, MailMessage msg) {}
        /** 接收到数据包（未设置SYN，ACK，STP或FIN位的数据包）. */
        void data(Connection c, MailMessage msg) {}
        /** 接收到ACK数据包（设置了ACK位的数据包）. */
        void ack(Connection c, MailMessage msg) {}
        /** 接收到STP数据包（将STP位置1的数据包）. */
        void stp(Connection c, MailMessage msg) {}
        /** 接收到FIN数据包（将FIN位置1的数据包）. */
        void fin(Connection c, MailMessage msg) {}
        /** 接收到FIN / ACK数据包（设置了FIN和ACK位的数据包）. */
        void finack(Connection c, MailMessage msg) {}
    }

    private class SendWindow extends Window {
        protected int sequenceNumber;	// 分配下一个传出数据包的序列号

        void acked(int sequence) {
            if (sequence < startSequence || sequence >= startSequence + window.size() || sequence >= lastSequenceNumber)
                return;

            int windowIndex = sequence - startSequence;
            window.set(windowIndex, null);

            // 由于这是发送缓冲区，因此我们假设没有在其中填充任何间隙（因为add（byte []）不会）
            while (window.size() > 0 && window.get(0) == null) {
                window.remove(0);
                startSequence++;
            }
        }

        MailMessage add(byte[] bytes) {
            MailMessage msg = makeMessage(MailMessage.DATA, sequenceNumber, bytes);
            if (super.add(msg)) {
                // 序列号加一
                sequenceNumber++;
            } else {
                // 无法添加到窗口
                msg = null;
            }

            return msg;
        }

        Packet stopPacket(ByteStream sendBuffer) {
            if (stopPacket == null) {
                lastSequenceNumber = (startSequence + window.size()) + (sendBuffer.size() / MailMessage.maxContentsLength) + (sendBuffer.size() % MailMessage.maxContentsLength != 0 ? 1 : 0);
                stopPacket = makeMessage(MailMessage.STP, lastSequenceNumber, MailMessage.EMPTY_CONTENT).packet;
            }

            return stopPacket;
        }

        private Packet stopPacket = null;
    }

    private static class ReceiveWindow extends Window {
        MailMessage remove() {
            if (window.size() > 0 && window.get(0) != null) {
                startSequence++;
                return window.remove(0);
            }
            else
                return null;
        }

        void stopAt(int sequence) {
            if (lastSequenceNumber == -1)
                lastSequenceNumber = sequence;
        }
    }

    private static final char networkDebugFlag = 'n';

    private static class ByteStream extends ByteArrayOutputStream {
        byte[] dequeue(int bytes) {
            byte[] temp = super.toByteArray(), returnArray;

            if (bytes > temp.length)
                returnArray = new byte[temp.length];
            else
                returnArray = new byte[bytes];

            System.arraycopy(temp, 0, returnArray, 0, returnArray.length);

            super.reset();

            super.write(temp, returnArray.length, temp.length - returnArray.length);

            return returnArray;
        }
    }
}
