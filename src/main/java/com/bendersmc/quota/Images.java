package com.bendersmc.quota;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/** Loads user-picked images (soup mark, rank image) as GUI textures. */
public final class Images {
	public record Tex(Identifier id, int w, int h) {}

	private static final Map<String, Tex> CACHE = new HashMap<>();

	private Images() {}

	/** Copies/convert the picked file into config/bmcquota/images as PNG. Returns the stored path or null. */
	public static String importFile(Path src) {
		try {
			Path dir = Tracker.DIR.resolve("images");
			Files.createDirectories(dir);
			Path out = dir.resolve("img_" + System.currentTimeMillis() + ".png");
			try {
				BufferedImage bi = ImageIO.read(src.toFile());
				if (bi == null) return null;
				ImageIO.write(bi, "png", out.toFile());
			} catch (Throwable t) {
				if (src.getFileName().toString().toLowerCase().endsWith(".png")) {
					Files.copy(src, out, StandardCopyOption.REPLACE_EXISTING);
				} else return null;
			}
			return out.toString();
		} catch (Exception e) {
			return null;
		}
	}

	public static Tex get(String path) {
		if (path == null || path.isBlank()) return null;
		if (CACHE.containsKey(path)) return CACHE.get(path);
		Tex tex = null;
		try (InputStream in = Files.newInputStream(Path.of(path))) {
			NativeImage img = NativeImage.read(in);
			Identifier id = Identifier.fromNamespaceAndPath("bmcquota", "dyn/" + CACHE.size() + "_" + System.nanoTime());
			Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "bmcquota image", img));
			tex = new Tex(id, img.getWidth(), img.getHeight());
		} catch (Exception ignored) {}
		CACHE.put(path, tex);
		return tex;
	}

	/** Draws the texture centred in a size x size box, keeping aspect ratio. */
	public static void draw(GuiGraphicsExtractor g, Tex t, int x, int y, int size) {
		if (t == null) return;
		float s = Math.min((float) size / t.w(), (float) size / t.h());
		int w = Math.max(1, Math.round(t.w() * s));
		int h = Math.max(1, Math.round(t.h() * s));
		g.blit(RenderPipelines.GUI_TEXTURED, t.id(), x + (size - w) / 2, y + (size - h) / 2,
				0f, 0f, w, h, t.w(), t.h(), t.w(), t.h());
	}
}
