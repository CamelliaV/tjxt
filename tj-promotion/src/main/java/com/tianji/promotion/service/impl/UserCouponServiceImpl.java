package com.tianji.promotion.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.ExchangeCode;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.UserCouponQuery;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.ExchangeCodeStatus;
import com.tianji.promotion.mapper.UserCouponMapper;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import com.tianji.promotion.utils.CodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户领取优惠券的记录，是真正使用的优惠券信息 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-26
 */
@Service
@RequiredArgsConstructor
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCoupon> implements IUserCouponService {
	private final IExchangeCodeService exchangeCodeService;
	@Autowired
	@Lazy
	private ICouponService couponService;

	/**
	 * 领取优惠劵
	 */
	@Override
	public void receiveCoupon(Long id) {
		if (id == null) {
			return;
		}
		// * 优惠劵是否存在
		Coupon coupon = couponService.getById(id);
		if (coupon == null) {
			throw new DbException("目标优惠劵不存在：" + id);
		}
		// * 判断发放时间
		LocalDateTime now = LocalDateTime.now();
		if (now.isBefore(coupon.getIssueBeginTime()) || now.isAfter(coupon.getIssueEndTime())) {
			throw new BizIllegalException("优惠劵不在发放时间内");
		}
		// * 判断库存
		if (coupon.getIssueNum() >= coupon.getTotalNum()) {
			throw new BizIllegalException("优惠劵库存不足");
		}
		// * 校验单人领取数量并更新卷已领取数并添加用户卷记录
		// * 悲观锁解决单人刷单
		// * Aspectj获取代理对象，重新激活声明式事务
		String userIdString = UserContext.getUser().toString().intern();
		synchronized (userIdString) {
			IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
			userCouponService.checkAndCreateUserCoupon(coupon);
		}
	}

	/**
	 * 校验单人领取数量并更新卷已领取数并添加用户卷记录（工具方法）
	 */
	@Transactional
	@Override
	public void checkAndCreateUserCoupon(Coupon coupon) {
		// * 判断是否超出单人可领数量
		Integer receivedNum = lambdaQuery()
				.eq(UserCoupon::getUserId, UserContext.getUser())
				.eq(UserCoupon::getCouponId, coupon.getId())
				.count();
		if (receivedNum >= coupon.getUserLimit()) {
			throw new BizIllegalException("用户领取已达上限");
		}
		// * 允许领取，优惠劵领取数+1
		boolean success = couponService.lambdaUpdate()
				.eq(Coupon::getId, coupon.getId())
				// * 乐观锁解决超卖
				.lt(Coupon::getIssueNum, coupon.getTotalNum())
				.setSql("issue_num = issue_num + 1")
				.update();
		if (!success) {
			throw new DbException("优惠卷领取更新失败");
		}
		// * 用户卷里加一条记录
		UserCoupon userCoupon = new UserCoupon();
		userCoupon.setUserId(UserContext.getUser());
		userCoupon.setCouponId(coupon.getId());
		// * 设置有效日期
		LocalDateTime termBeginTime = coupon.getTermBeginTime();
		LocalDateTime termEndTime = coupon.getTermEndTime();
		// * 使用天数而不是日期范围
		if (termBeginTime == null) {
			termBeginTime = LocalDateTime.now();
			termEndTime = termBeginTime.plusDays(coupon.getTermDays());
		}
		userCoupon.setTermBeginTime(termBeginTime);
		userCoupon.setTermEndTime(termEndTime);
		save(userCoupon);
	}

	/**
	 * 分页查询我的优惠劵
	 */
	@Override
	public PageDTO<CouponVO> queryMyCoupon(UserCouponQuery query) {
		// * 获取筛选状态
		Integer status = query.getStatus();
		// * 分页查询用户优惠劵
		Page<UserCoupon> page = lambdaQuery()
				.eq(UserCoupon::getUserId, UserContext.getUser())
				.eq(status != null, UserCoupon::getStatus, status)
				.page(query.toMpPageDefaultSortByCreateTimeDesc());
		List<UserCoupon> records = page.getRecords();
		// * 无用户卷数据
		if (CollUtils.isEmpty(records)) {
			return PageDTO.empty(0L, 0L);
		}
		// * 构造优惠劵id集合查询优惠劵信息
		List<Long> couponIds = records.stream()
				.map(UserCoupon::getCouponId)
				.collect(Collectors.toList());
		List<Coupon> couponList = couponService.lambdaQuery()
				.in(Coupon::getId, couponIds)
				.list();
		// * 无对应优惠劵
		if (CollUtil.isEmpty(couponList)) {
			throw new DbException("部分id优惠劵不存在");
		}
		// * 构造map
		Map<Long, LocalDateTime> termEndTimeMap = records.stream()
				.collect(Collectors.toMap(UserCoupon::getCouponId, UserCoupon::getTermEndTime));
		// * 补全vo的使用结束时间（与用户卷表有关）
		List<CouponVO> voList = new ArrayList<>();
		for (Coupon coupon : couponList) {
			CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
			vo.setTermEndTime(termEndTimeMap.getOrDefault(coupon.getId(), null));
			voList.add(vo);
		}
		return PageDTO.of(page, voList);
	}

	/**
	 * 兑换优惠劵
	 */
	@Override
	@Transactional
	public void exchangeCoupon(String code) {
		// * 解析兑换码
		long id = CodeUtil.parseCode(code);
		// * 查询兑换码
		ExchangeCode exchangeCode = exchangeCodeService.getById(id);
		// * 是否存在
		if (exchangeCode == null) {
			throw new DbException("目标兑换码不存在：" + id);
		}
		// * 判断是否兑换状态
		// * 判断是否过期
		LocalDateTime now = LocalDateTime.now();
		if (exchangeCode.getStatus() != ExchangeCodeStatus.UNUSED || now.isAfter(exchangeCode.getExpiredTime())) {
			throw new BizIllegalException("兑换码已使用或已过期");
		}
		// * 判断是否超出领取数量
		// * 更新状态（优惠卷领取+1；用户卷新增记录）
		Coupon coupon = couponService.getById(exchangeCode.getExchangeTargetId());
		Long userId = UserContext.getUser();
		// * 嵌套的事务与外部的事务会放在一起（即包括内部与外部更新兑换码）
		// * 事务提交应在锁之前
		synchronized (userId.toString().intern()) {
			IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
			userCouponService.checkAndCreateUserCouponWithCode(coupon, id);
		}
	}

	/**
	 * 校验单人领取数量并更新卷已领取数并添加用户卷记录并更新兑换码状态（工具方法）
	 */
	@Transactional
	@Override
	public void checkAndCreateUserCouponWithCode(Coupon coupon, Long id) {
		// * 代理对象确保声明式事务有效
		IUserCouponService userCouponService = (IUserCouponService) AopContext.currentProxy();
		userCouponService.checkAndCreateUserCoupon(coupon);
		// * 更新兑换码状态
		exchangeCodeService.lambdaUpdate()
				.eq(ExchangeCode::getId, id)
				.set(ExchangeCode::getUserId, UserContext.getUser())
				.set(ExchangeCode::getStatus, ExchangeCodeStatus.USED)
				.update();
		// throw new RuntimeException();
	}
}
