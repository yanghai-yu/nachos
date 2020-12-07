package nachos.threads;
import nachos.ag.BoatGrader;
import nachos.machine.Lib;

public class Boat
{
    static BoatGrader bg;
    
    public static void selfTest()
    {
	BoatGrader b = new BoatGrader();

//	System.out.println("\n ***Testing Boats with 2 children, 1 adult***");
//  	begin(1, 2, b);

  	System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
  	begin(3, 3, b);
    }

    public static void begin(int adults, int children, BoatGrader b) {
        // Store the externally generated autograder in a class
        // variable to be accessible by children.
        bg = b;

        // Instantiate global variables here

        // Create threads here. See section 3.4 of the Nachos for Java
        // Walkthrough linked from the projects page.

//		Runnable r = new Runnable() {
//			public void run() {
//				SampleItinerary();
//			}
//		};
//		KThread t = new KThread(r);
//		t.setName("Sample Boat Thread");
//		t.fork();

        //new出各个线程
        KThread[] aThreads = new KThread[adults];
        KThread[] cThreads = new KThread[children];
        for(int i = 0;i<adults;i++) {
            aThreads[i] = new KThread(new Adult(bg));
            aThreads[i].setName("Adult:"+i);
        }
        for(int i = 0;i<children;i++) {
            cThreads[i] =  new KThread(new Child(bg));
            cThreads[i].setName("Child:"+i);
        }

        //fork出各个线程
        for(int i = 0;i<adults;i++) {
            aThreads[i].fork();
        }
        for(int i = 0;i<children;i++) {
            cThreads[i].fork();
        }

        //将主线程join到所有儿童线程后面
        for(int i = 0;i<children;i++) {
            cThreads[i].join();
        }

        System.out.println("已经全员到达Molokai岛");

    }


    static void AdultItinerary()
    {
	bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 

	/* This is where you should put your solutions. Make calls
	   to the BoatGrader to show that it is synchronized. For
	   example:
	       bg.AdultRowToMolokai();
	   indicates that an adult has rowed the boat across to Molokai
	*/
    }

    static void ChildItinerary()
    {
	bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
	//DO NOT PUT ANYTHING ABOVE THIS LINE. 
    }

    static void SampleItinerary()
    {
	// Please note that this isn't a valid solution (you can't fit
	// all of them on the boat). Please also note that you may not
	// have a single thread calculate a solution and then just play
	// it back at the autograder -- you will be caught.
	System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
	bg.AdultRowToMolokai();
	bg.ChildRideToMolokai();
	bg.AdultRideToMolokai();
	bg.ChildRideToMolokai();
    }


}
/**
 * 用于作为儿童，大人的父类
 * 记录：大人的数量，小孩的数量，Oahu岛上大人数量，Oahu岛上小孩的数量，Molokai岛上大人的数量，Molokai岛上小孩的数量
 * @author wojiaodpf
 *
 */
class Traveler{
    private static int childNum = 0;//所有小孩的数目
    private static int adultNum = 0;//所有大人的数目
    private static int childNumInOahu = 0;//在Oahu岛上小孩的数目
    private static int adultNumInOahu = 0;//在Oahu岛上大人的数目
    private static int childNumInMolokai = 0;//在Molokai岛上小孩的数目
    private static int adultNumInMolokai = 0;//在Molokai岛上大人的数目
    static Lock mutex = new Lock();//互斥量，用于线程同步
    static Condition2 waitChildInOahu = new Condition2(mutex);//在Oahu等待的儿童线程
    static Condition2 waitAdultInOahu = new Condition2(mutex);//在Oahu等待的成人线程
    static Condition2 waitChildInMolokai = new Condition2(mutex);//在Molokai等待的儿童线程
    static Condition2 waitInBoat = new Condition2(mutex);//在船上等待另一个乘客到来的线程
    static Condition2 waitForDrive = new Condition2(mutex);//等待船开的线程
    static boolean boatPosition = false;//船位于的位置，false代表位于Oahu岛，true代表位于Molokai岛
    static boolean ifHavePilot = false;//船上是否有人作为船长，false代表没有，true代表有人准备开船
    static boolean ifIsDriving = false;//声明船是否在使用，如果为false代表目前不在运行，如果为true代表目前正在运行

