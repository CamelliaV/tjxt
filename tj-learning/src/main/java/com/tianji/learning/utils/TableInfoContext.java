package com.tianji.learning.utils;

/**
 * @author CamelliaV
 * @since 2024/11/21 / 19:32
 */

public class TableInfoContext {
    public final static ThreadLocal<String> TL = new ThreadLocal<>();

    public static String getInfo() {
        return TL.get();
    }

    public static void setInfo(String info) {
        TL.set(info);
    }

    public static void remove() {
        TL.remove();
    }
}
