package com.projecthero.mod.hero.revamp.batcha;

import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.13.22 revamp batch A (Strength, Laser Vision, Flight, Super Speed, Super Regeneration, Super Durability):
 * small shared helpers -- resource access that skips no-op writes (every resource write re-syncs the whole
 * experimental state), one-shot animations that hold off looping stance poses for a moment, ally checks and
 * a couple of particle shapes.
 */
public final class BatchA {
	private BatchA() {
	}

	// ---------------- resources ----------------

	public static float res(ServerPlayer p, String powerKey, String name) {
		Power power = Powers.byKey(powerKey);
		return power == null ? 0f : ExperimentalPowers.getResource(p, power, name);
	}

	/** Writes {@code value} (clamped to {@code 0..max}) only when it actually changes. */
	public static void set(ServerPlayer p, String powerKey, String name, float value, float max) {
		Power power = Powers.byKey(powerKey);
		if (power == null) {
			return;
		}
		float v = Math.max(0f, Math.min(max, value));
		if (Math.abs(ExperimentalPowers.getResource(p, power, name) - v) > 1.0e-4f) {
			ExperimentalPowers.setResource(p, power, name, v, max);
		}
	}

	public static void set(ServerPlayer p, String powerKey, String name, float value) {
		set(p, powerKey, name, value, 1.0e9f);
	}

	public static void add(ServerPlayer p, String powerKey, String name, float delta, float max) {
		set(p, powerKey, name, res(p, powerKey, name) + delta, max);
	}

	/** Counts a countdown resource down by one tick; returns the new value. */
	public static float countDown(ServerPlayer p, String powerKey, String name) {
		float v = res(p, powerKey, name);
		if (v <= 0f) {
			return 0f;
		}
		v = Math.max(0f, v - 1f);
		set(p, powerKey, name, v);
		return v;
	}

	// ---------------- animation ----------------

	/**
	 * The shared library's looping poses: somebody's channel is running -- a stance never cuts in on one. Laser Vision's
	 * held beams (v0.14.5 renamed them from {@code beam_eyes} to {@code p02.*}) are channels too: the beam renderer draws
	 * from that animation, so a stance (Super Strength's carry / rush, Super Speed's carry ...) replacing it after 30 ticks
	 * made the beam vanish for every viewer while the server kept burning them.
	 */
	private static final java.util.Set<String> CHANNELS = java.util.Set.of("channel_right", "channel_two_hand", "beam_eyes",
			"scream", "guard", "shield_brace", "crouch_charge", "spin_arms", "p02.beam", "p02.max", "p02.max_charge");

	/** Whether {@code anim} is a channel a stance never replaces. */
	public static boolean isChannel(String anim) {
		return CHANNELS.contains(anim);
	}
	/** The stance loops this batch re-asserts every tick; any of them may replace another. */
	private static final java.util.Set<String> STANCES = java.util.Set.of("float_arms", "carry_overhead", "p01.rush",
			"p03.superman", "p03.carry", "p04.carry");

	/**
	 * Plays a one-shot pose. {@code holdTicks} is how long it needs before a stance loop may take over again -- kept
	 * for readability at the call sites; {@link #stance} judges it from the shared animation clock instead.
	 */
	public static void play(ServerPlayer p, String powerKey, String anim, int holdTicks) {
		MutationVisuals.play(p, anim);
	}

	public static void play(ServerPlayer p, String powerKey, String anim) {
		MutationVisuals.play(p, anim);
	}

	/**
	 * Re-asserts a looping stance pose (flying, carrying ...) without trampling anything else: never over a channel,
	 * over a batch-A or shared one-shot only once it has had 30 ticks to finish, over another batch's pose after 60.
	 */
	public static void stance(ServerPlayer p, String powerKey, String anim) {
		com.projecthero.mod.hero.visual.MutationVisualState s = MutationVisuals.state(p);
		String cur = s.anim();
		if (cur.equals(anim)) {
			return;
		}
		if (!cur.isEmpty() && !STANCES.contains(cur)) {
			if (CHANNELS.contains(cur)) {
				return;
			}
			long age = p.level().getGameTime() - s.animStart();
			boolean foreign = cur.startsWith("p") && cur.length() > 3 && cur.charAt(3) == '.' && !isBatchA(cur);
			if (age >= 0 && age < (foreign ? 60 : 30)) {
				return;
			}
		}
		MutationVisuals.play(p, anim);
	}

	private static boolean isBatchA(String anim) {
		return anim.startsWith("p01.") || anim.startsWith("p02.") || anim.startsWith("p03.") || anim.startsWith("p04.")
				|| anim.startsWith("p12.") || anim.startsWith("p13.");
	}


	// ---------------- allies ----------------

	/**
	 * Someone this player's support moves (Slipstream, Mend) are meant for: a squad-mate, one of their own
	 * tamed animals, a villager or an iron golem. Never the player themselves.
	 */
	public static boolean isAlly(ServerPlayer player, LivingEntity e) {
		if (e == player || !e.isAlive()) {
			return false;
		}
		if (e instanceof Player other) {
			return com.projecthero.mod.squad.Squads.areAllies(player, other); // v0.14.4: not a rampaging Hulk
		}
		if (e instanceof TamableAnimal pet) {
			return pet.isTame() && player.getUUID().equals(pet.getOwnerUUID());
		}
		return e instanceof AbstractVillager || e instanceof IronGolem;
	}

	// ---------------- particles ----------------

	/** A flat ring of particles around {@code c}. */
	public static void ring(ServerLevel level, Vec3 c, double radius, ParticleOptions particle, int points, double speed) {
		for (int i = 0; i < points; i++) {
			double a = i / (double) points * Math.PI * 2;
			double dx = Math.cos(a);
			double dz = Math.sin(a);
			level.sendParticles(particle, c.x + dx * radius, c.y, c.z + dz * radius, 1, dx * speed, 0.02, dz * speed, 1.0);
		}
	}

	/** A ring of block-crack debris sampling whatever the ground is made of around {@code c}. */
	public static void debrisRing(ServerLevel level, Vec3 c, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = i / (double) points * Math.PI * 2;
			double bx = c.x + Math.cos(a) * radius;
			double bz = c.z + Math.sin(a) * radius;
			BlockPos gp = BlockPos.containing(bx, c.y - 0.5, bz);
			var state = level.getBlockState(gp);
			if (state.isAir()) {
				state = net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState();
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), bx, c.y + 0.1, bz, 6, 0.25, 0.15, 0.25, 0.15);
		}
	}

	/** The horizontal unit vector the player faces. */
	public static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** True when {@code e} is inside the cone of half-angle {@code acos(minDot)} along the look direction. */
	public static boolean inCone(ServerPlayer p, LivingEntity e, double minDot) {
		Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		return to.normalize().dot(p.getLookAngle()) >= minDot;
	}
}
