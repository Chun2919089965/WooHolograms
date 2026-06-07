package com.oolonghoo.holograms.animation;

import com.oolonghoo.holograms.util.ColorUtil;
import com.oolonghoo.holograms.util.TextUtil;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文本动画抽象类
 * 参考 DecentHolograms 的 TextAnimation 实现
 * 用于处理文本相关的动画效果
 *
 */
public abstract class TextAnimation extends Animation {

    /**
     * 预编译帧缓存：key 为 text+args 组合，value 为帧数组
     */
    private final Map<String, String[]> precompiledFrames = new ConcurrentHashMap<>();

    /**
     * 构造函数
     *
     * @param name 动画名称
     * @param speed 动画速度
     * @param pause 暂停时间
     */
    protected TextAnimation(String name, int speed, int pause) {
        super(name, speed, pause);
    }

    /**
     * 构造函数（带别名）
     *
     * @param name 动画名称
     * @param speed 动画速度
     * @param pause 暂停时间
     * @param aliases 别名数组
     */
    protected TextAnimation(String name, int speed, int pause, String... aliases) {
        super(name, speed, pause, aliases);
    }

    /**
     * 执行动画效果
     *
     * @param string 原始文本
     * @param step 当前步骤
     * @param args 动画参数
     * @return 动画处理后的文本
     */
    public abstract String animate(String string, long step, String... args);

    /**
     * 生成缓存键
     * 同一动画实例可被不同标签复用，相同文本搭配不同 args 需要独立缓存
     *
     * @param text 文本
     * @param args 动画参数
     * @return 缓存键
     */
    private String cacheKey(String text, String... args) {
        if (args == null || args.length == 0) return text;
        return text + "|" + String.join(",", args);
    }

    /**
     * 预编译动画帧
     * 子类实现此方法，将动画所有可能的帧一次性计算并返回
     *
     * @param text 原始文本
     * @param args 动画参数
     * @return 预编译的帧数组
     */
    protected abstract String[] precompile(String text, String... args);

    /**
     * 获取预编译帧
     * 首次调用时触发预编译，后续直接从缓存获取
     *
     * @param text 原始文本
     * @param args 动画参数
     * @return 预编译的帧数组
     */
    public String[] getPrecompiledFrames(String text, String... args) {
        String key = cacheKey(text, args);
        return precompiledFrames.computeIfAbsent(key, k -> precompile(text, args));
    }

    /**
     * 清除预编译缓存
     */
    public void clearPrecompiled() {
        precompiledFrames.clear();
    }

    /**
     * 移除文本中的特殊颜色代码
     * 
     * @param string 原始文本
     * @return 清理后的文本
     */
    protected String stripSpecialColors(String string) {
        if (string == null) {
            return "";
        }
        return TextUtil.stripColor(string);
    }

    /**
     * 获取文本的实际长度（不包含颜色代码）
     * 
     * @param string 文本
     * @return 实际长度
     */
    protected int getTextLength(String string) {
        return TextUtil.getLength(string);
    }

    /**
     * 处理颜色代码
     * 
     * @param string 文本
     * @return 处理后的文本
     */
    protected String colorize(String string) {
        return ColorUtil.colorize(string);
    }
}
