package com.projecthero.mod.saiyan;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.saiyan.data.SaiyanState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3: the Saiyan's movement. <b>X</b> is the Ki Dash: a burst along the look, faster in the stronger forms
 * ({@link SaiyanForm#speedMult()}), 6 Ki. <b>Double-tap jump</b> in the air is flight, built like the Kryptonian's: vanilla
 * {@code mayfly}/{@code flying} so the server never kicks a hovering player, the movement itself the client's shared
 * directional flight (W along the look, Sprint faster, a dead hover with no input). It costs 0.25 Ki a second and ends at
 * 0 Ki, on landing, or on another double-tap.
 */
public final class SaiyanFlight {
	public static final String DASH = "ki_dash";
	public static final String FLIGHT = "flight";

	private static final Map<UUID, Long> STARTED = new ConcurrentHashMap<>();
	private static final Map<UUID, Vec3> LAST_POS = new ConcurrentHashMap<>();
	/** No fall damage until this game time (after a dash). */
	private static final Map<UUID, Long> SAFE_UNTIL = new ConcurrentHashMap<>();

	private SaiyanFlight() {
	}

	static void clearSessionState() {
		STARTED.clear();
		LAST_POS.clear();
		SAFE_UNTIL.clear();
	}

	static void forget(UUID id) {
		STARTED.remove(id);
		LAST_POS.remove(id);
		SAFE_UNTIL.remove(id);
	}

	static boolean fallSafe(ServerPlayer player) {
		Long until = SAFE_UNTIL.get(player.getUUID());
		return isFlying(player) || (until != null && player.level().getGameTime() <= until);
	}

	static boolean isFlying(ServerPlayer player) {
		return Saiyan.isFlying(player);
	}

	// ---------------------------------------------------------------- X: the Ki Dash

	public static boolean dash(ServerPlayer player) {
		if (!SaiyanAbilityManager.canAct(player)) {
			return false;
		}
		int cd = Saiyan.cooldownRemaining(player, DASH);
		if (cd > 0) {
			return false;
		}
		if (!Saiyan.spendKi(player, SaiyanConfig.DASH_COST)) {
			return false;
		}
		SaiyanForm f = Saiyan.form(player);
		Saiyan.startCooldown(player, DASH, SaiyanConfig.DASH_COOLDOWN);
		Vec3 look = player.getLookAngle();
		double speed = SaiyanConfig.DASH_SPEED * f.speedMult();
		Vec3 v = new Vec3(look.x * speed, Math.max(-0.4, look.y * speed * 0.6) + (player.onGround() ? 0.2 : 0.0), look.z * speed);
		AbilityHelpers.launchSelf(player, v);
		player.resetFallDistance();
		SAFE_UNTIL.put(player.getUUID(), player.level().getGameTime() + SaiyanConfig.DASH_FALL_GRACE);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 0.9, 0);
		level.sendParticles(Saiyan.dust(f.auraColor, 1.5f), c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y - 0.6, c.z, 6, 0.3, 0.1, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.KI_DASH, SoundSource.PLAYERS, 0.7f, 1.0f);
		return true;
	}

	// ---------------------------------------------------------------- double-tap jump: flight

	public static void toggle(ServerPlayer player) {
		if (isFlying(player)) {
			stop(player);
			Saiyan.startCooldown(player, FLIGHT, SaiyanConfig.FLIGHT_TOGGLE_COOLDOWN);
			return;
		}
		if (!SaiyanAbilityManager.canAct(player) || player.isPassenger() || player.isFallFlying()) {
			return;
		}
		if (Saiyan.cooldownRemaining(player, FLIGHT) > 0) {
			return;
		}
		if (Saiyan.ki(player) <= 0f && !player.getAbilities().instabuild) {
			Saiyan.say(player, "message.projecthero.saiyan.no_ki", ChatFormatting.GRAY);
			return;
		}
		start(player);
		Saiyan.startCooldown(player, FLIGHT, SaiyanConfig.FLIGHT_TOGGLE_COOLDOWN);
	}

	static void start(ServerPlayer player) {
		SaiyanState n = Saiyan.state(player).copy();
		n.flying = true;
		Saiyan.save(player, n);
		STARTED.put(player.getUUID(), player.level().getGameTime());
		grantAbilities(player);
		player.resetFallDistance();
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position();
		level.sendParticles(Saiyan.dust(Saiyan.form(player).auraColor, 1.6f), c.x, c.y + 0.5, c.z, 14, 0.4, 0.3, 0.4, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 10, 0.4, 0.1, 0.4, 0.05);
		level.playSound(null, c.x, c.y, c.z, SaiyanSounds.KI_DASH, SoundSource.PLAYERS, 0.45f, 0.8f); // the take-off: a softer, lower dash
	}

	private static void grantAbilities(ServerPlayer player) {
		if (!player.getAbilities().instabuild && !player.isSpectator()) {
			player.getAbilities().mayfly = true;
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
		}
	}

	/** Ends the flight if it is running. */
	public static void stop(ServerPlayer player) {
		SaiyanState s = player.getAttachedOrElse(ModAttachments.SAIYAN_STATE, null);
		boolean was = s != null && s.flying;
		if (was) {
			SaiyanState n = s.copy();
			n.flying = false;
			Saiyan.save(player, n);
		}
		STARTED.remove(player.getUUID());
		if (was && !player.getAbilities().instabuild && !player.isSpectator() && !anyOtherFlightWants(player)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	/** Per tick for every Saiyan. */
	static void tick(ServerPlayer player) {
		Vec3 pos = player.position();
		Vec3 last = LAST_POS.put(player.getUUID(), pos);
		Long safe = SAFE_UNTIL.get(player.getUUID());
		if (safe != null && player.level().getGameTime() > safe) {
			SAFE_UNTIL.remove(player.getUUID());
		}
		if (!isFlying(player)) {
			return;
		}
		if (player.isSpectator() || player.isPassenger() || player.isFallFlying() || !player.isAlive()) {
			stop(player);
			return;
		}
		long now = player.level().getGameTime();
		Long start = STARTED.computeIfAbsent(player.getUUID(), k -> now);
		if (com.projecthero.mod.flight.FlightLanding.landed(player, start)) {
			stop(player);
			return;
		}
		if (!player.getAbilities().flying || !player.getAbilities().mayfly) {
			if (now - start >= SaiyanConfig.FLIGHT_LIFTOFF_GRACE && !player.getAbilities().flying && player.getAbilities().mayfly) {
				stop(player); // the client dropped vanilla flight on its own (landed / double-tapped): follow it
				// the same double-tap's toggle request may land after this: the cooldown keeps it from taking off again
				Saiyan.startCooldown(player, FLIGHT, SaiyanConfig.FLIGHT_TOGGLE_COOLDOWN);
				return;
			}
			grantAbilities(player);
		}
		player.resetFallDistance();
		double speed = last == null ? 0.0 : pos.distanceTo(last);
		if (speed > 20.0) {
			speed = 0.0;
		}
		if (speed > 0.6 && player.tickCount % 2 == 0) {
			ServerLevel level = (ServerLevel) player.level();
			Vec3 travel = pos.subtract(last);
			Vec3 dir = travel.lengthSqr() > 1.0e-6 ? travel.normalize() : player.getLookAngle();
			Vec3 tail = pos.add(0, player.getBbHeight() * 0.5, 0).subtract(dir.scale(1.0));
			level.sendParticles(Saiyan.dust(Saiyan.form(player).auraColor, 1.2f), tail.x, tail.y, tail.z, 2, 0.15, 0.15, 0.15, 0.0);
		}
	}

	private static boolean anyOtherFlightWants(ServerPlayer player) {
		return player.getAttachedOrElse(ModAttachments.FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)
				|| player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false);
	}
}
