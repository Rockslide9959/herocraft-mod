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

	/** Forget stale records (once a second from the client tick) and everything when the world is gone. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			FRAMES.clear();
			return;
		}
		long now = client.level.getGameTime();
		if (now % 20L == 0L) {
			FRAMES.values().removeIf(f -> Math.abs(now - f.tick) > 40L);
		}
	}
}
