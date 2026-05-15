package com.oolonghoo.holograms.storage;

import com.oolonghoo.holograms.WooHolograms;
import com.oolonghoo.holograms.action.Action;
import com.oolonghoo.holograms.action.ClickType;
import com.oolonghoo.holograms.hologram.Billboard;
import com.oolonghoo.holograms.hologram.Brightness;
import com.oolonghoo.holograms.hologram.EnumFlag;
import com.oolonghoo.holograms.hologram.Hologram;
import com.oolonghoo.holograms.hologram.HologramLine;
import com.oolonghoo.holograms.hologram.HologramPage;
import com.oolonghoo.holograms.hologram.HologramType;
import com.oolonghoo.holograms.hologram.TextAlignment;
import com.oolonghoo.holograms.util.LocationUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class YamlHologramStorage implements HologramStorage {

    private final WooHolograms plugin;
    private final File hologramsDir;
    private final ReentrantLock saveLock = new ReentrantLock();

    public YamlHologramStorage(WooHolograms plugin) {
        this.plugin = plugin;
        this.hologramsDir = new File(plugin.getDataFolder(), "holograms");
        if (!hologramsDir.exists()) {
            hologramsDir.mkdirs();
        }
    }

    private File getHologramFile(String id) {
        return new File(hologramsDir, id + ".yml");
    }

    @Override
    public boolean save(Hologram hologram) {
        saveLock.lock();
        try {
            String id = hologram.getId();
            File file = getHologramFile(id);
            YamlConfiguration yaml = new YamlConfiguration();

            Location loc = hologram.getLocation();
            if (loc == null) {
                plugin.getLogger().warning(() -> "Cannot save hologram " + id + ": location is null");
                return false;
            }

            World world = loc.getWorld();
            if (world == null) {
                plugin.getLogger().warning(() -> "Cannot save hologram " + id + ": world is null");
                return false;
            }

            yaml.set("location", LocationUtil.toString(loc));
            yaml.set("enabled", hologram.isEnabled());
            yaml.set("type", hologram.getType().getId());
            yaml.set("visible", hologram.isVisible());
            yaml.set("persistent", hologram.isPersistent());
            yaml.set("line-height", hologram.getLineHeight());
            yaml.set("billboard", hologram.getBillboard().getId());
            yaml.set("facing", hologram.getFacing());
            yaml.set("double-sided", hologram.isDoubleSided());
            yaml.set("display-range", hologram.getDisplayRange());
            yaml.set("update-range", hologram.getUpdateRange());
            yaml.set("update-interval", hologram.getUpdateInterval());
            yaml.set("down-origin", hologram.isDownOrigin());

            if (hologram.getPermission() != null && !hologram.getPermission().isEmpty()) {
                yaml.set("permission", hologram.getPermission());
            }

            if (!hologram.getFlags().isEmpty()) {
                yaml.set("flags", hologram.getFlags().stream()
                        .map(EnumFlag::name)
                        .collect(Collectors.toList()));
            }

            List<HologramPage> pages = hologram.getPages();
            for (int pageIndex = 0; pageIndex < pages.size(); pageIndex++) {
                HologramPage page = pages.get(pageIndex);
                String pagePath = "pages." + pageIndex;

                saveActions(yaml, pagePath + ".actions", page.getActions());

                List<HologramLine> lines = page.getLines();
                for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
                    HologramLine line = lines.get(lineIndex);
                    String linePath = pagePath + ".lines." + lineIndex;

                    yaml.set(linePath + ".content", line.getContent());
                    yaml.set(linePath + ".height", line.getHeight());
                    yaml.set(linePath + ".offsetX", line.getOffsetX());
                    yaml.set(linePath + ".offsetY", line.getOffsetY());
                    yaml.set(linePath + ".offsetZ", line.getOffsetZ());
                    yaml.set(linePath + ".facing", line.getFacing());

                    if (line.getCustomYaw() != null) {
                        yaml.set(linePath + ".custom-yaw", line.getCustomYaw());
                    }

                    if (line.getCustomPitch() != null) {
                        yaml.set(linePath + ".custom-pitch", line.getCustomPitch());
                    }

                    if (line.getBrightness() != null) {
                        yaml.set(linePath + ".brightness",
                                line.getBrightness().getSkyLight() + "," + line.getBrightness().getBlockLight());
                    }

                    if (line.getAlignment() != null) {
                        yaml.set(linePath + ".alignment", line.getAlignment().getId());
                    }

                    if (line.getBillboard() != null) {
                        yaml.set(linePath + ".billboard", line.getBillboard().getId());
                    }

                    if (line.getPermission() != null && !line.getPermission().isEmpty()) {
                        yaml.set(linePath + ".permission", line.getPermission());
                    }

                    if (!line.getFlags().isEmpty()) {
                        yaml.set(linePath + ".flags", line.getFlags().stream()
                                .map(EnumFlag::name)
                                .collect(Collectors.toList()));
                    }

                    if (line.hasActions()) {
                        saveActions(yaml, linePath + ".actions", line.getActions());
                    }
                }
            }

            return saveYaml(yaml, file);
        } finally {
            saveLock.unlock();
        }
    }

    private void saveActions(YamlConfiguration yaml, String path, Map<ClickType, List<Action>> actionsMap) {
        for (Map.Entry<ClickType, List<Action>> entry : actionsMap.entrySet()) {
            ClickType clickType = entry.getKey();
            List<Action> actions = entry.getValue();
            if (actions == null || actions.isEmpty()) {
                continue;
            }
            List<String> actionStrings = new ArrayList<>();
            for (Action action : actions) {
                actionStrings.add(action.toString());
            }
            yaml.set(path + "." + clickType.name(), actionStrings);
        }
    }

    @Override
    public Hologram load(String id) {
        File file = getHologramFile(id);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return loadHologram(id, yaml);
    }

    @Override
    public Map<String, Hologram> loadAll() {
        Map<String, Hologram> holograms = new HashMap<>();

        File[] files = hologramsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return holograms;
        }

        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            Hologram hologram = load(id);
            if (hologram != null) {
                holograms.put(id, hologram);
            }
        }

        return holograms;
    }

    @Override
    public boolean delete(String id) {
        File file = getHologramFile(id);
        if (file.exists()) {
            return file.delete();
        }
        return true;
    }

    @Override
    public boolean exists(String id) {
        return getHologramFile(id).exists();
    }

    @Override
    public int count() {
        File[] files = hologramsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        return files == null ? 0 : files.length;
    }

    @Override
    public void saveAsync(Hologram hologram) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> save(hologram));
    }

    @Override
    public void saveAll() {
    }

    @Override
    public void saveAllAsync() {
    }

    private String getCompatString(ConfigurationSection section, String kebabKey, String camelKey) {
        String value = section.getString(kebabKey);
        if (value == null) {
            value = section.getString(camelKey);
        }
        return value;
    }

    private boolean getCompatBoolean(ConfigurationSection section, String kebabKey, String camelKey, boolean defaultValue) {
        if (section.contains(kebabKey)) {
            return section.getBoolean(kebabKey);
        }
        if (section.contains(camelKey)) {
            return section.getBoolean(camelKey);
        }
        return defaultValue;
    }

    private double getCompatDouble(ConfigurationSection section, String kebabKey, String camelKey, double defaultValue) {
        if (section.contains(kebabKey)) {
            return section.getDouble(kebabKey);
        }
        if (section.contains(camelKey)) {
            return section.getDouble(camelKey);
        }
        return defaultValue;
    }

    private int getCompatInt(ConfigurationSection section, String kebabKey, String camelKey, int defaultValue) {
        if (section.contains(kebabKey)) {
            return section.getInt(kebabKey);
        }
        if (section.contains(camelKey)) {
            return section.getInt(camelKey);
        }
        return defaultValue;
    }

    private Location loadLocation(ConfigurationSection section) {
        if (section.contains("location")) {
            String locStr = section.getString("location");
            if (locStr != null && !locStr.isEmpty()) {
                Location loc = LocationUtil.fromString(locStr);
                if (loc != null) {
                    return loc;
                }
            }
        }

        String worldName = section.getString("world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }

        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            return null;
        }

        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0);
        float pitch = (float) section.getDouble("pitch", 0);

        return new Location(world, x, y, z, yaw, pitch);
    }

    private Hologram loadHologram(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }

        Location location = loadLocation(section);
        if (location == null) {
            plugin.getLogger().warning(() -> "Cannot load hologram " + id + ": location is null");
            return null;
        }

        Hologram hologram = new Hologram(id, location, true);
        hologram.setStorage(this);

        hologram.setEnabled(section.getBoolean("enabled", true));
        String typeId = getCompatString(section, "type", "type");
        if (typeId != null && !typeId.isEmpty()) {
            hologram.setType(HologramType.fromId(typeId));
        }
        hologram.setVisible(getCompatBoolean(section, "visible", "visible", true));
        hologram.setPersistent(getCompatBoolean(section, "persistent", "persistent", true));
        hologram.setLineHeight(getCompatDouble(section, "line-height", "lineHeight", 0.25));
        hologram.setBillboard(Billboard.fromId(getCompatString(section, "billboard", "billboard")));
        hologram.setFacing((float) section.getDouble("facing", 0));
        hologram.setDoubleSided(getCompatBoolean(section, "double-sided", "doubleSided", false));
        hologram.setDisplayRange(getCompatDouble(section, "display-range", "displayRange", 48.0));
        hologram.setUpdateRange(getCompatDouble(section, "update-range", "updateRange", 48.0));
        hologram.setUpdateInterval(getCompatInt(section, "update-interval", "updateInterval", 40));
        hologram.setDownOrigin(getCompatBoolean(section, "down-origin", "downOrigin", true));

        String permission = section.getString("permission");
        if (permission != null && !permission.isEmpty()) {
            hologram.setPermission(permission);
        }

        if (section.contains("flags")) {
            List<String> flagList = section.getStringList("flags");
            for (String flagStr : flagList) {
                try {
                    EnumFlag flag = EnumFlag.valueOf(flagStr.toUpperCase());
                    hologram.addFlags(flag);
                } catch (IllegalArgumentException e) {
                    if (plugin.getConfigManager().isDebug()) {
                        plugin.getLogger().warning(() -> "Unknown flag '" + flagStr + "' for hologram " + hologram.getName());
                    }
                }
            }
        }

        ConfigurationSection pagesSection = section.getConfigurationSection("pages");
        if (pagesSection != null) {
            hologram.removePage(0);

            Set<String> pageKeys = pagesSection.getKeys(false);
            for (String pageIndex : pageKeys) {
                HologramPage page = hologram.addPage();
                ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageIndex);

                if (pageSection != null) {
                    loadPageActions(pageSection.getConfigurationSection("actions"), page);

                    ConfigurationSection linesSection = pageSection.getConfigurationSection("lines");
                    if (linesSection != null) {
                        Set<String> lineKeys = linesSection.getKeys(false);
                        for (String lineIndex : lineKeys) {
                            ConfigurationSection lineSection = linesSection.getConfigurationSection(lineIndex);
                            if (lineSection != null) {
                                loadHologramLine(lineSection, page);
                            }
                        }
                        page.realignLines();
                    }
                }
            }
        }

        return hologram;
    }

    private void loadPageActions(ConfigurationSection section, HologramPage page) {
        if (section == null) {
            return;
        }

        for (ClickType clickType : ClickType.values()) {
            if (!section.contains(clickType.name())) {
                continue;
            }

            List<String> actionStrings = section.getStringList(clickType.name());
            for (String actionStr : actionStrings) {
                Action action = Action.fromString(actionStr);
                if (action != null) {
                    page.addAction(clickType, action);
                }
            }
        }
    }

    private void loadHologramLine(ConfigurationSection section, HologramPage page) {
        String content = section.getString("content", "");
        HologramLine line = page.addLine(content);

        line.setHeight(section.getDouble("height", 0.25));
        line.setOffsetX(section.getDouble("offsetX", 0));
        line.setOffsetY(section.getDouble("offsetY", 0));
        line.setOffsetZ(section.getDouble("offsetZ", 0));
        line.setFacing((float) section.getDouble("facing", 0));

        if (section.contains("custom-yaw")) {
            line.setCustomYaw((float) section.getDouble("custom-yaw"));
        } else if (section.contains("customYaw")) {
            line.setCustomYaw((float) section.getDouble("customYaw"));
        }

        if (section.contains("custom-pitch")) {
            line.setCustomPitch((float) section.getDouble("custom-pitch"));
        } else if (section.contains("customPitch")) {
            line.setCustomPitch((float) section.getDouble("customPitch"));
        }

        if (section.contains("brightness")) {
            String brightnessValue = section.getString("brightness", "15,15");
            if (brightnessValue != null) {
                String[] brightnessParts = brightnessValue.split(",");
                if (brightnessParts.length == 2) {
                    try {
                        int skyLight = Integer.parseInt(brightnessParts[0]);
                        int blockLight = Integer.parseInt(brightnessParts[1]);
                        line.setBrightness(Brightness.of(skyLight, blockLight));
                    } catch (NumberFormatException e) {
                        if (plugin.getConfigManager().isDebug()) {
                            String hologramName = line.getHologram() != null ? line.getHologram().getName() : "unknown";
                            plugin.getLogger().warning(() -> "Invalid brightness format for line in hologram " + hologramName + ": " + brightnessValue);
                        }
                    }
                }
            }
        }

        if (section.contains("alignment")) {
            line.setAlignment(TextAlignment.fromId(section.getString("alignment")));
        }

        if (section.contains("billboard")) {
            line.setBillboard(Billboard.fromId(section.getString("billboard")));
        }

        String linePermission = section.getString("permission");
        if (linePermission != null && !linePermission.isEmpty()) {
            line.setPermission(linePermission);
        }

        if (section.contains("flags")) {
            List<String> flagList = section.getStringList("flags");
            for (String flagStr : flagList) {
                try {
                    EnumFlag flag = EnumFlag.valueOf(flagStr.toUpperCase());
                    line.addFlags(flag);
                } catch (IllegalArgumentException e) {
                    if (plugin.getConfigManager().isDebug()) {
                        String hologramName = line.getHologram() != null ? line.getHologram().getName() : "unknown";
                        plugin.getLogger().warning(() -> "Unknown flag '" + flagStr + "' for line in hologram " + hologramName);
                    }
                }
            }
        }

        ConfigurationSection actionsSection = section.getConfigurationSection("actions");
        if (actionsSection != null) {
            for (ClickType clickType : ClickType.values()) {
                if (!actionsSection.contains(clickType.name())) {
                    continue;
                }

                List<String> actionStrings = actionsSection.getStringList(clickType.name());
                for (String actionStr : actionStrings) {
                    Action action = Action.fromString(actionStr);
                    if (action != null) {
                        line.addAction(clickType, action);
                    }
                }
            }
        }
    }

    private boolean saveYaml(YamlConfiguration yaml, File file) {
        try {
            yaml.save(file);
            return true;
        } catch (IOException e) {
            String errorMsg = e.getMessage();
            plugin.getLogger().severe(() -> "Cannot save hologram file: " + errorMsg);
            return false;
        }
    }

    public void migrateFromOldFormat() {
        File oldDataDir = new File(plugin.getDataFolder(), "data");
        File oldFile = new File(oldDataDir, "holograms.yml");

        if (!oldFile.exists()) {
            return;
        }

        File[] existingFiles = hologramsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (existingFiles != null && existingFiles.length > 0) {
            return;
        }

        YamlConfiguration oldYaml = YamlConfiguration.loadConfiguration(oldFile);
        ConfigurationSection section = oldYaml.getConfigurationSection("holograms");
        if (section == null) {
            return;
        }

        Set<String> keys = section.getKeys(false);
        int count = 0;

        for (String id : keys) {
            ConfigurationSection holoSection = section.getConfigurationSection(id);
            if (holoSection == null) {
                continue;
            }

            Location location = loadLocation(holoSection);
            if (location == null) {
                continue;
            }

            Hologram hologram = new Hologram(id, location, true);
            hologram.setStorage(this);

            hologram.setEnabled(holoSection.getBoolean("enabled", true));
            hologram.setType(HologramType.fromId(holoSection.getString("type", "TEXT")));
            hologram.setVisible(holoSection.getBoolean("visible", true));
            hologram.setPersistent(holoSection.getBoolean("persistent", true));
            hologram.setLineHeight(holoSection.getDouble("lineHeight", 0.25));
            hologram.setBillboard(Billboard.fromId(holoSection.getString("billboard", "center")));
            hologram.setFacing((float) holoSection.getDouble("facing", 0));
            hologram.setDoubleSided(holoSection.getBoolean("doubleSided", false));
            hologram.setDisplayRange(holoSection.getDouble("displayRange", 48.0));
            hologram.setUpdateRange(holoSection.getDouble("updateRange", 48.0));
            hologram.setUpdateInterval(holoSection.getInt("updateInterval", 3));
            hologram.setDownOrigin(holoSection.getBoolean("downOrigin", true));

            String permission = holoSection.getString("permission");
            if (permission != null && !permission.isEmpty()) {
                hologram.setPermission(permission);
            }

            if (holoSection.contains("flags")) {
                List<String> flagList = holoSection.getStringList("flags");
                for (String flagStr : flagList) {
                    try {
                        EnumFlag flag = EnumFlag.valueOf(flagStr.toUpperCase());
                        hologram.addFlags(flag);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            ConfigurationSection pagesSection = holoSection.getConfigurationSection("pages");
            if (pagesSection != null) {
                hologram.removePage(0);

                Set<String> pageKeys = pagesSection.getKeys(false);
                for (String pageIndex : pageKeys) {
                    HologramPage page = hologram.addPage();
                    ConfigurationSection pageSection = pagesSection.getConfigurationSection(pageIndex);

                    if (pageSection != null) {
                        loadPageActions(pageSection.getConfigurationSection("actions"), page);

                        ConfigurationSection linesSection = pageSection.getConfigurationSection("lines");
                        if (linesSection != null) {
                            Set<String> lineKeys = linesSection.getKeys(false);
                            for (String lineIndex : lineKeys) {
                                ConfigurationSection lineSection = linesSection.getConfigurationSection(lineIndex);
                                if (lineSection != null) {
                                    loadHologramLine(lineSection, page);
                                }
                            }
                        }
                    }
                }
            }

            if (save(hologram)) {
                count++;
            }
        }

        if (count > 0) {
            File backupFile = new File(oldDataDir, "holograms.yml.bak");
            if (oldFile.renameTo(backupFile)) {
                plugin.getLogger().info("Migrated " + count + " holograms from old format to new format");
            } else {
                plugin.getLogger().warning("Migrated " + count + " holograms but failed to rename old file to .bak");
            }
        }
    }

    @Override
    public void reload() {
    }
}
