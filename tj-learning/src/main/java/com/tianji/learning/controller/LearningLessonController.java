package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-10-30
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "课表相关接口")
@RequiredArgsConstructor
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @GetMapping("/page")
    @ApiOperation("分页查询我的课表")
    public PageDTO<LearningLessonVO> queryMyLessons(@Validated PageQuery query) {
        return lessonService.queryMyLessons(query);
    }

    @GetMapping("/now")
    @ApiOperation("查询当前用户最近学习的课程")
    public LearningLessonVO queryCurrent() {
        return lessonService.queryCurrent();
    }

    @GetMapping("/{courseId}")
    @ApiOperation("根据课程id查询课程信息")
    public LearningLessonVO queryByCourseId(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return lessonService.queryByCourseId(courseId);
    }

    @GetMapping("/{courseId}/valid")
    @ApiOperation("根据课程id检查当前用户课表是否有该课程")
    public Long isLessonValid(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return lessonService.isLessonValid(courseId);
    }

    @GetMapping("/{courseId}/count")
    @ApiOperation("统计当前课程学习人数")
    public Integer countLearningLessonByCourse(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return lessonService.countLearningLessonByCourse(courseId);
    }

    @DeleteMapping("/{courseId}")
    @ApiOperation("删除当前用户课表中的某课程")
    public void deleteLesson(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        lessonService.deleteLessonByCourse(null, courseId);
    }

    @PostMapping("/plans")
    @ApiOperation("创建学习计划")
    public void createLessonPlan(@RequestBody LearningPlanDTO dto) {
        lessonService.createLessonPlan(dto);
    }

    @GetMapping("/plans")
    @ApiOperation("查询学习计划")
    public LearningPlanPageVO queryMyPlan(PageQuery pageQuery) {
        return lessonService.queryMyPlan(pageQuery);
    }
}
