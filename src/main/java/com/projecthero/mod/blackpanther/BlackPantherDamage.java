package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.firearm.Gunfire;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * What hurts Black Panther (v0.17.3), suited:
 * <ul>
 *   <li>no fall damage;</li>
 *   <li>arrows and bullets under {@link BlackPantherConfig#DEFLECT_LIMIT} bounce off and charge the bar like a soaked hit;</li>
 *   <li>Absorb Stance: frontal hits do nothing and go wholly into the bar, up to 40 a stance (then it breaks);</li>
 *   <li>every other hit from something (a creature, a blast): 40% soaked and stored, 1 damage = 4 Kinetic. Burning, drowning,
 *       hunger and the like are not hits and are not soaked.</li>
 * </ul>
 * And a Stalk hit: from behind, three times the damage on a creature, 1.75x on a player (capped on bosses); any hit ends the Stalk. A change cancels the hit
 * and re-issues the new amount behind a re-entrancy guard, vanilla's difficulty scaling undone first.
 */
public final class BlackPantherDamage {
	private static final ThreadLocal<Boolean> REENTRANT = ThreadLocal.withInitial(() -> false);

	/** The last projectile hit, for Kinetic Return. */
	public record LastShot(UUID shooter, float damage, long at) {
	}

	private static final Map<UUID, LastShot> LAST_SHOT = new ConcurrentHashMap<>();

	private BlackPantherDamage() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(BlackPantherDamage::allowDamage);
	}

	static void clearSessionState() {
		LAST_SHOT.clear();
		LAST_HURT.clear();
	}

	static void forget(UUID id) {
		LAST_SHOT.remove(id);
		LAST_HURT.remove(id);
	}

	public static LastShot lastShot(ServerPlayer player) {
		return LAST_SHOT.get(player.getUUID());
	}

	/** A Stalk hit from behind: x3 on a creature, x1.75 on a player (the tester's PvP nerf). */
	public static float backstabFactor(LivingEntity target) {
		return target instanceof net.minecraft.world.entity.player.Player ? BlackPantherConfig.STALK_BACKSTAB_PLAYER : BlackPantherConfig.STALK_BACKSTAB;
	}

	/** v0.17.4 Shadow Step (the unsuited Stalk): x2 on a creature, x1.5 on a player. */
	public static float shadowFactor(LivingEntity target) {
		return target instanceof net.minecraft.world.entity.player.Player ? BlackPantherConfig.SHADOW_BACKSTAB_PLAYER : BlackPantherConfig.SHADOW_BACKSTAB;
	}

	/** v0.17.4: the last time this Black Panther was hurt (Herb Vitality waits 5 s after it). */
	public static long lastHurt(ServerPlayer player) {
		Long t = LAST_HURT.get(player.getUUID());
		return t == null ? Long.MIN_VALUE / 2 : t;
	}

	private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();

	public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (amount > 0.0f && entity instanceof ServerPlayer hurt && BlackPanther.hasPower(hurt)) {
			LAST_HURT.put(hurt.getUUID(), hurt.level().getGameTime());
		}
		if (REENTRANT.get() || amount <= 0.0f) {
			return true;
		}
		if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity && BlackPanther.hasPower(attacker)) {
			float factor = 1f;
			if (BlackPanther.state(attacker).stalking) {
				boolean suitedStalk = BlackPanther.suited(attacker);
				boolean behind = behind(attacker, entity);
				if (suitedStalk) {
					BlackPantherAbilities.endStalk(attacker);
				} else {
					BlackPantherUnsuited.endShadow(attacker);
				}
				if (behind) {
					factor *= suitedStalk ? backstabFactor(entity) : shadowFactor(entity);
					ServerLevel level = (ServerLevel) attacker.level();
					Vec3 c = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
					level.sendParticles(suitedStalk ? BlackPanther.PURPLE_BIG : net.minecraft.core.particles.ParticleTypes.CRIT, c.x, c.y, c.z, 12, 0.3, 0.4,
							0.3, 0.0);
					level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0f, 0.7f);
				}
			}
			// v0.17.4 Kimoyo Scan: the marked creature takes more from him
			if (!BlackPantherCombat.striking() && BlackPantherUnsuited.marked(attacker, entity)) { // (a move's own strike adds it itself)
				factor *= BlackPantherConfig.SCAN_BONUS;
			}
			if (factor != 1f) {
				reissue(entity, source, BlackPantherCombat.capped(entity, amount * factor));
				return false;
			}
		}
		if (entity instanceof ServerPlayer bare && BlackPanther.hasPower(bare) && !BlackPanther.suited(bare)) {
			return allowBare(bare, source, amount);
		}
		if (!(entity instanceof ServerPlayer player) || !BlackPanther.suited(player)) {
			return true;
		}
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL)) {
			player.resetFallDistance();
			return false;
		}
		// Kinetic Return remembers the RAW incoming amount, before any soak, deflection or stance -- arrows and bullets alike,
		// even a pellet that lands inside the hurt window
		boolean shot = source.is(DamageTypeTags.IS_PROJECTILE) || Gunfire.active();
		if (shot && source.getEntity() != null && source.getEntity() != player) {
			LAST_SHOT.put(player.getUUID(), new LastShot(source.getEntity().getUUID(), amount, player.level().getGameTime()));
		}
		if (player.invulnerableTime > 10) {
			return true; // vanilla will ignore this hit: it must not charge the bar
		}
		BlackPantherState s = BlackPanther.state(player);
		if (s.absorbing && fromFront(player, source)) {
			float gain = Math.min(amount * BlackPantherConfig.KINETIC_PER_DAMAGE, BlackPantherConfig.ABSORB_MAX - s.absorbed);
			BlackPanther.store(player, gain, true);
			impact(player, 8);
			if (BlackPanther.state(player).absorbed >= BlackPantherConfig.ABSORB_MAX - 0.01f) {
				BlackPantherAbilities.breakStance(player);
			}
			return false;
		}
		if (shot && amount < BlackPantherConfig.DEFLECT_LIMIT) {
			BlackPanther.store(player, amount * BlackPantherConfig.SOAK * BlackPantherConfig.KINETIC_PER_DAMAGE, false);
			impact(player, 4);
			return false;
		}
		if (source.getEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION)) {
			float soak = amount * BlackPantherConfig.SOAK;
			BlackPanther.store(player, soak * BlackPantherConfig.KINETIC_PER_DAMAGE, false);
			impact(player, 3);
			reissue(player, source, amount - soak);
			return false;
		}
		return true;
	}

	/**
	 * v0.17.4, out of the suit: Cat's Landing halves falls; a Backflip's evade makes the next hit miss; a raised Parry cuts a
	 * frontal melee hit by 60%, or turns it wholly aside (and staggers the attacker) in its first 6 ticks.
	 */
	private static boolean allowBare(ServerPlayer player, DamageSource source, float amount) {
		if (source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			float left = amount * BlackPantherConfig.CAT_LANDING;
			if (left < 0.5f) {
				return false;
			}
			reissue(player, source, left);
			return false;
		}
		if (player.invulnerableTime > 10) {
			return true;
		}
		Entity attacker = source.getEntity();
		if ((attacker != null || source.is(DamageTypeTags.IS_PROJECTILE)) && BlackPantherUnsuited.evade(player)) {
			return false;
		}
		BlackPantherState s = BlackPanther.state(player);
		boolean melee = attacker instanceof LivingEntity && source.getDirectEntity() == attacker && !source.is(DamageTypeTags.IS_PROJECTILE);
		if (s.absorbing && melee && fromFront(player, source)) {
			if (BlackPantherUnsuited.perfectParry(player, (LivingEntity) attacker)) {
				return false;
			}
			ServerLevel level = (ServerLevel) player.level();
			level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.8f, 1.2f);
			reissue(player, source, amount * (1f - BlackPantherConfig.PARRY_REDUCTION));
			return false;
		}
		return true;
	}

	private static void impact(ServerPlayer player, int particles) {
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(BlackPanther.PURPLE, player.getX(), player.getY() + 1.0, player.getZ(), particles, 0.35, 0.5, 0.35, 0.0);
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 0.8f);
	}

	static boolean fromFront(ServerPlayer player, DamageSource source) {
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
		Vec3 pos = from != null ? from.position() : source.getSourcePosition();
		if (pos == null) {
			return false;
		}
		Vec3 flat = new Vec3(pos.x - player.getX(), 0, pos.z - player.getZ());
		Vec3 look = player.getLookAngle();
		Vec3 lookFlat = new Vec3(look.x, 0, look.z);
		if (flat.lengthSqr() < 1.0e-4 || lookFlat.lengthSqr() < 1.0e-4) {
			return true;
		}
		return flat.normalize().dot(lookFlat.normalize()) >= BlackPantherConfig.FRONT_ARC;
	}

	/** The attacker stands behind where {@code target} is facing. */
	static boolean behind(Entity attacker, LivingEntity target) {
		Vec3 flat = new Vec3(attacker.getX() - target.getX(), 0, attacker.getZ() - target.getZ());
		Vec3 facing = Vec3.directionFromRotation(0f, target.getYHeadRot());
		return flat.lengthSqr() > 1.0e-4 && flat.normalize().dot(facing) < -0.2;
	}

	private static void reissue(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer && source.scalesWithDifficulty()) {
			amount = switch (target.level().getDifficulty()) {
				case HARD -> amount / 1.5f;
				case EASY -> amount >= 2f ? 2f * (amount - 1f) : amount;
				default -> amount;
			};
		}
		REENTRANT.set(true);
		try {
			target.hurt(source, amount);
		} finally {
			REENTRANT.set(false);
		}
	}
}
