package com.bendersmc.quota;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Text-fitting helpers so nothing spills out of the panels. */
final class Ui {
	private Ui() {}

	static int w(Font f, String s) {
		return f.width(Component.literal(s));
	}

	/** Cuts the text and adds ".." so it is at most maxW wide. */
	static String fit(Font f, String s, int maxW) {
		if (w(f, s) <= maxW) return s;
		int n = s.length();
		while (n > 0 && w(f, s.substring(0, n) + "..") > maxW) n--;
		return s.substring(0, n) + "..";
	}

	/** Word-wraps the text to lines of at most maxW. */
	static List<String> wrap(Font f, String s, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : s.split(" ")) {
			String t = line.length() == 0 ? word : line + " " + word;
			if (line.length() == 0 || w(f, t) <= maxW) {
				line.setLength(0);
				line.append(t);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}
}
