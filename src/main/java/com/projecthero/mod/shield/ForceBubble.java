package com.projecthero.mod.shield;

import java.util.function.Predicate;

import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.15: a reusable held bubble shield -- the server half (the client half is
 * {@code client.shield.ForceBubbleRenderer}). First used by Nova's Force Field (gold); built to be shared, so Green
 * Lantern's Z shield can switch to the same bubble in green ({@link Style#GREEN_LANTERN}).
 *
 * <p>The power that owns the bubble keeps its own "is it up" state and upkeep; this class only does what every bubble
 * does the same way:
 * <ul>
 *   <li>{@link #blocks(DamageSource)} -- would the bubble stop this hit? (every projectile and every blow struck in
 *       person; explosions, fire, magic, falls and {@code /kill} pass through). Call it from the power's damage hook and
 *       cancel the hit when it says yes, then call {@link #absorb};</li>
 *   <li>{@link #absorb} -- the spark where the hit met the bubble, the sound, and a melee attacker shoved back;</li>
 *   <li>{@link #tick} -- once a server tick while it is up: every projectile not fired by the owner that comes inside the
 *       bubble is destroyed (so arrows never even land), plus a light sparkle on the surface.</li>
 * </ul>
 */
public final class ForceBubble {
	/**
	 * How a bubble looks: {@code rgb} the soft shell, {@code hotRgb} the bright rings / lattice, {@code accentRgb} one
	 * accent ring, {@code dust} / {@code dustBig} the server particles, {@code radius} in blocks (from the body's middle).
	 */
	public record Style(int rgb, int hotRgb, int accentRgb, DustParticleOptions dust, DustParticleOptions dustBig, double radius) {
		/** Nova's Force Field: gold with a cyan accent. */
		public static final Style NOVA = new Style(0xFFC83C, 0xFFF0B0, 0x8BF8FF,
				new DustParticleOptions(new Vector3f(1.0f, 0.80f, 0.24f), 1.3f),
				new DustParticleOptions(new Vector3f(1.0f, 0.84f, 0.30f), 2.2f), 1.8);
		/** Green Lantern's (for the planned switch): ring green with a pale-green accent. */
		public static final Style GREEN_LANTERN = new Style(0x3CFF5A, 0xC8FFD0, 0xE8FFE8,
				new DustParticleOptions(new Vector3f(0.24f, 1.0f, 0.35f), 1.3f),
				new DustParticleOptions(new Vector3f(0.40f, 1.0f, 0.50f), 2.2f), 1.8);
	}

	private ForceBubble() {
	}

	/** The middle of the bubble round {@code owner} (half his height up). */
	public static Vec3 center(LivingEntity owner) {
		return owner.position().add(0, owner.getBbHeight() * 0.5, 0);
	}

	/** Would a bubble stop this hit? Every projectile and every blow struck in person. */
	public static boolean blocks(DamageSource source) {
		return isProjectile(source) || isMelee(source);
	}

	public static boolean isProjectile(DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile;
	}

	/** A blow struck in person: the attacker is the thing that hit (not an arrow, not an explosion, not magic). */
	public static boolean isMelee(DamageSource source) {
		return source.getEntity() instanceof LivingEntity && source.getDirectEntity() == source.getEntity()
				&& !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE)
				&& !source.is(DamageTypes.MAGIC) && !source.is(DamageTypes.INDIRECT_MAGIC);
	}

	/**
	 * The hit was stopped: a spark on the bubble where it came from, the clang, and a melee attacker shoved back (unless
	 * {@code immovable} says it must not be moved, e.g. a boss).
	 */
	public static void absorb(ServerPlayer owner, DamageSource source, Style style, Predicate<LivingEntity> immovable) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = center(owner);
		if (source.getDirectEntity() != null) {
			Vec3 from = source.getDirectEntity().position().add(0, source.getDirectEntity().getBbHeight() * 0.5, 0);
			Vec3 dir = from.subtract(c);
			Vec3 at = dir.lengthSqr() < 1.0e-6 ? c : c.add(dir.normalize().scale(style.radius()));
			spark(level, at, style);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f, 1.4f);
		if (isMelee(source) && source.getEntity() instanceof LivingEntity attacker && attacker != owner
				&& (immovable == null || !immovable.test(attacker))) {
			AbilityHelpers.knockbackFrom(attacker, owner.position(), 0.9);
		}
	}

	/**
	 * One server tick of a raised bubble: destroys every projectile inside it that {@code owner} did not fire, and
	 * sparkles the surface every few ticks. Returns how many projectiles it stopped.
	 */
	public static int tick(ServerPlayer owner, Style style) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = center(owner);
		double r = style.radius() + 0.6;
		int stopped = 0;
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, new AABB(c, c).inflate(r))) {
			if (proj.getOwner() == owner || proj.isRemoved() || proj.position().distanceToSqr(c) > r * r) {
				continue;
			}
			Vec3 at = proj.position();
			spark(level, at, style);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.6f);
			proj.discard();
			stopped++;
		}
		if (owner.tickCount % 4 == 0) {
			for (int i = 0; i < 6; i++) {
				double a = owner.getRandom().nextDouble() * Math.PI * 2;
				double b = Math.acos(2 * owner.getRandom().nextDouble() - 1);
				double rr = style.radius();
				level.sendParticles(style.dust(), c.x + rr * Math.sin(b) * Math.cos(a), c.y + rr * Math.cos(b),
						c.z + rr * Math.sin(b) * Math.sin(a), 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		return stopped;
	}

	/** The raise: a burst over the whole surface. */
	public static void raise(ServerPlayer owner, Style style) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = center(owner);
		level.sendParticles(style.dustBig(), c.x, c.y, c.z, 40, style.radius() * 0.55, style.radius() * 0.55, style.radius() * 0.55, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.2f);
	}

	/** The drop: a soft fizzle. */
	public static void drop(ServerPlayer owner, Style style) {
		ServerLevel level = (ServerLevel) owner.level();
		Vec3 c = center(owner);
		level.sendParticles(style.dust(), c.x, c.y, c.z, 24, style.radius() * 0.5, style.radius() * 0.5, style.radius() * 0.5, 0.0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.7f, 1.8f);
	}

	private static void spark(ServerLevel level, Vec3 at, Style style) {
		level.sendParticles(style.dustBig(), at.x, at.y, at.z, 8, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
	}
}