    boolean position;//乘客位于的位置（false代表位于Oahu岛；true代表Molokai岛）
    BoatGrader bg;//船的引用

    /**
     * 新加一个成人时调用的方法
     */
    public static void addAdult() {
        Traveler.adultNum++;//大人数量+1
        Traveler.adultNumInOahu++;//在Oahu岛的大人数量+1
    }

    /**
     * 新加一个小孩时调用的方法
     */
    public static void addChild() {
        Traveler.childNum++;//小孩的数量+1
        Traveler.childNumInOahu++;//在Oahu岛的小孩数量+1
    }

    /**
     * 获得成人的总数
     * @return
     */
    public static int getAdultNum() {
        return Traveler.adultNum;
    }


    /**
     * 获得小孩的总数
     * @return Traveler.childNum 小孩总数
     */
    public static int getChildNum() {
        return Traveler.childNum;
    }

    /**
     * 获取在Oahu岛上的成人的数目，前提是发起请求的乘客位于Oahu岛
     * @param t 发起请求的乘客本身
     * @return Oahu岛上的成人的数目
     */
    public static int getAdultNumInOahu(Traveler t) {
        Lib.assertTrue(t.position==false);//确定发起请求的乘客位于Oahu岛

        return Traveler.adultNumInOahu;//返回在Oahu岛的成人乘客的数目
    }

    /**
     * 获取在Oahu岛上的儿童的数目，前提是发起请求的乘客位于Oahu岛
     * @param t 发起请求的乘客本身
     * @return Oahu岛上的儿童的数目
     */
    public static int getChildNumInOahu(Traveler t) {
        Lib.assertTrue(t.position==false);//确定发起请求的乘客位于Oahu岛

        return Traveler.childNumInOahu;//返回在Oahu岛的儿童乘客的数目
    }

    /**
     * 获取在Molokai岛上的成人的数目，前提是发起请求的乘客位于Molokai岛
     * @param t 发起请求的乘客本身
     * @return Molokai岛上的成人的数目
     */
    public static int getAdultNumInMolokai(Traveler t) {
        Lib.assertTrue(t.position==true);//确定发起请求的乘客位于Molokai岛

        return Traveler.adultNumInMolokai;//返回在Molokai岛上的成人的数目
    }

    /**
     * 获取在Molokai岛上的儿童的数目，前提是发起请求的乘客位于Molokai岛
     * @param t 发起请求的乘客本身
     * @return Molokai岛上的儿童数目
     */
    public static int getChildNumIntMolokai(Traveler t) {
        Lib.assertTrue(t.position==true);//确定发起请求的乘客位于Molokai岛

        return Traveler.childNumInMolokai;//返回在Molokai岛上儿童的数目
    }

    /**
     * 位于Oahu岛上的一个成人前往Molokai岛上，前提是成人位置为Oahu岛
     * @param t 成人
     */
    public static void adultToMolokai(Adult t) {
        Lib.assertTrue(t.position==false);//确定发起请求的乘客位于Oahu岛

        Traveler.adultNumInOahu--;//位于Oahu岛的成人数量-1
        Traveler.adultNumInMolokai++;//位于Molokai岛的成人数量+1
        t.position = true;//将成人的位置变为true（Molokai岛）
    }

    /**
     * 位于Oahu岛的一个儿童前往Molokai岛上，前提是儿童位置为Oahu岛
     * @param t 儿童
     */
    public static void childToMolokai(Child t) {
        Lib.assertTrue(t.position==false);//确定发起请求的乘客位于Oahu岛

        Traveler.childNumInOahu--;//位于Oahu岛的儿童数量-1
        Traveler.childNumInMolokai++;//位于Molokai岛的儿童数量+1
        t.position = true;//将儿童的位置变为true（Molokai岛）
    }

