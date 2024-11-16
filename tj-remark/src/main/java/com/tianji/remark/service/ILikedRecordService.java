package com.tianji.remark.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;

import java.util.List;
import java.util.Set;

/**
 * <p>
 * 点赞记录表 服务类
 * </p>
 *
 * @author CamelliaV
 * @since 2024-11-13
 */
public interface ILikedRecordService extends IService<LikedRecord> {

    void addOrDeleteLikeRecord(LikeRecordFormDTO dto);

    Set<Long> queryLikedListByUserIdsAndBizIds(String bizType, List<Long> bizIds);

    void readLikeTimesAndSendMq(String bizType, int maxBizSize);

    void addOrDeleteLikeRecordPersistent(LikeRecordFormDTO dto);

    void syncLikeRecordsToDb(String bizType, int maxKeyScanSingle, int maxAllUpdate);

    Set<Long> queryLikedListByUserIdsAndBizIdsPersistent(String bizType, List<Long> bizIds);
}
