package com.hmdp;

import com.hmdp.utils.RedisIdWorker;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    private RedisIdWorker redisIdWorker;

    private ExecutorService executorService = Executors.newFixedThreadPool(500);
    void testIdWorker() throws InterruptedException {
        // 任务数
        int taskCount = 300;
        // 每个任务生成的ID数量
        int idPerTask = 100;

        CountDownLatch countDownLatch = new CountDownLatch(taskCount);

        Runnable task = () -> {
            try {
                for (int i = 0; i < idPerTask; i++) {
                    long id = redisIdWorker.nextId("order");
                    // 1. 去掉打印，纯测性能
                    // System.out.println("id=" + id);
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                // 2. 保证无论是否报错，计数器都会减1，防止死锁
                countDownLatch.countDown();
            }
        };

        long begin = System.currentTimeMillis();

        for (int i = 0; i < taskCount; i++) {
            executorService.submit(task);
        }

        // 等待所有线程完成
        countDownLatch.await();

        long end = System.currentTimeMillis();

        System.out.println("耗时: " + (end - begin) + " ms");
        System.out.println("共生成 ID: " + (taskCount * idPerTask) + " 个");
    }

}
