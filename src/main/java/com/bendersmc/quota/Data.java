package com.bendersmc.quota;

import java.util.*;

/** Everything that gets saved to config/bmcquota/data.json */
public final class Data {
	public static final String[] DEFAULT_MODES = {"newsurvival", "newsurvival2", "skyblock", "afk"};

	public List<Profile> profiles = new ArrayList<>();
	public int active = 0;

	public String rankName = "Moderator";
	public String rankColor = "AQUA"; // ChatFormatting enum name
	public String rankImage = "";

	/** Playtime only counts while the connected server address contains this. */
	public String serverMatch = "bendersmc";
	/** Commands (without the slash) that mean "I left my gamemode". */
	public List<String> stopCommands = new ArrayList<>(List.of("lobby", "hub", "l"));

	public Map<String, Long> playMillis = new LinkedHashMap<>();
	public int discordCount = 0;
	public int helpCount = 0;
	public long weekStart = System.currentTimeMillis();
	public Set<String> notified = new HashSet<>();

	/** One "soup" (supervisor) and the quotas they set. */
	public static final class Profile {
		public String name = "Soup 1";
		public String mark = "";
		/** gamemode id -> required hours (0 = no quota) */
		public Map<String, Double> hours = new LinkedHashMap<>();
		public int discord = 0; // total Discord messages (0 = no quota)
		public int help = 0;    // optional public-help messages (0 = no quota)

		public Profile() {}

		public Profile(String name) {
			this.name = name;
			for (String m : DEFAULT_MODES) hours.put(m, 5.0); // placeholder, adjust in the menu
		}
	}

	public Profile profile() {
		if (profiles.isEmpty()) profiles.add(new Profile("Soup 1"));
		active = Math.max(0, Math.min(active, profiles.size() - 1));
		return profiles.get(active);
	}

	public static String pretty(String key) {
		if (key == null || key.isEmpty()) return "-";
		switch (key) {
			case "newsurvival": return "NewSurvival";
			case "newsurvival2": return "NewSurvival2";
			case "skyblock": return "Skyblock";
			case "afk": return "AFK";
			default: return Character.toUpperCase(key.charAt(0)) + key.substring(1);
		}
	}
}
