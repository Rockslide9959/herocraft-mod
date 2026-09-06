package com.projecthero.mod.firearm;

import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Decides whether a bullet counts as a headshot, in a way that scales with any mob's dimensions
 * (spec section 8).
 *
 * <p>The check is a real ray/box intersection against a small "head box" -- the top slice of the
 * target's bounding box, sized from its eye height so it tracks the actual head, with a floor so
 * tiny mobs still have a usable head and a small forgiveness margin. This is far more reliable than
 * inspecting the AABB entry point (which vanilla's {@code getEntityHitResult} does not always give
 * cleanly -- e.g. a point-blank shot reports the shooter's eye instead). Per-{@link EntityType}
 * overrides in {@link #OVERRIDES} handle mobs with no meaningful head (slimes) or an oversized one.
 */
public final class HeadshotResolver {
	/** entityType -> fraction of the bounding-box height (from the top) that is the head; 0 = no head. */
	private static final Map<EntityType<?>, Float> OVERRIDES = new IdentityHashMap<>();

	static {
		OVERRIDES.put(EntityType.SLIME, 0f);
		OVERRIDES.put(EntityType.MAGMA_CUBE, 0f);
		OVERRIDES.put(EntityType.GHAST, 0f);
		OVERRIDES.put(EntityType.SHULKER, 0f);
		OVERRIDES.put(EntityType.ENDER_DRAGON, 0f);
		OVERRIDES.put(EntityType.WARDEN, 0.30f);
		OVERRIDES.put(EntityType.IRON_GOLEM, 0.30f);
		OVERRIDES.put(EntityType.RAVAGER, 0.34f);
		OVERRIDES.put(EntityType.HOGLIN, 0.34f);
		OVERRIDES.put(EntityType.ZOGLIN, 0.34f);
	}

	private HeadshotResolver() {
	}

	/**
	 * @param target   the living entity that was hit
	 * @param rayStart the shot's origin (player eye)
	 * @param rayEnd   the shot's far point
	 * @return true if the shot line passes through the target's head box
	 */
	public static boolean isHeadshot(LivingEntity target, Vec3 rayStart, Vec3 rayEnd) {
		float h = target.getBbHeight();
		if (h <= 0.05f) {
			return false;
		}
		Float override = OVERRIDES.get(target.getType());
		float headFraction;
		if (override != null) {
			if (override <= 0f) {
				return false;
			}
			headFraction = override;
		} else {
			float eyeFrac = Math.min(0.95f, target.getEyeHeight() / h);
			headFraction = Math.max(0.24f, (1f - eyeFrac) + 0.14f); // a little forgiveness
		}

		AABB body = target.getBoundingBox();
		double headBottom = target.getY() + h * (1f - headFraction);
		double pad = Math.min(0.12, body.getXsize() * 0.12); // small outward margin
		AABB headBox = new AABB(
				body.minX - pad, headBottom - 0.08, body.minZ - pad,
				body.maxX + pad, target.getY() + h + 0.05, body.maxZ + pad);

		return headBox.clip(rayStart, rayEnd).isPresent();
	}

	/** Point-based fallback (kept for the gametest and any caller without the ray). */
	public static boolean isHeadshot(LivingEntity target, Vec3 hit) {
		return isHeadshot(target, hit.add(0, 0, 0), hit.add(hit.subtract(target.position()).normalize().scale(0.01)))
				|| pointInHead(target, hit);
	}

	private static boolean pointInHead(LivingEntity target, Vec3 hit) {
		float h = target.getBbHeight();
		if (h <= 0.05f) {
			return false;
		}
		Float override = OVERRIDES.get(target.getType());
		float headFraction;
		if (override != null) {
			if (override <= 0f) {
				return false;
			}
			headFraction = override;
		} else {
			float eyeFrac = Math.min(0.95f, target.getEyeHeight() / h);
			headFraction = Math.max(0.24f, (1f - eyeFrac) + 0.14f);
		}
		return hit.y - target.getY() >= h * (1f - headFraction) - 0.08;
	}
}
