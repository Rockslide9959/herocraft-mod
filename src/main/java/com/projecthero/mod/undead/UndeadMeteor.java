package com.projecthero.mod.undead;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.frostgiant.FrostFx;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadCloneEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.17.3 Shift+Z (user: "Make this need to held for 5 seconds to activate and make it his ultimate ability from the show or manga,
 * dont make it the victor mode"): Unluck Meteor. In chapter one Andy turns Fuuko's Unluck on the sky and calls down a meteor. Hold
 * Shift+Z for {@link UndeadKitConfig#METEOR_CHARGE} (5 s): red unluck energy winds up round him, the sky darkens (client) and a
 * red ring grows where he is looking; he is slowed. Let go early and it is gone, no cooldown. At 5 s a meteor streaks down on the
 * block he is looking at (up to {@link UndeadKitConfig#METEOR_REACH}): {@link UndeadKitConfig#METEOR_DAMAGE} at the centre, less
 * out to {@link UndeadKitConfig#METEOR_RADIUS}, a huge throw, fire, a flash, a shockwave and the ground shaking. No block is
 * changed. {@link UndeadKitConfig#METEOR_COOLDOWN} (90 s).
 */
public final class UndeadMeteor {
	public static final String ID = "unluck_meteor";
	private static final ResourceLocation SLOW_ID = PowerToggles.id("undead_meteor_charge");
	/** Unluck: the red of Fuuko's power. */
	public static final DustParticleOptions UNLUCK = new DustParticleOptions(new Vector3f(0.95f, 0.05f, 0.12f), 1.4f);
	public static final DustParticleOptions UNLUCK_DARK = new DustParticleOptions(new Vector3f(0.35f, 0.0f, 0.06f), 2.2f);

	/** A meteor on its way down: whose, where, when it lands, which way it came. */
	private record Falling(UUID owner, ServerLevel level, Vec3 target, long landsAt, Vec3 dir) {
	}

	/** player -> when Shift+Z went down. */
	private static final Map<UUID, Long> CHARGES = new ConcurrentHashMap<>();
	private static final List<Falling> FALLING = new ArrayList<>();

	private UndeadMeteor() {
	}

	static void clearSessionState() {
		CHARGES.clear();
		synchronized (FALLING) {
			FALLING.clear();
		}
	}

	static void forget(UUID id) {
		CHARGES.remove(id);
	}

	public static boolean charging(ServerPlayer p) {
		return CHARGES.containsKey(p.getUUID());
	}

	/** Gametests: how long it has been charging (moved back in time), then a tick. */
	public static void forTestsCharged(ServerPlayer p, int ticks) {
		if (CHARGES.containsKey(p.getUUID())) {
			CHARGES.put(p.getUUID(), p.level().getGameTime() - ticks);
		}
		tick(p);
	}

	/** Gametests: the meteors still falling. */
	public static int forTestsFalling() {
		synchronized (FALLING) {
			return FALLING.size();
		}
	}

	/** Shift+Z down: the charge starts (false, with a line, on cooldown). */
	public static boolean startCharge(ServerPlayer p) {
		if (!Undead.canAct(p) || charging(p)) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, ID);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + ID),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		long now = p.level().getGameTime();
		CHARGES.put(p.getUUID(), now);
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadKitConfig.FLAG_METEOR_CHARGE, true);
		Undead.save(p, n);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SLOW_ID, UndeadKitConfig.METEOR_CHARGE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ServerLevel level = (ServerLevel) p.level();
		level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.5f, 0.6f);
		return true;
	}

	/** Shift+Z let go (or the charge broken off) before it was called down: nothing happens, no cooldown. */
	public static void cancel(ServerPlayer p) {
		if (CHARGES.remove(p.getUUID()) == null) {
			return;
		}
		end(p);
		p.level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5f, 0.8f);
	}

	private static void end(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SLOW_ID);
		UndeadState s = Undead.peek(p);
		if (s != null && s.flag(UndeadKitConfig.FLAG_METEOR_CHARGE)) {
			UndeadState n = s.copy();
			n.setFlag(UndeadKitConfig.FLAG_METEOR_CHARGE, false);
			Undead.save(p, n);
		}
	}

	/** Every tick, for the caster: the charge's build-up, and at 5 s the call. */
	static void tick(ServerPlayer p) {
		Long start = CHARGES.get(p.getUUID());
		if (start == null) {
			if (Undead.state(p).flag(UndeadKitConfig.FLAG_METEOR_CHARGE)) {
				end(p); // a stale flag (a reload mid-charge)
			}
			return;
		}
		UndeadState s = Undead.state(p);
		long now = p.level().getGameTime();
		if (!p.isAlive() || s.reformUntil > now) {
			cancel(p);
			return;
		}
		long age = now - start;
		float k = Math.min(1f, age / (float) UndeadKitConfig.METEOR_CHARGE);
		ServerLevel level = (ServerLevel) p.level();
		// (the unluck winding up round him is the clients' own -- UndeadKitFx -- so it can stay out of his own first-person view)
		// where it will land: a red ring on the ground, growing to the blast's size
		if (age % 4 == 0) {
			Vec3 t = target(p);
			double rr = 1.0 + (UndeadKitConfig.METEOR_RADIUS - 1.0) * k;
			int pts = 10 + (int) (rr * 3);
			for (int i = 0; i < pts; i++) {
				double a = i * Math.PI * 2 / pts + age * 0.05;
				level.sendParticles(UNLUCK, t.x + Math.cos(a) * rr, t.y + 0.15, t.z + Math.sin(a) * rr, 1, 0, 0, 0, 0);
			}
		}
		if (age % 20 == 0 && age > 0) {
			level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6f + 0.3f * k, 0.8f + 0.5f * k);
		}
		if (age == UndeadKitConfig.METEOR_CHARGE - 30) {
			level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.5f, 0.6f);
		}
		if (age >= UndeadKitConfig.METEOR_CHARGE) {
			CHARGES.remove(p.getUUID());
			end(p);
			call(p);
		}
	}

	/** What he is looking at, on the ground, within reach. */
	public static Vec3 target(ServerPlayer p) {
		Vec3 eye = p.getEyePosition();
		Vec3 far = eye.add(p.getLookAngle().scale(UndeadKitConfig.METEOR_REACH));
		BlockHitResult bhr = p.level().clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
		Vec3 end = bhr.getType() == HitResult.Type.MISS ? far : bhr.getLocation();
		EntityHitResult ehr = ProjectileUtil.getEntityHitResult(p, eye, end, new AABB(eye, end).inflate(1.0),
				e -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable() && !(e instanceof UndeadCloneEntity), eye.distanceToSqr(end));
		if (ehr != null) {
			return ehr.getEntity().position();
		}
		if (bhr.getType() == HitResult.Type.MISS) {
			// at the sky: straight down from the far end of the look
			BlockHitResult down = p.level().clip(new ClipContext(end, end.subtract(0, 96, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
			if (down.getType() != HitResult.Type.MISS) {
				return down.getLocation();
			}
		}
		return end;
	}

	/** 5 s: the meteor is called down on the target. */
	static void call(ServerPlayer p) {
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.abilityReadyAt.put(ID, now + UndeadKitConfig.METEOR_COOLDOWN);
		n.animId = UndeadKitConfig.ANIM_METEOR_CALL;
		n.animStart = now;
		Undead.save(p, n);
		Vec3 t = target(p);
		Vec3 flat = new Vec3(t.x - p.getX(), 0, t.z - p.getZ());
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z) : flat;
		flat = flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
		Vec3 from = t.add(flat.scale(-UndeadKitConfig.METEOR_BACK)).add(0, UndeadKitConfig.METEOR_HEIGHT, 0);
		Vec3 dir = t.subtract(from).normalize();
		ServerLevel level = (ServerLevel) p.level();
		synchronized (FALLING) {
			FALLING.add(new Falling(p.getUUID(), level, t, now + UndeadKitConfig.METEOR_FALL, dir));
		}
		UndeadKitNet.fx(level, UndeadKitNet.METEOR_FALL, from, t, UndeadKitConfig.METEOR_FALL);
		level.playSound(null, p.getX(), p.getY() + 1, p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.5f, 0.5f);
		level.playSound(null, t.x, t.y + 8, t.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.sendParticles(UNLUCK, p.getX(), p.getY() + 1.2, p.getZ(), 30, 0.6, 0.8, 0.6, 0.05);
	}

	/** Every server tick: the meteors that land now. */
	static void serverTick(MinecraftServer server) {
		List<Falling> due = null;
		synchronized (FALLING) {
			for (Iterator<Falling> it = FALLING.iterator(); it.hasNext();) {
				Falling f = it.next();
				if (f.level().getServer() != server) {
					it.remove();
					continue;
				}
				long now = f.level().getGameTime();
				long left = f.landsAt() - now;
				if (left == 8) {
					f.level().playSound(null, f.target().x, f.target().y + 6, f.target().z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST_FAR, SoundSource.PLAYERS, 2.0f, 0.5f);
				}
				if (left <= 0) {
					it.remove();
					if (due == null) {
						due = new ArrayList<>();
					}
					due.add(f);
				}
			}
		}
		if (due != null) {
			for (Falling f : due) {
				impact(server, f);
			}
		}
	}

	/** It lands: the blast. */
	private static void impact(MinecraftServer server, Falling f) {
		ServerLevel level = f.level();
		Vec3 t = f.target();
		ServerPlayer owner = server.getPlayerList().getPlayer(f.owner());
		double r = UndeadKitConfig.METEOR_RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(t, t).inflate(r + 2.0), e -> e.isAlive() && !(e instanceof UndeadCloneEntity)
				&& (owner == null || (e != owner && HeroTargets.canHarm(owner, e))))) {
			Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
			double d = c.distanceTo(t);
			if (d > r + e.getBbWidth() * 0.5) {
				continue;
			}
			float share = (float) Math.max(0.0, Math.min(1.0, d / r));
			float dmg = UndeadKitConfig.METEOR_DAMAGE * (1f - (1f - UndeadKitConfig.METEOR_EDGE_SHARE) * share);
			double knock = UndeadKitConfig.METEOR_KNOCK * (1.0 - 0.6 * share);
			double lift = UndeadKitConfig.METEOR_LIFT * (1.0 - 0.5 * share);
			if (owner != null) {
				UndeadAbilities.strike(owner, e, t.subtract(0, 1.0, 0), dmg, knock, lift);
			} else {
				e.hurt(level.damageSources().explosion(null, null), dmg);
			}
			e.igniteForSeconds(UndeadKitConfig.METEOR_FIRE_SECONDS);
		}
		// the sight of it: fire, smoke, rock and earth thrown up (the blocks themselves stay as they are)
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, t.x, t.y + 0.5, t.z, 2, 1.5, 0.3, 1.5, 0);
		level.sendParticles(ParticleTypes.FLAME, t.x, t.y + 0.6, t.z, 90, 3.5, 0.6, 3.5, 0.15);
		level.sendParticles(ParticleTypes.LAVA, t.x, t.y + 0.5, t.z, 40, 2.5, 0.4, 2.5, 0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, t.x, t.y + 1.0, t.z, 70, 4.0, 1.2, 4.0, 0.06);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, t.x, t.y + 0.5, t.z, 24, 3.0, 0.4, 3.0, 0.04);
		level.sendParticles(UNLUCK_DARK, t.x, t.y + 1.0, t.z, 40, 4.0, 1.0, 4.0, 0);
		BlockState ground = level.getBlockState(BlockPos.containing(t.x, t.y - 0.5, t.z));
		if (!ground.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), t.x, t.y + 0.4, t.z, 160, 4.0, 0.5, 4.0, 0.4);
		}
		level.playSound(null, t.x, t.y, t.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 3.0f, 0.55f);
		level.playSound(null, t.x, t.y, t.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2f, 0.6f);
		FrostFx.shake(level, t, 1.3f, 32, UndeadKitConfig.METEOR_SHAKE_RADIUS);
		UndeadKitNet.fx(level, UndeadKitNet.METEOR_IMPACT, t, f.dir(), 0);
	}
}
