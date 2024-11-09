package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.client.course.CourseClient;
import com.tianji.api.dto.course.CourseFullInfoDTO;
import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.api.dto.leanring.LearningRecordDTO;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.domain.po.LearningRecord;
import com.tianji.learning.enums.LessonStatus;
import com.tianji.learning.enums.SectionType;
import com.tianji.learning.mapper.LearningRecordMapper;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.service.ILearningRecordService;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * <p>
 * 学习记录表 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-03
 */
@RequiredArgsConstructor
public class LearningRecordServiceImplUnOptimized extends ServiceImpl<LearningRecordMapper, LearningRecord> implements ILearningRecordService {

    private final ILearningLessonService lessonService;
    private final CourseClient courseClient;

    /**
     * 根据课程id查询对应（用户）学习记录
     */
    @Override
    public LearningLessonDTO queryByCourseId(Long courseId) {
        if (courseId == null) {
            return null;
        }
        // * 获取用户id，根据（userId，courseId）唯一查询课表
        Long userId = UserContext.getUser();
        LearningLesson lesson = lessonService.lambdaQuery()
                                             .eq(LearningLesson::getUserId, userId)
                                             .eq(LearningLesson::getCourseId, courseId)
                                             .one();
        if (lesson == null) {
            return null;
        }

        // * 根据课表查询学习记录
        Long lessonId = lesson.getId();
        List<LearningRecord> recordList = lambdaQuery()
                .eq(LearningRecord::getLessonId, lessonId)
                .list();
        if (CollUtils.isEmpty(recordList)) {
            return null;
        }
        List<LearningRecordDTO> recordDTOList = BeanUtils.copyList(recordList, LearningRecordDTO.class);
        // * 封装dto
        LearningLessonDTO dto = new LearningLessonDTO();
        dto.setId(lesson.getId());
        dto.setRecords(recordDTOList);
        dto.setLatestSectionId(lesson.getLatestSectionId());
        return dto;
    }

    /**
     * 提交学习记录
     */
    @Override
    public void addLearningRecord(LearningRecordFormDTO dto) {
        // * 健壮性检查，(lessonId，sectionId)唯一确定一条学习记录，后续逻辑依赖sectionType
        if (dto == null || dto.getSectionType() == null || dto.getLessonId() == null || dto.getSectionId() == null || dto.getMoment() == null) {
            return;
        }
        // * 构建学习记录
        LearningRecord record = BeanUtils.copyBean(dto, LearningRecord.class);
        Long userId = UserContext.getUser();
        record.setUserId(userId);
        // * 无效类型
        SectionType type = dto.getSectionType();
        if (type != SectionType.VIDEO && type != SectionType.EXAM) {
            return;
        }
        // * 是否第一次完成（考试与第一次视频完成都算）
        Boolean isFirstLessonFinished = false;
        if (type == SectionType.VIDEO) {
            LearningRecord learningRecord = lambdaQuery()
                    .eq(LearningRecord::getSectionId, record.getSectionId())
                    .eq(LearningRecord::getLessonId, record.getLessonId())
                    .one();
            // * 以前没有此记录，创建保存
            if (learningRecord == null) {
                boolean success = save(record);
                if (!success) {
                    throw new DbException("保存学习记录失败");
                }
            } else {
                // * 检查是否完成过，只有未完成过且已播放时间达到时长一半计入第一次完成
                Boolean isFinished = learningRecord.getFinished();
                if (!isFinished && record.getMoment() >= dto.getDuration() / 2) {
                    isFirstLessonFinished = true;
                    record = learningRecord;
                    record.setMoment(dto.getMoment());
                }
                lambdaUpdate()
                        .eq(LearningRecord::getId, learningRecord.getId())
                        .update(record);
            }
        }
        // * 考试提交直接视作第一次完成
        if (type == SectionType.EXAM) {
            isFirstLessonFinished = true;
        }

        // * 查询出课表用于更新
        LearningLesson lesson = lessonService.lambdaQuery()
                                             .eq(LearningLesson::getId, record.getLessonId())
                                             .one();
        if (lesson == null) {
            throw new DbException("课表数据不存在");
        }
        // * 如果第一次完成
        if (isFirstLessonFinished) {
            // * 记为已完成此小节学习并保存/更新
            record.setFinished(true);
            record.setFinishTime(dto.getCommitTime());
            boolean success = saveOrUpdate(record);
            if (!success) {
                throw new DbException("学习记录保存/更新失败");
            }
            // * 更新已学习小节数
            lesson.setLearnedSections(lesson.getLearnedSections() + 1);
            // * 获取课程总小节数
            CourseFullInfoDTO course = courseClient.getCourseInfoById(lesson.getCourseId(), false, false);
            if (course == null) {
                return;
            }
            Integer sectionNum = course.getSectionNum();
            // * 判断是否已学完更新相关状态
            if (lesson.getLearnedSections()
                      .equals(sectionNum)) {
                lesson.setStatus(LessonStatus.FINISHED);
            }
        }
        // ? lambdaUpdate
        // * 更新课表最近学习小节与时间
        lesson.setLatestSectionId(record.getSectionId());
        lesson.setLatestLearnTime(LocalDateTime.now());
        // * 第一次学习，更新学习状态
        if (lesson.getStatus() == LessonStatus.NOT_BEGIN) {
            lesson.setStatus(LessonStatus.LEARNING);
        }
        lessonService.getBaseMapper()
                     .update(lesson, new QueryWrapper<LearningLesson>().lambda()
                                                                       .eq(LearningLesson::getId, record.getLessonId()));
    }
}
