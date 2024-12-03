package com.tianji.api.dto.promotion;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@ApiModel(description = "订单的可用优惠券及折扣信息")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponDiscountDTO {
	@ApiModelProperty("用户优惠券id集合")
	private List<Long> ids = new ArrayList<>();
	@ApiModelProperty("优惠券规则")
	private List<String> rules = new ArrayList<>();
	@ApiModelProperty("本订单最大优惠金额")
	private Integer discountAmount = 0;
	@ApiModelProperty("优惠详细map，课程id：课程优惠金额")
	private Map<Long, Integer> discountDetailMap;
}