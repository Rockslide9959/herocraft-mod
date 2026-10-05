package com.projecthero.mod.ironman;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * v0.14.29 (agent F): the repulsor -&gt; melee combo, for every Iron Man suit.
 *
 * <ul>
 *   <li>A repulsor hit (tap, charged, the dash, the hand repulsor -- and on the Mark 1, which has no repulsor, the
 *       Strong Punch and the flamethrower) marks the target <b>Staggered</b> for {@link #STAGGER_TICKS}: a brief
 *       Slowness and a crackling spark ring round it.</li>
 *   <li>The next vanilla melee punch an Iron Man wearer lands on a staggered target consumes the mark and deals
 *       {@link #bonusDamage bonus damage} (+50% of the suit's melee bonus, at least {@link #MIN_BONUS}) with an impact
 *       burst.</li>
 *   <li>A punch followed by a repulsor hit within {@link #PUNCH_TO_REPULSOR_TICKS} gives the repulsor extra knockback.</li>
 * </ul>
 *
 * <p>Server side only. The melee punch is told apart from ability hits (which use the same {@code playerAttack} damage
 * source) by {@code PlayerAttackComboMixin} bracketing {@code Player.attack}. All maps are static server state, cleared
 * in {@code ServerStateReset}.
 */
public final class IronManCombo {
	public static final int STAGGER_TICKS = 60;
	public static final int STAGGER_SLOW_TICKS = 30;
	public static final float MIN_BONUS = 3.0f;
	public static final int PUNCH_TO_REPULSOR_TICKS = 30;
	public static final double PUNCH_KNOCKBACK_BONUS = 0.8;

	private record Stagger(LivingEntity target, long until) {
	}

	private static final Map<UUID, Stagger> STAGGERED = new HashMap<>();
	private static final Map<UUID, Long> LAST_PUNCH = new HashMap<>();
	/** The melee swing in flight on this thread: {attacker, target entity id}; set by the attack mixin. */
	private static final ThreadLocal<ServerPlayer> MELEE_ATTACKER = new ThreadLocal<>();
	private static final ThreadLocal<Integer> MELEE_TARGET = new ThreadLocal<>();

	private IronManCombo() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register(IronManCombo::afterDamage);
		ServerTickEvents.END_SERVER_TICK.register(IronManCombo::serverTick);
	}

	// ---------------- hooks ----------------

	/** A repulsor (or Mark 1 punch / flame) hit landed on {@code target}: stagger it, and pay off a punch-first combo. */
	public static void onRepulsorHit(ServerPlayer player, LivingEntity target) {
		if (target == null || target == player || !target.isAlive() || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		boolean fresh = !isStaggered(target);
		STAGGERED.put(target.getUUID(), new Stagger(target, now + STAGGER_TICKS));
		if (fresh) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STAGGER_SLOW_TICKS, 1, false, false, false), player);
			ring(level, target, 0);
		}
		Long punched = LAST_PUNCH.remove(player.getUUID());
		if (punched != null && now - punched <= PUNCH_TO_REPULSOR_TICKS) {
			AbilityHelpers.knockbackFrom(target, player.position(), PUNCH_KNOCKBACK_BONUS);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					10, 0.3, 0.3, 0.3, 0.2);
		}
	}

	/** {@code PlayerAttackComboMixin} HEAD: a vanilla melee swing is starting. */
	public static void beforeMelee(ServerPlayer player, Entity target) {
		MELEE_ATTACKER.set(player);
		MELEE_TARGET.set(target == null ? -1 : target.getId());
	}

	/** {@code PlayerAttackComboMixin} RETURN: the swing is over. */
	public static void afterMelee(ServerPlayer player) {
		MELEE_ATTACKER.remove();
		MELEE_TARGET.remove();
	}

	public static boolean isStaggered(LivingEntity target) {
		Stagger s = STAGGERED.get(target.getUUID());
		return s != null && s.until > target.level().getGameTime() && target.isAlive();
	}

	/** +50% of the worn suit's melee bonus, never less than {@link #MIN_BONUS}. */
	public static float bonusDamage(IronManSuit suit) {
		float strength = suit == null ? 0f : (float) suit.strengthBonus();
		return Math.max(MIN_BONUS, strength * 0.5f);
	}

	// ---------------- internals ----------------

	private static void afterDamage(LivingEntity entity, DamageSource source, float base, float dealt, boolean blocked) {
		ServerPlayer attacker = MELEE_ATTACKER.get();
		if (attacker == null || dealt <= 0f || source.getEntity() != attacker || source.getDirectEntity() != attacker
				|| !source.is(DamageTypes.PLAYER_ATTACK)) {
			return;
		}
		Integer targetId = MELEE_TARGET.get();
		if (targetId == null || targetId != entity.getId()) {
			return;
		}
		String suitId = IronManArmor.wornSuitId(attacker);
		if (suitId == null || !TonyStark.hasPower(attacker)) {
			return;
		}
		ServerLevel level = (ServerLevel) attacker.level();
		LAST_PUNCH.put(attacker.getUUID(), level.getGameTime());
		if (!isStaggered(entity)) {
			return;
		}
		STAGGERED.remove(entity.getUUID()); // consumed before the bonus hit re-enters this listener
		float bonus = bonusDamage(IronManSuits.byId(suitId));
		if (entity.isAlive()) {
			AbilityHelpers.hurtBurst(attacker, entity, bonus);
		}
		double y = entity.getY() + entity.getBbHeight() * 0.55;
		level.sendParticles(ParticleTypes.EXPLOSION, entity.getX(), y, entity.getZ(), 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), y, entity.getZ(), 24, 0.35, 0.35, 0.35, 0.4);
		level.sendParticles(ParticleTypes.CRIT, entity.getX(), y, entity.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.6f);
		level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.35f, 1.8f);
		level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3f, 1.9f);
	}

	private static void serverTick(MinecraftServer server) {
		if (STAGGERED.isEmpty()) {
			return;
		}
		Iterator<Stagger> it = STAGGERED.values().iterator();
		while (it.hasNext()) {
			Stagger s = it.next();
			LivingEntity t = s.target;
			if (t.isRemoved() || !t.isAlive() || !(t.level() instanceof ServerLevel level) || level.getGameTime() >= s.until) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			if (now % 4 == 0) {
				ring(level, t, now);
			}
		}
	}

	/** A small rotating ring of electric sparks round the target's middle. */
	private static void ring(ServerLevel level, LivingEntity t, long now) {
		double r = Math.max(0.45, t.getBbWidth() * 0.75);
		double y = t.getY() + t.getBbHeight() * 0.55;
		double spin = now * 0.35;
		for (int i = 0; i < 8; i++) {
			double a = spin + i * (Math.PI * 2 / 8);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX() + Math.cos(a) * r, y, t.getZ() + Math.sin(a) * r,
					1, 0, 0, 0, 0);
		}
	}

	public static void clearSessionState() {
		STAGGERED.clear();
		LAST_PUNCH.clear();
	}
}
