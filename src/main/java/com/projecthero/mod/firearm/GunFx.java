package com.projecthero.mod.firearm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.network.GunTracerPayload;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.15.16 (user: "create new vfx and sfx for the gun shooting"): everything a shot looks and sounds like, server side.
 * <ul>
 *   <li><b>the report</b>: the gun's own synthesised shot ({@link GunSounds}), heard loud out to ~48 blocks, and a
 *       muffled, echoing distant version sent to everyone between {@link #FAR_FROM} and {@link #FAR_TO} blocks;</li>
 *   <li><b>the muzzle</b>: a smoke puff + sparks (bigger for the shotgun and sniper), and a brass casing (a red shotgun
 *       hull, after the pump) spat out to the right (silently since v0.15.18 -- the landing clink was removed); the
 *       shotgun's pump and the sniper's bolt follow a moment later;</li>
 *   <li><b>tracers</b>: one {@link GunTracerPayload} per bullet / pellet, so nearby clients draw a streak and an impact
 *       flash ({@code client.firearm.GunTracers});</li>
 *   <li><b>impacts</b>: block chips + dust + sparks and a thud on blocks (sometimes a ricochet whine off stone / metal),
 *       a wet hit and blood-red dust on creatures.</li>
 * </ul>
 */
public final class GunFx {
	public static final double FAR_FROM = 40.0;
	public static final double FAR_TO = 220.0;

	private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.02f, 0.02f), 1.1f);

	/** A delayed beat after a shot: a sound (the pump / bolt) or, with {@code sound == null}, just the ejected casing. */
	private record Pending(ServerLevel level, UUID shooter, SoundEvent sound, float volume, float pitch, long at, boolean particle,
			boolean hull) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private GunFx() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	/** 0 pistol, 1 rifle, 2 shotgun, 3 sniper. */
	public static int kind(FirearmData data) {
		return switch (data.id) {
			case Firearms.PISTOL -> 0;
			case Firearms.SHOTGUN -> 2;
			case Firearms.SNIPER -> 3;
			default -> 1;
		};
	}

	/** The shot itself: report (near + far), muzzle smoke / sparks, the ejected casing and the delayed mechanical sounds. */
	public static void shot(ServerPlayer player, FirearmData data, Vec3 muzzle, Vec3 look) {
		ServerLevel level = player.serverLevel();
		int kind = kind(data);
		float pitch = data.firePitch + (level.random.nextFloat() - 0.5f) * 0.08f;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), data.fireSound, SoundSource.PLAYERS, data.fireVolume, pitch);
		SoundEvent far = GunSounds.farOf(data.fireSound);
		if (far != null) {
			Holder<SoundEvent> holder = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(far);
			for (ServerPlayer p : level.players()) {
				double d = p.distanceTo(player);
				if (d >= FAR_FROM && d <= FAR_TO * (kind == 3 ? 1.3 : 1.0)) {
					// a sound reaches 16 * volume blocks; fade with distance from there
					float vol = (float) (d / 16.0 + 1.0) * (float) Math.max(0.25, 1.0 - d / (FAR_TO * 1.4));
					p.connection.send(new ClientboundSoundPacket(holder, SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(),
							vol, pitch, level.random.nextLong()));
				}
			}
		}

		// muzzle smoke + sparks
		boolean big = kind >= 2;
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, big ? 6 : 3, 0.04, 0.04, 0.04, 0.012);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, big ? 7 : 3, 0.03, 0.03, 0.03, 0.12);
		if (big) {
			Vec3 ahead = muzzle.add(look.scale(0.6));
			level.sendParticles(ParticleTypes.POOF, ahead.x, ahead.y, ahead.z, kind == 2 ? 5 : 3, 0.12, 0.12, 0.12, 0.02);
		}

		// casing: out of the ejection port to the right (the shotgun's hull and the sniper's case come out on the pump / bolt)
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		long now = level.getGameTime();
		// v0.15.18 (user: "remove that tring sound effect when shooting"): the casing / hull still flies out, but its
		// brass "tring" (and the shotgun hull's tock) landing a moment later is gone -- those sounds were removed outright
		if (kind <= 1) {
			eject(level, muzzle.subtract(look.scale(0.45)), right, false);
		} else {
			schedule(level, player, kind == 2 ? GunSounds.PUMP : GunSounds.BOLT, 0.7f, 1.0f, now + (kind == 2 ? 7 : 10), false, false);
			schedule(level, player, null, 0f, 1f, now + (kind == 2 ? 17 : 22), true, kind == 2);
		}
	}

	private static void eject(ServerLevel level, Vec3 at, Vec3 right, boolean hull) {
		ItemStack shell = new ItemStack(hull ? Items.RED_DYE : Items.GOLD_NUGGET);
		Vec3 v = right.scale(0.18).add(0, 0.12, 0);
		// count 0 = one particle with exactly this velocity
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, shell), at.x, at.y, at.z, 0, v.x, v.y, v.z, 1.0);
	}

	private static void schedule(ServerLevel level, ServerPlayer shooter, SoundEvent sound, float vol, float pitch, long at, boolean particle,
			boolean hull) {
		PENDING.add(new Pending(level, shooter.getUUID(), sound, vol, pitch, at, particle, hull));
	}

	private static void tick() {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (p.level.getGameTime() < p.at) {
				continue;
			}
			it.remove();
			if (!(p.level.getPlayerByUUID(p.shooter) instanceof ServerPlayer s)) {
				continue;
			}
			if (p.sound != null) {
				p.level.playSound(null, s.getX(), s.getY(), s.getZ(), p.sound, SoundSource.PLAYERS, p.volume, p.pitch);
			}
			if (p.particle) {
				Vec3 look = s.getLookAngle();
				Vec3 right = look.cross(new Vec3(0, 1, 0));
				right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
				Vec3 at = s.getEyePosition().add(look.scale(0.4)).add(right.scale(0.25)).subtract(0, 0.4, 0);
				eject(p.level, at, right, p.hull);
			}
		}
	}

	/** One bullet's flight, for the clients' tracer + impact flash. */
	public static void tracer(ServerLevel level, ServerPlayer shooter, FirearmData data, Vec3 from, Vec3 to, int hit) {
		GunTracerPayload payload = new GunTracerPayload(shooter.getId(), from.x, from.y, from.z, to.x, to.y, to.z, kind(data), hit);
		for (ServerPlayer viewer : PlayerLookup.around(level, from.lerp(to, 0.5), 96.0 + from.distanceTo(to) * 0.5)) {
			ServerPlayNetworking.send(viewer, payload);
		}
	}

	/** A bullet striking a block: chips, dust, sparks and a thud -- off stone / metal it sometimes ricochets. */
	public static void impactBlock(ServerLevel level, Vec3 pos, BlockPos blockPos) {
		BlockState state = level.getBlockState(blockPos);
		if (!state.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.x, pos.y, pos.z, 10, 0.08, 0.08, 0.08, 0.12);
			level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, state), pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0.02);
		}
		level.sendParticles(ParticleTypes.SMOKE, pos.x, pos.y, pos.z, 3, 0.05, 0.05, 0.05, 0.015);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 4, 0.04, 0.04, 0.04, 0.18);
		float pitch = 0.9f + level.random.nextFloat() * 0.25f;
		level.playSound(null, pos.x, pos.y, pos.z, GunSounds.IMPACT, SoundSource.PLAYERS, 0.5f, pitch);
		boolean hard = state.getSoundType() == net.minecraft.world.level.block.SoundType.STONE
				|| state.getSoundType() == net.minecraft.world.level.block.SoundType.METAL
				|| state.getSoundType() == net.minecraft.world.level.block.SoundType.DEEPSLATE;
		if (hard && level.random.nextFloat() < 0.3f) {
			level.playSound(null, pos.x, pos.y, pos.z, GunSounds.RICOCHET, SoundSource.PLAYERS, 0.45f, 0.9f + level.random.nextFloat() * 0.3f);
		}
	}

	/** A bullet striking a creature: a wet hit and a spray of dark red. */
	public static void impactFlesh(ServerLevel level, Vec3 pos, LivingEntity target) {
		level.sendParticles(BLOOD, pos.x, pos.y, pos.z, 8, 0.12, 0.12, 0.12, 0.0);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, pos.x, pos.y, pos.z, 2, 0.1, 0.1, 0.1, 0.0);
		level.playSound(null, pos.x, pos.y, pos.z, GunSounds.FLESH, SoundSource.PLAYERS, 0.55f, 0.9f + level.random.nextFloat() * 0.2f);
	}
}
