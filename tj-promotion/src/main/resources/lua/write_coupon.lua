local couponKey = KEYS[1]
local issueBeginTime = ARGV[1]
local issueEndTime = ARGV[2]
local totalNum = ARGV[3]
local userLimit = ARGV[4]

local SUCCESS = 1
local FAILURE = 0
-- 原子性脚本，只需要一次NX的校验
if (redis.call('hsetnx', couponKey, "issueBeginTime", issueBeginTime) == 0) then
    return FAILURE
end
redis.call('hset', couponKey, "issueEndTime", issueEndTime)
redis.call('hset', couponKey, "totalNum", totalNum)
redis.call('hset', couponKey, "userLimit", userLimit)
return SUCCESS