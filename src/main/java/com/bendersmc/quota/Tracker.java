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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Playtime tracking, gamemode detection, quota checks, saving. All calls happen on the client thread. */
public final class Tracker {
	public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("bmcquota");
	public static final String NONE = "none";
	private static final Path FILE = DIR.resolve("data.json");
	private static final Path DISCORD_FILE = DIR.resolve("discord.json");
	private static final Path DEBUG_LOG = DIR.resolve("debug.log");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern SENDING = Pattern.compile("(?i)sending you to\\s+(.+?)\\s+now");

	public static Data d = new Data();
	/** Gamemode currently being counted (null = not counting). */
	public static String mode;
	public static boolean online;

	// detection state
	private static String pending;          // mode decided from the chat message, applied on the next JOIN
	private static long pendingAt;
	private static String joinqMode;        // what you typed after /joinq
	private static long joinqAt;
	private static int joins;               // JOINs on this connection (first one = initial login)
	private static long logUntil;

	// "which mode is this?" popup state
	private static boolean askDue;
	private static long askAt;
	private static String askKey;           // normalised server name to remember the answer for (may be null)

	private static long last, discordModified;
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
		if (d.serverModes == null) d.serverModes = new java.util.LinkedHashMap<>();
		d.profile();
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

	// ---------- gamemode detection ----------

	private static String norm(String s) { return s.toLowerCase().replaceAll("[^a-z0-9]", ""); }

	private static void apply(String m) {
		mode = (m == null || NONE.equals(m)) ? null : m;
		if (mode != null) d.profile().hours.putIfAbsent(mode, 0.0);
		last = System.currentTimeMillis();
	}

	/** Maps a server name to a known mode id, or NONE for lobbies; null if unknown. */
	private static String matchMode(String key) {
		if (key.contains("lobby") || key.contains("hub")) return NONE;
		for (String k : d.profile().hours.keySet()) if (norm(k).equals(key)) return k;
		for (String k : Data.DEFAULT_MODES) if (norm(k).equals(key)) return k;
		return null;
	}

	public static void onJoin(Minecraft mc) {
		var server = mc.getCurrentServer();
		String ip = server == null ? "" : String.valueOf(server.ip).toLowerCase();
		online = ip.contains(d.serverMatch.toLowerCase());
		long now = System.currentTimeMillis();
		last = now;
		logUntil = now + 20_000;
		log("JOIN server=" + ip);
		if (!online) return;
		joins++;

		if (pending == null && joinqMode != null && now - joinqAt < 120_000) {
			pending = joinqMode;
			pendingAt = now;
		}
		if (pending != null && now - pendingAt < 120_000) {
			apply(pending);
			pending = null;
			joinqMode = null;
			askDue = false;
			askKey = null;
			return;
		}
		pending = null;
		mode = null;
		if (askKey != null) return;   // we already know there is a named server to ask about
		if (joins > 1) {              // switched servers but nothing told us where: ask
			askDue = true;
			askAt = now + 4000;
		}
	}

	public static void onDisconnect() {
		online = false;
		mode = null;
		pending = null;
		joinqMode = null;
		joins = 0;
		dismissAsk();
		save();
	}

	/** Called with the command text WITHOUT the leading slash. */
	public static void onCommand(String command) {
		String[] parts = command.trim().toLowerCase().split("\\s+");
		if (parts.length == 0) return;
		long now = System.currentTimeMillis();
		if (parts[0].equals("joinq") && parts.length >= 2) {
			joinqMode = parts[1];
			joinqAt = now;
			logUntil = now + 20_000;
			Data.Profile p = d.profile();
			if (!p.hours.containsKey(joinqMode)) { p.hours.put(joinqMode, 0.0); dirty = true; }
			log(">>> /" + command);
		} else if (d.stopCommands.contains(parts[0])) {
			mode = null;
			pending = null;
			joinqMode = null;
		}
	}

	public static void onGameMessage(Component message) {
		String text = message.getString();
		long now = System.currentTimeMillis();
		Matcher m = SENDING.matcher(text);
		boolean matched = m.find();
		if (matched || now < logUntil) log(text);
		if (!matched) return;

		String name = m.group(1).replaceAll("[\\[\\]]", "").trim();
		String key = norm(name);
		if (key.isEmpty()) return;
		logUntil = now + 20_000;

		String target = d.serverModes.get(key);
		if (target == null) target = matchMode(key);
		if (target == null && joinqMode != null && now - joinqAt < 300_000) {
			target = joinqMode;               // learn: this server is what /joinq took us to
			d.serverModes.put(key, target);
			dirty = true;
			log("learned " + key + " -> " + target);
		}
		joinqMode = null;
		if (target != null) {
			pending = target;
			pendingAt = now;
			askDue = false;
			askKey = null;
		} else {
			pending = null;
			askKey = key;
			askNameValue = name;
			askDue = true;
			askAt = now + 4000;
		}
	}

	private static String askNameValue;

	// ---------- "which mode is this?" popup ----------

	/** Called by the popup when you pick a mode (or "none"). */
	public static void answer(String choice) {
		apply(choice);
		if (askKey != null) d.serverModes.put(askKey, choice);
		askDue = false;
		askKey = null;
		askNameValue = null;
		dirty = true;
	}

	public static void dismissAsk() {
		askDue = false;
		askKey = null;
		askNameValue = null;
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
		if (askDue && online && now >= askAt) {
			if (mc.gui.screen() == null) {
				askDue = false;
				mc.gui.setScreen(new AskModeScreen(askNameValue));
			} else if (now > askAt + 60_000) {
				dismissAsk();
			}
		}
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
			String key = "m:" + p.name + ":" + e.getKey();
			if (modeDone(p, e.getKey())) {
				fire(key, Data.pretty(e.getKey()) + " quota complete!", "Playtime target reached");
			} else {
				d.notified.remove(key); // quota was raised: allow the toast again when it's met
				all = false;
			}
		}
		if (p.discord > 0) {
			any = true;
			if (d.discordCount >= p.discord) fire("d:" + p.name, "Discord quota complete!", d.discordCount + " messages sent");
			else { d.notified.remove("d:" + p.name); all = false; }
		}
		if (p.help > 0) {
			any = true;
			if (d.helpCount >= p.help) fire("h:" + p.name, "Public help quota complete!", d.helpCount + " messages sent");
			else { d.notified.remove("h:" + p.name); all = false; }
		}
		if (any && all) fire("all:" + p.name, "All quotas complete!", "Nice work, " + d.rankName);
		else d.notified.remove("all:" + p.name);
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
