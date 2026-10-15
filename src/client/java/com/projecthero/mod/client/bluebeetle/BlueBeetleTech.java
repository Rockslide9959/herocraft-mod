package com.projecthero.mod.client.bluebeetle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.bluebeetle.BlueBeetleConfig;
import com.projecthero.mod.bluebeetle.data.BlueBeetleState;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

import org.joml.Matrix4f;

/**
 * v0.16.5 (user): Blue Beetle's voxel-built tech, every voxel a cube on the {@link #TEX} palette (the CyborgTech
 * technique, with half-pixel voxels for the fine detail).
 * <ul>
 *   <li><b>The Scarab</b> ("make the scarab model better") -- an oval carapace split down the middle, blue and black with
 *       a gold rim, a glowing core in the split, a head with glowing eyes and six legs gripping the back.</li>
 *   <li><b>The Plasma Blaster</b> ("make a blaster build itself on the players arm") -- builds round the main forearm,
 *       elbow to muzzle, while R is held, and comes apart after.</li>
 *   <li><b>The Mega Cannon</b> ("a big blaster that forms on both arms when the player puts them together") -- a wide twin
 *       cannon over both forearms, built during the charge.</li>
 *   <li><b>The Scarab Hammer</b> (G) -- the main arm forms a hammer of energy for the smash.</li>
 * </ul>
 * Drawn from the synced state, so every viewer sees the same thing. No build glow: voxels simply appear.
 */
public final class BlueBeetleTech {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/blue_beetle/blue_beetle_tech.png");

	static final int BLACK = 0, DBLUE = 1, BLUE = 2, LBLUE = 3, GOLD = 4, DGOLD = 5, GLOW = 6, CORE = 7, GUN = 8, GREY = 9, INDIGO = 10,
			PALE = 11;

	/** One voxel: its corner in part space (model pixels), its edge, its palette colour and when it appears (0..1). */
	record Voxel(float x, float y, float z, float s, int colour, float order) {
	}

	private static List<Voxel> scarab;
	private static final Map<String, List<Voxel>> CACHE = new ConcurrentHashMap<>();

	/** Per player and build: [progress 0..1, client game time it was last stepped]. */
	private static final Map<String, float[]> BUILDS = new ConcurrentHashMap<>();

	private BlueBeetleTech() {
	}

	public static void clearSessionState() {
		BUILDS.clear();
	}

	// ---------------------------------------------------------------- the Scarab

	/** The Scarab on the back plate (body-part space: the back face is at z = +2, the neck at y = 0). */
	public static void renderScarab(PoseStack pose, MultiBufferSource buffers, int light, HumanoidModel<?> parent) {
		pose.pushPose();
		parent.body.translateAndRotate(pose);
		voxels(pose, buffers, light, scarab(), 1f);
		pose.popPose();
	}

	/** The Scarab alone, centred on the origin, nose toward -y (for the drones). */
	public static void renderScarabCentred(PoseStack pose, MultiBufferSource buffers, int light) {
		pose.pushPose();
		pose.translate(0f, -4.6f / 16f, -2.3f / 16f);
		voxels(pose, buffers, light, scarab(), 1f);
		pose.popPose();
	}

	static List<Voxel> scarab() {
		if (scarab == null) {
			scarab = buildScarab();
		}
		return scarab;
	}

