package com.projecthero.mod.powerarmour.kit;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.SuitData;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: the base moves (Servo Punch, Ground Stomp, Servo Grip / throw, Shoulder Charge, Core Overload, Jump Jets /
 * Thruster Pack, Visor) and the per-tick upkeep of everything that lasts (charges, grips, thrown mobs, the overload wind-up, the
 * shield, the repair drone, the rail beam). Numbers: {@link PowerArmourConfig}. The arm weapons and back modules are
 * {@link PowerArmourWeapons}.
 *
 * <p>Live state is in the player's {@link PowerArmourState} (synced: poses, HUD) plus three static maps of ids (the charge runs,
 * thrown mobs, rail beams) cleared by {@code ServerStateReset}.
 */
public final class PowerArmourAbilities {
	public static final int ANIM_NONE = 0, ANIM_PUNCH = 1, ANIM_PUNCH_HEAVY = 2, ANIM_STOMP = 3, ANIM_GRAB = 4, ANIM_JETS = 5, ANIM_THROW = 6,
			ANIM_CHARGE = 7, ANIM_OVERLOAD = 8, ANIM_WEAPON = 9, ANIM_PILEDRIVER = 10, ANIM_MISSILES = 11, ANIM_DEPLOY = 12, ANIM_RAIL = 13,
			ANIM_TESLA = 14, ANIM_BLAST = 15;
	/** {@link PowerArmourState#charge}: a held key. */
	public static final int CHARGE_PUNCH = 1, CHARGE_RAIL = 2, CHARGE_EXIT = 3, CHARGE_FIRE = 4;

	private record ChargeRun(Vec3 dir, int[] left, Set<Integer> hit) {
	}

	private record Thrown(UUID owner, long until, float damage) {
	}

	private static final Map<UUID, ChargeRun> CHARGES = new ConcurrentHashMap<>();
	private static final Map<UUID, Thrown> THROWN = new ConcurrentHashMap<>();
	static final Map<UUID, Integer> RAIL = new ConcurrentHashMap<>();

	private PowerArmourAbilities() {
	}

	public static void clearSessionState() {
		CHARGES.clear();
		THROWN.clear();
		RAIL.clear();
	}

	// ================================================================== helpers

	static boolean ready(PowerArmourState s, String key, long now) {
		return s.readyAt.getOrDefault(key, 0L) <= now;
	}

	static void cool(PowerArmourState s, String key, int base, long now) {
		s.readyAt.put(key, now + Math.max(1, Math.round(base * s.suit.tier.cooldown)));
	}

	/** v0.17.7 (Street tier) ceilings: a kit hit never goes over its range's top, whatever the suit tier and Overclock. */
	static float melee(float d) {
		return Math.min(d, 10f); // v0.17.7: was PowerRank.STREET.meleeMax (tiers removed)
	}

	static float ability(float d) {
		return Math.min(d, 10f); // v0.17.7: was PowerRank.STREET.abilityMax (tiers removed)
	}

	static float ultimate(float d) {
		return Math.min(d, 20f); // v0.17.7: was PowerRank.STREET.ultimateMax (tiers removed)
	}

	/** Every kit hit: the tier's damage, +15% with Overclock. */
	static float mul(SuitData suit) {
		return suit.tier.damage * (suit.has(ModuleType.OVERCLOCK) ? PowerArmourConfig.OVERCLOCK_DAMAGE : 1f);
	}

