package com.oolongho.holograms.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ColorUtil 全链路冒烟测试（归一化 → MiniMessage → Component/legacy 序列化）
 * 需要运行时 classpath 携带 adventure 相关 jar
 */
class ColorUtilFormatTest {

    @Test
    void plainTextFastPath() {
        assertEquals("普通文本", ColorUtil.formatLegacy("普通文本"));
    }

    @Test
    void legacyCodes() {
        String result = ColorUtil.formatLegacy("&a绿&l粗");
        assertTrue(result.contains("§a"), result);
        assertTrue(result.contains("§l"), result);
    }

    @Test
    void prefixHexToLegacyLongForm() {
        // formatLegacy 输出 §x 长格式（CraftChatMessage 兼容，序列化器输出小写十六进制）
        String result = ColorUtil.formatLegacy("&#FF0000红");
        assertTrue(result.toLowerCase(java.util.Locale.ROOT).contains("§x§f§f§0§0§0§0"), result);
    }

    @Test
    void dhGradientComponent() {
        // DH 渐变标签 → Component，逐字着色
        Component component = ColorUtil.format("<#00BFFF>&l林之川</#54FF9F>");
        assertNotNull(component);
        String legacy = ColorUtil.formatLegacy("<#00BFFF>林</#54FF9F>");
        // 首字符接近 #00BFFF，以 §x 长格式呈现
        assertTrue(legacy.contains("§x§0§0§B§F§F§F") || legacy.contains("§x§0§0§b§f§f"), legacy);
    }

    @Test
    void cmiGradientComponent() {
        Component component = ColorUtil.format("{#FF0000>}a{#0000FF<}");
        assertNotNull(component);
    }

    @Test
    void rainbow() {
        assertNotNull(ColorUtil.format("{rainbow}彩虹{/rainbow}"));
        assertNotNull(ColorUtil.format("<RAINBOW:3>rb</RAINBOW>"));
    }

    @Test
    void miniMessageNativePassthrough() {
        Component component = ColorUtil.format("<color:#FF0000>红</color>");
        assertNotNull(component);
    }

    @Test
    void componentTextPreserved() {
        // 纯文本内容不被颜色处理破坏
        Component component = ColorUtil.format("&a林之川");
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(component);
        assertEquals("林之川", plain);
    }

    @Test
    void ampersandEscape() {
        String result = ColorUtil.formatLegacy("a && b");
        assertTrue(result.contains("&"), result);
        assertTrue(!result.contains("§"), result);
    }

    @Test
    void idempotentLegacyOutput() {
        String once = ColorUtil.formatLegacy("<#00BFFF>林</#54FF9F>");
        assertEquals(once, ColorUtil.formatLegacy(once));
    }

    @Test
    void emptyAndNull() {
        assertEquals("", ColorUtil.formatLegacy(null));
        assertEquals("", ColorUtil.formatLegacy(""));
        assertEquals(Component.empty(), ColorUtil.format(null));
    }
}