	private static List<Voxel> buildScarab() {
		List<Voxel> v = new ArrayList<>();
		final float h = 0.5f;
		final float cy0 = 4.6f, rx = 2.6f, ry = 3.0f;
		for (float gy = 1.5f; gy < 8.0f; gy += h) {
			for (float gx = -3.0f; gx < 3.0f; gx += h) {
				float cx = gx + h / 2f, cy = gy + h / 2f;
				float e = (cx / rx) * (cx / rx) + ((cy - cy0) / ry) * ((cy - cy0) / ry);
				if (e > 1f) {
					continue;
				}
				boolean seam = Math.abs(cx) < 0.3f;
				// the underside, black, flat on the back
				v.add(new Voxel(gx, gy, 2.3f, h, BLACK, 0f));
				// the carapace: gold rim, blue wing cases split by the seam, the core glowing in the split
				if (e <= 0.88f) {
					int c;
					if (seam) {
						c = cy > 3.4f && cy < 5.8f ? CORE : BLACK;
					} else if (e > 0.6f) {
						c = e > 0.75f ? GOLD : DGOLD;
					} else {
						boolean upper = cy < cy0;
						c = cx < 0 ? (upper ? LBLUE : BLUE) : (upper ? BLUE : DBLUE);
					}
					v.add(new Voxel(gx, gy, 2.8f, h, c, 0f));
				}
				// the dome of each wing case
				if (e <= 0.5f && !seam) {
					int c = e > 0.36f ? INDIGO : (cx < 0 ? LBLUE : BLUE);
					v.add(new Voxel(gx, gy, 3.3f, h, c, 0f));
				} else if (seam && cy > 3.6f && cy < 5.6f) {
					v.add(new Voxel(gx, gy, 3.3f, h, GLOW, 0f)); // the core stands proud of the split
				}
				if (e <= 0.18f && !seam) {
					v.add(new Voxel(gx, gy, 3.8f, h, cx < 0 ? LBLUE : BLUE, 0f));
				}
			}
		}
		// the head (toward the neck): black with gold mandibles and two glowing eyes
		for (float gy = 0.5f; gy < 1.5f; gy += h) {
			for (float gx = -1.0f; gx < 1.0f; gx += h) {
				float cx = gx + h / 2f;
				int c = gy < 1.0f && Math.abs(cx) > 0.5f ? GLOW : BLACK;
				v.add(new Voxel(gx, gy, 2.3f, h, BLACK, 0f));
				v.add(new Voxel(gx, gy, 2.8f, h, c, 0f));
			}
		}
		v.add(new Voxel(-1.5f, 0.0f, 2.3f, h, GOLD, 0f));
		v.add(new Voxel(1.0f, 0.0f, 2.3f, h, GOLD, 0f));
		// six legs gripping the back, gold-tipped
		float[][][] legs = {
				{ { 2.2f, 2.6f }, { 3.0f, 2.0f }, { 3.6f, 1.3f }, { 3.9f, 0.6f } },
				{ { 2.5f, 4.6f }, { 3.4f, 4.4f }, { 4.0f, 4.9f }, { 4.3f, 5.7f } },
				{ { 2.2f, 6.6f }, { 2.9f, 7.4f }, { 3.3f, 8.3f }, { 3.5f, 9.1f } } };
		Set<Long> seen = new HashSet<>();
		for (int side = -1; side <= 1; side += 2) {
			for (float[][] leg : legs) {
				for (int k = 0; k < leg.length - 1; k++) {
					for (float t = 0f; t <= 1f; t += 0.2f) {
						float x = side * Mth.lerp(t, leg[k][0], leg[k + 1][0]);
						float y = Mth.lerp(t, leg[k][1], leg[k + 1][1]);
						float gx = (float) Math.floor(x / h) * h, gy = (float) Math.floor(y / h) * h;
						long key = ((long) Math.round(gx * 4) << 32) ^ (Math.round(gy * 4) & 0xFFFFFFFFL);
						if (!seen.add(key)) {
							continue;
						}
						boolean tip = k == leg.length - 2 && t >= 0.6f;
						v.add(new Voxel(gx, gy, 2.3f, h, tip ? GOLD : (k == 0 ? GUN : BLACK), 0f));
					}
				}
			}
		}
		return v;
	}

	// ---------------------------------------------------------------- build progress (client-stepped)

	/**
	 * How built one of {@code player}'s builds is, 0..1: toward 1 at {@code 1 / buildTicks} a tick while {@code wanted},
	 * back toward 0 at {@code 1 / breakTicks} a tick after.
	 */
	static float progress(Player player, String key, boolean wanted, float buildTicks, float breakTicks, float partial) {
		if (player.level() == null) {
			return 0f;
		}
		float now = player.level().getGameTime() + partial;
		String id = player.getUUID() + key;
		float[] st = BUILDS.get(id);
		if (st == null) {
			if (!wanted) {
				return 0f;
			}
			st = new float[] { 0f, now };
			BUILDS.put(id, st);
		}
		float dt = Mth.clamp(now - st[1], 0f, 20f);
		st[1] = now;
		st[0] = wanted ? Math.min(1f, st[0] + dt / buildTicks) : Math.max(0f, st[0] - dt / breakTicks);
		if (st[0] <= 0f && !wanted) {
			BUILDS.remove(id);
			return 0f;
		}
		return st[0];
	}

	private static BlueBeetleState state(Player player) {
		BlueBeetleState s = player.getAttachedOrElse(ModAttachments.BLUE_BEETLE_STATE, null);
		return s == null || !s.hasPower || !s.suited ? null : s;
	}

	private static boolean cannonWanted(Player player, BlueBeetleState s) {
		if (s.fx.chargeStart != 0L) {
			return true;
		}
		long age = player.level().getGameTime() - s.animStart;
		return s.animId == BlueBeetleState.ANIM_CANNON && age >= 0 && age < BlueBeetleConfig.CANNON_CHARGE_TICKS + 14;
	}

