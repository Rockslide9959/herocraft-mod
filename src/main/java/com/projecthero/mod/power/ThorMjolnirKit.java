package com.projecthero.mod.power;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.power.ThorKit.Kind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Cosmic revamp, <b>Mjolnir: Storm Caller</b> -- classic white-blue lightning, bolts from the sky, shockwave rings. The
 * fastest flight; his lightning stuns briefly ({@link ThorKit#stun}); Storm Call lasts x1.5. G Lightning Strike, Z
 * Thunderclap, C Chain Lightning and Shift+C Lightning Beam are {@link ThorPowers}' (retuned); the rest are here. Storm Dash
 * and Meteor Dive are shared with Twin Gods.
 */
public final class ThorMjolnirKit {
	private ThorMjolnirKit() {
	}

	// ---------------------------------------------------------------- Shift+R Whirlwind Throw

	public static void whirlwindThrow(ServerPlayer p) {
		if (!ThorPowers.isHoldingMjolnir(p) || !ThorKit.begin(p, ThorAbility.M_WHIRLWIND, ThorKit.WHIRLWIND_CD, ThorKit.WHIRLWIND_COST)) {
			return;
		}
		ThorVisuals.anim(p, ThorFx.ANIM_WHIRLWIND);
		ThorSequences.start(p, (pl, t) -> {
			ServerLevel level = pl.serverLevel();
			if (t < ThorKit.WHIRLWIND_WINDUP) {
				// spun by the strap: a ring of sparks whirling round his hand, the hum rising
				double a = t * 1.3;
				Vec3 c = pl.position().add(0, 1.3, 0);
				for (int k = 0; k < 3; k++) {
					double ak = a + k * 2.1;
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(ak) * 0.9, c.y + 0.4, c.z + Math.sin(ak) * 0.9, 1,
							0.02, 0.02, 0.02, 0.01);
				}
				if (t % 3 == 0) {
					ThorKit.sound(level, c, SoundEvents.TRIDENT_RIPTIDE_1, 0.5f, 1.4f + t * 0.05f);
				}
				return true;
			}
			InteractionHand hand = pl.getMainHandItem().is(ModItems.MJOLNIR) ? InteractionHand.MAIN_HAND
					: pl.getOffhandItem().is(ModItems.MJOLNIR) ? InteractionHand.OFF_HAND : null;
			if (hand == null) {
				return false;
			}
			ItemStack thrown = pl.getItemInHand(hand).copy();
			pl.setItemInHand(hand, ItemStack.EMPTY);
			MjolnirEntity e = new MjolnirEntity(level, pl);
			e.setItem(thrown);
			e.throwFromPlayer(pl, pl.getLookAngle());
			e.setDeltaMovement(e.getDeltaMovement().scale(1.25));
			e.setSpecialThrow(ThorKit.WHIRLWIND_DAMAGE, ThorKit.WHIRLWIND_PIERCE);
			level.addFreshEntity(e);
			ThorPowers.grantThrowFlightGrace(pl);
			Vec3 c = pl.getEyePosition();
			level.sendParticles(ParticleTypes.GUST, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.4, 0.4, 0.4, 0.4);
			ThorKit.sound(level, c, SoundEvents.TRIDENT_RIPTIDE_3, 0.9f, 1.0f);
			ThorKit.sound(level, c, SoundEvents.WIND_CHARGE_BURST, 0.8f, 0.6f);
			ThorKit.sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.3f, 1.6f);
			return false;
		});
	}

	// ---------------------------------------------------------------- Shift+G Thunder Field

	public static void thunderField(ServerPlayer p) {
		ThunderField.cast(p, ThorAbility.THUNDER_FIELD, ThorKit.FIELD_CD, ThorKit.FIELD_COST, 1.0, false);
	}

	// ---------------------------------------------------------------- X Storm Dash (Mjolnir, Twin)

	public static void stormDash(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.M_STORM_DASH, ThorKit.DASH_CD, ThorKit.DASH_COST)) {
			return;
		}
		Vec3 start = p.position();
		ThorVisuals.anim(p, ThorFx.ANIM_STORM_DASH, start);
		boolean flying = ThorPowers.isFlying(p);
		Vec3 dir = flying ? p.getLookAngle() : ThorKit.flatLook(p);
		ServerLevel level = p.serverLevel();
		ThorKit.sound(level, start, SoundEvents.TRIDENT_RIPTIDE_2, 0.9f, 1.2f);
		ThorKit.sound(level, start, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 1.8f);
		Set<Integer> struck = new HashSet<>();
		double speed = ThorKit.DASH_DISTANCE / 4.0;
		ThorSequences.start(p, (pl, t) -> {
			if (t < 4) {
				pl.setDeltaMovement(dir.x * speed, flying ? dir.y * speed : Math.max(0.05, pl.getDeltaMovement().y), dir.z * speed);
				pl.hurtMarked = true;
			}
			// the trail: everything within 1.8 blocks of the line he burned along
			Vec3 now = pl.position().add(0, 1, 0);
			Vec3 from = start.add(0, 1, 0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, now).inflate(1.8),
					e -> e != pl && ThorTargets.canAffect(pl, e))) {
				if (distToSegment(e.position().add(0, e.getBbHeight() * 0.5, 0), from, now) <= 1.8 && struck.add(e.getId())
						&& ThorKit.hit(pl, e, ThorKit.DASH_DAMAGE, Kind.LIGHTNING)) {
					ThorKit.knock(e, now, 0.6, 0.3);
					level.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 1, e.getZ(), 16, 0.3, 0.4, 0.3, 0.2);
				}
			}
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, now.x, now.y, now.z, 8, 0.3, 0.5, 0.3, 0.1);
			if (t == 4) {
				ThorKit.sound(level, now, SoundEvents.TRIDENT_THUNDER, 0.5f, 1.6f);
			}
			return t < 7;
		});
	}

	static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
		Vec3 ab = b.subtract(a);
		double len2 = ab.lengthSqr();
		if (len2 < 1e-6) {
			return p.distanceTo(a);
		}
		double t = Math.max(0, Math.min(1, p.subtract(a).dot(ab) / len2));
		return p.distanceTo(a.add(ab.scale(t)));
	}

	// ---------------------------------------------------------------- Shift+X Meteor Dive (Mjolnir, Twin)

	public static void meteorDive(ServerPlayer p) {
		if (!ThorKit.begin(p, ThorAbility.M_METEOR_DIVE, ThorKit.DIVE_CD, ThorKit.DIVE_COST)) {
			return;
		}
		ServerLevel level = p.serverLevel();
		boolean grounded = p.onGround();
		Vec3 aim = ThorKit.aimPoint(p, 40.0);
		if (grounded) {
			// from the ground: a launch skyward, then the dive comes down on the spot ahead
			Vec3 ahead = p.position().add(ThorKit.flatLook(p).scale(12.0));
			aim = ThorTwinThunder.groundAt(level, ahead);
		}
		Vec3 target = aim;
		ThorVisuals.anim(p, ThorFx.ANIM_METEOR_DIVE, p.position());
		if (ThorPowers.isFlying(p)) {
			ThorPowers.landFlight(p);
		}
		int rise = grounded ? 9 : 0;
		if (grounded) {
			p.setDeltaMovement(0, 1.6, 0);
			p.hurtMarked = true;
			ThorKit.sound(level, p.position(), SoundEvents.WIND_CHARGE_BURST, 0.9f, 0.6f);
		}
		ThorKit.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.6f);
		ThorSequences.start(p, new ThorSequences.Seq() {
			@Override
			public boolean tick(ServerPlayer pl, int t) {
				ServerLevel lv = pl.serverLevel();
				pl.fallDistance = 0;
				if (t < rise) {
					lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, pl.getX(), pl.getY(), pl.getZ(), 4, 0.3, 0.3, 0.3, 0.05);
					return true;
				}
				if (t == rise) {
					ThorKit.sound(lv, pl.position(), SoundEvents.TRIDENT_RIPTIDE_3, 1.0f, 0.8f);
					ThorKit.sound(lv, pl.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5f, 1.4f);
				}
				Vec3 to = target.subtract(pl.position());
				boolean landed = (t > rise + 1 && pl.onGround()) || to.length() < 1.2 || t >= rise + 50;
				if (landed) {
					Vec3 at = pl.position();
					ThorVisuals.anim(pl, ThorFx.ANIM_LEAP_SLAM, at);
					ThorTwinThunder.visualBolt(lv, pl, at);
					ThorUnarmedKit.slam(pl, at, ThorKit.DIVE_DAMAGE, ThorKit.DIVE_RADIUS, 1.5f);
					ThorKit.sound(lv, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4f, 0.8f);
					return false;
				}
				Vec3 v = to.normalize().scale(Math.min(2.6, 0.9 + (t - rise) * 0.35));
				pl.setDeltaMovement(v);
				pl.hurtMarked = true;
				// a falling star: sparks and a smoking tail
				lv.sendParticles(ParticleTypes.ELECTRIC_SPARK, pl.getX(), pl.getY() + 1, pl.getZ(), 10, 0.35, 0.5, 0.35, 0.15);
				lv.sendParticles(ParticleTypes.CLOUD, pl.getX(), pl.getY() + 1, pl.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
				return true;
			}

			@Override
			public String tag() {
				return "dive";
			}
		});
	}
}
