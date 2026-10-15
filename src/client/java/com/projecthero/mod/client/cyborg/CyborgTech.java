package com.projecthero.mod.client.cyborg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.cyborg.CyborgConfig;
import com.projecthero.mod.cyborg.data.CyborgState;

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
 * v0.16.5 (user): the tech Cyborg builds out of his own body, one voxel (model pixel) at a time.
 * <ul>
 *   <li><b>The jetpack</b> -- "builds a jetpack on his back ... when he starts flying and make energy come out of there":
 *       Jet Flight (double-tap jump) builds it outward from the back plate, landing folds it away; while it flies, cyan
 *       energy jets pour out of its two nozzles and the soles of his boots.</li>
 *   <li><b>The arm blaster</b> -- "when he uses the sonic cannon and sonic burst make it craft a blaster on his arm similar
 *       to max steel": a barrel builds round the main forearm, muzzle last, for the Sonic Cannon's charge and the Sonic
 *       Burst, and comes apart again a moment after.</li>
 * </ul>
 * Every voxel is a cube on the {@link #TEX} palette; the newest ones of a build glow cyan. Drawn from the synced state, so
 * every viewer sees the same thing.
 */
public final class CyborgTech {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/cyborg/cyborg_tech.png");

	static final int DARK = 0, MID = 1, LIGHT = 2, CYAN = 3, RED = 4, BLACK = 5, SILVER = 6, CORE = 7;

	/** One voxel: its corner in part space (model pixels), its palette colour and when it appears (0..1). */
	private record Voxel(float x, float y, float z, int colour, float order) {
	}

	private static List<Voxel> jetpack;
	private static List<Voxel> blasterRight;
	private static List<Voxel> blasterLeft;
	private static List<Voxel> blasterRightSlim;
	private static List<Voxel> blasterLeftSlim;

	/** Per player: [blaster progress 0..1, client game time it was last stepped]. */
	private static final Map<UUID, float[]> BLASTER = new ConcurrentHashMap<>();

	private CyborgTech() {
	}

	public static void clearSessionState() {
		BLASTER.clear();
	}

	// ---------------------------------------------------------------- the jetpack

	/** How built the jetpack is, 0..1 (building after take-off, folding away after landing). */
	public static float jetpack(Player player, float partial) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.fx.jetChange + partial;
		float t = s.fx.jetChange <= 0L || age < 0f ? 1f : Mth.clamp(age / CyborgConfig.JETPACK_BUILD_TICKS, 0f, 1f);
		return s.hovering ? t : 1f - t;
	}

	/** The jetpack on the body (body-part space), and the energy jets while he flies. */
	public static void renderJetpack(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float partial,
			float age) {
		float built = jetpack(player, partial);
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		boolean flying = s != null && s.hovering;
		if (built <= 0f) {
			return;
		}
		if (jetpack == null) {
			jetpack = buildJetpack();
		}
		pose.pushPose();
		parent.body.translateAndRotate(pose);
		voxels(pose, buffers, light, jetpack, built, flying);
		if (flying && built > 0.85f) {
			// energy pouring out of the two nozzles (their mouths at y 11, x +-3, z 4)
			for (int side = -1; side <= 1; side += 2) {
				jet(pose, buffers, side * 3f, 11.2f, 4f, 1.4f, 0.9f + 0.25f * Mth.sin(age * 2.7f + side), age);
			}
		}
		pose.popPose();
		if (flying) {
			// ... and out of the soles of his boots
			for (ModelPart leg : new ModelPart[] { parent.rightLeg, parent.leftLeg }) {
				pose.pushPose();
				leg.translateAndRotate(pose);
				jet(pose, buffers, 0f, 12.1f, 0f, 1.7f, 0.75f + 0.2f * Mth.sin(age * 3.1f + leg.x), age);
				pose.popPose();
			}
		}
	}

	private static List<Voxel> buildJetpack() {
		List<Voxel> v = new ArrayList<>();
		// the mount plate on the back (z 2..3), with a red status light
		for (int y = 2; y < 9; y++) {
			for (int x = -3; x < 3; x++) {
				v.add(new Voxel(x, y, 2.05f, (y == 2 && (x == -1 || x == 0)) ? RED : MID, 0f));
			}
		}
		// the core housing in the middle, glowing
		for (int y = 4; y < 7; y++) {
			for (int x = -1; x < 1; x++) {
				v.add(new Voxel(x, y, 3.05f, y == 5 ? CORE : CYAN, 0f));
			}
		}
		// two thruster pods (x -4..-2 and 2..4, y 1..10, z 3..5), a cyan band round each, black nozzles below
		for (int side = -1; side <= 1; side += 2) {
			int x0 = side < 0 ? -4 : 2;
			for (int y = 1; y < 11; y++) {
				for (int x = x0; x < x0 + 2; x++) {
					for (int z = 3; z < 5; z++) {
						int colour = y == 10 ? BLACK : y == 6 ? CYAN : y == 1 ? SILVER : (x == x0) == (side < 0) ? DARK : LIGHT;
						v.add(new Voxel(x, y, z + 0.05f, colour, 0f));
					}
				}
			}
		}
		// build outward from the middle of the back plate, scattered: a voxel at a time
		List<Voxel> out = new ArrayList<>();
		float max = 0f;
		for (Voxel a : v) {
			max = Math.max(max, dist(a.x() + 0.5f, a.y() + 0.5f, a.z(), 0f, 5.5f, 2f));
		}
		for (Voxel a : v) {
			float d = dist(a.x() + 0.5f, a.y() + 0.5f, a.z(), 0f, 5.5f, 2f) / max;
			out.add(new Voxel(a.x(), a.y(), a.z(), a.colour(), Mth.clamp(0.8f * d + 0.2f * hash(a), 0f, 1f)));
		}
		out.sort(Comparator.comparingDouble(Voxel::order));
		return out;
	}

	// ---------------------------------------------------------------- the arm blaster

	/** Whether the blaster should be out: the Sonic Cannon charging / just fired, or a Sonic Burst. */
	private static boolean blasterWanted(Player player) {
		CyborgState s = player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
		if (s == null || !s.hasPower || player.level() == null) {
			return false;
		}
		if (s.chargeStart != 0L) {
			return true;
		}
		long age = player.level().getGameTime() - s.animStart;
		return age >= 0 && ((s.animId == CyborgState.ANIM_CANNON && age < CyborgConfig.CANNON_CHARGE_TICKS + 24)
				|| (s.animId == CyborgState.ANIM_BURST && age < 30));
	}

	/** How built the blaster is, 0..1: 8 ticks to build, 10 to come apart (stepped by the client's clock). */
	public static float blaster(Player player, float partial) {
		if (player.level() == null) {
			return 0f;
		}
		float now = player.level().getGameTime() + partial;
		float[] st = BLASTER.computeIfAbsent(player.getUUID(), k -> new float[] { 0f, now });
		float dt = Mth.clamp(now - st[1], 0f, 20f);
		st[1] = now;
		st[0] = blasterWanted(player) ? Math.min(1f, st[0] + dt / 8f) : Math.max(0f, st[0] - dt / 10f);
		if (st[0] <= 0f && !blasterWanted(player)) {
			BLASTER.remove(player.getUUID());
			return 0f;
		}
		return st[0];
	}

	/** The blaster on the main arm in third person (arm-part space). */
	public static void renderBlaster(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float partial,
			boolean slim) {
		float built = blaster(player, partial);
		if (built <= 0f) {
			return;
		}
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		pose.pushPose();
		(right ? parent.rightArm : parent.leftArm).translateAndRotate(pose);
		voxels(pose, buffers, light, blaster(right, slim), built, true);
		pose.popPose();
	}

	/** The blaster on the first-person arm (the arm vanilla just drew; its pose is already on {@code pose}'s parent). */
	public static void renderBlasterFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right,
			boolean slim) {
		if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
			return;
		}
		float built = blaster(player, net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
		if (built <= 0f) {
			return;
		}
		pose.pushPose();
		arm.translateAndRotate(pose);
		voxels(pose, buffers, light, blaster(right, slim), built, true);
		pose.popPose();
	}

	private static List<Voxel> blaster(boolean right, boolean slim) {
		if (blasterRight == null) {
			blasterRight = buildBlaster(-3f, 4);
			blasterLeft = buildBlaster(-1f, 4);
			blasterRightSlim = buildBlaster(-2f, 3);
			blasterLeftSlim = buildBlaster(-1f, 3);
		}
		return right ? (slim ? blasterRightSlim : blasterRight) : (slim ? blasterLeftSlim : blasterLeft);
	}

	/**
	 * A barrel round the forearm of an arm cube at {@code x0} (width {@code w}, z -2..2): a one-voxel shell from the elbow
	 * (y 4) past the fist (y 12), cyan vents, then a muzzle ring with a glowing core. Built from the elbow toward the muzzle.
	 */
	private static List<Voxel> buildBlaster(float x0, int w) {
		List<Voxel> v = new ArrayList<>();
		float lo = x0 - 1f, hi = x0 + w; // the shell's x range: one voxel outside the arm each side
		for (int y = 4; y < 14; y++) {
			boolean muzzle = y >= 12;
			for (float x = lo; x <= hi; x += 1f) {
				for (int z = -3; z <= 2; z++) {
					boolean edge = x == lo || x == hi || z == -3 || z == 2;
					if (!edge && !(muzzle && y == 13)) {
						continue; // the arm itself is inside (the fist end is closed by the muzzle face)
					}
					int colour;
					if (muzzle) {
						boolean centre = !edge;
						colour = centre ? (y == 13 ? CORE : CYAN) : BLACK;
					} else if (y == 7 || y == 10) {
						colour = (z == -3 || x == lo) && (int) Math.abs(x) % 2 == 0 ? CYAN : DARK; // the vents
					} else if (y == 4) {
						colour = SILVER;
					} else {
						colour = (x == hi || z == 2) ? MID : LIGHT;
					}
					v.add(new Voxel(x, y, z, colour, 0f));
				}
			}
		}
		List<Voxel> out = new ArrayList<>();
		for (Voxel a : v) {
			out.add(new Voxel(a.x(), a.y(), a.z(), a.colour(), Mth.clamp(((a.y() - 4f) / 10f) * 0.85f + 0.15f * hash(a), 0f, 1f)));
		}
		out.sort(Comparator.comparingDouble(Voxel::order));
		return out;
	}

	// ---------------------------------------------------------------- drawing

	/** The voxels up to {@code built}; the newest ones of a build glow cyan. Part space, model pixels. */
	private static void voxels(PoseStack pose, MultiBufferSource buffers, int light, List<Voxel> list, float built, boolean glowNewest) {
		VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEX));
		PoseStack.Pose last = pose.last();
		for (Voxel a : list) {
			if (a.order() > built) {
				break;
			}
			boolean fresh = glowNewest && built < 1f && built - a.order() < 0.08f;
			boolean lit = fresh || a.colour() == CYAN || a.colour() == CORE || a.colour() == RED;
			cube(vc, last, lit ? LightTexture.FULL_BRIGHT : light, a.x(), a.y(), a.z(), fresh ? CYAN : a.colour());
		}
	}

	/** One voxel cube, its six faces on palette colour {@code colour}. */
	private static void cube(VertexConsumer vc, PoseStack.Pose pose, int light, float px, float py, float pz, int colour) {
		float x0 = px / 16f, x1 = (px + 1f) / 16f, y0 = py / 16f, y1 = (py + 1f) / 16f, z0 = pz / 16f, z1 = (pz + 1f) / 16f;
		float u = (colour * 2 + 1) / 16f, v = 0.5f;
		Matrix4f m = pose.pose();
		face(vc, m, pose, light, u, v, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0f, 0f, -1f);
		face(vc, m, pose, light, u, v, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, 0f, 0f, 1f);
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, -1f, 0f, 0f);
		face(vc, m, pose, light, u, v, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1f, 0f, 0f);
		// model space y grows downward: the "top" (smaller y) faces up
		face(vc, m, pose, light, u, v, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, 0f, -1f, 0f);
		face(vc, m, pose, light, u, v, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0f, 1f, 0f);
	}

	private static void face(VertexConsumer vc, Matrix4f m, PoseStack.Pose pose, int light, float u, float v, float ax, float ay, float az,
			float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
		vc.addVertex(m, ax, ay, az).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, bx, by, bz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, cx, cy, cz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
		vc.addVertex(m, dx, dy, dz).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, nx, ny, nz);
	}

	/**
	 * A cyan energy jet out of point ({@code x}, {@code y}, {@code z}) (part space, model pixels) along +y (down the part):
	 * four crossed, tapering sheets, a pale core inside a blue glow, flickering. {@code length} in blocks.
	 */
	private static void jet(PoseStack pose, MultiBufferSource buffers, float x, float y, float z, float width, float length, float age) {
		VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
		Matrix4f m = pose.last().pose();
		float cx = x / 16f, cy = y / 16f, cz = z / 16f;
		float w = width / 16f;
		float flick = 0.85f + 0.15f * Mth.sin(age * 4.3f + x);
		for (int layer = 0; layer < 2; layer++) {
			float lw = layer == 0 ? w : w * 0.45f;
			float len = (layer == 0 ? length : length * 0.6f) * flick;
			float r = layer == 0 ? 0.25f : 0.85f, g = layer == 0 ? 0.7f : 0.97f, b = 1f;
			float a = layer == 0 ? 0.55f : 0.9f;
			for (int k = 0; k < 2; k++) {
				float ox = k == 0 ? lw : 0f, oz = k == 0 ? 0f : lw;
				// both windings: the lightning type is culled
				sheet(vc, m, cx - ox, cy, cz - oz, cx + ox, cy, cz + oz, cx, cy + len, cz, r, g, b, a);
				sheet(vc, m, cx + ox, cy, cz + oz, cx - ox, cy, cz - oz, cx, cy + len, cz, r, g, b, a);
			}
		}
	}

	private static void sheet(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz, float tx, float ty, float tz,
			float r, float g, float b, float a) {
		vc.addVertex(m, ax, ay, az).setColor(r, g, b, a);
		vc.addVertex(m, bx, by, bz).setColor(r, g, b, a);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
		vc.addVertex(m, tx, ty, tz).setColor(r, g, b, 0f);
	}

	private static float dist(float ax, float ay, float az, float bx, float by, float bz) {
		float dx = ax - bx, dy = ay - by, dz = az - bz;
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static float hash(Voxel a) {
		int h = Float.floatToIntBits(a.x() * 31f + a.y() * 17f + a.z() * 7f) * 374761393;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h >>> 8) & 0xFFFF) / 65536f;
	}
}
