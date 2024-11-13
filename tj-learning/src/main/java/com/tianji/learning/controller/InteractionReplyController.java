package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.ReplyDTO;
import com.tianji.learning.domain.query.ReplyPageQuery;
import com.tianji.learning.domain.vo.ReplyVO;
import com.tianji.learning.service.IInteractionReplyService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动问题的回答或评论 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-09
 */
@RestController
@RequestMapping("/replies")
@RequiredArgsConstructor
@Api(tags = "评论相关接口")
public class InteractionReplyController {

    private final IInteractionReplyService replyService;

    @PostMapping
    @ApiOperation("新增评论")
    public void saveReply(@RequestBody @Validated ReplyDTO dto) {
        replyService.saveReply(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询回答或评论")
    public PageDTO<ReplyVO> queryReplyPage(@Validated ReplyPageQuery query) {
        return replyService.queryReplyPage(query, false);
    }

}
