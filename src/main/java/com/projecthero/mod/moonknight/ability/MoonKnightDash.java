package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * X -- the Dash (v0.13.21), and SNEAK+X the Grappling Line.
 * <ul>
 *   <li><b>X</b> ({@code dash}): a burst of speed exactly where the crosshair points -- up, down or level (v0.14.3; it
 *       used to be flattened to the horizontal)
 *       ({@link MoonKnightConfig#DASH_SPEED} blocks/tick for {@link MoonKnightConfig#DASH_TICKS} ticks -- about 7
 *       blocks), trailing moonlight; no fall damage for a moment after. Fires on the press. Cooldown
 *       {@link MoonKnightConfig#DASH_COOLDOWN}.</li>
 *   <li><b>SNEAK+X</b> ({@code dash_sneak}): {@link MoonKnightGrapple#fireLine} -- 60 blocks; a block pulls you
 *       there, a mob is reeled in to you.</li>
 * </ul>
 * Like the grapple the burst is server velocity re-sent every tick ({@code AbilityHelpers.launchSelf}), so the owning
 * client never fights it.
 */
public final class MoonKnightDash implements MoonKnightMove {
	public static final MoonKnightDash INSTANCE = new MoonKnightDash();

	private record Dash(Vec3 dir, long start) {
	}

	private static final Map<UUID, Dash> DASHES = new ConcurrentHashMap<>();
	/** Game time until which a finished dash keeps falls harmless. */
	private static final Map<UUID, Long> FALL_GRACE = new ConcurrentHashMap<>();

	private MoonKnightDash() {
	}

	@Override
	public boolean firesOnPress() {
		return true;
	}

	@Override
	public void tap(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "dash")) {
			return;
		}
		dash(player);
	}

	@Override
	public void sneak(ServerPlayer player) {
		MoonKnightGrapple.fireLine(player);
	}

	/** Start a dash along the look, in 3D (v0.14.3: aim up to dash up, down to dive). Public for the gametests. */
	public static void dash(ServerPlayer player) {
		Vec3 dir = player.getLookAngle();
		if (dir.lengthSqr() < 1.0e-4) {
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			dir = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
		}
		dir = dir.normalize();
		MoonKnightCape.stopGlide(player);
		DASHES.put(player.getUUID(), new Dash(dir, player.level().getGameTime()));
		push(player, dir);
		MoonKnightAnim.play(player, MoonKnightAnim.DASH);
		MoonKnightAbilities.cooldown(player, "dash", MoonKnightConfig.DASH_COOLDOWN);
		ServerLevel level = player.serverLevel();
		level.sendParticles(MoonKnightCombat.MOON, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 6, 0.2, 0.05, 0.2, 0.02);
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.8f, 1.5f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.8f);
	}

	public static boolean isDashing(ServerPlayer player) {
		return DASHES.containsKey(player.getUUID());
	}

	private static void push(ServerPlayer player, Vec3 dir) {
		// v0.14.3: the whole burst follows the aim, vertical included
		AbilityHelpers.launchSelf(player, dir.scale(MoonKnightConfig.DASH_SPEED));
	}

	private static void end(ServerPlayer player) {
		if (DASHES.remove(player.getUUID()) == null) {
			return;
		}
		Vec3 v = player.getDeltaMovement();
		// bleed the burst off so he doesn't skate on for another ten blocks
		AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.3, Math.min(v.y, 0.0), v.z * 0.3));
		FALL_GRACE.put(player.getUUID(), player.level().getGameTime() + 20L);
		player.resetFallDistance();
	}

	@Override
	public void tick(ServerPlayer player) {
		Dash d = DASHES.get(player.getUUID());
		if (d == null) {
			return;
		}
		long age = player.level().getGameTime() - d.start();
		if (age >= MoonKnightConfig.DASH_TICKS || player.isInWater() || player.horizontalCollision && age > 0) {
			end(player);
			return;
		}
		player.resetFallDistance();
		if (age > 0) {
			push(player, d.dir());
		}
		player.serverLevel().sendParticles(MoonKnightCombat.MOON, player.getX(), player.getY() + 1.0, player.getZ(), 3,
				0.25, 0.4, 0.25, 0.0);
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		DASHES.remove(player.getUUID());
	}

	/** A dash in progress (or just finished) never takes fall damage. */
	public static boolean protectsFromFall(ServerPlayer player) {
		if (isDashing(player)) {
			return true;
		}
		Long until = FALL_GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	public static void clearSessionState() {
		DASHES.clear();
		FALL_GRACE.clear();
	}
}
