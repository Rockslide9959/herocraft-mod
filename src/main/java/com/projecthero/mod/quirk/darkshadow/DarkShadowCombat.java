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
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7: Dark Shadow's light, darkness, strength and hit code.
 *
 * <p><b>Light at the shadow</b> ({@link #lightAt}): the brightest of the raw light where the creature floats (block light,
 * sky light dimmed by night / rain), a light source held in either hand (its emission minus
 * {@link DarkShadowConfig.Light#heldLightPenalty}) and a running Light Flare (15). Darkness 0..1 follows it linearly between
 * {@link DarkShadowConfig.Light#brightLight} and {@link DarkShadowConfig.Light#pitchLight}; the server smooths it and syncs it
 * ({@link DarkShadow#V_DARK}), and strength and size follow the synced value.
 *
 * <p>Every hit goes through {@link #strike}: the mod-wide targeting rules ({@link HeroTargets#canHarm}); bosses take at most
 * {@link DarkShadowConfig.Berserk#bossMaxFractionPerHit} of their max health a hit and are never knocked about.
 */
public final class DarkShadowCombat {
	public static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.16f, 0.05f, 0.26f), 1.1f);
	public static final DustParticleOptions SHADOW_BIG = new DustParticleOptions(new Vector3f(0.20f, 0.05f, 0.32f), 1.7f);
	public static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.46f, 0.24f, 0.9f), 1.0f);
	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.75f, 0.12f, 0.25f), 1.2f);

	private DarkShadowCombat() {
	}

	private static DarkShadowConfig cfg() {
		return DarkShadowConfig.get();
	}

	private static Vec3 flatLook(Player p) {
		Vec3 l = p.getLookAngle();
		Vec3 f = new Vec3(l.x, 0, l.z);
		return f.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0f, p.getYRot()) : f.normalize();
	}

	/** Where it comes out of: the upper back. */
	public static Vec3 back(Player p) {
		return p.position().add(0, p.isCrouching() ? 1.05 : 1.25, 0).subtract(flatLook(p).scale(0.25));
	}

	/** Where it floats at rest (where its light is read): above and behind the left shoulder. */
	public static Vec3 shadowPos(Player p) {
		Vec3 f = flatLook(p);
		Vec3 left = new Vec3(f.z, 0, -f.x);
		return back(p).add(0, 0.9, 0).add(left.scale(0.35)).subtract(f.scale(0.2));
	}

	/** The light a held item gives off (0 for none). */
	public static int heldLight(Player p) {
		int best = 0;
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack st = p.getItemInHand(hand);
			if (st.getItem() instanceof BlockItem bi) {
				best = Math.max(best, bi.getBlock().defaultBlockState().getLightEmission());
			}
		}
		return best <= 0 ? 0 : Math.max(0, best - cfg().light.heldLightPenalty);
	}

	/** Light at the shadow, 0..15 (see the class comment). */
	public static int lightAt(Player p) {
		int raw = p.level().getMaxLocalRawBrightness(BlockPos.containing(shadowPos(p)));
		int light = Math.max(raw, heldLight(p));
		if (DarkShadow.running(p, DarkShadow.V_FLARE)) {
			light = 15;
		}
		return light;
	}

	/** Darkness 0..1 for a light level (unsmoothed). */
	public static float darknessFor(int light) {
		DarkShadowConfig.Light l = cfg().light;
		return Mth.clamp((l.brightLight - light) / (float) Math.max(1, l.brightLight - l.pitchLight), 0f, 1f);
	}

	public static boolean inDarkness(Player p) {
		return lightAt(p) <= cfg().light.darkLight;
	}

	/** The damage multiplier now: from the synced darkness (Berserk: its own). */
	public static float strength(ServerPlayer p) {
		if (DarkShadow.berserk(p)) {
			return cfg().berserk.strength;
		}
		return strengthAt(DarkShadow.darkness(p));
	}

	public static float strengthAt(float darkness) {
		return Mth.lerp(Mth.clamp(darkness, 0f, 1f), cfg().light.strengthMin, cfg().light.strengthMax);
	}

	/** A range that grows with the dark. */
	public static double range(ServerPlayer p, double min, double max) {
		return Mth.lerp(DarkShadow.darkness(p), min, max);
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= cfg().berserk.bossHealthThreshold || TitanCombat.isBoss(e);
	}

	public static boolean isTarget(ServerPlayer owner, LivingEntity e) {
		return e != owner && HeroTargets.canHarm(owner, e);
	}

	/** One hit: damage (boss-capped), knockback away from {@code origin} and {@code lift}. True if it landed. */
	public static boolean strike(ServerPlayer owner, LivingEntity target, Vec3 origin, float damage, double knockback, double lift) {
		if (!isTarget(owner, target)) {
			return false;
		}
		boolean boss = isBoss(target);
		float dmg = boss ? Math.min(damage, target.getMaxHealth() * cfg().berserk.bossMaxFractionPerHit) : damage;
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
		level.sendParticles(SHADOW, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 4, 0.2, 0.25, 0.2, 0.03);
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
	 * What a berserk Dark Shadow goes for, nearest first (threats first): any creature it may harm -- never a squadmate,
	 * a friendly pet, a villager (config). Its own user is handled separately (it turns on them too, non-lethally).
	 */
	public static List<LivingEntity> frenzyTargets(ServerPlayer p, double radius) {
		Vec3 c = p.position().add(0, 1.0, 0);
		List<LivingEntity> all = AbilityHelpers.living(p.serverLevel(), c, radius, e -> e != p && frenzyMayHit(p, e));
		all.sort(Comparator.<LivingEntity>comparingInt(e -> HeroTargets.isHostile(p, e) ? 0 : 1)
				.thenComparingDouble(e -> AbilityHelpers.distanceSqToBox(e, c)));
		return all;
	}

	public static boolean frenzyMayHit(ServerPlayer p, LivingEntity e) {
		if (e == p || !isTarget(p, e) || (cfg().berserk.spareVillagers && e instanceof AbstractVillager)) {
			return false;
		}
		return !(e instanceof Player other && Squads.areAllies(p, other));
	}

	/** A cloud of shadow at {@code c}. */
	public static void smoke(ServerLevel level, Vec3 c, int count, double spread) {
		level.sendParticles(SHADOW, c.x, c.y, c.z, count, spread, spread, spread, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, Math.max(1, count / 4), spread * 0.6, spread * 0.6, spread * 0.6, 0.01);
	}

	/** Shadow feathers bursting out from {@code c} (the nova, the wings). */
	public static void feathers(ServerLevel level, Vec3 c, int count, double speed) {
		level.sendParticles(SHADOW_BIG, c.x, c.y, c.z, count, 0.4, 0.4, 0.4, 0.0);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, count / 2, 0.2, 0.2, 0.2, speed);
		level.sendParticles(VIOLET, c.x, c.y, c.z, count / 3, 0.8, 0.6, 0.8, 0.0);
	}

	/** A shadow streak from {@code from} to {@code to} (the lunge's after-image, a tendril). */
	public static void streak(ServerLevel level, Vec3 from, Vec3 to) {
		AbilityHelpers.line(level, from, to, SHADOW, 3.0);
	}

	public static float clampMeterInLight(int light, float m) {
		return light >= cfg().light.calmLight ? Math.min(m, 99f) : m;
	}

	static boolean quirkHolder(Player p) {
		return Quirks.hasPower(p);
	}
}
