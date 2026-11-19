package com.bendersmc.quota;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Method;

public class BmcQuota implements ClientModInitializer {
	private static KeyMapping openKey;

	@Override
	public void onInitializeClient() {
		Tracker.init();

		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.bmcquota.open", InputConstants.KEY_K,
				KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bmcquota", "main"))));

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			Tracker.tick(mc);
			while (openKey.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new QuotaScreen(null));
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> Tracker.onJoin(mc));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> Tracker.onDisconnect());
		ClientSendMessageEvents.COMMAND.register(Tracker::onCommand);
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> Tracker.onGameMessage(message));
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Tracker.save());

		// "BMC Quotas" button on the title screen and the pause menu (top-left).
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof TitleScreen || screen instanceof PauseScreen) {
				addWidget(screen, Button.builder(Component.literal("BMC Quotas"),
						b -> mc.gui.setScreen(new QuotaScreen(screen))).bounds(6, 6, 80, 20).build());
			}
		});
	}

	/** Screen#addRenderableWidget is protected, so call it reflectively (compile-safe across versions). */
	private static void addWidget(Screen screen, Object widget) {
		try {
			for (Method m : Screen.class.getDeclaredMethods()) {
				if (m.getName().equals("addRenderableWidget") && m.getParameterCount() == 1) {
					m.setAccessible(true);
					m.invoke(screen, widget);
					return;
				}
			}
		} catch (Throwable t) {
			System.err.println("[bmcquota] could not add button: " + t);
		}
	}
}
