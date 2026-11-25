package com.projecthero.mod.power;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.power.ThorKit.Kind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp, <b>Both: Twin Gods</b> -- Mjolnir in one hand, Stormbreaker in the other; lightning and the Bifrost's
 * rainbow intertwined. The highest damage, medium flight, but every move costs double Storm Energy and there is no Bifrost
 * travel. Storm Dash / Meteor Dive are {@link ThorMjolnirKit}'s; the swing is the 4-hit dual combo + Twin Thunder.
 */
public final class ThorTwinKit {
	/** Twin Throw's timeline: the wind-up, then the two weapons' flight to the meeting point (client draws the same). */
	public static final int THROW_WINDUP = 6, THROW_FLIGHT = 10;
	/** Thunderstruck Cleave: lightning flows hammer to axe until the slash, then the wave runs {@link #WAVE_LENGTH} blocks. */
	public static final int CLEAVE_SLASH = 10, CLEAVE_WAVE_TICKS = 8;
	public static final double WAVE_LENGTH = 13.0;
	/** Crossfire: the two beams meet on the target on this tick. */
	public static final int CROSSFIRE_HIT = 6;

	private ThorTwinKit() {
	}

	// ---------------------------------------------------------------- Shift+R Twin Throw

	public static void twinThrow(ServerPlayer p) {
		if (!WeaponCombo.isDualWielding(p) || !ThorKit.begin(p, ThorAbility.T_TWIN_THROW, ThorKit.TWIN_THROW_CD, ThorKit.TWIN_THROW_COST)) {
			return;
		}
		LivingEntity target = ThorKit.aimTarget(p, 30.0, 8.0);
		Vec3 meet = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : ThorKit.aimPoint(p, 30.0);
		ThorVisuals.anim(p, ThorFx.ANIM_TWIN_THROW, meet);
		ServerLevel level = p.serverLevel();
		ThorKit.sound(level, p.position(), SoundEvents.TRIDENT_RIPTIDE_2, 0.7f, 0.9f);
		int id = target == null ? -1 : target.getId();
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel lv = pl.serverLevel();
			if (t == THROW_WINDUP) {
				ThorKit.sound(lv, pl.position(), SoundEvents.WIND_CHARGE_BURST, 0.9f, 0.6f);
				ThorKit.sound(lv, pl.position(), SoundEvents.TRIDENT_THROW, 1.0f, 0.7f);
			}
			if (t == THROW_WINDUP + THROW_FLIGHT) {
				Entity e = id < 0 ? null : lv.getEntity(id);
				Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.5, 0) : meet;
				for (LivingEntity v : ThorTargets.inRadius(lv, pl, c, ThorKit.TWIN_THROW_RADIUS)) {
					if (ThorKit.hit(pl, v, ThorKit.TWIN_THROW_DAMAGE, Kind.LIGHTNING)) {
						ThorKit.knock(v, c, 1.2, 0.5);
					}
				}
				ThorTwinThunder.visualBolt(lv, pl, ThorTwinThunder.groundAt(lv, c));
				ThorKit.prism(lv, c, 70, 1.6, 1.2);
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 80, 1.2, 1.0, 1.2, 0.5);
				lv.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.4, 0.4, 0.4, 0);
				lv.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				ThorKit.sound(lv, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4f, 0.8f);
				ThorKit.sound(lv, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4f, 0.8f);
				ThorKit.sound(lv, c, SoundEvents.ANVIL_LAND, 0.4f, 0.5f);
				ThorTwinThunder.shake(lv, c, 1.1f, 12, 24.0);
				return false;
			}
			return true;
		});
	}

	// ---------------------------------------------------------------- G Thunderstruck Cleave

	public static void thunderstruckCleave(ServerPlayer p) {
		if (!WeaponCombo.isDualWielding(p)
				|| !ThorKit.begin(p, ThorAbility.T_THUNDERSTRUCK, ThorKit.THUNDERSTRUCK_CD, ThorKit.THUNDERSTRUCK_COST)) {
			return;
		}
		Vec3 facing = ThorKit.flatLook(p);
		Vec3 start = p.position();
		Vec3 end = start.add(facing.scale(WAVE_LENGTH));
		ThorVisuals.anim(p, ThorFx.ANIM_THUNDERSTRUCK, end);
		ServerLevel level = p.serverLevel();
		ThorKit.sound(level, start, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.0f);
		Set<Integer> struck = new HashSet<>();
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel lv = pl.serverLevel();
			if (t < CLEAVE_SLASH) {
				if (t % 3 == 0) {
					ThorKit.sound(lv, pl.position(), SoundEvents.TRIDENT_THUNDER, 0.35f, 1.4f + t * 0.05f);
				}
				return true;
			}
			if (t == CLEAVE_SLASH) {
				ThorKit.sound(lv, start, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.5f);
				ThorKit.sound(lv, start, SoundEvents.WIND_CHARGE_BURST, 1.0f, 0.7f);
				ThorKit.sound(lv, start, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.3f);
			}
			double k = Math.min(1.0, (t - CLEAVE_SLASH + 1) / (double) CLEAVE_WAVE_TICKS);
			Vec3 front = start.add(facing.scale(1.0 + (WAVE_LENGTH - 1.0) * k));
			Vec3 side = new Vec3(-facing.z, 0, facing.x);
			double halfWidth = 1.2 + 2.2 * k;
			for (LivingEntity e : ThorTargets.inRadius(lv, pl, front.add(0, 1, 0), halfWidth + 1.0)) {
				Vec3 rel = e.position().subtract(front);
				if (Math.abs(rel.dot(facing)) <= 1.6 && Math.abs(rel.dot(side)) <= halfWidth && struck.add(e.getId())
						&& ThorKit.hit(pl, e, ThorKit.THUNDERSTRUCK_DAMAGE, Kind.LIGHTNING)) {
					ThorKit.knock(e, start, 1.1, 0.35);
				}
			}
			for (int i = -4; i <= 4; i++) {
				Vec3 at = front.add(side.scale(halfWidth * i / 4.0)).add(0, 1.0 - Math.abs(i) * 0.08, 0);
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, 0.1, 0.2, 0.1, 0.05);
			}
			ThorKit.prism(lv, front.add(0, 1, 0), 6, halfWidth * 0.6, 0.3);
			return t < CLEAVE_SLASH + CLEAVE_WAVE_TICKS;
		});
	}

	// ---------------------------------------------------------------- Shift+G Storm Anchor

	public static void stormAnchor(ServerPlayer p) {
		ThunderField.cast(p, ThorAbility.T_STORM_ANCHOR, ThorKit.ANCHOR_CD, ThorKit.ANCHOR_COST, 2.0, true);
	}

	// ---------------------------------------------------------------- C Crossfire

	public static void crossfire(ServerPlayer p) {
		if (!WeaponCombo.isDualWielding(p) || !ThorKit.begin(p, ThorAbility.T_CROSSFIRE, ThorKit.CROSSFIRE_CD, ThorKit.CROSSFIRE_COST)) {
			return;
		}
		LivingEntity target = ThorKit.aimTarget(p, 24.0, 10.0);
		Vec3 at = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : ThorKit.aimPoint(p, 24.0);
		ThorVisuals.anim(p, ThorFx.ANIM_CROSSFIRE, at);
		ServerLevel level = p.serverLevel();
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7f, 1.6f);
		int id = target == null ? -1 : target.getId();
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel lv = pl.serverLevel();
			Entity e = id < 0 ? null : lv.getEntity(id);
			Vec3 c = e != null ? e.position().add(0, e.getBbHeight() * 0.5, 0) : at;
			if (t >= 2 && t <= CROSSFIRE_HIT + 4) {
				ThorPowers.sendLightningArc(pl, 90, null, e, c, 2, 5);
				ThorKit.prismLine(lv, pl.getEyePosition().add(0, -0.4, 0), c, 1.0);
			}
			if (t == CROSSFIRE_HIT) {
				if (e instanceof LivingEntity v) {
					ThorKit.hit(pl, v, ThorKit.CROSSFIRE_DAMAGE, Kind.LIGHTNING);
					ThorKit.hit(pl, v, ThorKit.CROSSFIRE_DAMAGE, Kind.PHYSICAL); // the Bifrost beam
					ThorKit.knock(v, pl.position(), 1.0, 0.3);
				}
				ThorKit.prism(lv, c, 40, 0.6, 0.6);
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.4, 0.4, 0.4, 0.4);
				lv.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
				ThorKit.sound(lv, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.0f, 1.2f);
				ThorKit.sound(lv, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.4f);
				ThorTwinThunder.shake(lv, c, 0.6f, 8, 14.0);
			}
			return t < CROSSFIRE_HIT + 4;
		});
	}

	// ---------------------------------------------------------------- Z Twin Thunder Strike on demand

	public static void twinThunderStrike(ServerPlayer p) {
		if (!WeaponCombo.isDualWielding(p) || ThorTwinThunder.isActive(p)
				|| !ThorKit.begin(p, ThorAbility.TWIN_THUNDER, ThorKit.TWIN_STRIKE_CD, ThorKit.TWIN_STRIKE_COST)) {
			return;
		}
		ThorTwinThunder.queueNow(p);
	}
}
