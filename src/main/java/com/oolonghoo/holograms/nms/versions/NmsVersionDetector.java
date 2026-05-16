package com.oolonghoo.holograms.nms.versions;

import org.bukkit.Bukkit;

public final class NmsVersionDetector {

    private NmsVersionDetector() {}

    public static String getServerVersion() {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        return packageName.substring(packageName.lastIndexOf('.') + 1);
    }

    public static String getMinecraftVersion() {
        String version = Bukkit.getServer().getBukkitVersion();
        int dashIndex = version.indexOf('-');
        if (dashIndex > 0) {
            return version.substring(0, dashIndex);
        }
        return version;
    }

    public static boolean isSupported() {
        String version = getMinecraftVersion();
        if (version.startsWith("1.21")) {
            return true;
        }
        try {
            String[] parts = version.split("\\.");
            if (parts.length >= 1) {
                int major = Integer.parseInt(parts[0]);
                if (major >= 26) {
                    return true;
                }
            }
        } catch (NumberFormatException ignored) {}
        return false;
    }

    public static String getUnsupportedMessage() {
        return "WooHolograms requires Paper 1.21+ or 26.1+. Current server: " + getMinecraftVersion();
    }
}
