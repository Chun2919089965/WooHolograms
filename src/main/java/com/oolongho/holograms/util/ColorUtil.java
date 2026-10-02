package com.oolongho.holograms.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色工具类（三段式管线的②③段）
 *
 * <p>管线结构：{@link SyntaxNormalizer} 归一化 → 本类统一 MiniMessage 解析 → Component。</p>
 *
 * <p>支持全部主流颜色语法：&amp;/§ 传统代码、&amp;#RRGGBB、{#RRGGBB}、[#RRGGBB]、
 * Iridium/DH 渐变与彩虹、CMI 渐变与彩虹、§x 长十六进制，以及 MiniMessage 全标签
 * （渐变/彩虹/过渡/点击/悬浮/字体等）。</p>
 */
public class ColorUtil {

    // MiniMessage 实例，支持所有标准标签
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // 支持渐变/彩虹/过渡的 MiniMessage 实例（全标签）
    private static final MiniMessage MINI_MESSAGE_WITH_GRADIENT = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.color())
                    .resolver(StandardTags.decorations())
                    .resolver(StandardTags.gradient())
                    .resolver(StandardTags.rainbow())
                    .resolver(StandardTags.transition())
                    .resolver(StandardTags.reset())
                    .resolver(StandardTags.newline())
                    .resolver(StandardTags.translatable())
                    .resolver(StandardTags.keybind())
                    .resolver(StandardTags.insertion())
                    .resolver(StandardTags.clickEvent())
                    .resolver(StandardTags.hoverEvent())
                    .resolver(StandardTags.font())
                    .build())
            .build();

    // Legacy 序列化器（§ 格式，支持 RGB 十六进制 §x 序列，与 CraftChatMessage 解析格式一致）
    private static final LegacyComponentSerializer SECTION_SERIALIZER =
            LegacyComponentSerializer.builder()
                    .character('§')
                    .hexColors()
                    .build();

    // 十六进制颜色模式：&#RRGGBB 与 §#RRGGBB（stripColor 用；解析侧由 SyntaxNormalizer 处理）
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");

    // adventure 序列化输出的紧凑十六进制 §#RRGGBB（CraftChatMessage 不识别，需展开为 §x 长格式）
    private static final Pattern COMPACT_HEX_PATTERN = Pattern.compile("§#([0-9a-fA-F]{6})");

    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("[&§]([0-9a-fA-Fk-oK-OrR])");

    // MiniMessage 标签模式：覆盖 <#hex>、<gradient:...>、<color:...>、</close>、<!negation> 等带参数形式
    private static final Pattern MINI_MESSAGE_TAG_PATTERN = Pattern.compile("<[!?/#]?[a-zA-Z#][^<>]*>");

    /** format() 的组件级 LRU 缓存（key = 归一化+legacy 转换后的 MiniMessage 串） */
    private static final Map<String, Component> FORMAT_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
                    return size() > 256;
                }
            });

    /** 合法 legacy 代码字符表（translateAmpersand 用） */
    private static final String LEGACY_CODE_CHARS = "0123456789AaBbCcDdEeFfKkLlMmNnOoRr";

    /**
     * 统一格式化入口：任意颜色语法 → Adventure Component
     *
     * <p>管线：\n 展开 → {@link SyntaxNormalizer} 归一化 → 纯 legacy 快路径或
     * MiniMessage 解析（带 LRU 缓存）。MiniMessage 解析失败时降级为 legacy 翻译，
     * 任何输入都不会抛出异常或丢字。</p>
     *
     * @param raw 原始文本（任意颜色语法混写）
     * @return 解析后的 Component；null/空文本返回 Component.empty()
     */
    public static Component format(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Component.empty();
        }

        String text = raw.replace("\\n", "\n");

        // 纯文本快路径：无任何语法特征字符
        if (text.indexOf('&') < 0 && text.indexOf('§') < 0 && text.indexOf('<') < 0
                && text.indexOf('{') < 0 && text.indexOf('[') < 0) {
            return Component.text(text);
        }

        String normalized = SyntaxNormalizer.normalize(text);

        // 纯 legacy 快路径：归一化后无 MiniMessage 标签 → 直接 § 翻译反序列化
        if (normalized.indexOf('<') < 0) {
            return SECTION_SERIALIZER.deserialize(translateAmpersand(normalized));
        }

        String mini = legacyToMiniMessage(normalized);
        Component cached = FORMAT_CACHE.get(mini);
        if (cached != null) {
            return cached;
        }

        try {
            Component component = MINI_MESSAGE_WITH_GRADIENT.deserialize(mini);
            FORMAT_CACHE.put(mini, component);
            return component;
        } catch (Exception e) {
            // MiniMessage 解析失败（异常标签等）时降级为 legacy 翻译，保留原文
            return SECTION_SERIALIZER.deserialize(translateAmpersand(normalized));
        }
    }

    /**
     * 统一格式化为 § legacy 字符串（需要传统格式的出口用，如 CraftChatMessage 回退路径）
     *
     * @param raw 原始文本（任意颜色语法）
     * @return § 格式字符串（十六进制为 §x 长格式）
     */
    public static String formatLegacy(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return serializeLegacy(format(raw));
    }

    /**
     * 将颜色代码转换为实际颜色
     * 支持 & 和 § 颜色代码、十六进制颜色（&#/{#/[# 前缀）、Iridium/CMI 渐变与彩虹、MiniMessage 格式
     *
     * @param text 原始文本
     * @return 转换后的 § 格式文本
     * @deprecated 内部请改用 {@link #format(String)}（Component 直通）或
     *             {@link #formatLegacy(String)}（§ 字符串）；本方法保留为兼容别名
     */
    @Deprecated
    public static String colorize(String text) {
        return formatLegacy(text);
    }

    /**
     * & → § 翻译（等价 ChatColor.translateAlternateColorCodes，纯实现无 bukkit 依赖，代码字符统一小写）
     */
    private static String translateAmpersand(String text) {
        if (text.indexOf('&') < 0) {
            return text;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && LEGACY_CODE_CHARS.indexOf(chars[i + 1]) >= 0) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    /**
     * 清空 format 组件缓存（reload 时调用，语言/标签集变化后生效）
     */
    public static void clearFormatCache() {
        FORMAT_CACHE.clear();
    }

    /**
     * 检查文本是否包含 MiniMessage 标签
     *
     * @param text 文本
     * @return 是否包含 MiniMessage 标签
     */
    private static boolean containsMiniMessageTags(String text) {
        if (text == null) {
            return false;
        }
        return MINI_MESSAGE_TAG_PATTERN.matcher(text).find();
    }

    /**
     * 序列化 Component 为 § 格式字符串，并将紧凑十六进制 §#RRGGBB
     * 展开为 CraftChatMessage 可解析的 §x§R§R§G§G§B§B 长格式
     */
    private static String serializeLegacy(Component component) {
        String serialized = SECTION_SERIALIZER.serialize(component);
        Matcher matcher = COMPACT_HEX_PATTERN.matcher(serialized);
        if (!matcher.find()) {
            return serialized;
        }
        StringBuilder result = new StringBuilder(serialized.length() + 16);
        matcher.reset();
        while (matcher.find()) {
            StringBuilder expanded = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                expanded.append('§').append(c);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(expanded.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 移除所有颜色代码
     * 
     * @param text 原始文本
     * @return 无颜色文本
     */
    public static String stripColor(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        text = LEGACY_COLOR_PATTERN.matcher(text).replaceAll("");

        text = HEX_COLOR_PATTERN.matcher(text).replaceAll("");

        text = stripMiniMessageTags(text);

        return text;
    }

    /**
     * 移除 MiniMessage 标签
     * 
     * @param text 原始文本
     * @return 无标签文本
     */
    private static String stripMiniMessageTags(String text) {
        if (text == null) {
            return "";
        }

        try {
            Component component = MINI_MESSAGE.deserialize(text);
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (Exception e) {
            // 如果解析失败，使用正则移除
            return text.replaceAll("<[^>]+>", "");
        }
    }

    /**
     * 将文本转换为 Adventure Component
     * 任意颜色语法（等价 {@link #format(String)}）
     *
     * @param text 原始文本
     * @return Component
     */
    public static Component toComponent(String text) {
        return format(text);
    }

    /**
     * 将 Component 转换为字符串
     * 
     * @param component Component
     * @return 字符串
     */
    public static String fromComponent(Component component) {
        if (component == null) {
            return "";
        }
        return serializeLegacy(component);
    }

    /**
     * 将 Component 转换为 MiniMessage 格式字符串
     * 
     * @param component Component
     * @return MiniMessage 格式字符串
     */
    public static String toMiniMessageString(Component component) {
        if (component == null) {
            return "";
        }
        return MINI_MESSAGE.serialize(component);
    }

    /**
     * 解析 MiniMessage 格式文本
     * 
     * @param text MiniMessage 格式文本
     * @return 解析后的 Component
     */
    public static Component parseMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE_WITH_GRADIENT.deserialize(text);
    }

    /**
     * 解析渐变色文本
     * 使用 MiniMessage 的渐变语法
     * 
     * @param text 包含渐变标签的文本
     * @return 解析后的文本
     */
    public static String parseGradient(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        try {
            Component component = MINI_MESSAGE_WITH_GRADIENT.deserialize(text);
            return serializeLegacy(component);
        } catch (Exception e) {
            return text;
        }
    }

    /**
     * 创建渐变色文本
     * 
     * @param text 文本内容
     * @param fromColor 起始颜色（十六进制，如 "FF0000"）
     * @param toColor 结束颜色（十六进制，如 "0000FF"）
     * @return 渐变色文本
     */
    public static String createGradient(String text, String fromColor, String toColor) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String gradientText = "<gradient:#" + fromColor + ":#" + toColor + ">" + text + "</gradient>";
        return parseGradient(gradientText);
    }

    /**
     * 创建多色渐变文本
     * 
     * @param text 文本内容
     * @param colors 颜色数组（十六进制）
     * @return 渐变色文本
     */
    public static String createMultiGradient(String text, String... colors) {
        if (text == null || text.isEmpty() || colors == null || colors.length < 2) {
            return text != null ? text : "";
        }

        StringBuilder gradientBuilder = new StringBuilder("<gradient:");
        for (int i = 0; i < colors.length; i++) {
            gradientBuilder.append("#").append(colors[i]);
            if (i < colors.length - 1) {
                gradientBuilder.append(":");
            }
        }
        gradientBuilder.append(">").append(text).append("</gradient>");

        return parseGradient(gradientBuilder.toString());
    }

    /**
     * 创建彩虹渐变文本
     * 
     * @param text 文本内容
     * @return 彩虹渐变文本
     */
    public static String createRainbow(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        String rainbowText = "<rainbow>" + text + "</rainbow>";
        return parseGradient(rainbowText);
    }

    /**
     * 解析十六进制颜色为 Component
     * 
     * @param text 包含 &#RRGGBB 格式的文本
     * @return 解析后的 Component
     */
    public static Component parseHexColors(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // 转换 &#RRGGBB 为 MiniMessage 格式
        String processed = HEX_COLOR_PATTERN.matcher(text).replaceAll("<color:#$1>");

        return MINI_MESSAGE.deserialize(processed);
    }

    /**
     * 检查文本是否包含颜色代码
     * 
     * @param text 文本
     * @return 是否包含颜色代码
     */
    public static boolean containsColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        return LEGACY_COLOR_PATTERN.matcher(text).find() ||
               HEX_COLOR_PATTERN.matcher(text).find() ||
               containsMiniMessageTags(text);
    }

    /**
     * 获取文本的纯文本长度（不含颜色代码）
     * 
     * @param text 文本
     * @return 纯文本长度
     */
    public static int getPlainTextLength(String text) {
        return stripColor(text).length();
    }

    /**
     * 将传统颜色代码转换为 MiniMessage 格式
     * 
     * @param text 传统格式文本
     * @return MiniMessage 格式文本
     */
    private static final String[] LEGACY_TO_MINI = {
            "<black>", "<dark_blue>", "<dark_green>", "<dark_aqua>",
            "<dark_red>", "<dark_purple>", "<gold>", "<gray>",
            "<dark_gray>", "<blue>", "<green>", "<aqua>",
            "<red>", "<light_purple>", "<yellow>", "<white>",
            "<obfuscated>", "<bold>", "<strikethrough>", "<underlined>",
            "<italic>", "<reset>"
    };

    public static String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher = LEGACY_COLOR_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            char code = Character.toLowerCase(matcher.group(1).charAt(0));
            int index;
            if (code >= '0' && code <= '9') {
                index = code - '0';
            } else if (code >= 'a' && code <= 'f') {
                index = code - 'a' + 10;
            } else if (code == 'k') {
                index = 16;
            } else if (code == 'l') {
                index = 17;
            } else if (code == 'm') {
                index = 18;
            } else if (code == 'n') {
                index = 19;
            } else if (code == 'o') {
                index = 20;
            } else {
                index = 21;
            }
            matcher.appendReplacement(result, LEGACY_TO_MINI[index]);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    // ==================== Chroma 彩虹色工具方法 ====================

    /**
     * 计算 Chroma 颜色
     * 通过 HSL 色轮循环实现动态渐变效果
     *
     * @param step 当前步骤（通常基于系统时间）
     * @return ARGB 颜色值
     */
    public static int chromaColor(long step) {
        float hue = (float) ((step * 5.0) % 360.0); // 每5步旋转1度，使用 double 避免长时间运行精度下降
        return hslToArgb(hue, 1.0f, 0.5f);
    }

    /**
     * HSL 转 ARGB
     *
     * @param hue        色相 (0-360)
     * @param saturation 饱和度 (0-1)
     * @param lightness  亮度 (0-1)
     * @return ARGB 颜色值
     */
    public static int hslToArgb(float hue, float saturation, float lightness) {
        float c = (1.0f - Math.abs(2.0f * lightness - 1.0f)) * saturation;
        float x = c * (1.0f - Math.abs((hue / 60.0f) % 2.0f - 1.0f));
        float m = lightness - c / 2.0f;

        float r, g, b;
        if (hue < 60) {
            r = c; g = x; b = 0;
        } else if (hue < 120) {
            r = x; g = c; b = 0;
        } else if (hue < 180) {
            r = 0; g = c; b = x;
        } else if (hue < 240) {
            r = 0; g = x; b = c;
        } else if (hue < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }

        int red = Math.round((r + m) * 255.0f);
        int green = Math.round((g + m) * 255.0f);
        int blue = Math.round((b + m) * 255.0f);

        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
