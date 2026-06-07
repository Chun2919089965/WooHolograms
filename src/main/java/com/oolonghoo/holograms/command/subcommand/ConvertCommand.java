package com.oolonghoo.holograms.command.subcommand;

import com.oolonghoo.holograms.WooHolograms;
import com.oolonghoo.holograms.command.Subcommand;
import com.oolonghoo.holograms.hologram.Hologram;
import com.oolonghoo.holograms.hologram.HologramPage;
import com.oolonghoo.holograms.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据转换命令
 * 支持从 HolographicDisplays 插件导入全息图数据
 * /wh convert holographicdisplays 或 /wh convert hd
 *
 */
public class ConvertCommand extends Subcommand {

    private final WooHolograms plugin;

    public ConvertCommand(WooHolograms plugin) {
        super("convert", "从其他插件导入全息图数据", "/wh convert <holographicdisplays|hd>", "wooholograms.convert", Arrays.asList("cv"));
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.usage")));
            return true;
        }

        String source = args[0].toLowerCase();
        switch (source) {
            case "holographicdisplays", "hd" -> convertHolographicDisplays(sender);
            default -> sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.unknown-source", "source", args[0])));
        }
        return true;
    }

    /**
     * 从 HolographicDisplays 2.x 导入全息图数据
     * HD 2.x 的数据存储在 plugins/HolographicDisplays/ 目录下的 YAML 文件中
     */
    private void convertHolographicDisplays(CommandSender sender) {
        File pluginsDir = plugin.getDataFolder().getParentFile();
        File hdDir = new File(pluginsDir, "HolographicDisplays");

        if (!hdDir.exists() || !hdDir.isDirectory()) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.hd-not-found")));
            return;
        }

        // 检查是否为 HD 3.x（数据库存储）
        File databaseFile = new File(hdDir, "database.db");
        if (databaseFile.exists()) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.hd-v3-unsupported")));
            return;
        }

        // 扫描所有 .yml 文件
        File[] ymlFiles = hdDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (ymlFiles == null || ymlFiles.length == 0) {
            sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.hd-no-data")));
            return;
        }

        int totalFiles = ymlFiles.length;
        int imported = 0;
        int skipped = 0;
        int failed = 0;

        sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.hd-start", "count", String.valueOf(totalFiles))));

        for (File ymlFile : ymlFiles) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(ymlFile);

            for (String holoName : config.getKeys(false)) {
                // 检查名称合法性
                if (!plugin.getHologramManager().isValidName(holoName)) {
                    plugin.getLogger().warning("跳过非法名称的全息图: " + holoName);
                    skipped++;
                    continue;
                }

                // 检查是否已存在
                if (plugin.getHologramManager().containsHologram(holoName)) {
                    plugin.getLogger().warning("跳过已存在的全息图: " + holoName);
                    skipped++;
                    continue;
                }

                // 解析位置
                String locationStr = config.getString(holoName + ".location");
                Location location = parseHdLocation(locationStr);
                if (location == null) {
                    plugin.getLogger().warning("跳过位置无效的全息图: " + holoName + " (location: " + locationStr + ")");
                    failed++;
                    continue;
                }

                // 解析行内容
                List<String> lines = config.getStringList(holoName + ".lines");
                if (lines.isEmpty()) {
                    plugin.getLogger().warning("跳过无内容的全息图: " + holoName);
                    skipped++;
                    continue;
                }

                // 创建全息图
                Hologram hologram = plugin.getHologramManager().createHologram(holoName, location);
                if (hologram == null) {
                    failed++;
                    continue;
                }

                // 添加行内容
                HologramPage page = hologram.getPage(0);
                if (page != null) {
                    for (String line : lines) {
                        // HD 的 #ICON: 行格式与 WooHolograms 兼容
                        page.addLine(line);
                    }
                }

                imported++;
            }
        }

        // 输出统计结果
        sender.sendMessage(ColorUtil.colorize(plugin.getMessages().getWithPrefix("convert.hd-result",
                "imported", String.valueOf(imported),
                "skipped", String.valueOf(skipped),
                "failed", String.valueOf(failed))));
    }

    /**
     * 解析 HD 的位置格式: world,x,y,z
     *
     * @param locationStr 位置字符串
     * @return Location 对象，解析失败返回 null
     */
    private Location parseHdLocation(String locationStr) {
        if (locationStr == null || locationStr.isEmpty()) {
            return null;
        }

        String[] parts = locationStr.split(",");
        if (parts.length < 4) {
            return null;
        }

        try {
            String worldName = parts[0].trim();
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                return null;
            }

            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            List<String> sources = Arrays.asList("holographicdisplays", "hd");
            List<String> result = new ArrayList<>();
            for (String source : sources) {
                if (source.startsWith(input)) {
                    result.add(source);
                }
            }
            return result;
        }
        return new ArrayList<>();
    }
}
