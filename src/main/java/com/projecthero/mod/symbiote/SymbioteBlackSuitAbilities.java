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
 * Black Suit Spider-Man's extra Symbiote-flavoured abilities. Per the spec's explicit "use
 * contextual abilities... rather than requiring an unreasonable number of new keybinds" instruction,
 * these do NOT get new keys -- they are sneak-modified variants of Spider-Man's existing slots,
 * dispatched from {@code SpiderManAbilityManager.handle} only while
 * {@link SymbioteHostType#SPIDER_MAN} is active:
 *
 * <ul>
 *   <li><b>Sneak + X (Web Yank's key)</b> -- Symbiote Tendril Strike: the same short-range tendril
 *       lash the Normal host's Tendril Strike is ({@link SymbioteAbilityManager}), a straight melee
 *       hit for real damage and a knockback -- <em>not</em> a pull.</li>
 *   <li><b>Sneak + Z (Web Shot's key), held</b> -- Symbiote Crush: lock onto whatever you are looking
 *       at within 10 blocks and hold the key to crush it in the symbiote for 8 damage a second, up to
 *       5 seconds, covering it in black symbiote particles the whole time.</li>
 *   <li><b>Sneak + C (the wall-crawl toggle's key)</b> -- Symbiote Slam: airborne, it drives you
 *       straight down into the ground for a 15-damage black-symbiote shockwave on impact; on the
 *       ground it is just an ordinary slam right where you stand.</li>
 * </ul>
 *
 * <p>Symbiote Recovery (slightly faster regen than a Normal host, still nowhere near overpowered) is
 * a passive with no key at all -- see {@code SpiderPassives#tick}.
 */
public final class SymbioteBlackSuitAbilities {
	/** Keys into {@code SpiderManState.abilityReadyAt} -- synced, so {@code SymbioteHud} can show these
	 *  cooldowns client-side exactly like every other Spider-Man ability, instead of the
	 *  server-only-and-therefore-invisible-to-the-HUD map this used before. */
	public static final String TENDRIL_STRIKE = "symbiote_tendril_strike";
	public static final String CRUSH = "symbiote_crush";
	public static final String SLAM_ENHANCED = "symbiote_slam_enhanced";

	private static final int CD_TENDRIL_STRIKE = 80;  // 4s
	private static final int CD_CRUSH = 200;          // 10s after the crush ends
	private static final int CD_SLAM_ENHANCED = 200;  // 10s

	// ---- Symbiote Tendril Strike ----
	private static final double TENDRIL_STRIKE_RANGE = 8.0;

	// ---- Symbiote Crush (Sneak + Z, held) ----
	private static final double CRUSH_RANGE = 10.0;
	private static final int CRUSH_MAX_TICKS = 100;          // 5s hold cap
	private static final int CRUSH_DAMAGE_INTERVAL = 10;     // dealt in chunks so vanilla i-frames don't eat it
	private static final float CRUSH_DAMAGE_PER_INTERVAL = 4.0f; // 4 / 0.5s == 8 damage per second

	// ---- Symbiote Slam (Sneak + C) ----
	private static final float SLAM_DAMAGE = 15.0f;
	private static final double SLAM_RADIUS = 5.0;
	private static final double SLAM_DIVE_SPEED = 2.4;
	private static final int SLAM_LAND_DEADLINE_TICKS = 40;  // fire the impact anyway if still falling after 2s

	/** Active Crush per player: {targetEntityId, startTick, lastDamageTick}. Transient -- cleared on
	 *  server stop and on death / relog / dimension change / power loss, same discipline as every other
	 *  static server-scratch map in the mod. */
	private static final Map<Integer, long[]> CRUSH_ACTIVE = new ConcurrentHashMap<>();
	/** Players in a forced Symbiote Slam dive, waiting to hit the ground: value = the fallback impact tick. */
	private static final Map<Integer, Long> SLAM_PENDING = new ConcurrentHashMap<>();

	private SymbioteBlackSuitAbilities() {
	}

	private static boolean blackSuit(ServerPlayer player) {
		return Symbiote.isActive(player) && SymbioteHostType.of(player) == SymbioteHostType.SPIDER_MAN;
	}

	/**
	 * Symbiote Tendril Strike: a short-range tendril lash for real damage and a solid knockback --
	 * mechanically the Normal host's Tendril Strike, so the two variants share the same feel rather
	 * than one being a pull and one being a hit.
	 */
	public static void tendrilStrike(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, TENDRIL_STRIKE)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, TENDRIL_STRIKE_RANGE);
		if (target == null) {
			return;
		}
		float damage = 9.0f + player.getRandom().nextFloat() * 2.0f;
		if (!AbilityHelpers.hurt(player, target, damage)) {
			return;
		}
		AbilityHelpers.knockbackFrom(target, player.position(), 0.9);
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.6));
		Vec3 hit = target.position().add(0, target.getBbHeight() * 0.5, 0);
		AbilityHelpers.line(level, hand, hit, ParticleTypes.SQUID_INK, 4.0);
		AbilityHelpers.burst(level, hit, ParticleTypes.SQUID_INK, 14, 0.3);
		AbilityHelpers.burst(level, hit, ParticleTypes.CRIT, 6, 0.3);
		SymbioteSounds.organic(player, 0.8f, 0.5f);
		SpiderMan.triggerCooldown(player, TENDRIL_STRIKE, CD_TENDRIL_STRIKE);
	}

	/**
	 * Symbiote Crush (press edge): lock onto whatever the player is aiming at within 10 blocks. The
	 * squeeze itself, the black-particle cover and the 8-damage-a-second tick all happen in
	 * {@link #serverTick}; {@link #releaseCrush} (key release) or the 5-second cap ends it.
	 */
	public static void beginCrush(ServerPlayer player) {
		if (CRUSH_ACTIVE.containsKey(player.getId())) {
			return;
		}
		if (!SpiderMan.abilityReady(player, CRUSH)) {
			return;
		}
		LivingEntity target = AbilityHelpers.raycastEntity(player, CRUSH_RANGE);
		if (target == null || !target.isAlive()) {
			return;
		}
		long now = player.level().getGameTime();
		CRUSH_ACTIVE.put(player.getId(), new long[] { target.getId(), now, now });
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.crush_seized"), true);
	}

	/** Key release -- let the crushed target go. Safe to call unconditionally. */
	public static void releaseCrush(ServerPlayer player) {
		endCrush(player);
	}

	private static void endCrush(ServerPlayer player) {
		if (CRUSH_ACTIVE.remove(player.getId()) != null) {
			SpiderMan.triggerCooldown(player, CRUSH, CD_CRUSH);
		}
	}

	/**
	 * Symbiote Slam (Sneak + C). Airborne: drive the player straight down and mark them for a
	 * 15-damage black-symbiote shockwave the instant they hit the ground ({@link #serverTick}).
	 * Grounded: an ordinary slam right where they stand. Returns false (so the key falls through to the
	 * wall-crawl toggle) only when the ability is still on cooldown.
	 */
	public static boolean slam(ServerPlayer player) {
		if (!SpiderMan.abilityReady(player, SLAM_ENHANCED)) {
			return false;
		}
		SpiderMan.triggerCooldown(player, SLAM_ENHANCED, CD_SLAM_ENHANCED);
		ServerLevel level = AbilityHelpers.level(player);
		if (player.onGround()) {
			slamImpact(player);
			return true;
		}
		Vec3 v = player.getDeltaMovement();
		AbilityHelpers.launchSelf(player, new Vec3(v.x * 0.3, -SLAM_DIVE_SPEED, v.z * 0.3));
		SLAM_PENDING.put(player.getId(), player.level().getGameTime() + SLAM_LAND_DEADLINE_TICKS);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.SQUID_INK, 24, 0.5);
		AbilityHelpers.burst(level, player.position().add(0, 1, 0), ParticleTypes.LARGE_SMOKE, 8, 0.4);
		SymbioteSounds.organic(player, 1.0f, 0.4f);
		return true;
	}

	private static void slamImpact(ServerPlayer player) {
		ServerLevel level = AbilityHelpers.level(player);
		Vec3 center = player.position();
		for (LivingEntity target : AbilityHelpers.enemiesAround(player, center, SLAM_RADIUS)) {
			if (AbilityHelpers.hurt(player, target, SLAM_DAMAGE)) {
				AbilityHelpers.knockbackFrom(target, center, 1.3);
			}
		}
		AbilityHelpers.burst(level, center, ParticleTypes.SQUID_INK, 44, 0.85);
		AbilityHelpers.burst(level, center, ParticleTypes.LARGE_SMOKE, 16, 0.7);
		AbilityHelpers.burst(level, center, ParticleTypes.CRIT, 16, 0.9);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_BIG_FALL, 1.0f, 0.5f);
		player.resetFallDistance();
	}

	/** Per-player upkeep for the two hold/deferred Black Suit abilities. Called every tick a Spider-Man
	 *  is online (self-gates on the black suit actually being on). */
	public static void serverTick(ServerPlayer player) {
		long now = player.level().getGameTime();

		Long slamDeadline = SLAM_PENDING.get(player.getId());
		if (slamDeadline != null) {
			player.resetFallDistance();
			player.fallDistance = 0.0f;
			if (!blackSuit(player)) {
				SLAM_PENDING.remove(player.getId());
			} else if (player.onGround() || now >= slamDeadline) {
				SLAM_PENDING.remove(player.getId());
				slamImpact(player);
			}
		}

		long[] c = CRUSH_ACTIVE.get(player.getId());
		if (c == null) {
			return;
		}
		if (!blackSuit(player)) {
			endCrush(player);
			return;
		}
		ServerLevel level = AbilityHelpers.level(player);
		Entity e = level.getEntity((int) c[0]);
		if (!(e instanceof LivingEntity target) || !target.isAlive()
				|| player.distanceToSqr(target) > CRUSH_RANGE * CRUSH_RANGE * 1.6) {
			endCrush(player);
			return;
		}
		if (now - c[1] >= CRUSH_MAX_TICKS) {
			endCrush(player);
			return;
		}

		// Pin it in place and wring it -- heavy slow + weakness while it is held.
		target.setDeltaMovement(target.getDeltaMovement().scale(0.15));
		target.hurtMarked = true;
		AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
		AbilityHelpers.applyControl(target, MobEffects.WEAKNESS, 20, 1);

		Vec3 mid = target.position().add(0, target.getBbHeight() * 0.5, 0);
		double rx = target.getBbWidth() * 0.5 + 0.15;
		level.sendParticles(ParticleTypes.SQUID_INK, mid.x, mid.y, mid.z, 12, rx, target.getBbHeight() * 0.5, rx, 0.01);
		if (player.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, mid.x, mid.y, mid.z, 4, rx, 0.35, rx, 0.0);
		}
		AbilityHelpers.line(level, player.getEyePosition().add(player.getLookAngle().scale(0.5)), mid,
				ParticleTypes.SQUID_INK, 2.5);

		if (now - c[2] >= CRUSH_DAMAGE_INTERVAL) {
			c[2] = now;
			AbilityHelpers.hurt(player, target, CRUSH_DAMAGE_PER_INTERVAL);
			SymbioteSounds.organic(player, 0.6f, 0.3f);
		}
	}

	/** Server-stop cleanup -- same discipline as every other static session map. */
	public static void clearSessionState() {
		CRUSH_ACTIVE.clear();
		SLAM_PENDING.clear();
	}

	/** Death / relog / dimension change / power loss: drop this player's transient Black Suit state. */
	public static void clearFor(ServerPlayer player) {
		CRUSH_ACTIVE.remove(player.getId());
		SLAM_PENDING.remove(player.getId());
	}
}
