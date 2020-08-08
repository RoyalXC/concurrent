[TOC]

# 1.Atomic*实现并发安全

```java
package com.jiangzheng.concurrent.test;

public class CountThread {

    private static int count;
    
    public static void inc() {
      count++;
    }
    
    public static void main(String[] args) {
        for (int i = 20; i > 0; i--) {
            new Thread(() ->  {
                for (int j = 5000; j > 0; j--) {
                    inc();
                }
            }, "thread-" + i).start();
        }
        
       while(Thread.activeCount() > 1) {
           Thread.yield();
       }
       
       System.out.println("final count: " + count);
    }
}
```
## 1.1 实现可见性

AtomicInteger.java：


```java
public class AtomicInteger extends Number implements java.io.Serializable {

    private static final long serialVersionUID = 6214790243416807050L;

    // setup to use Unsafe.compareAndSwapInt for updates
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicInteger.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private volatile int value;
    
    .....
}
```
Atomic* 类使用volatile变量，保证了多线线程下并发操作变量值的及时同步，即解决了可见性问题。

## 1.2 实现原子性

1.AtomicInteger.getAndIncrement():

```java
public final int getAndIncrement() {
   return unsafe.getAndAddInt(this, valueOffset, 1);
}
```
2.Unsafe.getAndAddInt()

```java
public final int getAndAddInt(Object paramObject, long paramLong, int aramInt){
    int i;
    do{
      i = getIntVolatile(paramObject, paramLong);
    } while (!compareAndSwapInt(paramObject, paramLong, i, i + paramInt));
    return i;
}

....

public final native boolean compareAndSwapInt(Object paramObject, long aramLong, int paramInt1, int paramInt2);
```

# 2 Unsafe类

Unsafe是JDK内部用的工具类。它通过暴露一些Java意义上说“不安全”的功能给Java层代码，来让JDK能够更多的使用Java代码来实现一些原本是平台相关的、需要使用native语言（例如C或C++）才可以实现的功能，提供了硬件级别的原子操作,如compareAndSwap*()方法.

Unsafe 通过直接暴露或者使用CAS机制调用这些硬件级别的原子操作，提供线程安全的原子操作。

该类不应该在JDK核心类库之外使用!

## 1.CAS机制

Compare And Swap 比较替换

CAS 定了三个值：内存地址V，旧的预期值A，要修改的新值B；通过内存地址V获取内存中的值和的预期值A比较，如果相同则替换并返回true，不相同则返回false；该比较替换操作是由计算机硬件级别提供的原子性操作。JDK底层实现并被Unsafe暴露给JDK核心类库使用。

Atomic*类或者Unsafe通过自旋的方式，检测CAS操作结果，当出现失败时，通过重新获取旧的预期值A，不断尝试更新内存地址V中的值。

CAS属于乐观锁，让线程不断去尝试更新，不会使线程阻塞挂起，导致

**CAS机制的问题:**

1.ABA问题

CAS通过值得比较来确定是否更新，当出现线程将内存值从A改成B再改成A，其他线程察觉不到该变化，而实际过程中内存值经历了变化过程，从而带来ABA问题。从JDK1.5开始提供了AtomicStampedReference类来解决ABA问题。

AtomicStampedReference.java:

```java
public class AtomicStampedReference<V> {    
    private static class Pair<T> {
        final T reference;
        final int stamp;
        private Pair(T reference, int stamp) {
            this.reference = reference;
            this.stamp = stamp;
        }
        static <T> Pair<T> of(T reference, int stamp) {
            return new Pair<T>(reference, stamp);
        }
    }

    private volatile Pair<V> pair;
    
    .....
   
}
```
2.AutomicInteger等原子类只能保证一个共享变量的线程安全，如果需要对多个共享变量操作时，从JDK1.5开始提供了AtomicReference.java，通过将多个共享变量定义成对象形式。

AtomicReference.java:

```java
public class AtomicReference<V> implements java.io.Serializable {
    
  	private static final long serialVersionUID = -1848883965231344442L;
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long valueOffset;

    static {
        try {
            valueOffset = unsafe.objectFieldOffset
                (AtomicReference.class.getDeclaredField("value"));
        } catch (Exception ex) { throw new Error(ex); }
    }

    private volatile V value;
    
    .....
    
}
```

