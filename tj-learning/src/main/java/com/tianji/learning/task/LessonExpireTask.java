package com.tianji.learning.task;

import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author CamelliaV
 * @since 2024/11/8 / 21:30
 */
@Component
@RequiredArgsConstructor
public class LessonExpireTask {
    private final ILearningLessonService lessonService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void checkAndExpireLessons() {
        lessonService.checkAndExpireLessons();
    }
}
