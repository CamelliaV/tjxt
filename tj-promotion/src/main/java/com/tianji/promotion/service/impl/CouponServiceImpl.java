package com.tianji.promotion.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.api.cache.CategoryCache;
import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.promotion.constants.PromotionConstants;
import com.tianji.promotion.domain.dto.CouponFormDTO;
import com.tianji.promotion.domain.dto.CouponIssueFormDTO;
import com.tianji.promotion.domain.po.Coupon;
import com.tianji.promotion.domain.po.CouponScope;
import com.tianji.promotion.domain.po.UserCoupon;
import com.tianji.promotion.domain.query.CouponQuery;
import com.tianji.promotion.domain.vo.CouponDetailVO;
import com.tianji.promotion.domain.vo.CouponPageVO;
import com.tianji.promotion.domain.vo.CouponScopeVO;
import com.tianji.promotion.domain.vo.CouponVO;
import com.tianji.promotion.enums.CouponStatus;
import com.tianji.promotion.enums.ObtainType;
import com.tianji.promotion.enums.UserCouponStatus;
import com.tianji.promotion.job.CouponHandler;
import com.tianji.promotion.mapper.CouponMapper;
import com.tianji.promotion.service.ICouponScopeService;
import com.tianji.promotion.service.ICouponService;
import com.tianji.promotion.service.IExchangeCodeService;
import com.tianji.promotion.service.IUserCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 优惠券的规则信息 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-25
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
	private final ICouponScopeService couponScopeService;
	private final IExchangeCodeService exchangeCodeService;
	private final CategoryCache categoryCache;
	private final StringRedisTemplate redisTemplate;
	@Autowired
	private IUserCouponService userCouponService;

	/**
	 * 新增优惠劵
	 */
	@Override
	@Transactional
	public void addCoupon(CouponFormDTO dto) {
		Coupon coupon = BeanUtils.copyBean(dto, Coupon.class);
		// * 保存优惠劵
		// * 注意修改specific名字，与mysql关键字冲突
		save(coupon);
		// * 不限定范围
		if (!dto.getSpecific()) {
			return;
		}
		// * 批量保存适用范围，多个分类范围限定项
		Set<Long> scopes = new HashSet<>(dto.getScopes());
		if (CollUtils.isEmpty(scopes)) {
			throw new BizIllegalException("优惠劵范围指定为空");
		}
		List<CouponScope> couponScopes = scopes.stream()
				.map(scopeId -> new CouponScope().setCouponId(coupon.getId()).setBizId(scopeId))
				.collect(Collectors.toList());
		couponScopeService.saveBatch(couponScopes);
	}

	/**
	 * 分页查询优惠劵
	 */
	@Override
	public PageDTO<CouponPageVO> queryPage(CouponQuery query) {
		// * type对应discountType
		Integer type = query.getType();
		Integer status = query.getStatus();
		String name = query.getName();
		// * 分页查询数据库
		Page<Coupon> page = lambdaQuery()
				.eq(type != null, Coupon::getDiscountType, type)
				.eq(status != null, Coupon::getStatus, status)
				.like(StringUtils.isNotEmpty(name), Coupon::getName, name)
				.page(query.toMpPageDefaultSortByCreateTimeDesc());
		List<Coupon> records = page.getRecords();
		if (CollUtils.isEmpty(records)) {
			// * page转dto
			return PageDTO.empty(page);
		}
		// * 转voList
		List<CouponPageVO> voList = BeanUtils.copyList(records, CouponPageVO.class);
		return PageDTO.of(page, voList);
	}

	/**
	 * 发放优惠劵
	 */
	@Override
	// * 如果多线程执行，声明式事务失效
	@Transactional
	public void issueCoupon(CouponIssueFormDTO dto) {
		log.info(">>>>> issueCoupon by {}", Thread.currentThread().getId());
		// * 根据id查询优惠劵
		Coupon coupon = getById(dto.getId());
		if (coupon == null) {
			throw new DbException("指定优惠劵不存在" + dto.getId());
		}
		// * 判断状态，应为暂停或待发放
		CouponStatus status = coupon.getStatus();
		if (status != CouponStatus.PAUSE && status != CouponStatus.DRAFT) {
			throw new BizIllegalException("该优惠劵状态不允许发放： " + status.getDesc());
		}
		// * 判断是否立即发放（立即发放没有开始发放时间）
		boolean isBegin = dto.getIssueBeginTime() == null;
		// * 拷贝属性
		Coupon couponToUpdate = BeanUtils.copyBean(dto, Coupon.class);
		// * 更新状态与发放时间
		if (isBegin) {
			couponToUpdate.setStatus(CouponStatus.ISSUING);
			couponToUpdate.setIssueBeginTime(LocalDateTime.now());
		} else {
			couponToUpdate.setStatus(CouponStatus.UN_ISSUE);
			if (LocalDateTime.now().isAfter(dto.getIssueBeginTime())) {
				couponToUpdate.setStatus(CouponStatus.ISSUING);
			}
		}
		// * 更新数据库
		updateById(couponToUpdate);
		// * 如果立即发放，现在就缓存至Redis，不然延期缓存
		if (isBegin) {
			coupon.setIssueBeginTime(couponToUpdate.getIssueBeginTime());
			coupon.setIssueEndTime(dto.getIssueEndTime());
			userCouponService.cacheCouponInfoWithLua(coupon);
		}
		// * 使用兑换码获取并且未发放（暂停状态之前已生成兑换码，不应重复生成）
		if (coupon.getObtainWay() == ObtainType.ISSUE && coupon.getStatus() == CouponStatus.DRAFT) {
			// * 从dto补全发行结束时间（兑换码失效时间）
			coupon.setIssueEndTime(dto.getIssueEndTime());
			exchangeCodeService.generateCode(coupon);
		}
	}

	@Override
	public void cacheCouponInfo(Coupon coupon) {
		String key = PromotionConstants.COUPON_CACHE_PREFIX + coupon.getId();
		Map<String, String> couponInfoMap = new HashMap<>();
		// * 仅放入用于后续领取/兑换时需要校验的字段值
		couponInfoMap.put("issueBeginTime",
				coupon.getIssueBeginTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		couponInfoMap.put("issueEndTime",
				coupon.getIssueEndTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
		couponInfoMap.put("totalNum", coupon.getTotalNum().toString());
		couponInfoMap.put("userLimit", coupon.getUserLimit().toString());
		redisTemplate.opsForHash()
				.putAll(key, couponInfoMap);
	}

	/**
	 * 更新优惠劵
	 */
	@Override
	public void updateCoupon(CouponFormDTO dto) {
		// * 无更新目标
		Long couponId = dto.getId();
		if (couponId == null) {
			return;
		}
		// * 查询是否有待更新的数据
		Coupon coupon = lambdaQuery()
				.eq(Coupon::getId, couponId)
				.eq(Coupon::getStatus, CouponStatus.DRAFT)
				.one();
		if (coupon == null) {
			throw new BadRequestException("没有可更新的数据");
		}
		// * 拷贝更新
		Coupon couponToUpdate = BeanUtils.copyBean(dto, Coupon.class);
		updateById(couponToUpdate);
		// * 如果以前指定了范围，先删除
		if (coupon.getSpecific()) {
			// * 唯一键（优惠劵id）查询并删除
			couponScopeService.lambdaUpdate()
					.eq(CouponScope::getCouponId, couponId)
					.remove();
		}
		// * 新的数据指定了范围，构造保存范围数据
		Set<Long> scopes = new HashSet<>(dto.getScopes());
		if (dto.getSpecific() && CollUtils.isNotEmpty(scopes)) {
			List<CouponScope> couponScopeList = scopes.stream()
					.map(s -> new CouponScope().setCouponId(couponId).setBizId(s))
					.collect(Collectors.toList());
			couponScopeService.saveBatch(couponScopeList);
		}
	}

	/**
	 * 删除优惠劵
	 */
	@Override
	@Transactional
	public void deleteCoupon(Long id) {
		// * 没有id就没有待删除数据
		if (id == null) {
			return;
		}
		// * 只有待发放状态可以删除
		Coupon coupon = getById(id);
		if (coupon.getStatus() != CouponStatus.DRAFT) {
			throw new BadRequestException("优惠劵不处于可删除（待发放）状态");
		}
		// * 删除本身与限定条件
		removeById(id);
		if (coupon.getSpecific()) {
			couponScopeService.lambdaUpdate()
					.eq(CouponScope::getCouponId, id)
					.remove();
		}
	}

	/**
	 * 根据id查询优惠劵详情
	 */
	@Override
	public CouponDetailVO queryCouponDetailById(Long id) {
		// * 健壮性校验
		CouponDetailVO vo = new CouponDetailVO();
		// * 无指定id，空vo
		if (id == null) {
			return vo;
		}
		// * 查询coupon
		Coupon coupon = getById(id);
		if (coupon == null) {
			throw new BadRequestException("没有对应id的优惠劵");
		}
		// * 基础拷贝
		vo = BeanUtils.copyBean(coupon, CouponDetailVO.class);
		// * 如果限定范围，补全限定范围
		if (coupon.getSpecific()) {
			// * 查询限定范围
			List<CouponScope> scopes = couponScopeService.lambdaQuery()
					.eq(CouponScope::getCouponId, id)
					.list();
			List<CouponScopeVO> scopeVOList = new ArrayList<>();
			// * 不为空，查询对应各限定范围项名字
			if (CollUtils.isNotEmpty(scopes)) {
				// * 选择任一级id最终都会对应到三级id，所以直接调用三级id接口
				// * stream不改变顺序，新增与更新均采用Set更新，不存在重复的可能，可以直接适配List传参
				List<Long> bizIds = scopes.stream()
						.map(CouponScope::getBizId)
						.collect(Collectors.toList());
				List<String> scopeNames = categoryCache.getNameByLv3Ids(bizIds);
				// * 组装集合
				for (int i = 0; i < scopes.size(); i++) {
					CouponScopeVO scopeVO = new CouponScopeVO();
					scopeVO.setId(scopes.get(i).getBizId());
					scopeVO.setName(scopeNames.get(i));
					scopeVOList.add(scopeVO);
				}
			}
			// * 封装限定范围集合至vo
			vo.setScopes(scopeVOList);
		}
		// * 返回vo
		return vo;
	}

	/**
	 * 定时发放未发放状态的优惠劵
	 */
	@Override
	public void checkAndIssueCoupons(int shardIndex, int shardTotal, int size) {
		LocalDateTime now = LocalDateTime.now();
		CouponStatus oldStatus = CouponStatus.UN_ISSUE;
		CouponStatus newStatus = CouponStatus.ISSUING;

		// * MOD筛选id，数据修改不重叠，不会多次修改
		getBaseMapper().updateCouponIssueStatusByPage(shardIndex, shardTotal, size, oldStatus, now, newStatus);
	}

	/**
	 * 截止优惠劵发放
	 */
	@Override
	public void checkAndFinishCoupons(int shardIndex, int shardTotal, int size) {
		LocalDateTime now = LocalDateTime.now();
		CouponStatus oldStatus = CouponStatus.ISSUING;
		CouponStatus newStatus = CouponStatus.FINISHED;
		getBaseMapper().updateCouponFinishStatusByPage(shardIndex, shardTotal, size, oldStatus, now, newStatus);
	}

	/**
	 * 暂停发放优惠劵
	 */
	@Override
	public void pauseCouponIssue(Long id) {
		if (id == null) {
			return;
		}
		lambdaUpdate()
				.eq(Coupon::getId, id)
				.eq(Coupon::getStatus, CouponStatus.ISSUING)
				.set(Coupon::getStatus, CouponStatus.PAUSE)
				.update();
		String key = PromotionConstants.COUPON_CACHE_PREFIX + id;
		redisTemplate.opsForHash().delete(key, "issueBeginTime", "issueEndTime", "totalNum", "userLimit");
	}

	/**
	 * 用户端查询发放中优惠劵
	 */
	@Override
	public List<CouponVO> queryIssuingCouponList() {
		// * 查询发放中且为手动领取的优惠劵
		List<Coupon> couponList = lambdaQuery()
				.eq(Coupon::getStatus, CouponStatus.ISSUING)
				.eq(Coupon::getObtainWay, ObtainType.PUBLIC)
				.list();
		if (CollUtils.isEmpty(couponList)) {
			return CollUtils.emptyList();
		}
		// * 优惠劵id集合和用户id查用户卷表，获得当前用户领取的优惠卷集合
		List<Long> couponIds = couponList.stream()
				.map(Coupon::getId)
				.collect(Collectors.toList());
		Long userId = UserContext.getUser();
		List<UserCoupon> userCouponList = userCouponService.lambdaQuery()
				.eq(UserCoupon::getUserId, userId)
				.in(UserCoupon::getCouponId, couponIds)
				.list();
		// * stream分组统计用户领取的优惠劵与对应数量
		Map<Long, Long> userCouponMap = userCouponList.stream()
				.collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
		// * stream分组统计用户领了没用的优惠劵与对应数量
		Map<Long, Long> userCouponUnusedMap = userCouponList.stream()
				.filter(uc -> uc.getStatus() == UserCouponStatus.UNUSED)
				.collect(Collectors.groupingBy(UserCoupon::getCouponId, Collectors.counting()));
		List<CouponVO> voList = new ArrayList<>();
		// * 遍历封装
		for (Coupon coupon : couponList) {
			CouponVO vo = BeanUtils.copyBean(coupon, CouponVO.class);
			// * 是否可领：issueNum < totalNum && 当前用户领取当前卷量 < userLimit
			vo.setAvailable(coupon.getIssueNum() < coupon.getTotalNum() && userCouponMap.getOrDefault(coupon.getId(),
					0L) < coupon.getUserLimit());
			// * 是否可用：当前用户当前卷没使用的量 > 0
			vo.setReceived(userCouponUnusedMap.getOrDefault(coupon.getId(), 0L) > 0);
			voList.add(vo);
		}
		return voList;
	}

	@Override
	@Transactional
	public void checkAndIssueCoupons2PC(int shardIndex, int shardTotal, int size, String key, List<String> waitingKeys) {
		LocalDateTime now = LocalDateTime.now();
		// * 分页查询，只要其他事务没有在读取前提交，数据就不会冲突
		Page<Coupon> resultPage = lambdaQuery()
				.eq(Coupon::getStatus, CouponStatus.UN_ISSUE)
				.le(Coupon::getIssueBeginTime, now)
				.page(new Page<>(shardIndex, size));
		// * 更改状态，更新数据库
		List<Coupon> records = resultPage.getRecords();
		if (CollUtils.isNotEmpty(records)) {
			for (Coupon record : records) {
				record.setStatus(CouponStatus.ISSUING);
			}
			updateBatchById(records);
		}
		// * 完成业务，写入自己对应的redis队列
		redisTemplate.opsForList().leftPush(key, "random value");
		// * 尝试读取其他的队列，获取成功，提交，不成功超时，同样提交（xxljob中设置任务超时[时间大于此处等待，但小于业务周期]，让执行超时的进程自行结束任务）
		long timeout = CouponHandler.COUPON_STATUS_UPDATE_TIMEOUT;
		TimeUnit unit = CouponHandler.COUPON_STATUS_UPDATE_TIMEOUT_UNIT;
		// * 有一个超时直接提交
		long before = DateUtils.toEpochMilli(LocalDateTime.now());
		for (String waitingKey : waitingKeys) {
			String result = redisTemplate.opsForList().leftPop(waitingKey, timeout, unit);
			// * 等待超时，业务提交
			if (result == null) {
				return;
			}
			long passedSeconds = (DateUtils.toEpochMilli(LocalDateTime.now()) - before) / 1000;
			timeout -= passedSeconds;
			// * 总计等待超时，业务提交
			if (timeout <= 0) {
				return;
			}
		}
		// * 成功获得所有其他队列的数据，执行成功业务提交
	}

	@Override
	@Transactional
	public void checkAndFinishCoupons2PC(int shardIndex, int shardTotal, int size, String key, List<String> waitingKeys) {
		LocalDateTime now = LocalDateTime.now();
		// * 分页查询，只要其他事务没有在读取前提交，数据就不会冲突
		Page<Coupon> resultPage = lambdaQuery()
				.eq(Coupon::getStatus, CouponStatus.ISSUING)
				.le(Coupon::getIssueEndTime, now)
				.page(new Page<>(shardIndex, size));
		// * 更改状态，更新数据库
		List<Coupon> records = resultPage.getRecords();
		if (CollUtils.isNotEmpty(records)) {
			for (Coupon record : records) {
				record.setStatus(CouponStatus.FINISHED);
			}
			updateBatchById(records);
		}
		// * 完成业务，写入自己对应的redis队列
		redisTemplate.opsForList().leftPush(key, "random value");
		// * 尝试读取其他的队列，获取成功，提交，不成功超时，同样提交（xxljob中设置任务超时[时间大于此处等待，但小于业务周期]，让执行超时的进程自行结束任务）
		long timeout = CouponHandler.COUPON_STATUS_UPDATE_TIMEOUT;
		TimeUnit unit = CouponHandler.COUPON_STATUS_UPDATE_TIMEOUT_UNIT;
		// * 有一个超时直接提交
		long before = DateUtils.toEpochMilli(LocalDateTime.now());
		for (String waitingKey : waitingKeys) {
			String result = redisTemplate.opsForList().leftPop(waitingKey, timeout, unit);
			// * 等待超时，业务提交
			if (result == null) {
				return;
			}
			long passedSeconds = (DateUtils.toEpochMilli(LocalDateTime.now()) - before) / 1000;
			timeout -= passedSeconds;
			// * 总计等待超时，业务提交
			if (timeout <= 0) {
				return;
			}
		}
		// * 成功获得所有其他队列的数据，执行成功业务提交
	}
}
