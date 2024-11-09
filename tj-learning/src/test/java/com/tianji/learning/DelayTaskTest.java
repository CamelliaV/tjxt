package com.tianji.learning;

import com.tianji.learning.task.DelayTask;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.concurrent.DelayQueue;

/**
 * @author CamelliaV
 * @since 2024/11/8 / 16:33
 */
@SpringBootTest(classes = LearningApplication.class)
@Slf4j
public class DelayTaskTest {
    @Test
    public void testDelayTask() {
        DelayQueue<DelayTask<String>> delayQueue = new DelayQueue<>();

        delayQueue.add(new DelayTask<String>(">Task<1>", Duration.ofSeconds(1)));
        delayQueue.add(new DelayTask<String>(">Task<2>", Duration.ofSeconds(3)));
        delayQueue.add(new DelayTask<String>(">Task<3>", Duration.ofSeconds(5)));

        while (true) {
            // * poll不阻塞
            try {
                DelayTask<String> delayTask = delayQueue.take();
                log.info(">>>>>>>>> {} executed <<<<<<<", delayTask.getData());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
