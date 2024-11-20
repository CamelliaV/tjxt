package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-18
 */
@RestController
@RequestMapping("/boards")
@Api(tags = "排行榜赛季相关接口")
@RequiredArgsConstructor
public class PointsBoardSeasonController {
    private final IPointsBoardSeasonService pointsBoardSeasonService;

    @GetMapping("/seasons/list")
    @ApiOperation("赛季列表查询")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons() {
        return pointsBoardSeasonService.queryPointsBoardSeasons();
    }
}
