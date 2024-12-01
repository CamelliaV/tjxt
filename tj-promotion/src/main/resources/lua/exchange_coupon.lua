--[[
  返回值枚举
  1 - 仅COUPON不存在
  2 - 仅用户条目不存在
  6 - 都不存在
  -- quick fail 查库写入redis
  3 - 判断发放时间 < begin || > end
  4 - 判断库存 <= 0
  5 - 判断已兑换与已领取量increment 后 > userLimit(coupon)
  （无失败）减少totalNum
  0 - 成功
  -- MQ异步更新时延，查库后重查redis
]]--
local ONLY_COUPON_NOT_EXIST = 1
local ONLY_USER_COUPON_NOT_EXIST = 2
local BOTH_NOT_EXIST = 6
local INVALID_TIME = 3
local INVALID_INVENTORY = 4
local EXCEED_USER_LIMIT = 5
local SUCCESS = 0

-- KEYS[1] coupon | keys[2] argv[1] userCoupon
-- lua & redis 返回 unix timestamp（s单位）
-- coupon不存在
local couponKey = KEYS[1]
local userCouponKey = KEYS[2]
local exchangeCouponKey = KEYS[3]
local userId = ARGV[1]
-- ! 返回0如果不存在，注意lua 0为true！
local isCouponExist = redis.call('EXISTS', couponKey) > 0 and true or false
-- 返回nil如果不存在 nil为false
local isUserCouponExist = redis.call('HGET', userCouponKey, userId) and true or false
-- 仅COUPON不存在
-- redis.log(redis.LOG_WARNING, '0validation...')
if (not isCouponExist and isUserCouponExist) then
    -- redis.log(redis.LOG_WARNING, 'ONLY_COUPON_NOT_EXIST...')
    return ONLY_COUPON_NOT_EXIST
end
-- 仅用户条目不存在
-- redis.log(redis.LOG_WARNING, '1validation...')
if (isCouponExist and not isUserCouponExist) then
    return ONLY_USER_COUPON_NOT_EXIST
end
-- 都不存在
-- redis.log(redis.LOG_WARNING, 'both...')
if (not isCouponExist and not isUserCouponExist) then
    return BOTH_NOT_EXIST
end
-- redis.log(redis.LOG_WARNING, '3validation...')
-- 校验发放时间
local now = tonumber(redis.call('TIME')[1])
local issueBeginTime = tonumber(redis.call('HGET', couponKey, 'issueBeginTime'))
local issueEndTime = tonumber(redis.call('HGET', couponKey, 'issueEndTime'))
if (now < issueBeginTime or now > issueEndTime) then
    return INVALID_TIME -- 过期/未到时间
end
-- 校验库存
if (tonumber(redis.call('HGET', couponKey, 'totalNum')) <= 0) then
    return INVALID_INVENTORY -- 库存不足
end
-- 校验已领取数
-- hincrby返回修改后数据
if (tonumber(redis.call('SETNX', exchangeCouponKey, userId)) == 0 or tonumber(redis.call('HGET', couponKey, 'userLimit')
) <
        redis.call('HINCRBY',
                userCouponKey,
                userId,
                '1')) then
    return EXCEED_USER_LIMIT -- 用户领取超限
end
-- 业务正常，总数-1
redis.call('HINCRBY', couponKey, 'totalNum', '-1')
return SUCCESS