    /**
     * 位于Molokai岛上的一个儿童前往Oahu岛上，前提是儿童位置为Molokai岛
     * @param t 儿童
     */
    public static void childToOahu(Child t) {
        Lib.assertTrue(t.position==true);//确定发起请求的乘客位于Molokai岛

        Traveler.childNumInMolokai--;//位于Molokai岛的儿童数量-1
        Traveler.childNumInOahu++;//位于Oahu岛的儿童数量+1
        t.position = false;//将儿童的位置变为false（Oahu岛）
    }

    /**
     * 用于判断是否所有人都位于Molokai岛上了
     * @param t 调用的乘客本身
     * @return 如果所有人都位于Molokai岛上了，则返回true；否则返回false
     */
    public static boolean peopleAllInMolokai(Traveler t) {
        Lib.assertTrue(t.position==true);//确定发起请求的乘客位于Molokai岛

        //返回是否在Molokai岛上的成人数量等于所有成人数量，并且在Molokai岛上儿童数量等于所有儿童数量
        return (Traveler.adultNumInMolokai==Traveler.adultNum)&&(Traveler.childNumInMolokai==Traveler.childNum);
    }
}
/**
 * 这是用来仿真模拟成人行为的类
 * @author wojiaodpf
 *
 */
class Adult extends Traveler implements Runnable{

    public Adult(BoatGrader bg) {
        this.bg = bg;//记录船的引用
        Traveler.addAdult();//新增大人
        this.position = false;//初始化大人的位置为Oahu岛(false)
    }

    @Override
    public void run() {
        bg.initializeAdult();//声明fork()了一个大人

        Traveler.mutex.acquire();//获得锁
        while(Traveler.boatPosition==true||Traveler.ifIsDriving==true||Traveler.getChildNumInOahu(this)>=2) {
            //当船在Molokai岛上，或者船正在开，或者在Oahu岛上的儿童数量大于2时，都要进入睡眠，等待唤醒
            Traveler.waitAdultInOahu.sleep();
        }

        //此时船在Oahu岛上，船不在运行，并且Oahu岛上儿童只有一个
        //则大人乘船前往Molokai岛
        Traveler.ifIsDriving = true;//开船
        bg.AdultRowToMolokai();//声明大人开船从Oahu岛前往Molokai岛
        Traveler.adultToMolokai(this);//将两岛的大人数量修改，并且将大人位置变为Molokai岛
        Traveler.ifIsDriving = false;//下船，船并不在使用
        Traveler.boatPosition = true;//将船的位置变为Molokai岛
        Traveler.waitChildInMolokai.wake();//唤醒一个在Molokai岛等待的儿童，让儿童去接在Oahu岛上剩下的人
        Traveler.mutex.release();//释放锁
    }

}
/**
 * 这是用来仿真模拟儿童行为的类
 * @author wojiaodpf
 *
 */
class Child extends Traveler implements Runnable{

    public Child(BoatGrader bg) {
        this.bg = bg;//记录船的应用
        Traveler.addChild();//新增小孩
        this.position = false;//初始化小孩的位置为Oahu岛(false)
    }

