package com.projecthero.mod.undead;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.undead.data.UndeadState;
import com.projecthero.mod.undead.entity.UndeadPartEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shift+V (user: "come up with a utility ability"): Rib Cage Guard. He tears his ribcage open and the ribs spread out in
 * front of him as a curved shield of bone, for as long as Shift+V is held (up to {@link UndeadKitConfig#RIB_TICKS}; a quick tap
 * keeps it up the whole time). From the front it stops projectiles dead and takes 75% off melee hits, and whoever hits it takes
 * {@link UndeadKitConfig#RIB_THORNS} back. {@link UndeadKitConfig#RIB_COOLDOWN} once it comes down.
 */
public final class UndeadRibGuard {
	public static final String ID = "rib_cage_guard";

	/** player -> {when it went up, when it comes down at the latest, 1 once the key was let go}. */
	private static final Map<UUID, long[]> GUARDS = new ConcurrentHashMap<>();
	/** attacker id -> when the ribs last bit back (one bite per swing). */
	private static final Map<Integer, Long> BITTEN = new ConcurrentHashMap<>();
	/** Set while the softened melee hit is being re-dealt (so the guard doesn't catch it again). */
	private static final ThreadLocal<Boolean> REDEALING = ThreadLocal.withInitial(() -> false);

	private UndeadRibGuard() {
	}

	static void clearSessionState() {
		GUARDS.clear();
		BITTEN.clear();
	}

	static void forget(UUID id) {
		GUARDS.remove(id);
	}

	public static boolean up(ServerPlayer p) {
		long[] g = GUARDS.get(p.getUUID());
		return g != null && p.level().getGameTime() < g[1];
	}

	/** Shift+V down: the ribs spread out (false, with a line, if he can't). */
	public static boolean raise(ServerPlayer p) {
		if (!Undead.canAct(p) || GUARDS.containsKey(p.getUUID())) {
			return false;
		}
		int cd = Undead.cooldownRemaining(p, ID);
		if (cd > 0) {
			Undead.say(p, "message.projecthero.undead.cooldown", ChatFormatting.GRAY, Component.translatable("projecthero.undead.ability." + ID),
					String.format(java.util.Locale.ROOT, "%.1f", cd / 20.0f));
			return false;
		}
		if (Undead.missing(p, UndeadState.BODY)) {
			Undead.say(p, "message.projecthero.undead.no_body", ChatFormatting.GRAY);
			return false;
		}
		long now = p.level().getGameTime();
		GUARDS.put(p.getUUID(), new long[] { now, now + UndeadKitConfig.RIB_TICKS, 0L });
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadKitConfig.FLAG_RIB_GUARD, true);
		Undead.save(p, n);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.6, 0).add(flat(p).scale(0.4));
		level.playSound(null, c.x, c.y, c.z, SoundEvents.BONE_BLOCK_BREAK, SoundSource.PLAYERS, 0.9f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.5f, 0.5f);
		UndeadSfx.bloodBurst(level, c, 0.6f);
		level.sendParticles(Undead.BLOOD, c.x, c.y, c.z, 8, 0.25, 0.25, 0.25, 0.0);
		return true;
	}

	/** Shift+V let go: a hold ends the guard; a tap leaves it up its full time. */
	public static void release(ServerPlayer p) {
		long[] g = GUARDS.get(p.getUUID());
		if (g == null || g[2] != 0L) {
			return;
		}
		g[2] = 1L;
		long now = p.level().getGameTime();
		if (now - g[0] >= UndeadKitConfig.RIB_TAP) {
			g[1] = Math.min(g[1], now);
		}
	}

	/** Every tick: projectiles from the front stop on the ribs; at its end the ribs fold back in. */
	static void tick(ServerPlayer p) {
		long[] g = GUARDS.get(p.getUUID());
		if (g == null) {
			if (Undead.state(p).flag(UndeadKitConfig.FLAG_RIB_GUARD)) {
				setFlag(p, false); // a stale flag (a reload while it was up)
			}
			return;
		}
		long now = p.level().getGameTime();
		UndeadState s = Undead.state(p);
		if (now >= g[1] || s.broken(UndeadState.BODY) || s.reformUntil > now || !p.isAlive()) {
			lower(p);
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.55, 0);
		for (Projectile pr : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0),
				pr -> pr.getOwner() != p && !(pr instanceof UndeadPartEntity) && pr.isAlive())) {
			Vec3 v = pr.getDeltaMovement();
			if (v.lengthSqr() < 0.01 || v.dot(c.subtract(pr.position())) <= 0 || !front(p, pr.position())) {
				continue;
			}
			// it stops dead on the bone and drops
			pr.setDeltaMovement(v.normalize().scale(-0.12).add(0, 0.08, 0));
			pr.hurtMarked = true;
			if (pr instanceof net.minecraft.world.entity.projectile.AbstractArrow arrow) {
				arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.5));
			}
			chip(p, pr.position());
		}
	}

	private static void lower(ServerPlayer p) {
		GUARDS.remove(p.getUUID());
		long now = p.level().getGameTime();
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadKitConfig.FLAG_RIB_GUARD, false);
		n.abilityReadyAt.put(ID, now + UndeadKitConfig.RIB_COOLDOWN);
		Undead.save(p, n);
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.6, 0);
		p.level().playSound(null, c.x, c.y, c.z, SoundEvents.SKELETON_STEP, SoundSource.PLAYERS, 0.7f, 0.6f);
	}

	private static void setFlag(ServerPlayer p, boolean on) {
		UndeadState n = Undead.state(p).copy();
		n.setFlag(UndeadKitConfig.FLAG_RIB_GUARD, on);
		Undead.save(p, n);
	}

	/** Gametests: drop it now (the cooldown starts). */
	public static void forTestsLower(ServerPlayer p) {
		if (GUARDS.containsKey(p.getUUID())) {
			lower(p);
		}
	}

	/** Whether {@code at} is in front of him (within the guard's arc). */
	static boolean front(ServerPlayer p, Vec3 at) {
		Vec3 to = new Vec3(at.x - p.getX(), 0, at.z - p.getZ());
		if (to.lengthSqr() < 1.0e-4) {
			return true;
		}
		return to.normalize().dot(flat(p)) >= UndeadKitConfig.RIB_FRONT_COS;
	}

	private static Vec3 flat(ServerPlayer p) {
		Vec3 look = p.getLookAngle();
		Vec3 f = new Vec3(look.x, 0, look.z);
		return f.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : f.normalize();
	}

	/** Bone chips and a dull knock where something hit the ribs. */
	private static void chip(ServerPlayer p, Vec3 at) {
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(UndeadBoneSword.BONE_CHIP, at.x, at.y, at.z, 5, 0.1, 0.1, 0.1, 0.0);
		level.sendParticles(UndeadBoneSword.BLOOD_FLECK, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 0.3f, 0.6f);
		UndeadKitNet.fx(level, UndeadKitNet.RIB_BLOCK, at, flat(p), 0);
	}

	/**
	 * From {@link UndeadBoneSword#blocks} ({@code UndeadDamage.allowDamage}): true if the ribs stop this hit. A projectile from the
	 * front is stopped outright; a melee hit from the front is stopped and dealt again at a quarter, and the attacker is bitten.
	 */
	static boolean blocks(ServerPlayer p, DamageSource source, float amount) {
		if (REDEALING.get() || !up(p)) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null || !front(p, from)) {
			return false;
		}
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		Vec3 hitAt = p.position().add(0, p.getBbHeight() * 0.55, 0).add(flat(p).scale(0.6));
		if (direct instanceof Projectile) {
			chip(p, hitAt);
			return true;
		}
		if (attacker instanceof LivingEntity le && direct == attacker && le != p) {
			chip(p, hitAt);
			long now = p.level().getGameTime();
			Long last = BITTEN.get(le.getId());
			if (last == null || now - last >= 10 || now < last) {
				BITTEN.put(le.getId(), now);
				UndeadAbilities.strike(p, le, p.position(), UndeadKitConfig.RIB_THORNS, 0.5, 0.05);
			}
			float rest = amount * UndeadKitConfig.RIB_MELEE_SHARE;
			if (rest > 0.01f) {
				REDEALING.set(true);
				try {
					p.hurt(source, rest);
				} finally {
					REDEALING.set(false);
				}
			}
			return true;
		}
		return false;
	}
}
