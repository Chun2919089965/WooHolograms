package com.oolonghoo.holograms.nms.versions.renderer;

import com.oolonghoo.holograms.hologram.*;
import com.oolonghoo.holograms.nms.util.DecentPosition;
import com.oolonghoo.holograms.nms.versions.EntityIdGenerator;
import com.oolonghoo.holograms.nms.versions.EntityMetadataBuilder;
import com.oolonghoo.holograms.nms.versions.EntityPacketsBuilder;
import net.minecraft.network.syncher.SynchedEntityData;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 页面级文本渲染器
 * 将连续的 TEXT 行合并为单个 TextDisplay 实体渲染
 * 解决背景宽度不一致、文本换行重叠和对齐问题
 */
public class PageTextRenderer {

    /** 一个文本行组：连续的TEXT行 */
    private static class TextGroup {
        final int frontEntityId;
        final int backEntityId;
        final List<HologramLine> lines;

        TextGroup(int frontEntityId, int backEntityId, List<HologramLine> lines) {
            this.frontEntityId = frontEntityId;
            this.backEntityId = backEntityId;
            this.lines = lines;
        }
    }

    private final HologramPage page;
    private final EntityIdGenerator entityIdGenerator;
    private List<TextGroup> textGroups = new ArrayList<>();
    private boolean destroyed = false;

    private float currentYaw = 0.0f;
    private float currentPitch = 0.0f;
    private boolean currentDoubleSided = false;

    /** 每个玩家每组的文本缓存，用于增量更新 */
    private final Map<UUID, Map<Integer, String>> lastTextPerPlayerGroup = new ConcurrentHashMap<>();

    public PageTextRenderer(HologramPage page, EntityIdGenerator entityIdGenerator) {
        this.page = page;
        this.entityIdGenerator = entityIdGenerator;
        rebuildGroups();
    }

    /**
     * 重建文本行组
     * 遍历页面所有行，将连续的TEXT行分为一组
     */
    public void rebuildGroups() {
        textGroups.clear();
        lastTextPerPlayerGroup.clear();

        List<HologramLine> currentGroupLines = new ArrayList<>();
        for (HologramLine line : page.getLines()) {
            if (line.getType() == HologramType.TEXT) {
                currentGroupLines.add(line);
            } else {
                if (!currentGroupLines.isEmpty()) {
                    textGroups.add(new TextGroup(
                            entityIdGenerator.getFreeEntityId(),
                            entityIdGenerator.getFreeEntityId(),
                            new ArrayList<>(currentGroupLines)
                    ));
                    currentGroupLines.clear();
                }
            }
        }
        // 处理末尾的TEXT行组
        if (!currentGroupLines.isEmpty()) {
            textGroups.add(new TextGroup(
                    entityIdGenerator.getFreeEntityId(),
                    entityIdGenerator.getFreeEntityId(),
                    new ArrayList<>(currentGroupLines)
            ));
        }
    }

    /**
     * 渲染所有文本行组给指定玩家
     */
    public void render(Player player, Location baseLocation) {
        if (destroyed) return;

        Hologram hologram = page.getParent();
        if (hologram == null) return;

        Billboard billboard = hologram.getBillboard();
        boolean doubleSided = hologram.isDoubleSided();
        float hologramFacing = hologram.getFacing();
        TextAlignment alignment = hologram.getAlignment();
        int backgroundColor = (hologram.getBackgroundAlpha() << 24) | hologram.getBackgroundColor();
        int lineWidth = hologram.getLineWidth();

        float yaw, pitch;
        if (billboard == Billboard.FIXED_ANGLE) {
            yaw = hologramFacing;
            pitch = 0;
        } else {
            yaw = baseLocation.getYaw();
            pitch = baseLocation.getPitch();
        }
        this.currentYaw = yaw;
        this.currentPitch = pitch;
        this.currentDoubleSided = doubleSided;

        Map<Integer, String> playerGroupTexts = new HashMap<>();

        for (int gi = 0; gi < textGroups.size(); gi++) {
            TextGroup group = textGroups.get(gi);

            // 收集该组所有TEXT行的文本
            List<String> textLines = new ArrayList<>();
            for (HologramLine line : group.lines) {
                textLines.add(line.getDisplayText(player));
            }
            String textKey = String.join("\n", textLines);
            playerGroupTexts.put(gi, textKey);

            // 使用组内第一行的位置
            Location groupLocation = group.lines.get(0).getLocation();
            if (groupLocation == null) continue;

            EntityMetadataBuilder metadataBuilder = EntityMetadataBuilder.create()
                    .withInvisible()
                    .withNoGravity()
                    .withTextDisplayText(textLines)
                    .withBillboard(billboard)
                    .withTextAlignment(alignment)
                    .withTextBackgroundColor(backgroundColor)
                    .withTextLineWidth(lineWidth);

            List<SynchedEntityData.DataItem<?>> metadata = metadataBuilder.toWatchableObjects();

            EntityPacketsBuilder packetsBuilder = EntityPacketsBuilder.create()
                    .withSpawnEntity(group.frontEntityId, org.bukkit.entity.EntityType.TEXT_DISPLAY,
                            new DecentPosition(groupLocation.getX(), groupLocation.getY(), groupLocation.getZ()),
                            yaw, pitch)
                    .withEntityMetadata(group.frontEntityId, metadata);

            if (doubleSided) {
                packetsBuilder.withSpawnEntity(group.backEntityId, org.bukkit.entity.EntityType.TEXT_DISPLAY,
                                new DecentPosition(groupLocation.getX(), groupLocation.getY(), groupLocation.getZ()),
                                yaw + 180.0f, pitch)
                        .withEntityMetadata(group.backEntityId, metadata);
            }

            packetsBuilder.sendTo(player);
        }

        lastTextPerPlayerGroup.put(player.getUniqueId(), playerGroupTexts);
    }

