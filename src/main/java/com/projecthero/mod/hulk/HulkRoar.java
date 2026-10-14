package com.projecthero.mod.hulk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hulk.data.HulkState;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.4 (user): "N (transformed): roar; low-level mobs flee; with an animation". N as the Hulk plays
 * {@link HulkState#ANIM_ROAR} with a roar and a shockwave of dust; every low-level mob within {@link HulkConfig#ROAR_RADIUS}
 * (max health at or under {@link HulkConfig#ROAR_FLEE_MAX_HEALTH}, never a boss, never a player) drops its target and runs
 * from him for {@link HulkConfig#ROAR_FLEE_TICKS}. {@link HulkConfig#ROAR_COOLDOWN_TICKS} cooldown (the {@code "roar"} entry
 * of the ability cooldowns). The scared mobs are a static world-object cache, cleared by {@code ServerStateReset} through
 * {@link Hulk#clearSessionState}.
 */
public final class HulkRoar {
	public static final String ROAR = "roar";
	/** Ticks into the roar clip when the sound and the shockwave go off. */
	public static final int ROAR_IMPACT_TICKS = 6;
	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.6f);

	private record Fear(List<PathfinderMob> mobs, long until) {
	}

	private static final Map<UUID, Fear> FEAR = new ConcurrentHashMap<>();

	private HulkRoar() {
	}

	public static void clearSessionState() {
		FEAR.clear();
	}

	public static void clear(UUID id) {
		FEAR.remove(id);
	}

	/** N pressed as the Hulk (the packet). Server-validated; safe to spam. */
	public static void press(ServerPlayer player) {
		HulkState s = Hulk.state(player);
		long now = player.level().getGameTime();
		if (!s.hasPower || !s.hulk || !player.isAlive() || Hulk.changing(s, now) || s.rampaging(now)) {
			return;
		}
		int cd = HulkAbilities.cooldownRemaining(player, ROAR);
		if (cd > 0) {
			Hulk.say(player, "message.projecthero.hulk.roar_cooldown", ChatFormatting.GRAY, (cd + 19) / 20);
			return;
		}
		roar(player);
	}

	/** The roar itself (no checks -- tests and the rampage can call it). */
	public static void roar(ServerPlayer player) {
		HulkAbilities.cooldown(player, ROAR, HulkConfig.ROAR_COOLDOWN_TICKS);
		HulkAbilities.anim(player, HulkState.ANIM_ROAR);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		level.playSound(null, at.x, at.y, at.z, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 3.0f, 0.55f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.PLAYERS, 2.5f, 0.4f);
		HulkAbilities.schedule(player, ROAR_IMPACT_TICKS, () -> impact(player));
	}

	private static void impact(ServerPlayer player) {
		if (!Hulk.isHulk(player) || !player.isAlive()) {
			return;
		}
		ServerLevel level = (ServerLevel) player.level();
		Vec3 at = player.position();
		double r = HulkConfig.ROAR_RADIUS;
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 2.0f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.8f, 1.4f);
		Vec3 mouth = player.getEyePosition().add(HulkAbilities.flatLook(player).scale(0.4 + player.getBbWidth() * 0.5));
		level.sendParticles(ParticleTypes.SONIC_BOOM, mouth.x, mouth.y, mouth.z, 1, 0, 0, 0, 0);
		for (double ring = 2.0; ring <= Math.min(r, 14.0); ring += 3.0) {
			HulkCombat.ring(level, ParticleTypes.CLOUD, at.add(0, 0.2, 0), ring, (int) (ring * 3));
			HulkCombat.ring(level, GAMMA, at.add(0, 0.8, 0), ring, (int) (ring * 4));
		}
		HulkCombat.shake(level, at, 0.5f, 14, r + 8.0);
		List<PathfinderMob> scared = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r, 6.0, r),
				e -> e != player && e.isAlive())) {
			if (!scares(player, e)) {
				continue;
			}
			if (e instanceof PathfinderMob mob) {
				scared.add(mob);
				flee(mob, at);
			} else if (e instanceof Mob mob) {
				mob.setTarget(null);
			}
		}
		if (!scared.isEmpty()) {
			FEAR.put(player.getUUID(), new Fear(scared, level.getGameTime() + HulkConfig.ROAR_FLEE_TICKS));
		}
	}

	/** Would the roar scare this one off? Low-level mobs only: never a player, never a boss, never his own pet. */
	public static boolean scares(ServerPlayer hulk, LivingEntity e) {
		if (e instanceof Player || !(e instanceof Mob) || e.distanceToSqr(hulk) > HulkConfig.ROAR_RADIUS * HulkConfig.ROAR_RADIUS) {
			return false;
		}
		if (e.getMaxHealth() > HulkConfig.ROAR_FLEE_MAX_HEALTH || com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
			return false;
		}
		return !(e instanceof net.minecraft.world.entity.OwnableEntity pet && hulk.getUUID().equals(pet.getOwnerUUID()));
	}

	/** Drop the mob's target and send it running from {@code from}. */
	private static void flee(PathfinderMob mob, Vec3 from) {
		mob.setTarget(null);
		Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 7, from);
		if (away != null) {
			mob.getNavigation().moveTo(away.x, away.y, away.z, 1.5);
		}
	}

	/** Is {@code mob} still running from a Hulk's roar? (GameTests) */
	public static boolean fleeing(Mob mob) {
		for (Fear fear : FEAR.values()) {
			if (fear.mobs().contains(mob)) {
				return true;
			}
		}
		return false;
	}

	/** Every tick for a Gamma player: keep his scared mobs running until the fear wears off. */
	static void tick(ServerPlayer player) {
		Fear fear = FEAR.get(player.getUUID());
		if (fear == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (now >= fear.until()) {
			FEAR.remove(player.getUUID());
			return;
		}
		Vec3 from = player.position();
		fear.mobs().removeIf(m -> !m.isAlive() || m.level() != player.level());
		for (PathfinderMob mob : fear.mobs()) {
			if (mob.getTarget() != null || mob.getNavigation().isDone() || now % 20 == 0) {
				flee(mob, from);
			}
		}
	}
}
