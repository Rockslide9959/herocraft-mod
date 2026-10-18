package com.projecthero.mod.quirk.darkshadow;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.quirk.Quirks;
import com.projecthero.mod.squad.Squads;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow's light, strength and hit code. Every hit goes through {@link #strike}: the mod-wide targeting
 * rules ({@link HeroTargets#canHarm}: never the user, a shielded squadmate or a friendly pet; players only with PvP on);
 * bosses take at most {@link DarkShadowConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never
 * knocked about. Dark Shadow's own frenzy ({@link #frenzyTargets}) is stricter still: never a squadmate even with
 * friendly fire on, never a villager.
 */
public final class DarkShadowCombat {
	/** Its colours: the purple-black body and the faint violet smoke round its edges. */
	public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.16f, 0.04f, 0.26f), 1.5f);
	public static final DustParticleOptions SHADOW_BIG = new DustParticleOptions(new Vector3f(0.22f, 0.05f, 0.36f), 2.4f);
	public static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.48f, 0.24f, 1.0f), 1.0f);

	private DarkShadowCombat() {
	}

	/** Light at the eyes, 0..15: the brighter of block light and sky light (sky light dimmed at night, in rain). */
	public static int lightAt(Player p) {
		BlockPos at = BlockPos.containing(p.getEyePosition());
		return p.level().getMaxLocalRawBrightness(at);
	}

	public static boolean inDarkness(Player p) {
		return lightAt(p) <= DarkShadowConfig.DARK_LIGHT;
	}

	/** The damage multiplier for the current Shadow meter (weak in daylight, strong in the dark). */
	public static float strength(ServerPlayer p) {
		return strengthAt(Quirks.meter(p));
	}

	public static float strengthAt(float meter) {
		return DarkShadowConfig.STRENGTH_MIN + (DarkShadowConfig.STRENGTH_MAX - DarkShadowConfig.STRENGTH_MIN) * (meter / 100f);
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= DarkShadowConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return HeroTargets.canHarm(owner, e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * DarkShadowConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1f, dmg))) {
			return false;
		}
		if (!boss) {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		Vec3 c = center(target);
		ServerLevel level = owner.serverLevel();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(SHADOW, c.x, c.y, c.z, 8, 0.3, 0.35, 0.3, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.02);
		return true;
	}

	public static Vec3 center(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.55, 0);
	}

	/** The creature under the crosshair, else the valid one nearest the crosshair within {@code degrees} and {@code range} in sight. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range),
				e -> e != p && e.isAlive() && isTarget(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > range) {
				continue;
			}
			double dot = to.scale(1.0 / d).dot(look);
			if (dot > bestDot && p.hasLineOfSight(le)) {
				bestDot = dot;
				best = le;
			}
		}
		return best;
	}

	/**
	 * What an uncontrolled Dark Shadow (Berserk, Abyssal Rampage) goes for, nearest first: threats first, then any
	 * other creature it may harm -- never its user, a squadmate (friendly fire or not), a friendly pet or a villager.
	 */
	public static List<LivingEntity> frenzyTargets(ServerPlayer p, double radius) {
		Vec3 c = p.position().add(0, 1.0, 0);
		List<LivingEntity> all = AbilityHelpers.living(p.serverLevel(), c, radius, e -> e != p && frenzyMayHit(p, e));
		all.sort(Comparator.<LivingEntity>comparingInt(e -> HeroTargets.isHostile(p, e) ? 0 : 1)
				.thenComparingDouble(e -> AbilityHelpers.distanceSqToBox(e, c)));
		return all;
	}

	public static boolean frenzyMayHit(ServerPlayer p, LivingEntity e) {
		if (e == p || !isTarget(p, e) || e instanceof AbstractVillager) {
			return false;
		}
		return !(e instanceof Player other && Squads.areAllies(p, other));
	}

	/** A cloud of shadow at {@code c}. */
	public static void smoke(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(SHADOW, c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, Math.max(1, count / 4), spread * 0.6, spread * 0.6, spread * 0.6, 0.01);
	}

	/** The tendril from the user's back to {@code to} (particles, for every viewer -- the model draws the body). */
	public static void tendril(ServerLevel level, Vec3 from, Vec3 to) {
		AbilityHelpers.line(level, from, to, SHADOW, 2.5);
	}
}