    @Override
    public void run() {
        bg.initializeChild();//声明fork()了一个小孩


        while(true) {
            Traveler.mutex.acquire();


            if(position==true) {
                //如果身处Molokai岛
                while(Traveler.boatPosition==false||Traveler.ifIsDriving==true) {
                    //如果船在Oahu岛，或者正在运行，那么就要睡觉，睡在Molokai岛的儿童进程等待队列中
                    Traveler.waitChildInMolokai.sleep();
                }
                //睡醒了，此时船位于true（Molokai岛），并且船并未运行

                if(Traveler.peopleAllInMolokai(this)) {
                    //如果全部乘客都到Molokai岛了，那么就可以返回了
                    Traveler.waitChildInMolokai.wakeAll();//唤醒所有在Molokai岛等待的儿童
                    Traveler.mutex.release();//释放锁
                    break;
                }
                else {
                    //否则还有人没到Molokai岛，则由这个儿童再回Oahu岛去接
                    //这个儿童独自开船从Molokai岛返回Oahu岛
                    Traveler.ifIsDriving = true;//设置为正在开船
                    bg.ChildRowToOahu();//声明孩子驾驶船从Molokai岛前往Oahu岛
                    Traveler.childToOahu(this);//儿童从Molokai岛前往Oahu岛，两岛上儿童数目变化，并且儿童的位置变为false（Oahu岛）
                    Traveler.ifIsDriving = false;//到达Oahu岛，船不在运行
                    Traveler.boatPosition = false;//将船的位置变为Oahu岛
                    Traveler.waitChildInOahu.wake();//唤醒一个正在Oahu岛等待着的儿童（似乎可以不唤醒，因为自己是没有睡的，下一次循环是能够充当开船的角色）
                    Traveler.mutex.release();//释放锁
                }
            }
            else {
                //否则如果身处Oahu岛
                while(Traveler.boatPosition==true||Traveler.ifIsDriving==true) {
                    //当船位于Molokai岛的时候，就睡觉,等待船回到Oahu岛
                    //或者船正在运行的时候，也睡觉
                    Traveler.waitChildInOahu.sleep();
                }

                if(Traveler.getChildNumInOahu(this)>=2) {
                    //如果在Oahu岛的儿童数量大于等于2，那么就准备运送两名儿童去Molokai岛
                    if(Traveler.ifHavePilot==false) {
                        //如果没有人在开船，那么就自己来开船
                        Traveler.ifHavePilot=true;
                        Traveler.waitChildInOahu.wake();//唤醒另一个儿童线程让他来搭船
                        Traveler.waitInBoat.sleep();//在船上等待另一个儿童乘客的到来
                        //乘客已经到来，并且将唤醒自己，所以开船前往Molokai岛
                        bg.ChildRowToMolokai();//声明孩子驾驶船从Oahu岛前往Molokai岛
                        Traveler.childToMolokai(this);//儿童从Oahu岛到Molokai岛，两岛上儿童数目改变，儿童位置改变为true（Molokai岛）
                        Traveler.waitForDrive.wake();//叫醒等待船开的儿童进程，告诉他已经到达Molokai岛了
                        Traveler.ifHavePilot = false;//开船的儿童下船，就没有开船的人了
                        Traveler.mutex.release();//释放锁
                    }else {
                        //如果有人在开船，那么只需要坐船前往Molokai岛
                        Traveler.waitInBoat.wake();//唤醒正在等待乘客儿童到来的开船儿童
                        Traveler.ifIsDriving = true;//乘客已经上满，不能再商人，设置船为正在运行状态
                        Traveler.waitForDrive.sleep();//等待开船儿童将船开到Molokai岛
                        //船已经开到Molokai岛了，并且开船儿童已经下船
                        bg.ChildRideToMolokai();//声明一个孩子搭船从Oahu岛前往Molokai岛
                        Traveler.childToMolokai(this);//儿童从Oahu岛到Molokai岛，两岛上儿童数目改变，儿童位置改变为true（Molokai岛）
                        Traveler.ifIsDriving = false;//乘客儿童和开船儿童均已下船，声明船已不在使用
                        Traveler.boatPosition = true;//将船的位置变为Molokai岛
                        Traveler.waitChildInMolokai.wake();//唤醒一个在Molokai岛等待的儿童（似乎可以不唤醒，因为自己是没有睡的，下一次循环是能够充当开船的角色）
                        Traveler.mutex.release();//释放锁
                    }
                }
                else {
                    //如果在Oahu岛的儿童数量小于2
                    if(Traveler.getAdultNumInOahu(this)>0) {
                        //如果还有成人在Oahu岛，则让这个成人前往Molokai岛
                        Traveler.waitAdultInOahu.wake();//唤醒一个在Oahu岛等待的成年人
                        Traveler.waitChildInOahu.sleep();//自己继续在Oahu岛继续等待
                        Traveler.mutex.release();
                    }
                }
            }
        }
    }

}

