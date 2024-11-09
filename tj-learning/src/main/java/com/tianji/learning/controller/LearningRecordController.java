package com.tianji.learning.controller;


import com.tianji.api.dto.leanring.LearningLessonDTO;
import com.tianji.learning.domain.dto.LearningRecordFormDTO;
import com.tianji.learning.service.ILearningRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 学习记录表 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-03
 */
@RestController
@RequestMapping("/learning-records")
@Api(tags = "学习记录相关接口")
@RequiredArgsConstructor
public class LearningRecordController {

    private final ILearningRecordService recordService;

    @GetMapping("/course/{courseId}")
    @ApiOperation("根据课程id查询学习记录")
    public LearningLessonDTO queryByCourseId(@ApiParam(value = "课程id", example = "1") @PathVariable("courseId") Long courseId) {
        return recordService.queryByCourseId(courseId);
    }

    @PostMapping
    @ApiOperation("提交课程学习记录")
    public void addLearningRecord(@RequestBody LearningRecordFormDTO dto) {
        recordService.addLearningRecord(dto);
    }
}
