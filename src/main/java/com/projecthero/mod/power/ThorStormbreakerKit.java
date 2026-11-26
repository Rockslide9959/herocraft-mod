package com.projecthero.mod.power;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorKit.Kind;
import com.projecthero.mod.stormbreaker.Bifrost;
import com.projecthero.mod.stormbreaker.StormbreakerEntity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp, <b>Stormbreaker: Executioner</b> -- heavy golden-white lightning (bolt streaks, axe-edge sparks, cleaving
 * arc trails, a lightning beam; user: no rainbow on Stormbreaker's moves, only on Bifrost Travel). Slow 26 swings and slow flight; ability hits ignore armour and do x1.25 to a foe under 30% health
 * ({@link ThorKit#hit}); Shift+X opens the Bifrost (moved off Shift + right-click).
 */
public final class ThorStormbreakerKit {
	private ThorStormbreakerKit() {
	}

	// ---------------------------------------------------------------- Shift+R Storm Hurl (id bifrost_hurl)

	public static void bifrostHurl(ServerPlayer p) {
		if (!ThorPowers.isHoldingStormbreaker(p) || !ThorKit.begin(p, ThorAbility.S_BIFROST_HURL, ThorKit.HURL_CD, ThorKit.HURL_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_BIFROST_HURL);
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.5f);
		int[] axeId = { -1 };
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel level = pl.serverLevel();
			if (t < 6) {
				ThorKit.storm(level, pl.position().add(0, 1.6, 0), 3, 0.4, 0.4);
				return true;
			}
			if (t == 6) {
				InteractionHand hand = pl.getMainHandItem().is(ModItems.STORMBREAKER) ? InteractionHand.MAIN_HAND
						: pl.getOffhandItem().is(ModItems.STORMBREAKER) ? InteractionHand.OFF_HAND : null;
				if (hand == null) {
					return false;
				}
				ItemStack thrown = pl.getItemInHand(hand).copy();
				pl.setItemInHand(hand, ItemStack.EMPTY);
				StormbreakerEntity e = new StormbreakerEntity(level, pl);
				e.setItem(thrown);
				e.setThrownFrom(hand); // v0.17.7: it comes back to this hand
				e.launch(pl, pl.getLookAngle());
				e.setSpecialThrow(ThorKit.HURL_DAMAGE, ThorKit.HURL_PIERCE, true);
				level.addFreshEntity(e);
				axeId[0] = e.getId();
				ThorPowers.grantThrowFlightGrace(pl);
				Vec3 c = pl.getEyePosition();
				ThorKit.storm(level, c, 30, 0.5, 0.5);
				ThorKit.sound(level, c, SoundEvents.TRIDENT_RIPTIDE_3, 0.9f, 0.8f);
				ThorKit.sound(level, c, SoundEvents.WIND_CHARGE_BURST, 0.7f, 0.6f);
				ThorKit.sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.6f, 2.0f);
				return true;
			}
			// the crackling lightning wake behind the flying axe
			Entity axe = level.getEntity(axeId[0]);
			if (axe instanceof StormbreakerEntity sb && !sb.isReturning() && !sb.isResting()) {
				Vec3 back = axe.position().subtract(axe.getDeltaMovement());
				ThorKit.stormLine(level, back, axe.position(), 0.35);
			}
			return t < 26;
		});
	}

	// ---------------------------------------------------------------- G Storm Cleave

	public static void stormCleave(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.S_STORM_CLEAVE, ThorKit.CLEAVE_CD, ThorKit.CLEAVE_COST)) {
			return;
		}
		LivingEntity target = ThorKit.aimTarget(p, 5.5, 30.0);
		Vec3 at = target != null ? target.position() : p.position().add(ThorKit.flatLook(p).scale(2.2));
		ThorVisuals.anim(p, ThorFx.ANIM_STORM_CLEAVE, at);
		int id = target == null ? -1 : target.getId();
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_POWER_SELECT, 0.5f, 1.2f);
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel level = pl.serverLevel();
			if (t == 3) {
				// lightning finds the raised blade
				Vec3 blade = pl.position().add(0, 2.6, 0);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, blade.x, blade.y, blade.z, 20, 0.2, 0.2, 0.2, 0.2);
				ThorKit.sound(level, blade, SoundEvents.TRIDENT_THUNDER, 0.6f, 1.4f);
			}
			if (t == 7) {
				Entity e = id < 0 ? null : level.getEntity(id);
				Vec3 hitAt = e != null ? e.position() : at;
				ThorTwinThunder.visualBolt(level, pl, hitAt);
				if (e instanceof LivingEntity victim && ThorKit.hit(pl, victim, ThorKit.CLEAVE_DAMAGE, Kind.LIGHTNING)) {
					ThorKit.knock(victim, pl.position(), 0.5, 0.0);
				}
				ThorKit.storm(level, hitAt.add(0, 1, 0), 24, 0.5, 0.6);
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hitAt.x, hitAt.y + 1, hitAt.z, 40, 0.4, 0.6, 0.4, 0.3);
				level.sendParticles(ParticleTypes.SWEEP_ATTACK, hitAt.x, hitAt.y + 1, hitAt.z, 1, 0, 0, 0, 0);
				ThorUnarmedKit.debris(level, hitAt, 2);
				ThorKit.sound(level, hitAt, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.9f);
				ThorKit.sound(level, hitAt, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8f, 1.1f);
				ThorTwinThunder.shake(level, hitAt, 0.7f, 8, 14.0);
				return false;
			}
			return true;
		});
	}

	// ---------------------------------------------------------------- Shift+G Thunder Step (id bifrost_step)

	public static void bifrostStep(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.S_BIFROST_STEP, ThorKit.STEP_CD, ThorKit.STEP_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		Vec3 origin = p.position();
		LivingEntity target = ThorKit.aimTarget(p, ThorKit.STEP_RANGE, 10.0);
		Vec3 dest;
		if (target != null) {
			Vec3 back = origin.subtract(target.position()).multiply(1, 0, 1);
			back = back.lengthSqr() < 1e-6 ? ThorKit.flatLook(p).scale(-1) : back.normalize();
			dest = target.position().add(back.scale(target.getBbWidth() * 0.5 + 1.0));
		} else {
			dest = ThorKit.aimPoint(p, ThorKit.STEP_RANGE).subtract(p.getLookAngle().scale(0.8)).subtract(0, p.getEyeHeight() * 0.5, 0);
		}
		Vec3 landed = blink(p, origin, dest);
		ThorVisuals.anim(p, ThorFx.ANIM_BIFROST_STEP, origin);
		blinkFlash(level, origin, landed, 0.3);
		ThorKit.sound(level, origin, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.6f);
		ThorKit.sound(level, landed, SoundEvents.TRIDENT_THUNDER, 0.8f, 1.3f);
		// the arrival cleave
		ThorSequences.start(p, (pl, t) -> {
			if (t == 2) {
				cleaveArc(pl, ThorKit.STEP_DAMAGE, 4.5, 0.26, 0.9);
				ThorKit.storm(pl.serverLevel(), pl.position().add(0, 1, 0), 40, 1.6, 0.6);
				return false;
			}
			return true;
		});
	}

	/**
	 * Moves {@code p} toward {@code dest} as far as there is room (stepping back along the line until his box is clear) and
	 * returns where he ended up.
	 */
	static Vec3 blink(ServerPlayer p, Vec3 from, Vec3 dest) {
		Vec3 d = dest.subtract(from);
		double len = d.length();
		Vec3 best = from;
		for (int i = 20; i >= 1; i--) {
			Vec3 at = from.add(d.scale(i / 20.0));
			if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
				best = at;
				break;
			}
		}
		if (best != from && len > 0.1) {
			p.teleportTo(best.x, best.y, best.z);
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			p.resetFallDistance();
		}
		return best;
	}

	/** A lightning-streak blink: a bolt-flash where he left and where he lands, a crackling streak between. */
	static void blinkFlash(ServerLevel level, Vec3 origin, Vec3 landed, double step) {
		ThorKit.stormLine(level, origin.add(0, 1, 0), landed.add(0, 1, 0), step);
		for (Vec3 end : new Vec3[] { origin, landed }) {
			ThorKit.storm(level, end.add(0, 1, 0), 18, 0.4, 0.9);
			level.sendParticles(ParticleTypes.FLASH, end.x, end.y + 1, end.z, 1, 0, 0, 0, 0);
		}
	}

	/** A cleave through everything in front within {@code r} (dot of facing >= {@code minDot}). */
	static void cleaveArc(ServerPlayer p, float dmg, double r, double minDot, double knock) {
		ServerLevel level = p.serverLevel();
		Vec3 origin = p.position();
		Vec3 facing = ThorKit.flatLook(p);
		for (LivingEntity e : ThorTargets.inRadius(level, p, origin.add(0, 1, 0), r)) {
			Vec3 to = e.position().subtract(origin).multiply(1, 0, 1);
			if (to.length() > 0.75 && to.normalize().dot(facing) < minDot) {
				continue;
			}
			if (ThorKit.hit(p, e, dmg, Kind.PHYSICAL)) {
				ThorKit.knock(e, origin, knock, 0.15);
			}
		}
		float yaw = p.getYRot() * Mth.DEG_TO_RAD;
		double half = Math.acos(Math.max(-1, Math.min(1, minDot)));
		for (int i = -6; i <= 6; i++) {
			double a = yaw + i * half / 6.0;
			Vec3 at = origin.add(-Math.sin(a) * r * 0.7, 1.0, Math.cos(a) * r * 0.7);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		}
		ThorKit.sound(level, origin, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
		ThorKit.sound(level, origin, SoundEvents.TRIDENT_THUNDER, 0.4f, 1.6f);
	}

	// ---------------------------------------------------------------- C Rending Arc

	public static void rendingArc(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.S_RENDING_ARC, ThorKit.ARC_CD, ThorKit.ARC_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_RENDING_ARC);
		ThorKit.sound(p.serverLevel(), p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.6f, 0.7f);
		ThorSequences.start(p, (pl, t) -> {
			if (t == 5) {
				cleaveArc(pl, ThorKit.ARC_DAMAGE, ThorKit.ARC_RADIUS, 0.0, 1.0); // 180 degrees
				ServerLevel level = pl.serverLevel();
				Vec3 o = pl.position();
				float yaw = pl.getYRot() * Mth.DEG_TO_RAD;
				for (int i = -12; i <= 12; i++) {
					double a = yaw + i * (Math.PI / 2) / 12.0;
					Vec3 a0 = o.add(-Math.sin(a) * 2.0, 1.1, Math.cos(a) * 2.0);
					Vec3 a1 = o.add(-Math.sin(a) * ThorKit.ARC_RADIUS, 1.1, Math.cos(a) * ThorKit.ARC_RADIUS);
					ThorKit.stormLine(level, a0, a1, 1.2);
				}
				ThorKit.sound(level, o, SoundEvents.WIND_CHARGE_BURST, 0.8f, 0.9f);
				ThorKit.sound(level, o, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.7f, 1.4f);
				return false;
			}
			return true;
		});
	}

	// ---------------------------------------------------------------- Z Executioner's Drop

	/** x1.5 against a foe under 30% health, never over 30 (before the storm buff / Sunder, which the hurt hook caps at 60). */
	public static float executionDamage(LivingEntity e) {
		return e.getHealth() < e.getMaxHealth() * ThorKit.EXECUTE_THRESHOLD
				? Math.min(ThorKit.EXEC_DAMAGE * ThorKit.EXEC_MUL, ThorKit.EXEC_MAX)
				: ThorKit.EXEC_DAMAGE;
	}

	public static void executionersDrop(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.S_EXECUTIONER, ThorKit.EXEC_CD, ThorKit.EXEC_COST)) {
			return;
		}
		ThorUnarmedKit.leapThen(p, ThorFx.ANIM_EXECUTIONER, 0.9, 1.15, "exec", ThorStormbreakerKit::executionLanding);
	}

	/** The landing of Executioner's Drop. Public for tests. */
	public static void executionLanding(ServerPlayer p, Vec3 at) {
		ServerLevel level = p.serverLevel();
		ThorVisuals.anim(p, ThorFx.ANIM_EXECUTIONER_SLAM, at);
		for (LivingEntity e : ThorTargets.inRadius(level, p, at.add(0, 0.5, 0), ThorKit.EXEC_RADIUS)) {
			if (ThorKit.hit(p, e, executionDamage(e), Kind.PHYSICAL, false, true)) {
				ThorKit.knock(e, at, 0.8, 0.5);
			}
		}
		ThorTwinThunder.visualBolt(level, p, at);
		ThorUnarmedKit.debris(level, at, ThorKit.EXEC_RADIUS);
		ThorKit.storm(level, at.add(0, 0.6, 0), 60, ThorKit.EXEC_RADIUS * 0.5, 0.4);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 2, 0.5, 0.1, 0.5, 0);
		ThorKit.sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.3f, 0.6f);
		ThorKit.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 0.9f);
		ThorKit.sound(level, at, SoundEvents.TRIDENT_THUNDER, 0.9f, 0.7f);
		ThorTwinThunder.shake(level, at, 1.3f, 14, 22.0);
	}

	// ---------------------------------------------------------------- X Lightning Dash (id bifrost_dash)

	public static void bifrostDash(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.S_BIFROST_DASH, ThorKit.BDASH_CD, ThorKit.BDASH_COST)) {
			return;
		}
		Vec3 origin = p.position();
		Vec3 dir = ThorPowers.isFlying(p) ? p.getLookAngle() : ThorKit.flatLook(p);
		Vec3 landed = blink(p, origin, origin.add(dir.scale(ThorKit.BDASH_DISTANCE)));
		ServerLevel level = p.serverLevel();
		ThorVisuals.anim(p, ThorFx.ANIM_BIFROST_DASH, origin);
		blinkFlash(level, origin, landed, 0.25);
		ThorKit.sound(level, origin, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.5f, 1.8f);
		ThorKit.sound(level, landed, SoundEvents.TRIDENT_THUNDER, 0.6f, 1.6f);
		ThorKit.sound(level, landed, SoundEvents.BREEZE_JUMP, 0.6f, 1.4f);
	}

	// ---------------------------------------------------------------- Shift+X Bifrost Travel (the only rainbow left)

	public static void bifrostTravel(ServerPlayer p) {
		if (Bifrost.canUse(p)) {
			Bifrost.sendScreen(p, true);
		} else {
			p.displayClientMessage(Component.translatable("message.projecthero.bifrost.fail.not_holding"), true);
		}
	}

	/**
	 * A storm column crashing down at {@code at} (Stormbreaker's Judgement, Ragnarok): a bolt in the middle and three round it
	 * plus a pillar of crackle. v0.17.7: it was the Bifrost's rainbow column, which now only Bifrost Travel uses.
	 */
	static void stormColumn(ServerLevel level, ServerPlayer cause, Vec3 at) {
		ThorTwinThunder.visualBolt(level, cause, at);
		for (int i = 0; i < 3; i++) {
			double a = Math.PI * 2 * i / 3 + level.random.nextDouble();
			ThorTwinThunder.visualBolt(level, cause, ThorTwinThunder.groundAt(level, at.add(Math.cos(a) * 1.8, 0, Math.sin(a) * 1.8)));
		}
		for (int y = 0; y < 12; y += 2) {
			ThorKit.storm(level, at.add(0, y, 0), 6, 0.5, 0.8);
		}
	}
}
