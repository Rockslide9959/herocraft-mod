package com.projecthero.mod.firearm;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.network.BulletHolePayload;
import com.projecthero.mod.network.FirearmHeadshotPayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Server-authoritative hitscan firing. No projectile entity exists for a bullet: the shot is a ray
 * resolved the instant the trigger request arrives, so there is nothing to sync, nothing to leak,
 * no double damage and no way for a client to claim its own hit (spec sections 21, 37). The client
 * only ever asks to fire ({@link com.projecthero.mod.network.FirearmFirePayload}); everything below --
 * ammo, fire rate, spread, damage, headshots -- is decided here.
 */
public final class FirearmShooting {
	private FirearmShooting() {
	}

	public enum Result { FIRED, EMPTY, NOT_READY }

	/** v0.15.18: the temporary armour cut of a {@link ShotSpec#armorIgnore} round (added and removed around one hit). */
	private static final ResourceLocation ARMOR_PIERCE = ProjectHeroMod.id("firearm_armor_pierce");

	/** An ordinary trigger pull -- unless the hooks have a pending special shot armed for it ({@link FirearmHooks#nextShot}). */
	public static Result fire(ServerPlayer player, ItemStack stack, FirearmData data) {
		return fire(player, stack, data, FirearmHooks.get().nextShot(player, data));
	}

