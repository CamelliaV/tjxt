package com.tianji.api.client.remark;

import com.tianji.api.client.remark.fallback.RemarkClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Set;

/**
 * @author CamelliaV
 * @since 2024/11/13 / 23:34
 */
@FeignClient(name = "remark-service", fallbackFactory = RemarkClientFallback.class)
public interface RemarkClient {
    @GetMapping("/likes/list")
    Set<Long> queryLikedListByUserIdsAndBizIds(@RequestParam("bizType") String bizType, @RequestParam("bizIds") List<Long> bizIds);
}
