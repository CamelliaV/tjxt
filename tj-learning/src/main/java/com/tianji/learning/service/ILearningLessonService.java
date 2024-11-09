package com.tianji.learning.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-10-30
 */
public interface ILearningLessonService extends IService<LearningLesson> {
    void addLesson(OrderBasicDTO dto);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    LearningLessonVO queryCurrent();

    LearningLessonVO queryByCourseId(Long courseId);

    Long isLessonValid(Long courseId);

    Integer countLearningLessonByCourse(Long courseId);

    void deleteLessonByCourse(Long userId, Long courseId);

    void createLessonPlan(LearningPlanDTO dto);

    LearningPlanPageVO queryMyPlan(PageQuery pageQuery);

    void checkAndExpireLessons();
}

