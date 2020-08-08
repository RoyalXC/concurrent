package com.xxc.example.concurrent.test;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Condition;

/**
 * 功能描述:
 *
 * @author 薛行晨(RoyalXC)
 * @date 2020/8/8 14:20
 */
public class TestMyAQS {
    private static final MyAQS lock = new MyAQS();
    final static Condition notFull = lock.newCondition();
    final static Condition notEmpty = lock.newCondition();
    private static final Queue<String> queue = new LinkedBlockingDeque<>(10);
    private static final int queueSize = 10;

    public static void main(String[] args) {
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
        producer.start();
        consumer.start();
    }
}
