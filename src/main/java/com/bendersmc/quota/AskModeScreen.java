package com.bendersmc.quota;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Small popup shown when the mod can't tell which gamemode you just joined. ESC = ignore. */
public class AskModeScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("bmcquota", "textures/gui/logo.png");
	private static final int LOGO_W = 289, LOGO_H = 265;
	private static final int[] STRIP = {0xFFFB8C00, 0xFFE53935, 0xFF00ACC1, 0xFF43A047, 0xFF1E63D6};
	private static final int PW = 300;

	private final String serverName;
	private final List<String> keys = new ArrayList<>();
	private int x0, y0, ph;

	public AskModeScreen(String serverName) {
		super(Component.literal("Which mode is this?"));
		this.serverName = serverName;
	}

	@Override
	protected void init() {
		keys.clear();
		keys.addAll(Tracker.d.profile().hours.keySet());
		int total = keys.size() + 1;
		int rows = (total + 1) / 2;
		ph = 42 + rows * 24 + 8;
		x0 = (width - PW) / 2;
		y0 = Math.max(2, (height - ph) / 2);
		for (int i = 0; i < total; i++) {
			boolean none = i == keys.size();
			String k = none ? Tracker.NONE : keys.get(i);
			String label = none ? "Not counting" : Data.pretty(k);
			addRenderableWidget(Button.builder(Component.literal(label), b -> {
				Tracker.answer(k);
				Minecraft.getInstance().gui.setScreen(null);
			}).bounds(x0 + 10 + (i % 2) * 145, y0 + 42 + (i / 2) * 24, 140, 20).build());
		}
	}

	@Override
	public void onClose() {
		Tracker.dismissAsk();
		Minecraft.getInstance().gui.setScreen(null);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		int x1 = x0 + PW, y1 = y0 + ph;
		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
		g.fill(x0, y0, x1, y1, 0xD0141418);
		int sw = PW / STRIP.length;
		for (int i = 0; i < STRIP.length; i++) {
			g.fill(x0 + i * sw, y0, i == STRIP.length - 1 ? x1 : x0 + (i + 1) * sw, y0 + 3, STRIP[i]);
		}
		int lh = 22, lw = lh * LOGO_W / LOGO_H;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x0 + 8, y0 + 8, 0f, 0f, lw, lh, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
		g.text(font, Component.literal("Which mode is this?"), x0 + 38, y0 + 9, 0xFFFFFFFF);
		String sub = serverName != null ? "Server: " + serverName + " (I'll remember your answer)"
				: "I couldn't tell where you just went";
		g.text(font, Component.literal(sub), x0 + 38, y0 + 22, 0xFF999999);
	}
}