	/** The arm blaster (R held). */
	static float blaster(Player player, float partial) {
		BlueBeetleState s = state(player);
		boolean wanted = s != null && (s.fx.blasting || (s.animId == BlueBeetleState.ANIM_BLAST && player.level().getGameTime() - s.animStart < 10));
		return progress(player, "blaster", wanted, 7f, 9f, partial);
	}

	/** The twin cannon: built across the 2 s charge, comes apart after the shot. */
	static float cannon(Player player, float partial) {
		BlueBeetleState s = state(player);
		boolean wanted = s != null && cannonWanted(player, s);
		return progress(player, "cannon", wanted, BlueBeetleConfig.CANNON_CHARGE_TICKS * 0.8f, 12f, partial);
	}

	/** The energy hammer: forms in 4 ticks, gone soon after the smash. */
	static float hammer(Player player, float partial) {
		BlueBeetleState s = state(player);
		boolean wanted = false;
		if (s != null && s.animId == BlueBeetleState.ANIM_HAMMER) {
			long age = player.level().getGameTime() - s.animStart;
			wanted = age >= 0 && age < BlueBeetleConfig.HAMMER_HIT_TICK + 5;
		}
		return progress(player, "hammer", wanted, 4f, 5f, partial);
	}

	// ---------------------------------------------------------------- third person

	/** The arm blaster, the twin cannon and the hammer in third person. */
	public static void renderArmTech(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float partial) {
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart main = right ? parent.rightArm : parent.leftArm;
		float blaster = blaster(player, partial);
		float hammer = hammer(player, partial);
		if (blaster > 0f || hammer > 0f) {
			pose.pushPose();
			main.translateAndRotate(pose);
			if (blaster > 0f) {
				voxels(pose, buffers, light, blasterVoxels(right), blaster);
			}
			if (hammer > 0f) {
				voxels(pose, buffers, light, hammerVoxels(right), hammer);
			}
			pose.popPose();
		}
		float cannon = cannon(player, partial);
		if (cannon > 0f) {
			// the cannon's frame: the shoulder line, turned with the arms (both point the same way in the cannon pose)
			pose.pushPose();
			pose.translate(0f, 2f / 16f, 0f);
			pose.mulPose(com.mojang.math.Axis.XP.rotation(0.5f * (parent.rightArm.xRot + parent.leftArm.xRot)));
			voxels(pose, buffers, light, cannonVoxels(), cannon);
			pose.popPose();
		}
	}

