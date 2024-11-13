package com.tianji.learning.task;

import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.JsonUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class LearningRecordDelayTaskHandler {

    private final static String RECORD_KEY_TEMPLATE = "learning:record:{}";
    // * 实际核心数，非逻辑处理器数
    private final static int CPU_ACTUAL_CORES = 8;
    private static volatile boolean begin = true;
    private final StringRedisTemplate redisTemplate;
    private final DelayQueue<DelayTask<RecordTaskData>> queue = new DelayQueue<>();
    private final LearningRecordMapper recordMapper;
    private final ILearningLessonService lessonService;
    // * 涉及循环依赖 修改了spring.main.allow-circular-references，使用autowired自动确定注入时机
    // * 偷懒复用批量更新
    @Autowired
    private final ILearningRecordService recordService;
    // * 仅为实现定时任务（不采用）添加，key为记录id，value为记录
    private final Map<Long, LearningRecord> lastRecords;

    @PostConstruct
    public void init() {
        ExecutorService threadPool = Executors.newFixedThreadPool(CPU_ACTUAL_CORES);
        CompletableFuture.runAsync(this::handleDelayTask, threadPool);
    }

    @PreDestroy
    public void destroy() {
        log.debug("关闭学习记录处理的延迟任务");
        begin = false;
    }

    /**
     * （仅实现，不采用）定时任务，每隔20秒检查Redis缓存是否有需要持久化的学习记录
     */
//    @Scheduled(fixedRate = 20_000)
    private void checkAndPersistRecords() {
        log.debug("定时持久化学习记录开始");
        // * 没有需要更新的数据（Redis数据为空）
        if (CollUtils.isEmpty(lastRecords)) {
            return;
        }
        List<LearningRecord> recordList = readRecordCacheBatch();
        // * 健壮性检查，Redis中无数据，原则上lastRecords不为空Redis数据也不为空
        if (CollUtils.isEmpty(recordList)) {
            return;
        }
        // * （可选）仅更新播放进度相比上次写入时没有变化的数据，但可能出现刚写入Redis就触发定时更新的情况，效果未必好
        List<LearningRecord> recordsToUpdate = recordList.stream()
                                                         .filter(record -> Objects.equals(record.getMoment(), lastRecords.get(record.getId())
                                                                                                                         .getMoment()))
                                                         .collect(Collectors.toList());
        // * finished不修改，此处必定为非第一次完成
        recordsToUpdate.forEach(record -> record.setFinished(null));
        // * 更新学习记录
        boolean success = recordService.updateBatchById(recordsToUpdate);
        if (!success) {
            throw new DbException("定时任务更新学习记录失败");
        }
        // * 更新课表 最近学习小节id与时间
        // * Redis中数据只有三部分，需要使用更完全的数据
        List<LearningLesson> lessons = recordsToUpdate.stream()
                                                      .map(record -> {
                                                          LearningRecord fullRecord = lastRecords.get(record.getId());
                                                          LearningLesson lesson = new LearningLesson();
                                                          lesson.setId(fullRecord.getLessonId());
                                                          lesson.setLatestLearnTime(LocalDateTime.now());
                                                          lesson.setLatestSectionId(fullRecord.getSectionId());
                                                          return lesson;
                                                      })
                                                      .collect(Collectors.toList());
        success = lessonService.updateBatchById(lessons);
        if (!success) {
            throw new DbException("定时任务更新课表失败");
        }
        // * 更新结束，清空暂存区
        lastRecords.clear();
        log.info("定时持久化学习记录任务成功");
    }

    private void handleDelayTask() {
        while (begin) {
            try {
                // 1.尝试获取任务
                DelayTask<RecordTaskData> task = queue.take();
                log.debug("获取到要处理的播放记录任务");
                RecordTaskData data = task.getData();
                // 2.读取Redis缓存
                LearningRecord record = readRecordCache(data.getLessonId(), data.getSectionId());
                if (record == null) {
                    continue;
                }
                // 3.比较数据
                if (!Objects.equals(data.getMoment(), record.getMoment())) {
                    // 4.如果不一致，播放进度在变化，无需持久化
                    continue;
                }
                // 5.如果一致，证明用户离开了视频，需要持久化
                // 5.1.更新学习记录
                record.setFinished(null);
                recordMapper.updateById(record);
                // 5.2.更新课表
                LearningLesson lesson = new LearningLesson();
                lesson.setId(data.getLessonId());
                lesson.setLatestSectionId(data.getSectionId());
                lesson.setLatestLearnTime(LocalDateTime.now());
                lessonService.updateById(lesson);

                log.debug("准备持久化学习记录信息");
            } catch (Exception e) {
                log.error("处理播放记录任务发生异常", e);
            }
        }
    }

    // * 替换addLearningRecordTask采用定时任务方案
    public void addLearningRecordTaskScheduled(LearningRecord record) {
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.添加后续需要更新数据库的数据
        lastRecords.putIfAbsent(record.getId(), record);
    }

    // * 定时方案使用
    private List<LearningRecord> readRecordCacheBatch() {
        try {
            // * 1.批量读取Redis数据，因为没有根据lessonId聚类，这里只一次传输多条单field读取
            // * 逆天API三个泛型只给传一个
            List<Object> results = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    for (Long recordKey : lastRecords.keySet()) {
                        LearningRecord record = lastRecords.get(recordKey);
                        String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
                        // * 完全虚假的类型安全
                        // * 18年的issue 2024还没close😅
                        // * https://github.com/spring-projects/spring-data-redis/issues/1431
                        //noinspection unchecked
                        operations.opsForHash()
                                  .get(key, record.getSectionId()
                                                  .toString());
                    }
                    return null;
                }
            });
            // * Redis中无数据，不进行更新
            if (CollUtils.isEmpty(results)) {
                return null;
            }
            // * 反序列化Redis数据用于后续Service数据库数据更新
            List<LearningRecord> recordList = results.stream()
                                                     .map(record -> JsonUtils.toBean(record.toString(), LearningRecord.class))
                                                     .collect(Collectors.toList());
            return recordList;
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }


    public void addLearningRecordTask(LearningRecord record) {
        // 1.添加数据到Redis缓存
        writeRecordCache(record);
        // 2.提交延迟任务到延迟队列 DelayQueue
        queue.add(new DelayTask<>(new RecordTaskData(record), Duration.ofSeconds(20)));
    }

    public void writeRecordCache(LearningRecord record) {
        log.debug("更新学习记录的缓存数据");
        try {
            // 1.数据转换
            String json = JsonUtils.toJsonStr(new RecordCacheData(record));
            // 2.写入Redis
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, record.getLessonId());
            redisTemplate.opsForHash()
                         .put(key, record.getSectionId()
                                         .toString(), json);
            // 3.添加缓存过期时间
            redisTemplate.expire(key, Duration.ofMinutes(1));
        } catch (Exception e) {
            log.error("更新学习记录缓存异常", e);
        }
    }

    public LearningRecord readRecordCache(Long lessonId, Long sectionId) {
        try {
            // 1.读取Redis数据
            String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
            Object cacheData = redisTemplate.opsForHash()
                                            .get(key, sectionId.toString());
            if (cacheData == null) {
                return null;
            }
            // 2.数据检查和转换
            return JsonUtils.toBean(cacheData.toString(), LearningRecord.class);
        } catch (Exception e) {
            log.error("缓存读取异常", e);
            return null;
        }
    }


    public void cleanRecordCache(Long lessonId, Long sectionId) {
        // 删除数据
        String key = StringUtils.format(RECORD_KEY_TEMPLATE, lessonId);
        redisTemplate.opsForHash()
                     .delete(key, sectionId.toString());
    }

    @Data
    @NoArgsConstructor
    private static class RecordCacheData {
        private Long id;
        private Integer moment;
        private Boolean finished;

        public RecordCacheData(LearningRecord record) {
            this.id = record.getId();
            this.moment = record.getMoment();
            this.finished = record.getFinished();
        }
    }

    @Data
    @NoArgsConstructor
    private static class RecordTaskData {
        private Long lessonId;
        private Long sectionId;
        private Integer moment;

        public RecordTaskData(LearningRecord record) {
            this.lessonId = record.getLessonId();
            this.sectionId = record.getSectionId();
            this.moment = record.getMoment();
        }
    }
}
