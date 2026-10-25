package com.projecthero.mod.silversurfer;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.silversurfer.network.SurferStatuePayload;

import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: Shift+G Transmutation's silver statues. A transmuted creature is held where it stood (no movement, no target,
 * its own attacks cancelled by {@link SilverSurferDamage}) for {@link SilverSurferConfig#STATUE_TICKS}; the next hit on it
 * -- anyone's -- shatters it for {@link SilverSurferConfig#SHATTER_BONUS} more. Every viewer is told
 * ({@link SurferStatuePayload}) so the client draws it in silver. Bosses are never frozen (only slowed). Players are held
 * half as long, and only when the server allows hard crowd control on players. No AI flag is ever set, so nothing that
 * could outlive a crash is saved on the creature. Static state: cleared by {@code ServerStateReset}.
 */
public final class SilverSurferStatues {
	private static final class Statue {
		final ResourceKey<Level> dim;
		final long until;
		final Vec3 pos;
		final float yRot;
		final float xRot;
		final UUID owner;

		Statue(ResourceKey<Level> dim, long until, Vec3 pos, float yRot, float xRot, UUID owner) {
			this.dim = dim;
			this.until = until;
			this.pos = pos;
			this.yRot = yRot;
			this.xRot = xRot;
			this.owner = owner;
		}
	}

	private static final Map<UUID, Statue> STATUES = new ConcurrentHashMap<>();

	private SilverSurferStatues() {
	}

	static void clearSessionState() {
		STATUES.clear();
	}

	public static boolean isStatue(Entity e) {
		Statue s = e == null ? null : STATUES.get(e.getUUID());
		return s != null && s.until > e.level().getGameTime();
	}

	/** Turns {@code target} to silver; false (and nothing changes) for a boss, which is only slowed. */
	public static boolean transmute(ServerPlayer owner, LivingEntity target) {
		ServerLevel level = (ServerLevel) target.level();
		if (SilverSurferCombat.isBoss(target)) {
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SilverSurferConfig.STATUE_TICKS, 2, false, true, true));
			return false;
		}
		int ticks = SilverSurferConfig.STATUE_TICKS;
		if (target instanceof Player) {
			if (!HeroConfig.get().abilityHardCrowdControlOnPlayers) {
				target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SilverSurferConfig.STATUE_PLAYER_TICKS, 2, false, true, true));
				return false;
			}
			ticks = SilverSurferConfig.STATUE_PLAYER_TICKS;
			target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, true));
			target.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false, false));
			target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 9, false, false, true));
		}
		STATUES.put(target.getUUID(), new Statue(level.dimension(), level.getGameTime() + ticks, target.position(), target.getYRot(),
				target.getXRot(), owner.getUUID()));
		target.setDeltaMovement(Vec3.ZERO);
		target.hurtMarked = true;
		if (target instanceof Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		broadcast(target, ticks);
		Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(SilverSurfer.SILVER_BIG, c.x, c.y, c.z, 30, target.getBbWidth() * 0.5, target.getBbHeight() * 0.5,
				target.getBbWidth() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 0.6f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.8f);
		return true;
	}

	/** The statue breaks: silver shards, the sound of shattering metal. Returns whether it was a statue. */
	public static boolean shatter(LivingEntity target) {
		Statue s = STATUES.remove(target.getUUID());
		if (s == null) {
			return false;
		}
		broadcast(target, 0);
		if (target.level() instanceof ServerLevel level) {
			Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.defaultBlockState()), c.x, c.y, c.z, 40,
					target.getBbWidth() * 0.4, target.getBbHeight() * 0.4, target.getBbWidth() * 0.4, 0.2);
			level.sendParticles(SilverSurfer.SILVER_BIG, c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.0);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2f, 0.7f);
			level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 0.6f, 1.5f);
		}
		if (target instanceof Player) {
			target.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
			target.removeEffect(MobEffects.JUMP);
			target.removeEffect(MobEffects.WEAKNESS);
		}
		return true;
	}

	private static void broadcast(LivingEntity target, int ticks) {
		SurferStatuePayload payload = new SurferStatuePayload(target.getId(), ticks);
		for (ServerPlayer viewer : PlayerLookup.tracking(target)) {
			ServerPlayNetworking.send(viewer, payload);
		}
		if (target instanceof ServerPlayer self) {
			ServerPlayNetworking.send(self, payload);
		}
	}

	/** Every server tick: holds every statue in place, thaws the ones whose time is up. */
	public static void tick(MinecraftServer server) {
		if (STATUES.isEmpty()) {
			return;
		}
		Iterator<Map.Entry<UUID, Statue>> it = STATUES.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Statue> e = it.next();
			Statue s = e.getValue();
			ServerLevel level = server.getLevel(s.dim);
			Entity en = level == null ? null : level.getEntity(e.getKey());
			if (!(en instanceof LivingEntity target) || !target.isAlive()) {
				it.remove();
				continue;
			}
			if (s.until <= level.getGameTime()) {
				it.remove();
				broadcast(target, 0);
				continue;
			}
			if (target instanceof ServerPlayer) {
				continue; // players are held by their effects (teleporting them every tick would fight their client)
			}
			target.setPos(s.pos.x, Math.min(target.getY(), s.pos.y), s.pos.z);
			target.setDeltaMovement(0, Math.min(0, target.getDeltaMovement().y), 0);
			target.setYRot(s.yRot);
			target.setXRot(s.xRot);
			target.setYHeadRot(s.yRot);
			target.setYBodyRot(s.yRot);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
				mob.setTarget(null);
			}
			if (level.getGameTime() % 10 == 0) {
				Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
				level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.3, 0.5, 0.3, 0.0);
			}
		}
	}
}
