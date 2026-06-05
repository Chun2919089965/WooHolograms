package eu.decentsoftware.holograms.api;

import eu.decentsoftware.holograms.api.holograms.Hologram;
import eu.decentsoftware.holograms.api.holograms.HologramLine;
import eu.decentsoftware.holograms.api.holograms.HologramPage;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * DH 兼容层 - DHAPI 静态工具类
 * 镜像 DecentHolograms 的 DHAPI，委托到 WooHologramsAPI
 */
public final class DHAPI {

	private DHAPI() {}

	// ===== 创建/删除 =====

	public static Hologram createHologram(String name, Location location) {
		com.oolonghoo.holograms.hologram.Hologram holo =
				com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().createHologram(name, location);
		return holo != null ? new Hologram(holo) : null;
	}

	public static Hologram createHologram(String name, Location location, List<String> lines) {
		Hologram hologram = createHologram(name, location);
		if (hologram != null && lines != null && !lines.isEmpty()) {
			com.oolonghoo.holograms.hologram.HologramPage page = hologram.getHandle().getPage(0);
			if (page != null) {
				for (String line : lines) {
					if (line != null) {
						page.addLine(line);
					}
				}
			}
		}
		return hologram;
	}

	public static Hologram createHologram(String name, Location location, boolean saveToFile) {
		com.oolonghoo.holograms.hologram.Hologram holo =
				com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().createHologram(name, location, saveToFile);
		return holo != null ? new Hologram(holo) : null;
	}

	public static Hologram createHologram(String name, Location location, boolean saveToFile, List<String> lines) {
		Hologram hologram = createHologram(name, location, saveToFile);
		if (hologram != null && lines != null && !lines.isEmpty()) {
			com.oolonghoo.holograms.hologram.HologramPage page = hologram.getHandle().getPage(0);
			if (page != null) {
				for (String line : lines) {
					if (line != null) {
						page.addLine(line);
					}
				}
			}
		}
		return hologram;
	}

	public static boolean removeHologram(String name) {
		return com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().deleteHologram(name);
	}

	// ===== 查询 =====

	public static Hologram getHologram(String name) {
		com.oolonghoo.holograms.hologram.Hologram holo =
				com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().getHologram(name);
		return holo != null ? new Hologram(holo) : null;
	}

	public static Collection<Hologram> getHolograms() {
		Collection<com.oolonghoo.holograms.hologram.Hologram> holos =
				com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().getHolograms();
		List<Hologram> result = new ArrayList<>(holos.size());
		for (com.oolonghoo.holograms.hologram.Hologram holo : holos) {
			result.add(new Hologram(holo));
		}
		return result;
	}

	public static boolean hologramExists(String name) {
		return com.oolonghoo.holograms.api.WooHologramsAPI.getHologramManager().exists(name);
	}

	// ===== 页面操作 =====

	public static HologramPage createHologramPage(Hologram hologram) {
		if (hologram == null) return null;
		return hologram.addPage();
	}

	public static HologramPage getHologramPage(Hologram hologram, int page) {
		if (hologram == null) return null;
		return hologram.getPage(page);
	}

	public static HologramPage insertHologramPage(Hologram hologram, int page) {
		if (hologram == null) return null;
		return hologram.insertPage(page);
	}

	public static boolean removeHologramPage(Hologram hologram, int page) {
		if (hologram == null) return false;
		return hologram.removePage(page);
	}

	// ===== 行操作 =====

	public static HologramLine addHologramLine(Hologram hologram, String line) {
		return addHologramLine(hologram, 0, line);
	}

	public static HologramLine addHologramLine(Hologram hologram, int page, String line) {
		if (hologram == null) return null;
		HologramPage holoPage = hologram.getPage(page);
		if (holoPage == null) return null;
		return holoPage.addLine(line);
	}

	public static HologramLine insertHologramLine(Hologram hologram, int page, int index, String line) {
		if (hologram == null) return null;
		HologramPage holoPage = hologram.getPage(page);
		if (holoPage == null) return null;
		boolean success = holoPage.insertLine(index, line);
		return success ? holoPage.getLine(index) : null;
	}

	public static boolean setHologramLine(Hologram hologram, int page, int index, String line) {
		if (hologram == null) return false;
		HologramPage holoPage = hologram.getPage(page);
		if (holoPage == null) return false;
		return holoPage.setLine(index, line);
	}

	public static boolean removeHologramLine(Hologram hologram, int page, int index) {
		if (hologram == null) return false;
		HologramPage holoPage = hologram.getPage(page);
		if (holoPage == null) return false;
		return holoPage.removeLine(index) != null;
	}

	public static HologramLine getHologramLine(Hologram hologram, int page, int index) {
		if (hologram == null) return null;
		HologramPage holoPage = hologram.getPage(page);
		if (holoPage == null) return null;
		return holoPage.getLine(index);
	}

	// ===== 传送 =====

	public static void teleportHologram(String name, Location location) {
		Hologram hologram = getHologram(name);
		if (hologram != null) {
			hologram.teleport(location);
		}
	}

	// ===== 显示/隐藏 =====

	public static void showHologram(String name, Player player) {
		Hologram hologram = getHologram(name);
		if (hologram != null) {
			hologram.show(player);
		}
	}

	public static void hideHologram(String name, Player player) {
		Hologram hologram = getHologram(name);
		if (hologram != null) {
			hologram.hide(player);
		}
	}
}
