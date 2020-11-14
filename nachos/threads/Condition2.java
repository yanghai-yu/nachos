package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see    nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param    conditionLock    the lock associated with this condition
     * variable. The current thread must hold this
     * lock whenever it uses <tt>sleep()</tt>,
     * <tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
        this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        //关中断
        boolean intStatus = Machine.interrupt().disable();
        waitQueue.waitForAccess(KThread.currentThread());
        haveWaiter = true;
        //释放锁
        conditionLock.release();
        //线程挂起
        KThread.sleep();
        conditionLock.acquire();
        Machine.interrupt().restore(intStatus);

    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());

        boolean intStatus = Machine.interrupt().disable();

        KThread waitThread = waitQueue.nextThread();
        if (waitThread != null) {
            waitThread.ready();
        } else {
            haveWaiter = false;
        }
        Machine.interrupt().restore(intStatus);
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
        Lib.assertTrue(conditionLock.isHeldByCurrentThread());
        boolean intStatus = Machine.interrupt().disable();

        while (haveWaiter) {
            wake();
        }
        Machine.interrupt().restore(intStatus);
    }

    private Lock conditionLock;
    //曾经考虑过在waitQueue.nextThread()时判断非空，但当执行waitAll()的while时
    //由于我们只需要判断状态而不需要取出线程，于是选择定义了一个状态位来表示当前的
    //waitQueue中有无线程
    private boolean haveWaiter = false;
    //定义一个等待队列
    private ThreadQueue waitQueue =
            ThreadedKernel.scheduler.newThreadQueue(false);
}
