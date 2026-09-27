package com.projecthero.mod.behemoth;

import java.util.List;

import com.projecthero.mod.behemoth.entity.AbyssalBehemothEntity;
import com.projecthero.mod.behemoth.entity.BehemothFireballEntity;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The actual effect of each Abyssal Behemoth ability (spec sections 7-15): damage, knockback, particles,
 * sound. {@link AbyssalBehemothEntity} only ever decides <em>when</em> to call in here; nothing about
 * entity queries, damage falloff or projectile spawning is duplicated per ability, the same split
 * {@code AllMightShockwave} uses for All Might's Smashes.
 */
public final class BehemothCombat {
	private static final Vector3f EMBER = new Vector3f(1.0f, 0.35f, 0.08f);
	private static final Vector3f CORE_GLOW = new Vector3f(1.0f, 0.82f, 0.35f);

	private BehemothCombat() {
	}

	// ---------------------------------------------------------------- 1: Abyssal Fireball

	public static void fireFireball(AbyssalBehemothEntity self, LivingEntity target) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 lead = leadDirection(self, target, cfg.fireballSpeed);
		level.addFreshEntity(new BehemothFireballEntity(level, self, lead, cfg.fireballSpeed, cfg.fireballDamage, cfg.fireballExplosionRadius, false));
		level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 3.0f, 0.5f);
	}

	// ---------------------------------------------------------------- 2: Hellfire Barrage

	public static void fireBarrageShot(AbyssalBehemothEntity self, LivingEntity target, int index, int total) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 base = leadDirection(self, target, cfg.hellfireSpeed);
		// controlled variation (spec 8): each shot gets its own small yaw/pitch spread and a tiny speed jitter
		double spread = 0.16;
		double yaw = (level.random.nextDouble() - 0.5) * spread + Math.sin(index * 1.7) * spread * 0.6;
		double pitch = (level.random.nextDouble() - 0.5) * spread * 0.6;
		Vec3 dir = base.add(yaw, pitch, yaw * 0.4).normalize();
		double speed = cfg.hellfireSpeed * (0.9 + level.random.nextDouble() * 0.25);
		level.addFreshEntity(new BehemothFireballEntity(level, self, dir, speed, cfg.hellfireDamageEach, 1.6f, false));
		level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.6f, 0.8f + index * 0.03f);
		burst(level, ParticleTypes.SMALL_FLAME, self.position(), 6, 0.6, 0.05);
	}

	// ---------------------------------------------------------------- 3: Magma Rain

	/** Called once per impact point, after the warning delay -- the actual damage/explosion at that spot. */
	public static void magmaImpact(AbyssalBehemothEntity self, Vec3 pos) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.6f, 0.9f);
		burst(level, ParticleTypes.LAVA, pos, 10, 0.6, 0.1);
		burst(level, ParticleTypes.LARGE_SMOKE, pos, 14, 0.8, 0.05);
		burst(level, ParticleTypes.FLAME, pos, 20, 0.9, 0.08);
		for (LivingEntity victim : nearbyLiving(level, pos, 3.0, self)) {
			double d = Math.sqrt(victim.distanceToSqr(pos.x, pos.y, pos.z));
			float dmg = cfg.magmaRainDamage * (float) (1.0 - 0.3 * (d / 3.0));
			if (hurt(self, victim, dmg)) {
				knockAway(victim, pos, 1.4);
			}
		}
	}

	/** The warning marker shown for {@link BehemothConfig.Abilities#magmaRainWarningTicks} before impact. */
	public static void magmaWarning(AbyssalBehemothEntity self, Vec3 pos) {
		ServerLevel level = (ServerLevel) self.level();
		level.sendParticles(ParticleTypes.LAVA, pos.x, pos.y + 0.1, pos.z, 1, 0.15, 0.0, 0.15, 0.0);
		level.sendParticles(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, pos.x, pos.y + 0.2, pos.z, 1, 0.2, 0.0, 0.2, 0.01);
	}

	// ---------------------------------------------------------------- 4: Abyssal Beam

	/** One tick of the sweeping beam: damages a thin slab along the current direction. */
	public static void beamTick(AbyssalBehemothEntity self, Vec3 dir) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 origin = self.position().add(0, self.getBbHeight() * 0.5, 0);
		Vec3 flat = new Vec3(dir.x, 0, dir.z).lengthSqr() > 1.0e-6 ? new Vec3(dir.x, 0, dir.z).normalize() : new Vec3(0, 0, 1);
		double half = cfg.beamWidth * 0.5 + 1.0;
		double range = cfg.beamRange;
		Vec3 mid = origin.add(dir.scale(range * 0.5));
		AABB box = new AABB(mid.x - range * 0.5 - half, origin.y - half - range * 0.5, mid.z - range * 0.5 - half,
				mid.x + range * 0.5 + half, origin.y + half + range * 0.5, mid.z + range * 0.5 + half);
		for (LivingEntity victim : nearbyLiving(level, box, self)) {
			Vec3 rel = victim.position().subtract(origin);
			double along = rel.dot(dir);
			if (along < 0 || along > range) {
				continue;
			}
			Vec3 closest = origin.add(dir.scale(along));
			double lateral = victim.position().distanceTo(closest);
			if (lateral > cfg.beamWidth * 0.5 + victim.getBbWidth() * 0.5) {
				continue;
			}
			hurt(self, victim, cfg.beamDamagePerTick);
			knockAway(victim, origin, 0.5);
		}
		for (int i = 4; i < range; i += 4) {
			Vec3 seg = origin.add(dir.scale(i));
			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, seg.x, seg.y, seg.z, 2, 0.2, 0.2, 0.2, 0.01);
			level.sendParticles(new DustParticleOptions(CORE_GLOW, 2.0f), seg.x, seg.y, seg.z, 1, 0.15, 0.15, 0.15, 0.0);
		}
	}

	// ---------------------------------------------------------------- 5: Cinder Tether

	/** Pulls up to {@code cinderTetherCount} nearby players down slightly and damages/slows them. Returns how many connected. */
	public static int fireCinderTether(AbyssalBehemothEntity self) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		int hits = 0;
		for (LivingEntity victim : nearbyLiving(level, self.position(), cfg.cinderTetherRange, self)) {
			if (hits >= cfg.cinderTetherCount) {
				break;
			}
			if (!(victim instanceof Player) || victim.getY() >= self.getY()) {
				continue; // only pulls in targets below it -- this is the anti-flight-only mechanic
			}
			hurt(self, victim, cfg.cinderTetherDamage);
			victim.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
			Vec3 toSelf = self.position().subtract(victim.position()).normalize();
			victim.setDeltaMovement(victim.getDeltaMovement().add(toSelf.x * 0.15, 0.05, toSelf.z * 0.15));
			victim.hurtMarked = true;
			for (int i = 0; i < 10; i++) {
				Vec3 p = victim.position().lerp(self.position(), i / 10.0);
				level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y + 0.3, p.z, 1, 0.05, 0.05, 0.05, 0.0);
			}
			hits++;
		}
		level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 0.5f);
		return hits;
	}

	// ---------------------------------------------------------------- 6: Netherstorm

	public static void netherstormPulse(AbyssalBehemothEntity self) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		double ang = level.random.nextDouble() * Math.PI * 2;
		double dist = level.random.nextDouble() * cfg.netherstormRadius;
		Vec3 pos = self.position().add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
		// find a safe-ish vertical spot (near the target's own altitude band) rather than searching blocks every tick
		Vec3 strike = new Vec3(pos.x, self.getY() - 2.0, pos.z);
		level.playSound(null, strike.x, strike.y, strike.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.2f, 1.3f);
		burst(level, ParticleTypes.SOUL_FIRE_FLAME, strike, 16, 0.7, 0.1);
		burst(level, ParticleTypes.ASH, strike, 20, 1.4, 0.05);
		for (LivingEntity victim : nearbyLiving(level, strike, 3.5, self)) {
			if (hurt(self, victim, cfg.netherstormStrikeDamage)) {
				knockAway(victim, strike, 0.8);
			}
		}
	}

	// ---------------------------------------------------------------- 7: Hellwind

	public static void hellwind(AbyssalBehemothEntity self) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 center = self.position();
		for (LivingEntity victim : nearbyLiving(level, center, cfg.hellwindRadius, self)) {
			double d = Math.sqrt(victim.distanceToSqr(center.x, center.y, center.z));
			float scale = (float) (1.0 - 0.3 * (d / cfg.hellwindRadius));
			if (hurt(self, victim, cfg.hellwindDamage * scale)) {
				knockAway(victim, center, cfg.hellwindKnockback * scale);
				victim.setDeltaMovement(victim.getDeltaMovement().add(0, 0.5, 0));
			}
		}
		level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.0f, 0.4f);
		ring(level, ParticleTypes.GUST_EMITTER_LARGE, center, cfg.hellwindRadius * 0.5, 12);
		ring(level, ParticleTypes.CLOUD, center, cfg.hellwindRadius, 24);
		shake(level, center, 0.7f, 14);
	}

	// ---------------------------------------------------------------- 8: Sovereign Descent

	public static void sovereignDescentImpact(AbyssalBehemothEntity self) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 center = self.position();
		for (LivingEntity victim : nearbyLiving(level, center, cfg.sovereignDescentRadius, self)) {
			double d = Math.sqrt(victim.distanceToSqr(center.x, center.y, center.z));
			float scale = (float) (1.0 - 0.4 * (d / cfg.sovereignDescentRadius));
			if (hurt(self, victim, cfg.sovereignDescentDamage * scale)) {
				knockAway(victim, center, 2.6 * scale);
			}
		}
		level.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 3.5f, 0.5f);
		burst(level, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0.0, 0.0);
		ring(level, ParticleTypes.LAVA, center, cfg.sovereignDescentRadius * 0.6, 20);
		shake(level, center, 0.9f, 18);
	}

	// ---------------------------------------------------------------- 9: Skyfall

	public static void fireSkyfallVolley(AbyssalBehemothEntity self, LivingEntity target, int index) {
		ServerLevel level = (ServerLevel) self.level();
		var cfg = BehemothConfig.abilities();
		Vec3 dir = leadDirection(self, target, cfg.skyfallSpeed);
		Vec3 jitter = new Vec3((level.random.nextDouble() - 0.5) * 0.1, 0, (level.random.nextDouble() - 0.5) * 0.1);
		level.addFreshEntity(new BehemothFireballEntity(level, self, dir.add(jitter).normalize(), cfg.skyfallSpeed, cfg.skyfallDamageEach, 1.4f, true));
		level.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 2.0f, 1.2f + index * 0.05f);
	}

	// ---------------------------------------------------------------- shared helpers

	/** A straight-line aim with a little lead on the target's current velocity -- not perfect tracking. */
	private static Vec3 leadDirection(AbyssalBehemothEntity self, LivingEntity target, double speed) {
		Vec3 from = self.position().add(0, self.getBbHeight() * 0.5, 0);
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).add(target.getDeltaMovement().scale(6.0));
		return to.subtract(from).normalize();
	}

	private static List<LivingEntity> nearbyLiving(ServerLevel level, Vec3 center, double radius, AbyssalBehemothEntity self) {
		return nearbyLiving(level, new AABB(center.x - radius, center.y - radius, center.z - radius,
				center.x + radius, center.y + radius, center.z + radius), self);
	}

	private static List<LivingEntity> nearbyLiving(ServerLevel level, AABB box, AbyssalBehemothEntity self) {
		boolean pvp = level.getServer() != null && level.getServer().isPvpAllowed();
		return level.getEntitiesOfClass(LivingEntity.class, box, e -> e != self && e.isAlive()
				&& (!(e instanceof Player) || pvp));
	}

	/** Damage, boss-capped, through the shared boss-scaling rule the Titan family already uses. */
	private static boolean hurt(AbyssalBehemothEntity self, LivingEntity target, float rawDamage) {
		float dmg = rawDamage;
		if (TitanCombat.isBoss(target)) {
			dmg = Math.min(dmg, (float) (target.getMaxHealth() * 0.10));
		}
		return target.hurt(self.damageSources().mobAttack(self), Math.max(1.0f, dmg));
	}

	private static void knockAway(LivingEntity target, Vec3 origin, double strength) {
		Vec3 dir = target.position().subtract(origin);
		if (dir.lengthSqr() < 1.0e-4) {
			dir = new Vec3(1, 0, 0);
		}
		dir = dir.normalize();
		target.setDeltaMovement(target.getDeltaMovement().add(dir.x * strength, Math.max(0.15, strength * 0.3), dir.z * strength));
		target.hurtMarked = true;
	}

	private static void burst(ServerLevel level, ParticleOptions p, Vec3 c, int count, double spread, double speed) {
		level.sendParticles(p, c.x, c.y, c.z, count, spread, spread, spread, speed);
	}

	private static void ring(ServerLevel level, ParticleOptions p, Vec3 c, double radius, int points) {
		for (int i = 0; i < points; i++) {
			double a = (Math.PI * 2 * i) / points;
			level.sendParticles(p, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.02);
		}
	}

	private static void shake(ServerLevel level, Vec3 at, float intensity, int ticks) {
		double r2 = 48.0 * 48.0;
		for (ServerPlayer p : level.players()) {
			double d2 = p.distanceToSqr(at);
			if (d2 <= r2) {
				float falloff = (float) (1.0 - Math.sqrt(d2 / r2) * 0.8);
				ServerPlayNetworking.send(p, new TitanShakePayload(intensity * falloff, ticks));
			}
		}
	}
}
