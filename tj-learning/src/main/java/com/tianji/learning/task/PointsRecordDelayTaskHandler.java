package com.tianji.learning.task;

import com.tianji.learning.constants.LearningConstants;
import com.tianji.learning.service.IPointsRecordService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RequiredArgsConstructor
@Component
public class PointsRecordDelayTaskHandler {

    // * 实际核心数，非逻辑处理器数
    // private final static int CPU_ACTUAL_CORES = 8;
    private final static int CPU_ACTUAL_CORES = 1;
    private static volatile boolean begin = true;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    @Autowired
    private IPointsRecordService pointsRecordService;

    @PostConstruct
    public void init() {
        ExecutorService threadPool = Executors.newFixedThreadPool(CPU_ACTUAL_CORES);
        CompletableFuture.runAsync(this::handleDelayTask, threadPool);
    }

    @PreDestroy
    public void destroy() {
        log.debug("关闭积分记录处理的延迟任务");
        begin = false;
    }

    private void handleDelayTask() {
        while (begin) {
            try {
                // 1.尝试获取任务
                DelayTask<RecordTaskData> task = queue.take();
                RecordTaskData data = task.getData();
                pointsRecordService.deletePointsRecordWithRange(data.getMinId(), data.getMaxId(), LearningConstants.SHARDING_POINTS_RECORD_DELETE_LIMIT);
            } catch (Exception e) {
                log.error("处理分片积分记录延迟任务发生异常", e);
            }
        }
    }


    public void addPointsRecordShardingTask(Long minId, Long maxId) {
        queue.add(new DelayTask<>(new RecordTaskData(minId, maxId), Duration.ofSeconds(LearningConstants.SHARDING_POINTS_RECORD_DELETE_DELAY)));
    }

    @Data
    @AllArgsConstructor
    private static class RecordTaskData {
        private Long minId;
        private Long maxId;
    }
}
