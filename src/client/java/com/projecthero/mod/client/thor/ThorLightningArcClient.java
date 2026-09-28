package com.projecthero.mod.client.thor;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.network.ThorLightningArcPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side book-keeping for Thor's crackling lightning arcs (v0.13.4): Lightning Beam and Chain
 * Lightning both resolve down to one or more of these segments instead of a line of particles. Mirrors
 * {@code SpiderStrands}' hold/fade shape, keyed by {@code (casterId, slot)} instead of (playerId, slot)
 * so a segment naturally replaces its own predecessor (the continuous beam resending every tick) while
 * unrelated segments (other chain hops, other casters) never collide.
 */
public final class ThorLightningArcClient {
	/** One live segment. {@code startTick} is in game ticks (with fraction) so fades are frame-rate independent. */
	public static final class Arc {
		final int casterId;
		final int slot;
		final int fromEntityId;
		final int targetEntityId;
		Vec3 point;
		final double startTick;
		final int hold;
		final int fade;

		Arc(int casterId, int slot, int fromEntityId, int targetEntityId, Vec3 point, double startTick, int hold, int fade) {
			this.casterId = casterId;
			this.slot = slot;
			this.fromEntityId = fromEntityId;
			this.targetEntityId = targetEntityId;
			this.point = point;
			this.startTick = startTick;
			this.hold = hold;
			this.fade = fade;
		}

		/** Opacity 0..1 at {@code now} (game ticks); {@code <= 0} once fully faded. */
		float alpha(double now) {
			double age = now - startTick;
			if (age <= hold) {
				return 1.0f;
			}
			if (fade <= 0) {
				return 0.0f;
			}
			return (float) (1.0 - (age - hold) / fade);
		}
	}

	private static final List<Arc> ARCS = new ArrayList<>();

	private ThorLightningArcClient() {
	}

	public static List<Arc> arcs() {
		return ARCS;
	}

	public static void reset() {
		ARCS.clear();
	}

	private static double now(float partial) {
		Minecraft mc = Minecraft.getInstance();
		return (mc.level == null ? 0L : mc.level.getGameTime()) + partial;
	}

	public static void accept(ThorLightningArcPayload p) {
		ARCS.removeIf(a -> a.casterId == p.casterId() && a.slot == p.slot());
		if (p.holdTicks() <= 0 && p.fadeTicks() <= 0) {
			return;
		}
		ARCS.add(new Arc(p.casterId(), p.slot(), p.fromEntityId(), p.targetEntityId(),
				new Vec3(p.x(), p.y(), p.z()), now(0.0f), p.holdTicks(), p.fadeTicks()));
	}

	/** Drop expired segments and segments whose caster has left the world. Call once per frame. */
	public static void prune(float partial) {
		Minecraft mc = Minecraft.getInstance();
		double now = now(partial);
		ARCS.removeIf(a -> a.alpha(now) <= 0.0f || mc.level == null || mc.level.getEntity(a.casterId) == null);
	}

	/** Where a segment's near end is right now: the given entity, or -- when {@code fromEntityId < 0} --
	 * an approximation of Mjolnir's position in the caster's main hand (v0.13.6: previously this used a
	 * point at chest height, which read as the bolt coming out of Thor's chest rather than the hammer). */
	public static Vec3 fromPoint(Arc arc, float partial) {
		Minecraft mc = Minecraft.getInstance();
		if (arc.fromEntityId >= 0) {
			Entity e = mc.level == null ? null : mc.level.getEntity(arc.fromEntityId);
			if (e != null && e.isAlive()) {
				return interpolated(e, partial).add(0.0, e.getBbHeight() * 0.5, 0.0);
			}
		}
		Entity caster = mc.level == null ? null : mc.level.getEntity(arc.casterId);
		if (caster instanceof Player player) {
			return hammerHandPosition(player, partial);
		}
		return arc.point;
	}

	/** Roughly where Mjolnir sits in the caster's main hand: down near the hip on the main-arm side and
	 * a little forward, rather than up at the chest/head. */
	private static Vec3 hammerHandPosition(Player player, float partial) {
		Vec3 look = player.getViewVector(partial);
		Vec3 forward = new Vec3(look.x, 0.0, look.z);
		forward = forward.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : forward.normalize();
		Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
		double sideSign = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;
		return interpolated(player, partial)
				.add(0.0, player.getBbHeight() * 0.42, 0.0)
				.add(side.scale(0.4 * sideSign))
				.add(forward.scale(0.3));
	}

	/** Where a segment's far end is right now: its entity (while it exists) or the stored raw point. */
	public static Vec3 targetPoint(Arc arc, float partial) {
		if (arc.targetEntityId >= 0) {
			Minecraft mc = Minecraft.getInstance();
			Entity e = mc.level == null ? null : mc.level.getEntity(arc.targetEntityId);
			if (e != null && e.isAlive()) {
				Vec3 at = interpolated(e, partial).add(0.0, e.getBbHeight() * 0.5, 0.0);
				arc.point = at;
				return at;
			}
		}
		return arc.point;
	}

	private static Vec3 interpolated(Entity e, float partial) {
		return new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()),
				Mth.lerp(partial, e.zo, e.getZ()));
	}
}
