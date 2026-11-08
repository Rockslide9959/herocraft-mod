package com.projecthero.mod.andy;

import com.projecthero.mod.andy.data.AndyState;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: where on Andy a hit lands. All in the player's bounding box, seen from his body's facing:
 * <ul>
 *   <li>the top quarter is the head;</li>
 *   <li>the bottom 37.5% are the legs, right or left by the side of the hit;</li>
 *   <li>between: the body, or an arm when the hit is out to the side (more than a quarter of the width off centre).</li>
 * </ul>
 * A projectile lands where it is; a melee swing at the attacker's reaching height (eyes minus half a block) and on his side,
 * with a little scatter; a fall on both legs; an explosion on every part ({@link AndyConfig#BLAST_SHARE} each). Damage with no
 * place (fire, drowning, poison, hunger, magic) hits no part at all, only health.
 */
public final class AndyParts {
	private AndyParts() {
	}

	/** His right, flat, from the body's facing (yaw 0 faces +z, and his right is then -x). */
	public static Vec3 right(Player player) {
		double yaw = Math.toRadians(player.yBodyRot);
		return new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
	}

	/** The part a point in or near the player's box belongs to. */
	public static int locate(Player player, Vec3 hit) {
		AABB box = player.getBoundingBox();
		double h = Math.max(0.1, box.getYsize());
		double rel = (hit.y - box.minY) / h;
		Vec3 centre = box.getCenter();
		double side = new Vec3(hit.x - centre.x, 0, hit.z - centre.z).dot(right(player));
		double half = box.getXsize() * 0.5;
		boolean rightSide = side > 0;
		if (player.getPose() == net.minecraft.world.entity.Pose.SWIMMING || player.getPose() == net.minecraft.world.entity.Pose.FALL_FLYING) {
			// lying flat: the box is short; read it along the body instead -- everything is "the body" but a near-ground hit
			rel = Math.max(0.3, Math.min(0.7, rel));
		}
		if (rel >= 0.75) {
			return AndyState.HEAD;
		}
		if (rel < 0.375) {
			return rightSide ? AndyState.RIGHT_LEG : AndyState.LEFT_LEG;
		}
		if (Math.abs(side) > half * 0.5) {
			return rightSide ? AndyState.RIGHT_ARM : AndyState.LEFT_ARM;
		}
		return AndyState.BODY;
	}

	/**
	 * How much of {@code amount} lands on each part (index {@link AndyState#HEAD}..{@link AndyState#LEFT_LEG}); all zero when
	 * the damage has no place.
	 */
	public static float[] assign(Player player, DamageSource source, float amount, RandomSource random) {
		float[] out = new float[AndyState.PARTS];
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.STALAGMITE)) {
			out[AndyState.RIGHT_LEG] = amount * 0.5f;
			out[AndyState.LEFT_LEG] = amount * 0.5f;
			return out;
		}
		if (source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL) || source.is(DamageTypes.FALLING_STALACTITE)) {
			out[AndyState.HEAD] = amount;
			return out;
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			for (int i = 0; i < AndyState.PARTS; i++) {
				out[i] = amount * AndyConfig.BLAST_SHARE;
			}
			return out;
		}
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		if (direct != null && direct != attacker) {
			// a projectile: where it is now (it is touching him)
			out[locate(player, clampInto(player, direct.getBoundingBox().getCenter()))] = amount;
			return out;
		}
		if (attacker != null && attacker != player) {
			out[locate(player, meleePoint(player, attacker, random))] = amount;
			return out;
		}
		return out;
	}

	/** Where a melee swing lands: the attacker's reaching height and side, scattered a little. */
	public static Vec3 meleePoint(Player victim, Entity attacker, RandomSource random) {
		double reach = attacker instanceof LivingEntity le ? le.getEyeY() - 0.5 : attacker.getY() + attacker.getBbHeight() * 0.6;
		reach += (random.nextDouble() - 0.5) * 0.5;
		Vec3 centre = victim.getBoundingBox().getCenter();
		Vec3 r = right(victim);
		double side = new Vec3(attacker.getX() - centre.x, 0, attacker.getZ() - centre.z).dot(r) * 0.4 + (random.nextDouble() - 0.5) * 0.5;
		return clampInto(victim, new Vec3(centre.x + r.x * side, reach, centre.z + r.z * side));
	}

	static Vec3 clampInto(Player player, Vec3 p) {
		AABB b = player.getBoundingBox();
		return new Vec3(Math.max(b.minX, Math.min(b.maxX, p.x)), Math.max(b.minY, Math.min(b.maxY - 0.01, p.y)), Math.max(b.minZ, Math.min(b.maxZ, p.z)));
	}

	/** Roughly where a part is in the world (for sounds and particles). */
	public static Vec3 centre(Player player, int part) {
		Vec3 r = right(player);
		double h = player.getBbHeight();
		Vec3 base = player.position();
		return switch (part) {
			case AndyState.HEAD -> base.add(0, h * 0.87, 0);
			case AndyState.RIGHT_ARM -> base.add(r.scale(0.36)).add(0, h * 0.6, 0);
			case AndyState.LEFT_ARM -> base.add(r.scale(-0.36)).add(0, h * 0.6, 0);
			case AndyState.RIGHT_LEG -> base.add(r.scale(0.12)).add(0, h * 0.2, 0);
			case AndyState.LEFT_LEG -> base.add(r.scale(-0.12)).add(0, h * 0.2, 0);
			default -> base.add(0, h * 0.56, 0);
		};
	}
}
