package com.projecthero.mod.galactus.orbit;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.mojang.math.Transformation;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Brightness;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * v0.17.2 Galactus raid v2: the moving rock -- vanilla block displays that fly apart when an island shatters, drift back
 * together when it re-forms, and slide into place when the Worldship pulls the islands into its hull. Each piece is spawned
 * still, then (a tick later, once the client has it) handed one long transformation the client interpolates on its own --
 * one packet per piece per move, no per-tick server work -- and discarded when its time is up. Static scratch state:
 * cleared by {@code ServerStateReset}.
 */
public final class OrbitDebris {
	private static final List<Piece> LIVE = new ArrayList<>();
	/** Hard cap on live pieces across every raid. */
	public static final int CAP = 900;

	private OrbitDebris() {
	}

	private static final class Piece {
		final ResourceKey<Level> level;
		final UUID id;
		final long moveAt;
		final Vector3f to;
		final Quaternionf spin;
		final float scale;
		final int duration;
		final long discardAt;
		boolean moved;

		Piece(ResourceKey<Level> level, UUID id, long moveAt, Vector3f to, Quaternionf spin, float scale, int duration, long discardAt) {
			this.level = level;
			this.id = id;
			this.moveAt = moveAt;
			this.to = to;
			this.spin = spin;
			this.scale = scale;
			this.duration = duration;
			this.discardAt = discardAt;
		}
	}

	public static int live() {
		return LIVE.size();
	}

	/**
	 * A block of {@code state} appearing at {@code from} (block corner), which after {@code delay} ticks travels to
	 * {@code to} over {@code duration} ticks (turning by {@code spin}) and is removed {@code linger} ticks after it arrives.
	 */
	public static Display.BlockDisplay fly(ServerLevel level, BlockState state, Vec3 from, Vec3 to, float scale, Quaternionf startSpin,
			Quaternionf spin, int delay, int duration, int linger) {
		if (LIVE.size() >= CAP) {
			return null;
		}
		Display.BlockDisplay d = EntityType.BLOCK_DISPLAY.create(level);
		if (d == null) {
			return null;
		}
		d.setBlockState(state);
		d.setViewRange(6.0f);
		d.setBrightnessOverride(Brightness.FULL_BRIGHT);
		d.moveTo(from.x, from.y, from.z, 0f, 0f);
		d.setTransformationInterpolationDelay(0);
		d.setTransformationInterpolationDuration(0);
		d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(startSpin), new Vector3f(scale, scale, scale), null));
		d.addTag("projecthero_orbit_debris");
		level.addFreshEntity(d);
		long now = level.getGameTime();
		Vector3f off = new Vector3f((float) (to.x - from.x), (float) (to.y - from.y), (float) (to.z - from.z));
		LIVE.add(new Piece(level.dimension(), d.getUUID(), now + Math.max(1, delay), off, spin, scale, duration,
				now + Math.max(1, delay) + duration + Math.max(0, linger)));
		return d;
	}

	public static void tick(MinecraftServer server) {
		if (LIVE.isEmpty()) {
			return;
		}
		for (Iterator<Piece> it = LIVE.iterator(); it.hasNext();) {
			Piece p = it.next();
			ServerLevel level = server.getLevel(p.level);
			Entity e = level == null ? null : level.getEntity(p.id);
			if (level == null || e == null) {
				// unloaded or gone
				if (level == null || level.getGameTime() > p.discardAt + 200) {
					it.remove();
				}
				continue;
			}
			long now = level.getGameTime();
			if (!p.moved && now >= p.moveAt && e instanceof Display.BlockDisplay d) {
				p.moved = true;
				d.setTransformationInterpolationDelay(0);
				d.setTransformationInterpolationDuration(p.duration);
				d.setTransformation(new Transformation(p.to, new Quaternionf(p.spin), new Vector3f(p.scale, p.scale, p.scale), null));
			}
			if (now >= p.discardAt) {
				e.discard();
				it.remove();
			}
		}
	}

	/** Removes every live piece in {@code level} (an instance being wiped). */
	public static void clear(ServerLevel level) {
		for (Iterator<Piece> it = LIVE.iterator(); it.hasNext();) {
			Piece p = it.next();
			if (p.level == level.dimension()) {
				Entity e = level.getEntity(p.id);
				if (e != null) {
					e.discard();
				}
				it.remove();
			}
		}
	}

	public static void clearStatics() {
		LIVE.clear();
	}
}
