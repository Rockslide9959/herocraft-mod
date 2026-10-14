package com.projecthero.mod.client.flight;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * v0.16.2: the local player's flight preferences from the {@code ,} Flight Mode menu, kept per player in
 * {@code config/projecthero_flight.json}: Regular or Physics flight, and the Physics options. Client-only -- the server
 * never needs the mode (every flight is client-simulated and server-accepted either way); only the resulting roll is
 * shared, for other players to see ({@code FlightAttitude}).
 */
public final class FlightSettings {
	public static final float MIN_ROLL_SENSITIVITY = 0.25f;
	public static final float MAX_ROLL_SENSITIVITY = 2.0f;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data;

	/** The file's shape. Defaults are the Do a Barrel Roll defaults: mouse X rolls, A / D yaw, gentle auto-level. */
	private static final class Data {
		boolean physics = false;
		boolean mouseXRolls = true;
		float rollSensitivity = 1.0f;
		boolean invertRoll = false;
		boolean autoLevel = true;
		boolean cameraRoll = true;
	}

	private FlightSettings() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_flight.json");
	}

	private static Data data() {
		if (data == null) {
			data = new Data();
			Path f = file();
			if (Files.exists(f)) {
				try (Reader r = Files.newBufferedReader(f)) {
					Data read = GSON.fromJson(r, Data.class);
					if (read != null) {
						data = read;
					}
				} catch (Exception e) {
					ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read {}: {}", f, e.toString());
				}
			}
			data.rollSensitivity = clampSensitivity(data.rollSensitivity);
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

	private static float clampSensitivity(float v) {
		return Float.isFinite(v) ? Math.max(MIN_ROLL_SENSITIVITY, Math.min(MAX_ROLL_SENSITIVITY, v)) : 1.0f;
	}

	public static boolean physics() {
		return data().physics;
	}

	public static void setPhysics(boolean on) {
		data().physics = on;
		save();
	}

	/** True: mouse X rolls and A / D yaw (Do a Barrel Roll). False: mouse X yaws and A / D roll. */
	public static boolean mouseXRolls() {
		return data().mouseXRolls;
	}

	public static void setMouseXRolls(boolean on) {
		data().mouseXRolls = on;
		save();
	}

	public static float rollSensitivity() {
		return data().rollSensitivity;
	}

	public static void setRollSensitivity(float v) {
		data().rollSensitivity = clampSensitivity(v);
		save();
	}

	public static boolean invertRoll() {
		return data().invertRoll;
	}

	public static void setInvertRoll(boolean on) {
		data().invertRoll = on;
		save();
	}

	public static boolean autoLevel() {
		return data().autoLevel;
	}

	public static void setAutoLevel(boolean on) {
		data().autoLevel = on;
		save();
	}

	/** The view rolls with the flier (first person and third-person back). Off keeps the horizon level. */
	public static boolean cameraRoll() {
		return data().cameraRoll;
	}

	public static void setCameraRoll(boolean on) {
		data().cameraRoll = on;
		save();
	}
}
