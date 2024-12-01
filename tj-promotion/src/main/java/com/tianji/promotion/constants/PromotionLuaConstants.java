package com.tianji.promotion.constants;

import java.time.ZoneId;

/**
 * @author CamelliaV
 * @since 2024/11/28 / 13:15
 */

public interface PromotionLuaConstants {
	int ONLY_COUPON_NOT_EXIST = 1;
	int ONLY_USER_COUPON_NOT_EXIST = 2;
	int BOTH_NOT_EXIST = 6;
	int INVALID_TIME = 3;
	int INVALID_INVENTORY = 4;
	int EXCEED_USER_LIMIT = 5;
	int SUCCESS = 0;
	// * 原则上应该在nacos配置，简化起见就放这了
	ZoneId ZONE_ID = ZoneId.of("Asia/Shanghai");
}
