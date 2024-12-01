package com.tianji.promotion.constants;

public interface PromotionConstants {
	// * 兑换码序号
	String COUPON_CODE_SERIAL_KEY = "coupon:code:serial";
	// * 分布式锁
	String COUPON_RECEIVE_REDIS_LOCK_PREFIX = "coupon:receive:lock:";
	String COUPON_EXCHANGE_REDIS_LOCK_PREFIX = "coupon:exchange:lock:";
	// * 优惠卷与用户卷缓存
	String COUPON_CACHE_PREFIX = "prs:coupon:";
	String USER_COUPON_CACHE_PREFIX = "prs:user:coupon:";
	String EXCHANGE_COUPON_CACHE_PREFIX = "prs:coupon:exchange:";
}
