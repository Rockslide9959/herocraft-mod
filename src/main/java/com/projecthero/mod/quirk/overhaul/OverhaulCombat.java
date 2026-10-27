package com.projecthero.mod.quirk.overhaul;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Overhaul's shared hit code and his look. Every hit goes through {@link #strike}: the mod-wide targeting rules
 * ({@link HeroTargets}: never himself, a squadmate or a friendly pet; players only with PvP on); bosses take at most
 * {@link OverhaulConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health a hit and are never knocked about. A body he
 * touches breaks into cubes and red dust.
 */
public final class OverhaulCombat {
	/** The red of a disassembled body. */
	public static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.78f, 0.06f, 0.08f), 1.2f);
	public static final DustParticleOptions RED_BIG = new DustParticleOptions(new Vector3f(0.62f, 0.02f, 0.05f), 2.0f);
	/** The teal-white of a body put back together. */
	public static final DustParticleOptions MEND = new DustParticleOptions(new Vector3f(0.62f, 0.95f, 0.86f), 1.0f);
	private static final BlockParticleOption FLESH = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState());

	private OverhaulCombat() {
	}

	public static boolean isBoss(LivingEntity e) {
		return TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && e.isAlive() && HeroTargets.canHarm(owner, e);
	}

	/** Fusion hits harder; v0.17.1 Germophobe: the first move after a mob hit him, +20%. */
	public static float scaled(ServerPlayer p, float base) {
		float d = Overhaul.fused(p) ? base * OverhaulConfig.FUSION_DAMAGE_MULT : base;
		return OverhaulMoves.germophobe(p) ? d * OverhaulConfig.GERMOPHOBE_MULT : d;
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * OverhaulConfig.BOSS_MAX_FRACTION_PER_HIT) : damage;
		Vec3 before = target.getDeltaMovement();
		if (!AbilityHelpers.hurtBurst(owner, target, owner.level().damageSources().playerAttack(owner), Math.max(1f, dmg))) {
			return false;
		}
		if (boss) {
			// a boss is never knocked about -- not even by the hit's own vanilla knockback
			target.setDeltaMovement(before);
			target.hurtMarked = true;
		} else {
			if (knockback > 0.0) {
				AbilityHelpers.knockbackFrom(target, origin, knockback);
			}
			if (lift > 0.0) {
				AbilityHelpers.push(target, new Vec3(0.0, lift, 0.0));
			}
		}
		disassembleFx(owner.serverLevel(), target, 1f);
		if (!target.isAlive()) {
			// v0.17.1: a body he kills bursts into shards, and its matter is his
			OverhaulFx.burst(owner.serverLevel(), target, true);
			com.projecthero.mod.quirk.fx.QuirkSounds.play(owner.serverLevel(), target.position(), OverhaulSounds.DISASSEMBLE, 1.1f, 0.8f);
			com.projecthero.mod.quirk.Quirks.addMeter(owner, OverhaulConfig.MATTER_PER_KILL);
		} else {
			OverhaulFx.burst(owner.serverLevel(), target, false);
		}
		return true;
	}

	/** A body breaking into cubes and red dust ({@code size} 1 = a hit, 3 = it came apart). */
	public static void disassembleFx(ServerLevel level, LivingEntity e, float size) {
		Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
		double w = e.getBbWidth() * 0.5;
		int n = (int) (6 * size);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, n, w, e.getBbHeight() * 0.3, w, 0.25);
		level.sendParticles(size > 1.5f ? RED_BIG : RED, c.x, c.y, c.z, n + 2, w, e.getBbHeight() * 0.3, w, 0.0);
		level.sendParticles(FLESH, c.x, c.y, c.z, n * 2, w, e.getBbHeight() * 0.3, w, 0.2);
	}

	/** The creature under the crosshair, else the valid one nearest the crosshair within {@code degrees} and {@code range} that he can see. */
	public static @Nullable LivingEntity aimed(ServerPlayer p, double range, double degrees) {
		LivingEntity direct = AbilityHelpers.raycastEntity(p, range);
		if (direct != null && isTarget(p, direct)) {
			return direct;
		}
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double bestDot = Math.cos(Math.toRadians(degrees));
		LivingEntity best = null;
		for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(range), e -> isTarget(p, e))) {
			Vec3 to = le.position().add(0, le.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d < 0.3 || d > range + le.getBbWidth() * 0.5) {
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

	/** His touch reach (Fusion reaches further). */
	public static double reach(ServerPlayer p) {
		return OverhaulConfig.TOUCH_RANGE + (Overhaul.fused(p) ? OverhaulConfig.FUSION_REACH_BONUS : 0.0);
	}
}