    /**
     * 更新所有文本行组的文本（增量更新，仅发送变化的组）
     */
    public void updateText(Player player) {
        if (destroyed) return;

        Hologram hologram = page.getParent();
        if (hologram == null) return;

        Billboard billboard = hologram.getBillboard();
        boolean doubleSided = hologram.isDoubleSided();
        TextAlignment alignment = hologram.getAlignment();
        int backgroundColor = (hologram.getBackgroundAlpha() << 24) | hologram.getBackgroundColor();
        int lineWidth = hologram.getLineWidth();

        Map<Integer, String> playerGroupTexts = lastTextPerPlayerGroup.computeIfAbsent(
                player.getUniqueId(), k -> new HashMap<>());

        for (int gi = 0; gi < textGroups.size(); gi++) {
            TextGroup group = textGroups.get(gi);

            // 收集文本行
            List<String> textLines = new ArrayList<>();
            for (HologramLine line : group.lines) {
                textLines.add(line.getDisplayText(player));
            }
            String textKey = String.join("\n", textLines);

            // 检查文本是否变化
            String lastText = playerGroupTexts.get(gi);
            if (textKey.equals(lastText)) continue;
            playerGroupTexts.put(gi, textKey);

            EntityMetadataBuilder metadataBuilder = EntityMetadataBuilder.create()
                    .withInvisible()
                    .withNoGravity()
                    .withTextDisplayText(textLines)
                    .withBillboard(billboard)
                    .withTextAlignment(alignment)
                    .withTextBackgroundColor(backgroundColor)
                    .withTextLineWidth(lineWidth);

            List<SynchedEntityData.DataItem<?>> metadata = metadataBuilder.toWatchableObjects();

            EntityPacketsBuilder packetsBuilder = EntityPacketsBuilder.create()
                    .withEntityMetadata(group.frontEntityId, metadata);

            if (doubleSided) {
                packetsBuilder.withEntityMetadata(group.backEntityId, metadata);
            }

            packetsBuilder.sendTo(player);
        }
    }

    /**
     * 销毁所有文本行组实体（指定玩家）
     */
    public void destroy(Player player) {
        EntityPacketsBuilder packetsBuilder = EntityPacketsBuilder.create();
        for (TextGroup group : textGroups) {
            packetsBuilder.withRemoveEntity(group.frontEntityId);
            packetsBuilder.withRemoveEntity(group.backEntityId);
        }
        packetsBuilder.sendTo(player);
        lastTextPerPlayerGroup.remove(player.getUniqueId());
    }

    /**
     * 销毁所有状态（不发送包，需在调用前先对所有viewer发送destroy）
     */
    public void destroyAll() {
        destroyed = true;
        lastTextPerPlayerGroup.clear();
    }

    /**
     * 传送所有文本行组实体
     */
    public void teleport(Player player) {
        if (destroyed) return;

        for (TextGroup group : textGroups) {
            Location groupLocation = group.lines.get(0).getLocation();
            if (groupLocation == null) continue;

            EntityPacketsBuilder packetsBuilder = EntityPacketsBuilder.create()
                    .withTeleportEntity(group.frontEntityId, new DecentPosition(
                            groupLocation.getX(), groupLocation.getY(), groupLocation.getZ(),
                            currentYaw, currentPitch));

            if (currentDoubleSided) {
                packetsBuilder.withTeleportEntity(group.backEntityId, new DecentPosition(
                        groupLocation.getX(), groupLocation.getY(), groupLocation.getZ(),
                        currentYaw + 180.0f, currentPitch));
            }

            packetsBuilder.sendTo(player);
        }
    }

    /**
     * 获取所有实体ID（用于注册和点击检测）
     */
    public List<Integer> getEntityIds() {
        List<Integer> ids = new ArrayList<>();
        for (TextGroup group : textGroups) {
            ids.add(group.frontEntityId);
            ids.add(group.backEntityId);
        }
        return ids;
    }

    /**
     * 根据实体ID查找对应的行（用于点击检测）
     */
    public HologramLine getLineByEntityId(int entityId) {
        for (TextGroup group : textGroups) {
            if (group.frontEntityId == entityId || group.backEntityId == entityId) {
                return group.lines.isEmpty() ? null : group.lines.get(0);
            }
        }
        return null;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    public void reset() {
        destroyed = false;
        currentYaw = 0.0f;
        currentPitch = 0.0f;
        currentDoubleSided = false;
        lastTextPerPlayerGroup.clear();
    }
}
