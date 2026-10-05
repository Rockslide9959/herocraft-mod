package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.data.TonyStarkState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.27: countermeasure flares (Mark 2 X; the Mark III's are {@code advanced}) and the supersonic boost (Mark 2
 * Shift+X while flying).
 *
 * <ul>
 *   <li><b>Flares</b> burst from the suit and blind every hostile within {@link #RANGE} blocks in front of the wearer,
 *       with Slowness IV, for {@link #EFFECT_TICKS} (8 s).</li>
 *   <li><b>Advanced flares</b> also fly at each of those targets as homing flares: on arrival they knock it back, set
 *       it on fire for {@link #BURN_TICKS} (3 s) and burn it for {@link #BURN_DAMAGE} every {@link #BURN_INTERVAL}
 *       ticks for those 3 s.</li>
 *   <li><b>Supersonic boost</b>: flight at twice the speed for the duration (the client reads the
 *       {@link #boostUntil} timer off the synced Tony Stark state).</li>
 * </ul>
 * Homing flares in the air and burning targets are static server state keyed by the firing player's UUID, cleared in
 * {@code ServerStateReset}; {@link #tick} runs from {@link com.projecthero.mod.ironman.IronManSuitTicker}.
 */
public final class IronManFlares {
	public static final double RANGE = 8.0;
	public static final int EFFECT_TICKS = 8 * 20;
	/** Slowness IV. */
	public static final int SLOW_AMPLIFIER = 3;
	public static final float ENERGY_COST = 25f;
	public static final int BURN_TICKS = 3 * 20;
	public static final float BURN_DAMAGE = 5.0f;
	public static final int BURN_INTERVAL = 10;
	public static final double HOMING_SPEED = 1.1;
	public static final int MAX_HOMING = 8;
	/** Ability id the supersonic boost's timer is stored under (per suit) in {@code abilityReadyAt}. */
	public static final String BOOST_ID = "supersonic_boost";
	/** How much faster the boost flies. */
	public static final double BOOST_SPEED_MULTIPLIER = 2.0;

	private static final Map<UUID, List<Homing>> HOMING = new HashMap<>();
	private static final Map<UUID, List<Burn>> BURNS = new HashMap<>();

	private static final class Homing {
		Vec3 pos;
		final int targetId;
		int life = 40;
		float burnDamage = BURN_DAMAGE; // v0.14.29: per flare (the Mark 4's burn +2)

		Homing(Vec3 pos, int targetId) {
			this.pos = pos;
			this.targetId = targetId;
		}
	}

	private static final class Burn {
		final int targetId;
		final long until;
		long nextHit;
		float damage = BURN_DAMAGE; // v0.14.29

		Burn(int targetId, long until, long nextHit) {
			this.targetId = targetId;
			this.until = until;
			this.nextHit = nextHit;
		}
	}

	private IronManFlares() {
	}

	public static void clearSessionState() {
		HOMING.clear();
		BURNS.clear();
	}

	/**
	 * Fire the flares. Needs the worn chestplate; checks the {@link IronManAbilities#FLARE} cooldown and
	 * {@link #ENERGY_COST} itself, then starts {@code cooldownTicks}. Returns true if the flares went off.
	 */
	public static boolean fire(ServerPlayer player, boolean advanced, int cooldownTicks) {
		return fire(player, advanced, cooldownTicks, BURN_DAMAGE);
	}

	/** v0.14.29: {@link #fire(ServerPlayer, boolean, int)} with the advanced flares' burn damage per hit (Mark 4 = 7). */
	public static boolean fire(ServerPlayer player, boolean advanced, int cooldownTicks, float burnDamage) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.canOperate(player)) {
			return false;
		}
		if (!IronManArmor.hasChestplate(player, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_chest"), true);
			return false;
		}
		if (!IronManAbilities.cooldownReady(player, suitId, IronManAbilities.FLARE)) {
			return false;
		}
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		float cost = ENERGY_COST * (suit == null ? 1f : suit.energyCostMultiplier());
		if (!IronManEnergy.spend(player, suitId, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return false;
		}
		TonyStark.triggerCooldown(player, suitId, IronManAbilities.FLARE, cooldownTicks);

		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		Vec3 origin = eye.add(look.scale(1.0));
		level.sendParticles(ParticleTypes.FLASH, origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
		// a fan of flare streaks thrown out in front
		for (int i = 0; i < 7; i++) {
			double spread = (i - 3) * 0.18;
			Vec3 side = look.cross(new Vec3(0, 1, 0));
			side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
			Vec3 dir = look.add(side.scale(spread)).add(0, 0.15, 0).normalize();
			for (double d = 1.0; d <= RANGE; d += 1.0) {
				Vec3 p = eye.add(dir.scale(d)).add(0, -0.03 * d * d * 0.2, 0);
				level.sendParticles(ParticleTypes.FIREWORK, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.01);
				level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
		}
		IronManAbilityFx.play(player, IronManAbilityFx.FLARE, 12);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 1.4f);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.0f, 1.2f);

		List<LivingEntity> targets = targets(player);
		for (LivingEntity e : targets) {
			e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS, 0, false, true, true));
			e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, EFFECT_TICKS, SLOW_AMPLIFIER, false, true, true));
		}
		if (advanced) {
			List<Homing> list = HOMING.computeIfAbsent(player.getUUID(), k -> new ArrayList<>());
			int n = 0;
			for (LivingEntity e : targets) {
				if (n++ >= MAX_HOMING) {
					break;
				}
				Homing h = new Homing(origin, e.getId());
				h.burnDamage = burnDamage;
				list.add(h);
			}
		}
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.flare_deployed"), true);
		return true;
	}

	/** Every hostile the flares reach: within {@link #RANGE} blocks and in front of the wearer. */
	public static List<LivingEntity> targets(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle().normalize();
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, eye, RANGE)) { // area CC: rule 2, threats only
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist <= RANGE && (dist < 1.5 || to.scale(1.0 / dist).dot(look) > 0.0)) {
				out.add(e);
			}
		}
		return out;
	}

	/** Per tick for every Iron Man wearer -- steers this player's homing flares and burns what they hit. */
	public static void tick(ServerPlayer player) {
		UUID id = player.getUUID();
		List<Homing> flares = HOMING.get(id);
		List<Burn> burns = BURNS.get(id);
		if (flares == null && burns == null) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		if (flares != null) {
			for (Iterator<Homing> it = flares.iterator(); it.hasNext();) {
				Homing h = it.next();
				Entity t = level.getEntity(h.targetId);
				if (!(t instanceof LivingEntity target) || !target.isAlive() || --h.life <= 0) {
					it.remove();
					continue;
				}
				Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0);
				Vec3 to = aim.subtract(h.pos);
				double dist = to.length();
				if (dist <= HOMING_SPEED + target.getBbWidth() * 0.5) {
					it.remove();
					strike(player, target, now, h.burnDamage);
					continue;
				}
				Vec3 next = h.pos.add(to.scale(HOMING_SPEED / dist));
				for (int i = 0; i < 3; i++) {
					Vec3 p = h.pos.lerp(next, i / 3.0);
					level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
				}
				level.sendParticles(ParticleTypes.FIREWORK, next.x, next.y, next.z, 1, 0.03, 0.03, 0.03, 0.0);
				h.pos = next;
			}
			if (flares.isEmpty()) {
				HOMING.remove(id);
			}
		}
		burns = BURNS.get(id);
		if (burns != null) {
			for (Iterator<Burn> it = burns.iterator(); it.hasNext();) {
				Burn b = it.next();
				Entity t = level.getEntity(b.targetId);
				if (!(t instanceof LivingEntity target) || !target.isAlive() || now > b.until) {
					it.remove();
					continue;
				}
				if (now >= b.nextHit) {
					b.nextHit = now + BURN_INTERVAL;
					AbilityHelpers.hurtBurst(player, target, AbilityHelpers.fire(player), b.damage);
					level.sendParticles(ParticleTypes.FLAME, target.getX(), target.getY() + target.getBbHeight() * 0.5,
							target.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
				}
			}
			if (burns.isEmpty()) {
				BURNS.remove(id);
			}
		}
	}

	/** A homing flare reaching its target: knockback, 3 s alight, and 5 every half second for those 3 s. */
	static void strike(ServerPlayer player, LivingEntity target, long now) {
		strike(player, target, now, BURN_DAMAGE);
	}

	static void strike(ServerPlayer player, LivingEntity target, long now, float burnDamage) {
		ServerLevel level = (ServerLevel) player.level();
		AbilityHelpers.knockbackFrom(target, player.position(), 1.0);
		target.igniteForTicks(BURN_TICKS);
		Burn burn = new Burn(target.getId(), now + BURN_TICKS, now);
		burn.damage = burnDamage;
		BURNS.computeIfAbsent(player.getUUID(), k -> new ArrayList<>()).add(burn);
		level.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
				1, 0, 0, 0, 0);
		level.playSound(null, target.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST,
				net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
	}

	/** True if {@code target} is currently being burnt by one of this player's advanced flares. */
	public static boolean burning(ServerPlayer player, LivingEntity target) {
		List<Burn> burns = BURNS.get(player.getUUID());
		if (burns == null) {
			return false;
		}
		for (Burn b : burns) {
			if (b.targetId == target.getId()) {
				return true;
			}
		}
		return false;
	}

	/** Number of this player's homing flares still in the air. */
	public static int homingInFlight(ServerPlayer player) {
		List<Homing> list = HOMING.get(player.getUUID());
		return list == null ? 0 : list.size();
	}

	// ---------------------------------------------------------------- supersonic boost

	/**
	 * Supersonic boost: for {@code durationTicks} the wearer's suit flight goes twice as fast. Needs the worn boots;
	 * starts flight if the wearer wasn't flying. Spends {@code energyCost} (scaled by the suit's cost multiplier).
	 * Returns false (and does nothing) while a boost is already running.
	 */
	public static boolean supersonicBoost(ServerPlayer player, int durationTicks, float energyCost) {
		String suitId = IronManArmor.wornSuitId(player);
		if (suitId == null || !IronManArmor.canOperate(player)) {
			return false;
		}
		if (!IronManArmor.isPieceWorn(player, EquipmentSlot.FEET, suitId)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.need_boots"), true);
			return false;
		}
		long now = player.level().getGameTime();
		if (boostUntil(TonyStark.state(player), suitId) > now) {
			return false;
		}
		var suit = com.projecthero.mod.ironman.suit.IronManSuits.byId(suitId);
		float cost = energyCost * (suit == null ? 1f : suit.energyCostMultiplier());
		if (!IronManEnergy.spend(player, suitId, cost)) {
			IronManAbilities.noEnergy(player, cost);
			return false;
		}
		// the timer rides in the synced cooldown map, so the client's flight can read it without a new field
		TonyStark.triggerCooldown(player, suitId, BOOST_ID, durationTicks);
		if (!IronManFlight.isFlying(player)) {
			IronManFlight.setFlying(player, true);
		}
		ServerLevel level = (ServerLevel) player.level();
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.4, player.getZ(), 30, 0.4, 0.4, 0.4, 0.1);
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX(), player.getY() + 0.4, player.getZ(), 1, 0, 0, 0, 0);
		AbilityHelpers.sound(player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, 1.4f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.BREEZE_SHOOT, 1.2f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.ironman.supersonic_boost_online"), true);
		return true;
	}

	/** The game time the supersonic boost runs out for this suit (0 / past = not boosting). Either side. */
	public static long boostUntil(TonyStarkState state, String suitId) {
		if (state == null || suitId == null) {
			return 0L;
		}
		return state.abilityReadyAt.getOrDefault(suitId + "/" + BOOST_ID, 0L);
	}

	/** True while this suit's supersonic boost is running. Either side. */
	public static boolean boosting(TonyStarkState state, String suitId, long gameTime) {
		return boostUntil(state, suitId) > gameTime;
	}
}
