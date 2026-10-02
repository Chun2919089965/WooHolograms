package com.oolongho.holograms.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SyntaxNormalizer 语法归一化测试矩阵
 * 覆盖各语法族、混写、幂等性、容错与转义
 */
class SyntaxNormalizerTest {

    // ==================== 渐变 ====================

    @Test
    void iridiumGradient() {
        // 用户实际遇到的 DH 渐变写法
        assertEquals("<gradient:#00BFFF:#54FF9F>&l✦ 林 之 川 ✦</gradient>",
                SyntaxNormalizer.normalize("<#00BFFF>&l✦ 林 之 川 ✦</#54FF9F>"));
    }

    @Test
    void iridiumGradientCurlyBraces() {
        // DH 兼容：{} 括号风格亦认
        assertEquals("<gradient:#FF0000:#00FF00>abc</gradient>",
                SyntaxNormalizer.normalize("{#FF0000}abc{/#00FF00}"));
    }

    @Test
    void cmiGradient() {
        assertEquals("<gradient:#FF0000:#0000FF>hello</gradient>",
                SyntaxNormalizer.normalize("{#FF0000>}hello{#0000FF<}"));
    }

    @Test
    void sequentialGradients() {
        assertEquals("<gradient:#FF0000:#00FF00>a</gradient><gradient:#0000FF:#FF00FF>b</gradient>",
                SyntaxNormalizer.normalize("<#FF0000>a</#00FF00><#0000FF>b</#FF00FF>"));
    }

    @Test
    void unclosedGradientLeftAsSolidColorTag() {
        // 未闭合的 <#hex> 是 MiniMessage 原生纯色标签，保留不丢字
        String result = SyntaxNormalizer.normalize("<#FF0000>plain text");
        assertEquals("<#FF0000>plain text", result);
    }

    // ==================== 彩虹 ====================

    @Test
    void cmiRainbow() {
        assertEquals("<rainbow>text</rainbow>",
                SyntaxNormalizer.normalize("{rainbow}text{/rainbow}"));
    }

    @Test
    void iridiumRainbowWithSpeed() {
        assertEquals("<rainbow:5>text</rainbow>",
                SyntaxNormalizer.normalize("<RAINBOW:5>text</RAINBOW>"));
    }

    @Test
    void iridiumRainbowWithoutSpeed() {
        assertEquals("<rainbow>text</rainbow>",
                SyntaxNormalizer.normalize("<RAINBOW>text</RAINBOW>"));
    }

    // ==================== 单色 ====================

    @Test
    void curlyHex() {
        assertEquals("<color:#FF5555>text", SyntaxNormalizer.normalize("{#FF5555}text"));
    }

    @Test
    void bracketHex() {
        assertEquals("<color:#55FF55>text", SyntaxNormalizer.normalize("[#55FF55]text"));
    }

    @Test
    void prefixHex() {
        assertEquals("<color:#AA00AA>text", SyntaxNormalizer.normalize("&#AA00AAtext"));
        assertEquals("<color:#AA00AA>text", SyntaxNormalizer.normalize("§#AA00AAtext"));
    }

    @Test
    void sectionLongHex() {
        assertEquals("<color:#FF0000>red",
                SyntaxNormalizer.normalize("§x§F§F§0§0§0§0red"));
    }

    // ==================== 转义 ====================

    @Test
    void ampersandEscape() {
        // && 转为字面 &，不被当作颜色代码前缀
        assertEquals("a & b &a c", SyntaxNormalizer.normalize("a && b &&a c"));
    }

    // ==================== 幂等与不干预 ====================

    @Test
    void idempotent() {
        String[] inputs = {
                "<#00BFFF>&l林之川</#54FF9F>",
                "{#FF0000>}text{#0000FF<}",
                "{rainbow}text{/rainbow}",
                "<RAINBOW:3>text</RAINBOW>",
                "{#FF5555}text",
                "§#ABCDEFtext",
                "plain text"
        };
        for (String input : inputs) {
            String once = SyntaxNormalizer.normalize(input);
            assertEquals(once, SyntaxNormalizer.normalize(once), "not idempotent: " + input);
        }
    }

    @Test
    void plainTextUntouched() {
        assertEquals("普通文本 abc 123", SyntaxNormalizer.normalize("普通文本 abc 123"));
        // 裸 # 后非六位十六进制不动
        assertEquals("#fff #12345 short", SyntaxNormalizer.normalize("#fff #12345 short"));
    }

    @Test
    void miniMessageCanonicalTagsUntouched() {
        String canonical = "<gradient:#FF0000:#00FF00>text</gradient><rainbow:2>rb</rainbow>";
        assertEquals(canonical, SyntaxNormalizer.normalize(canonical));
    }

    @Test
    void nullAndEmptySafe() {
        assertEquals("", SyntaxNormalizer.normalize(""));
        assertEquals("", SyntaxNormalizer.normalize(null));
    }

    // ==================== 混写 ====================

    @Test
    void mixedSyntax() {
        String result = SyntaxNormalizer.normalize("<#00BFFF>a</#54FF9F> {#FFFF00}b &#FF00FFc");
        assertTrue(result.contains("<gradient:#00BFFF:#54FF9F>a</gradient>"), result);
        assertTrue(result.contains("<color:#FFFF00>b"), result);
        assertTrue(result.contains("<color:#FF00FF>c"), result);
    }
}
