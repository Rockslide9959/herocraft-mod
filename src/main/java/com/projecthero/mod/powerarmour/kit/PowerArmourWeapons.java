package com.projecthero.mod.powerarmour.kit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmour;
import com.projecthero.mod.powerarmour.PowerArmourConfig;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.PowerArmourState;
import com.projecthero.mod.powerarmour.entity.ArmourMissileEntity;
import com.projecthero.mod.powerarmour.entity.SentryTurretEntity;
import com.projecthero.mod.powerarmour.network.PowerArmourFxPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: Shift+R (the arm weapon) and C (the back module), plus their upkeep -- the gatling's spin and shots, the
 * flamer's cone, the rail beam (a beam: it reports to {@code BeamClash} every live tick), the shield bubble, the repair drone.
 * With the visor on, a Target Lock helmet aims the gatling, the Tesla Coil and the Rail Cannon at the nearest threat in a 30
 * degree cone.
 */
public final class PowerArmourWeapons {
	private PowerArmourWeapons() {
	}

	// ================================================================== aim

	/** Where the arm weapon points: at the locked target (visor on + Target Lock), else along the look. */
	static Vec3 aim(ServerPlayer player, PowerArmourState s) {
		LivingEntity lock = lockTarget(player, s);
		if (lock != null) {
			return lock.position().add(0, lock.getBbHeight() * 0.6, 0).subtract(player.getEyePosition()).normalize();
		}
		return player.getLookAngle();
	}

	public static LivingEntity lockTarget(ServerPlayer player, PowerArmourState s) {
		if (!s.visor || !s.suit.has(ModuleType.TARGET_LOCK)) {
			return null;
		}
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : AbilityHelpers.hostilesAround(player, eye, PowerArmourConfig.LOCK_RANGE)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(eye);
			double d = to.length();
			if (d < 0.5 || to.normalize().dot(look) < PowerArmourConfig.LOCK_CONE || !player.hasLineOfSight(e)) {
				continue;
			}
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The first harmable creature along a ray (stopping at blocks), or null. */
	static LivingEntity rayHit(ServerPlayer player, Vec3 from, Vec3 dir, double range) {
		ServerLevel level = player.serverLevel();
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			to = block.getLocation();
		}
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (Entity e : level.getEntities(player, new AABB(from, to).inflate(1.0), e -> e instanceof LivingEntity && e.isPickable())) {
			AABB box = e.getBoundingBox().inflate(0.3);
			var hit = box.clip(from, to);
			if (hit.isPresent() && HeroTargets.canHarm(player, e)) {
				double d = hit.get().distanceToSqr(from);
				if (d < bestD) {
					bestD = d;
					best = (LivingEntity) e;
				}
			}
		}
		return best;
	}

	static Vec3 rayEnd(ServerPlayer player, Vec3 from, Vec3 dir, double range) {
		Vec3 to = from.add(dir.scale(range));
		BlockHitResult block = player.serverLevel().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return block.getType() == HitResult.Type.MISS ? to : block.getLocation();
	}

	/** The hand the weapon is on: in front of the right shoulder. */
	static Vec3 muzzle(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		float yaw = player.getYRot() * Mth.DEG_TO_RAD;
		Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
		return player.getEyePosition().add(look.scale(0.8)).add(right.scale(0.38)).add(0, -0.3, 0);
	}

	// ================================================================== Shift+R

