package com.projecthero.mod.shazam;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Planet tier: the one place every hit Shazam deals is shaped -- his moves, his bare fists, anything whose source is him.
 * Applied at the top of {@code LivingEntity#hurt} ({@code mixin.LivingEntityShazamHitMixin}), server side:
 * <ol>
 *   <li><b>Wisdom of Solomon</b>: an enemy he marked (V) takes his hits as crits, x{@link ShazamConfig#WISDOM_CRIT}.</li>
 *   <li><b>{@link ShazamConfig#MAX_HIT}</b>: no single hit of his over 40, whatever stacked on it.</li>
 *   <li><b>Bosses</b> ({@link #isBoss}): at most {@link ShazamConfig#BOSS_MAX_FRACTION_PER_HIT} of their max health per hit.</li>
 * </ol>
 * A frame per victim (as {@code ThorStorm}'s) keeps a hit re-issued from inside this one (Shazam vs Shazam, {@code ShazamDamage})
 * from being shaped twice.
 *
 * <p>Also the <b>Thunderstrike chain</b>: for 8 s after the ultimate every hit he lands arcs 16 lightning on to the nearest other enemy
 * ({@link #onDamaged}, once every {@link ShazamConfig#ULT_CHAIN_GAP} ticks; a chain never chains).
 *
 * <p>Wisdom's marks are entity ids per player (never entities), cleared by {@code ServerStateReset} through {@link #clearSessionState}.
 */
public final class ShazamCombat {
	/** player -> marked entity id -> until (game time). */
	private static final Map<UUID, Map<Integer, Long>> MARKS = new ConcurrentHashMap<>();
	/** player -> the game time of the last Thunderstrike chain. */
	private static final Map<UUID, Long> LAST_CHAIN = new ConcurrentHashMap<>();

	private static final ThreadLocal<ArrayDeque<Object>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
	private static final Object UNSHAPED = new Object();
	private static final ThreadLocal<Boolean> CHAINING = ThreadLocal.withInitial(() -> false);

	private ShazamCombat() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> onDamaged(entity, source, damageTaken));
	}

	static void clearSessionState() {
		MARKS.clear();
		LAST_CHAIN.clear();
	}

	static void forget(UUID id) {
		MARKS.remove(id);
		LAST_CHAIN.remove(id);
	}

	public static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= ShazamConfig.BOSS_HEALTH_THRESHOLD || TitanCombat.isBoss(e);
	}

	// ---------------------------------------------------------------- Wisdom of Solomon's marks

	static void mark(ServerPlayer p, LivingEntity e, long until) {
		MARKS.computeIfAbsent(p.getUUID(), k -> new ConcurrentHashMap<>()).put(e.getId(), until);
	}

	/** {@code e} is marked by {@code p}'s Wisdom of Solomon right now. */
	public static boolean marked(ServerPlayer p, Entity e) {
		Map<Integer, Long> m = MARKS.get(p.getUUID());
		if (m == null) {
			return false;
		}
		Long until = m.get(e.getId());
		return until != null && until > p.level().getGameTime();
	}

	static void tick(ServerPlayer p) {
		Map<Integer, Long> m = MARKS.get(p.getUUID());
		if (m != null && p.tickCount % 20 == 0) {
			long now = p.level().getGameTime();
			m.values().removeIf(until -> until <= now);
		}
	}

	// ---------------------------------------------------------------- the hurt hook

	/** The hit's attacker if it is a transformed Shazam (or family), else null. */
	private static ServerPlayer shazamOf(DamageSource source, LivingEntity victim) {
		return source.getEntity() instanceof ServerPlayer p && p != victim && Shazam.transformed(p) ? p : null;
	}

	/** What a hit of {@code amount} from {@code p} on {@code victim} comes to: the Wisdom crit, then the 40 cap, then the boss cap. */
	public static float shape(ServerPlayer p, LivingEntity victim, float amount) {
		float out = amount;
		if (marked(p, victim)) {
			out *= ShazamConfig.WISDOM_CRIT;
		}
		out = Math.min(out, ShazamConfig.MAX_HIT);
		if (isBoss(victim)) {
			out = Math.min(out, victim.getMaxHealth() * ShazamConfig.BOSS_MAX_FRACTION_PER_HIT);
		}
		return out;
	}

	/** {@code LivingEntity#hurt} HEAD (server): the amount to use. Always paired with {@link #exit}. */
	public static float enter(LivingEntity victim, DamageSource source, float amount) {
		ArrayDeque<Object> frames = FRAMES.get();
		ServerPlayer p = amount > 0f && !frames.contains(victim) ? shazamOf(source, victim) : null;
		frames.push(p != null ? victim : UNSHAPED);
		if (p == null) {
			return amount;
		}
		float out = shape(p, victim, amount);
		if (out > amount && victim.level() instanceof ServerLevel level) { // the crit: a gold flash of crit sparks
			Vec3 c = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 10, 0.3, 0.35, 0.3, 0.2);
			level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 8, 0.3, 0.35, 0.3, 0.25);
		}
		return out;
	}

	/** {@code LivingEntity#hurt} RETURN (server). */
	public static void exit() {
		ArrayDeque<Object> frames = FRAMES.get();
		if (!frames.isEmpty()) {
			frames.pop();
		}
	}

	// ---------------------------------------------------------------- the Thunderstrike chain

	public static boolean thunderCharged(ServerPlayer p) {
		var s = Shazam.peek(p);
		return s != null && s.transformed && s.thunderUntil > p.level().getGameTime();
	}

	/** A hit landed: while thunder-charged, it arcs 16 lightning on to the nearest other enemy within 8 blocks. */
	static void onDamaged(LivingEntity victim, DamageSource source, float taken) {
		if (CHAINING.get() || taken <= 0f || victim.level().isClientSide()) {
			return;
		}
		ServerPlayer p = shazamOf(source, victim);
		if (p == null || !thunderCharged(p)) {
			return;
		}
		long now = p.level().getGameTime();
		Long last = LAST_CHAIN.get(p.getUUID());
		if (last != null && now - last < ShazamConfig.ULT_CHAIN_GAP && now >= last) {
			return;
		}
		LivingEntity next = nearestOther(p, victim);
		if (next == null) {
			return;
		}
		LAST_CHAIN.put(p.getUUID(), now);
		chain(p, victim, next);
	}

	private static LivingEntity nearestOther(ServerPlayer p, LivingEntity from) {
		double r = ShazamConfig.ULT_CHAIN_RANGE;
		LivingEntity best = null;
		double bestD = r * r;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(r),
				e -> e != from && Shazam.areaVictim(p, e))) {
			double d = e.distanceToSqr(from);
			if (d <= bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** One chain hop (for the tests too): the arc and 16 lightning, which never chains on itself. */
	public static boolean chain(ServerPlayer p, LivingEntity from, LivingEntity to) {
		ServerLevel level = p.serverLevel();
		Vec3 a = from.position().add(0, from.getBbHeight() * 0.6, 0);
		Vec3 b = to.position().add(0, to.getBbHeight() * 0.5, 0);
		ShazamFx.arc(level, a, b, 1.2f);
		level.playSound(null, b.x, b.y, b.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35f, 1.6f);
		CHAINING.set(true);
		try {
			return Shazam.hit(p, to, ShazamConfig.ULT_CHAIN_DAMAGE, true);
		} finally {
			CHAINING.set(false);
		}
	}
}