	/** First person: the blaster (also for the cannon's charge) and the hammer on the main arm. */
	public static void renderArmTechFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right) {
		if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
			return;
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float blaster = Math.max(blaster(player, partial), cannon(player, partial));
		float hammer = hammer(player, partial);
		if (blaster <= 0f && hammer <= 0f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		if (blaster > 0f) {
			voxels(pose, buffers, light, blasterVoxels(right), blaster);
		}
		if (hammer > 0f) {
			voxels(pose, buffers, light, hammerVoxels(right), hammer);
		}
		pose.popPose();
	}

	// ---------------------------------------------------------------- the models

	private static List<Voxel> blasterVoxels(boolean right) {
		return CACHE.computeIfAbsent("blaster_" + right, k -> buildBlaster(right ? -3f : -1f, 4, right));
	}

	private static List<Voxel> hammerVoxels(boolean right) {
		return CACHE.computeIfAbsent("hammer_" + right, k -> buildHammer(right ? -1f : 1f));
	}

	private static List<Voxel> cannonVoxels() {
		return CACHE.computeIfAbsent("cannon", k -> buildCannon());
	}

	/**
	 * A sleek blue blaster round the forearm of an arm cube at {@code x0} (width {@code w}, z -2..2): a one-voxel shell
	 * from a gold cuff at the elbow (y 3) past the fist, glowing vents on top (-z, which faces up when the arm aims), a
	 * gold fin down the outer side, then a black muzzle with a glowing bore. Built elbow to muzzle.
	 */
	private static List<Voxel> buildBlaster(float x0, int w, boolean right) {
		List<Voxel> v = new ArrayList<>();
		float lo = x0 - 1f, hi = x0 + w;
		for (int y = 3; y < 13; y++) {
			boolean muzzle = y >= 11;
			for (float x = lo; x <= hi; x += 1f) {
				for (int z = -3; z <= 2; z++) {
					boolean edge = x == lo || x == hi || z == -3 || z == 2;
					if (!edge && !(y == 12)) {
						continue;
					}
					int c;
					if (muzzle) {
						c = edge ? BLACK : (y == 12 ? ((x == lo + 1 || x == hi - 1) && (z == -2 || z == 1) ? GLOW : CORE) : BLACK);
					} else if (y == 3) {
						c = GOLD;
					} else if (z == -3) {
						c = (y == 5 || y == 7 || y == 9) && x != lo && x != hi ? GLOW : (x == lo || x == hi ? DBLUE : BLUE);
					} else if (z == 2) {
						c = BLACK;
					} else {
						c = y == 6 || y == 8 ? INDIGO : DBLUE;
					}
					v.add(new Voxel(x, y, z, 1f, c, 0f));
				}
			}
		}
		// the barrel past the fist (2x2) with a glowing tip
		float bx = x0 + w / 2f - 1f;
		for (int y = 13; y < 15; y++) {
			for (float x = bx; x < bx + 2f; x += 1f) {
				for (int z = -1; z < 1; z++) {
					v.add(new Voxel(x, y, z, 1f, y == 14 ? GLOW : GUN, 0f));
				}
			}
		}
		// the gold fin on the outer side, half-pixel thin
		float fx = right ? lo - 0.5f : hi + 1f;
		for (float y = 4f; y < 11f; y += 0.5f) {
			float top = y < 6f ? -1.5f + (6f - y) * 0.5f : -1.5f;
			for (float z = top; z < 1f; z += 0.5f) {
				v.add(new Voxel(fx, y, z, 0.5f, z < -1f ? GLOW : GOLD, 0f));
			}
		}
		return ordered(v, a -> (a.y() - 3f) / 12f);
	}

	/**
	 * The twin Mega Cannon over both forearms (shoulder-line space: +y along the arms, -z up when they aim): a wide blue
	 * shell x -6..6, gold bands, a glowing seam and vents along the top, black underneath, and a muzzle face with two big
	 * glowing bores. Built from the forearms toward the muzzle.
	 */
	private static List<Voxel> buildCannon() {
		List<Voxel> v = new ArrayList<>();
		int x0 = -6, x1 = 5, z0 = -4, z1 = 3, y0 = 5, y1 = 15;
		for (int y = y0; y <= y1; y++) {
			for (int x = x0; x <= x1; x++) {
				for (int z = z0; z <= z1; z++) {
					boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
					boolean face = y == y1;
					if (!edge && !face) {
						continue;
					}
					int c;
					if (face) {
						boolean boreL = Math.abs(x + 3) <= 1 && Math.abs(z) <= 1;
						boolean boreR = Math.abs(x - 2) <= 1 && Math.abs(z) <= 1;
						c = boreL || boreR ? ((x == -3 || x == 2) && z == 0 ? CORE : GLOW) : (edge ? GOLD : BLACK);
					} else if (y == y0 || y == 11) {
						c = GOLD;
					} else if (z == z0) {
						// the top: a glowing seam down the middle, vents either side
						c = x == -1 || x == 0 ? GLOW : ((y == 7 || y == 9 || y == 13) && (x == -4 || x == 3) ? GLOW : (x < 0 ? LBLUE : BLUE));
					} else if (z == z1) {
						c = BLACK;
					} else {
						c = (z == -1 || z == 0) && (y == 8 || y == 13) ? INDIGO : DBLUE;
					}
					v.add(new Voxel(x, y, z, 1f, c, 0f));
				}
			}
		}
		// two barrels out of the bores
		for (int b = 0; b < 2; b++) {
			int cx = b == 0 ? -3 : 2;
			for (int y = y1 + 1; y <= y1 + 2; y++) {
				for (int x = cx - 1; x <= cx + 1; x++) {
					for (int z = -1; z <= 1; z++) {
						if (x == cx && z == 0) {
							continue;
						}
						v.add(new Voxel(x, y, z, 1f, y == y1 + 2 ? GLOW : GUN, 0f));
					}
				}
			}
		}
		return ordered(v, a -> (a.y() - 5f) / 13f);
	}

	/**
	 * The energy hammer on the fist (arm space): a glowing handle out of the fist (y 10..16) and a big head across it
	 * (long along z, the way the arm swings), pale edges, a white-blue core band, gold caps.
	 */
	private static List<Voxel> buildHammer(float cx) {
		List<Voxel> v = new ArrayList<>();
		for (int y = 10; y < 16; y++) {
			for (float x = cx - 1f; x < cx + 1f; x += 1f) {
				for (int z = -1; z < 1; z++) {
					v.add(new Voxel(x, y, z, 1f, GLOW, 0f));
				}
			}
		}
		int hx0 = (int) cx - 3, hx1 = (int) cx + 2, hz0 = -6, hz1 = 5, hy0 = 16, hy1 = 21;
		for (int y = hy0; y <= hy1; y++) {
			for (int x = hx0; x <= hx1; x++) {
				for (int z = hz0; z <= hz1; z++) {
					boolean edge = x == hx0 || x == hx1 || z == hz0 || z == hz1 || y == hy0 || y == hy1;
					if (!edge) {
						continue;
					}
					int c;
					if (z == hz0 || z == hz1) {
						c = GOLD; // the striking faces
					} else if (y == hy0 + 2 || y == hy0 + 3) {
						c = CORE;
					} else {
						int edges = (x == hx0 || x == hx1 ? 1 : 0) + (y == hy0 || y == hy1 ? 1 : 0);
						c = edges > 0 && (z == hz0 + 1 || z == hz1 - 1) ? PALE : GLOW;
					}
					v.add(new Voxel(x, y, z, 1f, c, 0f));
				}
			}
		}
		return ordered(v, a -> Math.min(1f, (a.y() - 10f) / 12f));
	}

	private static List<Voxel> ordered(List<Voxel> v, java.util.function.Function<Voxel, Float> base) {
		List<Voxel> out = new ArrayList<>();
		for (Voxel a : v) {
			out.add(new Voxel(a.x(), a.y(), a.z(), a.s(), a.colour(), Mth.clamp(base.apply(a) * 0.85f + 0.15f * hash(a), 0f, 1f)));
		}
		out.sort(Comparator.comparingDouble(Voxel::order));
		return out;
	}

	// ---------------------------------------------------------------- drawing

	static boolean lit(int colour) {
		return colour == GLOW || colour == CORE || colour == PALE;
	}

	/** The voxels up to {@code built}, part space in model pixels. Glow colours are full-bright. */
	static void voxels(PoseStack pose, MultiBufferSource buffers, int light, List<Voxel> list, float built) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
		PoseStack.Pose last = pose.last();
		for (Voxel a : list) {
			if (a.order() > built) {
				break;
			}
			cube(vc, last, lit(a.colour()) ? LightTexture.FULL_BRIGHT : light, a.x(), a.y(), a.z(), a.s(), a.colour());
		}
	}

	/** One voxel cube of edge {@code s} (model pixels), its six faces on palette colour {@code colour}. */
	static void cube(VertexConsumer vc, PoseStack.Pose pose, int light, float px, float py, float pz, float s, int colour) {
		float x0 = px / 16f, x1 = (px + s) / 16f, y0 = py / 16f, y1 = (py + s) / 16f, z0 = pz / 16f, z1 = (pz + s) / 16f;
		float u = (colour * 2 + 1) / 32f, v = 0.5f;
		Matrix4f m = pose.pose();
		face(vc, m, pose, light, u, v, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
		face(vc, m, pose, light, u, v, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0f, 0f, 1f);
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1f, 0f, 0f);
		face(vc, m, pose, light, u, v, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0f, -1f, 0f);
		face(vc, m, pose, light, u, v, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
	}

	/** A world-space voxel of edge {@code size} blocks centred on ({@code cx}, {@code cy}, {@code cz}). */
	static void worldCube(VertexConsumer vc, PoseStack.Pose pose, int light, float cx, float cy, float cz, float size, int colour) {
		float h = size / 2f;
		float x0 = cx - h, x1 = cx + h, y0 = cy - h, y1 = cy + h, z0 = cz - h, z1 = cz + h;
		float u = (colour * 2 + 1) / 32f, v = 0.5f;
		Matrix4f m = pose.pose();
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0f, 0f, -1f);
		face(vc, m, pose, light, u, v, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0f, 0f, 1f);
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1f, 0f, 0f);
		face(vc, m, pose, light, u, v, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1f, 0f, 0f);
		face(vc, m, pose, light, u, v, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0f, -1f, 0f);
		face(vc, m, pose, light, u, v, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0f, 1f, 0f);
	}

	private static void face(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, int light, float u, float v, float ax, float ay, float az,
			float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		vc.addVertex(m, ax, ay, az).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, bx, by, bz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	private static float hash(Voxel a) {
		int h = Float.floatToIntBits(a.x() * 31f + a.y() * 17f + a.z() * 7f + 0.37f) * 374761393;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h >>> 8) & 0xFFFF) / 65536f;
	}

	/** Forget a player's build clocks (they left). */
	static void forget(UUID id) {
		BUILDS.keySet().removeIf(k -> k.startsWith(id.toString()));
	}
}
