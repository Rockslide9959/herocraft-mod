package com.projecthero.mod.oathbreaker;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.network.WorldEventZoomPayload;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntity;
import com.projecthero.mod.oathbreaker.entity.OathbreakerEntityTypes;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;

/**
 * The delay between using a Knight's Soul and The Oathbreaker actually appearing: the item consumes
 * itself and cues the buildup immediately (rumble, a slight camera zoom, particles), then 5 real
 * seconds later this spawns him in, crouched (see {@link OathbreakerEntity#spawnIn}).
 *
 * <p>Purely a transient in-memory queue, the same shape {@link com.projecthero.mod.behemoth.BehemothSpawner}
 * uses for its own cooldown map -- nothing here is worth persisting across a restart (a summon lost mid-wait
 * just means the player has to use another Knight's Soul), so it is cleared by {@code ServerStateReset} like
 * every other static piece of server state this mod keeps rather than written to disk.
 */
public final class OathbreakerSummon {
	private static final int DELAY_TICKS = 5 * 20;
	/** How far the rumble/zoom cues reach. */
	private static final double CUE_RADIUS = 32.0;

	private static final class Pending {
		final ServerLevel level;
		final BlockPos pos;
		int ticksLeft;

		Pending(ServerLevel level, BlockPos pos, int ticksLeft) {
			this.level = level;
			this.pos = pos;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final List<Pending> PENDING = new ArrayList<>();

	private OathbreakerSummon() {
	}

	public static void clearSessionState() {
		PENDING.clear();
	}

	/** Called from {@code KnightsSoulItem} the instant the item is consumed. */
	public static void begin(ServerLevel level, BlockPos anchorPos) {
		PENDING.add(new Pending(level, anchorPos, DELAY_TICKS));
		cue(level, anchorPos, 0.4f, 6);
		sendZoom(level, anchorPos, 0.12f, 30);
		level.playSound(null, anchorPos, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.BLOCKS, 2.0f, 0.5f);
	}

	public static void tick(MinecraftServer server) {
		if (PENDING.isEmpty()) {
			return;
		}
		Iterator<Pending> it = PENDING.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			p.ticksLeft--;
			int elapsed = DELAY_TICKS - p.ticksLeft;
			// A slow, escalating rumble for the whole wait -- stronger and more frequent as the moment
			// gets closer, so the buildup actually reads as building up rather than one flat hum.
			if (p.ticksLeft > 0 && elapsed % Math.max(4, 16 - elapsed / 10) == 0) {
				float strength = 0.15f + 0.35f * (elapsed / (float) DELAY_TICKS);
				cue(p.level, p.pos, strength, 8);
				p.level.sendParticles(ParticleTypes.SOUL, p.pos.getX() + 0.5, p.pos.getY() + 1.0, p.pos.getZ() + 0.5,
						4, 0.4, 0.3, 0.4, 0.02);
			}
			if (p.ticksLeft <= 0) {
				spawnNow(p.level, p.pos);
				it.remove();
			}
		}
	}

	private static void spawnNow(ServerLevel level, BlockPos anchorPos) {
		BlockPos spawnPos = findSpot(level, anchorPos);
		if (spawnPos == null) {
			// The ground changed during the 5s wait (built over, etc.) -- nothing sane to do but drop
			// the summon; the Knight's Soul is already spent, same as any other spell that fizzles.
			return;
		}
		OathbreakerEntity oathbreaker = OathbreakerEntityTypes.OATHBREAKER.create(level);
		if (oathbreaker == null) {
			return;
		}
		oathbreaker.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
				level.random.nextFloat() * 360f, 0f);
		oathbreaker.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.MOB_SUMMONED, null);
		scaleHealthForParty(level, oathbreaker, spawnPos);
		oathbreaker.setPersistenceRequired();
		level.addFreshEntity(oathbreaker);
		oathbreaker.spawnIn();

		cue(level, anchorPos, 1.0f, 15);
		sendZoom(level, anchorPos, 0.2f, 20);
		level.playSound(null, spawnPos, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0f, 0.7f);
		level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
				60, 0.6, 0.6, 0.6, 0.08);
	}

	/**
	 * v0.14.0: max health scales up 40% per extra player within {@link OathbreakerTuning#HP_SCALE_RADIUS}
	 * blocks of the spawn point, capped at {@link OathbreakerTuning#HP_SCALE_MAX_EXTRA_PLAYERS} extra (a
	 * 4-player cap total) -- locked in right here at spawn and never touched again, per spec.
	 */
	private static void scaleHealthForParty(ServerLevel level, OathbreakerEntity oathbreaker, BlockPos spawnPos) {
		AABB area = AABB.ofSize(spawnPos.getCenter(), OathbreakerTuning.HP_SCALE_RADIUS * 2,
				OathbreakerTuning.HP_SCALE_RADIUS * 2, OathbreakerTuning.HP_SCALE_RADIUS * 2);
		int nearbyPlayers = level.getEntitiesOfClass(ServerPlayer.class, area,
				p -> p.distanceToSqr(spawnPos.getCenter()) <= OathbreakerTuning.HP_SCALE_RADIUS * OathbreakerTuning.HP_SCALE_RADIUS).size();
		int extraPlayers = Math.min(OathbreakerTuning.HP_SCALE_MAX_EXTRA_PLAYERS, Math.max(0, nearbyPlayers - 1));
		if (extraPlayers <= 0) {
			return;
		}
		var maxHealth = oathbreaker.getAttribute(Attributes.MAX_HEALTH);
		if (maxHealth == null) {
			return;
		}
		double scaled = OathbreakerTuning.MAX_HEALTH_BASE * (1.0 + OathbreakerTuning.HP_SCALE_PER_EXTRA_PLAYER * extraPlayers);
		maxHealth.setBaseValue(scaled);
		oathbreaker.setHealth((float) scaled);
	}

	/** A legal, open spot for the boss right above/beside the anchor. Shared by {@code KnightsSoulItem}'s
	 * up-front check (so a Knight's Soul is never spent somewhere it will just fail to place) and
	 * {@link #spawnNow} (which recomputes it once the wait ends, in case the ground changed). */
	public static BlockPos findSpot(ServerLevel level, BlockPos anchor) {
		for (int radius = 1; radius <= 3; radius++) {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				BlockPos candidate = anchor.relative(dir, radius);
				if (isOpen(level, candidate)) {
					return candidate;
				}
			}
		}
		return isOpen(level, anchor.above()) ? anchor.above() : null;
	}

	private static boolean isOpen(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos)) {
			return false;
		}
		for (int dy = 0; dy < 4; dy++) {
			BlockPos p = pos.above(dy);
			if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
				return false;
			}
		}
		BlockPos below = pos.below();
		return level.getBlockState(below).isSolidRender(level, below);
	}

	private static void cue(ServerLevel level, BlockPos center, float intensity, int ticks) {
		TitanShakePayload payload = new TitanShakePayload(intensity, ticks);
		for (var player : level.players()) {
			if (player.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) <= CUE_RADIUS * CUE_RADIUS) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}

	private static void sendZoom(ServerLevel level, BlockPos center, float amount, int ticks) {
		WorldEventZoomPayload payload = new WorldEventZoomPayload(amount, ticks);
		for (var player : level.players()) {
			if (player.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) <= CUE_RADIUS * CUE_RADIUS) {
				ServerPlayNetworking.send(player, payload);
			}
		}
	}
}
