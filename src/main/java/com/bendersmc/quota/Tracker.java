package com.bendersmc.quota;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/** Playtime tracking, quota checks, saving. All calls happen on the client thread. */
public final class Tracker {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("bmcquota");
	private static final Path FILE = DIR.resolve("data.json");
	private static final Path DISCORD_FILE = DIR.resolve("discord.json");
	private static final Path DEBUG_LOG = DIR.resolve("debug.log");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static Data d = new Data();
	/** Gamemode currently being counted (null = not counting). */
	public static String mode;
	public static boolean online;

	private static String pending;
	private static long pendingAt, last, logUntil, discordModified;
	private static int ticks;
	private static boolean dirty;

	private Tracker() {}

	// ---------- lifecycle ----------

	public static void init() {
		try {
			Files.createDirectories(DIR);
			if (Files.exists(FILE)) {
				Data loaded = GSON.fromJson(Files.readString(FILE), Data.class);
				if (loaded != null) d = loaded;
			}
		} catch (Exception e) {
			System.err.println("[bmcquota] could not load data: " + e);
		}
		d.profile(); // make sure at least one profile exists
		last = System.currentTimeMillis();
	}

	public static void dirty() { dirty = true; }

	public static void save() {
		try {
			Files.createDirectories(DIR);
			Files.writeString(FILE, GSON.toJson(d));
			dirty = false;
		} catch (Exception e) {
			System.err.println("[bmcquota] could not save data: " + e);
		}
	}

	// ---------- events ----------

	public static void onJoin(Minecraft mc) {
		var server = mc.getCurrentServer();
		String ip = server == null ? "" : String.valueOf(server.ip).toLowerCase();
		online = ip.contains(d.serverMatch.toLowerCase());
		long now = System.currentTimeMillis();
		if (online && pending != null && now - pendingAt < 120_000) {
			mode = pending;
			pending = null;
		}
		last = now;
	}

	public static void onDisconnect() {
		online = false;
		mode = null;
		pending = null;
		save();
	}

	/** Called with the command text WITHOUT the leading slash. */
	public static void onCommand(String command) {
		String[] parts = command.trim().toLowerCase().split("\\s+");
		if (parts.length == 0) return;
		if (parts[0].equals("joinq") && parts.length >= 2) {
			pending = parts[1];
			pendingAt = System.currentTimeMillis();
			logUntil = pendingAt + 20_000; // log what the server prints so we can refine detection
			Data.Profile p = d.profile();
			if (!p.hours.containsKey(pending)) { p.hours.put(pending, 0.0); dirty = true; }
			log(">>> /" + command);
		} else if (d.stopCommands.contains(parts[0])) {
			mode = null;
			pending = null;
		}
	}

	public static void onGameMessage(Component message) {
		if (System.currentTimeMillis() < logUntil) log(message.getString());
	}

	private static void log(String line) {
		try {
			Files.createDirectories(DIR);
			Files.writeString(DEBUG_LOG, System.currentTimeMillis() + " " + line + "\n",
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (Exception ignored) {}
	}

	// ---------- tick ----------

	public static void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (online && mode != null) {
			long dt = now - last;
			if (dt > 0 && dt < 5000) d.playMillis.merge(mode, dt, Long::sum);
		}
		last = now;
		ticks++;
		if (ticks % 20 == 0) check();
		if (ticks % 100 == 0) pollDiscordFile();
		if (ticks % 600 == 0 && dirty) save();
	}

	// ---------- discord counters ----------

	public static void addDiscord(int delta) { d.discordCount = Math.max(0, d.discordCount + delta); dirty = true; }
	public static void addHelp(int delta) { d.helpCount = Math.max(0, d.helpCount + delta); dirty = true; }

	/** A companion script (or you) can write {"messages": 123, "help": 10} to config/bmcquota/discord.json. */
	private static void pollDiscordFile() {
		try {
			if (!Files.exists(DISCORD_FILE)) return;
			long m = Files.getLastModifiedTime(DISCORD_FILE).toMillis();
			if (m == discordModified) return;
			discordModified = m;
			JsonObject o = JsonParser.parseString(Files.readString(DISCORD_FILE)).getAsJsonObject();
			if (o.has("messages")) d.discordCount = o.get("messages").getAsInt();
			if (o.has("help")) d.helpCount = o.get("help").getAsInt();
			dirty = true;
		} catch (Exception ignored) {}
	}

	// ---------- quotas ----------

	public static void resetWeek() {
		d.playMillis.clear();
		d.discordCount = 0;
		d.helpCount = 0;
		d.notified.clear();
		d.weekStart = System.currentTimeMillis();
		save();
	}

	public static boolean modeDone(Data.Profile p, String key) {
		double h = p.hours.getOrDefault(key, 0.0);
		return h > 0 && d.playMillis.getOrDefault(key, 0L) >= h * 3_600_000d;
	}

	private static void check() {
		Data.Profile p = d.profile();
		boolean any = false, all = true;
		for (Map.Entry<String, Double> e : p.hours.entrySet()) {
			if (e.getValue() <= 0) continue;
			any = true;
			if (modeDone(p, e.getKey())) {
				fire("m:" + p.name + ":" + e.getKey(), Data.pretty(e.getKey()) + " quota complete!", "Playtime target reached");
			} else all = false;
		}
		if (p.discord > 0) {
			any = true;
			if (d.discordCount >= p.discord) fire("d:" + p.name, "Discord quota complete!", d.discordCount + " messages sent");
			else all = false;
		}
		if (p.help > 0) {
			any = true;
			if (d.helpCount >= p.help) fire("h:" + p.name, "Public help quota complete!", d.helpCount + " messages sent");
			else all = false;
		}
		if (any && all) fire("all:" + p.name, "All quotas complete!", "Nice work, " + d.rankName);
	}

	private static void fire(String key, String title, String message) {
		if (!d.notified.add(key)) return;
		dirty = true;
		Minecraft mc = Minecraft.getInstance();
		mc.gui.toastManager().addToast(new SystemToast(SystemToast.SystemToastId.NARRATOR_TOGGLE,
				Component.literal(title).withStyle(ChatFormatting.GOLD), Component.literal(message)));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0F));
	}
}