3.CAS通过不断自旋的方式实现线程安全，当并发高，冲突多时，会导致CPU消耗增大；可以使用像LongAdder这样分段加锁的实现，来减少冲突。

## 2.重要方法解析

**1.long objectFieldOffset(Field field)** 

返回指定的变量在所属类的内存偏移地址

**2.boolean compareAndSwapLong(Object obj, long offset, long expect, long update)和类似方法**

比较对象 obj 中偏移量为 offset 的变量的值是不是和 expect 相等，相等则使用 update 值更新，然后返回 true，否者返回 false

**3.long getAndSetLong(Object obj, long offset, long update)和类似方法**

方法获取对象obj中偏移量为offset的变量volatile语义的值，并设置变量volatile语义的值为update

**4.long getAndAddLong(Object obj, long offset, long addValue)和类似方法**

 获取对象 obj 中偏移量为 offset 的变量 volatile 语义的值，并设置变量值为原始值 +addValue

**5.void park(boolean isAbsolute, long time)**

阻塞当前线程，其中参数isAbsolute等于false时候，time等于0表示一直阻塞，time大于0表示等待指定的time后阻塞线程会被唤醒，这个time是个相对值，是个增量值，也就是相对当前时间累加time后当前线程就会被唤醒。如果isAbsolute等于true，并且time大于0表示阻塞后到指定的时间点后会被唤醒，这里time是个绝对的时间，是某一个时间点换算为ms后的值。另外当其它线程调用了当前阻塞线程的interrupt方法中断了当前线程时候，当前线程也会返回，当其它线程调用了unpark方法并且把当前线程作为参数时候当前线程也会返回

**6.void unpark(Object thread)**

唤醒调用park后阻塞的线程，参数为需要唤醒的线程。

# 4.LockSupport 类

挂起和唤醒线程，LockSupport类与每个使用它的线程都会关联一个许可证，在默认情况下调用LockSupport的方法的线程是不持有许可证的，LockSupport是使用Unsafe类实现的。

方法解析：

**1.static void park()**

如果调用park的线程已经拿到与LockSupport关联的许可证，则调用LockSupport.park()时会马上返回，否则调用线程会被禁止参与线程的调度，也就是被阻塞

**2.static void park(Object blocker)**

和park方法类似，JDK推荐我们使用带有blocker参数的方法，并且blocker设置为this，这样在使用争端工具打印线程堆栈排产问题的时候就能知道是哪个类被阻塞了。

**3.static void unpark(Thread thread)**

调用unpark时，如果线程没有关联的许可证，则让thread线程持有

**4.static void parkNanos(long nanos)**

和park方法类似，如果调用parkNanos的线程已经拿到与LockSupport关联的许可证，则调用LockSupport.park()时会马上返回，否则调用线程会被挂起nanos时间后修改为自动返回

# 5.抽象同步队列AQS

AbstractQueuedSynchronizer抽象同步队列简称AQS，它是实现同步器的基础组件，并发包中锁的底层就是使用AQS实现的。

## 5.1.类结构

![AQS类结构](image\AQS类结构.png)



1.AQS是一个**FIFO**的双向队列,其内部通过节点**head**和**tail**记录队首和队尾元素，队列元素的类型为Node。

**2.Node对象：**

**thread**变量用来存放进入AQS队列里面的线程；

**SHARED**用来标记该线程是获取共享资源时被阻塞挂起后放入AQS队列的，**EXCLUSIVE**用来标记线程是获取独占资源时被挂起后放入AQS队列的；

**waitStatus**记录当前线程等待状态，可以为CANCELLED（线程被取消了）、SIGNAL（线程需要被唤醒）、CONDITION（线程在条件队列里面等待）、PROPAGATE（释放共享资源时需要通知其他节点）;

 **prev**记录当前节点的前驱节点，**next**记录当前节点的后继节点。      

3.在AQS中维持了一个单一的状态信息**state**，可以通过getState、setState、compareAndSetState函数修改其值。对于ReentrantLock的实现来说，state可以用来表示当前线程获取锁的可重入次数；对于读写锁ReentrantReadWriteLock来说，state的高16位表示读状态，也就是获取该读锁的次数，低16位表示获取到写锁的线程的可重入次数；对于semaphore来说，state用来表示当前可用信号的个数；对于CountDownlatch来说，state用来表示计数器当前的值。      

