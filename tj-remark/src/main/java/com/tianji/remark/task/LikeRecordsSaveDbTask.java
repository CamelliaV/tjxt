package com.tianji.remark.task;

import com.tianji.common.constants.Constant;
import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/15 / 20:49
 */
@Component
@RequiredArgsConstructor
public class LikeRecordsSaveDbTask {
    // * 读取常量服务名
    private static final List<String> BIZ_TYPES = List.of(Constant.CONFIG_BIZTYPE_QA);
    private static final int MAX_ALL_UPDATE = 5000;
    private static final int MAX_KEY_SCAN_SINGLE = 30;
    private final ILikedRecordService likeService;

    // * SpringTask方案
//    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
//    public void syncLikeRecordsToDb() {
//        for (String bizType : BIZ_TYPES) {
//            likeService.syncLikeRecordsToDb(bizType, MAX_KEY_SCAN_SINGLE, MAX_ALL_UPDATE);
//        }
//    }
    @XxlJob("syncLikeRecordsToDb")
    public void syncLikeRecordsToDb() {
        for (String bizType : BIZ_TYPES) {
            likeService.syncLikeRecordsToDb(bizType, MAX_KEY_SCAN_SINGLE, MAX_ALL_UPDATE);
        }
    }
}
