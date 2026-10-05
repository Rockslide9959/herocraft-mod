package com.projecthero.mod.client.render;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * v0.14.23: hand-tuned placement for the {@link HandRing} rings, one per ring and per view (third / first person), edited
 * live in {@code RingEditorScreen} ({@code /ringeditor}) and saved to {@code config/projecthero_ring_placement.json}.
 *
 * <p>Each placement is applied in the arm's pixel space on top of the built-in ring position: the ring is rotated and
 * scaled about its own centre (the gem on the knuckles), then moved by the offset. All zeros / scale 1 = the built-in
 * look. {@link #DEFAULTS} holds the shipped values (bake tuned values in there to make them everyone's default).
 */
public final class RingPlacement {
	public enum View {
		THIRD_PERSON, FIRST_PERSON
	}

	/** One ring in one view: offset in arm pixels, rotation in degrees, uniform scale. */
	public static final class Placement {
		public float x, y, z;
		public float pitch, yaw, roll;
		public float scale = 1f;

		public Placement() {
		}

		public Placement(float x, float y, float z, float pitch, float yaw, float roll, float scale) {
			this.x = x;
			this.y = y;
			this.z = z;
			this.pitch = pitch;
			this.yaw = yaw;
			this.roll = roll;
			this.scale = scale;
		}

		public Placement copy() {
			return new Placement(x, y, z, pitch, yaw, roll, scale);
		}

		public void set(Placement o) {
			x = o.x;
			y = o.y;
			z = o.z;
			pitch = o.pitch;
			yaw = o.yaw;
			roll = o.roll;
			scale = o.scale;
		}

		boolean isIdentity() {
			return x == 0 && y == 0 && z == 0 && pitch == 0 && yaw == 0 && roll == 0 && scale == 1f;
		}
	}

	/** The rings that can be placed, by {@link HandRing.Palette#id()}. */
	public static final String[] RINGS = { HandRing.GREEN_LANTERN.id(), HandRing.FLASH.id() };

	/** Shipped defaults per ring id and view; anything missing is the built-in look. */
	private static final Map<String, Placement[]> DEFAULTS = new LinkedHashMap<>();

	static {
		// v0.14.23: tuned in the ring editor (third person, first person)
		DEFAULTS.put(HandRing.GREEN_LANTERN.id(), new Placement[] {
				new Placement(-2.4f, 0.9f, 2.0f, 0f, 90f, 0f, 1f),
				new Placement(-2.5f, 0.5f, 2.1f, 0f, 90f, 0f, 1f) });
		DEFAULTS.put(HandRing.FLASH.id(), new Placement[] {
				new Placement(-2.4f, 0.9f, 1.15f, 0f, 90f, 0f, 1f),
				new Placement(-2.5f, 0.5f, 1.2f, 0f, 90f, 0f, 1f) });
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Map<String, Placement[]> VALUES = new LinkedHashMap<>();
	private static boolean loaded;

	private RingPlacement() {
	}

	public static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("projecthero_ring_placement.json");
	}

	public static Placement defaults(String ring, View view) {
		Placement[] d = DEFAULTS.get(ring);
		return d == null ? new Placement() : d[view.ordinal()].copy();
	}

	/** The live (editable) placement for {@code ring} in {@code view}. */
	public static Placement get(String ring, View view) {
		ensureLoaded();
		return VALUES.computeIfAbsent(ring, r -> new Placement[] { defaults(r, View.THIRD_PERSON), defaults(r, View.FIRST_PERSON) })[view.ordinal()];
	}

	public static View currentView() {
		return HandRing.firstPerson ? View.FIRST_PERSON : View.THIRD_PERSON;
	}

	/**
	 * Applies {@code ring}'s placement for the view being drawn. {@code pose} must be in arm pixels (already scaled by
	 * 1/16); {@code px, py, pz} is the pivot -- the ring's centre.
	 */
	public static void apply(PoseStack pose, String ring, float px, float py, float pz) {
		Placement p = get(ring, currentView());
		if (p.isIdentity()) {
			return;
		}
		pose.translate(p.x + px, p.y + py, p.z + pz);
		pose.mulPose(Axis.YP.rotationDegrees(p.yaw));
		pose.mulPose(Axis.XP.rotationDegrees(p.pitch));
		pose.mulPose(Axis.ZP.rotationDegrees(p.roll));
		pose.scale(p.scale, p.scale, p.scale);
		pose.translate(-px, -py, -pz);
	}

	// ---------------------------------------------------------------- persistence

	private static void ensureLoaded() {
		if (!loaded) {
			loaded = true;
			load();
		}
	}

	public static void load() {
		VALUES.clear();
		Path f = file();
		if (!Files.exists(f)) {
			return;
		}
		try (Reader r = Files.newBufferedReader(f)) {
			JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
			for (String ring : root.keySet()) {
				JsonObject o = root.getAsJsonObject(ring);
				Placement[] pair = { defaults(ring, View.THIRD_PERSON), defaults(ring, View.FIRST_PERSON) };
				if (o.has("third_person")) {
					pair[0] = GSON.fromJson(o.get("third_person"), Placement.class);
				}
				if (o.has("first_person")) {
					pair[1] = GSON.fromJson(o.get("first_person"), Placement.class);
				}
				VALUES.put(ring, pair);
			}
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("Could not read {}; using the default ring placement", f, e);
			VALUES.clear();
		}
	}

	public static String toJson() {
		ensureLoaded();
		JsonObject root = new JsonObject();
		for (String ring : RINGS) {
			JsonObject o = new JsonObject();
			o.add("third_person", GSON.toJsonTree(get(ring, View.THIRD_PERSON)));
			o.add("first_person", GSON.toJsonTree(get(ring, View.FIRST_PERSON)));
			root.add(ring, o);
		}
		return GSON.toJson(root);
	}

	public static void save() {
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			try (Writer w = Files.newBufferedWriter(f)) {
				w.write(toJson());
			}
		} catch (IOException e) {
			ProjectHeroMod.LOGGER.warn("Could not save {}", f, e);
		}
	}
}
