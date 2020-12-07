package nachos.threads;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
        //初始化成员变量
        this.mutex = new Lock();
        this.listenerCondition = new Condition2(mutex);
        this.speakerCondition = new Condition2(mutex);
        this.speakingCondition = new Condition2(mutex);
        this.waitListenerNum = 0;
        this.waitSpeakerNum = 0;
        this.word = -1;
        this.communicating = false;
    }


    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param    word    the integer to transfer.
     */
    public void speak(int word) {
        mutex.acquire();

        while(communicating||waitListenerNum==0) {
            //如果有人正在通信，或者没有听者正在等待，那么就睡觉
            waitSpeakerNum++;//正在等待的说者+1
            speakerCondition.sleep();//睡觉
            waitSpeakerNum--;//醒来的话，正在等待的说者-1
        }

        //否则有听者在等待并且没人正在通信
        communicating = true;//设置正在说话
        listenerCondition.wake();//唤醒听者
        this.word = word;//说话

        speakingCondition.sleep();//等待听完
        communicating = false;//设置正在通信为否
        if(waitSpeakerNum>0&&waitListenerNum>0) {
            //如果正在等待的说话的人大于0，并且正在等待的听话的人大于0，那么就可以叫一个等待说话的人起来说话啦
            speakerCondition.wake();
        }
        mutex.release();//释放锁
    }


    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return the integer transferred.
     */
    public int listen() {
        mutex.acquire();

        if(communicating==false||waitSpeakerNum>0) {
            //如果没有人在通信，并且有说者在等待的话
            speakerCondition.wake();//唤醒一个说者
        }
        waitListenerNum++;//等待的听者的数量+1
        listenerCondition.sleep();//睡觉，等待说者说完并且将其唤醒
        waitListenerNum--;//等待的听者的数量-1
        int word = this.word;//获取说者说的话
        speakingCondition.wake();//已经听完，唤醒正在等待听完的说者

        mutex.release();
        return word;//返回听到的东西
    }


    int word;
    boolean communicating;//正在交流的标志
    Condition2 listenerCondition;//听者等待等待队列
    int waitListenerNum;//正在等待的听者的数目
    Condition2 speakerCondition;//说者等待队列
    int waitSpeakerNum;//正在等待的说者的数目
    Condition2 speakingCondition;//正在说话者的等待队列
    Lock mutex;
}
