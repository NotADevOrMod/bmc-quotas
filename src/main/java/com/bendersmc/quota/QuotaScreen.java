package com.bendersmc.quota;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.function.DoubleConsumer;

public class QuotaScreen extends Screen {
	private static final Identifier LOGO = Identifier.fromNamespaceAndPath("bmcquota", "textures/gui/logo.png");
	private static final int LOGO_W = 289, LOGO_H = 265;
	private static final int PW = 330, PH = 200;
	private static final ChatFormatting[] COLORS = {
			ChatFormatting.BLACK, ChatFormatting.DARK_BLUE, ChatFormatting.DARK_GREEN, ChatFormatting.DARK_AQUA,
			ChatFormatting.DARK_RED, ChatFormatting.DARK_PURPLE, ChatFormatting.GOLD, ChatFormatting.GRAY,
			ChatFormatting.DARK_GRAY, ChatFormatting.BLUE, ChatFormatting.GREEN, ChatFormatting.AQUA,
			ChatFormatting.RED, ChatFormatting.LIGHT_PURPLE, ChatFormatting.YELLOW, ChatFormatting.WHITE};
	private static final int[] STRIP = {0xFFFB8C00, 0xFFE53935, 0xFF00ACC1, 0xFF43A047, 0xFF1E63D6};

	private final Screen parent;
	private int tab = 0, modePage = 0, x0, y0;
	private boolean confirmReset;
	private String dropTarget;
	private EditBox addBox;

	public QuotaScreen(Screen parent) {
		super(Component.literal("BendersMC Quotas"));
		this.parent = parent;
	}

	// ======================= layout =======================

