package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningRecord;

/**
 * <p>
 * 学习记录表 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-03
 */
public interface ILearningRecordService extends IService<LearningRecord> {

    LearningLessonDTO queryByCourseId(Long courseId);

    void addLearningRecord(LearningRecordFormDTO dto);
}
