package com.projecthero.mod.client.ironman;

import java.util.HashMap;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.util.RenderUtil;

/**
 * v0.16.7 (user) Hulkbuster fixes: "the only time the energy should come out is when the armour is flying or when its
 * using the unibeam and then it should only be the vfx and not model parts". The Hulkbuster body's old fx cubes
 * ({@code jet_back}, {@code jet_r}, {@code jet_l}, {@code beam_fx}) are gone from the geo -- those bones are cubeless
 * locators now. Each time a Hulkbuster body is drawn this records where they (and the two palms) really are (offsets
 * from the entity's feet, world axes), per entity; {@code IronManFlightFxClient} puts its thruster jets there and {@code IronManBeamClient} starts the
 * unibeam at the reactor. A record older than {@link #FRESH_TICKS} (not drawn -- first person, off screen) is ignored and
 * the callers fall back to their own maths. Render-thread / client-thread state only.
 */
public final class HulkbusterLocators {
	public static final int BOOT_R = 0, BOOT_L = 1, PALM_R = 2, PALM_L = 3, BACK = 4, BEAM = 5, COUNT = 6;
	/** A capture this many ticks old is stale. */
	public static final int FRESH_TICKS = 3;
	/** The palm: this far down the hand bone from its wrist pivot (model px). */
	private static final float PALM_DOWN = 3.4f;

	/** Per entity id: the captured points (offsets from the entity's feet, world axes), the jet directions and when. */
	private static final Map<Integer, Frame> FRAMES = new HashMap<>();

	public static final class Frame {
		public final Vec3[] pos = new Vec3[COUNT];
		public final Vector3f[] dir = new Vector3f[COUNT];
		long tick;
	}

	private HulkbusterLocators() {
	}

	/** Which locator {@code bone} feeds (-1 = none). */
	public static int slotOf(GeoBone bone) {
		return switch (bone.getName()) {
			case "jet_r" -> BOOT_R;
			case "jet_l" -> BOOT_L;
			case "hand_r" -> PALM_R;
			case "hand_l" -> PALM_L;
			case "jet_back" -> BACK;
			case "beam_fx" -> BEAM;
			default -> -1;
		};
	}

	/**
	 * Record {@code bone} (slot {@code slot}) for {@code entity}: {@code poseStack} is at the bone's parent (as GeoRenderer
	 * hands it to {@code renderRecursively}), {@code origin} the pose matrix at the entity's render origin (its interpolated
	 * feet), {@code partialTick} the frame's.
	 */
	public static void capture(Entity entity, int slot, GeoBone bone, PoseStack poseStack, Matrix4f origin, float partialTick) {
		if (entity == null || entity.level() == null) {
			return;
		}
		poseStack.pushPose();
		RenderUtil.translateMatrixToBone(poseStack, bone);
		RenderUtil.translateToPivotPoint(poseStack, bone);
		RenderUtil.rotateMatrixAroundBone(poseStack, bone);
		RenderUtil.scaleMatrixForBone(poseStack, bone);
		Matrix4f local = new Matrix4f(origin).invert().mul(poseStack.last().pose());
		poseStack.popPose();
		Vector3f p = new Vector3f();
		boolean palm = slot == PALM_R || slot == PALM_L;
		local.transformPosition(0f, palm ? -PALM_DOWN / 16f : 0f, 0f, p);
		Vector3f d = new Vector3f();
		if (slot == BEAM) {
			local.transformDirection(0f, 0f, -1f, d); // the reactor faces the model's front (-z)
		} else {
			local.transformDirection(0f, -1f, 0f, d); // jets / palms push down the limb
		}
		if (d.lengthSquared() > 1.0e-8f) {
			d.normalize();
		}
		Frame f = FRAMES.computeIfAbsent(entity.getId(), k -> new Frame());
		f.pos[slot] = new Vec3(p.x, p.y, p.z);
		f.dir[slot] = d;
		f.tick = entity.level().getGameTime();
	}

