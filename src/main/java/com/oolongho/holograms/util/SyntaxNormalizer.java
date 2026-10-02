package com.oolongho.holograms.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 颜色语法归一化器
 * 将各类第三方颜色语法统一翻译为 MiniMessage 规范标签。
 * 纯字符串变换，无任何 MC/Adventure 依赖，可独立单元测试。
 *
 * <p>支持的输入语法：</p>
 * <ul>
 *   <li>Iridium/DH 渐变：<code>&lt;#hex&gt;text&lt;/#hex&gt;</code>（括号 <code>{}</code> 风格亦认，与 DH 行为一致）</li>
 *   <li>CMI 渐变：<code>{#hex&gt;}text{#hex&lt;}</code></li>
 *   <li>CMI 彩虹：<code>{rainbow}text{/rainbow}</code></li>
 *   <li>Iridium/DH 彩虹：<code>&lt;RAINBOW&gt;text&lt;/RAINBOW&gt;</code>（速度参数可选）</li>
 *   <li>括号族单色：<code>{#hex}</code>、<code>[#hex]</code></li>
 *   <li>前缀族单色：<code>&amp;#hex</code>、<code>§#hex</code></li>
 *   <li>§x 长十六进制（客户端内部格式，从别处复制得来）</li>
 *   <li><code>&amp;&amp;</code> 转义为字面 <code>&amp;</code></li>
 * </ul>
 *
 * <p>设计约束：</p>
 * <ul>
 *   <li><b>幂等</b>：产物全部是 <code>&lt;gradient&gt;</code>/<code>&lt;color&gt;</code>/<code>&lt;rainbow&gt;</code>
 *       规范标签，二次归一化不改变结果（动画每 tick 重跑必须安全）</li>
 *   <li><b>容错</b>：未闭合的渐变标签保留为 <code>&lt;#hex&gt;</code>（MiniMessage 原生纯色标签），不丢字</li>
 *   <li><b>顺序敏感</b>：先渐变（消费区间文本），后彩虹，最后单色（点匹配），避免误替换</li>
 * </ul>
 */
public final class SyntaxNormalizer {

    private SyntaxNormalizer() {}

    /** && 转义占位符（私用区字符，正常文本不会出现） */
    private static final String AMP_SENTINEL = "\uE000";

    /** Iridium/DH 式渐变：<#hex>text</#hex>，起始/闭合括号风格可混用（与 DH GRADIENT_PATTERN 对齐） */
    private static final Pattern IRIDIUM_GRADIENT_PATTERN = Pattern.compile(
            "[<{]#([0-9a-fA-F]{6})[}>](((?![<{]#[0-9a-fA-F]{6}[}>]).)*)[<{]/#([0-9a-fA-F]{6})[}>]");

    /** CMI 式渐变：{#hex>}text{#hex<} */
    private static final Pattern CMI_GRADIENT_PATTERN = Pattern.compile(
            "\\{#([0-9a-fA-F]{6})>\\}(.*?)\\{#([0-9a-fA-F]{6})<\\}", Pattern.DOTALL);

    /** CMI 式彩虹：{rainbow}text{/rainbow} */
    private static final Pattern CMI_RAINBOW_PATTERN = Pattern.compile(
            "\\{rainbow}(.*?)\\{/rainbow}", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** Iridium/DH 式彩虹：<RAINBOW>text</RAINBOW>，速度参数可选（作为 rainbow 相位透传） */
    private static final Pattern IRIDIUM_RAINBOW_PATTERN = Pattern.compile(
            "<RAINBOW:([0-9]{1,3})>(.*?)</RAINBOW>|<RAINBOW>(.*?)</RAINBOW>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 括号族单色：{#hex}、[#hex] */
    private static final Pattern BRACED_HEX_PATTERN = Pattern.compile("[{\\[]#([0-9a-fA-F]{6})[}\\]]");

    /** §x§R§R§G§G§B§B 长十六进制（客户端内部格式） */
    private static final Pattern SECTION_LONG_HEX_PATTERN = Pattern.compile(
            "§x§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])"
                    + "§([0-9a-fA-F])§([0-9a-fA-F])§([0-9a-fA-F])");

    /** &/§ 前缀紧凑十六进制：&#hex、§#hex */
    private static final Pattern PREFIX_HEX_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");

    /**
     * 归一化：任意第三方颜色语法 → MiniMessage 规范标签
     *
     * @param text 原始文本
     * @return 归一化后的文本（幂等）
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text == null ? "" : text;
        }

        // 快速跳过：无任何语法特征（#、§x 长格式、&&、rainbow 字样）时原样返回
        if (text.indexOf('#') < 0 && text.indexOf('§') < 0 && !text.contains("&&")
                && !containsRainbowToken(text)) {
            return text;
        }

        // 1. && 转义先收起，避免后续 & 处理误伤
        text = text.replace("&&", AMP_SENTINEL);

        // 2. 渐变（Iridium 与 CMI 括号风格不同，无冲突，先后皆可）
        text = IRIDIUM_GRADIENT_PATTERN.matcher(text)
                .replaceAll("<gradient:#$1:#$4>$2</gradient>");
        text = CMI_GRADIENT_PATTERN.matcher(text)
                .replaceAll("<gradient:#$1:#$3>$2</gradient>");

        // 3. 彩虹
        text = CMI_RAINBOW_PATTERN.matcher(text)
                .replaceAll("<rainbow>$1</rainbow>");
        text = rewriteIridiumRainbows(text);

        // 4. 单色（点匹配，在渐变消费完区间之后执行）
        text = BRACED_HEX_PATTERN.matcher(text)
                .replaceAll("<color:#$1>");
        text = SECTION_LONG_HEX_PATTERN.matcher(text).replaceAll(match ->
                "<color:#" + match.group(1) + match.group(2) + match.group(3)
                        + match.group(4) + match.group(5) + match.group(6) + ">");
        text = PREFIX_HEX_PATTERN.matcher(text)
                .replaceAll("<color:#$1>");

        // 5. 恢复字面 &
        return text.replace(AMP_SENTINEL, "&");
    }

    /**
     * 重写 Iridium/DH 彩虹标签
     * 带速度参数的形式将参数作为 MiniMessage rainbow 相位透传（语义近似，视觉接近）
     */
    private static String rewriteIridiumRainbows(String text) {
        Matcher matcher = IRIDIUM_RAINBOW_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder(text.length());
        while (matcher.find()) {
            String body;
            String replacement;
            if (matcher.group(1) != null) {
                // <RAINBOW:n>body</RAINBOW>
                body = matcher.group(2);
                replacement = "<rainbow:" + matcher.group(1) + ">" + Matcher.quoteReplacement(body) + "</rainbow>";
            } else {
                // <RAINBOW>body</RAINBOW>
                body = matcher.group(3);
                replacement = "<rainbow>" + Matcher.quoteReplacement(body) + "</rainbow>";
            }
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 是否包含 rainbow 字样（忽略大小写），用于快路径判断
     */
    private static boolean containsRainbowToken(String text) {
        return text.toLowerCase(java.util.Locale.ROOT).contains("rainbow");
    }
}