4.AQS有个内部类**ConditionObject**，用来结合锁实现线程同步。ConditionObject可以直接访问AQS对象内部的变量，比如state状态值和AQS队列ConditionObject是条件变量，每个条件变量对应一个条件队列（单向链表队列），其用来存放调用条件变量的await方法后被阻塞的线程，如类图所示，这个条件队列的头、尾元素分别为firstWaiter和lastWaiter。

## 5.2.锁的底层支持

对于AQS来说，线程同步的关键是对状态值state进行操作。根据state是否属于一个线程，操作state的方式分为独占方式和共享方式。

### 5.2.1独占方式的实现

使用独占方式获取的资源是与具体线程绑定的，就是说如果一个线程获取到了资源，就会标记是这个线程获取到了，其他线程再尝试操作state获取资源时会发现当前该资源不是自己持有的，就会在获取失败后被阻塞。比如独占锁ReentrantLock的实现，当一个线程获取了ReentrantLock的锁后，在AQS内部会首先使用CAS操作把state状态值从0变为1，然后设置当前锁的持有者为当前线程，当该线程再次获取锁时发现它就是锁的持有者，则会把状态值从1变为2，也就是设置可重入次数，而当另外一个线程获取锁时发现自己并不是该锁的持有者就会被放入AQS阻塞队列后挂起。

**独占方式下获取和释放资源使用的方法为：** void acquire（int arg）、void acquireInterruptibly （int arg）、boolean release（int arg）

**（1）获取资源的流程：**

当一个线程调用acquire（int arg）方法获取独占资源时，会首先使用tryAcquire方法尝试获取资源，具体是设置状态变量state的值，成功则直接返回，失败则将当前线程封装为类型为Node.EXCLUSIVE的Node节点后插入到AQS阻塞队列的尾部，并调用LockSupport.park（this）方法挂起自己。

```java
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}
```
**（2）释放资源的流程：**

当一个线程调用release（int arg）方法时会尝试使用tryRelease操作释放资源，这里是设置状态变量state的值，然后调用LockSupport.unpark（thread）方法激活AQS队列里面被阻塞的一个线程（thread）。被激活的线程则使用tryAcquire尝试，看当前状态变量state的值是否能满足自己的需要，满足则该线程被激活，然后继续向下运行，否则还是会被放入AQS队列并被挂起。

```java
public final boolean release(int arg) {
	if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}
```
### 5.2.2独占式ReentrantLock的实现

AQS类并没有提供可用的tryAcquire和tryRelease方法，AQS是锁阻塞和同步器的基础框架，tryAcquire和tryRelease需要由具体的子类来实现。

**1.子类在实现tryAcquire和tryRelease时需要定义：**

(1) 根据具体场景使用CAS算法尝试修改state状态值，成功则返回true，否则返回false。

(2) 在调用acquire和release方法时state状态值的增减代表什么含义。

**2.tryAcquire的实现：**

(1)定义当state为0时表示锁空闲，为1时表示锁已经被占用。

(2)在重写tryAcquire时，在内部需要使用CAS算法查看当前state是否为0，如果为0则使用CAS设置为1，并设置当前锁的持有者为当前线程，而后返回true，如果CAS失败则返回false。

```java
final boolean nonfairTryAcquire(int acquires) {
    final Thread current = Thread.currentThread();
    int c = getState();
    if (c == 0) {
        if (compareAndSetState(0, acquires)) {
          setExclusiveOwnerThread(current);
          return true;
        }
    }
    else if (current == getExclusiveOwnerThread()) {
        int nextc = c + acquires;
        if (nextc < 0) // overflow
          throw new Error("Maximum lock count exceeded");
        setState(nextc);
        return true;
    }
    return false;
}
```

**2.tryRelease的实现：**

(1) 定义当state为0时表示锁空闲，为1时表示锁已经被占用。

(2)tryRelease 独占方式所以不需要加同步机制（CAS机制）保证先线程安全，state减一，ReentrantLock可重入，所以当state=0时，完全释放锁，返回true，否则返回false

```java
protected final boolean tryRelease(int releases) {
    int c = getState() - releases;
    if (Thread.currentThread() != getExclusiveOwnerThread())
        throw new IllegalMonitorStateException();
    boolean free = false;
    if (c == 0) {//
    	free = true;
        setExclusiveOwnerThread(null);
    }
    setState(c);
    return free;
}
```



