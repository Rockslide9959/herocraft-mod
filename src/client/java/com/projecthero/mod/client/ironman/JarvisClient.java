package com.projecthero.mod.client.ironman;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.client.gui.IronManGui;
import com.projecthero.mod.network.IronManJarvisPayload;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

/**
 * v0.14.29 (agent F): the client half of JARVIS ({@code JarvisDialogue}). A line arrives as an
 * {@link IronManJarvisPayload} and is typed out in a small wrapped speech box above the hotbar for a few seconds --
 * cyan "J.A.R.V.I.S." for the voice, amber "SYSTEM" for the Mark 1's crude readout. Never wider than
 * {@link #MAX_W} px; long lines wrap.
 *
 * <p>{@code /jarvis on|off} (client command) mutes it; the choice is saved in {@code config/projecthero_client.json}.
 * Default on.
 */
public final class JarvisClient {
	public static final int MAX_W = 220;
	private static final long SHOW_MS = 4500L;
	private static final long FADE_MS = 600L;
	private static final float CHARS_PER_MS = 0.06f;

	private static Component current;
	private static boolean currentCrude;
	private static long shownAt;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** {@code config/projecthero_client.json}. */
	private static final class Settings {
		boolean jarvisVoice = true;
	}

	private static Settings settings;

	private JarvisClient() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(IronManJarvisPayload.TYPE,
				(payload, context) -> context.client().execute(() -> receive(payload)));
		HudRenderCallback.EVENT.register(JarvisClient::render);
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
				ClientCommandManager.literal("jarvis")
						.executes(ctx -> {
							ctx.getSource().sendFeedback(Component.translatable(enabled()
									? "message.projecthero.ironman.jarvis.toggle_on" : "message.projecthero.ironman.jarvis.toggle_off")
									.withStyle(ChatFormatting.AQUA));
							return 1;
						})
						.then(ClientCommandManager.literal("on").executes(ctx -> {
							setEnabled(true);
							ctx.getSource().sendFeedback(Component.translatable("message.projecthero.ironman.jarvis.toggle_on")
									.withStyle(ChatFormatting.AQUA));
							return 1;
						}))
						.then(ClientCommandManager.literal("off").executes(ctx -> {
							setEnabled(false);
							ctx.getSource().sendFeedback(Component.translatable("message.projecthero.ironman.jarvis.toggle_off")
									.withStyle(ChatFormatting.AQUA));
							return 1;
						}))));
	}

	public static boolean enabled() {
		return settings().jarvisVoice;
	}

	public static void setEnabled(boolean on) {
		settings().jarvisVoice = on;
		save();
		if (!on) {
			current = null;
		}
	}

	private static void receive(IronManJarvisPayload payload) {
		if (!enabled()) {
			return;
		}
		show(Component.translatable("message.projecthero.ironman.jarvis." + payload.line()), payload.crude());
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.playSound(payload.crude() ? SoundEvents.NOTE_BLOCK_BIT.value() : SoundEvents.AMETHYST_BLOCK_CHIME,
					0.35f, payload.crude() ? 0.7f : 1.6f);
		}
	}

	/** Show a line now (also used by the visual-check harness). */
	public static void show(Component line, boolean crude) {
		current = line;
		currentCrude = crude;
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
		float alpha = age > SHOW_MS - FADE_MS ? Math.max(0.06f, (SHOW_MS - age) / (float) FADE_MS) : 1f;
		Font font = mc.font;
		String full = current.getString();
		int shown = Math.min(full.length(), Math.max(1, (int) (age * CHARS_PER_MS)));
		Component typed = Component.literal(full.substring(0, shown));
		int w = Math.min(MAX_W, g.guiWidth() - 16);
		List<FormattedCharSequence> lines = font.split(typed, w - 8);
		List<FormattedCharSequence> fullLines = font.split(current, w - 8); // box sized for the whole line (no jitter)
		int boxW = 0;
		for (FormattedCharSequence l : fullLines) {
			boxW = Math.max(boxW, font.width(l));
		}
		String label = Component.translatable(currentCrude ? "message.projecthero.ironman.jarvis.prefix_system"
				: "message.projecthero.ironman.jarvis.prefix").getString();
		boxW = Math.max(boxW, font.width(label)) + 8;
		int boxH = 12 + fullLines.size() * 10 + 3;
		int x = (g.guiWidth() - boxW) / 2;
		int y = g.guiHeight() - 92 - boxH;
		int accent = currentCrude ? IronManGui.AMBER : IronManGui.CYAN;
		int text = currentCrude ? IronManGui.AMBER : IronManGui.TEXT;
		g.fill(x, y, x + boxW, y + boxH, IronManGui.alpha(currentCrude ? 0xFF140C04 : 0xFF041826, 0.72f * alpha));
		g.renderOutline(x, y, boxW, boxH, IronManGui.alpha(accent, 0.8f * alpha));
		g.fill(x, y, x + 2, y + boxH, IronManGui.alpha(accent, alpha));
		g.drawString(font, label, x + 5, y + 3, IronManGui.alpha(accent, alpha), false);
		int ly = y + 14;
		for (FormattedCharSequence l : lines) {
			g.drawString(font, l, x + 5, ly, IronManGui.alpha(text, alpha), false);
			ly += 10;
		}
	}

	// ---------------- settings file ----------------

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_client.json");
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
