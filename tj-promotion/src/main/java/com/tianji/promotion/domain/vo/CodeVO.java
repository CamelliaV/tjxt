package com.tianji.promotion.domain.vo;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * @author CamelliaV
 * @since 2024/11/26 / 22:45
 */
@Data
@ApiModel(description = "兑换码分页数据")
public class CodeVO {
	@ApiModelProperty("兑换码id")
	private Long id;
	@ApiModelProperty("兑换码")
	private String code;
}
