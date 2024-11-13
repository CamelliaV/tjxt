package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-09
 */
@RestController
@RequestMapping("/questions")
@Api(tags = "问题相关接口")
@RequiredArgsConstructor
public class InteractionQuestionController {
    private final IInteractionQuestionService questionService;

    @PostMapping
    @ApiOperation("新增问题")
    public void saveQuestion(@RequestBody @Validated QuestionFormDTO dto) {
        questionService.saveQuestion(dto);
    }

    @GetMapping("/page")
    @ApiOperation("分页查询问题（用户端）")
    public PageDTO<QuestionVO> queryQuestionPage(@Validated QuestionPageQuery query) {
        return questionService.queryQuestionPage(query);
    }

    @GetMapping("/{id}")
    @ApiOperation("根据id查询问题详情")
    public QuestionVO queryQuestionById(@PathVariable("id") Long id) {
        return questionService.queryQuestionById(id);
    }

    @PutMapping("/{id}")
    @ApiOperation("根据id更新问题")
    public void updateQuestionById(@PathVariable("id") Long id, QuestionFormDTO dto) {
        questionService.updateQuestionById(id, dto);
    }

    @DeleteMapping("/{id}")
    @ApiOperation("根据id删除问题")
    public void deleteQuestionById(@PathVariable("id") Long id) {
        questionService.deleteQuestionById(id);
    }
}
