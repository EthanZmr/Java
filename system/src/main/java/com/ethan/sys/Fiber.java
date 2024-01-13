package com.ethan.sys;

/**
 * @author ethan
 * @since 2024/1/13
 */
public class Fiber {

    public static void main0(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread[] threads = new Thread[100000];
        // 虚拟线程(协程)
        for (int i = 0; i < 100000; i++) {
            threads[i] = Thread.ofVirtual().start(Fiber::calc);
        }

        for (int i = 0; i < 100000; i++) {
            threads[i].join();
        }

        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();
        Thread[] threads = new Thread[10000];
        for (int i = 0; i < 10000; i++) {
            threads[i] = new Thread(() -> {
                calc();
            });
        }

        for (int i = 0; i < 10000; i++) {
            threads[i].start();
        }

        for (int i = 0; i < 10000; i++) {
            threads[i].join();
        }
        long end = System.currentTimeMillis();
        System.out.println(end - start);
    }

    public static void calc() {
        int i = 0;
        while (i < 10000) {
            i++;
        }
    }
}
