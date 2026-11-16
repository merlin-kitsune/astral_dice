package com.merlinkitsune.astral_dice.data;

/**
 * 与 Minecraft 版本无关的资源 id 校验（{@code namespace:path}）。
 *
 * <p>1.20.1 / 1.21.1 用 {@code ResourceLocation}，26.1.2 起改名 {@code Identifier}；
 * 公共数据模型只保存字符串，由各平台在边界上转换成自己的 id 类型。
 */
public final class ResourceId {

    private ResourceId() {
    }

    /** 校验并原样返回；不合法则抛 {@link IllegalArgumentException}。 */
    public static String require(String value, String where) {
        if (!isValid(value)) {
            throw new IllegalArgumentException(where + " 不是合法资源 id: " + value);
        }
        return value;
    }

    /** 是否形如 {@code namespace:path}（小写字母 / 数字 / _ / - / .，path 额外允许 /）。 */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        int colon = value.indexOf(':');
        if (colon <= 0 || colon == value.length() - 1 || value.indexOf(':', colon + 1) >= 0) {
            return false;
        }
        return isValidPart(value.substring(0, colon), false)
                && isValidPart(value.substring(colon + 1), true);
    }

    private static boolean isValidPart(String part, boolean allowSlash) {
        if (part.isEmpty() || part.charAt(0) == '/' || part.charAt(part.length() - 1) == '/') {
            return false;
        }
        for (int i = 0; i < part.length(); i++) {
            char c = part.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '_' || c == '-' || c == '.' || (allowSlash && c == '/');
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
