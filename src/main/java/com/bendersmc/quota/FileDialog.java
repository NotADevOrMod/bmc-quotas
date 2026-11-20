package com.bendersmc.quota;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * "Browse for image" dialog. Runs on its own thread so the game doesn't freeze, and tries in order:
 * the OS's own picker (PowerShell on Windows, osascript on macOS, zenity/kdialog on Linux),
 * then LWJGL's tinyfd. If none works the screen falls back to drag-and-drop.
 */
public final class FileDialog {
	public interface Callback {
		/** path = chosen file (null if cancelled); unavailable = no file picker could be opened at all. */
		void done(String path, boolean unavailable);
	}

	private record Result(String path, boolean unavailable) {}

	private static final String WIN_SCRIPT =
			"[Console]::OutputEncoding=[System.Text.Encoding]::UTF8; "
			+ "Add-Type -AssemblyName System.Windows.Forms; "
			+ "$o=New-Object System.Windows.Forms.Form; $o.TopMost=$true; "
			+ "$f=New-Object System.Windows.Forms.OpenFileDialog; $f.Title='Choose an image'; "
			+ "$f.Filter='Images|*.png;*.jpg;*.jpeg;*.gif|All files|*.*'; "
			+ "if($f.ShowDialog($o) -eq [System.Windows.Forms.DialogResult]::OK){ $f.FileName }";
	private static final String MAC_SCRIPT =
			"POSIX path of (choose file with prompt \"Choose an image\" of type {\"public.image\"})";

	private FileDialog() {}

	public static void pickImageAsync(Callback cb) {
		Thread t = new Thread(() -> {
			Result r;
			try {
				r = pick();
			} catch (Throwable e) {
				r = new Result(null, true);
			}
			cb.done(r.path(), r.unavailable());
		}, "bmcquota-filepicker");
		t.setDaemon(true);
		t.start();
	}

	private static Result pick() {
		String os = System.getProperty("os.name", "").toLowerCase();
		Result r = null;
		if (os.contains("win")) {
			r = run(List.of("powershell", "-NoProfile", "-STA", "-Command", WIN_SCRIPT));
		} else if (os.contains("mac")) {
			r = run(List.of("osascript", "-e", MAC_SCRIPT));
		} else {
			r = run(List.of("zenity", "--file-selection", "--title=Choose an image",
					"--file-filter=Images | *.png *.jpg *.jpeg *.gif"));
			if (r == null) r = run(List.of("kdialog", "--getopenfilename",
					System.getProperty("user.home", "."), "*.png *.jpg *.jpeg *.gif|Images"));
		}
		return r != null ? r : tiny();
	}

	/** Runs a picker command and waits for it. Returns null if the command couldn't be started. */
	private static Result run(List<String> cmd) {
		try {
			Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
			p.getOutputStream().close();
			String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
			p.waitFor();
			if (out.isEmpty()) return new Result(null, false); // cancelled
			return new Result(out.lines().findFirst().orElse(out).trim(), false);
		} catch (Exception e) {
			return null;
		}
	}

	private static Result tiny() {
		try (MemoryStack st = MemoryStack.stackPush()) {
			PointerBuffer filters = st.mallocPointer(4);
			for (String s : new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif"}) filters.put(st.UTF8(s));
			filters.flip();
			Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			Object r = c.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
					PointerBuffer.class, CharSequence.class, boolean.class)
					.invoke(null, "Choose an image", "", filters, "Image files", false);
			return new Result(r == null ? null : r.toString(), false);
		} catch (Throwable t) {
			return new Result(null, true);
		}
	}
}