	@Override
	protected void init() {
		x0 = (width - PW) / 2;
		y0 = Math.max(2, (height - PH) / 2);
		int x1 = x0 + PW;
		tabBtn("Overview", 0, x0 + 6);
		tabBtn("Quotas", 1, x0 + 80);
		tabBtn("Profile", 2, x0 + 154);
		addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
				.bounds(x1 - 56, y0 + 43, 50, 16).build());
		switch (tab) {
			case 0 -> overviewWidgets();
			case 1 -> quotaWidgets();
			default -> profileWidgets();
		}
	}

	private void tabBtn(String name, int idx, int x) {
		Button b = Button.builder(Component.literal(name), btn -> {
			tab = idx;
			confirmReset = false;
			dropTarget = null;
			rebuildWidgets();
		}).bounds(x, y0 + 43, 70, 16).build();
		b.active = tab != idx;
		addRenderableWidget(b);
	}

	private Button small(String label, int x, int y, int w, Runnable r) {
		Button b = Button.builder(Component.literal(label), btn -> r.run()).bounds(x, y, w, 14).build();
		addRenderableWidget(b);
		return b;
	}

	@Override
	public void onClose() {
		Tracker.save();
		Minecraft.getInstance().gui.setScreen(parent);
	}

	// ======================= overview =======================

	private List<String> overviewKeys(Data.Profile p) {
		List<String> keys = new ArrayList<>();
		for (var e : p.hours.entrySet()) {
			if (e.getValue() > 0 || Tracker.d.playMillis.getOrDefault(e.getKey(), 0L) > 0) keys.add(e.getKey());
		}
		return keys;
	}

	private int overviewRows(Data.Profile p) {
		return overviewKeys(p).size() + 1 + (p.help > 0 ? 1 : 0);
	}

	private int rowH(int n) { return Math.min(17, 106 / Math.max(n, 1)); }

	private void overviewWidgets() {
		Data.Profile p = Tracker.d.profile();
		int n = overviewRows(p);
		int rh = rowH(n);
		int dRow = overviewKeys(p).size();
		int y = y0 + 66 + dRow * rh;
		small("-", x0 + 252, y, 18, () -> Tracker.addDiscord(-1));
		small("+", x0 + 272, y, 18, () -> Tracker.addDiscord(1));
		small("+10", x0 + 292, y, 28, () -> Tracker.addDiscord(10));
		if (p.help > 0) {
			int y2 = y + rh;
			small("-", x0 + 252, y2, 18, () -> Tracker.addHelp(-1));
			small("+", x0 + 272, y2, 18, () -> Tracker.addHelp(1));
			small("+10", x0 + 292, y2, 28, () -> Tracker.addHelp(10));
		}

		int fy = y0 + PH - 24;
		String cur = Tracker.mode == null ? "nothing" : Data.pretty(Tracker.mode);
		addRenderableWidget(Button.builder(Component.literal("Tracking: " + cur), b -> {
			List<String> opts = new ArrayList<>();
			opts.add(null);
			opts.addAll(Tracker.d.profile().hours.keySet());
			int i = opts.indexOf(Tracker.mode);
			Tracker.mode = opts.get((i + 1) % opts.size());
			rebuildWidgets();
		}).bounds(x0 + 6, fy, 140, 16).build());
		addRenderableWidget(Button.builder(Component.literal(confirmReset ? "Click again to reset" : "Reset week"), b -> {
			if (confirmReset) {
				Tracker.resetWeek();
				confirmReset = false;
			} else confirmReset = true;
			rebuildWidgets();
		}).bounds(x0 + PW - 130, fy, 124, 16).build());
	}

	private void drawOverview(GuiGraphicsExtractor g) {
		Data d = Tracker.d;
		Data.Profile p = d.profile();
		List<String> keys = overviewKeys(p);
		int n = overviewRows(p);
		int rh = rowH(n);
		int by = y0 + 66;
		for (int i = 0; i < keys.size(); i++) {
			String k = keys.get(i);
			double quota = p.hours.getOrDefault(k, 0.0);
			long played = d.playMillis.getOrDefault(k, 0L);
			int ry = by + i * rh;
			text(g, Data.pretty(k), x0 + 10, ry + 3, 0xFFFFFFFF);
			if (quota > 0) {
				bar(g, x0 + 104, ry + 2, 80, 10, played / (quota * 3_600_000d));
				boolean done = Tracker.modeDone(p, k);
				text(g, done ? "DONE  " + hm(played) : hm(played) + " / " + hrs(quota), x0 + 190, ry + 3,
						done ? 0xFF55FF55 : 0xFFCCCCCC);
			} else {
				text(g, hm(played) + "  (no quota)", x0 + 104, ry + 3, 0xFF888888);
			}
		}
		counterRow(g, "Discord msgs", d.discordCount, p.discord, by + keys.size() * rh);
		if (p.help > 0) counterRow(g, "Public help", d.helpCount, p.help, by + (keys.size() + 1) * rh);

		int fy = y0 + PH - 24;
		boolean on = Tracker.online;
		text(g, on ? "Online" : "Offline", x0 + 150, fy + 4, on ? 0xFF55FF55 : 0xFF888888);
	}

	private void counterRow(GuiGraphicsExtractor g, String label, int count, int quota, int ry) {
		text(g, label, x0 + 10, ry + 3, 0xFFFFFFFF);
		if (quota > 0) {
			bar(g, x0 + 104, ry + 2, 80, 10, (double) count / quota);
			boolean done = count >= quota;
			text(g, count + "/" + quota, x0 + 190, ry + 3, done ? 0xFF55FF55 : 0xFFCCCCCC);
		} else {
			text(g, count + "  (no quota)", x0 + 104, ry + 3, 0xFF888888);
		}
	}

	private void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double pct) {
		pct = Math.max(0, Math.min(1, pct));
		g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
		g.fill(x, y, x + w, y + h, 0xFF2A2A2A);
		int fw = (int) Math.round(w * pct);
		if (fw > 0) g.fill(x, y, x + fw, y + h, pct >= 1 ? 0xFF43A047 : 0xFFFB8C00);
	}

	// ======================= quotas tab =======================

	private int qy(int i) { return y0 + 66 + i * 17; }

	private void quotaWidgets() {
		Data.Profile p = Tracker.d.profile();
		List<String> keys = new ArrayList<>(p.hours.keySet());
		int pages = Math.max(1, (keys.size() + 3) / 4);
		modePage = Math.max(0, Math.min(modePage, pages - 1));
		for (int i = 0; i < 4; i++) {
			int idx = modePage * 4 + i;
			if (idx >= keys.size()) break;
			String k = keys.get(idx);
			int y = qy(i);
			deltaButtons(y, new double[]{-5, -0.5, 0.5, 5}, dv ->
					p.hours.put(k, Math.max(0, Math.round((p.hours.get(k) + dv) * 2) / 2.0)));
			small("x", x0 + 312, y, 14, () -> {
				p.hours.remove(k);
				Tracker.dirty();
				rebuildWidgets();
			});
		}
		deltaButtons(qy(4), new double[]{-50, -10, 10, 50}, dv -> p.discord = (int) Math.max(0, p.discord + dv));
		deltaButtons(qy(5), new double[]{-50, -10, 10, 50}, dv -> p.help = (int) Math.max(0, p.help + dv));

		addBox = new EditBox(font, x0 + 10, qy(6), 130, 14, Component.empty());
		addBox.setMaxLength(24);
		addBox.setHint(Component.literal("new gamemode id..."));
		addRenderableWidget(addBox);
		small("Add", x0 + 144, qy(6), 36, () -> {
			String k = addBox.getValue().trim().toLowerCase().replaceAll("\\s+", "");
			if (!k.isEmpty()) {
				p.hours.putIfAbsent(k, 0.0);
				Tracker.dirty();
				rebuildWidgets();
			}
		});
		small("<", x0 + 188, qy(6), 18, () -> { modePage = Math.max(0, modePage - 1); rebuildWidgets(); });
		small(">", x0 + 208, qy(6), 18, () -> { modePage++; rebuildWidgets(); });
	}

	private void deltaButtons(int y, double[] deltas, DoubleConsumer onDelta) {
		for (int i = 0; i < deltas.length; i++) {
			double dv = deltas[i];
			String label = (dv > 0 ? "+" : "") + (dv == Math.floor(dv) ? String.valueOf((long) dv) : String.valueOf(dv));
			small(label, x0 + 190 + i * 30, y, 28, () -> {
				onDelta.accept(dv);
				Tracker.dirty();
			});
		}
	}

	private void drawQuotas(GuiGraphicsExtractor g) {
		Data.Profile p = Tracker.d.profile();
		List<String> keys = new ArrayList<>(p.hours.keySet());
		int pages = Math.max(1, (keys.size() + 3) / 4);
		for (int i = 0; i < 4; i++) {
			int idx = modePage * 4 + i;
			if (idx >= keys.size()) break;
			String k = keys.get(idx);
			double h = p.hours.get(k);
			text(g, Data.pretty(k), x0 + 10, qy(i) + 3, 0xFFFFFFFF);
			text(g, h > 0 ? "Quota: " + hrs(h) : "No quota", x0 + 104, qy(i) + 3, h > 0 ? 0xFFFFFF55 : 0xFF888888);
		}
		text(g, "Discord msgs", x0 + 10, qy(4) + 3, 0xFFFFFFFF);
		text(g, p.discord > 0 ? "Quota: " + p.discord : "No quota", x0 + 104, qy(4) + 3, p.discord > 0 ? 0xFFFFFF55 : 0xFF888888);
		text(g, "Public help", x0 + 10, qy(5) + 3, 0xFFFFFFFF);
		text(g, p.help > 0 ? "Quota: " + p.help : "Optional", x0 + 104, qy(5) + 3, p.help > 0 ? 0xFFFFFF55 : 0xFF888888);
		text(g, "Page " + (modePage + 1) + "/" + pages, x0 + 232, qy(6) + 3, 0xFFAAAAAA);
		text(g, "Editing quotas for: " + p.name, x0 + 10, y0 + PH - 12, 0xFF888888);
	}

	// ======================= profile tab =======================

	private int py(int i) { return y0 + 66 + i * 20; }

	private void profileWidgets() {
		Data d = Tracker.d;
		Data.Profile p = d.profile();
		int cx = x0 + 80;

		int y = py(0);
		small("<", cx, y, 18, () -> { d.active = (d.active - 1 + d.profiles.size()) % d.profiles.size(); rebuildWidgets(); });
		EditBox nb = new EditBox(font, cx + 22, y, 110, 14, Component.empty());
		nb.setMaxLength(24);
		nb.setValue(p.name);
		nb.setResponder(s -> { if (!s.isBlank()) { p.name = s; Tracker.dirty(); } });
		addRenderableWidget(nb);
		small(">", cx + 136, y, 18, () -> { d.active = (d.active + 1) % d.profiles.size(); rebuildWidgets(); });
		small("New", cx + 160, y, 40, () -> {
			Data.Profile np = new Data.Profile("Soup " + (d.profiles.size() + 1));
			np.hours.clear();
			np.hours.putAll(p.hours);
			np.discord = p.discord;
			np.help = p.help;
			d.profiles.add(np);
			d.active = d.profiles.size() - 1;
			Tracker.dirty();
			rebuildWidgets();
		});
		small("Del", cx + 204, y, 36, () -> {
			if (d.profiles.size() > 1) {
				d.profiles.remove(d.active);
				d.active = Math.max(0, d.active - 1);
				Tracker.dirty();
				rebuildWidgets();
			}
		});

		y = py(1);
		small("Browse...", cx, y, 60, () -> browse("soup"));
		small("Clear", cx + 64, y, 44, () -> { p.mark = ""; Tracker.dirty(); rebuildWidgets(); });

		y = py(2);
		EditBox rn = new EditBox(font, cx, y, 132, 14, Component.empty());
		rn.setMaxLength(24);
		rn.setValue(d.rankName);
		rn.setResponder(s -> { d.rankName = s; Tracker.dirty(); });
		addRenderableWidget(rn);

		y = py(3);
		small("<", cx, y, 18, () -> { cycleColor(-1); rebuildWidgets(); });
		addRenderableWidget(Button.builder(Component.literal(colorName(colorIndex())).withStyle(COLORS[colorIndex()]),
				b -> { cycleColor(1); rebuildWidgets(); }).bounds(cx + 22, y, 100, 14).build());
		small(">", cx + 126, y, 18, () -> { cycleColor(1); rebuildWidgets(); });

		y = py(4);
		small("Browse...", cx, y, 60, () -> browse("rank"));
		small("Clear", cx + 64, y, 44, () -> { d.rankImage = ""; Tracker.dirty(); rebuildWidgets(); });
	}

	private void drawProfile(GuiGraphicsExtractor g) {
		Data d = Tracker.d;
		Data.Profile p = d.profile();
		text(g, "Soup", x0 + 10, py(0) + 3, 0xFFFFFFFF);
		text(g, "Soup mark", x0 + 10, py(1) + 3, 0xFFFFFFFF);
		text(g, "Rank name", x0 + 10, py(2) + 3, 0xFFFFFFFF);
		text(g, "Rank colour", x0 + 10, py(3) + 3, 0xFFFFFFFF);
		text(g, "Rank image", x0 + 10, py(4) + 3, 0xFFFFFFFF);
		text(g, (d.active + 1) + "/" + d.profiles.size(), x0 + 80 + 246, py(0) + 3, 0xFF888888);
		preview(g, Images.get(p.mark), x0 + 80 + 114, py(1) - 1);
		preview(g, Images.get(d.rankImage), x0 + 80 + 114, py(4) - 1);
		String hint = dropTarget != null
				? "Couldn't open a file picker: drag an image file onto this window now."
				: "Browse opens a file picker. You can also drag an image onto the window.";
		text(g, hint, x0 + 10, py(5) + 6, dropTarget != null ? 0xFFFFFF55 : 0xFF888888);
	}

	private void preview(GuiGraphicsExtractor g, Images.Tex t, int x, int y) {
		g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF000000);
		g.fill(x, y, x + 16, y + 16, 0xFF2A2A2A);
		Images.draw(g, t, x, y, 16);
	}

	private int colorIndex() {
		for (int i = 0; i < COLORS.length; i++) if (COLORS[i].name().equals(Tracker.d.rankColor)) return i;
		return 11;
	}

	private void cycleColor(int dir) {
		Tracker.d.rankColor = COLORS[(colorIndex() + dir + COLORS.length) % COLORS.length].name();
		Tracker.dirty();
	}

	private static String colorName(int i) {
		String[] parts = COLORS[i].name().toLowerCase().split("_");
		StringBuilder sb = new StringBuilder();
		for (String s : parts) sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(' ');
		return sb.toString().trim();
	}

	private void browse(String target) {
		String f = FileDialog.pickImage();
		if (f == null) { dropTarget = target; return; }
		applyImage(target, Path.of(f));
	}

	private void applyImage(String target, Path src) {
		String stored = Images.importFile(src);
		if (stored == null) return;
		if (target.equals("soup")) Tracker.d.profile().mark = stored;
		else Tracker.d.rankImage = stored;
		dropTarget = null;
		Tracker.dirty();
		rebuildWidgets();
	}

	/** Drag-and-drop fallback for the image pickers. */
	public void onFilesDrop(List<Path> paths) {
		if (dropTarget != null && !paths.isEmpty()) applyImage(dropTarget, paths.get(0));
	}

	// ======================= rendering =======================

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta); // panorama / blurred world, like vanilla menus
		Data d = Tracker.d;
		Data.Profile p = d.profile();
		int x1 = x0 + PW, y1 = y0 + PH;

		g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, 0xFF000000);
		g.fill(x0, y0, x1, y1, 0xD0141418);

		// header: logo, title, rank + soup
		int lh = 28, lw = lh * LOGO_W / LOGO_H;
		g.blit(RenderPipelines.GUI_TEXTURED, LOGO, x0 + 6, y0 + 4, 0f, 0f, lw, lh, LOGO_W, LOGO_H, LOGO_W, LOGO_H);
		text(g, "BendersMC Quotas", x0 + 42, y0 + 8, 0xFFFFFFFF);
		text(g, "Week since " + new SimpleDateFormat("dd MMM HH:mm").format(new Date(d.weekStart)), x0 + 42, y0 + 21, 0xFF888888);

		ChatFormatting fmt = COLORS[colorIndex()];
		Component rank = Component.literal(d.rankName).withStyle(fmt);
		Component soup = Component.literal("Soup: " + p.name);
		int endX = x1 - 62;
		g.text(font, rank, endX - font.width(rank), y0 + 8, 0xFFFFFFFF);
		g.text(font, soup, endX - font.width(soup), y0 + 21, 0xFFAAAAAA);
		Images.draw(g, Images.get(d.rankImage), x1 - 30, y0 + 6, 24);
		Images.draw(g, Images.get(p.mark), x1 - 58, y0 + 6, 24);

		int sw = PW / STRIP.length;
		for (int i = 0; i < STRIP.length; i++) {
			g.fill(x0 + i * sw, y0 + 36, i == STRIP.length - 1 ? x1 : x0 + (i + 1) * sw, y0 + 39, STRIP[i]);
		}

		switch (tab) {
			case 0 -> drawOverview(g);
			case 1 -> drawQuotas(g);
			default -> drawProfile(g);
		}
	}

	private void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
		g.text(font, Component.literal(s), x, y, color);
	}

	private static String hm(long ms) {
		long m = ms / 60_000;
		return (m / 60) + "h " + (m % 60) + "m";
	}

	private static String hrs(double h) {
		return h == Math.floor(h) ? (long) h + "h" : h + "h";
	}
}
