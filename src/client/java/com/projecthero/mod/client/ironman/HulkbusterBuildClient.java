package com.projecthero.mod.client.ironman;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.ironman.HulkbusterParts;
import com.projecthero.mod.ironman.entity.HulkbusterPodEntity;
import com.projecthero.mod.ironman.suit.IronManAssemblyPlan;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Player;

/**
 * v0.16.7 (user): "the armour is supposed to build on like how the old armours used to, half of the armour is supposed to
 * come onto the player and then the rest builds on and shifts down". The Hulkbuster body builds on the Stark Gantry way
 * ({@link IronManGantryBuild}), per texel of its own 64x64 body texture:
 * <ul>
 *   <li><b>carried</b> -- what the arriving piece brings: the helmet whole, the TOP half of the torso's base plate (above
 *       the y 27 seam), each GAUNTLET (forearm + hand base), each THIGH TOP (above y 13.5). Visible from the moment the
 *       piece sets off (so the flying piece is only that half);</li>
 *   <li><b>slide</b> -- the rest of each base plate slides out from UNDER the carried half once it has clamped on: the
 *       torso's lower half down out of the chest top, the upper arm up out of the gauntlet, the thigh bottom, shin and
 *       foot down out of the thigh top -- drawn in texture space column by column, the leading edge first (the gantry's
 *       slide columns);</li>
 *   <li><b>outer</b> -- then the raised plate layer builds on texel by texel (torso and legs top-down, arms bottom-up),
 *       plain: no glowing seam, and the suit's lights stay dark until the whole build is done.</li>
 * </ul>
 * Two schedules: the Replacement Pod's mid-air build (each part's own launch / clamp tick, {@link HulkbusterPodEntity}) and
 * stepping into the suit (the Sentry / crate closing round you: every half there at once, legs first). Frames are made
 * lazily per (schedule, tick) and shared by every player at that tick.
 */
public final class HulkbusterBuildClient {
	private static final ResourceLocation GEO = IronManHulkbusterModel.GEO;
	private static final ResourceLocation TEX = IronManHulkbusterModel.TEXTURE;

	static final byte NONE = -1, CARRIED = 0, SLIDE = 1, SLIDE_CAP = 2, OUTER = 3;
	/** Ticks a base plate takes to slide out; when the outer layer starts after the clamp; how long it takes. */
	static final float SLIDE_TICKS = 10f, OUTER_DELAY = 6f, OUTER_TICKS = 14f;

	/** One build timetable: per part, when its carried half sets off and when it clamps on (ticks). */
	record Schedule(String key, float[] launch, float[] attach) {
		float end() {
			float e = 0f;
			for (float a : attach) {
				e = Math.max(e, a + OUTER_DELAY + OUTER_TICKS + 1f);
			}
			return e;
		}
	}

	/** The Replacement Pod's mid-air build: its own launch order and flight time. */
	static final Schedule VERONICA;
	/** Stepping in: every carried half there at once, the legs first, then torso, arms, head. */
	static final Schedule STEP_IN = new Schedule("step", new float[] { 0f, 0f, 0f, 0f, 0f, 0f },
			new float[] { 9f, 5f, 7f, 7f, 0f, 0f });

	static {
		float[] launch = new float[HulkbusterParts.COUNT], attach = new float[HulkbusterParts.COUNT];
		for (int k = 0; k < HulkbusterPodEntity.BUILD_ORDER.length; k++) {
			int part = HulkbusterPodEntity.BUILD_ORDER[k];
			launch[part] = HulkbusterPodEntity.pieceLaunch(k);
			attach[part] = launch[part] + HulkbusterPodEntity.PIECE_FLIGHT;
		}
		VERONICA = new Schedule("pod", launch, attach);
	}

	/** Per texel: kind, part, build position k (0..1), slide column / row. */
	private record Texels(int w, int h, byte[] kind, byte[] part, float[] k, int[] col, int[] row, int[][] cols) {
	}

	private static Texels texels;
	private static boolean texelsTried;
	private static NativeImage source;
	private static final Map<String, ResourceLocation> FRAMES = new HashMap<>();
	private static int made;

	/** Per player id: the start game time of a running build and its schedule; last tick seen without the full suit. */
	private static final Map<Integer, Long> START = new HashMap<>();
	private static final Map<Integer, Schedule> RUNNING = new HashMap<>();
	private static final Map<Integer, Long> LAST_INACTIVE = new HashMap<>();
	private static final Map<Integer, Boolean> WAS_ACTIVE = new HashMap<>();

	private HulkbusterBuildClient() {
	}

