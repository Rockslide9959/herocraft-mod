package com.projecthero.mod.sentry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.sentry.data.SentryState;
import com.projecthero.mod.sentry.network.SentryFxPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 the Void Form kit (Shift+V, or the takeover at full strength). Each move is a plain method the takeover's brain calls too
 * ({@link VoidTakeover}); {@link #use} adds the player's cooldowns and the Void each move feeds. The running Void Rifts and Nightfall
 * domes are kept by owner id, dimension key and position only (never a world or an entity), cleared on server stop.
 */
public final class VoidMoves {
	/** A black hole or a dome of darkness: who, where, until. */
	record Zone(UUID owner, ResourceKey<Level> dim, Vec3 at, long until, boolean rift) {
	}

	private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();

	private VoidMoves() {
	}

	static void clearSessionState() {
		ZONES.clear();
	}

	static void forget(UUID id) {
		ZONES.removeIf(z -> z.owner.equals(id));
	}

	/** How many zones are running (tests). */
	public static int zones() {
		return ZONES.size();
	}

	// ---------------------------------------------------------------- input

	static void use(ServerPlayer p, String id) {
		if (SentryAbilityManager.RETURN.equals(id)) {
			returnToSentry(p);
			return;
		}
		if (Sentry.cooldownRemaining(p, id) > 0) {
			return;
		}
		if (fire(p, id, null)) {
			Sentry.startCooldown(p, id, cooldownOf(p, id));
			Sentry.addVoid(p, voidOf(id));
		}
	}

	/** A Void move's cooldown: 25% shorter merged with the Void (v0.17.5 rework). */
	public static int cooldownOf(ServerPlayer p, String id) {
		int max = SentryAbilityManager.maxCooldown(id);
		return Sentry.merged(p) ? Math.max(1, Math.round(max * SentryConfig.MERGED_COOLDOWN)) : max;
	}

	/** Above 75 Void a Sentry key fires this instead (with its cooldown and Void). */
	static void misfire(ServerPlayer p, String id) {
		if (Sentry.cooldownRemaining(p, id) > 0) {
			return;
		}
		if (fire(p, id, null)) {
			Sentry.startCooldown(p, id, SentryAbilityManager.maxCooldown(id));
			Sentry.addVoid(p, voidOf(id));
		}
	}

	/** Fires a Void move ({@code target} given by the takeover's brain, else what he looks at). True if it went off. */
	public static boolean fire(ServerPlayer p, String id, LivingEntity target) {
		return switch (id) {
			case SentryAbilityManager.SHADOW_LASH -> lash(p) >= 0;
			case SentryAbilityManager.ERASE -> erase(p, target);
			case SentryAbilityManager.GRASP -> grasp(p) > 0;
			case SentryAbilityManager.VOID_RIFT -> rift(p, target == null ? null : target.position());
			case SentryAbilityManager.NIGHTFALL -> nightfall(p);
			case SentryAbilityManager.SHADOW_STEP -> shadowStep(p);
			default -> false;
		};
	}

	static float voidOf(String id) {
		return switch (id) {
			case SentryAbilityManager.SHADOW_LASH -> SentryConfig.LASH_VOID;
			case SentryAbilityManager.ERASE -> SentryConfig.ERASE_VOID;
			case SentryAbilityManager.GRASP -> SentryConfig.GRASP_VOID;
			case SentryAbilityManager.VOID_RIFT -> SentryConfig.RIFT_VOID;
			case SentryAbilityManager.NIGHTFALL -> SentryConfig.NIGHTFALL_VOID;
			case SentryAbilityManager.SHADOW_STEP -> SentryConfig.STEP_VOID;
			default -> 0f;
		};
	}

	// ---------------------------------------------------------------- R: Shadow Lash

	/** Tendrils whip everything in a 100-degree cone out to 6 blocks. Returns how many were hit (-1: never fails, 0 is a miss). */
	public static int lash(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		double r = SentryConfig.LASH_RANGE;
		double cos = Math.cos(Math.toRadians(50));
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> Sentry.areaVictim(p, e))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double d = to.length();
			if (d > r + e.getBbWidth() * 0.5 || (d > 1.0 && to.normalize().dot(look) < cos)) {
				continue;
			}
			if (Sentry.hit(p, e, SentryConfig.LASH_DAMAGE, true)) {
				n++;
			}
			AbilityHelpers.knockbackFrom(e, p.position(), 0.8);
			SentryFx.send(level, SentryFxPayload.TENDRIL, eye.add(0, -0.4, 0), 0.25f, 6, e.position().add(0, e.getBbHeight() * 0.5, 0), p.getId());
		}
		for (int i = -2; i <= 2; i++) {
			Vec3 sweep = look.yRot((float) Math.toRadians(i * 22)).scale(r * 0.85);
			Vec3 at = eye.add(sweep);
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y - 0.3, at.z, 3, 0.2, 0.2, 0.2, 0.01);
		}
		p.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
		Sentry.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 1.3f);
		return n;
	}

	// ---------------------------------------------------------------- Shift+R: Erase

	/** A creature wiped into a burnt shadow: 100, and a kill leaves the shadow. False with nothing in sight. */
	public static boolean erase(ServerPlayer p, LivingEntity target) {
		LivingEntity t = target != null ? target : SentryMoves.target(p, SentryConfig.ERASE_RANGE);
		if (t == null || !Sentry.areaVictim(p, t)) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 c = t.position().add(0, t.getBbHeight() * 0.5, 0);
		SentryFx.send(level, SentryFxPayload.ERASE, c, t.getBbHeight(), 20, p.getEyePosition(), p.getId());
		Sentry.hit(p, t, SentryConfig.ERASE_DAMAGE, true);
		if (!t.isAlive() || t.isDeadOrDying()) {
			Sentry.shadow(level, t);
		}
		level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 30, t.getBbWidth() * 0.5, t.getBbHeight() * 0.5, t.getBbWidth() * 0.5, 0.02);
		level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 20, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.5f);
		return true;
	}

	// ---------------------------------------------------------------- G: Grasp

	/** Tendrils drag up to five creatures within 14 to him. Returns how many. */
	public static int grasp(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		double r = SentryConfig.GRASP_RANGE;
		List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> Sentry.areaVictim(p, e) && e.distanceToSqr(p) <= r * r));
		list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
		int n = 0;
		Vec3 chest = p.position().add(0, 1.0, 0);
		for (LivingEntity e : list) {
			if (n >= SentryConfig.GRASP_MAX) {
				break;
			}
			Vec3 to = chest.subtract(e.position());
			double d = Math.max(1.0, to.length());
			if (SentryMoves.liftable(e)) {
				e.setDeltaMovement(to.normalize().scale(Math.min(2.2, 0.35 + d * 0.14)).add(0, 0.25, 0));
				e.hurtMarked = true;
			}
			Sentry.hit(p, e, SentryConfig.GRASP_DAMAGE, true);
			SentryFx.send(level, SentryFxPayload.TENDRIL, chest, 0.3f, 12, e.position().add(0, e.getBbHeight() * 0.5, 0), p.getId());
			n++;
		}
		if (n == 0) {
			Sentry.say(p, "message.projecthero.sentry.no_target", ChatFormatting.GRAY);
			return 0;
		}
		Sentry.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6f, 0.7f);
		return n;
	}

	// ---------------------------------------------------------------- Z: Void Rift

	/** A black hole at {@code at} (or where he looks, 24 out) that pulls and crushes for 4 s. */
	public static boolean rift(ServerPlayer p, Vec3 at) {
		Vec3 c = at;
		if (c == null) {
			BlockHitResult bh = AbilityHelpers.raycastBlock(p, SentryConfig.RIFT_RANGE);
			c = bh != null && bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().add(0, 1.5, 0)
					: p.getEyePosition().add(p.getLookAngle().scale(SentryConfig.RIFT_RANGE * 0.6));
		}
		long until = p.level().getGameTime() + SentryConfig.RIFT_TICKS;
		ZONES.add(new Zone(p.getUUID(), p.level().dimension(), c, until, true));
		SentryFx.send(p.serverLevel(), SentryFxPayload.RIFT, c, (float) SentryConfig.RIFT_RADIUS, SentryConfig.RIFT_TICKS, null, p.getId());
		p.level().playSound(null, c.x, c.y, c.z, SoundEvents.PORTAL_TRIGGER, net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 0.5f);
		return true;
	}

	// ---------------------------------------------------------------- Shift+Z: Nightfall

	public static boolean nightfall(ServerPlayer p) {
		long until = p.level().getGameTime() + SentryConfig.NIGHTFALL_TICKS;
		Vec3 c = p.position();
		ZONES.add(new Zone(p.getUUID(), p.level().dimension(), c, until, false));
		SentryState n = Sentry.state(p).copy();
		n.nightfallUntil = until;
		Sentry.save(p, n);
		SentryFx.send(p.serverLevel(), SentryFxPayload.NIGHTFALL, c, (float) SentryConfig.NIGHTFALL_RADIUS, SentryConfig.NIGHTFALL_TICKS, null, p.getId());
		Sentry.sound(p, SoundEvents.WARDEN_ROAR, 0.7f, 0.6f);
		return true;
	}

	// ---------------------------------------------------------------- X: Shadow Step

	/** How dark a spot is right now (block light, or sky light after the time of day, whichever is brighter). */
	public static int lightAt(ServerLevel level, BlockPos pos) {
		return level.getMaxLocalRawBrightness(pos);
	}

	/** To any dark spot he looks at, 40 out. False ("Too bright there") if the spot is lit. */
	public static boolean shadowStep(ServerPlayer p) {
		BlockHitResult bh = AbilityHelpers.raycastBlock(p, SentryConfig.STEP_RANGE);
		if (bh == null || bh.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		BlockPos dest = bh.getBlockPos().relative(bh.getDirection());
		if (bh.getDirection() != net.minecraft.core.Direction.UP && level.getBlockState(dest.below()).isAir()) {
			dest = dest.below(); // a wall: stand at its foot side
		}
		if (lightAt(level, dest) > SentryConfig.STEP_MAX_LIGHT) {
			Sentry.say(p, "message.projecthero.sentry.too_bright", ChatFormatting.GRAY);
			return false;
		}
		if (!level.getBlockState(dest).getCollisionShape(level, dest).isEmpty() || !level.getBlockState(dest.above()).getCollisionShape(level, dest.above()).isEmpty()) {
			return false;
		}
		Vec3 from = p.position();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y + 1.0, from.z, 20, 0.3, 0.8, 0.3, 0.02);
		p.teleportTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5);
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, dest.getX() + 0.5, dest.getY() + 1.0, dest.getZ() + 0.5, 20, 0.3, 0.8, 0.3, 0.02);
		level.playSound(null, dest, SoundEvents.ENDERMAN_TELEPORT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.5f);
		return true;
	}

	// ---------------------------------------------------------------- V: Return

	static void returnToSentry(ServerPlayer p) {
		SentryState s = Sentry.state(p);
		if (s.voidLevel >= SentryConfig.RETURN_MAX_VOID) {
			Sentry.say(p, "message.projecthero.sentry.return_failed", ChatFormatting.DARK_RED);
			return;
		}
		Sentry.setVoidForm(p, false);
		ServerLevel level = p.serverLevel();
		level.sendParticles(Sentry.dust(Sentry.GOLD, 1.6f), p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 1.0, 0.5, 0.0);
		Sentry.sound(p, SoundEvents.BEACON_ACTIVATE, 0.8f, 1.5f);
	}

	// ---------------------------------------------------------------- tick (zones and the form's look)

	public static void tick(ServerPlayer p) {
		if (Sentry.voidForm(p) && p.tickCount % 3 == 0) {
			p.serverLevel().sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 1.0, p.getZ(), 3, 0.3, 0.7, 0.3, 0.01);
		}
		SentryState s = Sentry.state(p);
		if (s.nightfallUntil != 0L && s.nightfallUntil <= p.level().getGameTime()) {
			SentryState n = s.copy();
			n.nightfallUntil = 0L;
			Sentry.save(p, n);
		}
	}

	/** Every zone, once per server tick per world (from {@code SentryMod}). */
	public static void tickLevel(ServerLevel level) {
		if (ZONES.isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		for (Iterator<Zone> it = ZONES.iterator(); it.hasNext();) {
			Zone z = it.next();
			if (!z.dim.equals(level.dimension())) {
				continue;
			}
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(z.owner);
			if (now >= z.until || owner == null || owner.level() != level || !owner.isAlive()) {
				ZONES.remove(z);
				continue;
			}
			if (z.rift) {
				tickRift(level, owner, z, now);
			} else {
				tickNightfall(level, owner, z, now);
			}
		}
	}

	private static void tickRift(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		double r = SentryConfig.RIFT_RADIUS;
		boolean crush = (z.until - now) % SentryConfig.RIFT_DAMAGE_INTERVAL == 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(z.at, z.at).inflate(r),
				e -> Sentry.areaVictim(owner, e) && e.distanceToSqr(z.at) <= r * r)) {
			Vec3 to = z.at.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
			double d = Math.max(0.5, to.length());
			if (SentryMoves.liftable(e)) {
				e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(to.normalize().scale(Math.min(0.6, 0.12 + 1.2 / d))));
				e.hurtMarked = true;
				e.resetFallDistance();
			}
			if (crush) {
				Sentry.hit(owner, e, SentryConfig.RIFT_DAMAGE, true);
			}
		}
		if (now % 2 == 0) {
			level.sendParticles(ParticleTypes.REVERSE_PORTAL, z.at.x, z.at.y, z.at.z, 8, r * 0.4, r * 0.4, r * 0.4, 0.0);
			level.sendParticles(ParticleTypes.SQUID_INK, z.at.x, z.at.y, z.at.z, 4, 0.4, 0.4, 0.4, 0.0);
		}
	}

	private static void tickNightfall(ServerLevel level, ServerPlayer owner, Zone z, long now) {
		if (now % 20 != 0) {
			return;
		}
		double r = SentryConfig.NIGHTFALL_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(z.at, z.at).inflate(r), e -> e.distanceToSqr(z.at) <= r * r)) {
			if (e == owner) {
				e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 40, 1, false, true, true));
			} else if (Sentry.areaVictim(owner, e)) {
				AbilityHelpers.applyControl(e, MobEffects.DARKNESS, 60, 0);
				AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, 60, 0);
				e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, true));
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, z.at.x, z.at.y + 1.0, z.at.z, 30, r * 0.6, 2.0, r * 0.6, 0.0);
	}
}
