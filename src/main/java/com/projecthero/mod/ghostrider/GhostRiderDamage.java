package com.projecthero.mod.ghostrider;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

/**
 * What hurts Ghost Rider, and who is <i>guilty</i> (v0.16.5).
 *
 * <ul>
 *   <li>Fire and lava never hurt the host (transformed or not); transformed he can't drown either.</li>
 *   <li>Soul Shield: every hit is halved and a melee attacker is set alight.</li>
 *   <li>A creature under his Judgement Mark takes +25% from him.</li>
 *   <li>Guilt: any creature (or player) that hurts a Ghost Rider, a villager or a player earns 1 guilt, killing a villager
 *       or a player earns 5 (capped at 10, forgotten after 5 minutes without new sins). Guilt feeds Vengeance and the
 *       Penance Stare.</li>
 * </ul>
 * Fabric's {@code ALLOW_DAMAGE} is a veto, so a change cancels the hit and re-issues the new amount behind a re-entrancy
 * guard (the pattern every power here uses).
 */
public final class GhostRiderDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	private record Guilt(int points, long at) {
	}

	private record Mark(UUID owner, long until) {
	}

	/** Creature UUID -> its guilt. */
	private static final Map<UUID, Guilt> GUILT = new ConcurrentHashMap<>();
	/** Entity id -> who marked it, and until when. */
	private static final Map<Integer, Mark> MARKS = new ConcurrentHashMap<>();

	private GhostRiderDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(GhostRiderDamage::allowDamage);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof AbstractVillager || entity instanceof Player) {
				sin(source.getEntity(), entity, GhostRiderConfig.GUILT_KILL);
			}
			GUILT.remove(entity.getUUID());
			MARKS.remove(entity.getId());
		});
	}

	static void clearSessionState() {
		GUILT.clear();
		MARKS.clear();
	}

	// ---------------------------------------------------------------- guilt

	/** The creature's guilt right now (0 = innocent). Hostile monsters are born guilty (at least 1). */
	public static int guilt(Entity e) {
		int base = e instanceof net.minecraft.world.entity.monster.Enemy ? 1 : 0;
		Guilt g = GUILT.get(e.getUUID());
		if (g == null) {
			return base;
		}
		long now = e.level().getGameTime();
		if (now - g.at() > GhostRiderConfig.GUILT_MEMORY_TICKS || now < g.at()) {
			GUILT.remove(e.getUUID());
			return base;
		}
		return Math.max(base, g.points());
	}

	/** Adds guilt (tests / the command). */
	public static void addGuilt(LivingEntity e, int points) {
		int base = guilt(e); // a monster's born guilt counts as its first point
		GUILT.put(e.getUUID(), new Guilt(Math.min(GhostRiderConfig.GUILT_MAX, base + points), e.level().getGameTime()));
	}

	private static void sin(Entity culprit, LivingEntity victim, int points) {
		if (!(culprit instanceof LivingEntity sinner) || sinner == victim || !sinner.isAlive()) {
			return;
		}
		if (sinner instanceof Player p && GhostRider.hasPower(p)) {
			return; // the Spirit judges, it is not judged
		}
		addGuilt(sinner, points);
	}

	// ---------------------------------------------------------------- Judgement Mark

	static void mark(ServerPlayer owner, LivingEntity e, int ticks) {
		MARKS.put(e.getId(), new Mark(owner.getUUID(), owner.level().getGameTime() + ticks));
	}

	/** Is {@code e} marked by any Ghost Rider right now? */
	public static boolean isMarked(Entity e) {
		Mark m = MARKS.get(e.getId());
		if (m == null) {
			return false;
		}
		if (m.until() < e.level().getGameTime()) {
			MARKS.remove(e.getId());
			return false;
		}
		return true;
	}

	private static boolean markedBy(Entity e, Entity attacker) {
		Mark m = MARKS.get(e.getId());
		return m != null && attacker != null && m.owner().equals(attacker.getUUID()) && isMarked(e);
	}

	// ---------------------------------------------------------------- the veto

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		// sins: hurting a Ghost Rider, a villager or a player
		if (entity instanceof AbstractVillager || entity instanceof Player) {
			sin(source.getEntity(), entity, GhostRiderConfig.GUILT_HURT);
		}
		boolean bypass = source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
		if (!(entity instanceof ServerPlayer player) || !GhostRider.hasPower(player)) {
			if (!bypass && markedBy(entity, source.getEntity())) {
				reissue(entity, source, amount * GhostRiderConfig.MARK_MULTIPLIER);
				return false;
			}
			return true;
		}
		if (bypass) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		}
		if (!GhostRider.transformed(player)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_DROWNING)) {
			return false;
		}
		if (GhostRider.shieldUp(player)) {
			Entity attacker = source.getDirectEntity();
			if (attacker instanceof LivingEntity le && le != player && le.distanceToSqr(player) < 36.0 && !le.fireImmune()) {
				le.igniteForSeconds(GhostRiderConfig.SHIELD_BURN_SECONDS);
				((ServerLevel) player.level()).sendParticles(ParticleTypes.FLAME, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
						10, 0.3, 0.4, 0.3, 0.04);
			}
			float reduced = amount * GhostRiderConfig.SHIELD_FACTOR;
			if (reduced < 0.05f) {
				return false;
			}
			reissue(player, source, reduced);
			return false;
		}
		return true;
	}

	private static void reissue(LivingEntity entity, DamageSource source, float amount) {
		REENTRANT.set(true);
		try {
			entity.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