	/** v0.15.18: a trigger pull with an optional {@link ShotSpec} override (null = a normal shot). */
	public static Result fire(ServerPlayer player, ItemStack stack, FirearmData data, ShotSpec spec) {
		long now = player.level().getGameTime();

		if (FirearmStack.isReloading(stack)) {
			// The shotgun can fire out of a partial reload, keeping the shells already loaded.
			if (data.shellReload && FirearmStack.magazine(stack, data) > 0) {
				FirearmReload.cancel(stack);
			} else {
				return Result.NOT_READY;
			}
		}
		long sinceFired = now - FirearmStack.lastFired(stack);
		int interval = Math.max(1, Math.round(data.fireIntervalTicks * FirearmHooks.get().fireIntervalFactor(player)));
		if ((spec == null || !spec.ignoreFireRate) && (sinceFired < interval || sinceFired < data.cycleTicks)) {
			return Result.NOT_READY;
		}
		int mag = FirearmStack.magazine(stack, data);
		if (mag <= 0) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					data.emptySound, SoundSource.PLAYERS, 0.6f, 1.0f);
			return Result.EMPTY;
		}
		int cost = spec == null ? 1 : Math.max(1, spec.ammoCost);
		if (mag < cost) {
			return Result.EMPTY;
		}

		FirearmStack.setMagazine(stack, mag - cost);
		FirearmStack.setLastFired(stack, now);

		boolean aiming = player.getAttachedOrElse(ModAttachments.FIREARM_AIMING, false);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = spec != null && spec.aimDir != null ? spec.aimDir.normalize() : player.getLookAngle();
		int pellets = spec != null && spec.pellets > 0 ? spec.pellets : Math.max(1, data.pellets);
		double range = spec != null && spec.range > 0 ? spec.range : data.range;
		// v0.9.4: muzzle flash / smoke / tracers spawn at the GUN in the shooter's hand -- down and to
		// the right of the crosshair -- not dead-centre in front of the eyes, so rapid fire does not
		// wash out the shooter's own view.
		Vec3 upRef = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 rightRef = look.cross(upRef).normalize();
		Vec3 muzzle = eye.add(look.scale(0.9)).add(rightRef.scale(0.30)).subtract(0.0, 0.35, 0.0);

		float recoil = FirearmManager.currentRecoil(player);
		float baseSpread = aiming ? data.spreadDegrees * data.adsSpreadFactor : data.spreadDegrees;
		float spread = (baseSpread + recoil) * FirearmHooks.get().spreadFactor(player, aiming);
		if (spec != null) {
			spread *= spec.spreadFactor;
		}

		DamageSource source = level.damageSources().playerAttack(player);
		boolean pvp = player.getServer() != null && player.getServer().isPvpAllowed();
		boolean anyHeadshot = false;
		LivingEntity feedbackTarget = null;

		for (int p = 0; p < pellets; p++) {
			Vec3 dir = spec != null && spec.fanDegrees > 0f
					? fanDirection(player, look, spec.fanDegrees, p, pellets)
					: spreadDirection(player, look, spread);
			Vec3 end = eye.add(dir.scale(range));

			BlockHitResult blockHit = level.clip(new ClipContext(eye, end,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
			Vec3 rayEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

			// v0.15.18: a piercing round (ShotSpec.pierce) hits everything along its line, nearest first
			List<EntityHitResult> hits;
			if (spec != null && spec.pierce) {
				hits = pierceHits(level, player, eye, rayEnd);
			} else {
				EntityHitResult entHit = ProjectileUtil.getEntityHitResult(level, player, eye, rayEnd,
						new AABB(eye, rayEnd).inflate(1.0), FirearmShooting::isShootable);
				hits = entHit != null && entHit.getEntity() instanceof LivingEntity ? List.of(entHit) : List.of();
			}

			for (EntityHitResult entHit : hits) {
				LivingEntity target = (LivingEntity) entHit.getEntity();
				Vec3 hp = entHit.getLocation();
				if (target instanceof Player && !pvp) {
					// PvP disabled -> bullet passes harmlessly, still show the impact.
					impact(level, hp, null);
					GunFx.tracer(level, player, data, muzzle, hp, 2);
					continue;
				}
				boolean headshot = (spec != null && spec.forceHeadshot) || HeadshotResolver.isHeadshot(target, eye, end);
				float dmg = headshot ? data.headDamage : data.bodyDamage;
				if (spec != null && (headshot ? spec.headDamage : spec.bodyDamage) >= 0f) {
					dmg = headshot ? spec.headDamage : spec.bodyDamage;
				}
				dmg *= rangeFalloff(data, eye.distanceTo(hp));
				dmg *= FirearmHooks.get().damageFactor(player, target, headshot);
				if (spec != null) {
					dmg *= spec.damageFactor;
				}

				// Bypass the vanilla hurt-cooldown ("red flash" i-frames): every bullet -- and every
				// shotgun pellet in the same trigger pull -- lands its full damage. Without this a
				// fast weapon or a shotgun only registers its first hit and the rest do ~0.
				int savedInvuln = target.invulnerableTime;
				target.invulnerableTime = 0;
				final float bullet = dmg;
				// v0.15.18: an armour-piercing round cuts the target's armour value for just this hit
				AttributeInstance armor = spec != null && spec.armorIgnore > 0f ? target.getAttribute(Attributes.ARMOR) : null;
				if (armor != null) {
					armor.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_PIERCE,
							-Math.min(1f, spec.armorIgnore), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
				}
				try {
					Gunfire.hit(() -> target.hurt(source, bullet)); // v0.14.31: gunfire marker (Iron Man armour is bulletproof)
				} finally {
					if (armor != null) {
						armor.removeModifier(ARMOR_PIERCE);
					}
				}
				// keep whatever the hit just set (20t) so the red flash still plays; only clear a
				// leftover window if the hit was fully absorbed
				if (target.invulnerableTime == 0) {
					target.invulnerableTime = savedInvuln;
				}
				FirearmHooks.get().onHit(player, target, headshot);
				if (spec != null && spec.onHit != null) {
					spec.onHit.accept(target);
				}
				double kb = data.knockback + (data.pellets > 1 ? closeKnockbackBonus(data, eye.distanceTo(hp)) : 0.0);
				if (kb > 0.0) {
					knockback(target, eye, kb);
				}
				impact(level, hp, target);
				if (spec == null || !spec.pierce) {
					GunFx.tracer(level, player, data, muzzle, hp, 2);
				}

				if (headshot) {
					anyHeadshot = true;
					feedbackTarget = target;
					FirearmHooks.get().onHeadshot(player, target);
					level.sendParticles(ParticleTypes.CRIT, hp.x, hp.y, hp.z, 6, 0.1, 0.1, 0.1, 0.2);
				}
				if (!target.isAlive() || target.getHealth() <= 0f) {
					FirearmHooks.get().onFirearmKill(player, target);
				}
			}
			if (!hits.isEmpty() && (spec == null || !spec.pierce)) {
				continue;
			}
			{
				boolean struck = blockHit.getType() != HitResult.Type.MISS;
				if (struck) {
					GunFx.impactBlock(level, rayEnd, blockHit.getBlockPos());
					bulletHole(level, blockHit);
				}
				GunFx.tracer(level, player, data, muzzle, rayEnd, struck ? 1 : 0);
			}
		}

		// v0.15.16: report (near + distant), muzzle smoke / sparks, casing, pump / bolt (GunFx); the flash and tracers are
		// drawn client side
		GunFx.shot(player, data, muzzle, look);

		// deliberately no player.swing() -- a firearm recoils, it does not swing like a melee weapon
		FirearmManager.addRecoil(player, data.recoilPerShotDegrees * FirearmHooks.get().recoilFactor(player), data);
		FirearmManager.applyCameraKick(player, data.verticalKickDegrees);
		FirearmHooks.get().onFired(player, data.id, aiming);

		if (anyHeadshot && feedbackTarget != null) {
			ServerPlayNetworking.send(player, new FirearmHeadshotPayload());
		}
		if (spec != null && spec.onFired != null) {
			spec.onFired.run();
		}
		return Result.FIRED;
	}

	/** v0.15.18: every shootable living thing whose (slightly padded) box the line from..to passes through, nearest first. */
	private static List<EntityHitResult> pierceHits(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		List<EntityHitResult> out = new ArrayList<>();
		for (Entity e : level.getEntities(player, new AABB(from, to).inflate(1.0), FirearmShooting::isShootable)) {
			e.getBoundingBox().inflate(0.3).clip(from, to).ifPresent(hit -> out.add(new EntityHitResult(e, hit)));
		}
		out.sort(Comparator.comparingDouble(h -> h.getLocation().distanceToSqr(from)));
		return out;
	}

	/** v0.15.18: pellet {@code index} of {@code count}, fanned evenly across a {@code totalDegrees} horizontal arc. */
	private static Vec3 fanDirection(ServerPlayer player, Vec3 look, float totalDegrees, int index, int count) {
		double t = count <= 1 ? 0.5 : index / (double) (count - 1);
		double yawOff = Math.toRadians((t - 0.5) * totalDegrees + (player.getRandom().nextDouble() - 0.5) * 3.0);
		double pitchOff = Math.toRadians((player.getRandom().nextDouble() - 0.5) * 8.0);
		Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 side = look.cross(up).normalize();
		Vec3 vup = side.cross(look).normalize();
		return look.scale(Math.cos(yawOff)).add(side.scale(Math.sin(yawOff))).add(vup.scale(Math.tan(pitchOff))).normalize();
	}

	private static boolean isShootable(Entity e) {
		return e.isAlive() && e.isPickable() && !(e instanceof ArmorStand) && e instanceof LivingEntity;
	}

	private static Vec3 spreadDirection(ServerPlayer player, Vec3 look, float spreadDegrees) {
		if (spreadDegrees <= 0.001f) {
			return look;
		}
		var rng = player.getRandom();
		double rad = Math.toRadians(spreadDegrees);
		// Gaussian-ish cone: two small perpendicular offsets.
		Vec3 up = Math.abs(look.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
		Vec3 side = look.cross(up).normalize();
		Vec3 vup = side.cross(look).normalize();
		double a = (rng.nextGaussian() * 0.5) * rad;
		double b = (rng.nextGaussian() * 0.5) * rad;
		return look.add(side.scale(a)).add(vup.scale(b)).normalize();
	}

	private static float rangeFalloff(FirearmData data, double dist) {
		if (dist <= data.effectiveRange || data.minRangeDamageFactor >= 1.0f) {
			return 1.0f;
		}
		double span = Math.max(0.001, data.range - data.effectiveRange);
		double t = Math.min(1.0, (dist - data.effectiveRange) / span);
		return (float) (1.0 - t * (1.0 - data.minRangeDamageFactor));
	}

	private static double closeKnockbackBonus(FirearmData data, double dist) {
		if (dist >= data.effectiveRange) {
			return 0.0;
		}
		return data.knockback * (1.0 - dist / data.effectiveRange);
	}

	private static void knockback(LivingEntity target, Vec3 origin, double strength) {
		double dx = target.getX() - origin.x;
		double dz = target.getZ() - origin.z;
		double d = Math.max(0.1, Math.sqrt(dx * dx + dz * dz));
		target.knockback(strength, -dx / d, -dz / d);
		target.hurtMarked = true;
	}

	private static void tracer(ServerLevel level, Vec3 a, Vec3 b) {
		double len = a.distanceTo(b);
		int steps = Math.max(1, (int) (len / 3.0));
		for (int i = 1; i <= steps; i++) {
			Vec3 pt = a.lerp(b, (double) i / (steps + 1));
			level.sendParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
	}

	/**
	 * Tell nearby clients to drop a fading bullet-hole decal where the shot hit a solid block. Cosmetic
	 * only -- the block is never changed. Skipped for a block with no collision face (already filtered by
	 * the caller's MISS check) or air.
	 */
	private static void bulletHole(ServerLevel level, BlockHitResult hit) {
		if (level.getBlockState(hit.getBlockPos()).isAir()) {
			return;
		}
		Vec3 loc = hit.getLocation();
		BulletHolePayload payload = new BulletHolePayload(loc.x, loc.y, loc.z, hit.getDirection().get3DDataValue());
		for (ServerPlayer viewer : PlayerLookup.around(level, loc, 64.0)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	private static void impact(ServerLevel level, Vec3 pos, LivingEntity hitEntity) {
		if (hitEntity != null) {
			GunFx.impactFlesh(level, pos, hitEntity);
			return;
		}
		level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 2, 0.05, 0.05, 0.05, 0.01);
	}
}
