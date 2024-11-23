package com.tianji.learning.controller;


import com.tianji.learning.domain.query.PointsBoardQuery;
import com.tianji.learning.domain.vo.PointsBoardSeasonVO;
import com.tianji.learning.domain.vo.PointsBoardVO;
import com.tianji.learning.service.IPointsBoardSeasonService;
import com.tianji.learning.service.IPointsBoardService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
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
    private final IPointsBoardService boardService;

    @GetMapping()
    @ApiOperation("根据赛季id查询用户数据与榜单数据")
    public PointsBoardVO queryPointsBoardBySeason(@Validated PointsBoardQuery query) {
        return boardService.queryPointsBoardBySeason(query);
    }

    @GetMapping("/seasons/list")
    @ApiOperation("赛季列表查询")
    public List<PointsBoardSeasonVO> queryPointsBoardSeasons() {
        return pointsBoardSeasonService.queryPointsBoardSeasons();
    }
}
