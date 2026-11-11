package com.projecthero.mod.saiyan.kit;

import static com.projecthero.mod.saiyan.kit.SaiyanKit.*;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.saiyan.kit.SaiyanKit.Run;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Saiyan revamp, the close-range half of the kit:
 * <ul>
 *   <li>R / left click, {@code combo}: jab, cross, roundhouse, hook, then a spinning finisher kick that knocks back.</li>
 *   <li>Shift+R, {@code martial}: Rising Dragon Kick -- a flip kick that launches them, a chase, an axe kick that slams them down.</li>
 *   <li>Z, {@code strong}: Meteor Smash -- vanish, reappear above and behind the target, a double axe-handle into the ground.</li>
 *   <li>X, {@code dash}: Ki Dash -- a burst along the look with an afterimage trail.</li>
 *   <li>Shift+X, {@code rush}: Dragon Rush -- dash through everyone in the way with rapid strikes, then knock them all flying.</li>
 * </ul>
 * No move breaks a block: craters are particles only.
 */
final class SaiyanKitMelee {
	private static final int[] COMBO_ANIMS = { ANIM_JAB_R, ANIM_JAB_L, ANIM_KICK, ANIM_HOOK, ANIM_FINISH };

	private SaiyanKitMelee() {
	}

	// ---------------------------------------------------------------- R: Punch Combo

