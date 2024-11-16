package com.tianji.api.client.remark.fallback;

import com.tianji.api.client.remark.RemarkClient;
import com.tianji.common.utils.CollUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;

import java.util.List;
import java.util.Set;

/**
 * @author CamelliaV
 * @since 2024/11/13 / 23:39
 */
@Slf4j
public class RemarkClientFallback implements FallbackFactory<RemarkClient> {
    @Override
    public RemarkClient create(Throwable cause) {
        return new RemarkClient() {
            @Override
            public Set<Long> queryLikedListByUserIdsAndBizIds(String bizType, List<Long> bizIds) {
                return CollUtils.emptySet();
            }
        };
    }
}
