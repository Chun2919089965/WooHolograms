package com.oolonghoo.holograms.nms.versions;

import com.oolonghoo.holograms.hologram.Brightness;
import com.oolonghoo.holograms.hologram.TextAlignment;
import com.oolonghoo.holograms.hologram.Billboard;
import net.minecraft.core.Rotations;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 实体元数据构建器
 * 用于构建实体的元数据
 *
 * 
 * 
 */
public class EntityMetadataBuilder {

    private final List<SynchedEntityData.DataItem<?>> watchableObjects;

    private EntityMetadataBuilder() {
        this.watchableObjects = new ArrayList<>();
    }

    /**
     * 转换为可监视对象列表
     *
     * @return 可监视对象列表
     */
    public List<SynchedEntityData.DataItem<?>> toWatchableObjects() {
        return watchableObjects;
    }

    /**
     * 设置实体为不可见
     *
     * @return this
     */
    public EntityMetadataBuilder withInvisible() {
        /*
         * 实体属性：
         * 0x01 - 着火
         * 0x02 - 潜行
         * 0x08 - 疾跑
         * 0x10 - 游泳
         * 0x20 - 不可见
         * 0x40 - 发光效果
         * 0x80 - 鞘翅飞行
         */
        watchableObjects.add(EntityMetadataType.ENTITY_PROPERTIES.construct((byte) 0x20));
        return this;
    }

    /**
     * 设置盔甲架属性
     *
     * @param small  是否小型
     * @param marker 是否为标记（无碰撞箱）
     * @return this
     */
    public EntityMetadataBuilder withArmorStandProperties(boolean small, boolean marker) {
        /*
         * 盔甲架属性：
         * 0x01 - 小型
         * 0x02 - 未使用
         * 0x04 - 有手臂
         * 0x08 - 移除底板
         * 0x10 - 标记（零碰撞箱）
         */
        byte data = 0x08; // 始终移除底板
        if (small) {
            data |= 0x01;
        }
        if (marker) {
            data |= 0x10;
        }

        watchableObjects.add(EntityMetadataType.ARMOR_STAND_PROPERTIES.construct(data));
        return this;
    }

    /**
     * 设置物品
     *
     * @param itemStack 物品
     * @return this
     */
    public EntityMetadataBuilder withItemStack(ItemStack itemStack) {
        watchableObjects.add(EntityMetadataType.ITEM_STACK.construct(CraftItemStack.asNMSCopy(itemStack)));
        return this;
    }

    /**
     * 设置实体为静音
     *
     * @return this
     */
    public EntityMetadataBuilder withSilent() {
        watchableObjects.add(EntityMetadataType.ENTITY_SILENT.construct(true));
        return this;
    }

    /**
     * 设置实体无重力
     *
     * @return this
     */
    public EntityMetadataBuilder withNoGravity() {
        watchableObjects.add(EntityMetadataType.ENTITY_HAS_NO_GRAVITY.construct(true));
        return this;
    }

    /**
     * 设置盔甲架头部旋转
     * 用于模拟 Billboard 模式和阴影方向
     *
     * @param x X 轴旋转（弧度）
     * @param y Y 轴旋转（弧度）
     * @param z Z 轴旋转（弧度）
     * @return this
     */
    public EntityMetadataBuilder withHeadRotation(float x, float y, float z) {
        watchableObjects.add(EntityMetadataType.ARMOR_STAND_HEAD_POSE.construct(new Rotations(x, y, z)));
        return this;
    }

    /**
     * 设置亮度覆盖
     * 注意：盔甲架本身不支持亮度覆盖，此方法用于未来扩展
     * 当前版本通过发光效果模拟亮度
     *
     * @param brightness 亮度对象
     * @return this
     */
    public EntityMetadataBuilder withBrightness(Brightness brightness) {
        if (brightness == null) {
            return this;
        }

        if (brightness.getBlockLight() >= 15 || brightness.getSkyLight() >= 15) {
            watchableObjects.add(EntityMetadataType.ENTITY_PROPERTIES.construct((byte) 0x60));
        }

        return this;
    }

