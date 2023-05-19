package nachos.threads;

import nachos.machine.*;
import java.util.HashMap;



/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {

    private final HashMap<Integer, Pair> exchangeMap;
    private final HashMap<Integer, Boolean> TagIsExchanging;
    private final Lock lock;

    // private Lock lock;
    // private Integer firstValue;
    // private Integer valueB;
    // private final HashMap <Integer, Condition> conditions;
    // private final HashMap <Integer, Integer> values;
    // private boolean firstArrived;
    // private boolean isBWaiting; 


    

    /**
     * Allocate a new Rendezvous.
     */
    public Rendezvous () {

        lock = new Lock();
        exchangeMap = new HashMap<Integer, Pair>();
        TagIsExchanging = new HashMap<Integer, Boolean>();
        // conditions = new HashMap<Integer, Condition>();
        // values = new HashMap<Integer, Integer>();
        // firstArrived = false;
        // firstValue = null;
        // conditionA = new Condition(lock);
        // conditionB = new Condition(lock);
        // isAWaiting = false;
        // isBWaiting = false;

    }

    /**
     * Synchronously exchange a value with another thread.  The first
     * thread A (with value X) to exhange will block waiting for
     * another thread B (with value Y).  When thread B arrives, it
     * will unblock A and the threads will exchange values: value Y
     * will be returned to thread A, and value X will be returned to
     * thread B.
     *
     * Different integer tags are used as different, parallel
     * synchronization points (i.e., threads synchronizing at
     * different tags do not interact with each other).  The same tag
     * can also be used repeatedly for multiple exchanges.
     *
     * @param tag the synchronization tag.
     * @param value the integer to exchange.
     */
    public int exchange (int tag, int value) {
        lock.acquire();
        Condition2 condition = new Condition2(lock);
        if (TagIsExchanging.containsKey(tag)) {
            while (TagIsExchanging.get(tag)) {
                condition.sleepFor(1000);
            }
        }
        if (!exchangeMap.containsKey(tag)) {
            Pair pair = new Pair(condition, value);
            exchangeMap.put(tag, pair);
            TagIsExchanging.put(tag, false);
            pair.getCondition().sleep();
            int otherValue = exchangeMap.get(tag).getValue();
            exchangeMap.remove(tag);
            // \\\\\\\\\\\\\\\\\\\\\\\\\\
            TagIsExchanging.put(tag, false);
            lock.release();
            return otherValue;
        } else {
            Pair pair = exchangeMap.get(tag);
            int otherValue = pair.getValue();
            
            exchangeMap.put(tag, new Pair(condition, value));
            TagIsExchanging.put(tag, true);
            pair.getCondition().wake();
            lock.release();
            return otherValue;
           
        }

        //-------------------------------------------
        // Way 2

        // if (!exchangeMap.containsKey(tag)) {
        //     Condition condition = new Condition(lock);
        //     Pair pair = new Pair(condition, value);
        //     exchangeMap.put(tag, pair);
        //     pair.getCondition().sleep();
        //     int otherValue = exchangeMap.get(tag).getValue();
        //     exchangeMap.remove(tag);
        //     lock.release();
        //     return otherValue;
        // }
        
        //-------------------------------------------------------------
        // Way 3
        // while (TagIsExchanging.get)

        
    }

    private static class Pair {
        private Condition2 condition;
        private Integer value;
        // private boolean isExchanging;

        public Pair(Condition2 condition, int value) {
            this.condition = condition;
            this.value = value;
        }


        public Integer getValue() {
            return value;
        }


        public Condition2 getCondition() {
            return condition;
        }
        
    }


    // Place Rendezvous test code inside of the Rendezvous class.

    public static void rendezTest1() {
        final Rendezvous r = new Rendezvous();

        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 2;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t3.setName("t3");

        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 4;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t4.setName("t4");

        KThread t5 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 8;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t5.setName("t5");

        KThread t6 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 10;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t6.setName("t6");

        KThread t7 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 15;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t7.setName("t7");

        KThread t8 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 25;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t8.setName("t8");


        t1.fork(); t2.fork(); t3.fork(); t4.fork(); t5.fork(); t6.fork(); t7.fork(); t8.fork();
        // assumes join is implemented correctly
        t1.join(); t2.join(); t3.join(); t4.join(); t5.join(); t6.join(); t7.join(); t8.join();

       
        
    }

    public static void rendezTest2() {
        final Rendezvous r = new Rendezvous();
        final Rendezvous z = new Rendezvous();
         KThread t100 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -100;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = z.exchange (tag, send);
                //Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t100.setName("t100");


        KThread t1 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = -1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t1.setName("t1");

        KThread t2 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 1;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t2.setName("t2");

         KThread t200 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 100;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = z.exchange (tag, send);
                //Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t200.setName("t200");

        KThread t3 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 2;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t3.setName("t3");

         KThread t300 = new KThread( new Runnable () {
            public void run() {
                int tag = 0;
                int send = 200;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = z.exchange (tag, send);
                //Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t300.setName("t300");

        KThread t4 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 4;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = r.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t4.setName("t4");

        KThread t400 = new KThread( new Runnable () {
            public void run() {
                int tag = 1;
                int send = 400;

                System.out.println ("Thread " + KThread.currentThread().getName() + " exchanging " + send);
                int recv = z.exchange (tag, send);
                //Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t400.setName("t400");


        t1.fork(); t100.fork(); t2.fork(); t3.fork(); t4.fork(); t200.fork(); t300.fork(); t400.fork();
        // assumes join is implemented correctly
        t1.join(); t100.join(); t2.join(); t3.join(); t4.join(); t200.join(); t300.join(); t400.join();

       
        
    }

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
	    // place calls to your Rendezvous tests that you implement here
	   
        rendezTest1();
    }
}



