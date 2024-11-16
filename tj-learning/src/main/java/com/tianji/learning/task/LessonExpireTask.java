package com.tianji.learning.task;

import com.tianji.learning.service.ILearningLessonService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * @author CamelliaV
 * @since 2024/11/8 / 21:30
 */
@Component
@RequiredArgsConstructor
public class LessonExpireTask {
    private final ILearningLessonService lessonService;

    //    @Scheduled(cron = "0 0 2 * * ?")
    @XxlJob("checkAndExpireLessons")
    public void checkAndExpireLessons() {
        lessonService.checkAndExpireLessons();
    }
}