	public static void weaponPress(ServerPlayer player, PowerArmourState s, long now) {
		ModuleType w = s.suit.weapon();
		if (w == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.power_armour.no_weapon").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		switch (w) {
			case GATLING, FLAMER -> {
				s.charge = PowerArmourAbilities.CHARGE_FIRE;
				s.chargeStart = now;
				PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_WEAPON, now);
				if (w == ModuleType.GATLING) {
					PowerArmourAbilities.sound(player, SoundEvents.PISTON_EXTEND, 0.5f, 1.8f);
				}
			}
			case TESLA_COIL -> tesla(player, s, now);
			case RAIL_CANNON -> {
				if (PowerArmourAbilities.ready(s, "rail", now) && s.suit.energy() >= PowerArmourConfig.RAIL_COST) {
					s.charge = PowerArmourAbilities.CHARGE_RAIL;
					s.chargeStart = now;
					PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_WEAPON, now);
					PowerArmourAbilities.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.8f, 0.5f);
				}
			}
			case POWER_FIST -> piledriver(player, s, now);
			default -> {
			}
		}
	}

	public static void weaponRelease(ServerPlayer player, PowerArmourState s, long now) {
		if (s.charge == PowerArmourAbilities.CHARGE_RAIL) {
			s.charge = 0;
			if (now - s.chargeStart >= PowerArmourConfig.RAIL_CHARGE_TICKS && PowerArmourAbilities.pay(player, s, PowerArmourConfig.RAIL_COST)) {
				rail(player, s, now);
			}
		} else if (s.charge == PowerArmourAbilities.CHARGE_FIRE) {
			s.charge = 0;
		}
	}

	private static void gatlingShot(ServerPlayer player, PowerArmourState s, long now) {
		if (!s.suit.spend(PowerArmourConfig.GATLING_COST)) {
			s.charge = 0;
			PowerArmour.sync(player);
			return;
		}
		Vec3 dir = aim(player, s);
		var rnd = player.getRandom();
		double spread = 0.035;
		dir = dir.add((rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread, (rnd.nextDouble() - 0.5) * spread).normalize();
		Vec3 from = muzzle(player);
		LivingEntity hit = rayHit(player, player.getEyePosition(), dir, PowerArmourConfig.GATLING_RANGE);
		Vec3 end = hit != null ? hit.position().add(0, hit.getBbHeight() * 0.6, 0) : rayEnd(player, player.getEyePosition(), dir, PowerArmourConfig.GATLING_RANGE);
		if (hit != null) {
			AbilityHelpers.hurtBurst(player, hit, PowerArmourConfig.GATLING_DAMAGE * PowerArmourAbilities.mul(s.suit));
		}
		ServerLevel level = player.serverLevel();
		AbilityHelpers.line(level, from, end, ParticleTypes.SMALL_FLAME, 1.2);
		level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 1, 0.02, 0.02, 0.02, 0.01);
		if (hit == null) {
			level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 2, 0.05, 0.05, 0.05, 0.05);
		}
		PowerArmourAbilities.sound(player, SoundEvents.FIREWORK_ROCKET_BLAST, 0.35f, 1.9f);
	}

	private static void flamerTick(ServerPlayer player, PowerArmourState s, long now) {
		if (!s.suit.spend(PowerArmourConfig.FLAMER_COST)) {
			s.charge = 0;
			PowerArmour.sync(player);
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 from = muzzle(player);
		ServerLevel level = player.serverLevel();
		for (int i = 0; i < 8; i++) {
			double d = 0.6 + i * 0.7;
			var rnd = player.getRandom();
			Vec3 p = from.add(look.scale(d)).add((rnd.nextDouble() - 0.5) * d * 0.35, (rnd.nextDouble() - 0.5) * d * 0.35, (rnd.nextDouble() - 0.5) * d * 0.35);
			level.sendParticles(i < 3 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, from.add(look.scale(3.0)), PowerArmourConfig.FLAMER_RANGE * 0.6)) {
			Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(from);
			if (to.length() > PowerArmourConfig.FLAMER_RANGE || to.normalize().dot(look) < 0.88) {
				continue;
			}
			if (AbilityHelpers.hurt(player, e, AbilityHelpers.fire(player), PowerArmourConfig.FLAMER_DAMAGE * PowerArmourAbilities.mul(s.suit))) {
				e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
			}
		}
		if (now % 8 == 0) {
			PowerArmourAbilities.sound(player, SoundEvents.FIRECHARGE_USE, 0.5f, 0.8f);
		}
	}

	private static void tesla(ServerPlayer player, PowerArmourState s, long now) {
		if (!PowerArmourAbilities.ready(s, "tesla", now)) {
			return;
		}
		Vec3 dir = aim(player, s);
		LivingEntity first = rayHit(player, player.getEyePosition(), dir, 12.0);
		if (first == null) {
			LivingEntity lock = lockTarget(player, s);
			first = lock;
		}
		if (first == null) {
			double best = 0.8;
			for (LivingEntity e : AbilityHelpers.hostilesAround(player, player.getEyePosition(), 12.0)) {
				double dot = e.position().subtract(player.position()).normalize().dot(player.getLookAngle());
				if (dot > best && player.hasLineOfSight(e)) {
					best = dot;
					first = e;
				}
			}
		}
		if (first == null || !PowerArmourAbilities.pay(player, s, PowerArmourConfig.TESLA_COST)) {
			return;
		}
		float damage = PowerArmourAbilities.ability(PowerArmourConfig.TESLA_DAMAGE * PowerArmourAbilities.mul(s.suit));
		List<Vec3> points = new ArrayList<>();
		points.add(muzzle(player));
		List<LivingEntity> hit = new ArrayList<>();
		LivingEntity cur = first;
		while (cur != null && hit.size() < PowerArmourConfig.TESLA_TARGETS) {
			hit.add(cur);
			points.add(cur.position().add(0, cur.getBbHeight() * 0.6, 0));
			AbilityHelpers.hurtBurst(player, cur, damage);
			LivingEntity from = cur;
			cur = AbilityHelpers.enemiesAround(player, from.position(), 6.0).stream().filter(e -> !hit.contains(e))
					.min(Comparator.comparingDouble(e -> e.distanceToSqr(from))).orElse(null);
		}
		arc(player, points);
		PowerArmourAbilities.sound(player, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.6f);
		PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_TESLA, now);
		PowerArmourAbilities.cool(s, "tesla", PowerArmourConfig.TESLA_COOLDOWN, now);
	}

	private static void arc(ServerPlayer player, List<Vec3> points) {
		List<Double> data = new ArrayList<>();
		for (Vec3 p : points) {
			data.add(p.x);
			data.add(p.y);
			data.add(p.z);
		}
		PowerArmourFxPayload payload = new PowerArmourFxPayload(PowerArmourFxPayload.ARC, player.getId(), data);
		for (ServerPlayer viewer : PlayerLookup.tracking(player)) {
			ServerPlayNetworking.send(viewer, payload);
		}
		ServerPlayNetworking.send(player, payload);
	}

	private static void rail(ServerPlayer player, PowerArmourState s, long now) {
		Vec3 eye = player.getEyePosition();
		Vec3 dir = aim(player, s);
		Vec3 end = rayEnd(player, eye, dir, PowerArmourConfig.RAIL_RANGE);
		float damage = PowerArmourAbilities.ultimate(PowerArmourConfig.RAIL_DAMAGE * PowerArmourAbilities.mul(s.suit));
		ServerLevel level = player.serverLevel();
		for (Entity e : level.getEntities(player, new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity && e.isPickable())) {
			if (e.getBoundingBox().inflate(0.6).clip(eye, end).isPresent() && HeroTargets.canHarm(player, e)) {
				AbilityHelpers.hurtBurst(player, (LivingEntity) e, damage);
				level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() / 2, e.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		PowerArmourAbilities.RAIL.put(player.getUUID(), PowerArmourConfig.RAIL_BEAM_TICKS);
		PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_RAIL, now);
		PowerArmourAbilities.sound(player, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 1.8f);
		PowerArmourAbilities.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.8f);
		PowerArmourAbilities.cool(s, "rail", PowerArmourConfig.RAIL_COOLDOWN, now);
		AbilityHelpers.addImpulse(player, dir.scale(-0.35));
	}

	private static void piledriver(ServerPlayer player, PowerArmourState s, long now) {
		if (!PowerArmourAbilities.ready(s, "piledriver", now) || !PowerArmourAbilities.pay(player, s, PowerArmourConfig.PILEDRIVER_COST)) {
			return;
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
		float damage = PowerArmourAbilities.ability(PowerArmourConfig.PILEDRIVER_DAMAGE * PowerArmourAbilities.mul(s.suit));
		ServerLevel level = player.serverLevel();
		for (LivingEntity e : AbilityHelpers.enemiesAround(player, player.position().add(flat.scale(2.5)), 3.5)) {
			Vec3 to = e.position().subtract(player.position());
			if (to.multiply(1, 0, 1).normalize().dot(flat) < 0.5) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(player, e, damage) && !PowerArmourAbilities.isBoss(e)) {
				AbilityHelpers.push(e, new Vec3(flat.x * 0.6, 0.95, flat.z * 0.6));
			}
		}
		BlockPos below = BlockPos.containing(player.position().add(flat.scale(1.5))).below();
		BlockState st = level.getBlockState(below);
		for (int i = 1; i <= 5; i++) {
			Vec3 p = player.position().add(flat.scale(i));
			if (!st.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y + 0.1, p.z, 10, 0.3, 0.1, 0.3, 0.2);
			}
		}
		level.sendParticles(ParticleTypes.EXPLOSION, player.getX() + flat.x * 1.5, player.getY() + 0.2, player.getZ() + flat.z * 1.5, 1, 0, 0, 0, 0);
		PowerArmourAbilities.sound(player, SoundEvents.ANVIL_LAND, 0.8f, 0.6f);
		PowerArmourAbilities.sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.5f, 1.2f);
		PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_PILEDRIVER, now);
		PowerArmourAbilities.cool(s, "piledriver", PowerArmourConfig.PILEDRIVER_COOLDOWN, now);
	}

	// ================================================================== C

	public static void backModule(ServerPlayer player, PowerArmourState s, long now) {
		ModuleType b = s.suit.back();
		if (b == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.power_armour.no_back").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		switch (b) {
			case MISSILE_POD -> missiles(player, s, now);
			case SHIELD_PROJECTOR -> {
				if (s.shieldUntil > now) {
					s.shieldUntil = now;
					PowerArmourAbilities.cool(s, "shield", PowerArmourConfig.SHIELD_COOLDOWN, now);
					PowerArmourAbilities.sound(player, SoundEvents.BEACON_DEACTIVATE, 0.6f, 1.6f);
				} else if (PowerArmourAbilities.ready(s, "shield", now) && s.suit.energy() > 10f) {
					s.shieldUntil = now + PowerArmourConfig.SHIELD_TICKS;
					PowerArmourAbilities.sound(player, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.4f);
					PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_DEPLOY, now);
				}
			}
			case REPAIR_DRONE -> {
				if (s.droneUntil <= now && PowerArmourAbilities.ready(s, "drone", now) && PowerArmourAbilities.pay(player, s, PowerArmourConfig.DRONE_COST)) {
					s.droneUntil = now + PowerArmourConfig.DRONE_TICKS;
					PowerArmourAbilities.cool(s, "drone", PowerArmourConfig.DRONE_COOLDOWN, now);
					PowerArmourAbilities.sound(player, SoundEvents.BEE_LOOP, 0.8f, 1.8f);
					PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_DEPLOY, now);
				}
			}
			case SENTRY_TURRET -> turret(player, s, now);
			case THRUSTER_PACK -> {
				if (PowerArmourAbilities.ready(s, "afterburner", now) && PowerArmourAbilities.pay(player, s, PowerArmourConfig.AFTERBURNER_COST)) {
					Vec3 look = player.getLookAngle();
					AbilityHelpers.launchSelf(player, look.scale(2.0).add(0, 0.25, 0));
					player.serverLevel().sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.8, player.getZ(), 20, 0.3, 0.3, 0.3, 0.08);
					PowerArmourAbilities.sound(player, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1f, 0.7f);
					PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_JETS, now);
					PowerArmourAbilities.cool(s, "afterburner", PowerArmourConfig.AFTERBURNER_COOLDOWN, now);
				}
			}
			default -> {
			}
		}
	}

	private static void missiles(ServerPlayer player, PowerArmourState s, long now) {
		if (!PowerArmourAbilities.ready(s, "missiles", now) || !PowerArmourAbilities.pay(player, s, PowerArmourConfig.MISSILE_COST)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		List<LivingEntity> targets = new ArrayList<>(AbilityHelpers.hostilesAround(player, player.position(), 32.0));
		targets.removeIf(e -> !player.hasLineOfSight(e));
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		LivingEntity locked = lockTarget(player, s);
		Vec3 look = player.getLookAngle();
		Vec3 aimPoint = rayEnd(player, player.getEyePosition(), look, 32.0);
		float damage = PowerArmourAbilities.ability(PowerArmourConfig.MISSILE_DAMAGE * PowerArmourAbilities.mul(s.suit));
		for (int i = 0; i < PowerArmourConfig.MISSILE_COUNT; i++) {
			ArmourMissileEntity m = new ArmourMissileEntity(PowerArmourMod.MISSILE, level);
			m.setOwner(player);
			float yaw = player.getYRot() * Mth.DEG_TO_RAD;
			double side = (i % 2 == 0 ? -0.3 : 0.3);
			m.setPos(player.getX() + Mth.cos(yaw) * side - Mth.sin(yaw) * -0.35, player.getY() + 1.75 + (i / 2) * 0.2,
					player.getZ() + Mth.sin(yaw) * side + Mth.cos(yaw) * -0.35);
			LivingEntity target = locked != null ? locked : targets.isEmpty() ? null : targets.get(i % targets.size());
			m.setup(target, aimPoint, damage);
			m.setDeltaMovement(new Vec3(look.x * 0.3, 0.7 + i * 0.05, look.z * 0.3));
			level.addFreshEntity(m);
		}
		PowerArmourAbilities.sound(player, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1f, 0.8f);
		PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_MISSILES, now);
		PowerArmourAbilities.cool(s, "missiles", PowerArmourConfig.MISSILE_COOLDOWN, now);
	}

	private static void turret(ServerPlayer player, PowerArmourState s, long now) {
		if (!PowerArmourAbilities.ready(s, "turret", now) || !PowerArmourAbilities.pay(player, s, PowerArmourConfig.TURRET_COST)) {
			return;
		}
		ServerLevel level = player.serverLevel();
		for (SentryTurretEntity old : level.getEntitiesOfClass(SentryTurretEntity.class, player.getBoundingBox().inflate(64),
				t -> player.getUUID().equals(t.owner()))) {
			old.discard();
		}
		Vec3 look = player.getLookAngle();
		Vec3 at = player.position().add(new Vec3(look.x, 0, look.z).normalize().scale(1.6));
		SentryTurretEntity t = new SentryTurretEntity(PowerArmourMod.TURRET, level);
		t.moveTo(at.x, player.getY(), at.z, player.getYRot(), 0f);
		t.setup(player, PowerArmourConfig.TURRET_DAMAGE * PowerArmourAbilities.mul(s.suit), PowerArmourConfig.TURRET_TICKS);
		level.addFreshEntity(t);
		s.readyAt.put("turret_out", now + PowerArmourConfig.TURRET_TICKS);
		PowerArmourAbilities.sound(player, SoundEvents.IRON_TRAPDOOR_OPEN, 1f, 0.6f);
		PowerArmourAbilities.sound(player, SoundEvents.PISTON_EXTEND, 0.8f, 1.2f);
		PowerArmourAbilities.anim(s, PowerArmourAbilities.ANIM_DEPLOY, now);
		PowerArmourAbilities.cool(s, "turret", PowerArmourConfig.TURRET_COOLDOWN, now);
	}

	// ================================================================== tick

	static void tick(ServerPlayer player, PowerArmourState s, long now) {
		ModuleType w = s.suit.weapon();
		if (s.charge == PowerArmourAbilities.CHARGE_FIRE) {
			long held = now - s.chargeStart;
			if (w == ModuleType.GATLING) {
				if (held >= 6 && held % PowerArmourConfig.GATLING_INTERVAL == 0) {
					gatlingShot(player, s, now);
				}
			} else if (w == ModuleType.FLAMER) {
				if (held % 2 == 0) {
					flamerTick(player, s, now);
				}
			} else {
				s.charge = 0;
			}
		}
		Integer beam = PowerArmourAbilities.RAIL.get(player.getUUID());
		if (beam != null) {
			com.projecthero.mod.beamclash.BeamClash.report(player, player.getEyePosition(), player.getLookAngle(), PowerArmourConfig.RAIL_RANGE,
					1.2f, s.suit.glowColour());
			if (com.projecthero.mod.beamclash.BeamClash.locked(player)) {
				// v0.17.5: locked in a clash -- no drain, damage or timer
			} else if (beam <= 1) {
				PowerArmourAbilities.RAIL.remove(player.getUUID());
			} else {
				PowerArmourAbilities.RAIL.put(player.getUUID(), beam - 1);
			}
		}
		if (s.shieldUntil > now) {
			if (!s.suit.spend(PowerArmourConfig.SHIELD_PER_SECOND / 20f)) {
				s.shieldUntil = now;
			}
			ServerLevel level = player.serverLevel();
			for (Projectile p : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(2.4),
					p -> p.getOwner() != player && p.isAlive())) {
				Vec3 v = p.getDeltaMovement();
				p.setDeltaMovement(v.scale(-0.4));
				p.hurtMarked = true;
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY(), p.getZ(), 4, 0.1, 0.1, 0.1, 0.1);
			}
			if (s.shieldUntil <= now + 1) {
				PowerArmourAbilities.cool(s, "shield", PowerArmourConfig.SHIELD_COOLDOWN, now);
				PowerArmour.sync(player);
			}
		}
		if (s.droneUntil > now && now % 20 == 0) {
			s.suit.integrity = Math.min(s.suit.tier.integrity, s.suit.integrity + PowerArmourConfig.DRONE_REPAIR_PER_SECOND);
			if (player.getHealth() < player.getMaxHealth()) {
				player.heal(1f);
			}
			player.serverLevel().sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.2, player.getZ(), 3, 0.4, 0.5, 0.4, 0);
		}
	}
}
