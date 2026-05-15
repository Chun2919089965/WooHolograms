package com.oolonghoo.holograms.hologram;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;

public class HeadTexture {

    public enum Type {
        BASE64,
        PLAYER,
        HDB
    }

    private final Type type;
    private final String value;
    private UUID uuid;

    private HeadTexture(Type type, String value) {
        this.type = type;
        this.value = value;
    }

    public Type getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getTextureValue() {
        return value;
    }

    public static HeadTexture parse(String input) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        String upperInput = input.toUpperCase(Locale.ROOT);
        
        if (upperInput.startsWith("#HEAD:URL:") || upperInput.startsWith("#SMALLHEAD:URL:")) {
            String base64 = extractValue(input, "URL:");
            if (!base64.isEmpty()) {
                return new HeadTexture(Type.BASE64, base64);
            }
        } else if (upperInput.startsWith("#HEAD:PLAYER:") || upperInput.startsWith("#SMALLHEAD:PLAYER:")) {
            String playerName = extractValue(input, "PLAYER:");
            if (!playerName.isEmpty()) {
                return new HeadTexture(Type.PLAYER, playerName);
            }
        } else if (upperInput.startsWith("#HEAD:HDB:") || upperInput.startsWith("#SMALLHEAD:HDB:")) {
            String hdbId = extractValue(input, "HDB:");
            if (!hdbId.isEmpty()) {
                return new HeadTexture(Type.HDB, hdbId);
            }
        } else if (upperInput.startsWith("#HEAD:") || upperInput.startsWith("#SMALLHEAD:")) {
            String data = extractRawData(input);
            if (!data.isEmpty()) {
                if (isBase64Texture(data)) {
                    return new HeadTexture(Type.BASE64, data);
                } else {
                    return new HeadTexture(Type.PLAYER, data);
                }
            }
        }

        return null;
    }

    private static String extractValue(String input, String prefix) {
        String upperInput = input.toUpperCase(Locale.ROOT);
        int prefixIndex = upperInput.indexOf(prefix);
        if (prefixIndex == -1) {
            return "";
        }
        return input.substring(prefixIndex + prefix.length());
    }

    private static String extractRawData(String input) {
        String upperInput = input.toUpperCase(Locale.ROOT);
        if (upperInput.startsWith("#HEAD:")) {
            return input.substring(6);
        } else if (upperInput.startsWith("#SMALLHEAD:")) {
            return input.substring(11);
        }
        return "";
    }

    private static boolean isBase64Texture(String data) {
        if (data == null || data.isEmpty()) {
            return false;
        }
        if (data.contains("=")) {
            return true;
        }
        if (data.length() > 100 && !data.matches("^[a-zA-Z0-9_]+$")) {
            return true;
        }
        return false;
    }

    public static boolean isHeadTexture(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }
        String upperInput = input.toUpperCase(Locale.ROOT);
        return upperInput.startsWith("#HEAD:") || upperInput.startsWith("#SMALLHEAD:");
    }

    public static ItemStack createHeadFromBase64(String base64) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta != null && base64 != null && !base64.isEmpty()) {
            GameProfile profile = new GameProfile(UUID.randomUUID(), null);
            profile.getProperties().put("textures", new Property("textures", base64));

            try {
                Field profileField = meta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(meta, profile);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                try {
                    Method setProfileMethod = meta.getClass().getDeclaredMethod("setProfile", GameProfile.class);
                    setProfileMethod.setAccessible(true);
                    setProfileMethod.invoke(meta, profile);
                } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException ignored) {
                }
            }

            head.setItemMeta(meta);
        }

        return head;
    }

    @Override
    public String toString() {
        return "HeadTexture{" +
                "type=" + type +
                ", value='" + value + '\'' +
                '}';
    }
}
