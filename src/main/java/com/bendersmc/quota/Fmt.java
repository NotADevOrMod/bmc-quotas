package com.bendersmc.quota;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsing / formatting of typed quota values. */
public final class Fmt {
	private static final Pattern HM = Pattern.compile("^(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?$");
	private static final Pattern DOT = Pattern.compile("^(\\d+)([:.])(\\d{1,2})$");

	private Fmt() {}

	/**
	 * Hours the way you'd say them: "1.30" = 1h 30m, "1:30", "1h30m", "90m", "2" = 2h, "" = 0.
	 * One digit after the dot is padded ("1.3" = 1.30). Returns null if the text isn't valid.
	 */
	public static Double parseHours(String raw) {
		String s = raw.trim().toLowerCase();
		if (s.isEmpty()) return 0.0;
		try {
			Matcher m = HM.matcher(s);
			if (m.matches() && (m.group(1) != null || m.group(2) != null)) {
				int h = m.group(1) == null ? 0 : Integer.parseInt(m.group(1));
				int mi = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
				return h + mi / 60.0;
			}
			m = DOT.matcher(s);
			if (m.matches()) {
				int h = Integer.parseInt(m.group(1));
				String f = m.group(3);
				if (m.group(2).equals(".") && f.length() == 1) f = f + "0";
				int mi = Integer.parseInt(f);
				if (mi > 59) return null;
				return h + mi / 60.0;
			}
			if (s.matches("\\d+")) return Double.valueOf(Integer.parseInt(s));
		} catch (NumberFormatException ignored) {}
		return null;
	}

	/** Whole number of messages (empty = 0), or null if invalid. */
	public static Double parseCount(String raw) {
		String s = raw.trim();
		if (s.isEmpty()) return 0.0;
		if (!s.matches("\\d{1,7}")) return null;
		return Double.valueOf(Integer.parseInt(s));
	}

	/** Hours -> the text shown in the edit box: 1.5 -> "1.30", 5 -> "5". */
	public static String edit(double hours) {
		long total = Math.round(hours * 60);
		long h = total / 60, m = total % 60;
		return m == 0 ? String.valueOf(h) : h + "." + (m < 10 ? "0" + m : String.valueOf(m));
	}

	/** Hours -> readable: "1h 30m", "5h", "30m". */
	public static String pretty(double hours) {
		long total = Math.round(hours * 60);
		long h = total / 60, m = total % 60;
		if (h > 0 && m > 0) return h + "h " + m + "m";
		if (h > 0) return h + "h";
		return m + "m";
	}
}
