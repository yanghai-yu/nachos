package nachos.threads;

public class Condition2Test {
    Lock conlock = new Lock();

    Condition2 c2test =new Condition2(conlock);//c2test为共有条件变量

    public Condition2Test(){

    }

    public void simpleCondition2Test(){
        System.out.println("\n ***Condition2Test is now executing. ***");

        System.out.println("first 10000");

        final MyCount myCount = new MyCount("00001",10000);

        KThread thread1 = new KThread(new Runnable(){
            public void run(){
                new SaveThread("hhh",myCount,2000);

                System.out.println("hhh goes to sleep");

                conlock.acquire();

                c2test.sleep();

                System.out.println("hhh reacquires lock when woken.");

                conlock.release();

                System.out.println("hhh is awake!");

                myCount.save(2000,"hhh");

            }

        });

        KThread thread2 = new KThread(new Runnable(){
            public void run(){
                new SaveThread("yyy",myCount,3000);

                System.out.println("yyy goes to sleep");

                conlock.acquire();

                c2test.sleep();

                System.out.println("yyy reacquires lock when woken.");

                conlock.release();

                System.out.println("yyy is awake!");

                myCount.save(3000,"yyy");

            }

        });

        KThread thread3 = new KThread(new Runnable(){
            public void run(){
                System.out.println("thread3 waking up the thread");

                conlock.acquire();

                c2test.wake();
                c2test.wake();

                conlock.release();

                System.out.println("hhh and yyy woke up by wakeAll");

            }

        });

        thread1.fork();

        thread2.fork();

        thread3.fork();

        //thread1.join();

        thread1.join();

        thread2.join();

        thread3.join();

        // thread2.yield();

        System.out.println("***Condition2Test finished.***\n");

    }

}

class SaveThread extends KThread{
    private String name;

    private MyCount myCount;

    private int x;

    SaveThread(String name,MyCount myCount,int x){
        this.name = name;

        this.myCount = myCount;

        this.x = x;

    }

    public void run(){
        myCount.save(x,name);

    }

}

class MyCount{
    private String id;

    private int cash;

    Lock lock = new Lock();

    Condition2 c2test = new Condition2(lock);

    MyCount(String id,int cash){
        this.id = id;

        this.cash = cash;

    }

    public void save(int x,String name){
        lock.acquire();

        if(x>0){
            cash+=x;

            System.out.println(name +"save"+x+"mount is"+cash);

        }

        lock.release();

    }

}