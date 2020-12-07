package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking
 * the maximum).
 */
public class LotteryScheduler extends PriorityScheduler {

    /**
     * Allocate a new priority scheduler.
     */
    public LotteryScheduler() {
        super();
    }

    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }


    /**
     * Allocate a new priority thread queue.
     *
     * @param transferPriority <tt>true</tt> if this queue should
     *                         transfer priority from waiting threads
     *                         to the owning thread.
     * @return a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
        return new LotteryQueue(transferPriority);
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by Lottery.
     */
    protected class LotteryQueue extends PriorityScheduler.PriorityQueue {
        LotteryQueue(boolean transferPriority) {
            super(transferPriority);
        }


        protected ThreadState pickNextThread() {
            if (waitingList.isEmpty())
                return null;

            int totalLottery = 0;//用于统计总彩票数量

            for (KThread th : waitingList) {
                totalLottery += getThreadState(th).getEffectivePriority();//统计总彩票数量
            }

            Random random = new Random();
            int randomLottery = random.nextInt(totalLottery);//抽取一张彩票
//            System.out.println();
//            System.out.println("the chosen lottery is "+ randomLottery);
            int countLottery = -1;//因为彩票是从0开始抽取的
            KThread nextThread = null;//需要选出的下一个线程
            for (KThread th : waitingList) {//遍历队列，找出被抽中的线程
                countLottery += getThreadState(th).getEffectivePriority();
//                System.out.println("scan "+th.getName());
                if (countLottery >= randomLottery) {//线程被抽中
                    nextThread = th;
//                    System.out.println("the chosen thread is "+nextThread.getName()+" which holds "+getThreadState(nextThread).getEffectivePriority()+" lotteries");
                    break;
                }
            }

            return getThreadState(nextThread);
        }


    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see KThread#schedulingState
     */
    protected class ThreadState extends PriorityScheduler.ThreadState {
        public ThreadState(KThread thread) {
            super(thread);
        }


        public void updateEffectivePriority(PriorityQueue blockedQueue)  {
            PriorityQueue transferQueue=blockedQueue;
            while(transferQueue!=null&&transferQueue.resHolder!=null) {
                ThreadState tempState=getThreadState(transferQueue.resHolder);
                int total = tempState.effectivePriority;
                total += effectivePriority;
                if(total>priorityMaximum) {//该线程持有的彩票数量不能超过最大彩票数
                    total = priorityMaximum;

                }
                tempState.effectivePriority = total;
                transferQueue=tempState.blockedQueue;
            }

        }
    }
    public static void selfTest() {
        LotteryScheduler lsc = new LotteryScheduler();
        boolean status = Machine.interrupt().disable();
        KThread a = new KThread(new Runnable() {
            public void run() {
                for (int i = 0; i < 5; i++) {
                    System.out.println(" thread 1 looped " + i + " times");
                }
            }
        }).setName("thread1");
        lsc.setPriority(a, 1);
        System.out.println("thread1 holds " + lsc.getThreadState(a).priority+" lotteries");
        KThread c = new KThread(new Runnable() {
            public void run() {
                for (int i = 0; i < 5; i++) {
                    if (i == 2)
                        a.join();

                    System.out.println(" thread 2 looped " + i + " times");
                }
            }
        }).setName("thread2");
        lsc.setPriority(c, 6);
        System.out.println("thread2 holds " + lsc.getThreadState(c).priority+" lotteries");
        a.fork();
        c.fork();
//        a.join();
        c.join();
        Machine.interrupt().restore(status);
    }
}