    /**
     * 设置 Display Entity 的亮度覆盖
     * 使用 Display.Brightness 类设置亮度
     *
     * @param brightness 亮度对象
     * @return this
     */
    public EntityMetadataBuilder withDisplayBrightness(Brightness brightness) {
        if (brightness == null || brightness.isDefault()) {
            return this;
        }
        int brightnessInt = brightness.getBlockLight() << 4 | brightness.getSkyLight() << 20;
        watchableObjects.add(EntityMetadataType.DISPLAY_BRIGHTNESS.construct(brightnessInt));
        return this;
    }

    /**
     * 设置 Display Entity 的 Billboard 模式
     *
     * @param billboard Billboard 模式
     * @return this
     */
    public EntityMetadataBuilder withBillboard(Billboard billboard) {
        if (billboard == null) {
            billboard = Billboard.CENTER;
        }
        byte billboardValue;
        switch (billboard) {
            case FIXED_ANGLE:
                billboardValue = 0;
                break;
            case VERTICAL:
                billboardValue = 1;
                break;
            case HORIZONTAL:
                billboardValue = 2;
                break;
            case CENTER:
            default:
                billboardValue = 3;
                break;
        }
        watchableObjects.add(EntityMetadataType.DISPLAY_BILLBOARD.construct(billboardValue));
        return this;
    }



    /**
     * 设置 TextDisplay Entity 的文本内容
     *
     * @param text 文本内容
     * @return this
     */
    public EntityMetadataBuilder withTextDisplayText(String text) {
        Component component = CraftChatMessage.fromStringOrNull(text);
        watchableObjects.add(EntityMetadataType.TEXT_DISPLAY_TEXT.construct(component != null ? component : Component.empty()));
        return this;
    }

    /**
     * 设置 TextDisplay Entity 的文本对齐方式
     *
     * @param alignment 文本对齐方式
     * @return this
     */
    public EntityMetadataBuilder withTextAlignment(TextAlignment alignment) {
        if (alignment == null) {
            alignment = TextAlignment.LEFT;
        }
        byte styleFlags;
        switch (alignment) {
            case LEFT:
                styleFlags = 0x08;
                break;
            case RIGHT:
                styleFlags = 0x10;
                break;
            case CENTER:
            default:
                styleFlags = 0x00;
                break;
        }
        watchableObjects.add(EntityMetadataType.TEXT_DISPLAY_STYLE_FLAGS.construct(styleFlags));
        return this;
    }

    /**
     * 设置 TextDisplay Entity 的线宽
     *
     * @param lineWidth 线宽（默认 200）
     * @return this
     */
    public EntityMetadataBuilder withTextLineWidth(int lineWidth) {
        watchableObjects.add(EntityMetadataType.TEXT_DISPLAY_LINE_WIDTH.construct(lineWidth));
        return this;
    }

    /**
     * 设置 TextDisplay Entity 的背景颜色
     *
     * @param argb ARGB 颜色值（0xAA000000 为默认透明黑色）
     * @return this
     */
    public EntityMetadataBuilder withTextBackgroundColor(int argb) {
        watchableObjects.add(EntityMetadataType.TEXT_DISPLAY_BACKGROUND_COLOR.construct(argb));
        return this;
    }

    /**
     * 设置 TextDisplay Entity 的文本不透明度
     *
     * @param opacity 不透明度（0-255，-1 表示默认）
     * @return this
     */
    public EntityMetadataBuilder withTextOpacity(byte opacity) {
        watchableObjects.add(EntityMetadataType.TEXT_DISPLAY_OPACITY.construct(opacity));
        return this;
    }

    /**
     * 创建一个新的构建器
     *
     * @return 新的构建器实例
     */
    public static EntityMetadataBuilder create() {
        return new EntityMetadataBuilder();
    }
}
