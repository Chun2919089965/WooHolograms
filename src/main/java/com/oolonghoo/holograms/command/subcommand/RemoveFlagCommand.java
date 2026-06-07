package com.oolonghoo.holograms.command.subcommand;

import com.oolonghoo.holograms.WooHolograms;
import com.oolonghoo.holograms.command.Subcommand;
import com.oolonghoo.holograms.hologram.EnumFlag;
import com.oolonghoo.holograms.hologram.Hologram;
import com.oolonghoo.holograms.hologram.HologramLine;
import com.oolonghoo.holograms.hologram.HologramPage;
import com.oolonghoo.holograms.util.ColorUtil;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 移除标志命令
 * /wh removeflag <名称> [行号] <标志>
 * 不带行号移除全息图级标志，带行号移除行级标志
 */
public class RemoveFlagCommand extends Subcommand {

    private final WooHolograms plugin;

    public RemoveFlagCommand(WooHolograms plugin) {
        super("removeflag", "移除标志", "/wh removeflag <名称> [行号] <标志>", "wooholograms.admin", Arrays.asList("rf"));
        this.plugin = plugin;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(ColorUtil.colorize("&c用法: " + getUsage()));
            return true;
        }

        String name = args[0];
        Hologram hologram = plugin.getHologramManager().getHologram(name);
        if (hologram == null) {
            sender.sendMessage(plugin.getMessages().getWithPrefix("general.hologram-not-found", "name", name));
            return true;
        }

        // 判断参数模式：2个参数 = 全息图级标志，3个参数 = 行级标志
        EnumFlag flag;
        int lineNumber = -1;

        if (args.length == 2) {
            // /wh removeflag <名称> <标志>
            flag = EnumFlag.fromId(args[1]);
        } else {
            // /wh removeflag <名称> <行号> <标志>
            try {
                lineNumber = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                sender.sendMessage(ColorUtil.colorize("&c无效的行号: " + args[1]));
                return true;
            }
            flag = EnumFlag.fromId(args[2]);
        }

        if (flag == null) {
            sender.sendMessage(plugin.getMessages().getWithPrefix("flag.invalid-flag", "flag", args[args.length - 1]));
            return true;
        }

        if (lineNumber >= 0) {
            // 移除行级标志
            HologramPage page = hologram.getPage(0);
            if (page == null) {
                sender.sendMessage(ColorUtil.colorize("&c全息图没有页面！"));
                return true;
            }
            HologramLine line = page.getLine(lineNumber);
            if (line == null) {
                sender.sendMessage(plugin.getMessages().getWithPrefix("flag.invalid-line", "line", String.valueOf(lineNumber)));
                return true;
            }
            if (!line.getFlags().contains(flag)) {
                sender.sendMessage(plugin.getMessages().getWithPrefix("flag.not-has-flag", "flag", flag.getId(), "line", String.valueOf(lineNumber)));
                return true;
            }
            line.removeFlag(flag);
            hologram.save();
            sender.sendMessage(plugin.getMessages().getWithPrefix("flag.removed-line", "flag", flag.getId(), "line", String.valueOf(lineNumber), "name", name));
        } else {
            // 移除全息图级标志
            if (!hologram.getFlags().contains(flag)) {
                sender.sendMessage(plugin.getMessages().getWithPrefix("flag.not-has-flag-holo", "flag", flag.getId(), "name", name));
                return true;
            }
            hologram.removeFlag(flag);
            hologram.save();
            sender.sendMessage(plugin.getMessages().getWithPrefix("flag.removed-holo", "flag", flag.getId(), "name", name));
        }

        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return plugin.getHologramManager().getHologramNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // 补全行号或标志名
            List<String> completions = new ArrayList<>();
            // 标志名
            for (EnumFlag f : EnumFlag.values()) {
                if (f.getId().toLowerCase().startsWith(args[1].toLowerCase()) ||
                        f.name().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(f.getId());
                }
            }
            // 行号
            Hologram hologram = plugin.getHologramManager().getHologram(args[0]);
            if (hologram != null && hologram.getPage(0) != null) {
                int lineCount = hologram.getPage(0).size();
                for (int i = 0; i < lineCount; i++) {
                    completions.add(String.valueOf(i));
                }
            }
            return completions;
        } else if (args.length == 3) {
            // 第2个参数是行号，补全标志名
            List<String> completions = new ArrayList<>();
            for (EnumFlag f : EnumFlag.values()) {
                if (f.getId().toLowerCase().startsWith(args[2].toLowerCase()) ||
                        f.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(f.getId());
                }
            }
            return completions;
        }
        return new ArrayList<>();
    }
}
