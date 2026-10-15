package com.projecthero.mod.client.cyborg;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
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
 * v0.16.5 (user): the tech Cyborg builds out of his own body, a voxel at a time. Every voxel is a cube on the {@link #TEX}
 * palette; the newest ones of a build glow cyan. Drawn from the synced state, so every viewer sees the same thing.
 * <ul>
 *   <li><b>The jetpack</b> -- Jet Flight (double-tap jump) and the X Jet Dash build it outward from the back plate; it folds
 *       away on landing / when the dash ends. Cyan energy jets pour from its two nozzles and the soles of his boots.</li>
 *   <li><b>The arm blaster</b> (balance pass: "upgrade the blaster model") -- for the Sonic Cannon and Sonic Burst a
 *       detailed cannon builds round the main forearm from the elbow out: a chamfered barrel housing in half-pixel
 *       plates, three glowing power coils, heat-sink fins on the outside, a sight rail on top, and a long barrel past the
 *       fist ending in a slotted muzzle brake round a glowing core.</li>
 *   <li><b>The Arm Saw</b> (Shift+G) -- a toothed buzzsaw disc on the outside of the forearm, spinning.</li>
 *   <li><b>The missile launcher</b> (Z) -- a four-tube pod that pops up on the other shoulder for the barrage.</li>
 * </ul>
 */
public final class CyborgTech {
	public static final ResourceLocation TEX = ProjectHeroMod.id("textures/entity/cyborg/cyborg_tech.png");

	static final int DARK = 0, MID = 1, LIGHT = 2, CYAN = 3, RED = 4, BLACK = 5, SILVER = 6, CORE = 7;

	/** One voxel: its corner in part space (model pixels), its edge length, its palette colour and when it appears (0..1). */
	private record Voxel(float x, float y, float z, float size, int colour, float order) {
	}

	private static List<Voxel> jetpack;
	private static final Map<String, List<Voxel>> BLASTERS = new ConcurrentHashMap<>();
	private static final Map<String, List<Voxel>> LAUNCHERS = new ConcurrentHashMap<>();
	private static List<Voxel> sawDisc;

	/** Per player: [blaster progress 0..1, client game time it was last stepped]. */
	private static final Map<UUID, float[]> BLASTER = new ConcurrentHashMap<>();

	private CyborgTech() {
	}

	public static void clearSessionState() {
		BLASTER.clear();
	}

	private static CyborgState state(Player player) {
		return player.getAttachedOrElse(ModAttachments.CYBORG_STATE, null);
	}

	// ---------------------------------------------------------------- the jetpack

	/** Whether the jetpack should be out: Jet Flight, or the X Jet Dash. */
	private static boolean jetpackWanted(CyborgState s, long now) {
		return s.hovering || s.fx.jetDashUntil > now;
	}

	/** How built the jetpack is, 0..1 (building after take-off / a dash, folding away after). */
	public static float jetpack(Player player, float partial) {
		CyborgState s = state(player);
		if (s == null || !s.hasPower || player.level() == null) {
			return 0f;
		}
		long now = player.level().getGameTime();
		float age = now - s.fx.jetChange + partial;
		float t = s.fx.jetChange <= 0L || age < 0f ? 1f : Mth.clamp(age / CyborgConfig.JETPACK_BUILD_TICKS, 0f, 1f);
		return jetpackWanted(s, now) ? t : 1f - t;
	}

	/** The jetpack on the body (body-part space), and the energy jets while it is working. */
	public static void renderJetpack(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float partial,
			float age) {
		float built = jetpack(player, partial);
		CyborgState s = state(player);
		if (built <= 0f || s == null) {
			return;
		}
		boolean working = jetpackWanted(s, player.level().getGameTime());
		if (jetpack == null) {
			jetpack = buildJetpack();
		}
		pose.pushPose();
		parent.body.translateAndRotate(pose);
		voxels(pose, buffers, light, jetpack, built, working);
		if (working && built > 0.85f) {
			boolean dash = s.fx.jetDashUntil > player.level().getGameTime();
			// energy pouring out of the two nozzles (their mouths at y 11, x +-3, z 4), longer on a dash
			for (int side = -1; side <= 1; side += 2) {
				jet(pose, buffers, side * 3f, 11.2f, 4f, dash ? 1.8f : 1.4f, (dash ? 1.6f : 0.9f) + 0.25f * Mth.sin(age * 2.7f + side), age);
			}
		}
		pose.popPose();
		if (s.hovering) {
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
				v.add(new Voxel(x, y, 2.05f, 1f, (y == 2 && (x == -1 || x == 0)) ? RED : MID, 0f));
			}
		}
		// the core housing in the middle, glowing
		for (int y = 4; y < 7; y++) {
			for (int x = -1; x < 1; x++) {
				v.add(new Voxel(x, y, 3.05f, 1f, y == 5 ? CORE : CYAN, 0f));
			}
		}
		// two thruster pods (x -4..-2 and 2..4, y 1..10, z 3..5), a cyan band round each, black nozzles below
		for (int side = -1; side <= 1; side += 2) {
			int x0 = side < 0 ? -4 : 2;
			for (int y = 1; y < 11; y++) {
				for (int x = x0; x < x0 + 2; x++) {
					for (int z = 3; z < 5; z++) {
						int colour = y == 10 ? BLACK : y == 6 ? CYAN : y == 1 ? SILVER : (x == x0) == (side < 0) ? DARK : LIGHT;
						v.add(new Voxel(x, y, z + 0.05f, 1f, colour, 0f));
					}
				}
			}
		}
		return ordered(v, 0f, 5.5f, 2f);
	}

	// ---------------------------------------------------------------- the arm blaster

	/** Whether the blaster should be out: the Sonic Cannon charging / just fired, or a Sonic Burst. */
	private static boolean blasterWanted(Player player) {
		CyborgState s = state(player);
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

	/** The blaster and the saw on the main arm, the launcher on the other shoulder -- third person. */
	public static void renderArmTech(PoseStack pose, MultiBufferSource buffers, int light, Player player, HumanoidModel<?> parent, float partial,
			float age, boolean slim) {
		boolean right = player.getMainArm() == HumanoidArm.RIGHT;
		ModelPart arm = right ? parent.rightArm : parent.leftArm;
		float built = blaster(player, partial);
		if (built > 0f) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			voxels(pose, buffers, light, blaster(right, slim), built, true);
			pose.popPose();
		}
		renderSaw(pose, buffers, light, player, arm, right, slim, partial, age);
		float pod = launcher(player, partial);
		if (pod > 0f) {
			pose.pushPose();
			parent.body.translateAndRotate(pose);
			voxels(pose, buffers, light, launcherVoxels(!right), pod, true);
			pose.popPose();
		}
	}

	/** First person: the blaster and the saw on the arm vanilla just drew. */
	public static void renderArmTechFirstPerson(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right,
			boolean slim) {
		if ((player.getMainArm() == HumanoidArm.RIGHT) != right) {
			return;
		}
		float partial = net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
		float built = blaster(player, partial);
		if (built > 0f) {
			pose.pushPose();
			arm.translateAndRotate(pose);
			voxels(pose, buffers, light, blaster(right, slim), built, true);
			pose.popPose();
		}
		renderSaw(pose, buffers, light, player, arm, right, slim, partial, player.tickCount + partial);
	}

	private static List<Voxel> blaster(boolean right, boolean slim) {
		String key = (right ? "r" : "l") + (slim ? "s" : "w");
		return BLASTERS.computeIfAbsent(key, k -> {
			int w = slim ? 3 : 4;
			float x0 = right ? (slim ? -2f : -3f) : -1f;
			return buildBlaster(x0, w, right ? -1 : 1);
		});
	}

	/**
	 * The cannon round the forearm of an arm cube at {@code x0} (width {@code w}, depth z -2..2), {@code out} = the side away
	 * from the body (-1 = -x). In the arm's own space y runs from the shoulder (-2) to the fist (10); -z is the arm's front,
	 * which is "up" when the arm is raised to aim.
	 */
	private static List<Voxel> buildBlaster(float x0, int w, int out) {
		List<Voxel> v = new ArrayList<>();
		float h = 0.5f;
		float lo = x0 - 1f, hi = x0 + w + 0.5f; // housing x range (half-pixel steps), one pixel outside the arm
		float zlo = -3f, zhi = 2.5f;
		float cx = x0 + w / 2f;
		// the housing: elbow (y 4) to just past the fist (y 11), chamfered corners, in half-pixel plates
		for (float y = 4f; y < 11f; y += h) {
			boolean collar = y < 5f;
			boolean coil = y == 6f || y == 6.5f || y == 8f || y == 8.5f || y == 10f;
			for (float x = lo; x <= hi; x += h) {
				for (float z = zlo; z <= zhi; z += h) {
					boolean edgeX = x == lo || x == hi, edgeZ = z == zlo || z == zhi;
					if (!edgeX && !edgeZ) {
						continue;
					}
					boolean corner = (x == lo || x == hi) && (z == zlo || z == zhi);
					boolean nearCorner = (x <= lo + h || x >= hi - h) && (z <= zlo + h || z >= zhi - h);
					if (corner || (nearCorner && !collar)) {
						continue; // chamfered: an octagonal barrel
					}
					int colour;
					if (collar) {
						colour = SILVER;
					} else if (coil) {
						colour = (z == zlo || z == zhi) ? CYAN : (x == lo || x == hi) && (y == 6f || y == 8f) ? CYAN : MID;
					} else if (z == zlo) {
						colour = LIGHT; // the top plate
					} else if (z == zhi) {
						colour = DARK;
					} else {
						colour = ((int) (y * 2f)) % 3 == 0 ? MID : LIGHT;
					}
					v.add(new Voxel(x, y, z, h, colour, 0f));
				}
			}
		}
		// heat-sink fins on the outside (x beyond the housing), dark, every pixel along y 5.5..9.5
		float finX = out < 0 ? lo - 1f : hi + h;
		for (float y = 5.5f; y <= 9.5f; y += 1f) {
			for (float z = -2f; z <= 1.5f; z += h) {
				v.add(new Voxel(finX, y, z, h, DARK, 0f));
				v.add(new Voxel(finX + (out < 0 ? -h : h), y, z, h, z == -2f || z == 1.5f ? MID : DARK, 0f));
			}
		}
		// the sight rail on top (-z), with a red sight near the muzzle
		for (float y = 5f; y < 11f; y += h) {
			v.add(new Voxel(cx - h, y, zlo - h, h, y >= 9.5f && y < 10.5f ? RED : SILVER, 0f));
		}
		// the barrel past the fist: a 2x2-pixel bore in a 3x3 tube, y 11..14
		for (float y = 11f; y < 14f; y += h) {
			for (float x = cx - 1.5f; x < cx + 1.5f; x += h) {
				for (float z = -1.5f; z < 1.5f; z += h) {
					boolean bore = x >= cx - 0.5f && x < cx + 0.5f && z >= -0.5f && z < 0.5f;
					boolean shell = x == cx - 1.5f || x == cx + 1f || z == -1.5f || z == 1f;
					if (bore && y >= 13.5f) {
						v.add(new Voxel(x, y, z, h, CORE, 0f)); // the glowing core at the mouth
					} else if (shell) {
						v.add(new Voxel(x, y, z, h, y == 12f ? CYAN : MID, 0f));
					}
				}
			}
		}
		// the muzzle brake: a wider slotted ring, y 13.5..14.5
		for (float x = cx - 2f; x < cx + 2f; x += h) {
			for (float z = -2f; z < 2f; z += h) {
				boolean ring = x == cx - 2f || x == cx + 1.5f || z == -2f || z == 1.5f;
				if (ring) {
					boolean slot = (Math.round((x + z) * 2f) & 3) == 0;
					v.add(new Voxel(x, 13.5f, z, h, slot ? BLACK : SILVER, 0f));
					v.add(new Voxel(x, 14f, z, h, SILVER, 0f));
				}
			}
		}
		// built from the elbow out to the muzzle
		List<Voxel> out2 = new ArrayList<>();
		for (Voxel a : v) {
			out2.add(new Voxel(a.x(), a.y(), a.z(), a.size(), a.colour(), Mth.clamp(((a.y() - 4f) / 10.5f) * 0.88f + 0.12f * hash(a), 0f, 1f)));
		}
		out2.sort(Comparator.comparingDouble(Voxel::order));
		return out2;
	}

	// ---------------------------------------------------------------- the Arm Saw

	/** How far out the saw is, 0..1: 6 ticks to build when it starts, 6 to fold as it runs out. */
	private static float saw(Player player, float partial) {
		CyborgState s = state(player);
		if (s == null || !s.hasPower || player.level() == null || s.fx.sawUntil <= 0L) {
			return 0f;
		}
		float now = player.level().getGameTime() + partial;
		float left = s.fx.sawUntil - now;
		if (left <= 0f) {
			return 0f;
		}
		float since = now - (s.fx.sawUntil - CyborgConfig.SAW_TICKS);
		return Mth.clamp(Math.min(since / 6f, left / 6f), 0f, 1f);
	}

	private static void renderSaw(PoseStack pose, MultiBufferSource buffers, int light, Player player, ModelPart arm, boolean right, boolean slim,
			float partial, float age) {
		float built = saw(player, partial);
		if (built <= 0f) {
			return;
		}
		if (sawDisc == null) {
			sawDisc = buildSaw();
		}
		int w = slim ? 3 : 4;
		float x0 = right ? (slim ? -2f : -3f) : -1f;
		float outX = right ? x0 - 1.1f : x0 + w + 0.6f; // just outside the forearm, away from the body
		pose.pushPose();
		arm.translateAndRotate(pose);
		// the hub on the outside of the forearm, the disc spinning in the y-z plane
		pose.translate(outX / 16f, 8f / 16f, 0f);
		pose.mulPose(Axis.XP.rotation(age * 1.4f));
		voxels(pose, buffers, light, sawDisc, built, true);
		pose.popPose();
	}

	/** A toothed disc of radius 5 px round the hub (x = the disc's own thickness, half a pixel), built from the hub out. */
	private static List<Voxel> buildSaw() {
		List<Voxel> v = new ArrayList<>();
		float h = 0.5f;
		for (float y = -5.5f; y <= 5.5f; y += h) {
			for (float z = -5.5f; z <= 5.5f; z += h) {
				float r = (float) Math.sqrt((y + h / 2) * (y + h / 2) + (z + h / 2) * (z + h / 2));
				double ang = Math.atan2(z + h / 2, y + h / 2);
				boolean tooth = r > 4.6f && r <= 5.8f && ((int) Math.floor((ang + Math.PI) / (Math.PI / 8))) % 2 == 0;
				if (r > 4.6f && !tooth) {
					continue;
				}
				int colour = r < 1.1f ? DARK : r < 1.7f ? CYAN : tooth ? SILVER : r > 3.9f ? LIGHT : ((int) (r * 2f)) % 2 == 0 ? MID : LIGHT;
				v.add(new Voxel(-h / 2, y, z, h, colour, Mth.clamp(r / 6f, 0f, 1f)));
			}
		}
		v.sort(Comparator.comparingDouble(Voxel::order));
		return v;
	}

	// ---------------------------------------------------------------- the missile launcher

	/** How far up the shoulder launcher is, 0..1: up over 4 ticks at the barrage, down 36 ticks later. */
	private static float launcher(Player player, float partial) {
		CyborgState s = state(player);
		if (s == null || !s.hasPower || player.level() == null || s.animId != CyborgState.ANIM_MISSILES) {
			return 0f;
		}
		float age = player.level().getGameTime() - s.animStart + partial;
		if (age < 0f || age > 40f) {
			return 0f;
		}
		return Mth.clamp(Math.min(age / 4f, (40f - age) / 4f), 0f, 1f);
	}

	/**
	 * A four-tube pod on top of the shoulder ({@code rightShoulder} = the model's right), body-part space: it sits out over
	 * the arm (beside the head, not against it), 5 wide, 4 tall, 4 deep, on a short mount, the tube mouths facing forward.
	 */
	private static List<Voxel> launcherVoxels(boolean rightShoulder) {
		return LAUNCHERS.computeIfAbsent(rightShoulder ? "r" : "l", k -> {
			List<Voxel> v = new ArrayList<>();
			float x0 = rightShoulder ? -10f : 5f;
			// the mount on the shoulder top (the arm's top is y 0 in body space)
			for (float x = x0 + 1.5f; x < x0 + 3.5f; x += 1f) {
				for (float z = -0.5f; z < 1.5f; z += 1f) {
					v.add(new Voxel(x, -1f, z, 1f, DARK, 0f));
				}
			}
			// the pod: x0..x0+5, y -5..-1, z -1.5..2.5; four tube mouths on the front face
			for (float x = x0; x < x0 + 5f; x += 1f) {
				for (float y = -5f; y < -1f; y += 1f) {
					for (float z = -1.5f; z < 2.5f; z += 1f) {
						boolean front = z == -1.5f;
						int colour;
						if (front) {
							boolean mouth = (x == x0 + 1f || x == x0 + 3f) && (y == -5f + 0f || y == -3f + 0f);
							colour = mouth ? BLACK : (x == x0 || x == x0 + 4f) ? DARK : MID;
						} else if (y == -5f) {
							colour = z == 1.5f && x == x0 + 2f ? RED : LIGHT;
						} else if (y == -3f) {
							colour = CYAN; // a glowing band round the pod
						} else {
							colour = x == x0 || x == x0 + 4f ? DARK : MID;
						}
						v.add(new Voxel(x, y, z, 1f, colour, 0f));
					}
				}
			}
			return ordered(v, x0 + 2.5f, -1f, 0.5f);
		});
	}

	// ---------------------------------------------------------------- drawing

	/** {@code v} ordered outward from ({@code ox}, {@code oy}, {@code oz}), scattered: a build a voxel at a time. */
	private static List<Voxel> ordered(List<Voxel> v, float ox, float oy, float oz) {
		float max = 0f;
		for (Voxel a : v) {
			max = Math.max(max, dist(a.x() + a.size() / 2, a.y() + a.size() / 2, a.z(), ox, oy, oz));
		}
		List<Voxel> out = new ArrayList<>();
		for (Voxel a : v) {
			float d = dist(a.x() + a.size() / 2, a.y() + a.size() / 2, a.z(), ox, oy, oz) / Math.max(1e-3f, max);
			out.add(new Voxel(a.x(), a.y(), a.z(), a.size(), a.colour(), Mth.clamp(0.8f * d + 0.2f * hash(a), 0f, 1f)));
		}
		out.sort(Comparator.comparingDouble(Voxel::order));
		return out;
	}

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
			cube(vc, last, lit ? LightTexture.FULL_BRIGHT : light, a.x(), a.y(), a.z(), a.size(), fresh ? CYAN : a.colour());
		}
	}

	/** One voxel cube, its six faces on palette colour {@code colour}. */
	private static void cube(VertexConsumer vc, PoseStack.Pose pose, int light, float px, float py, float pz, float size, int colour) {
		float x0 = px / 16f, x1 = (px + size) / 16f, y0 = py / 16f, y1 = (py + size) / 16f, z0 = pz / 16f, z1 = (pz + size) / 16f;
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
