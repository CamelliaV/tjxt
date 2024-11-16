package com.tianji.remark.task;

import com.tianji.common.constants.Constant;
import com.tianji.remark.service.ILikedRecordService;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author CamelliaV
 * @since 2024/11/14 / 13:30
 */
@Component
@RequiredArgsConstructor
public class LikeTimesCheckTask {

    private static final List<String> BIZ_TYPES = List.of(Constant.CONFIG_BIZTYPE_QA);
    private static final int MAX_BIZ_SIZE = 30;
    private final ILikedRecordService likeService;

    //    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    @XxlJob("readLikeTimesAndSendMq")
    public void readLikeTimesAndSendMq() {
        for (String bizType : BIZ_TYPES) {
            likeService.readLikeTimesAndSendMq(bizType, MAX_BIZ_SIZE);
        }
    }
}
