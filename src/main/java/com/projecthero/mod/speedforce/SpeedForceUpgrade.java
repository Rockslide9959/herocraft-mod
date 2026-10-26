package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.device.ModDevices;
import com.projecthero.mod.hero.power.p04.SuperSpeedHandlers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Speed Force awakening -- how a plain Speedster becomes the Speed Force Speedster. All four at once:
 * <ol>
 *   <li>already a Speedster (the Super Speed mutation);</li>
 *   <li>the vanilla <b>Speed</b> effect on;</li>
 *   <li>a <b>Tachyon Device</b> in either hand;</li>
 *   <li>running in <b>Overdrive</b>;</li>
 * </ol>
 * -- then run over a block of <b>Charged Copper Plates</b> or get <b>struck by lightning</b>, and the Speed Force takes you
 * every time: the mutation goes and you become the Speed Force Speedster (Hero-Tier, the one Primary slot). The old
 * origin (Strength + Speed + Jump Boost and a surge -> the plain Speedster, {@code flash.SpeedForce}) is unchanged.
 */
public final class SpeedForceUpgrade {
	public enum Check { READY, ALREADY, NOT_SPEEDSTER, NO_SPEED, NO_TACHYON, NOT_OVERDRIVE }

	/** Last position of every plain Speedster (to sweep the path for a plate at 120 b/s). */
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_HINT = new ConcurrentHashMap<>();
	private static final int HINT_COOLDOWN = 40;

	private SpeedForceUpgrade() {
	}

	static void clearSessionState() {
		LAST_POS.clear();
		LAST_HINT.clear();
	}

	public static boolean holdsTachyon(ServerPlayer p) {
		return p.getItemInHand(InteractionHand.MAIN_HAND).is(SpeedForceMod.TACHYON_DEVICE)
				|| p.getItemInHand(InteractionHand.OFF_HAND).is(SpeedForceMod.TACHYON_DEVICE)
				// v0.17.1: the Speed Force Tachyon Device is a Tachyon Device too
				|| (SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE != null && (p.getItemInHand(InteractionHand.MAIN_HAND).is(SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE)
						|| p.getItemInHand(InteractionHand.OFF_HAND).is(SpeedForceMod.SPEED_FORCE_TACHYON_DEVICE)));
	}

	/** What (if anything) still stands between {@code p} and the Speed Force. */
	public static Check check(ServerPlayer p) {
		if (SpeedForceSpeedster.hasPower(p)) {
			return Check.ALREADY;
		}
		if (!SuperSpeedHandlers.owns(p)) {
			return Check.NOT_SPEEDSTER;
		}
		if (!p.hasEffect(MobEffects.MOVEMENT_SPEED)) {
			return Check.NO_SPEED;
		}
		if (!holdsTachyon(p)) {
			return Check.NO_TACHYON;
		}
		if (!SuperSpeedHandlers.overdrive(p)) {
			return Check.NOT_OVERDRIVE;
		}
		return Check.READY;
	}

	/**
	 * A surge reached a Speedster (lightning or a plate run over): if everything is in place the Speed Force takes them,
	 * every time; otherwise they are told what is missing. True if they awakened.
	 */
	public static boolean trySurge(ServerPlayer p) {
		Check c = check(p);
		if (c == Check.READY) {
			p.removeEffect(MobEffects.MOVEMENT_SPEED); // the surge burns it off
			boolean ok = SpeedForceSpeedster.grant(p);
			if (ok) {
				LAST_POS.remove(p.getUUID());
			}
			return ok;
		}
		if (c != Check.ALREADY && c != Check.NOT_SPEEDSTER) {
			hint(p, c);
		}
		return false;
	}

	private static void hint(ServerPlayer p, Check c) {
		long now = p.level().getGameTime();
		Long last = LAST_HINT.get(p.getUUID());
		if (last != null && now - last < HINT_COOLDOWN && now >= last) {
			return;
		}
		LAST_HINT.put(p.getUUID(), now);
		MutableComponent line = Component.translatable("message.projecthero.speed_force_speedster.missing",
				Component.translatable("message.projecthero.speed_force_speedster.missing." + c.name().toLowerCase(java.util.Locale.ROOT)));
		p.displayClientMessage(line.withStyle(ChatFormatting.GOLD), true);
	}

	/**
	 * Every tick for a player without the Speed Force: a plain Speedster's path since last tick is swept for Charged Copper
	 * Plates underfoot (at Overdrive's 120 b/s a single plate is crossed in a fraction of a tick).
	 */
	static void tickCandidate(ServerPlayer p) {
		if (!SuperSpeedHandlers.owns(p) || !p.isAlive() || p.isSpectator()) {
			LAST_POS.remove(p.getUUID());
			return;
		}
		Vec3 now = p.position();
		Vec3 last = LAST_POS.put(p.getUUID(), now);
		if (last == null || last.distanceToSqr(now) > 60.0 * 60.0) {
			return;
		}
		if (crossesPlate(p, last, now)) {
			plateUnderfoot(p);
		}
	}

	/** Whether the path from {@code a} to {@code b} (the feet) runs over a block of Charged Copper Plates. */
	public static boolean crossesPlate(ServerPlayer p, Vec3 a, Vec3 b) {
		ServerLevel level = p.serverLevel();
		double len = a.distanceTo(b);
		int steps = Math.max(1, (int) Math.ceil(len / 0.4));
		BlockPos lastChecked = null;
		for (int i = 0; i <= steps; i++) {
			Vec3 at = a.lerp(b, i / (double) steps);
			BlockPos below = BlockPos.containing(at.x, at.y - 0.2, at.z);
			if (below.equals(lastChecked)) {
				continue;
			}
			lastChecked = below;
			if (level.getBlockState(below).is(ModDevices.CHARGED_COPPER_PLATES)) {
				return true;
			}
		}
		return false;
	}

	/** Running over the plates: the surge goes through the Speedster. */
	public static boolean plateUnderfoot(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.3, p.getZ(), 20, 0.4, 0.2, 0.4, 0.2);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 1.6f);
		return trySurge(p);
	}

	/** Struck by lightning (from {@link SpeedForceDamage}). */
	public static boolean onLightning(ServerPlayer p) {
		if (SpeedForceSpeedster.hasPower(p)) {
			SpeedForceSpeedster.addMeter(p, SpeedForceConfig.LIGHTNING_GAIN);
			return false;
		}
		return trySurge(p);
	}
}
