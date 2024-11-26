package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.query.CodeQuery;
import com.tianji.promotion.domain.vo.CodeVO;

/**
 * <p>
 * 兑换码 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
public interface IExchangeCodeService extends IService<ExchangeCode> {

	void generateCode(Coupon coupon);

	PageDTO<CodeVO> queryCodePage(CodeQuery query);
}
