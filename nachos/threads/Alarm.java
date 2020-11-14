package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.LinkedList;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
        Machine.timer().setInterruptHandler(new Runnable() {
            public void run() {
                timerInterrupt();
            }
        });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
        boolean preState = Machine.interrupt().disable();//disable interrupt
        for (ThreadWaitTime t : ThreadsToWait) {
            if (t.wakeTime <= Machine.timer().getTime()) {//if this thread has waited for enough time,wake it up
                ThreadsToWait.remove(t);
                t.waitThread.ready();
            }
        }
        Machine.interrupt().restore(preState);
    }


    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param    x    the minimum number of clock ticks to wait.
     * @see    nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
        // for now, cheat just to get something working (busy waiting is bad)
        boolean intStatus = Machine.interrupt().disable();//disable interrupt
        long wakeTime = Machine.timer().getTime() + x;
        ThreadWaitTime waitingThread = new ThreadWaitTime(wakeTime, KThread.currentThread());
        ThreadsToWait.add(waitingThread);//Add the current thread to list
        KThread.sleep();//Put the current thread to sleep
        Machine.interrupt().restore(intStatus);
    }


    private class ThreadWaitTime {
        ThreadWaitTime(long wakeTime, KThread waitThread) {
            this.wakeTime = wakeTime;
            this.waitThread = waitThread;
        }

        KThread waitThread;
        long wakeTime;
    }

    public static void selfTest() {
        Lib.debug(dbgAlarm, "Enter KThread.selfTest");
        KThread test1 = new KThread(new PingTest(1));
        test1.fork();
        new PingTest(0).run();
    }

    private static class PingTest implements Runnable {
        PingTest(int which) {
            this.which = which;
        }

        public void run() {
            for (int i = 0; i < 5; i++) {
                if (i == 1 && which == 0) {
                    System.out.println("### thread " + which + " is going to sleep for 2500 ticks at time:" + Machine.timer().getTime());
                    new Alarm().waitUntil(2500);
                    System.out.println("### thread " + which + " came back at time:" + Machine.timer().getTime());
                }
                if (i == 1 && which == 1) {
                    System.out.println("### thread " + which + " is going to sleep for 500 ticks at time:" + Machine.timer().getTime());
                    new Alarm().waitUntil(500);
                    System.out.println("### thread " + which + " came back at time:" + Machine.timer().getTime());
                }
                System.out.println("*** thread " + which + " looped "
                        + i + " times");
                KThread.yield();
            }
        }

        private int which;
    }

    private static final char dbgAlarm = 'a';
    private static LinkedList<ThreadWaitTime> ThreadsToWait = new LinkedList<ThreadWaitTime>();

}
