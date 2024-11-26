package com.tianji.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;

/**
 * <p>
 * 优惠券的规则信息 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-25
 */
public interface ICouponService extends IService<Coupon> {

	void addCoupon(CouponFormDTO dto);

	PageDTO<CouponPageVO> queryPage(CouponQuery query);

	void issueCoupon(CouponIssueFormDTO dto);

	void updateCoupon(CouponFormDTO dto);

	void deleteCoupon(Long id);

	CouponDetailVO queryCouponDetailById(Long id);

	void checkAndIssueCoupons(int page, int size);

	void checkAndFinishCoupons(int page, int size);

	void pauseCouponIssue(Long id);
}
