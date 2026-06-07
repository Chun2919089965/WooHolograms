package com.oolonghoo.holograms.animation.text;

import com.oolonghoo.holograms.animation.TextAnimation;

/**
 * 闪烁动画
 * 创建闪烁效果的文本动画
 *
 */
public class BlinkAnimation extends TextAnimation {

    /**
     * 构造函数
     */
    public BlinkAnimation() {
        super("blink", 10, 0);
    }

    @Override
    public String animate(String string, long step, String... args) {
        if (string == null || string.isEmpty()) {
            return string;
        }

        String[] frames = getPrecompiledFrames(string, args);
        if (frames == null || frames.length == 0) return string;
        int index = getCurrentStep(step, frames.length);
        return frames[index];
    }

    @Override
    protected String[] precompile(String text, String... args) {
        // 闪烁只有两帧：显示和隐藏
        return new String[]{text, ""};
    }
}
