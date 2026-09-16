package com.projecthero.mod.greenlantern.construct;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * One live hard-light construct: owner, type, hit points (where applicable), lifetime, and whatever
 * placed blocks it owns. A single flexible holder rather than a class per construct -- see
 * {@link GreenLanternConstructs}' javadoc for why -- so a few fields are only meaningful for certain
 * {@link ConstructType.Kind}s (documented per field).
 */
public final class Construct {
	/** One block this construct is responsible for restoring when it expires/dismisses. */
	public record Cell(BlockPos pos, BlockState previous, BlockState placed) {
	}

	public final UUID owner;
	public final ConstructType type;
	public final ServerLevel level;
	public final long createdAt;
	/** 0 = no automatic expiry (dismiss-only), e.g. Energy Blade, Mining Drill. */
	public long expiresAt;
	public float hp;
	public final Vec3 anchor;
	public final List<Cell> cells = new ArrayList<>();

	// ---- kind-specific scratch state ----
	/** TURRET: game-time of its next shot. */
	public long nextFireAt;
	/** CAGE: the entity id caged, or -1. */
	public int cagedEntityId = -1;
	/** TETHER: the entity id being pulled, or -1. */
	public int tetherTargetId = -1;
	/** BUBBLE: radius this instance was deployed with (currently always {@code GreenLanternConfig#BUBBLE_RADIUS}). */
	public double radius;
	/** BRIDGE/RAMP: facing used at deploy time, so the tick loop doesn't need to re-derive it. */
	public net.minecraft.core.Direction facing;
	/** WALL/CAGE break-cooldown bookkeeping key suffix, so multiple constructs of the same type don't collide. */
	public final int instanceId;

	private static int nextInstanceId = 1;

	public Construct(UUID owner, ConstructType type, ServerLevel level, Vec3 anchor, long now) {
		this.owner = owner;
		this.type = type;
		this.level = level;
		this.anchor = anchor;
		this.createdAt = now;
		this.hp = type.maxHp();
		this.expiresAt = type.maxDurationTicks() > 0 ? now + type.maxDurationTicks() : 0L;
		this.instanceId = nextInstanceId++;
	}

	public boolean expired(long now) {
		return expiresAt != 0L && now >= expiresAt;
	}
}
