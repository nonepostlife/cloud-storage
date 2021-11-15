package ru.postlife.java.lesson1_io;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DaemonThreadsExample {

    public static void main(String[] args) {
        int x = 5;
        String s = "Hello";
        List<Integer> list = Arrays.asList(1, 2, 3);
        log.debug("x = {}, s = {}, list = {}", x, s, list);
        log.debug("Current thread: {}", Thread.currentThread());
        for (int i = 0; i < 5; i++) {
            int finalI = i;
            Thread thread = new Thread(() -> {
                while (true) {
                    System.out.println(finalI);
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException e) {
                        log.error("", e);
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
        log.debug("Main thread finish work");
    }
}
