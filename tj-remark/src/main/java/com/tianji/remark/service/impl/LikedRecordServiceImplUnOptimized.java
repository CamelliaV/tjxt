package com.tianji.remark.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.DbException;
import com.tianji.common.utils.*;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-13
 */
//@Service
@RequiredArgsConstructor
//@Slf4j
public class LikedRecordServiceImplUnOptimized extends ServiceImpl<LikedRecordMapper, LikedRecord> {

    private final RabbitMqHelper mqHelper;

    private final LikedRecordMapper likeMapper;

    /**
     * 新增或取消点赞
     */
    public void addOrDeleteLikeRecord(LikeRecordFormDTO dto) {
        // ! 依赖唯一索引 unique (biz_id, user_id, biz_type)
        // * 使用两句sql更通用 （先count（不用one，不取出实际数据占带宽）判断存在，不用bool判断）
        // * 点赞或取消
        Boolean isLike = dto.getLiked();
        // * MQ更新点赞数
        boolean shouldUpdate = false;
        if (BooleanUtils.isTrue(isLike)) {
            LikedRecord likedRecord = BeanUtils.copyBean(dto, LikedRecord.class);
            likedRecord.setUserId(UserContext.getUser());
            // * 若已有数据，插入失败，不触发MQ
            Integer count = lambdaQuery()
                    .eq(LikedRecord::getUserId, likedRecord.getUserId())
                    .eq(LikedRecord::getBizType, likedRecord.getBizType())
                    .eq(LikedRecord::getBizId, likedRecord.getBizId())
                    .count();
            if (count == 0) {
                shouldUpdate = true;
                boolean success = save(likedRecord);
                if (!success) {
                    throw new DbException("保存点赞记录数据异常");
                }
            }
            // * 合并一条sql写法（插入失败也会增加auto increment值）
            // * 由于lambdaUpdate无新增方法，save/insert无法绕过DuplicateKeyException直接返回0，采用以下实现
            // * 也可以用insert ignore 和 on duplicate
            /*
            try {
                shouldUpdate = save(likedRecord);
            } catch (DuplicateKeyException ignored) {
            }
             */
        } else {
            // * 不存在数据，删除失败，不触发MQ
            shouldUpdate = lambdaUpdate()
                    .eq(LikedRecord::getUserId, UserContext.getUser())
                    .eq(LikedRecord::getBizType, dto.getBizType())
                    .eq(LikedRecord::getBizId, dto.getBizId())
                    .remove();
        }
        // * 查询最新点赞次数
        Integer likeTimes = lambdaQuery()
                .eq(LikedRecord::getBizId, dto.getBizId())
                .eq(LikedRecord::getBizType, dto.getBizType())
                .count();
        // * 健壮性校验
        if (likeTimes == null) {
            return;
        }
        // * 封装dto发送消息
        LikedTimesDTO likedTimesDTO = new LikedTimesDTO();
        likedTimesDTO.setLikedTimes(likeTimes);
        likedTimesDTO.setBizId(dto.getBizId());
        if (shouldUpdate) {
            mqHelper.send(MqConstants.Exchange.LIKE_RECORD_EXCHANGE, StringUtils.format(MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE, dto.getBizType()), likedTimesDTO);
        }
    }

    /**
     * 用户id+业务id（类型）查询点赞状态
     */
    public Set<Long> queryLikedListByUserIdsAndBizIds(String bizType, List<Long> bizIds) {
        Long userId = UserContext.getUser();
        // * 根据用户id+业务类型+业务id查询
        List<LikedRecord> records = lambdaQuery()
                .eq(LikedRecord::getBizType, bizType)
                .in(LikedRecord::getBizId, bizIds)
                .eq(LikedRecord::getUserId, userId)
                .list();
        if (CollUtils.isEmpty(records)) {
            return CollUtils.emptySet();
        }
        // * 返回点赞了的业务id
        Set<Long> bizIdSet = records.stream()
                                    .map(LikedRecord::getBizId)
                                    .collect(Collectors.toSet());
        return bizIdSet;
    }
}
