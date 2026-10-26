package com.projecthero.mod.client.greenlantern;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.network.GreenLanternCuePayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * v0.17.1 (user-approved revamp): the client half of the Power Ring's voice ({@code GreenLanternRingVoice}) -- JARVIS's
 * delivery style in Lantern green. A line arrives as a {@link GreenLanternCuePayload} ({@link GreenLanternCuePayload#VOICE})
 * and is typed out in a small subtitle box in the top-left corner of the screen (clear of the GL HUD
 * in the bottom right, the combo popup, the boss bars and the chat), with a soft chime; it fades after a few seconds.
 * Never wider than {@link #MAX_W} px, long lines wrap. Combo cues go on to {@link GreenLanternComboPopup}.
 *
 * <p>Mute: the "Ring Voice" switch in the Shift+N suits screen; saved per client in {@code config/projecthero_ring_voice.json}.
 * Default on.
 */
public final class GreenLanternRingVoiceClient {
	public static final int MAX_W = 200;
	private static final long SHOW_MS = 4200L;
	private static final long FADE_MS = 700L;
	private static final float CHARS_PER_MS = 0.055f;
	private static final int GREEN = 0x5CFF8E;
	private static final int PALE = 0xD8FFE4;

	private static Component current;
	private static long shownAt;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final class Settings {
		boolean ringVoice = true;
	}

	private static Settings settings;

	private GreenLanternRingVoiceClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(GreenLanternCuePayload.TYPE,
				(payload, context) -> context.client().execute(() -> receive(payload)));
		HudRenderCallback.EVENT.register(GreenLanternRingVoiceClient::render);
		HudRenderCallback.EVENT.register(GreenLanternComboPopup::render);
	}

	public static boolean enabled() {
		return settings().ringVoice;
	}

	public static void setEnabled(boolean on) {
		settings().ringVoice = on;
		save();
		if (!on) {
			current = null;
		}
	}

	private static void receive(GreenLanternCuePayload payload) {
		if (payload.kind() == GreenLanternCuePayload.COMBO) {
			GreenLanternComboPopup.show(payload.key());
			return;
		}
		if (!enabled()) {
			return;
		}
		String key = "message.projecthero.green_lantern.ring_voice." + payload.key();
		show(payload.arg().isEmpty() ? Component.translatable(key) : Component.translatable(key, Component.translatable(payload.arg())));
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.3f, 1.9f);
			mc.player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.12f, 1.5f);
		}
	}

	/** Show a line now (also used by the visual-check harness). */
	public static void show(Component line) {
		current = line;
		shownAt = System.currentTimeMillis();
	}

	private static void render(GuiGraphics g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (current == null || mc.options.hideGui || mc.player == null) {
			return;
		}
		long age = System.currentTimeMillis() - shownAt;
		if (age > SHOW_MS) {
			current = null;
			return;
		}
		float alpha = age < 150 ? Math.max(0.1f, age / 150f) : age > SHOW_MS - FADE_MS ? Math.max(0.05f, (SHOW_MS - age) / (float) FADE_MS) : 1f;
		Font font = mc.font;
		Component label = Component.translatable("message.projecthero.green_lantern.ring_voice.prefix")
				.withStyle(s -> s.withColor(GREEN).withBold(true));
		String full = current.getString();
		int shown = Math.min(full.length(), Math.max(1, (int) (age * CHARS_PER_MS)));
		Component typed = Component.empty().append(label).append(" ").append(Component.literal(full.substring(0, shown)));
		Component whole = Component.empty().append(label).append(" ").append(Component.literal(full));
		int w = Math.min(MAX_W, g.guiWidth() / 2 - 12);
		List<FormattedCharSequence> lines = font.split(typed, w - 10);
		List<FormattedCharSequence> fullLines = font.split(whole, w - 10); // sized for the whole line: no jitter as it types
		int boxW = 0;
		for (FormattedCharSequence l : fullLines) {
			boxW = Math.max(boxW, font.width(l));
		}
		boxW += 11;
		int boxH = 5 + fullLines.size() * 10;
		int x = 6;
		// top-left corner: clear of the combo popup above the crosshair, the boss bars, the chat and the GL HUD
		int y = 6;
		g.fill(x, y, x + boxW, y + boxH, argb(0x041A0C, 0.70f * alpha));
		g.fill(x, y, x + boxW, y + 1, argb(GREEN, 0.55f * alpha));
		g.fill(x, y + boxH - 1, x + boxW, y + boxH, argb(GREEN, 0.25f * alpha));
		// the left edge pulses softly like the ring's gem
		float pulse = 0.75f + 0.25f * Mth.sin(age / 140f);
		g.fill(x, y, x + 2, y + boxH, argb(GREEN, alpha * pulse));
		int ly = y + 3;
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, x + 6, ly, argb(PALE, alpha), true);
			ly += 10;
		}
	}

	static int argb(int rgb, float alpha) {
		int a = Mth.clamp(Math.round(alpha * 255f), 4, 255);
		return (a << 24) | (rgb & 0xFFFFFF);
	}

	// ---------------- settings file ----------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_ring_voice.json");
	}

	private static Settings settings() {
		if (settings == null) {
			settings = new Settings();
			Path f = file();
			if (Files.exists(f)) {
				try (Reader r = Files.newBufferedReader(f)) {
					Settings read = GSON.fromJson(r, Settings.class);
					if (read != null) {
						settings = read;
					}
				} catch (Exception e) {
					ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {}: {}", f, e.toString());
				}
			}
		}
		return settings;
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file())) {
				GSON.toJson(settings(), w);
			}
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not save {}: {}", file(), e.toString());
		}
	}
}
