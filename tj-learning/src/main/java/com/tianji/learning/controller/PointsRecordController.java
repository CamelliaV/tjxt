package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Api(tags = "积分记录相关接口")
public class PointsRecordController {
    private final IPointsRecordService pointsRecordService;

    @GetMapping
    @ApiOperation("查询今日积分情况")
    public List<PointsStatisticsVO> queryMyPointsToday() {
        return pointsRecordService.queryMyPointsToday();
    }
}