### 5.2.3共享方式的实现

对应共享方式的资源与具体线程是不相关的，当多个线程去请求资源时通过CAS方式竞争获取资源，当一个线程获取到了资源后，另外一个线程再次去获取时如果当前资源还能满足它的需要，则当前线程只需要使用CAS方式进行获取即可。比如Semaphore信号量，当一个线程通过acquire（）方法获取信号量时，会首先看当前信号量个数是否满足需要，不满足则把当前线程放入阻塞队列，如果满足则通过自旋CAS获取信号量。 

**共享方式下获取和释放资源的方法为：** void acquireShared（int arg）、

void acquireSharedInterruptibly（int arg）、boolean releaseShared（int arg）

**（1）获取资源的流程：**

当线程调用acquireShared（int arg）获取共享资源时，会首先使用tryAcquireShared尝试获取资源，具体是设置状态变量state的值，成功则直接返回，失败则将当前线程封装为类型为Node.SHARED的Node节点后插入到AQS阻塞队列的尾部，并使用LockSupport.park（this）方法挂起自己。

```java
public final void acquireShared(int arg) {
  	if (tryAcquireShared(arg) < 0)
    	doAcquireShared(arg);
}
```



```java
private void doAcquireShared(int arg) {
  final Node node = addWaiter(Node.SHARED);
  boolean failed = true;
  try {
      boolean interrupted = false;
      for (;;) {
          final Node p = node.predecessor();
          if (p == head) {
              int r = tryAcquireShared(arg);
              if (r >= 0) {
                setHeadAndPropagate(node, r);
                p.next = null; // help GC
                if (interrupted)
                	selfInterrupt();
                failed = false;
                return;
              }
          }
          if (shouldParkAfterFailedAcquire(p, node) &&
          parkAndCheckInterrupt())
          interrupted = true;
       }
  } finally {
    if (failed)
    	cancelAcquire(node);
  }
}
```
**（2）释放资源的流程：**

当一个线程调用releaseShared（int arg）时会尝试使用tryReleaseShared操作释放资源，这里是设置状态变量state的值，然后使用LockSupport.unpark（thread）激活AQS队列里面被阻塞的一个线程（thread）。被激活的线程则使用tryReleaseShared查看当前状态变量state的值是否能满足自己的需要，满足则该线程被激活，然后继续向下运行，否则还是会被放入AQS队列并被挂起。

```java
public final boolean releaseShared(int arg) {
  if (tryReleaseShared(arg)) {
    doReleaseShared();
    return true;
  }
  return false;
}
```



    private void doReleaseShared() {
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;![en1-image](image\en1-image.png)
                if (ws == Node.SIGNAL) {
                	if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                	continue;            // loop to recheck cases
                	unparkSuccessor(h);
                }
                else if (ws == 0 &&
                    !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
            break;
        }
    }


### 5.2.4 AQS中的队列操作

**入队操作：**当一个线程获取锁失败后该线程会被转换为Node节点，然后就会使用enq（final Node node）方法将该节点插入到AQS的阻塞队列。



![enq](image\enq.png)

​	如图-1代码在第一次循环中，当要在AQS队列尾部插入元素时，AQS队列状态如图-2中（default）所示。也就是队列头、尾节点都指向null；当执行代码（1）后节点t指向了尾部节点，这时候队列状态如图-2中（I）所示。这时候t为null，故执行代码（2），使用CAS算法设置一个哨兵节点为头节点，如果CAS设置成功，则让尾部节点也指向哨兵节点，这时候队列状态如左图中（II）所示。      

 	到现在为止只插入了一个哨兵节点，还需要插入node节点，所以在第二次循环后执行到代码（1），这时候队列状态如图-2（III）所示；然后执行代码（3）设置node的前驱节点为尾部节点，这时候队列状态如图-2中（IV）所示；然后通过CAS算法设置node节点为尾部节点，CAS成功后队列状态如图-2中（V）所示；CAS成功后再设置原来的尾部节点的后驱节点为node，这时候就完成了双向链表的插入，此时队列状态如图-2中（VI）所示。

## 5.3.条件变量

### 5.3.1条件变量

![condtest](image\condtest.png)

### 5.3.2wait（）和signal（）

![condi-func](image\cond-func.png)

### 5.3.3总结

### ![cond-res](image\cond-res.png)