	/** The fresh capture for {@code entity} with every slot filled, or null. */
	public static Frame fresh(Entity entity) {
		Frame f = entity == null ? null : FRAMES.get(entity.getId());
		if (f == null || Math.abs(entity.level().getGameTime() - f.tick) > FRESH_TICKS) {
			return null;
		}
		for (int i = 0; i < COUNT; i++) {
			if (f.pos[i] == null) {
				return null;
			}
		}
		return f;
	}

	// ---------------- v0.16.7 (user) Veronica: where each of the six parts is (the pod's plates fly there, sparks / smoke)

	private static final Map<Integer, Vec3[]> PARTS = new HashMap<>();
	private static final Map<Integer, Long> PARTS_TICK = new HashMap<>();

	/** The part {@code bone} marks (-1 = none): the head, the chest, each elbow, each knee. */
	public static int partSlotOf(GeoBone bone) {
		return switch (bone.getName()) {
			case "head" -> com.projecthero.mod.ironman.HulkbusterParts.HEAD;
			case "chest" -> com.projecthero.mod.ironman.HulkbusterParts.TORSO;
			case "forearm_l" -> com.projecthero.mod.ironman.HulkbusterParts.LARM;
			case "forearm_r" -> com.projecthero.mod.ironman.HulkbusterParts.RARM;
			case "shin_l" -> com.projecthero.mod.ironman.HulkbusterParts.LLEG;
			case "shin_r" -> com.projecthero.mod.ironman.HulkbusterParts.RLEG;
			default -> -1;
		};
	}

	/** Record part {@code part} for {@code entity} (same frames as {@link #capture}). */
	public static void capturePart(Entity entity, int part, GeoBone bone, PoseStack poseStack, Matrix4f origin) {
		if (entity == null || entity.level() == null) {
			return;
		}
		poseStack.pushPose();
		RenderUtil.translateMatrixToBone(poseStack, bone);
		RenderUtil.translateToPivotPoint(poseStack, bone);
		RenderUtil.rotateMatrixAroundBone(poseStack, bone);
		RenderUtil.scaleMatrixForBone(poseStack, bone);
		Matrix4f local = new Matrix4f(origin).invert().mul(poseStack.last().pose());
		poseStack.popPose();
		Vector3f p = new Vector3f();
		// a little way into the limb / plate from the joint, and out to the front of the chest / head
		float down = part == com.projecthero.mod.ironman.HulkbusterParts.HEAD ? -4f
				: part == com.projecthero.mod.ironman.HulkbusterParts.TORSO ? -4f : 2f;
		local.transformPosition(0f, -down / 16f, part <= com.projecthero.mod.ironman.HulkbusterParts.TORSO ? -4f / 16f : 0f, p);
		Vec3[] arr = PARTS.computeIfAbsent(entity.getId(), k -> new Vec3[com.projecthero.mod.ironman.HulkbusterParts.COUNT]);
		arr[part] = new Vec3(p.x, p.y, p.z);
		PARTS_TICK.put(entity.getId(), entity.level().getGameTime());
	}

	/** The fresh offset (from the entity's feet, world axes) of {@code part} on {@code entity}, or null. */
	public static Vec3 part(Entity entity, int part) {
		Long t = entity == null ? null : PARTS_TICK.get(entity.getId());
		if (t == null || Math.abs(entity.level().getGameTime() - t) > FRESH_TICKS) {
			return null;
		}
		Vec3[] arr = PARTS.get(entity.getId());
		return arr == null ? null : arr[part];
	}

	/** Forget stale records (once a second from the client tick) and everything when the world is gone. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			FRAMES.clear();
			PARTS.clear();
			PARTS_TICK.clear();
			return;
		}
		long now = client.level.getGameTime();
		if (now % 20L == 0L) {
			FRAMES.values().removeIf(f -> Math.abs(now - f.tick) > 40L);
			PARTS_TICK.entrySet().removeIf(e -> {
				boolean stale = Math.abs(now - e.getValue()) > 40L;
				if (stale) {
					PARTS.remove(e.getKey());
				}
				return stale;
			});
		}
	}
}
