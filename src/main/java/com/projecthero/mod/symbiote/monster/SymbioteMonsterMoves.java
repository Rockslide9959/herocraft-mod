package com.projecthero.mod.symbiote.monster;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.symbiote.SymbioteAbilityManager;
import com.projecthero.mod.symbiote.SymbioteSounds;
import com.projecthero.mod.symbiote.SymbioteVfx;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;
import com.projecthero.mod.symbiote.entity.SymbioteTendrilEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Symbiote Monster form: the monster's moves (the normal kit is locked out while transformed).
 *
 * <pre>
 *   R  Claw Swipe      4.5-block 120-degree cone, 24 damage, knockback                                    1 s
 *   G  Ground Pound    6-block radius, 20 damage, launched up                                              6 s
 *   X  Leap Smash      a pounce up to ~14 blocks along the aim; landing: 4 blocks, 22 damage              5 s
 *   Z  Roar            10-block cone, 8 damage, Slowness III + Weakness II 4 s, pushed back                10 s
 *   C  Devour          a non-boss, non-player mob within 5 blocks is seized, dragged to the jaws and eaten
 *                      (any health): +30 Biomass, +6 HP; a boss takes a 35-damage bite                     8 s
 *   V  Tendril Pull    everything in an 8-block cone yanked toward you (4 damage)                          8 s
 *   Shift+Z            end the form early (the melt)
 * </pre>
 * Ranges are from the edge of the monster's body (+ half its hitbox width). Bosses ({@link SymbioteAbilityManager#isBoss}) are
 * never launched, pushed, yanked or eaten (damage only); squadmates are never touched ({@link SymbioteAbilityManager#enemiesAround}).
 * Each move plays its clip at once ({@link SymbioteMonsterState#move}) and lands on the clip's strike frame ({@link #PENDING}).
 * Static maps hold entity ids and numbers only; cleared per player ({@link #clear}) and on server stop ({@link #clearAll}).
 */
public final class SymbioteMonsterMoves {
	public static final float SWIPE_DAMAGE = 24.0f;
	public static final double SWIPE_RANGE = 4.5;
	public static final double SWIPE_COS = 0.5; // 120 degrees
	public static final int SWIPE_COOLDOWN = 20;
	public static final int SWIPE_HIT_TICK = 6;

	public static final float POUND_DAMAGE = 20.0f;
	public static final double POUND_RADIUS = 6.0;
	public static final int POUND_COOLDOWN = 120;
	public static final int POUND_HIT_TICK = 10;

	public static final float LEAP_DAMAGE = 22.0f;
	public static final double LEAP_RADIUS = 4.0;
	public static final int LEAP_COOLDOWN = 100;
	public static final int LEAP_LAUNCH_TICK = 3;
	private static final int LEAP_MAX_TICKS = 80;

	public static final float ROAR_DAMAGE = 8.0f;
	public static final double ROAR_RANGE = 10.0;
	public static final double ROAR_COS = 0.42;
	public static final int ROAR_COOLDOWN = 200;
	public static final int ROAR_HIT_TICK = 6;
	public static final int ROAR_EFFECT_TICKS = 80;

	public static final double DEVOUR_RANGE = 5.0;
	public static final float DEVOUR_BIOMASS = 30.0f;
	public static final float DEVOUR_HEAL = 6.0f;
	public static final float DEVOUR_BOSS_DAMAGE = 35.0f;
	public static final int DEVOUR_COOLDOWN = 160;
	public static final int DEVOUR_SEIZE_TICK = 6;
	public static final int DEVOUR_EAT_TICK = 18;

	public static final double PULL_RANGE = 8.0;
	public static final double PULL_COS = 0.5;
	public static final float PULL_DAMAGE = 4.0f;
	public static final int PULL_COOLDOWN = 160;
	public static final int PULL_HIT_TICK = 6;

	/** playerId -> {move, game time it lands, target id (devour) or -1}. */
	private static final Map<Integer, long[]> PENDING = new ConcurrentHashMap<>();
	/** playerId -> game time of the pounce (until it lands). */
	private static final Map<Integer, Long> LEAPING = new ConcurrentHashMap<>();
	/** playerId -> {prey id, game time seized} while the prey is dragged to the jaws. */
	private static final Map<Integer, long[]> DEVOURING = new ConcurrentHashMap<>();

	private SymbioteMonsterMoves() {
	}

	public static void clearAll() {
		PENDING.clear();
		LEAPING.clear();
		DEVOURING.clear();
	}

	public static void clear(ServerPlayer player) {
		int id = player.getId();
		PENDING.remove(id);
		LEAPING.remove(id);
		releasePrey(player);
	}

	public static boolean leaping(ServerPlayer player) {
		return LEAPING.containsKey(player.getId());
	}

	// ------------------------------------------------------------------------------------------------ keys

	/**
	 * Every key while transformed comes here first ({@code SymbioteAbilityManager.handle}). @return true when the form swallowed it
	 * (always while in the cocoon / monster / melt): the normal kit never sees it.
	 */
	public static boolean intercept(ServerPlayer player, AbilitySlot slot, boolean pressed, boolean sneak) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		if (!s.inForm()) {
			return false;
		}
		if (!pressed || s.phase() != SymbioteMonsterState.MONSTER) {
			return true;
		}
		long now = player.level().getGameTime();
		if (slot == AbilitySlot.SLOT_4 && sneak) {
			SymbioteMonster.beginMelt(player, now);
			return true;
		}
		int key = slot.ordinal();
		long readyAt = s.readyAt(key);
		if (now < readyAt) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.ability_cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", (readyAt - now) / 20.0f)), true);
			return true;
		}
		if (busy(player, s, now)) {
			return true;
		}
		boolean fired = switch (slot) {
			case SLOT_1 -> start(player, s, key, SymbioteMonsterState.MOVE_SWIPE, SWIPE_HIT_TICK, SWIPE_COOLDOWN, -1, now);
			case SLOT_2 -> start(player, s, key, SymbioteMonsterState.MOVE_POUND, POUND_HIT_TICK, POUND_COOLDOWN, -1, now);
			case SLOT_3 -> leap(player, s, key, now);
			case SLOT_4 -> start(player, s, key, SymbioteMonsterState.MOVE_ROAR, ROAR_HIT_TICK, ROAR_COOLDOWN, -1, now);
			case SLOT_5 -> start(player, s, key, SymbioteMonsterState.MOVE_PULL, PULL_HIT_TICK, PULL_COOLDOWN, -1, now);
			case SLOT_6 -> devour(player, s, key, now);
			default -> false;
		};
		if (fired) {
			SymbioteVitalsManager.markCombat(player);
		}
		return true;
	}

	/** A move is still winding up (its strike has not landed yet), or the pounce is in the air. */
	private static boolean busy(ServerPlayer player, SymbioteMonsterState s, long now) {
		return PENDING.containsKey(player.getId()) || LEAPING.containsKey(player.getId()) || DEVOURING.containsKey(player.getId());
	}

	private static boolean start(ServerPlayer player, SymbioteMonsterState s, int key, int move, int hitTick, int cooldown, long target, long now) {
		SymbioteMonster.setState(player, s.withMove(move, now).withReady(key, now + cooldown));
		PENDING.put(player.getId(), new long[] { move, now + hitTick, target });
		switch (move) {
			case SymbioteMonsterState.MOVE_SWIPE -> {
				AbilityHelpers.sound(player, SoundEvents.RAVAGER_ATTACK, 1.0f, 0.6f);
				SymbioteSounds.lash(player, 1.0f, 0.5f);
			}
			case SymbioteMonsterState.MOVE_POUND -> AbilityHelpers.sound(player, SoundEvents.WARDEN_AGITATED, 1.2f, 0.6f);
			case SymbioteMonsterState.MOVE_ROAR -> AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 0.6f, 1.6f);
			case SymbioteMonsterState.MOVE_PULL -> SymbioteSounds.organic(player, 1.2f, 0.4f);
			case SymbioteMonsterState.MOVE_DEVOUR -> AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 0.7f, 1.4f);
			default -> {
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------ per tick

	static void tick(ServerPlayer player, long now) {
		int id = player.getId();
		long[] p = PENDING.get(id);
		if (p != null && now >= p[1]) {
			PENDING.remove(id);
			land(player, (int) p[0], p[2], now);
		}
		Long leapAt = LEAPING.get(id);
		if (leapAt != null) {
			long age = now - leapAt;
			if (age == LEAP_LAUNCH_TICK) {
				launch(player);
			} else if (age > LEAP_LAUNCH_TICK + 3 && (player.onGround() || player.isInWater())) {
				LEAPING.remove(id);
				leapLanding(player, now);
			} else if (age > LEAP_MAX_TICKS) {
				LEAPING.remove(id);
			}
			player.resetFallDistance();
		}
		tickPrey(player, now);
	}

	private static void land(ServerPlayer player, int move, long target, long now) {
		switch (move) {
			case SymbioteMonsterState.MOVE_SWIPE -> swipe(player);
			case SymbioteMonsterState.MOVE_POUND -> pound(player);
			case SymbioteMonsterState.MOVE_ROAR -> roar(player);
			case SymbioteMonsterState.MOVE_PULL -> pull(player);
			case SymbioteMonsterState.MOVE_DEVOUR -> seize(player, (int) target, now);
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ geometry

	private static double halfWidth(Player player) {
		return player.getBbWidth() * 0.5;
	}

	private static Vec3 flatLook(Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, player.getYRot()) : flat.normalize();
	}

	/** Enemies within {@code range} of the body's edge whose direction is inside the cone {@code cos} of the flat aim. */
	private static List<LivingEntity> cone(ServerPlayer player, double range, double cos, double vertical) {
		Vec3 c = player.position();
		Vec3 aim = flatLook(player);
		double reach = range + halfWidth(player);
		return SymbioteAbilityManager.enemiesAround(player, c.add(0, player.getBbHeight() * 0.4, 0), reach + 2.0).stream().filter(e -> {
			Vec3 d = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
			double dist = d.length() - e.getBbWidth() * 0.5;
			if (dist > reach || e.getY() > c.y + player.getBbHeight() + 1 || e.getY() + e.getBbHeight() < c.y - vertical) {
				return false;
			}
			return d.length() < halfWidth(player) + 0.6 || d.normalize().dot(aim) >= cos;
		}).toList();
	}

	private static List<LivingEntity> around(ServerPlayer player, double radius, double vertical) {
		Vec3 c = player.position();
		double reach = radius + halfWidth(player);
		return SymbioteAbilityManager.enemiesAround(player, c, reach + 2.0).stream().filter(e -> {
			double d = Math.hypot(e.getX() - c.x, e.getZ() - c.z) - e.getBbWidth() * 0.5;
			return d <= reach && Math.abs(e.getY() - c.y) <= vertical;
		}).toList();
	}

	private static Vec3 mid(LivingEntity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Where the monster's jaws are (model head: 4.6 up, 2.6 ahead at full size). */
	public static Vec3 mouth(Player player) {
		double k = player.getScale() / (1.5 + SymbioteMonster.SCALE_BONUS);
		return player.position().add(flatLook(player).scale(2.6 * k)).add(0, 4.6 * k, 0);
	}

	/** One of the monster's hands at full reach in front (for the tendrils). */
	private static Vec3 hand(Player player, boolean right) {
		double k = player.getScale() / (1.5 + SymbioteMonster.SCALE_BONUS);
		Vec3 f = flatLook(player);
		Vec3 side = new Vec3(-f.z, 0, f.x).scale(right ? 1.0 : -1.0);
		return player.position().add(f.scale(2.4 * k)).add(side.scale(1.8 * k)).add(0, 3.0 * k, 0);
	}

	private static void dust(ServerPlayer player, Vec3 centre, double radius, int count) {
		ServerLevel level = AbilityHelpers.level(player);
		BlockPos below = BlockPos.containing(centre.x, centre.y - 0.5, centre.z);
		BlockState ground = level.getBlockState(below);
		if (ground.isAir()) {
			return;
		}
		BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, ground);
		for (int i = 0; i < count; i++) {
			double a = Math.PI * 2 * i / count;
			level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y + 0.1, centre.z + Math.sin(a) * radius, 3, 0.3, 0.1, 0.3, 0.15);
		}
	}

	// ------------------------------------------------------------------------------------------------ R: Claw Swipe

	private static void swipe(ServerPlayer player) {
		Vec3 c = player.position();
		for (LivingEntity e : cone(player, SWIPE_RANGE, SWIPE_COS, 2.0)) {
			if (AbilityHelpers.hurtBurst(player, e, SWIPE_DAMAGE) && !SymbioteAbilityManager.isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, 1.3);
				AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
			}
			SymbioteVfx.impact(player, e, 1.4f);
		}
		SymbioteVfx.slash(player, (float) (SWIPE_RANGE + halfWidth(player)));
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.6f);
	}

	// ------------------------------------------------------------------------------------------------ G: Ground Pound

	private static void pound(ServerPlayer player) {
		Vec3 c = player.position();
		for (LivingEntity e : around(player, POUND_RADIUS, 3.0)) {
			if (AbilityHelpers.hurtBurst(player, e, POUND_DAMAGE) && !SymbioteAbilityManager.isBoss(e)) {
				Vec3 away = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
				away = away.lengthSqr() < 1.0e-4 ? Vec3.ZERO : away.normalize();
				e.setDeltaMovement(away.scale(0.5).add(0, 1.05, 0));
				e.hurtMarked = true;
				e.hasImpulse = true;
			}
			SymbioteVfx.impact(player, e, 1.2f);
		}
		Vec3 front = c.add(flatLook(player).scale(halfWidth(player) + 1.0));
		SymbioteVfx.groundBurst(player, front, (float) POUND_RADIUS);
		dust(player, c, 2.5, 10);
		dust(player, c, 5.0, 16);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.55f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ATTACK_IMPACT, 1.6f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.ANVIL_LAND, 0.6f, 0.4f);
	}

	// ------------------------------------------------------------------------------------------------ X: Leap Smash

	private static boolean leap(ServerPlayer player, SymbioteMonsterState s, int key, long now) {
		SymbioteMonster.setState(player, s.withMove(SymbioteMonsterState.MOVE_LEAP, now).withReady(key, now + LEAP_COOLDOWN));
		LEAPING.put(player.getId(), now);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 0.9f, 0.8f);
		return true;
	}

	/** The pounce: ~14 blocks along the aim (flat), up a little more when looking up. */
	private static void launch(ServerPlayer player) {
		Vec3 flat = flatLook(player);
		double lookUp = Math.max(0.0, player.getLookAngle().y);
		Vec3 v = flat.scale(1.45).add(0, 0.95 + lookUp * 0.45, 0);
		AbilityHelpers.launchSelf(player, v);
		SymbioteVfx.legPush(player, player.position(), v.normalize());
		dust(player, player.position(), 1.5, 8);
		SymbioteSounds.lash(player, 1.2f, 0.5f);
	}

	private static void leapLanding(ServerPlayer player, long now) {
		SymbioteMonsterState s = SymbioteMonster.state(player);
		SymbioteMonster.setState(player, s.withMove(SymbioteMonsterState.MOVE_LAND, now));
		Vec3 c = player.position();
		for (LivingEntity e : around(player, LEAP_RADIUS, 3.0)) {
			if (AbilityHelpers.hurtBurst(player, e, LEAP_DAMAGE) && !SymbioteAbilityManager.isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, 1.4);
				AbilityHelpers.push(e, new Vec3(0, 0.6, 0));
			}
			SymbioteVfx.impact(player, e, 1.2f);
		}
		SymbioteVfx.groundBurst(player, c, (float) LEAP_RADIUS + 0.5f);
		dust(player, c, 2.0, 10);
		dust(player, c, 4.0, 14);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ATTACK_IMPACT, 1.4f, 0.6f);
		player.resetFallDistance();
	}

	// ------------------------------------------------------------------------------------------------ Z: Roar

	private static void roar(ServerPlayer player) {
		Vec3 c = player.position();
		for (LivingEntity e : cone(player, ROAR_RANGE, ROAR_COS, 3.0)) {
			AbilityHelpers.hurtBurst(player, e, ROAR_DAMAGE);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ROAR_EFFECT_TICKS, 2);
			AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, ROAR_EFFECT_TICKS, 1);
			if (!SymbioteAbilityManager.isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, c, 1.5);
				AbilityHelpers.push(e, new Vec3(0, 0.25, 0));
			}
		}
		SymbioteVfx.bodyBurst(player, 2.0f);
		SymbioteVfx.groundBurst(player, c.add(flatLook(player).scale(halfWidth(player) + 2.5)), 4.0f);
		AbilityHelpers.sound(player, SoundEvents.WARDEN_ROAR, 2.2f, 0.7f);
		AbilityHelpers.sound(player, SoundEvents.RAVAGER_ROAR, 1.8f, 0.55f);
		AbilityHelpers.sound(player, SoundEvents.ENDER_DRAGON_GROWL, 0.7f, 1.3f);
	}

	// ------------------------------------------------------------------------------------------------ C: Devour

	/** Could the monster eat {@code e}? Not a boss, not a player, not a dummy / armour stand / anything the grab rules refuse. */
	public static boolean edible(ServerPlayer player, LivingEntity e) {
		return e.isAlive() && !(e instanceof Player) && !SymbioteAbilityManager.isBoss(e) && AbilityHelpers.isValidGrabTarget(e, player);
	}

	private static boolean devour(ServerPlayer player, SymbioteMonsterState s, int key, long now) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : cone(player, DEVOUR_RANGE, 0.3, 2.5)) {
			if (e instanceof Player || !(edible(player, e) || SymbioteAbilityManager.isBoss(e))) {
				continue;
			}
			double d = e.distanceToSqr(player);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		if (best == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.monster.nothing_to_eat")
					.withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		return start(player, s, key, SymbioteMonsterState.MOVE_DEVOUR, DEVOUR_SEIZE_TICK, DEVOUR_COOLDOWN, best.getId(), now);
	}

	private static void seize(ServerPlayer player, int targetId, long now) {
		Entity t = player.level().getEntity(targetId);
		if (!(t instanceof LivingEntity prey) || !prey.isAlive()) {
			return;
		}
		Vec3 m = mouth(player);
		SymbioteTendrilEntity.fromPoint(player, hand(player, true), mid(prey), prey, DEVOUR_EAT_TICK - DEVOUR_SEIZE_TICK + 2, 2, 0.22f);
		SymbioteTendrilEntity.fromPoint(player, hand(player, false), mid(prey), prey, DEVOUR_EAT_TICK - DEVOUR_SEIZE_TICK + 2, 2, 0.22f);
		SymbioteVfx.wrap(player, prey, DEVOUR_EAT_TICK - DEVOUR_SEIZE_TICK, 1.0f);
		if (SymbioteAbilityManager.isBoss(prey)) {
			// too big to eat: the jaws come down on it instead
			AbilityHelpers.hurtBurst(player, prey, DEVOUR_BOSS_DAMAGE);
			SymbioteVfx.jaws(player, prey, mid(prey), 2.0f);
			AbilityHelpers.sound(player, SoundEvents.EVOKER_FANGS_ATTACK, 1.4f, 0.4f);
			AbilityHelpers.sound(player, SoundEvents.RAVAGER_ATTACK, 1.2f, 0.5f);
			player.displayClientMessage(Component.translatable("message.projecthero.symbiote.devour_too_big"), true);
			return;
		}
		DEVOURING.put(player.getId(), new long[] { targetId, now });
		prey.setNoGravity(true);
		prey.setDeltaMovement(Vec3.ZERO);
		SymbioteSounds.organic(player, 1.2f, 0.4f);
	}

	private static void tickPrey(ServerPlayer player, long now) {
		long[] d = DEVOURING.get(player.getId());
		if (d == null) {
			return;
		}
		Entity t = player.level().getEntity((int) d[0]);
		if (!(t instanceof LivingEntity prey) || !prey.isAlive() || prey.distanceToSqr(player) > 196.0) {
			releasePrey(player);
			return;
		}
		long age = now - d[1];
		int pull = DEVOUR_EAT_TICK - DEVOUR_SEIZE_TICK;
		Vec3 m = mouth(player);
		if (age < pull) {
			double f = Math.min(1.0, (age + 1) / (double) pull);
			f = f * f * (3 - 2 * f);
			Vec3 at = mid(prey);
			Vec3 next = at.add(m.subtract(at).scale(Math.min(1.0, f * 0.7)));
			prey.teleportTo(next.x, next.y - prey.getBbHeight() * 0.5, next.z);
			prey.setDeltaMovement(Vec3.ZERO);
			prey.fallDistance = 0;
			return;
		}
		DEVOURING.remove(player.getId());
		prey.setNoGravity(false);
		Vec3 at = mid(prey);
		AbilityHelpers.hurtBurst(player, prey, prey.getHealth() + prey.getAbsorptionAmount() + 100.0f);
		if (prey.isAlive()) {
			prey.kill();
		}
		SymbioteVitalsManager.gainBiomass(player, DEVOUR_BIOMASS);
		player.heal(DEVOUR_HEAL);
		SymbioteVfx.jaws(player, null, at, 2.0f);
		AbilityHelpers.sound(player, SoundEvents.GENERIC_EAT, 1.5f, 0.4f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_BURP, 1.2f, 0.5f);
		AbilityHelpers.sound(player, SoundEvents.EVOKER_FANGS_ATTACK, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.symbiote.devoured", prey.getDisplayName())
				.withStyle(ChatFormatting.DARK_PURPLE), true);
	}

	private static void releasePrey(ServerPlayer player) {
		long[] d = DEVOURING.remove(player.getId());
		if (d != null && player.level().getEntity((int) d[0]) instanceof LivingEntity prey) {
			prey.setNoGravity(false);
		}
	}

	// ------------------------------------------------------------------------------------------------ V: Tendril Pull

	private static void pull(ServerPlayer player) {
		Vec3 c = player.position();
		Vec3 front = c.add(flatLook(player).scale(halfWidth(player) + 1.2));
		int k = 0;
		for (LivingEntity e : cone(player, PULL_RANGE, PULL_COS, 3.0)) {
			SymbioteTendrilEntity.fromPoint(player, hand(player, (k++ & 1) == 0), mid(e), e, 12, 2, 0.2f);
			AbilityHelpers.hurtBurst(player, e, PULL_DAMAGE);
			if (SymbioteAbilityManager.isBoss(e) || !AbilityHelpers.isValidGrabTarget(e, player) && !(e instanceof Player)) {
				continue;
			}
			Vec3 to = front.subtract(e.position());
			double dist = Math.sqrt(to.x * to.x + to.z * to.z);
			if (dist < 0.5) {
				continue;
			}
			Vec3 dir = new Vec3(to.x / dist, 0, to.z / dist);
			e.setDeltaMovement(dir.scale(0.45 + 0.17 * dist).add(0, 0.35, 0));
			e.hurtMarked = true;
			e.hasImpulse = true;
			if (e instanceof ServerPlayer sp) {
				sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
			}
			SymbioteVfx.impact(player, e, 0.9f);
		}
		SymbioteSounds.lash(player, 1.5f, 0.6f);
		AbilityHelpers.sound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 1.4f, 0.5f);
	}
}