	public static void initialize() {
		ClientTickEvents.END_CLIENT_TICK.register(HulkbusterBuildClient::tick);
	}

	/** Leaving a world / a resource reload: drop every frame. */
	public static void clear() {
		Minecraft mc = Minecraft.getInstance();
		FRAMES.values().forEach(mc.getTextureManager()::release);
		FRAMES.clear();
		if (source != null) {
			source.close();
			source = null;
		}
		texels = null;
		texelsTried = false;
		START.clear();
		RUNNING.clear();
		LAST_INACTIVE.clear();
		WAS_ACTIVE.clear();
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			if (!START.isEmpty() || !WAS_ACTIVE.isEmpty()) {
				START.clear();
				RUNNING.clear();
				LAST_INACTIVE.clear();
				WAS_ACTIVE.clear();
			}
			return;
		}
		long now = level.getGameTime();
		for (Player p : level.players()) {
			int id = p.getId();
			observe(p, now);
			Schedule s = RUNNING.get(id);
			Long start = START.get(id);
			if (s != null && start != null && now - start > s.end() + 2) {
				RUNNING.remove(id);
				START.remove(id);
			}
		}
		WAS_ACTIVE.keySet().removeIf(id -> level.getEntity(id) == null);
	}

	/**
	 * Notice a build starting on {@code p}: the pod's mid-air build, or the suit closing round them (stepped in / swapped
	 * on) while this client watched. Called every tick and from the render too, so the very first frame of a fresh suit is
	 * already the first build frame (no flash of the whole suit before the tick catches up).
	 */
	private static void observe(Player p, long now) {
		int id = p.getId();
		HulkbusterPodEntity pod = HulkbusterVeronicaClient.buildPod(p);
		if (pod != null) {
			START.put(id, now - (long) Math.floor(pod.age(0f)));
			RUNNING.put(id, VERONICA);
		}
		boolean active = IronManHulkbusterAnimatable.active(p);
		boolean was = WAS_ACTIVE.getOrDefault(id, active);
		WAS_ACTIVE.put(id, active);
		if (!active) {
			LAST_INACTIVE.put(id, now);
		} else if (!was && pod == null && RUNNING.get(id) != VERONICA) {
			Long seen = LAST_INACTIVE.get(id);
			if (seen != null && now - seen <= 2) {
				START.put(id, now);
				RUNNING.put(id, STEP_IN);
			}
		}
	}

	/** The build {@code p} is in and how far (ticks), or null. */
	private static float age(Player p, float partial, Schedule[] out) {
		Schedule s = RUNNING.get(p.getId());
		Long start = START.get(p.getId());
		if (s == null || start == null || p.level() == null) {
			return Float.NaN;
		}
		HulkbusterPodEntity pod = s == VERONICA ? HulkbusterVeronicaClient.buildPod(p) : null;
		float age = pod != null ? pod.age(partial) : p.level().getGameTime() - start + partial;
		if (age < 0f || age >= s.end()) {
			return Float.NaN;
		}
		out[0] = s;
		return age;
	}

	/** Is {@code p}'s Hulkbuster still building on (its lights stay dark)? */
	public static boolean building(Player p) {
		return p != null && !Float.isNaN(age(p, 0f, new Schedule[1]));
	}

	/** The body texture to draw {@code p} with this frame, or null for the plain one. */
	public static ResourceLocation texture(Player p, float partial) {
		if (p == null || p.level() == null) {
			return null;
		}
		observe(p, p.level().getGameTime());
		Schedule[] s = new Schedule[1];
		float age = age(p, partial, s);
		if (Float.isNaN(age)) {
			return null;
		}
		return frame(s[0], (int) Math.floor(age));
	}

	// ------------------------------------------------------------------ frames

	static ResourceLocation frame(Schedule s, int t) {
		String key = s.key() + "_" + t;
		ResourceLocation got = FRAMES.get(key);
		if (got != null) {
			return got;
		}
		Texels tx = texels();
		if (tx == null) {
			return null;
		}
		NativeImage img = new NativeImage(tx.w(), tx.h(), true);
		img.copyFrom(source);
		int[][] cols = tx.cols();
		// the slide columns first (each texel shows the far-end texel that has slid this far)
		for (int c = 0; c < cols.length; c++) {
			int[] strip = cols[c];
			int part = tx.part()[strip[0]];
			float p = Math.max(0f, Math.min(1f, (t - (s.attach()[part] + 1f)) / SLIDE_TICKS));
			int n = strip.length, on = Math.round(p * n);
			for (int j = 0; j < n; j++) {
				int i = strip[j];
				if (j >= on) {
					img.setPixelRGBA(i % tx.w(), i / tx.w(), 0);
				} else if (on < n) {
					int from = strip[j + n - on];
					img.setPixelRGBA(i % tx.w(), i / tx.w(), source.getPixelRGBA(from % tx.w(), from / tx.w()));
				}
			}
		}
		for (int i = 0; i < tx.kind().length; i++) {
			byte kind = tx.kind()[i];
			if (kind == NONE || kind == SLIDE && tx.col()[i] >= 0) {
				continue;
			}
			int part = tx.part()[i];
			float appear = switch (kind) {
				case CARRIED -> s.launch()[part];
				case SLIDE, SLIDE_CAP -> s.attach()[part] + 1f + SLIDE_TICKS;
				default -> s.attach()[part] + OUTER_DELAY + tx.k()[i] * OUTER_TICKS;
			};
			if (t < appear) {
				img.setPixelRGBA(i % tx.w(), i / tx.w(), 0);
			}
		}
		ResourceLocation id = ProjectHeroMod.id("dynamic/hulkbuster_build/" + (made++) + "_" + key);
		Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(img));
		FRAMES.put(key, id);
		return id;
	}

	// ------------------------------------------------------------------ the body's texels

	private static Texels texels() {
		if (texels != null || texelsTried) {
			return texels;
		}
		texelsTried = true;
		Minecraft mc = Minecraft.getInstance();
		Optional<Resource> texRes = mc.getResourceManager().getResource(TEX);
		Optional<Resource> geoRes = mc.getResourceManager().getResource(GEO);
		if (texRes.isEmpty() || geoRes.isEmpty()) {
			return null;
		}
		try (InputStream in = texRes.get().open(); Reader reader = new InputStreamReader(geoRes.get().open(), StandardCharsets.UTF_8)) {
			source = NativeImage.read(in);
			texels = classify(JsonParser.parseReader(reader).getAsJsonObject(), source.getWidth(), source.getHeight());
		} catch (Exception e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not read the Hulkbuster body for its build: {}", e.toString());
			texels = null;
		}
		return texels;
	}

	/** The part a bone belongs to (its nearest ancestor among head / shoulders / thighs, else the torso). */
	private static int partOf(String name, Map<String, String> parent) {
		for (String b = name; b != null; b = parent.get(b)) {
			switch (b) {
				case "head", "neck":
					return HulkbusterParts.HEAD;
				case "shoulder_l":
					return HulkbusterParts.LARM;
				case "shoulder_r":
					return HulkbusterParts.RARM;
				case "thigh_l":
					return HulkbusterParts.LLEG;
				case "thigh_r":
					return HulkbusterParts.RLEG;
				default:
			}
		}
		return HulkbusterParts.TORSO;
	}

	/** Seams (model px): the torso's base splits at y 27, the arm at the elbow (y 27), the thigh at y 13.5. */
	static final float TORSO_SEAM = 27f, ARM_SEAM = 27f, LEG_SEAM = 13.5f;

	static Texels classify(JsonObject root, int w, int h) {
		JsonObject model = root.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
		JsonObject desc = model.getAsJsonObject("description");
		float su = w / (desc.has("texture_width") ? desc.get("texture_width").getAsFloat() : 64f);
		float sv = h / (desc.has("texture_height") ? desc.get("texture_height").getAsFloat() : 64f);
		Map<String, String> parent = new HashMap<>();
		for (JsonElement b : model.getAsJsonArray("bones")) {
			JsonObject bone = b.getAsJsonObject();
			if (bone.has("parent")) {
				parent.put(bone.get("name").getAsString(), bone.get("parent").getAsString());
			}
		}
		byte[] kind = new byte[w * h];
		byte[] part = new byte[w * h];
		float[] k = new float[w * h];
		float[] seamDist = new float[w * h];
		String[] colKey = new String[w * h];
		Arrays.fill(kind, NONE);
		for (JsonElement b : model.getAsJsonArray("bones")) {
			JsonObject bone = b.getAsJsonObject();
			String name = bone.get("name").getAsString();
			if (!bone.has("cubes")) {
				continue;
			}
			int pt = partOf(name, parent);
			int ci = 0;
			for (JsonElement ce : bone.getAsJsonArray("cubes")) {
				JsonObject cube = ce.getAsJsonObject();
				int cubeIndex = ci++;
				JsonArray o = cube.getAsJsonArray("origin");
				JsonArray sz = cube.getAsJsonArray("size");
				float y0 = o.get(1).getAsFloat(), y1 = y0 + sz.get(1).getAsFloat();
				boolean outer = cubeIndex > 0 || "backpack".equals(name);
				JsonElement uvEl = cube.get("uv");
				if (uvEl == null || !uvEl.isJsonObject()) {
					continue;
				}
				for (Map.Entry<String, JsonElement> fe : uvEl.getAsJsonObject().entrySet()) {
					String face = fe.getKey();
					JsonObject f = fe.getValue().getAsJsonObject();
					JsonArray uv = f.getAsJsonArray("uv"), us = f.getAsJsonArray("uv_size");
					float u0 = uv.get(0).getAsFloat(), v0 = uv.get(1).getAsFloat(), du = us.get(0).getAsFloat(), dv = us.get(1).getAsFloat();
					if (Math.abs(du) * Math.abs(dv) <= 4f && (du <= 1f || dv <= 1f)) {
						continue; // the 1-texel filler strips (never-seen inner faces)
					}
					boolean vertical = face.equals("up") || face.equals("down");
					int tx0 = (int) Math.floor(Math.min(u0, u0 + du) * su), tx1 = (int) Math.ceil(Math.max(u0, u0 + du) * su);
					int ty0 = (int) Math.floor(Math.min(v0, v0 + dv) * sv), ty1 = (int) Math.ceil(Math.max(v0, v0 + dv) * sv);
					for (int ty = ty0; ty < ty1; ty++) {
						for (int tx = tx0; tx < tx1; tx++) {
							if (tx < 0 || ty < 0 || tx >= w || ty >= h) {
								continue;
							}
							int i = ty * w + tx;
							float fv = dv == 0 ? 0.5f : ((ty + 0.5f) / sv - Math.min(v0, v0 + dv)) / Math.abs(dv);
							float y = vertical ? (face.equals("up") ? y1 : y0) : y1 - fv * (y1 - y0);
							float scatter = IronManAssemblyPlan.hash("hb_build", i);
							byte kd;
							float kk = 0f, seam = 0f;
							if (pt == HulkbusterParts.HEAD) {
								kd = CARRIED;
							} else if (outer) {
								kd = OUTER;
								float lin = switch (pt) {
									case HulkbusterParts.TORSO -> (37f - y) / 20f;
									case HulkbusterParts.LARM, HulkbusterParts.RARM -> (y - 17f) / 20f;
									default -> (18.5f - y) / 19.5f;
								};
								kk = mix(lin, scatter, 0.18f);
							} else {
								boolean carried;
								switch (pt) {
									case HulkbusterParts.TORSO -> { carried = y >= TORSO_SEAM - 0.01f; seam = TORSO_SEAM - y; }
									case HulkbusterParts.LARM, HulkbusterParts.RARM -> {
										carried = !name.startsWith("arm_") || y < ARM_SEAM + 0.01f;
										seam = y - ARM_SEAM;
									}
									default -> { carried = y >= LEG_SEAM - 0.01f; seam = LEG_SEAM - y; }
								}
								kd = carried ? CARRIED : vertical ? SLIDE_CAP : SLIDE;
							}
							// a texel drawn twice goes with the later kind (nothing shows early)
							if (kind[i] == NONE || kd > kind[i] || kd == kind[i] && kk > k[i]) {
								kind[i] = kd;
								part[i] = (byte) pt;
								k[i] = kk;
								seamDist[i] = seam;
								colKey[i] = kd == SLIDE ? pt + "|" + face + "|" + tx : null;
							}
						}
					}
				}
			}
		}
		// the slide columns: one per (part, face, texel column), nearest the seam first
		Map<String, List<Integer>> byCol = new HashMap<>();
		for (int i = 0; i < kind.length; i++) {
			if (kind[i] == SLIDE && colKey[i] != null) {
				byCol.computeIfAbsent(colKey[i], x -> new ArrayList<>()).add(i);
			}
		}
		int[] col = new int[w * h], row = new int[w * h];
		Arrays.fill(col, -1);
		List<int[]> cols = new ArrayList<>();
		for (List<Integer> c : byCol.values()) {
			c.sort((a, bb) -> Float.compare(seamDist[a], seamDist[bb]));
			int[] ids = c.stream().mapToInt(Integer::intValue).toArray();
			for (int j = 0; j < ids.length; j++) {
				col[ids[j]] = cols.size();
				row[ids[j]] = j;
			}
			cols.add(ids);
		}
		return new Texels(w, h, kind, part, k, col, row, cols.toArray(new int[0][]));
	}

	private static float mix(float k, float scatter, float amount) {
		k = Math.max(0f, Math.min(1f, k));
		return (1f - amount) * k + amount * scatter;
	}
}
