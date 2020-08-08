package com.xxc.example.concurrent;

import com.xxc.example.concurrent.test.MyAQS;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;

@SpringBootTest
class ConcurrentApplicationTests {

    private static final MyAQS lock = new MyAQS();
    final static Condition notFull = lock.newCondition();
    final static Condition notEmpty = lock.newCondition();
    private static final Queue<String> queue = new LinkedBlockingDeque<>(10);
    private static final int queueSize = 10;


    @Test
    void contextLoads() {
    }


    @Test
    void testMyAQS() {
        ExecutorService executorService = Executors.newCachedThreadPool();

        Thread producer = new Thread(() -> {
            while (true) {
                //占用锁
                lock.lock();
                try {
                    while (queue.size() == queueSize) {
                        notEmpty.await();
                    }
                    queue.add("item");
                    System.out.println("producer:" + queue.size());
                    notFull.signalAll();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    //释放锁
                    lock.unlock();
                }
            }
        });

        Thread consumer = new Thread(() -> {
            while (true) {
                lock.lock();
                try {
                    while (queue.size() == 0) {
                        notFull.await();
                    }
                    queue.poll();
                    System.out.println("consumer:" + queue.size());
                    notEmpty.signalAll();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    lock.unlock();
                }
            }
        });
        executorService.execute(producer);
        executorService.execute(consumer);
    }

}
