package com.tianji.learning.controller;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * @author CamelliaV
 * @since 2024/11/12 / 15:53
 */

@RestController
@RequestMapping("/admin/replies")
@RequiredArgsConstructor
@Api(tags = "评论相关接口（管理端）")
public class InteractionReplyAdminController {
    private final IInteractionReplyService replyService;

    @GetMapping("/page")
    @ApiOperation("分页查询回答或评论（管理端）")
    public PageDTO<ReplyVO> queryReplyPageAdmin(@Validated ReplyPageQuery query) {
        return replyService.queryReplyPage(query, true);
    }

    @PutMapping("{id}/hidden/{hidden}")
    @ApiOperation("修改评论显示状态（管理端）")
    public void updateReplyHiddenById(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        replyService.updateReplyHiddenById(id, hidden);
    }
}
