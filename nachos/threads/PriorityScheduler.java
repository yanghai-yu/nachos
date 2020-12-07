package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
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
        return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
        Lib.assertTrue(Machine.interrupt().disabled());

        return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
        Lib.assertTrue(Machine.interrupt().disabled());

        Lib.assertTrue(priority >= priorityMinimum &&
                priority <= priorityMaximum);

        getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMaximum)
            return false;

        setPriority(thread, priority + 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    public boolean decreasePriority() {
        boolean intStatus = Machine.interrupt().disable();

        KThread thread = KThread.currentThread();

        int priority = getPriority(thread);
        if (priority == priorityMinimum)
            return false;

        setPriority(thread, priority - 1);

        Machine.interrupt().restore(intStatus);
        return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param thread the thread whose scheduling state to return.
     * @return the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
        if (thread.schedulingState == null)
            thread.schedulingState = new ThreadState(thread);

        return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue {
        PriorityQueue(boolean transferPriority) {
            this.transferPriority = transferPriority;
        }

        public void waitForAccess(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).waitForAccess(this);
        }

        public void acquire(KThread thread) {
            Lib.assertTrue(Machine.interrupt().disabled());
            getThreadState(thread).acquire(this);
        }

        public KThread nextThread() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me
            //处理旧线程的ThreadState的更新
            KThread oldThread = resHolder;
            if (oldThread != null) {
                ThreadState oldState = getThreadState(oldThread);
                oldState.accessedSet.remove(this);
                if (oldState.accessedSet.size() == 0)
                    oldState.effectivePriority = oldState.priority;
            }

            //选出并处理新线程
            ThreadState nextThreadState = pickNextThread();
            /**
             * nextThreadState为null有2种情况
             * 一种情况是队列中所有线程被取完了，这时应该把resHolder设置为null
             * 另一种情况是在调用acquire方法之前就调用了nextThread方法，这时把resHolder设置为null也无可厚非*/
            if (nextThreadState == null) {
                resHolder = null;
                return null;
            } else {
                nextThreadState.acquire(this);
                return nextThreadState.thread;
            }

        }

        /**
         * Return the next thread that <tt>nextThread()</tt> would return,
         * without modifying the state of this queue.
         *
         * @return the next thread that <tt>nextThread()</tt> would
         * return.
         */
        protected ThreadState pickNextThread() {
            // implement me
            int maxPriority = 0;
            for (int i = 0; i < waitingList.size(); i++) {
                int tempPriority = getThreadState(waitingList.get(i)).getEffectivePriority();
                if (tempPriority > maxPriority)
                    maxPriority = tempPriority;
            }
            KThread nextThread = null;
            for (int i = 0; i < waitingList.size(); i++) {
                KThread tempThread = waitingList.get(i);
                if (getThreadState(tempThread).getEffectivePriority() == maxPriority) {
                    nextThread = tempThread;
                    break;
                }
            }
            if (nextThread == null) return null;
            else return getThreadState(nextThread);
        }

        public void print() {
            Lib.assertTrue(Machine.interrupt().disabled());
            // implement me (if you want)
        }

        /**
         * <tt>true</tt> if this queue should transfer priority from waiting
         * threads to the owning thread.
         */
        public boolean transferPriority;
        public KThread resHolder;//表明该队列正在等待的线程
        public ArrayList<KThread> waitingList = new ArrayList<>();//该等待队列的内部容器


    }

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
        /**
         * Allocate a new <tt>ThreadState</tt> object and associate it with the
         * specified thread.
         *
         * @param thread the thread this state belongs to.
         */
        public ThreadState(KThread thread) {
            this.thread = thread;

            setPriority(priorityDefault);
        }

        /**
         * Return the priority of the associated thread.
         *
         * @return the priority of the associated thread.
         */
        public int getPriority() {
            return priority;
        }

        /**
         * Return the effective priority of the associated thread.
         *
         * @return the effective priority of the associated thread.
         */
        public int getEffectivePriority() {
            // implement me
            return effectivePriority;
        }

        //更新优先级
        public void updateEffectivePriority(PriorityQueue blockedQueue) {
            PriorityQueue transferQueue = blockedQueue;
            /* 进行优先级更新，使用while是因为要处理优先级传递.
             * 若transferQueue为null意味着没有需要传递优先级的线程(遍历到的线程并不在某个队列中等待).
             * 若transferQueue.resHolder意味着没有线程在持有资源,这是为了防止用户调用方法不规范导致报错->
             * (队列中有线程等待但是却没有持有资源的线程,这种情况只可能发生在用户在直接获取资源时没有按照规定调用acquire方法)*/
            while (transferQueue != null && transferQueue.resHolder != null) {
                ThreadState tempState = getThreadState(transferQueue.resHolder);
                //若占用了资源的线程的有效优先级小于或者等于加入队列的线程的有效优先级，那没有更新的必要(结束循环)
                if (tempState.getEffectivePriority() < this.getEffectivePriority()) {
                    tempState.effectivePriority = this.getEffectivePriority();
                    transferQueue = tempState.blockedQueue;
                } else transferQueue = null;
            }
        }

        /**
         * Set the priority of the associated thread to the specified value.
         *
         * @param priority the new priority.
         */
        public void setPriority(int priority) {
            if (this.priority == priority)
                return;

            this.priority = priority;

            // implement me
            effectivePriority = Math.max(this.priority, this.effectivePriority);
            updateEffectivePriority(blockedQueue);
        }


        /**
         * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
         * the associated thread) is invoked on the specified priority queue.
         * The associated thread is therefore waiting for access to the
         * resource guarded by <tt>waitQueue</tt>. This method is only called
         * if the associated thread cannot immediately obtain access.
         *
         * @param waitQueue the queue that the associated thread is
         *                  now waiting on.
         * @see nachos.threads.ThreadQueue#waitForAccess
         */
        public void waitForAccess(PriorityQueue waitQueue) {
            // implement me
            waitQueue.waitingList.add(thread);
            if (waitQueue.transferPriority) {
                blockedQueue = waitQueue;
                updateEffectivePriority(blockedQueue);
            }
        }


        /**
         * Called when the associated thread has acquired access to whatever is
         * guarded by <tt>waitQueue</tt>. This can occur either as a result of
         * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
         * <tt>thread</tt> is the associated thread), or as a result of
         * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
         *
         * @see nachos.threads.ThreadQueue#acquire
         * @see nachos.threads.ThreadQueue#nextThread
         */
        public void acquire(PriorityQueue waitQueue) {
            // implement me
            if (!waitQueue.waitingList.isEmpty())
                waitQueue.waitingList.remove(thread);
            waitQueue.resHolder = thread;
            if (waitQueue.transferPriority) {
                accessedSet.add(waitQueue);
                blockedQueue = null;
            }
        }


        /**
         * The thread with which this object is associated.
         */
        protected KThread thread;
        /**
         * The priority of the associated thread.
         */
        protected int priority;
        /**
         * 表示有效优先级大小.若不传递优先级则其一直与priority相等;若传递优先级但是当前线程没有占用任何资源则其也和priority相等
         */
        protected int effectivePriority;
        /**
         * 该集合记录了全部与本线程已经的获得资源相绑定的等待队列
         */
        protected Set<PriorityQueue> accessedSet = new HashSet<>();
        /**
         * 记录当前线程正在哪个阻塞队列中等待
         */
        protected PriorityQueue blockedQueue;

    }
}
