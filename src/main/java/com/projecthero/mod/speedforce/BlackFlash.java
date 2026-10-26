package com.projecthero.mod.speedforce;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.speedforce.entity.BlackFlashEntity;
import com.projecthero.mod.speedforce.entity.SpeedForceEntityTypes;
import com.projecthero.mod.speedforce.network.SpeedForceFxPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Black Flash fights -- one per runner, at most ({@link #FIGHTS}; cleared through
 * {@code SpeedForceSpeedster#clearSessionState}). The rules:
 * <ul>
 *   <li>he is gone (back into the Speed Force) if his runner dies or logs out, or after
 *       {@link BlackFlashConfig.Boss#noTargetDespawnTicks} with nothing to fight;</li>
 *   <li>the drain the trip would have cost is held off while he fights: beat him and there is none; if he leaves any other
 *       way while the runner is alive, they take the full 2 minutes then;</li>
 *   <li>never saved (the entity type is {@code noSave}).</li>
 * </ul>
 */
public final class BlackFlash {
	/** runner -> the Black Flash hunting them. */
	private static final Map<UUID, BlackFlashEntity> FIGHTS = new ConcurrentHashMap<>();

	private BlackFlash() {
	}

	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				// v0.17.1 (user): killed by the Black Flash (or his Time Remnant) -- the Speed Force is taken from them for
				// 20 minutes, and he goes
				if (source.getEntity() instanceof BlackFlashEntity && SpeedForceSpeedster.hasPower(p)) {
					SpeedForceSpeedster.losePower(p, SpeedForceConfig.POWER_LOSS_TICKS);
				}
				end(p, "won");
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onLogout(handler.getPlayer()));
	}

	public static void clearSessionState() {
		FIGHTS.clear();
	}

	/** The Black Flash hunting {@code player} right now, or null. */
	public static BlackFlashEntity fightOf(ServerPlayer player) {
		BlackFlashEntity bf = FIGHTS.get(player.getUUID());
		if (bf != null && (bf.isRemoved() || !bf.isAlive())) {
			FIGHTS.remove(player.getUUID(), bf);
			return null;
		}
		return bf;
	}

	/**
	 * v0.17.1 (user): one of the Flash Time caster's extra ticks -- the Black Flash hunting them gets it too, so the slowed
	 * world doesn't slow him (he is a speedster).
	 */
	public static void extraTick(ServerPlayer caster, ServerLevel level) {
		BlackFlashEntity bf = fightOf(caster);
		if (bf == null || bf.level() != level || bf.isRemoved()) {
			return;
		}
		level.tickNonPassenger(bf);
		if (bf.isRemoved()) {
			return;
		}
		// the world's own entity sync only runs on the slowed ticks: send where he is (and his move's state) now
		net.minecraft.network.protocol.Packet<?> pos = new net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket(bf);
		net.minecraft.network.protocol.Packet<?> head = new net.minecraft.network.protocol.game.ClientboundRotateHeadPacket(bf,
				(byte) net.minecraft.util.Mth.floor(bf.getYHeadRot() * 256.0f / 360.0f));
		java.util.List<net.minecraft.network.syncher.SynchedEntityData.DataValue<?>> dirty = bf.getEntityData().packDirty();
		for (ServerPlayer viewer : net.fabricmc.fabric.api.networking.v1.PlayerLookup.tracking(bf)) {
			viewer.connection.send(pos);
			viewer.connection.send(head);
			if (dirty != null) {
				viewer.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket(bf.getId(), dirty));
			}
		}
	}

	/** Every fight running (tests). */
	public static int fights() {
		FIGHTS.values().removeIf(bf -> bf.isRemoved());
		return FIGHTS.size();
	}

	/**
	 * He follows {@code player} out of the Speed Force: in {@code level} at {@code at} (or the one already hunting them is
	 * brought there), the fight on. Null if he could not be made.
	 */
	public static BlackFlashEntity spawnFor(ServerPlayer player, ServerLevel level, Vec3 at) {
		BlackFlashEntity bf = fightOf(player);
		if (bf != null && bf.level() != level) {
			FIGHTS.remove(player.getUUID(), bf);
			bf.discard();
			bf = null;
		}
		if (bf == null) {
			bf = SpeedForceEntityTypes.BLACK_FLASH.create(level);
			if (bf == null) {
				return null;
			}
			bf.configure(player.getUUID());
			bf.moveTo(at.x, at.y, at.z, player.getYRot() + 180f, 0f);
			FIGHTS.put(player.getUUID(), bf);
			level.addFreshEntity(bf);
		} else {
			bf.moveTo(at.x, at.y, at.z, bf.getYRot(), 0f);
		}
		bf.setTarget(player);
		bf.threat().add(player, 1.0f);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1, at.z, 2, 0.2, 0.2, 0.2, 0);
		level.sendParticles(BlackFlashEntity.BLACK, at.x, at.y + 1, at.z, 40, 0.5, 1.0, 0.5, 0.0);
		fx(bf, SpeedForceFxPayload.BF_BOLT, at.add(0, 24, 0), at.add(0, 1, 0));
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 1.4f, 0.5f);
		bf.say(level, "intro");
		return bf;
	}

	/**
	 * Once a tick (from his AI step): the despawn rules. False if he is gone -- the runner is dead or not online, or he has
	 * had nothing to fight for too long.
	 */
	public static boolean tickRules(BlackFlashEntity bf, ServerLevel level) {
		UUID id = bf.hunted();
		ServerPlayer p = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		if (p == null || !p.isAlive()) {
			bf.discard();
			return false;
		}
		if (bf.noTargetTicks() >= BlackFlashConfig.get().boss.noTargetDespawnTicks) {
			bf.say(level, "gone");
			level.sendParticles(BlackFlashEntity.BLACK, bf.getX(), bf.getY() + 1, bf.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
			bf.discard();
			return false;
		}
		return true;
	}

	/** The runner logged out: he goes (and the drain the trip would have cost is saved on them). */
	public static void onLogout(ServerPlayer player) {
		end(player, null);
	}

	/** The runner died or logged out: he goes ({@code line}: what he says, or nothing). */
	private static void end(ServerPlayer player, String line) {
		BlackFlashEntity bf = FIGHTS.remove(player.getUUID());
		if (bf != null && !bf.isRemoved()) {
			if (line == null && player.isAlive() && SpeedForceSpeedster.hasPower(player)) {
				SpeedForceSpeedster.drain(player, SpeedForceConfig.DRAINED_TICKS); // logged out mid-fight: the drain is saved
			}
			if (line != null && bf.level() instanceof ServerLevel sl) {
				bf.say(sl, line);
			}
			bf.discard();
		}
	}

	/** Beaten: the runner leaves the Speed Force with no drain at all. */
	public static void onDefeated(BlackFlashEntity bf, ServerLevel level) {
		UUID id = bf.hunted();
		ServerPlayer p = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
		if (id != null) {
			FIGHTS.remove(id, bf);
		}
		if (p != null && SpeedForceSpeedster.hasPower(p)) {
			SpeedForceSpeedster.drain(p, 0);
			p.displayClientMessage(Component.translatable("message.projecthero.black_flash.defeated").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		}
	}

	/** He is gone without being beaten (despawned, the runner logged out): the trip's full drain lands now. */
	public static void onGone(BlackFlashEntity bf, boolean defeated) {
		UUID id = bf.hunted();
		if (id == null) {
			return;
		}
		FIGHTS.remove(id, bf);
		if (defeated || !(bf.level() instanceof ServerLevel level)) {
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
		if (p != null && p.isAlive() && SpeedForceSpeedster.hasPower(p)) {
			SpeedForceSpeedster.drain(p, SpeedForceConfig.DRAINED_TICKS);
			p.displayClientMessage(Component.translatable("message.projecthero.black_flash.escaped").withStyle(ChatFormatting.RED), false);
		}
	}

	/** One of his visuals, to every player within 96 blocks of him (playerId carries HIS id). */
	public static void fx(Entity bf, int kind, Vec3 from, Vec3 to) {
		if (!(bf.level() instanceof ServerLevel level)) {
			return;
		}
		SpeedForceFxPayload payload = new SpeedForceFxPayload(bf.getId(), kind, from.x, from.y, from.z, to.x, to.y, to.z);
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(bf) < 96 * 96 && ServerPlayNetworking.canSend(viewer, SpeedForceFxPayload.TYPE)) {
				ServerPlayNetworking.send(viewer, payload);
			}
		}
	}
}