	static void combo(ServerPlayer p) {
		long now = p.level().getGameTime();
		if (cooldownRemaining(p, COMBO) > 0) {
			return;
		}
		long[] st = COMBO_STATE.computeIfAbsent(p.getUUID(), k -> new long[] { 0, -1000 });
		if (now - st[1] < SaiyanKitConfig.COMBO_STEP_TICKS && now >= st[1]) {
			return;
		}
		if (now - st[1] > SaiyanKitConfig.COMBO_RESET_TICKS || now < st[1]) {
			st[0] = 0;
		}
		int step = (int) Math.min(SaiyanKitConfig.COMBO_HITS - 1, st[0]);
		boolean last = step == SaiyanKitConfig.COMBO_HITS - 1;
		st[1] = now;
		LivingEntity t = nearestInFront(p, SaiyanKitConfig.COMBO_RANGE);
		anim(p, COMBO_ANIMS[step]);
		if (step == 0 || step == 1 || step == 3) {
			p.swing(step == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
		}
		if (t != null && t.distanceTo(p) > 2.4) {
			// a half step in so the next hit still reaches
			Vec3 in = t.position().subtract(p.position()).multiply(1, 0, 1);
			if (in.lengthSqr() > 1.0e-4) {
				AbilityHelpers.launchSelf(p, in.normalize().scale(0.3).add(0, p.getDeltaMovement().y, 0));
			}
		}
		float dmg = last ? SaiyanKitConfig.COMBO_FINISH_DAMAGE : SaiyanKitConfig.COMBO_HIT_DAMAGE;
		if (t != null && strike(p, t, p.position(), dmg, last ? SaiyanKitConfig.COMBO_FINISH_KNOCKBACK : 0.12,
				last ? SaiyanKitConfig.COMBO_FINISH_LIFT : 0.0)) {
			if (!last && !isBoss(t)) {
				// hits 1-4 keep them in reach (vanilla's hurt knockback would carry them out of the combo)
				t.setDeltaMovement(t.getDeltaMovement().multiply(0.15, 1.0, 0.15));
				t.hurtMarked = true;
			}
			impact(p, t, last ? 1.3f : 0.55f);
			sound(p, last ? SaiyanKitSounds.PUNCH_HEAVY : SaiyanKitSounds.PUNCH_LIGHT, last ? 0.6f : 0.45f, last ? 0.9f : 1.0f + step * 0.06f);
		} else {
			sound(p, SaiyanKitSounds.WHOOSH, 0.15f, 1.7f + step * 0.05f);
		}
		st[0] = last ? 0 : step + 1;
		if (last) {
			cooldown(p, COMBO, SaiyanKitConfig.COMBO_RECOVERY);
		}
	}

	// ---------------------------------------------------------------- Shift+R: Rising Dragon Kick

	static void martial(ServerPlayer p) {
		LivingEntity t = nearestInFront(p, SaiyanKitConfig.MARTIAL_RANGE);
		if (t == null) {
			cooldown(p, MARTIAL, SaiyanKitConfig.MARTIAL_WHIFF_COOLDOWN);
			anim(p, ANIM_WHIFF_KICK);
			sound(p, SaiyanKitSounds.WHOOSH, 0.2f, 1.5f);
			say(p, "message.projecthero.saiyan.kit.no_target");
			return;
		}
		if (!needKi(p, SaiyanKitConfig.MARTIAL_KI)) {
			return;
		}
		cooldown(p, MARTIAL, SaiyanKitConfig.MARTIAL_COOLDOWN);
		Run r = new Run(MARTIAL, p.level().getGameTime(), true);
		r.target = t;
		begin(p, r);
		anim(p, ANIM_FLIP_KICK);
		if (strike(p, t, p.position(), SaiyanKitConfig.MARTIAL_LAUNCH_DAMAGE, 0.0, 0.0)) {
			impact(p, t, 1.0f);
			sound(p, SaiyanKitSounds.PUNCH_HEAVY, 0.6f, 1.15f);
		}
		if (!isBoss(t)) {
			t.setDeltaMovement(0, SaiyanKitConfig.MARTIAL_LAUNCH_SPEED, 0);
			t.hurtMarked = true;
		}
		AbilityHelpers.launchSelf(p, new Vec3(0, 1.0, 0));
		fx(p, SaiyanKitFxPayload.TRAIL, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, kiColor(form(p)), SaiyanKitConfig.MARTIAL_AXE_AT + 4);
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	static void tickMartial(ServerPlayer p, Run r, long age) {
		LivingEntity t = r.target;
		p.resetFallDistance();
		if (t == null || !t.isAlive() || t.level() != p.level() || age > SaiyanKitConfig.MARTIAL_MAX) {
			end(p, r);
			fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
			return;
		}
		t.resetFallDistance(); // the kicks are the damage; the slam's landing isn't a fall
		boolean boss = isBoss(t);
		if (age >= 2 && age < SaiyanKitConfig.MARTIAL_AXE_AT) {
			if (!boss && age >= 6) {
				// near the top of the rise: they hang there for the axe kick
				t.setDeltaMovement(t.getDeltaMovement().x * 0.5, 0.04, t.getDeltaMovement().z * 0.5);
				t.hurtMarked = true;
			}
			// he flies up to just above and in front of them
			Vec3 flat = p.getLookAngle().multiply(1, 0, 1);
			flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
			Vec3 want = t.position().add(0, t.getBbHeight() + 0.3, 0).subtract(flat.scale(0.9 + t.getBbWidth() * 0.5));
			Vec3 v = want.subtract(p.position()).scale(0.45);
			if (v.length() > 1.6) {
				v = v.normalize().scale(1.6);
			}
			AbilityHelpers.launchSelf(p, v);
		}
		if (age == SaiyanKitConfig.MARTIAL_AXE_AT - 3) {
			anim(p, ANIM_AXE_KICK);
		}
		if (age == SaiyanKitConfig.MARTIAL_AXE_AT) {
			if (strike(p, t, p.position(), SaiyanKitConfig.MARTIAL_AXE_DAMAGE, 0.0, 0.0)) {
				impact(p, t, 1.4f);
				sound(p, SaiyanKitSounds.PUNCH_HEAVY, 0.65f, 0.8f);
			}
			if (!boss) {
				t.setDeltaMovement(0, SaiyanKitConfig.MARTIAL_SLAM_SPEED, 0);
				t.hurtMarked = true;
			}
			AbilityHelpers.launchSelf(p, new Vec3(0, -0.4, 0));
			r.stage = 1;
		}
		if (r.stage == 1 && age > SaiyanKitConfig.MARTIAL_AXE_AT + 1
				&& (t.onGround() || t.isInWater() || age >= SaiyanKitConfig.MARTIAL_MAX)) {
			Vec3 at = groundBelow(level(p), t.position());
			fxAt(level(p), at, SaiyanKitFxPayload.CRATER, p.getId(), at, Vec3.ZERO, 1.8f, kiColor(form(p)), 0);
			soundAt(level(p), at, SaiyanKitSounds.BOOM, 0.3f, 1.3f);
			end(p, r);
			fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
		}
	}

	// ---------------------------------------------------------------- Z: Meteor Smash

	/** The creature under the crosshair within {@code range} (through no block), else the nearest one close to the look. */
	static LivingEntity crosshairTarget(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(range));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.5),
				e -> e instanceof LivingEntity le && le != p && le.isAlive() && e.isPickable() && HeroTargets.canHarm(p, e), eye.distanceToSqr(end));
		if (ehr != null && ehr.getEntity() instanceof LivingEntity le) {
			return le;
		}
		for (LivingEntity e : inFront(p, range, 0.93)) {
			if (p.hasLineOfSight(e)) {
				return e;
			}
		}
		return null;
	}

	static void smash(ServerPlayer p) {
		LivingEntity t = crosshairTarget(p, SaiyanKitConfig.SMASH_RANGE);
		if (t == null) {
			cooldown(p, STRONG, SaiyanKitConfig.SMASH_WHIFF_COOLDOWN);
			say(p, "message.projecthero.saiyan.kit.no_target");
			return;
		}
		if (!needKi(p, SaiyanKitConfig.SMASH_KI)) {
			return;
		}
		cooldown(p, STRONG, SaiyanKitConfig.SMASH_COOLDOWN);
		Vec3 from = p.position();
		Vec3 h = t.position().subtract(from).multiply(1, 0, 1);
		if (h.lengthSqr() < 1.0e-4) {
			h = p.getLookAngle().multiply(1, 0, 1);
		}
		h = h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
		double lift = Math.max(1.0, t.getBbHeight() * 0.6);
		Vec3 spot = t.position().add(h.scale(t.getBbWidth() * 0.5 + 0.8)).add(0, lift, 0);
		if (!clear(p, spot)) {
			spot = t.position().add(0, t.getBbHeight() + 0.3, 0);
		}
		if (!clear(p, spot)) {
			spot = t.position().subtract(h.scale(t.getBbWidth() * 0.5 + 0.8)).add(0, lift, 0);
		}
		if (!clear(p, spot)) {
			spot = from;
		}
		Vec3 face = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(spot.add(0, p.getEyeHeight(), 0));
		float yaw = (float) Math.toDegrees(Math.atan2(-face.x, face.z));
		float pitch = (float) -Math.toDegrees(Math.atan2(face.y, Math.sqrt(face.x * face.x + face.z * face.z)));
		int rgb = kiColor(form(p));
		fx(p, SaiyanKitFxPayload.GHOST, p.getId(), from, spot, p.getYRot(), rgb, 0);
		sound(p, SaiyanKitSounds.VANISH, 0.35f, 1.0f);
		p.teleportTo(level(p), spot.x, spot.y, spot.z, yaw, pitch);
		p.setYHeadRot(yaw);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		Run r = new Run(STRONG, p.level().getGameTime(), true);
		r.target = t;
		r.origin = spot;
		begin(p, r);
		anim(p, ANIM_SMASH);
		soundAt(level(p), spot, SaiyanKitSounds.VANISH, 0.35f, 1.3f);
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	static void tickSmash(ServerPlayer p, Run r, long age) {
		p.resetFallDistance();
		LivingEntity t = r.target;
		if (age < SaiyanKitConfig.SMASH_HIT_AT) {
			// he hangs in the air, both fists raised
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
		}
		if (age == SaiyanKitConfig.SMASH_HIT_AT && t != null && t.isAlive() && t.level() == p.level() && t.distanceTo(p) < 6.0) {
			t.resetFallDistance();
			if (strike(p, t, p.position(), SaiyanKitConfig.SMASH_DAMAGE, 0.0, 0.0)) {
				impact(p, t, 1.8f);
				sound(p, SaiyanKitSounds.PUNCH_HEAVY, 0.7f, 0.65f);
			}
			if (!isBoss(t)) {
				t.setDeltaMovement(t.getDeltaMovement().x * 0.2, -1.6, t.getDeltaMovement().z * 0.2);
				t.hurtMarked = true;
			}
			Vec3 at = groundBelow(level(p), t.position());
			fxAt(level(p), at, SaiyanKitFxPayload.CRATER, p.getId(), at, Vec3.ZERO, (float) SaiyanKitConfig.SMASH_CRATER_RADIUS, kiColor(form(p)), 1);
			soundAt(level(p), at, SaiyanKitSounds.BOOM, 0.35f, 1.1f);
			AbilityHelpers.launchSelf(p, new Vec3(0, -0.3, 0));
			r.hits++;
		}
		if (age > SaiyanKitConfig.SMASH_HIT_AT && t != null) {
			t.resetFallDistance();
		}
		if (age >= SaiyanKitConfig.SMASH_TICKS) {
			end(p, r);
			fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
		}
	}

	// ---------------------------------------------------------------- X: Ki Dash

	/** The look, flattened when he is on the ground and aiming down (so a dash never drives into the floor). */
	static Vec3 moveDir(ServerPlayer p) {
		Vec3 d = p.getLookAngle();
		if (p.onGround() && d.y < 0.05) {
			d = new Vec3(d.x, 0, d.z);
		}
		if (d.lengthSqr() < 1.0e-4) {
			d = Vec3.directionFromRotation(0f, p.getYRot());
		}
		return d.normalize();
	}

	static void dash(ServerPlayer p) {
		if (!needKi(p, SaiyanKitConfig.DASH_KI)) {
			return;
		}
		cooldown(p, DASH, SaiyanKitConfig.DASH_COOLDOWN);
		Vec3 dir = moveDir(p);
		Run r = new Run(DASH, p.level().getGameTime(), true);
		r.dir = dir;
		r.origin = p.position();
		begin(p, r);
		int rgb = kiColor(form(p));
		anim(p, ANIM_DASH);
		fx(p, SaiyanKitFxPayload.GHOST, p.getId(), p.position(), p.position().add(dir.scale(SaiyanKitConfig.DASH_SPEED * SaiyanKitConfig.DASH_TICKS)),
				p.getYRot(), rgb, 0);
		fx(p, SaiyanKitFxPayload.TRAIL, p.getId(), Vec3.ZERO, Vec3.ZERO, 0f, rgb, SaiyanKitConfig.DASH_TICKS + 4);
		sound(p, SaiyanKitSounds.WHOOSH, 0.5f, 1.0f);
		AbilityHelpers.launchSelf(p, dir.scale(SaiyanKitConfig.DASH_SPEED));
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	static void tickDash(ServerPlayer p, Run r, long age) {
		p.resetFallDistance();
		if (age < SaiyanKitConfig.DASH_TICKS) {
			AbilityHelpers.launchSelf(p, r.dir.scale(SaiyanKitConfig.DASH_SPEED));
			return;
		}
		AbilityHelpers.launchSelf(p, r.dir.multiply(0.35, 0.2, 0.35));
		end(p, r);
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	// ---------------------------------------------------------------- Shift+X: Dragon Rush

	static void rush(ServerPlayer p) {
		if (!needKi(p, SaiyanKitConfig.RUSH_KI)) {
			return;
		}
		cooldown(p, RUSH, SaiyanKitConfig.RUSH_COOLDOWN);
		Vec3 dir = moveDir(p);
		double full = SaiyanKitConfig.RUSH_SPEED * SaiyanKitConfig.RUSH_TICKS;
		// the path stops short of a wall
		Vec3 mid = p.position().add(0, p.getBbHeight() * 0.5, 0);
		BlockHitResult bhr = p.level().clip(new ClipContext(mid, mid.add(dir.scale(full)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		double reach = bhr.getType() == HitResult.Type.MISS ? full : Math.max(0.0, mid.distanceTo(bhr.getLocation()) - 0.6);
		Run r = new Run(RUSH, p.level().getGameTime(), true);
		r.dir = dir;
		r.origin = p.position();
		r.reach = reach;
		begin(p, r);
		anim(p, ANIM_RUSH);
		fx(p, SaiyanKitFxPayload.TRAIL, p.getId(), Vec3.ZERO, Vec3.ZERO, 1f, kiColor(form(p)), SaiyanKitConfig.RUSH_TICKS + 5);
		sound(p, SaiyanKitSounds.WHOOSH, 0.6f, 1.0f);
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	static void tickRush(ServerPlayer p, Run r, long age) {
		p.resetFallDistance();
		if (age < SaiyanKitConfig.RUSH_TICKS) {
			double d0 = Math.min(r.reach, age * SaiyanKitConfig.RUSH_SPEED);
			double d1 = Math.min(r.reach, (age + 1) * SaiyanKitConfig.RUSH_SPEED);
			AbilityHelpers.launchSelf(p, d1 > d0 ? r.dir.scale(SaiyanKitConfig.RUSH_SPEED) : Vec3.ZERO);
			// everyone along this tick's stretch of the path (worked from where the rush began, so lag can't skip anyone)
			AABB box = p.getDimensions(p.getPose()).makeBoundingBox(r.origin.add(r.dir.scale(d0))).expandTowards(r.dir.scale(Math.max(0.01, d1 - d0)))
					.inflate(0.9);
			for (LivingEntity t : HeroTargets.harmable(p.level(), p, box)) {
				if (t == p || !t.isAlive() || !r.hitIds.add(t.getId())) {
					continue;
				}
				if (strike(p, t, p.position(), SaiyanKitConfig.RUSH_DAMAGE, 0.0, 0.0)) {
					r.struck.add(t);
					r.hits++;
					impact(p, t, 0.75f);
					soundAt(level(p), t.position(), SaiyanKitSounds.PUNCH_LIGHT, 0.45f, 1.15f + p.getRandom().nextFloat() * 0.2f);
					if (!isBoss(t)) {
						t.setDeltaMovement(t.getDeltaMovement().scale(0.2));
						t.hurtMarked = true;
					}
				}
			}
			if (age % 3 == 1) {
				sound(p, SaiyanKitSounds.FLURRY, 0.3f, 1.1f);
			}
			return;
		}
		// the finisher: everyone he passed is knocked flying along the rush
		anim(p, ANIM_RUSH_FINISH);
		boolean any = false;
		for (LivingEntity t : r.struck) {
			if (!t.isAlive() || isBoss(t)) {
				continue;
			}
			t.setDeltaMovement(r.dir.x * SaiyanKitConfig.RUSH_KNOCKBACK, SaiyanKitConfig.RUSH_LIFT + Math.max(0, r.dir.y), r.dir.z * SaiyanKitConfig.RUSH_KNOCKBACK);
			t.hurtMarked = true;
			impact(p, t, 1.3f);
			any = true;
		}
		if (any) {
			sound(p, SaiyanKitSounds.PUNCH_HEAVY, 0.6f, 0.8f);
		}
		AbilityHelpers.launchSelf(p, r.dir.multiply(0.25, 0.1, 0.25));
		end(p, r);
		fallSafeFor(p, SaiyanKitConfig.FALL_SAFE_TICKS);
	}

	// ---------------------------------------------------------------- helpers

	static boolean clear(ServerPlayer p, Vec3 feet) {
		return p.level().noCollision(p, p.getDimensions(p.getPose()).makeBoundingBox(feet));
	}

	/** The top of the first solid block at or under {@code pos} (8 blocks down at most), else {@code pos}. */
	static Vec3 groundBelow(ServerLevel level, Vec3 pos) {
		BlockPos b = BlockPos.containing(pos);
		for (int i = 0; i < 9; i++) {
			BlockPos q = b.below(i);
			if (!level.getBlockState(q).getCollisionShape(level, q).isEmpty()) {
				double top = q.getY() + level.getBlockState(q).getCollisionShape(level, q).max(net.minecraft.core.Direction.Axis.Y);
				return new Vec3(pos.x, top, pos.z);
			}
		}
		return pos;
	}

}
