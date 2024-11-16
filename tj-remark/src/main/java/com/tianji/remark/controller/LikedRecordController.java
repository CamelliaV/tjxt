package com.tianji.remark.controller;


import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.service.ILikedRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-13
 */
@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@Api(tags = "点赞相关业务")
public class LikedRecordController {
    private final ILikedRecordService likeService;

    @PostMapping
    @ApiOperation("新增或取消点赞")
    public void addOrDeleteLikeRecord(@Validated @RequestBody LikeRecordFormDTO dto) {
//        likeService.addOrDeleteLikeRecord(dto);
        likeService.addOrDeleteLikeRecordPersistent(dto);
    }

    @GetMapping("/list")
    @ApiOperation("根据用户id与业务id及类型查询点赞状态")
    public Set<Long> queryLikedListByUserIdsAndBizIds(@RequestParam("bizType") String bizType, @RequestParam("bizIds") List<Long> bizIds) {
//        return likeService.queryLikedListByUserIdsAndBizIds(bizType, bizIds);
        return likeService.queryLikedListByUserIdsAndBizIdsPersistent(bizType, bizIds);
    }
}
