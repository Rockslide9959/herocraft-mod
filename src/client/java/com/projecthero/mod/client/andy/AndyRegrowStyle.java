package com.projecthero.mod.client.andy;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * v0.17.3 (user: "do both styles and give the player the option to pick between which style they would like to have"): which
 * look Andy's regrowth is drawn in on this computer -- {@code 3d} (the default: bone pushing out, muscle thickening outward,
 * skin closing over; {@link AndyRegrow}) or {@code stepped} (bone-and-blood surface revealed frame by frame, then raw flesh,
 * then skin; {@link AndyRegrowStepped}). A purely visual, per-viewer choice (it changes how you see every Andy, yours and
 * other players'), kept in {@code config/projecthero_andy.json} and set with the client command {@code /andystyle 3d|stepped}
 * ({@code /andystyle} alone says which is on). A client command rather than a server one: the server never needs it.
 */
public final class AndyRegrowStyle {
	public enum Style {
		THREE_D, STEPPED
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data;

	private static final class Data {
		String regrowStyle = "3d";
	}

	private AndyRegrowStyle() {
	}

	/** Set only by the client screenshot harness: overrides the saved choice. */
	public static volatile Style forced;

	public static Style style() {
		if (forced != null) {
			return forced;
		}
		return "stepped".equals(data().regrowStyle) ? Style.STEPPED : Style.THREE_D;
	}

	public static boolean stepped() {
		return style() == Style.STEPPED;
	}

	public static void set(Style s) {
		data().regrowStyle = s == Style.STEPPED ? "stepped" : "3d";
		save();
	}

	public static void initialize() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(ClientCommandManager.literal("andystyle")
				.executes(ctx -> {
					ctx.getSource().sendFeedback(current());
					return 1;
				})
				.then(ClientCommandManager.literal("3d").executes(ctx -> {
					set(Style.THREE_D);
					ctx.getSource().sendFeedback(current());
					return 1;
				}))
				.then(ClientCommandManager.literal("stepped").executes(ctx -> {
					set(Style.STEPPED);
					ctx.getSource().sendFeedback(current());
					return 1;
				}))));
	}

	private static Component current() {
		return Component.translatable("message.projecthero.andy.regrow_style",
				Component.translatable("message.projecthero.andy.regrow_style." + (stepped() ? "stepped" : "3d"))).withStyle(ChatFormatting.DARK_RED);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_andy.json");
	}

	private static Data data() {
		if (data == null) {
			data = new Data();
			Path f = file();
			if (Files.exists(f)) {
				try (Reader r = Files.newBufferedReader(f)) {
					Data read = GSON.fromJson(r, Data.class);
					if (read != null && read.regrowStyle != null) {
						data = read;
					}
				} catch (Exception e) {
					ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {}: {}", f, e.toString());
				}
			}
		}
		return data;
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			try (Writer w = Files.newBufferedWriter(file())) {
				GSON.toJson(data(), w);
			}
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not save {}: {}", file(), e.toString());
		}
	}
}
