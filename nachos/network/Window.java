package nachos.network;

import nachos.machine.Lib;
import nachos.machine.Packet;

import java.util.ArrayList;
import java.util.List;

public class Window {
    protected static final int WINDOW_SIZE = 16;
    protected ArrayList<MailMessage> window = new ArrayList<>(WINDOW_SIZE);
    protected int startSequence, lastSequenceNumber = -1;
    Window() {
        clear();
    }

    boolean add(MailMessage msg) {
        // 邮件先前已被收到并已退出队列
        if (msg.sequence < startSequence)
            return true;

        // 确保消息适合窗口w
        if (msg.sequence >= startSequence + WINDOW_SIZE)
            return false;
        // 确保信息没有超过STOP
        if (lastSequenceNumber > -1 && msg.sequence >= lastSequenceNumber)
            return false;

        int windowIndex = msg.sequence - startSequence;
        while (window.size() < windowIndex+1)	// 如果需要，扩展窗口缓冲区
            window.add(null);
        if (window.get(windowIndex) == null)	// 只有在我们以前没见过包的情况下才向窗口添加包
            window.set(windowIndex, msg);

        return true;
    }

    boolean empty() {
        return window.size() == 0;
    }
    boolean full() {
        return window.size() == WINDOW_SIZE;
    }

    List<Packet> packets() {
        List<Packet> lst = new ArrayList<Packet>();
        for (MailMessage m : window) {
            if (m != null)
                lst.add(m.packet);
        }
        Lib.debug(networkDebugFlag,"  Window has " + lst.size() + " packets");
        return lst;
    }

    void clear() {
        window.clear();
        startSequence = 0;
        lastSequenceNumber = -1;
    }

    private static final char networkDebugFlag = 'n';
}
