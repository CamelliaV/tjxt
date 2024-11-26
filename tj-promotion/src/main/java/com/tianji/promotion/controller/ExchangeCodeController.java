package com.tianji.promotion.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.CodeVO;
import com.tianji.promotion.service.IExchangeCodeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 兑换码 前端控制器
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
@RestController
@RequestMapping("/codes")
@Api(tags = "兑换码相关接口")
@RequiredArgsConstructor
public class ExchangeCodeController {
	private final IExchangeCodeService exchangeCodeService;

	@ApiOperation("分页查询兑换码")
	@GetMapping("/page")
	public PageDTO<CodeVO> queryCodePage(@Validated CodeQuery query) {
		return exchangeCodeService.queryCodePage(query);
	}
}
