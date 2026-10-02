package com.projecthero.mod.stormbreaker;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.projecthero.mod.item.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.19: Stormbreaker's Sneak + right-click -- open the Bifrost to the block you are looking at, up to
 * {@link #RANGE} blocks away, in the same dimension. A rainbow column marks both ends; anyone standing within
 * {@link #CARRY_RADIUS} blocks of you rides along (sneak to stay behind). 30 s vanilla item cooldown, which also
 * holds the throw for those 30 s -- melee and Thor's keys are unaffected.
 */
public final class Bifrost {
	public static final double RANGE = 256.0;
	public static final double CARRY_RADIUS = 3.0;
	public static final int COOLDOWN_TICKS = 30 * 20;
	/** How far the landing search climbs above / drops below the spot you aimed at. */
	private static final int CLIMB = 8;
	private static final int DROP = 6;
	private static final double COLUMN_HEIGHT = 22.0;

	/** Red, orange, yellow, green, blue, indigo, violet -- the bridge's colours bottom to top. */
	private static final Vector3f[] RAINBOW = {
			new Vector3f(1.00f, 0.15f, 0.15f), new Vector3f(1.00f, 0.55f, 0.10f), new Vector3f(1.00f, 0.95f, 0.20f),
			new Vector3f(0.20f, 0.95f, 0.30f), new Vector3f(0.20f, 0.55f, 1.00f), new Vector3f(0.35f, 0.25f, 0.90f),
			new Vector3f(0.70f, 0.30f, 1.00f),
	};

	private Bifrost() {
	}

	/**
	 * Whether the crosshair is on a block within the player's normal interaction reach -- that click belongs to the
	 * block (vanilla sneak-use rules), so the Bifrost stays shut and nothing about chests or doors changes.
	 */
	public static boolean targetsBlockInReach(Player player) {
		HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
		return hit.getType() == HitResult.Type.BLOCK;
	}

	/** Opens the Bifrost for {@code player}. @return true if they went. */
	public static boolean open(ServerPlayer player) {
		if (player.getCooldowns().isOnCooldown(ModItems.STORMBREAKER)) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		BlockHitResult hit = aim(player);
		if (hit == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.bifrost.no_target"), true);
			return false;
		}
		BlockPos landing = findLanding(level, hit);
		if (landing == null) {
			player.displayClientMessage(Component.translatable("message.projecthero.bifrost.no_room"), true);
			level.playSound(null, player.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 1.4f);
			return false;
		}

		Vec3 from = player.position();
		Vec3 to = Vec3.atBottomCenterOf(landing);

		List<ServerPlayer> riders = new ArrayList<>();
		riders.add(player);
		for (ServerPlayer other : level.players()) {
			if (other != player && other.isAlive() && !other.isSpectator() && !other.isShiftKeyDown()
					&& other.distanceToSqr(player) <= CARRY_RADIUS * CARRY_RADIUS) {
				riders.add(other);
			}
		}

		column(level, from);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.6f);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5f, 1.3f);

		for (ServerPlayer rider : riders) {
			// keep each rider's offset from the caster if that spot is just as safe, otherwise everyone shares the landing
			Vec3 offset = rider == player ? Vec3.ZERO : rider.position().subtract(from);
			Vec3 dest = to.add(offset.x, 0.0, offset.z);
			if (rider != player && !isStandable(level, BlockPos.containing(dest))) {
				dest = to;
			}
			if (rider.isPassenger()) {
				rider.stopRiding();
			}
			rider.teleportTo(dest.x, dest.y, dest.z);
			rider.setDeltaMovement(Vec3.ZERO);
			rider.hurtMarked = true;
			rider.resetFallDistance();
			if (rider != player) {
				rider.displayClientMessage(Component.translatable("message.projecthero.bifrost.carried", player.getDisplayName()), true);
			}
		}

		column(level, to);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0f, 1.3f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.1f);
		level.playSound(null, to.x, to.y, to.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5f, 1.2f);

		player.getCooldowns().addCooldown(ModItems.STORMBREAKER, COOLDOWN_TICKS);
		return true;
	}

	/**
	 * The block under the crosshair, up to {@link #RANGE} away. The ray is cut short at the first unloaded chunk, so
	 * aiming at the horizon never makes the server load (or generate) terrain synchronously.
	 */
	static BlockHitResult aim(Player player) {
		Level level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		double reach = 0.0;
		while (reach < RANGE) {
			double next = Math.min(RANGE, reach + 8.0);
			if (!level.isLoaded(BlockPos.containing(eye.add(look.scale(next))))) {
				break;
			}
			reach = next;
		}
		if (reach <= 0.0) {
			return null;
		}
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)),
				ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.BLOCK ? hit : null;
	}

	/**
	 * A spot with a solid floor and two free blocks to stand in: on top of what you aimed at (or in front of the face
	 * you aimed at, dropping to the floor below it), else the nearest such spot climbing up from there.
	 */
	public static BlockPos findLanding(Level level, BlockHitResult hit) {
		BlockPos start = hit.getDirection() == Direction.UP
				? hit.getBlockPos().above()
				: hit.getBlockPos().relative(hit.getDirection());
		for (int dy = 0; dy <= DROP; dy++) {
			BlockPos p = start.below(dy);
			if (isStandable(level, p)) {
				return p;
			}
			if (!isPassable(level, p)) {
				break;
			}
		}
		for (int dy = 1; dy <= CLIMB; dy++) {
			BlockPos p = start.above(dy);
			if (isStandable(level, p)) {
				return p;
			}
		}
		return null;
	}

	static boolean isStandable(Level level, BlockPos feet) {
		if (!level.isInWorldBounds(feet) || !level.isInWorldBounds(feet.above())) {
			return false;
		}
		BlockPos floor = feet.below();
		return isPassable(level, feet) && isPassable(level, feet.above())
				&& !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
				&& !level.getFluidState(floor).is(FluidTags.LAVA);
	}

	private static boolean isPassable(Level level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
				&& !level.getFluidState(pos).is(FluidTags.LAVA);
	}

	/** The rainbow bridge: a tall column of dust cycling red to violet, with a white sparkle core. */
	private static void column(ServerLevel level, Vec3 base) {
		int steps = (int) (COLUMN_HEIGHT / 0.5);
		for (int i = 0; i < steps; i++) {
			double y = base.y + i * 0.5;
			Vector3f colour = RAINBOW[(i / 3) % RAINBOW.length];
			level.sendParticles(new DustParticleOptions(colour, 2.0f), base.x, y, base.z, 3, 0.35, 0.15, 0.35, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, base.x, base.y + COLUMN_HEIGHT * 0.5, base.z,
				40, 0.25, COLUMN_HEIGHT * 0.3, 0.25, 0.02);
		level.sendParticles(ParticleTypes.FLASH, base.x, base.y + 1.0, base.z, 1, 0.0, 0.0, 0.0, 0.0);
	}
}
