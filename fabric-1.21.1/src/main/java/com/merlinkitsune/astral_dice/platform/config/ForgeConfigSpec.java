package com.merlinkitsune.astral_dice.platform.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置规格(Fabric 侧实现) —— 类名与嵌套类型名对齐 Forge 的
 * {@code common.ForgeConfigSpec},使 {@code config/ModCommonConfig}
 * **只需换 import**,配置项定义、注释与 {@code snapshot()} 全部零改动。
 *
 * <h2>为什么自实现而不是引 Cloth Config</h2>
 * <ul>
 *   <li>本模组的配置是**服务端公共配置**(玩法数值),不是客户端外观设置;引 Cloth Config
 *       会多一个前置,而它本身**不做配置同步**,服务端权威值仍要自己发;
 *   <li>真正需要的只是「定义项 + 落盘 + 读取」—— 即 FORGE 侧由 ForgeConfigSpec/NightConfig 提供的部分。
 *       这里用**自带的最小 TOML 读写**(本模组的配置是扁平的 section/key 结构,够用),
 *       零新增第三方前置。⇒ 与规划文档「保留 TOML 自实现」的口径一致。
 * </ul>
 *
 * <h2>支持的 TOML 子集(够本模组用;超出子集的内容会被忽略而不是报错)</h2>
 * <ul>
 *   <li>注释行 {@code # ...}</li>
 *   <li>节 {@code [section]} / {@code [a.b]}</li>
 *   <li>{@code key = true|false|123} (取第 1 个 {@code #} 之前的部分作为值)</li>
 * </ul>
 *
 * <p>⚠️ 与 Forge 的差异(已登记):不支持字符串/列表/双精度项(本模组不用);
 * 不支持 `worldRestart`/热重载回调(本模组的配置在启动时读一次,与既有行为一致)。
 */
public final class ForgeConfigSpec {

    /** 配置值基类。 */
    public abstract static class ConfigValue<T> {
        private final String path;
        private final T defaultValue;
        private final List<String> comments;
        private T value;

        ConfigValue(String path, T defaultValue, List<String> comments) {
            this.path = path;
            this.defaultValue = defaultValue;
            this.comments = comments;
            this.value = defaultValue;
        }

        public T get() {
            return value;
        }

        public T getDefault() {
            return defaultValue;
        }

        public String getPath() {
            return path;
        }

        List<String> getComments() {
            return comments;
        }

        void setRaw(Object raw) {
            this.value = coerce(raw);
        }

        abstract T coerce(Object raw);

        abstract String render();
    }

    /** 布尔项。 */
    public static final class BooleanValue extends ConfigValue<Boolean> {
        BooleanValue(String path, boolean defaultValue, List<String> comments) {
            super(path, defaultValue, comments);
        }

        @Override
        Boolean coerce(Object raw) {
            if (raw instanceof Boolean b) {
                return b;
            }
            return Boolean.parseBoolean(String.valueOf(raw));
        }

        @Override
        String render() {
            return String.valueOf(get());
        }
    }

    /** 整数项(含 {@code defineInRange} 的区间项)。 */
    public static final class IntValue extends ConfigValue<Integer> {
        IntValue(String path, int defaultValue, List<String> comments) {
            super(path, defaultValue, comments);
        }

        @Override
        Integer coerce(Object raw) {
            if (raw instanceof Number n) {
                return n.intValue();
            }
            try {
                return Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException e) {
                return getDefault();
            }
        }

        @Override
        String render() {
            return String.valueOf(get());
        }
    }

    /** 构建器。 */
    public static final class Builder {
        private final List<ConfigValue<?>> values = new ArrayList<>();
        private final List<String> pathStack = new ArrayList<>();
        private final List<String> pendingComments = new ArrayList<>();

        public Builder comment(String... comments) {
            for (String c : comments) {
                if (c != null) {
                    pendingComments.add(c);
                }
            }
            return this;
        }

        public Builder push(String path) {
            pathStack.add(path);
            return this;
        }

        public Builder pop() {
            if (!pathStack.isEmpty()) {
                pathStack.remove(pathStack.size() - 1);
            }
            return this;
        }

        public BooleanValue define(String key, boolean defaultValue) {
            return add(new BooleanValue(fullPath(key), defaultValue, takeComments()));
        }

        public IntValue define(String key, int defaultValue) {
            return add(new IntValue(fullPath(key), defaultValue, takeComments()));
        }

        public IntValue defineInRange(String key, int defaultValue, int min, int max) {
            IntValue v = new IntValue(fullPath(key), Math.max(min, Math.min(max, defaultValue)), takeComments());
            return add(v);
        }

        public ForgeConfigSpec build() {
            return new ForgeConfigSpec(values);
        }

        private <T extends ConfigValue<?>> T add(T v) {
            values.add(v);
            return v;
        }

        private List<String> takeComments() {
            List<String> out = new ArrayList<>(pendingComments);
            pendingComments.clear();
            return out;
        }

        private String fullPath(String key) {
            if (pathStack.isEmpty()) {
                return key;
            }
            return String.join(".", pathStack) + "." + key;
        }
    }

    private final List<ConfigValue<?>> values;

    private ForgeConfigSpec(List<ConfigValue<?>> values) {
        this.values = values;
    }

    public List<ConfigValue<?>> getValues() {
        return values;
    }

    /**
     * 从 TOML 文件读取(缺文件 / 缺键一律保留默认值,不抛异常)。
     *
     * @return 是否成功读到文件
     */
    public boolean load(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            return false;
        }
        try {
            String section = "";
            for (String rawLine : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = stripComment(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("[") && line.endsWith("]")) {
                    section = line.substring(1, line.length() - 1).trim();
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                String full = section.isEmpty() ? key : section + "." + key;
                for (ConfigValue<?> v : values) {
                    if (v.getPath().equals(full)) {
                        v.setRaw(value);
                    }
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 写出 TOML(目录不存在时创建)。 */
    public boolean save(Path file) {
        if (file == null) {
            return false;
        }
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.write(file, render().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** 渲染为 TOML 文本(带注释与节)。 */
    public String render() {
        StringBuilder sb = new StringBuilder();
        Map<String, List<ConfigValue<?>>> bySection = new LinkedHashMap<>();
        for (ConfigValue<?> v : values) {
            String path = v.getPath();
            int dot = path.lastIndexOf('.');
            String section = dot < 0 ? "" : path.substring(0, dot);
            bySection.computeIfAbsent(section, k -> new ArrayList<>()).add(v);
        }
        for (Map.Entry<String, List<ConfigValue<?>>> e : bySection.entrySet()) {
            if (!e.getKey().isEmpty()) {
                sb.append('\n').append('[').append(e.getKey()).append(']').append('\n');
            }
            for (ConfigValue<?> v : e.getValue()) {
                for (String c : v.getComments()) {
                    sb.append("# ").append(c).append('\n');
                }
                String path = v.getPath();
                int dot = path.lastIndexOf('.');
                String key = dot < 0 ? path : path.substring(dot + 1);
                sb.append(key).append(" = ").append(v.render()).append('\n');
            }
        }
        return sb.toString();
    }

    private static String stripComment(String line) {
        int idx = line.indexOf('#');
        return idx < 0 ? line : line.substring(0, idx);
    }
}
