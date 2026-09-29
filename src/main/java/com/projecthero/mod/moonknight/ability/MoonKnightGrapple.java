package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * G -- the Grapple Kick, plus the Grappling Line it shares its rope with (Moon Knight Phase 4; keys moved in v0.13.21).
 * <ul>
 *   <li><b>G</b> ({@code kick}): fire the line into the targeted mob (up to {@link MoonKnightConfig#GRAPPLE_RANGE}),
 *       get pulled in feet-first ({@code FLAG_DIVING}, DIVE_KICK pose) and finish with a flying dive kick on arrival
 *       ({@link MoonKnightConfig#DIVE_KICK_DAMAGE} x power + knockback). Fires on the press (G has no hold move).
 *       Cooldown {@link MoonKnightConfig#DIVE_KICK_COOLDOWN}.</li>
 *   <li><b>SNEAK+G</b> ({@code kick_sneak}): Shadow Step ({@link MoonKnightCape#shadowStep}) -- it lost its old
 *       Sneak+X home when X became the Dash.</li>
 *   <li><b>SNEAK+X</b> ({@code dash_sneak}, from {@link MoonKnightDash}): the Grappling Line, {@link #fireLine} --
 *       60 blocks. At a block it pulls you there; at a mob it reels the mob in to you and stuns it (Slowness IV);
 *       a boss (or anything too heavy to move) pulls you to it instead.</li>
 * </ul>
 * The rope is drawn by every client from the synced {@code MoonKnightAction} line fields
 * ({@code MoonKnightLineRenderer}). The pulls are server velocity, sent every tick with a motion packet (the Iron Man
 * flight / {@code AbilityHelpers.launchSelf} pattern): a velocity, never a position correction, so they don't
 * rubber-band. Falls are harmless while being pulled and for 2 s after.
 */
public final class MoonKnightGrapple implements MoonKnightMove {
	public static final MoonKnightGrapple INSTANCE = new MoonKnightGrapple();

	private static final class Pull {
		final boolean toMob;
		final Vec3 anchor;
		final int targetId;
		final long start;
		final ResourceKey<Level> dimension;
		Vec3 lastPos;
		int stuck;

		Pull(boolean toMob, Vec3 anchor, int targetId, long start, ResourceKey<Level> dimension, Vec3 from) {
			this.toMob = toMob;
			this.anchor = anchor;
			this.targetId = targetId;
			this.start = start;
			this.dimension = dimension;
			this.lastPos = from;
		}
	}

	/** A mob being reeled in on the line (SNEAK+X at a mob). */
	private record Reel(int targetId, long start, ResourceKey<Level> dimension) {
	}

	private static final Map<UUID, Pull> PULLS = new ConcurrentHashMap<>();
	private static final Map<UUID, Reel> REELS = new ConcurrentHashMap<>();
	/** Game time until which falls are harmless after a pull ended. */
	private static final Map<UUID, Long> FALL_GRACE = new ConcurrentHashMap<>();

	private static final int FALL_GRACE_TICKS = 40;

	private MoonKnightGrapple() {
	}

	@Override
	public boolean firesOnPress() {
		return true;
	}

	// ---------------------------------------------------------------- G: the Grapple Kick

	@Override
	public void tap(ServerPlayer player) {
		grappleKick(player);
	}

	/** Grapple to the targeted mob and dive-kick it on arrival. True if the line went out. */
	public static boolean grappleKick(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "kick")) {
			return false;
		}
		LivingEntity target = target(player);
		if (target == null) {
			return false;
		}
		startPull(player, new Pull(true, null, target.getId(), player.level().getGameTime(), player.level().dimension(),
				player.position()));
		setLine(player, target.getId(), target.position());
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_DIVING, true);
		MoonKnightAnim.play(player, MoonKnightAnim.GRAPPLE_FIRE);
		MoonKnightAbilities.cooldown(player, "kick", MoonKnightConfig.DIVE_KICK_COOLDOWN);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_THROW, 0.9f, 0.5f);
		return true;
	}

	private static void diveKick(ServerPlayer player, LivingEntity target, Vec3 dir) {
		float power = MoonKnightAbilities.power(player);
		MoonKnightCombat.hit(player, target, MoonKnightConfig.DIVE_KICK_DAMAGE * power);
		MoonKnightCombat.knock(target, player.position(), MoonKnightConfig.DIVE_KICK_KNOCKBACK * power, 0.3);
		ServerLevel level = player.serverLevel();
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.08);
		level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 12, 0.35, 0.35, 0.35, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.8f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.8f, 1.2f);
		// spring back off the kick
		AbilityHelpers.launchSelf(player, dir.scale(-0.35).add(0.0, 0.45, 0.0));
	}

	// ---------------------------------------------------------------- SNEAK+G: Shadow Step

	@Override
	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "kick_sneak")) {
			return;
		}
		MoonKnightCape.shadowStep(player);
	}

	// ---------------------------------------------------------------- SNEAK+X: the Grappling Line

	/**
	 * Fire the line (up to {@link MoonKnightConfig#GRAPPLE_RANGE}): a mob under the crosshair is reeled in (a boss or
	 * anything too heavy pulls you to it instead); otherwise the looked-at block pulls you there. True if it fired.
	 */
	public static boolean fireLine(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "dash_sneak")) {
			return false;
		}
		double range = MoonKnightConfig.GRAPPLE_RANGE;
		ServerLevel level = player.serverLevel();
		long now = level.getGameTime();
		LivingEntity mob = AbilityHelpers.raycastEntity(player, range);
		if (mob != null && !MoonKnightCombat.friendly(player, mob)) {
			if (TitanCombat.isBoss(mob) || !AbilityHelpers.isValidGrabTarget(mob, player)) {
				// too heavy to move: the line hauls the Moon Knight to it instead
				Vec3 anchor = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
				startPull(player, new Pull(false, anchor, -1, now, level.dimension(), player.position()));
				setLine(player, mob.getId(), anchor);
				player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.yank_heavy")
						.withStyle(ChatFormatting.GRAY), true);
			} else {
				endPull(player);
				MoonKnightCape.stopGlide(player);
				REELS.put(player.getUUID(), new Reel(mob.getId(), now, level.dimension()));
				setLine(player, mob.getId(), mob.position());
				// it can't walk away while it is dragged in; the full stun lands when it arrives
				AbilityHelpers.applyControl(mob, MobEffects.MOVEMENT_SLOWDOWN, MoonKnightConfig.GRAPPLE_MAX_PULL_TICKS,
						MoonKnightConfig.YANK_SLOW_AMPLIFIER);
				MoonKnightAnim.play(player, MoonKnightAnim.YANK);
			}
			Vec3 at = mob.position().add(0, mob.getBbHeight() * 0.5, 0);
			AbilityHelpers.line(level, AbilityHelpers.handPosition(player), at, MoonKnightCombat.MOON, 2.0);
			level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.2);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8f, 1.1f);
			AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0f, 0.7f);
		} else {
			BlockHitResult hit = AbilityHelpers.raycastBlock(player, range);
			if (hit.getType() == HitResult.Type.MISS) {
				player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.grapple_nothing",
						Math.round(range)).withStyle(ChatFormatting.GRAY), true);
				return false;
			}
			Vec3 anchor = hit.getLocation();
			startPull(player, new Pull(false, anchor, -1, now, level.dimension(), player.position()));
			setLine(player, -1, anchor);
			MoonKnightAnim.play(player, MoonKnightAnim.GRAPPLE_FIRE);
			level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.LEASH_KNOT_PLACE, SoundSource.PLAYERS, 1.0f, 1.2f);
			level.sendParticles(ParticleTypes.CRIT, anchor.x, anchor.y, anchor.z, 6, 0.1, 0.1, 0.1, 0.15);
		}
		MoonKnightAbilities.cooldown(player, "dash_sneak", MoonKnightConfig.GRAPPLE_COOLDOWN);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_THROW, 0.9f, 0.6f);
		return true;
	}

	/** True while this player is reeling a mob in. */
	public static boolean isReeling(ServerPlayer player) {
		return REELS.containsKey(player.getUUID());
	}

	private static void endReel(ServerPlayer player, boolean arrived) {
		Reel r = REELS.remove(player.getUUID());
		if (r == null) {
			return;
		}
		if (!isPulling(player)) {
			clearLine(player);
		}
		Entity e = player.level().getEntity(r.targetId());
		if (e instanceof LivingEntity mob && mob.isAlive()) {
			mob.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			if (arrived) {
				Vec3 v = mob.getDeltaMovement();
				mob.setDeltaMovement(v.x * 0.2, Math.min(v.y, 0.1), v.z * 0.2);
				mob.hurtMarked = true;
				AbilityHelpers.applyControl(mob, MobEffects.MOVEMENT_SLOWDOWN,
						Math.round(MoonKnightConfig.YANK_STUN_TICKS * MoonKnightAbilities.power(player)),
						MoonKnightConfig.YANK_SLOW_AMPLIFIER);
				player.serverLevel().sendParticles(ParticleTypes.CRIT, mob.getX(), mob.getY() + mob.getBbHeight() * 0.5,
						mob.getZ(), 8, 0.25, 0.25, 0.25, 0.2);
				AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.8f, 1.1f);
			}
		}
	}

	/** Drag the reeled mob toward the player, every tick, until it is about two blocks in front of him. */
	private static void tickReel(ServerPlayer player) {
		Reel r = REELS.get(player.getUUID());
		if (r == null) {
			return;
		}
		long age = player.level().getGameTime() - r.start();
		Entity e = player.level().getEntity(r.targetId());
		if (player.level().dimension() != r.dimension() || !(e instanceof LivingEntity mob) || !mob.isAlive()
				|| age > MoonKnightConfig.GRAPPLE_MAX_PULL_TICKS) {
			endReel(player, false);
			return;
		}
		if (age < MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS) {
			return; // the line is still flying out
		}
		Vec3 to = player.position().subtract(mob.position());
		double dist = to.length();
		double stop = MoonKnightConfig.YANK_STOP_DISTANCE + mob.getBbWidth() * 0.5;
		if (dist <= stop) {
			endReel(player, true);
			return;
		}
		Vec3 dir = to.scale(1.0 / dist);
		double speed = Math.min(MoonKnightConfig.GRAPPLE_REEL_SPEED, Math.max(0.3, (dist - stop) * 0.5));
		// a little lift so it drags over the ground instead of into it
		double lift = mob.onGround() ? 0.25 : Math.max(-0.2, dir.y * speed);
		mob.setDeltaMovement(dir.x * speed, Math.max(lift, dir.y * speed), dir.z * speed);
		mob.hurtMarked = true;
		mob.hasImpulse = true;
		mob.resetFallDistance();
	}

	// ---------------------------------------------------------------- the pull

	private static LivingEntity target(ServerPlayer player) {
		LivingEntity target = AbilityHelpers.raycastEntity(player, MoonKnightConfig.GRAPPLE_RANGE);
		if (target == null || MoonKnightCombat.friendly(player, target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.grapple_no_target")
					.withStyle(ChatFormatting.GRAY), true);
			return null;
		}
		return target;
	}

	private static void startPull(ServerPlayer player, Pull pull) {
		endPull(player);
		endReel(player, false);
		MoonKnightCape.stopGlide(player); // the line takes over from the cape
		PULLS.put(player.getUUID(), pull);
	}

	/** True while this player is being pulled by the line. */
	public static boolean isPulling(ServerPlayer player) {
		return PULLS.containsKey(player.getUUID());
	}

	private static void endPull(ServerPlayer player) {
		Pull p = PULLS.remove(player.getUUID());
		if (p == null) {
			return;
		}
		FALL_GRACE.put(player.getUUID(), player.level().getGameTime() + FALL_GRACE_TICKS);
		clearLine(player);
		if (p.toMob) {
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_DIVING, false);
			MoonKnightAnim.stop(player, MoonKnightAnim.DIVE_KICK);
		}
	}

	private static void tickPull(ServerPlayer player) {
		Pull p = PULLS.get(player.getUUID());
		if (p == null) {
			return;
		}
		if (player.level().dimension() != p.dimension) {
			endPull(player);
			return;
		}
		long age = player.level().getGameTime() - p.start;
		if (age < MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS) {
			return; // the line is still flying out
		}
		if (age > MoonKnightConfig.GRAPPLE_MAX_PULL_TICKS) {
			endPull(player);
			return;
		}
		Vec3 anchor;
		LivingEntity target = null;
		if (p.toMob) {
			Entity e = player.level().getEntity(p.targetId);
			if (!(e instanceof LivingEntity le) || !le.isAlive()) {
				endPull(player);
				return;
			}
			target = le;
			anchor = le.position().add(0, le.getBbHeight() * 0.5, 0);
			if (age == MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS) {
				MoonKnightAnim.play(player, MoonKnightAnim.DIVE_KICK);
				AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.7f, 1.4f);
			}
		} else {
			anchor = p.anchor;
		}
		Vec3 center = player.position().add(0, player.getBbHeight() * 0.5, 0);
		Vec3 to = anchor.subtract(center);
		double dist = to.length();
		Vec3 dir = dist < 1.0e-4 ? Vec3.ZERO : to.scale(1.0 / dist);
		if (p.toMob) {
			if (dist <= MoonKnightConfig.DIVE_KICK_REACH + target.getBbWidth() * 0.5) {
				endPull(player);
				diveKick(player, target, dir);
				return;
			}
		} else if (dist <= MoonKnightConfig.GRAPPLE_ARRIVE_DISTANCE) {
			endPull(player);
			// pop up and over the lip of whatever we grappled to
			AbilityHelpers.launchSelf(player, dir.scale(0.3).add(0.0, 0.45, 0.0));
			AbilityHelpers.sound(player, SoundEvents.LEASH_KNOT_BREAK, 0.6f, 1.3f);
			return;
		}
		// hung up on a ledge / wall: give up rather than grind against it
		if (age > MoonKnightConfig.GRAPPLE_LINE_TRAVEL_TICKS + 6 && player.position().distanceTo(p.lastPos) < 0.05) {
			if (++p.stuck >= 6) {
				endPull(player);
				return;
			}
		} else {
			p.stuck = 0;
		}
		p.lastPos = player.position();
		double speed = p.toMob ? MoonKnightConfig.DIVE_KICK_PULL_SPEED : MoonKnightConfig.GRAPPLE_PULL_SPEED;
		AbilityHelpers.launchSelf(player, dir.scale(Math.min(speed, Math.max(0.4, dist * 0.6))));
		if (age % 2 == 0) {
			player.serverLevel().sendParticles(MoonKnightCombat.MOON, center.x, center.y, center.z, 2, 0.2, 0.3, 0.2, 0.0);
		}
	}

	// ---------------------------------------------------------------- the rope (synced to every client)

	private static void setLine(ServerPlayer player, int targetId, Vec3 anchor) {
		MoonKnightAction c = MoonKnightAnim.action(player).copy();
		c.lineTargetId = targetId;
		c.lineX = anchor.x;
		c.lineY = anchor.y;
		c.lineZ = anchor.z;
		c.lineStart = player.level().getGameTime();
		MoonKnightAnim.save(player, c);
	}

	private static void clearLine(ServerPlayer player) {
		MoonKnightAction a = MoonKnightAnim.action(player);
		if (a.lineStart < 0 && a.lineTargetId < 0) {
			return;
		}
		MoonKnightAction c = a.copy();
		c.lineStart = -1L;
		c.lineTargetId = -1;
		MoonKnightAnim.save(player, c);
	}

	// ---------------------------------------------------------------- upkeep

	@Override
	public void tick(ServerPlayer player) {
		tickReel(player);
		tickPull(player);
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		endPull(player);
		endReel(player, false);
		clearLine(player);
	}

	/** Falls are harmless mid-pull and for 2 s after one ends. */
	public static boolean protectsFromFall(ServerPlayer player) {
		if (isPulling(player)) {
			return true;
		}
		Long until = FALL_GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	public static void clearSessionState() {
		PULLS.clear();
		REELS.clear();
		FALL_GRACE.clear();
	}
}
