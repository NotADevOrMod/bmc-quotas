package com.bendersmc.quota;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

/** Native "browse for image" dialog via LWJGL tinyfd (reflection, so it degrades gracefully). */
public final class FileDialog {
	private FileDialog() {}

	public static String pickImage() {
		try (MemoryStack st = MemoryStack.stackPush()) {
			PointerBuffer filters = st.mallocPointer(4);
			for (String s : new String[]{"*.png", "*.jpg", "*.jpeg", "*.gif"}) filters.put(st.UTF8(s));
			filters.flip();
			Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			Object r = c.getMethod("tinyfd_openFileDialog", CharSequence.class, CharSequence.class,
					PointerBuffer.class, CharSequence.class, boolean.class)
					.invoke(null, "Choose an image", "", filters, "Image files", false);
			return r == null ? null : r.toString();
		} catch (Throwable t) {
			return null; // caller falls back to drag-and-drop
		}
	}
}
