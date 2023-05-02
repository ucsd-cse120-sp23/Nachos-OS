package nachos.threads;

import nachos.machine.*;
import java.util.HashMap;


/**
 * A <i>Rendezvous</i> allows threads to synchronously exchange values.
 */
public class Rendezvous {

    private final HashMap<Integer, Pair> exchangeMap;
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
        if (!exchangeMap.containsKey(tag)) {
            Pair pair = new Pair(lock, value);
            exchangeMap.put(tag, pair);
            pair.getCondition().sleep();
            int otherValue = exchangeMap.get(tag).getValue();
            exchangeMap.remove(tag);
            lock.release();
            return otherValue;
        } else {
            Pair pair = exchangeMap.get(tag);
            int otherValue = pair.getValue();
            exchangeMap.put(tag, new Pair(lock, value));
            pair.getCondition().wake();
            lock.release();
            return otherValue;
        }

        // will be deleted!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // lock.acquire();
        // //first get the condition that corresponds to specific tag
        // Condition condition = conditions.get(tag); 
        // if (condition == null) {
        //     condition = new Condition(lock);
        //     conditions.put(tag, condition);
        //     values.put(tag, value);
        // }

        // if (!firstArrived) {
        //     firstArrived = true;
        //     firstValue = value;
        //     while(!firstArrived) {
        //         condition.sleep();
        //     }
        //     Integer secondValue = values.get(tag);
        //     values.put(tag, null);
        //     firstValue = null;
        //     firstArrived = false;
        //     condition.wake();
        //     lock.release();
        //     return secondValue;
        // } else {
        //     Integer secondValue = value;
        //     values.put(tag, secondValue);
        //     firstArrived = false;
        //     condition.wake();
        //     lock.release();
        //     return firstValue;
        // }


        // if (!isAWaiting) {
        //     valueA = value;
        //     isAWaiting = true;
        //     while(!isBWaiting) {
        //         conditionA.sleep();
        //     }
        //     int result = valueB;
        //     valueB = null;                     //it cannot use data structures other than locks and condition variables to store references to threads.
        //     isBWaiting = false;                
        //     conditionB.wake();
        //     lock.release();
        //     return result;
        // } else {
        //     valueB = value;
        //     isBWaiting = true;
        //     while(!isAWaiting) {
        //         conditionB.sleep();
        //     }
        //     int result = valueA;
        //     valueA = null;
        //     isAWaiting = false;
        //     conditionA.wake();
        //     lock.release();
        //     return result;
        // }
    }

    private static class Pair {
        private Condition condition;
        private Integer value;

        public Pair(Lock lock, int value) {
            condition = new Condition(lock);
            this.value = value;
        }

        public boolean isFull() {
            return value != null;
        }

        public Integer getValue() {
            return value;
        }

        // public void setValue(int value) {
        //     this.value = value;
        // }

        // public void setCondition(Lock lock) {
        //     condition = new Condition(lock);
        // }

        public Condition getCondition() {
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
                Lib.assertTrue (recv == 1, "Was expecting " + 1 + " but received " + recv);
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
                Lib.assertTrue (recv == -1, "Was expecting " + -1 + " but received " + recv);
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
                Lib.assertTrue (recv == 4, "Was expecting " + 4 + " but received " + recv);
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
                Lib.assertTrue (recv == 2, "Was expecting " + 2 + " but received " + recv);
                System.out.println ("Thread " + KThread.currentThread().getName() + " received " + recv);
            }
        });
        t4.setName("t4");


        t1.fork(); t2.fork(); 
        // assumes join is implemented correctly
        t1.join(); t2.join(); 

        t3.fork(); t4.fork();
        t3.join(); t4.join();
    }

    // Invoke Rendezvous.selfTest() from ThreadedKernel.selfTest()

    public static void selfTest() {
	    // place calls to your Rendezvous tests that you implement here
	    rendezTest1();
    }
}


