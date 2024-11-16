package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import io.lettuce.core.RedisException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-13
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {

    private final RabbitMqHelper mqHelper;

    private final StringRedisTemplate redisTemplate;

    private final LikedRecordMapper likedRecordMapper;

    /**
     * 新增或取消点赞
     */
    @Override
    public void addOrDeleteLikeRecord(LikeRecordFormDTO dto) {
        // * 拼装Key定位Set
        String key = RedisConstants.LIKES_ALL_KEY_PREFIX + dto.getBizType() + ":" + dto.getBizId();
        boolean success = false;
        String user = UserContext.getUser()
                                 .toString();
        // * 通过Set返回值进行后续业务
        // * 点赞
        Long result;
        if (dto.getLiked()) {
            result = redisTemplate.opsForSet()
                                  .add(key, user);

        } else {
            // * 删除
            result = redisTemplate.opsForSet()
                                  .remove(key, user);
        }
        success = result != null && result > 0;

        if (success) {
            // * 成功操作，统计当前点赞数
            Long likes = redisTemplate.opsForSet()
                                      .size(key);
            // * 无数据更新
            if (likes == null) {
                return;
            }
            // * key为业务类型，member业务id，score点赞数
            redisTemplate.opsForZSet()
                         .add(RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType(), dto.getBizId()
                                                                                           .toString(), likes);
            // * 业务结束，剩余更新对应业务点赞数到对应数据库放在定时任务做
        }

    }

    /**
     * 用户id+业务id（类型）查询点赞状态
     */
    @Override
    public Set<Long> queryLikedListByUserIdsAndBizIds(String bizType, List<Long> bizIds) {
        // * 批量查询对应业务id项用户点赞记录
        List<Object> objects = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long bizId : bizIds) {
                    String key = RedisConstants.LIKES_ALL_KEY_PREFIX + bizType + ":" + bizId;
                    connection.sIsMember(key.getBytes(), UserContext.getUser()
                                                                    .toString()
                                                                    .getBytes());
                }
                return null;
            }
        });
        // * 返回点赞过的业务id
        Set<Long> likedBizIds = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            Boolean isMember = (Boolean) objects.get(i);
            if (isMember) {
                likedBizIds.add(bizIds.get(i));
            }
        }
        return likedBizIds;
    }

    /**
     * 批量读取Redis点赞数数据，发送到MQ
     */
    @Override
    public void readLikeTimesAndSendMq(String bizType, int maxBizSize) {
        // * 查询Redis点赞数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + bizType;
        Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet()
                                                                          .popMin(key, maxBizSize);
        // // * 随机抽maxBizSize个同步数据库，不删除Redis
        // Set<ZSetOperations.TypedTuple<String>> typedTuples = redisTemplate.opsForZSet().distinctRandomMembersWithScore(key, maxBizSize);
        // * 无数据不操作
        if (CollUtils.isEmpty(typedTuples)) {
            return;
        }
        // * 封装dto
        List<LikedTimesDTO> dtoList = new ArrayList<>();
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            LikedTimesDTO dto = new LikedTimesDTO();
            String bizIdRaw = tuple.getValue();
            Double likesRaw = tuple.getScore();
            // * Redis数据不完整
            if (bizIdRaw == null || likesRaw == null) {
                continue;
            }
            dto.setBizId(Long.valueOf(bizIdRaw));
            dto.setLikedTimes(likesRaw.intValue());
            dtoList.add(dto);
        }
        // * 推送MQ
        mqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE, StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, bizType), dtoList);
    }

    /**
     * 新增或取消点赞（支持数据库持久化）分离存储的Set与更新Set以实现增量更新
     */
    @Override
    public void addOrDeleteLikeRecordPersistent(LikeRecordFormDTO dto) {
        String bizType = dto.getBizType();
        String bizIdString = dto.getBizId()
                                .toString();
        // * 拼装Key定位Set，使用new与del实现增量更新，避免每次必须全量更新
        String newKey = RedisConstants.LIKES_NEW_KEY_PREFIX + bizType + ":" + bizIdString;
        String delKey = RedisConstants.LIKES_DEL_KEY_PREFIX + bizType + ":" + bizIdString;
        String allKey = RedisConstants.LIKES_ALL_KEY_PREFIX + bizType + ":" + bizIdString;
        boolean success = false;
        Long userId = UserContext.getUser();
        String user = userId.toString();
        // * 通过Set返回值进行后续业务
        List<Object> results;
        if (dto.getLiked()) {
            // * 点赞
            // * 移出DEL;加入Redis NEW与ALL;NEW作数据库更新，ALL作全数据;避免NEW与DEL冲突(用户点完就取消)
            results = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @SuppressWarnings({"unchecked", "NullableProblems"})
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForZSet()
                              .remove(delKey, user);
                    operations.opsForZSet()
                              .add(newKey, user, 0);
                    operations.opsForSet()
                              .add(allKey, user);
                    return null;
                }
            });
        } else {
            // * 取消
            // * 移出NEW;加入Redis DEL与移出ALL;DEL作数据库更新，ALL作全数据
            results = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @SuppressWarnings({"unchecked", "NullableProblems"})
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForZSet()
                              .remove(newKey, user);
                    operations.opsForZSet()
                              .add(delKey, user, 0);
                    operations.opsForSet()
                              .remove(allKey, user);
                    return null;
                }
            });
        }
        success = CollUtils.isNotEmpty(results) && (Long) results.get(results.size() - 1) > 0;
        // * 是否成功（避免操作重复）
        if (success) {
            String timesKey = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
            // * 成功操作，修改当前点赞数
            results = redisTemplate.executePipelined(new SessionCallback<Object>() {
                @SuppressWarnings({"unchecked", "NullableProblems"})
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.opsForZSet()
                              .score(timesKey, bizIdString);
                    operations.opsForZSet()
                              .incrementScore(timesKey, bizIdString, dto.getLiked() ? 1 : -1);
                    return null;
                }
            });
            // * Redis操作失败
            if (CollUtils.isEmpty(results)) {
                throw new RedisException("Redis修改点赞计数失败");
            }
            // * 已有原始计数数据，业务结束
            if (results.get(0) != null) {
                return;
            }
            // * 不存在原始计数数据，查询数据库，将原始数据加上
            Integer count = lambdaQuery()
                    .eq(LikedRecord::getBizType, bizType)
                    .eq(LikedRecord::getBizId, dto.getBizId())
                    .count();
            // * 数据库不存在数据，结束业务
            if (count == null || count == 0) {
                return;
            }
            // * 加入原始数据
            redisTemplate.opsForZSet()
                         .incrementScore(timesKey, bizIdString, count);
        }
    }


    @Override
    public void syncLikeRecordsToDb(String bizType, int maxKeyScanSingle, int maxAllUpdate) {
        // * 构造Redis key匹配模板
        String newKeyAsterisk = RedisConstants.LIKES_NEW_KEY_PREFIX + bizType + ":*";
        String delKeyAsterisk = RedisConstants.LIKES_DEL_KEY_PREFIX + bizType + ":*";
        // * 构造Redis Scan参数，限定单个更新队列Key数量上限
        ScanOptions newScanOptions = ScanOptions.scanOptions()
                                                .match(newKeyAsterisk)
                                                .count(maxKeyScanSingle)
                                                .build();
        ScanOptions delScanOptions = ScanOptions.scanOptions()
                                                .match(delKeyAsterisk)
                                                .count(maxKeyScanSingle)
                                                .build();
        // * 构造bizId 为 key，userId全体为集合 作 value
        Map<Long, Set<ZSetOperations.TypedTuple<String>>> newRecords = new HashMap<>();
        Map<Long, Set<ZSetOperations.TypedTuple<String>>> delRecords = new HashMap<>();
        // * 取出新增数据
        fetchRecordsFromRedisByScan(newScanOptions, newRecords, maxAllUpdate);
        // * 剩余更新配额至少还剩 1 / 8
        int remaining = maxAllUpdate - newRecords.size();
        // * 取出删除数据
        if (remaining > maxAllUpdate / 8) {
            fetchRecordsFromRedisByScan(delScanOptions, delRecords, remaining);
        }
        // * 非空时操作
        List<LikedRecord> recordsToUpdate = buildRecordsToUpdateList(bizType, newRecords);
        if (CollUtils.isNotEmpty(recordsToUpdate)) {
            try {
                saveBatch(recordsToUpdate);
            } catch (DataIntegrityViolationException e) {
                log.debug("重复插入数据");
            }
        }
        // * 非空时操作
        recordsToUpdate = buildRecordsToUpdateList(bizType, delRecords);
        if (CollUtils.isNotEmpty(recordsToUpdate)) {
            likedRecordMapper.batchDeleteByUniqueKey(recordsToUpdate);
        }
    }

    private List<LikedRecord> buildRecordsToUpdateList(String bizType, Map<Long, Set<ZSetOperations.TypedTuple<String>>> recordsMap) {
        List<LikedRecord> recordsToUpdate = new ArrayList<>();
        if (CollUtils.isNotEmpty(recordsMap)) {
            for (Long bizId : recordsMap.keySet()) {
                Set<ZSetOperations.TypedTuple<String>> typedTuples = recordsMap.get(bizId);
                for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
                    String userIdString = tuple.getValue();
                    Long userId = Long.valueOf(userIdString);
                    LikedRecord record = new LikedRecord();
                    record.setBizId(bizId)
                          .setUserId(userId)
                          .setBizType(bizType);
                    recordsToUpdate.add(record);
                }
            }
        }
        return recordsToUpdate;
    }

    private void fetchRecordsFromRedisByScan(ScanOptions scanOptions, Map<Long, Set<ZSetOperations.TypedTuple<String>>> recordsMap, int remaining) {
        // * 打开Redis cursor 扫描各个key，取出总计不超过总更新配额的数据用于更新
        // * 由于已经有了全局异常处理，这里不做异常处理
        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(connection -> connection.scan(scanOptions))) {
            while (cursor != null && cursor.hasNext() && recordsMap.size() < remaining) {
                String key = new String(cursor.next());
                String[] split = key.split(":");
                Long bizId = Long.valueOf(split[split.length - 1]);
                int quota = remaining - recordsMap.size();
                Set<ZSetOperations.TypedTuple<String>> records = redisTemplate.opsForZSet()
                                                                              .popMin(key, quota);
                if (CollUtils.isNotEmpty(records)) {
                    Set<ZSetOperations.TypedTuple<String>> tupleSet = recordsMap.putIfAbsent(bizId, records);
                    if (tupleSet != null) {
                        tupleSet.addAll(records);
                    }
                }
            }
        }
    }

    /**
     * 查询点赞情况，先查Redis，没有就查数据库
     */
    @Override
    public Set<Long> queryLikedListByUserIdsAndBizIdsPersistent(String bizType, List<Long> bizIds) {
        Long userId = UserContext.getUser();
        String user = userId.toString();
        // * 批量查询对应业务id项用户点赞记录
        List<Object> objects = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long bizId : bizIds) {
                    String allKey = RedisConstants.LIKES_ALL_KEY_PREFIX + bizType + ":" + bizId;
                    String delKey = RedisConstants.LIKES_DEL_KEY_PREFIX + bizType + ":" + bizId;
                    // * Redis中是否有
                    connection.sIsMember(allKey.getBytes(), user.getBytes());
                    // * 防止删除没同步时数据库有记录
                    connection.zScore(delKey.getBytes(), user.getBytes());
                }
                return null;
            }
        });
        // * 如果在ALL set则已点赞，如果在DEL zset存在则已取消
        Set<Long> likedBizIds = new HashSet<>();
        Set<Long> secondCheckBizIds = new HashSet<>();
        // * 一次两条命令返回结果
        for (int i = 0; i < objects.size(); i += 2) {
            Boolean isLiked = (Boolean) objects.get(i);
            Double isDel = (Double) objects.get(i + 1);
            // * 确认已点赞
            if (isLiked) {
                likedBizIds.add(bizIds.get(i));
                continue;
            }
            // * 已经删除，跳过
            if (isDel != null) {
                continue;
            }
            // * Redis结束，但点赞状态仍不确定，需要二次检查数据库
            secondCheckBizIds.add(bizIds.get(i));
        }
        // * 检查数据库
        List<LikedRecord> records = lambdaQuery()
                .eq(LikedRecord::getUserId, userId)
                .eq(LikedRecord::getBizType, bizType)
                .in(LikedRecord::getBizId, secondCheckBizIds)
                .list();
        // * 二次检查列表里不存在点赞过的
        if (CollUtils.isEmpty(records)) {
            return likedBizIds;
        }
        // * 转id返回
        Set<Long> likedBizIdDbSet = records.stream()
                                           .map(LikedRecord::getBizId)
                                           .collect(Collectors.toSet());
        // * 写入Redis避免下次查数据库
        redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                for (Long bizId : likedBizIdDbSet) {
                    String allKey = RedisConstants.LIKES_ALL_KEY_PREFIX + bizType + ":" + bizId;
                    connection.sAdd(allKey.getBytes(), user.getBytes());
                }
                return null;
            }
        });
        // * 补充进结果集，返回最终点赞数据
        likedBizIds.addAll(likedBizIdDbSet);
        return likedBizIds;
    }
}
