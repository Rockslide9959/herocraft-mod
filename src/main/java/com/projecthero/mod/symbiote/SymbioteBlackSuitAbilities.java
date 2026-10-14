package com.projecthero.mod.symbiote;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.spider.SpiderMan;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Black Suit Spider-Man's own kit. v0.16.2: with the suit on, six of Spider-Man's eighteen moves become symbiote
 * moves (dispatched from {@code SpiderMoves} while {@link SymbioteHostType#SPIDER_MAN} is active):
 *
 * <ul>
 *   <li><b>X tap</b> (Web Strike) -- Tendril Lash: an 8-block whip, 7 damage and a short pull in.</li>
 *   <li><b>Shift + X</b> (Uppercut) -- Spike Burst: spikes erupt from the whole body, 8 damage to everything within 3.</li>
 *   <li><b>Shift + Z, held</b> (Grab &amp; Hurl) -- Consume: seize a target within 6 blocks and squeeze it for 5 damage
 *       a second for up to 2 seconds, healing for 30% of what it deals.</li>
 *   <li><b>Shift + V</b> (Web Pin) -- Ink Shroud: a black cloud that blinds and slows enemies within 5 blocks.</li>
 *   <li><b>Hold V</b> (Web Blossom) -- Let It Take Over, at a full Hunger meter ({@link SymbioteHunger}).</li>
 *   <li><b>Shift + C</b> (Web Wall) -- Symbiote Slam: a dive for a 10-damage shockwave (6 from the ground).</li>
 * </ul>
 * Every hit they land feeds the Hunger meter like any other hit does ({@link SymbioteHunger}).
 */
public final class SymbioteBlackSuitAbilities {
	/** Keys into {@code SpiderManState.abilityReadyAt} -- synced, so the Spider HUD shows their cooldowns. */
	public static final String TENDRIL_LASH = "symbiote_tendril_lash";
	public static final String SPIKE_BURST = "symbiote_spike_burst";
	public static final String CONSUME = "symbiote_consume";
	public static final String INK_SHROUD = "symbiote_ink_shroud";
	public static final String TAKE_OVER = "symbiote_take_over";
	public static final String SLAM = "symbiote_slam_enhanced";

	public static final int CD_TENDRIL_LASH = 30;  // 1.5s
	public static final int CD_SPIKE_BURST = 120;  // 6s
	public static final int CD_CONSUME = 280;      // 14s after the squeeze ends
	public static final int CD_INK_SHROUD = 240;   // 12s
	public static final int CD_SLAM = 280;         // 14s

	/** {@code SpiderMoves} slot index -> {tap, shift, hold} replacement id (null = Spider-Man's own move stays). */
	private static final String[][] SWAPS = {
			{null, null, null},
			{null, null, null},
			{TENDRIL_LASH, SPIKE_BURST, null},
			{null, CONSUME, null},
			{null, INK_SHROUD, TAKE_OVER},
			{null, SLAM, null},
	};

	private static final double LASH_RANGE = 8.0;
	private static final float LASH_DAMAGE = 7.0f;
	private static final double SPIKE_RADIUS = 3.0;
	private static final float SPIKE_DAMAGE = 8.0f;
	private static final double CONSUME_RANGE = 6.0;
	private static final int CONSUME_MAX_TICKS = 40;
	private static final int CONSUME_INTERVAL = 10;
	private static final float CONSUME_PER_INTERVAL = 2.5f; // 5 a second
	private static final float CONSUME_HEAL_FRACTION = 0.3f;
	private static final double SHROUD_RADIUS = 5.0;
	private static final int SHROUD_TICKS = 80;
	private static final float SLAM_DAMAGE = 10.0f;
	private static final float SLAM_GROUND_DAMAGE = 6.0f;
	private static final double SLAM_RADIUS = 4.0;
	private static final double SLAM_DIVE_SPEED = 2.4;
	private static final int SLAM_LAND_DEADLINE_TICKS = 40;

	/** Active Consume per player: {targetEntityId, startTick, lastDamageTick}. Transient. */
	private static final Map<Integer, long[]> CONSUME_ACTIVE = new ConcurrentHashMap<>();
	/** Players in a Slam dive, waiting to land: value = the fallback impact tick. */
	private static final Map<Integer, Long> SLAM_PENDING = new ConcurrentHashMap<>();

	private SymbioteBlackSuitAbilities() {
	}

	public static boolean blackSuit(net.minecraft.world.entity.player.Player player) {
		return SymbioteHunger.blackSuit(player);
	}

	/** The id shown / fired on slot {@code i} for kind 0 = tap, 1 = Shift, 2 = hold, or null to keep Spider-Man's. */
	public static String swapFor(net.minecraft.world.entity.player.Player player, int i, int kind) {
		if (i < 0 || i >= SWAPS.length || !blackSuit(player)) {
			return null;
		}
		return SWAPS[i][kind];
	}

	/** Full cooldown of one of these moves (the HUD sizes its bars with it); 0 if {@code id} is not one. */
	public static int cooldownOf(String id) {
		return switch (id) {
			case TENDRIL_LASH -> CD_TENDRIL_LASH;
			case SPIKE_BURST -> CD_SPIKE_BURST;
			case CONSUME -> CD_CONSUME;
			case INK_SHROUD -> CD_INK_SHROUD;
			case SLAM -> CD_SLAM;
			default -> 0;
		};
	}

	// ---------------- Tendril Lash (X) ----------------

	public static void tendrilLash(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, TENDRIL_LASH)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, LASH_RANGE);
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
		SpiderMan.triggerCooldown(player, TENDRIL_LASH, CD_TENDRIL_LASH);
		ServerLevel level = AbilityHelpers.level(player);
		if (target == null || com.projecthero.mod.squad.Squads.areAllies(player, target)) {
			Vec3 miss = player.getEyePosition().add(player.getLookAngle().scale(LASH_RANGE));
			com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity.fromHand(player, true, miss, null, 8, 2, 0.1f);
			SymbioteSounds.organic(player, 0.6f, 0.8f);
			return;
		}
		Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
		com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity.fromHand(player, true, hit, target, 10, 3, 0.12f);
		if (AbilityHelpers.hurtLands(player, target, LASH_DAMAGE)) {
			Vec3 pull = player.position().subtract(target.position());
			if (pull.lengthSqr() > 4.0) {
				target.push(pull.normalize().x * 0.55, 0.2, pull.normalize().z * 0.55);
				target.hurtMarked = true;
			}
			AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 10, 0.3);
		}
		SymbioteSounds.organic(player, 0.8f, 0.6f);
	}

	// ---------------- Spike Burst (Shift + X) ----------------

	public static void spikeBurst(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, SPIKE_BURST)) {
			return;
		}
		SpiderMan.triggerCooldown(player, SPIKE_BURST, CD_SPIKE_BURST);
		SymbioteAnim.play(player, SymbioteAnim.SPIKES_FLEX);
		Vec3 c = player.position().add(0, 1, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, c, SPIKE_RADIUS)) {
			if (com.projecthero.mod.squad.Squads.areAllies(player, e)) {
				continue;
			}
			if (AbilityHelpers.hurtLands(player, e, SPIKE_DAMAGE)) {
				AbilityHelpers.knockbackFrom(e, player.position(), 0.7);
			}
		}
		ServerLevel level = AbilityHelpers.level(player);
		AbilityHelpers.burst(level, c, ParticleTypes.SQUID_INK, 10, 0.7);
		AbilityHelpers.sound(player, SoundEvents.TRIDENT_HIT, 1.0f, 0.6f);
		SymbioteSounds.organic(player, 1.0f, 0.5f);
	}

	// ---------------- Consume (Shift + Z, held) ----------------

	public static void beginConsume(ServerPlayer player) {
		if (CONSUME_ACTIVE.containsKey(player.getId()) || !SpiderMan.abilityReady(player, CONSUME)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, CONSUME_RANGE);
		if (target == null || !target.isAlive() || com.projecthero.mod.squad.Squads.areAllies(player, target)) {
			return;
		}
		long now = player.level().getGameTime();
		CONSUME_ACTIVE.put(player.getId(), new long[] { target.getId(), now, now });
		SymbioteAnim.play(player, SymbioteAnim.TENDRIL_STRIKE);
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.consume_seized"), true);
	}

	/** Key release -- let go. Safe to call unconditionally. */
	public static void releaseConsume(ServerPlayer player) {
		if (CONSUME_ACTIVE.remove(player.getId()) != null) {
			SpiderMan.triggerCooldown(player, CONSUME, CD_CONSUME);
		}
	}

	// ---------------- Ink Shroud (Shift + V) ----------------

	public static void inkShroud(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, INK_SHROUD)) {
			return;
		}
		SpiderMan.triggerCooldown(player, INK_SHROUD, CD_INK_SHROUD);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 c = player.position().add(0, 1, 0);
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, c, SHROUD_RADIUS)) {
			if (com.projecthero.mod.squad.Squads.areAllies(player, e)) {
				continue;
			}
			AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, SHROUD_TICKS, 0);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, SHROUD_TICKS, 1);
			if (e instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() == player) {
				mob.setTarget(null); // it loses you in the dark
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 140, SHROUD_RADIUS * 0.5, 0.9, SHROUD_RADIUS * 0.5, 0.02);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 40, SHROUD_RADIUS * 0.45, 0.7, SHROUD_RADIUS * 0.45, 0.01);
		AbilityHelpers.sound(player, SoundEvents.SQUID_SQUIRT, 1.2f, 0.6f);
		SymbioteSounds.organic(player, 0.8f, 0.5f);
	}

	// ---------------- Let It Take Over (hold V) ----------------

	public static void takeOver(ServerPlayer player) {
		SymbioteHunger.beginTakeover(player);
	}

	// ---------------- Symbiote Slam (Shift + C) ----------------

	/** Returns false (so the key falls through to Web Wall) only while on cooldown. */
	public static boolean slam(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, SLAM)) {
			return false;
		}
		SpiderMan.triggerCooldown(player, SLAM, CD_SLAM);
		ServerLevel level = AbilityHelpers.level(player);
		if (player.onGround()) {
			slamImpact(player, SLAM_GROUND_DAMAGE);
			return true;
		}
		Vec3 v = player.getDeltaMovement();
		AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.3, -SLAM_DIVE_SPEED, v.z * 0.3));
		SLAM_PENDING.put(player.getId(), player.level().getGameTime() + SLAM_LAND_DEADLINE_TICKS);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 24, 0.5);
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		return true;
	}

	private static void slamImpact(ServerPlayer player, float damage) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, SLAM_RADIUS)) {
			if (!com.projecthero.mod.squad.Squads.areAllies(player, target) && AbilityHelpers.hurtLands(player, target, damage)) {
				AbilityHelpers.knockbackFrom(target, center, 1.1);
			}
		}
		AbilityHelpers.burst(level, center, ParticleTypes.SQUID_INK, 44, 0.85);
		AbilityHelpers.burst(level, center, ParticleTypes.LARGE_SMOKE, 16, 0.7);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.5f);
		player.resetFallDistance();
	}

	// ---------------- upkeep ----------------

	/** Called every tick a Spider-Man is online (self-gates on the black suit being on). */
	public static void serverTick(ServerPlayer player) {
		SymbioteHunger.tick(player);
		long now = player.level().getGameTime();

		Long slamDeadline = SLAM_PENDING.get(player.getId());
		if (slamDeadline != null) {
			player.resetFallDistance();
			if (!blackSuit(player)) {
				SLAM_PENDING.remove(player.getId());
			} else if (player.onGround() || now >= slamDeadline) {
				SLAM_PENDING.remove(player.getId());
				slamImpact(player, SLAM_DAMAGE);
			}
		}

		long[] c = CONSUME_ACTIVE.get(player.getId());
		if (c == null) {
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity((int) c[0]);
		if (!blackSuit(player) || !(e instanceof LivingEntity target) || !target.isAlive()
				|| player.distanceToSqr(target) > CONSUME_RANGE * CONSUME_RANGE * 1.6
				|| now - c[1] >= CONSUME_MAX_TICKS) {
			releaseConsume(player);
			return;
		}
		target.setDeltaMovement(target.getDeltaMovement().scale(0.15));
		target.hurtMarked = true;
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
		Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
		double rx = target.getBbWidth() * 0.5 + 0.15;
		level.sendParticles(ParticleTypes.SQUID_INK, mid.x, mid.y, mid.z, 8, rx, target.getBbHeight() * 0.5, rx, 0.01);
		if ((now - c[1]) % 10 == 0) {
			com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity.fromHand(player, true, mid, target, 10, 3, 0.12f);
		}
		if (now - c[2] >= CONSUME_INTERVAL) {
			c[2] = now;
			float before = target.getHealth();
			AbilityHelpers.hurt(player, target, CONSUME_PER_INTERVAL);
			float dealt = Math.max(0.0f, before - target.getHealth());
			if (dealt > 0.0f) {
				player.heal(dealt * CONSUME_HEAL_FRACTION);
			}
			SymbioteSounds.organic(player, 0.6f, 0.3f);
		}
	}

	public static void clearSessionState() {
		CONSUME_ACTIVE.clear();
		SLAM_PENDING.clear();
		SymbioteHunger.clearSessionState();
	}

	/** Death / relog / dimension change / power loss: drop this player's transient Black Suit state. */
	public static void clearFor(ServerPlayer player) {
		CONSUME_ACTIVE.remove(player.getId());
		SLAM_PENDING.remove(player.getId());
		SymbioteHunger.clearFor(player);
	}
}