	static boolean pay(ServerPlayer player, PowerArmourState s, float cost) {
		if (s.suit.spend(cost)) {
			return true;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.power_armour.low_power").withStyle(ChatFormatting.GRAY), true);
		return false;
	}

	static void anim(PowerArmourState s, int anim, long now) {
		s.anim = anim;
		s.animStart = now;
	}

	static boolean isBoss(LivingEntity e) {
		return e.getMaxHealth() >= PowerArmourConfig.BOSS_HEALTH || com.projecthero.mod.titanshifter.TitanCombat.isBoss(e);
	}

	static ServerLevel level(ServerPlayer p) {
		return p.serverLevel();
	}

	static void sound(ServerPlayer p, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
		level(p).playSound(null, p.getX(), p.getY() + 1.0, p.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
	}

	/** Breaking a block with a move: only if the player could, and never a block entity or something tough. */
	static boolean breakable(ServerPlayer player, BlockPos pos, float maxHardness) {
		ServerLevel level = level(player);
		BlockState state = level.getBlockState(pos);
		if (state.isAir() || level.getBlockEntity(pos) != null || !player.mayBuild() || !level.mayInteract(player, pos)) {
			return false;
		}
		float h = state.getDestroySpeed(level, pos);
		return h >= 0f && h <= maxHardness;
	}

	public static boolean turretOut(PowerArmourState s, long now) {
		return s.readyAt.getOrDefault("turret_out", 0L) > now;
	}

	// ================================================================== R: Servo Punch

	public static void punchStart(ServerPlayer player, PowerArmourState s, long now) {
		if (!ready(s, "punch", now) || s.charge != 0) {
			return;
		}
		s.charge = CHARGE_PUNCH;
		s.chargeStart = now;
		anim(s, ANIM_PUNCH, now);
	}

	/** A key let go: ends a punch charge (and throws it), a rail charge (fires it), a held weapon. */
	public static void release(ServerPlayer player, PowerArmourState s, AbilitySlot slot) {
		long now = player.level().getGameTime();
		if (slot == AbilitySlot.SLOT_1) {
			if (s.charge == CHARGE_PUNCH) {
				s.charge = 0;
				if (s.suit.powered()) {
					punch(player, s, Mth.clamp((now - s.chargeStart) / (float) PowerArmourConfig.PUNCH_CHARGE_TICKS, 0f, 1f), now);
				}
			} else if (s.charge == CHARGE_RAIL || s.charge == CHARGE_FIRE) {
				PowerArmourWeapons.weaponRelease(player, s, now);
			}
		} else if (slot == AbilitySlot.SLOT_3) {
			thrust(player, s, false);
		}
	}

	private static void punch(ServerPlayer player, PowerArmourState s, float charge, long now) {
		SuitData suit = s.suit;
		float cost = PowerArmourConfig.PUNCH_COST + charge * PowerArmourConfig.PUNCH_CHARGED_COST;
		if (!pay(player, s, cost)) {
			return;
		}
		boolean fist = suit.has(ModuleType.POWER_FIST);
		float damage = melee(PowerArmourConfig.PUNCH_DAMAGE * mul(suit) * (1f + PowerArmourConfig.PUNCH_CHARGE_BONUS * charge)
				* (fist ? PowerArmourConfig.POWER_FIST_MULT : 1f));
		Vec3 look = player.getLookAngle();
		player.swing(InteractionHand.MAIN_HAND, true);
		anim(s, charge >= 0.5f ? ANIM_PUNCH_HEAVY : ANIM_PUNCH, now);
		cool(s, "punch", PowerArmourConfig.PUNCH_COOLDOWN, now);
		LivingEntity target = AbilityHelpers.raycastEntity(player, PowerArmourConfig.PUNCH_RANGE);
		ServerLevel level = level(player);
		Vec3 fistAt = player.getEyePosition().add(look.scale(1.6));
		if (target != null && AbilityHelpers.hurtBurst(player, target, damage)) {
			double kb = PowerArmourConfig.PUNCH_KNOCKBACK + charge * 1.3;
			if (!isBoss(target)) {
				AbilityHelpers.push(target, new Vec3(look.x * kb, 0.25 + charge * 0.3, look.z * kb));
			}
			fistAt = target.position().add(0, target.getBbHeight() * 0.6, 0);
			level.sendParticles(ParticleTypes.CRIT, fistAt.x, fistAt.y, fistAt.z, 10, 0.2, 0.2, 0.2, 0.3);
		}
		sound(player, charge >= 0.5f ? SoundEvents.IRON_GOLEM_ATTACK : SoundEvents.PLAYER_ATTACK_STRONG, 1f, charge >= 0.5f ? 0.7f : 0.9f);
		if (charge >= 0.5f) {
			level.sendParticles(ParticleTypes.EXPLOSION, fistAt.x, fistAt.y, fistAt.z, 1, 0, 0, 0, 0);
			// a charged punch breaks the weak block it lands on
			HitResult hit = player.pick(PowerArmourConfig.PUNCH_RANGE + 0.5, 1f, false);
			if (target == null && hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
					&& breakable(player, bhr.getBlockPos(), PowerArmourConfig.BREAK_HARDNESS)) {
				level.destroyBlock(bhr.getBlockPos(), true, player);
			}
			if (fist) { // Power Fist: the shockwave carries on in a cone
				for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position().add(look.scale(2.0)), PowerArmourConfig.POWER_FIST_WAVE)) {
					if (e == target) {
						continue;
					}
					Vec3 to = e.position().subtract(player.position()).normalize();
					if (to.dot(look) > 0.6 && AbilityHelpers.hurtBurst(player, e, damage * 0.5f) && !isBoss(e)) {
						AbilityHelpers.push(e, new Vec3(to.x * 1.2, 0.35, to.z * 1.2));
					}
				}
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, fistAt.x, fistAt.y, fistAt.z, 3, 0.6, 0.3, 0.6, 0);
			}
		}
	}

	// ================================================================== G: Ground Stomp

	public static void stomp(ServerPlayer player, PowerArmourState s, long now) {
		if (!ready(s, "stomp", now) || !pay(player, s, PowerArmourConfig.STOMP_COST)) {
			return;
		}
		SuitData suit = s.suit;
		boolean seismic = suit.has(ModuleType.SEISMIC_PLATES);
		double r = seismic ? PowerArmourConfig.SEISMIC_RADIUS : PowerArmourConfig.STOMP_RADIUS;
		float damage = ability(PowerArmourConfig.STOMP_DAMAGE * mul(suit) * (seismic ? 1.2f : 1f));
		Vec3 c = player.position();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, c, r)) {
			if (Math.abs(e.getY() - c.y) > 2.5) {
				continue;
			}
			double d = Math.sqrt(e.distanceToSqr(c));
			float fall = (float) (1.0 - 0.4 * Math.min(1.0, d / r));
			if (AbilityHelpers.hurtBurst(player, e, damage * fall) && !isBoss(e)) {
				Vec3 out = e.position().subtract(c).multiply(1, 0, 1).normalize();
				AbilityHelpers.push(e, new Vec3(out.x * 0.9, 0.55, out.z * 0.9));
			}
			if (seismic) {
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 60, 1);
			}
		}
		ServerLevel level = level(player);
		BlockState below = level.getBlockState(player.blockPosition().below());
		if (!below.isAir()) {
			for (int i = 0; i < 28; i++) {
				double a = i / 28.0 * Math.PI * 2;
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), c.x + Math.cos(a) * r * 0.6, c.y + 0.1,
						c.z + Math.sin(a) * r * 0.6, 3, 0.2, 0.05, 0.2, 0.15);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.1, c.z, 2, 0.6, 0, 0.6, 0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.6f, 1.4f);
		sound(player, SoundEvents.IRON_GOLEM_DAMAGE, 1f, 0.5f);
		anim(s, ANIM_STOMP, now);
		cool(s, "stomp", PowerArmourConfig.STOMP_COOLDOWN, now);
	}

	// ================================================================== Shift+G: Servo Grip / Magnetic Clamp

	public static void grip(ServerPlayer player, PowerArmourState s, long now) {
		if (s.grabbed >= 0) {
			throwHeld(player, s, now);
			return;
		}
		if (!ready(s, "grip", now)) {
			return;
		}
		boolean clamp = s.suit.has(ModuleType.MAGNETIC_CLAMP);
		LivingEntity target = AbilityHelpers.raycastEntity(player, clamp ? PowerArmourConfig.CLAMP_RANGE : PowerArmourConfig.GRIP_RANGE);
		if (target == null || target instanceof Player || !HeroTargets.canHarm(player, target)) {
			return;
		}
		if (isBoss(target)) {
			player.displayClientMessage(Component.translatable("message.projecthero.power_armour.too_heavy").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (!pay(player, s, PowerArmourConfig.GRIP_COST)) {
			return;
		}
		s.grabbed = target.getId();
		s.chargeStart = now;
		anim(s, ANIM_GRAB, now);
		sound(player, clamp ? SoundEvents.BEACON_POWER_SELECT : SoundEvents.IRON_GOLEM_ATTACK, 0.8f, clamp ? 1.8f : 1.2f);
		if (clamp) {
			AbilityHelpers.line(level(player), player.getEyePosition(), target.position().add(0, target.getBbHeight() / 2, 0),
					ParticleTypes.ELECTRIC_SPARK, 3);
		}
	}

	private static void throwHeld(ServerPlayer player, PowerArmourState s, long now) {
		Entity e = player.level().getEntity(s.grabbed);
		s.grabbed = -1;
		cool(s, "grip", PowerArmourConfig.GRIP_COOLDOWN, now);
		anim(s, ANIM_THROW, now);
		if (e instanceof LivingEntity target && target.isAlive()) {
			Vec3 look = player.getLookAngle();
			AbilityHelpers.push(target, new Vec3(look.x * 2.0, 0.3 + Math.max(0, look.y) * 1.2, look.z * 2.0));
			THROWN.put(target.getUUID(), new Thrown(player.getUUID(), now + 40, ability(PowerArmourConfig.THROW_DAMAGE * mul(s.suit))));
			sound(player, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1f, 0.6f);
		}
	}

	// ================================================================== X: Jump Jets / Thruster Pack

	public static void jets(ServerPlayer player, PowerArmourState s, long now) {
		if (!ready(s, "jets", now) || !pay(player, s, PowerArmourConfig.JETS_COST)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
		Vec3 v = player.getDeltaMovement();
		AbilityHelpers.launchSelf(player, new Vec3(flat.x * 1.0 + v.x * 0.3, player.onGround() ? 0.95 : 0.72, flat.z * 1.0 + v.z * 0.3));
		ServerLevel level = level(player);
		level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.2, player.getZ(), 14, 0.2, 0.05, 0.2, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.3, 0.05, 0.3, 0.02);
		sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.6f);
		sound(player, SoundEvents.FIRECHARGE_USE, 0.5f, 1.3f);
		anim(s, ANIM_JETS, now);
		cool(s, "jets", PowerArmourConfig.JETS_COOLDOWN, now);
	}

	/** Thruster Pack: X held = thrust (the client flies the player; the server pays for it). */
	public static void thrust(ServerPlayer player, PowerArmourState s, boolean on) {
		if (on && !s.suit.has(ModuleType.THRUSTER_PACK)) {
			return;
		}
		if (s.thrusting != on) {
			s.thrusting = on;
			if (on) {
				sound(player, SoundEvents.FIRECHARGE_USE, 0.6f, 0.7f);
			}
			PowerArmour.sync(player);
		}
	}

	// ================================================================== Z: Shoulder Charge

	public static void shoulderCharge(ServerPlayer player, PowerArmourState s, long now) {
		if (!ready(s, "charge", now) || !pay(player, s, PowerArmourConfig.CHARGE_COST)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, 0, look.z);
		dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
		CHARGES.put(player.getUUID(), new ChargeRun(dir, new int[] { PowerArmourConfig.CHARGE_TICKS }, new HashSet<>()));
		anim(s, ANIM_CHARGE, now);
		sound(player, SoundEvents.RAVAGER_ROAR, 0.4f, 1.6f);
		sound(player, SoundEvents.FIRECHARGE_USE, 0.6f, 0.9f);
		cool(s, "charge", PowerArmourConfig.CHARGE_COOLDOWN, now);
	}

	private static void tickCharge(ServerPlayer player, PowerArmourState s, long now) {
		ChargeRun run = CHARGES.get(player.getUUID());
		if (run == null) {
			return;
		}
		if (run.left[0]-- <= 0) {
			CHARGES.remove(player.getUUID());
			return;
		}
		boolean breacher = s.suit.has(ModuleType.BREACHER_PLATING);
		ServerLevel level = level(player);
		if (breacher) {
			BlockPos feet = BlockPos.containing(player.position().add(run.dir.scale(0.9)));
			for (BlockPos pos : new BlockPos[] { feet, feet.above() }) {
				if (breakable(player, pos, PowerArmourConfig.BREACH_HARDNESS)) {
					level.destroyBlock(pos, true, player);
				}
			}
		} else if (player.horizontalCollision && run.left[0] < PowerArmourConfig.CHARGE_TICKS - 1) {
			CHARGES.remove(player.getUUID());
			sound(player, SoundEvents.IRON_GOLEM_DAMAGE, 0.8f, 0.6f);
			return;
		}
		Vec3 v = player.getDeltaMovement();
		AbilityHelpers.launchSelf(player, new Vec3(run.dir.x * PowerArmourConfig.CHARGE_SPEED, Math.min(v.y, 0.1), run.dir.z * PowerArmourConfig.CHARGE_SPEED));
		float damage = ability(PowerArmourConfig.CHARGE_DAMAGE * mul(s.suit));
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(0.7).expandTowards(run.dir.scale(0.8)),
				e -> e != player && HeroTargets.canHarm(player, e))) {
			if (run.hit.add(e.getId()) && AbilityHelpers.hurtBurst(player, e, damage)) {
				if (!isBoss(e)) {
					AbilityHelpers.push(e, new Vec3(run.dir.x * 1.5, 0.4, run.dir.z * 1.5));
				}
				level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
				sound(player, SoundEvents.IRON_GOLEM_ATTACK, 1f, 0.8f);
			}
		}
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(), 2, 0.2, 0.05, 0.2, 0.01);
	}

	// ================================================================== Shift+Z: Core Overload

	static float overloadCost(SuitData suit) {
		return Math.max(PowerArmourConfig.OVERLOAD_MIN, suit.capacity() * PowerArmourConfig.OVERLOAD_SHARE);
	}

	public static void overload(ServerPlayer player, PowerArmourState s, long now) {
		if (!ready(s, "overload", now) || s.overloadAt > 0) {
			return;
		}
		if (s.suit.energy() < overloadCost(s.suit)) {
			player.displayClientMessage(Component.translatable("message.projecthero.power_armour.overload_low").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		s.overloadAt = now + PowerArmourConfig.OVERLOAD_WINDUP;
		anim(s, ANIM_OVERLOAD, now);
		sound(player, SoundEvents.BEACON_POWER_SELECT, 1f, 0.6f);
		sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 0.6f, 1.6f);
	}

	private static void tickOverload(ServerPlayer player, PowerArmourState s, long now) {
		if (s.overloadAt <= 0) {
			return;
		}
		ServerLevel level = level(player);
		if (now < s.overloadAt) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.1, player.getZ(), 6, 0.5, 0.7, 0.5, 0.2);
			return;
		}
		s.overloadAt = 0;
		float cost = overloadCost(s.suit);
		if (!s.suit.spend(cost)) {
			s.suit.drain(s.suit.energy());
		}
		float damage = ultimate(PowerArmourConfig.OVERLOAD_DAMAGE * mul(s.suit));
		Vec3 c = player.position().add(0, 1.0, 0);
		double r = PowerArmourConfig.OVERLOAD_RADIUS;
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, c, r)) {
			double d = Math.sqrt(e.distanceToSqr(c));
			if (AbilityHelpers.hurtBurst(player, e, damage * (float) (1.0 - 0.5 * Math.min(1.0, d / r))) && !isBoss(e)) {
				Vec3 out = e.position().subtract(player.position()).normalize();
				AbilityHelpers.push(e, new Vec3(out.x * 2.0, 0.7, out.z * 2.0));
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 60, 0.4, 0.4, 0.4, 0.5);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 1.2f, 0.8f);
		sound(player, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.5f);
		anim(s, ANIM_BLAST, now);
		cool(s, "overload", PowerArmourConfig.OVERLOAD_COOLDOWN, now);
		PowerArmour.sync(player);
	}

	// ================================================================== V: Visor

	public static void visor(ServerPlayer player, PowerArmourState s) {
		s.visor = !s.visor;
		sound(player, s.visor ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 0.4f, 2f);
		if (s.visor) {
			PowerArmourModules.scanOres(player, s);
		}
	}

	// ================================================================== tick + teardown

	public static void tick(ServerPlayer player, PowerArmourState s, long now) {
		tickCharge(player, s, now);
		tickOverload(player, s, now);
		tickGrip(player, s, now);
		tickThrown(player, now);
		if (s.thrusting) {
			if (!s.suit.spend(PowerArmourConfig.THRUST_PER_TICK)) {
				s.thrusting = false;
				PowerArmour.sync(player);
			} else {
				player.resetFallDistance();
				if (now % 2 == 0) {
					level(player).sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.6, player.getZ(), 3, 0.25, 0.1, 0.25, 0.02);
				}
				if (now % 10 == 0) {
					sound(player, SoundEvents.FIRECHARGE_USE, 0.25f, 0.6f);
				}
			}
		}
		PowerArmourWeapons.tick(player, s, now);
	}

	private static void tickGrip(ServerPlayer player, PowerArmourState s, long now) {
		if (s.grabbed < 0) {
			return;
		}
		Entity e = player.level().getEntity(s.grabbed);
		if (!(e instanceof LivingEntity target) || !target.isAlive() || target.distanceToSqr(player) > 30 * 30) {
			s.grabbed = -1;
			PowerArmour.sync(player);
			return;
		}
		if (now - s.chargeStart >= PowerArmourConfig.GRIP_HOLD_TICKS) {
			throwHeld(player, s, now);
			PowerArmour.sync(player);
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 hold = player.getEyePosition().add(look.scale(1.9)).subtract(0, target.getBbHeight() * 0.5, 0);
		Vec3 to = hold.subtract(target.position());
		double step = Math.min(to.length(), 1.4);
		Vec3 next = to.lengthSqr() < 1e-6 ? target.position() : target.position().add(to.normalize().scale(step));
		target.teleportTo(next.x, next.y, next.z);
		target.setDeltaMovement(Vec3.ZERO);
		target.resetFallDistance();
		target.hurtMarked = true;
	}

	private static void tickThrown(ServerPlayer player, long now) {
		if (THROWN.isEmpty()) {
			return;
		}
		ServerLevel level = level(player);
		Iterator<Map.Entry<UUID, Thrown>> it = THROWN.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Thrown> en = it.next();
			if (!en.getValue().owner.equals(player.getUUID())) {
				continue;
			}
			Entity e = level.getEntity(en.getKey());
			if (!(e instanceof LivingEntity target) || !target.isAlive() || now > en.getValue().until) {
				it.remove();
				continue;
			}
			boolean landed = target.horizontalCollision || (target.onGround() && now > en.getValue().until - 36);
			if (landed) {
				it.remove();
				AbilityHelpers.hurtBurst(player, target, en.getValue().damage);
				level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.5, target.getZ(), 1, 0, 0, 0, 0);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.IRON_GOLEM_DAMAGE, SoundSource.PLAYERS, 0.8f, 0.7f);
				for (LivingEntity other : AbilityHelpers.enemiesAround(player, target.position(), 2.0)) {
					if (other != target) {
						AbilityHelpers.hurtBurst(player, other, en.getValue().damage * 0.5f);
					}
				}
			}
		}
	}

	/** Everything held or running stops (stepping out, ejected, unpowered sync). */
	public static void endAll(ServerPlayer player, PowerArmourState s) {
		CHARGES.remove(player.getUUID());
		RAIL.remove(player.getUUID());
		s.thrusting = false;
		s.charge = 0;
		s.overloadAt = 0;
		s.visor = false;
		s.shieldUntil = 0;
		s.droneUntil = 0;
		if (s.grabbed >= 0) {
			Entity e = player.level().getEntity(s.grabbed);
			if (e != null) {
				e.setDeltaMovement(Vec3.ZERO);
			}
			s.grabbed = -1;
		}
		MobEffectInstance nv = player.getEffect(MobEffects.NIGHT_VISION);
		if (nv != null && nv.isAmbient() && nv.getDuration() <= PowerArmourModules.NIGHT_VISION_TICKS) {
			player.removeEffect(MobEffects.NIGHT_VISION);
		}
	}

	/** Pulls the player's stored state's live AABB for a beam (the rail beam's damage line). */
	static AABB along(Vec3 a, Vec3 b, double r) {
		return new AABB(a, b).inflate(r);
	}
}
