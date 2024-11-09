package com.tianji.learning.task;

import lombok.Data;

import java.time.Duration;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

/**
 * @author CamelliaV
 * @since 2024/11/8 / 16:23
 */

@Data
public class DelayTask<D> implements Delayed {
    private D data;
    // * nanoseconds
    private long activeTime;

    public DelayTask(D data, Duration delayTime) {
        this.data = data;
        this.activeTime = System.nanoTime() + delayTime.toNanos();
    }

    // * 返回任务执行剩余时间
    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(Math.max(0, activeTime - System.nanoTime()), TimeUnit.NANOSECONDS);
    }

    // * 排序
    @Override
    public int compareTo(Delayed o) {
        long l = getDelay(TimeUnit.NANOSECONDS) - o.getDelay(TimeUnit.NANOSECONDS);
        if (l > 0) {
            return 1;
        } else if (l < 0) {
            return -1;
        } else {
            return 0;
        }
    }
}
