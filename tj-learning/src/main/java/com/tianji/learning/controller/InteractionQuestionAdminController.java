package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表（管理端） 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-09
 */
@RestController
@RequestMapping("/admin/questions")
@Api(tags = "问题相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {
    private final IInteractionQuestionService questionService;

    @GetMapping("/page")
    @ApiOperation("管理端分页查询问题")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(@Validated QuestionAdminPageQuery query) {
        return questionService.queryQuestionPageAdmin(query);
    }

    @PutMapping("/{id}/hidden/{hidden}")
    @ApiOperation("管理端更新问题隐藏状态")
    public void updateQuestionHiddenById(@PathVariable("id") Long id, @PathVariable("hidden") Boolean hidden) {
        questionService.updateQuestionHiddenById(id, hidden);
    }

    @GetMapping("/{id}")
    @ApiOperation("管理端根据id查问题详情")
    public QuestionAdminVO queryQuestionByIdAdmin(@PathVariable("id") Long id) {
        return questionService.queryQuestionByIdAdmin(id);
    }

}

