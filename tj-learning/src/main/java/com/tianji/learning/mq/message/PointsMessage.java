package com.tianji.learning.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author CamelliaV
 * @since 2024/11/18 / 22:51
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PointsMessage {
    private Long userId;
    private Integer points;
}
