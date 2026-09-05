package com.herocraft.mod.spider;

import com.herocraft.mod.attachment.ModAttachments;
import com.herocraft.mod.hero.data.ExperimentalState;
import com.herocraft.mod.spider.data.SpiderClimbLocal;
import com.herocraft.mod.spider.data.SpiderManState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The surface-adhesion engine shared by Spider Adhesion and Spider-Man -- the complete replacement
 * for the old implementation, which was a {@code LivingEntity#onClimbable} override plus a couple of
 * server-side velocity nudges.
 *
 * <h2>Why the old one felt limited</h2>
 * Making {@code onClimbable()} return true hands the player to vanilla's ladder physics. That gives
 * exactly one thing -- "hold the movement key into the wall to slide up it" -- and nothing else the
 * power promised:
 * <ul>
 *   <li>a ladder makes you <em>sink</em> at 0.15 blocks/tick whenever you stop pressing forward, so
 *       "hold position on a wall" was only possible while sneaking;</li>
 *   <li>ladder climbing is driven by forward input alone, so strafing across a wall did nothing;</li>
 *   <li>there is no ceiling case at all -- vanilla has no upside-down movement, so crawling along an
 *       overhang was impossible;</li>
 *   <li>the check was {@code player.horizontalCollision}, a single boolean with no notion of
 *       <em>which</em> surface you were on, so nothing could survive a corner, a block boundary or a
 *       one-tick gap: the flag drops for a tick, gravity takes over, and you fall off.</li>
 * </ul>
 *
 * <h2>What replaces it</h2>
 * This class tracks a real attachment: a surface {@link Direction}, chosen from every face actually
 * touching the player's hull, kept sticky across ticks, and released only after a grace period. All
 * movement is then expressed <em>relative to that surface</em>, so the same code drives a vertical
 * wall, a ceiling and the transition between them.
 *
 * <p>Movement itself is applied by {@code SpiderClimbMovement} (client source set), which the
 * owning client runs for its own player -- the same division of labour Super Speed already uses in
 * {@code LocalPlayerMixin} (the client simulates and reports positions; the server's movement
 * tolerance accepts them, and every ability, cost and permission is still decided server-side). The
 * server runs {@link #serverTick} to keep the synced {@link SpiderManState#climbState} -- which is
 * what other players' clients render from -- and to zero fall distance while attached.
 */
public final class SpiderClimb {

	/**
	 * How a given player is allowed to stick to surfaces right now. Spider Adhesion gets the slower,
	 * plainer version; Spider-Man gets the full one.
	 *
	 * @param climbSpeed   blocks per tick along the surface
	 * @param ceilings     whether the player may hold onto and cross a ceiling
	 * @param graceTicks   how long an attachment survives with no surface in reach (corners, block
	 *                     boundaries, a slab lip) before it is genuinely released
	 * @param stickyRadius how far from the hull a surface still counts as touchable
	 */
	public record Profile(double climbSpeed, boolean ceilings, int graceTicks, double stickyRadius) {
		public static final Profile SPIDER_MAN = new Profile(0.235, true, 8, 0.34);
		public static final Profile ADHESION = new Profile(0.145, true, 5, 0.22);
	}

	/** Vertical reach used when probing for a ceiling above the player's head. */
	private static final double CEILING_PROBE = 0.30;

	/** v0.6.17: whether the player has the toggleable wall-crawl mode on (Spider-Man's slot 6). */
	public static boolean wallCrawlEnabled(Player player) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.hasPower && s.wallCrawlEnabled;
	}

	private SpiderClimb() {
	}

	// ---------------- eligibility ----------------

	/**
	 * The adhesion profile in effect for this player, or {@code null} if they cannot stick to anything
	 * right now. Readable on both sides: Spider-Man reads the synced Hero-Class attachment, Spider
	 * Adhesion reads the synced experimental state exactly as the old implementation did, so existing
	 * saves keep working untouched.
	 */
	public static Profile profile(Player player) {
		if (player == null || player.isSpectator() || player.getAbilities().flying) {
			return null;
		}
		if (SpiderMan.hasPower(player)) {
			return Profile.SPIDER_MAN;
		}
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null || !st.ownedPowers.contains(SpiderMan.SPIDER_ADHESION_KEY)) {
			return null;
		}
		// Adhesion Mode or Wall Grip must be toggled on. v0.9.3: those toggles (like every Experimental
		// Tier mode) stay live even while another owned power holds the six slots, so this checks
		// ownership, not selection.
		boolean toggled = st.activeToggles.contains(SpiderMan.SPIDER_ADHESION_KEY + "/adhesion_mode")
				|| st.activeToggles.contains(SpiderMan.SPIDER_ADHESION_KEY + "/wall_grip");
		return toggled ? Profile.ADHESION : null;
	}

	/**
	 * Situations where adhesion must simply not engage, whatever the player has. Fluids, vehicles,
	 * elytra and creative flight all own the player's movement already, and fighting them produces
	 * exactly the rubber-banding the design forbids.
	 *
	 * <p><b>Standing on the ground counts.</b> Spider-Man's adhesion is a passive with no toggle, so
	 * without this rule simply walking down a corridor or past a house would glue him to the nearest
	 * wall and stop him walking at all. On your feet you walk; leave the ground with a surface in
	 * reach and you are on it. That also settles the mirror case -- standing under an overhang does
	 * not drag you onto the ceiling -- and makes climbing down to a floor end by itself.
	 *
	 * <p>A live swing counts too: brushing a wall mid-arc must not silently cancel the swing. Release
	 * the line first and the wall is there waiting, which is exactly the hand-over the design asks
	 * for.
	 */
	public static boolean blocked(Player player) {
		return player.isPassenger()
				|| player.isFallFlying()
				|| player.getAbilities().flying
				|| player.isSleeping()
				|| player.isInWater()
				|| player.isInLava()
				|| player.isSwimming()
				|| player.onGround()
				|| SpiderSwing.isSwinging(player);
	}

	// ---------------- surface detection ----------------

	/**
	 * Pick the surface the player should be attached to this tick, preferring the one they are
	 * already on so an attachment never flickers between two touching faces.
	 *
	 * @return the direction from the player <em>into</em> the surface, or {@code null} if nothing is
	 *         in reach
	 */
	public static Direction findSurface(Player player, Profile profile, Direction preferred) {
		Level level = player.level();
		AABB box = player.getBoundingBox();

		// A ceiling counts only when it is genuinely overhead -- probing UP from the hull. Preferred
		// first so an established ceiling crawl is never stolen by a wall the player brushes past.
		if (preferred != null && touching(level, box, preferred, profile.stickyRadius(), profile)) {
			return preferred;
		}
		if (profile.ceilings() && touching(level, box, Direction.UP, CEILING_PROBE, profile)) {
			return Direction.UP;
		}
		// Otherwise the wall the player is facing most directly wins, so "walk at a wall and stick"
		// does what it looks like.
		Direction best = horizontalSurface(player, level, box, profile);
		if (best != null) {
			return best;
		}
		// Second pass, slightly higher. This is what makes the ceiling-becomes-wall case work: coming
		// off the end of an overhang, the wall that continues upward starts at or above the ceiling
		// plane and is not yet beside the player's hull, so the pass above cannot see it.
		return horizontalSurface(player, level, box.move(0.0, 0.4, 0.0), profile);
	}

	private static Direction horizontalSurface(Player player, Level level, AABB box, Profile profile) {
		Direction best = null;
		double bestDot = -2.0;
		Vec3 look = player.getLookAngle();
		for (Direction d : Direction.Plane.HORIZONTAL) {
			if (!touching(level, box, d, profile.stickyRadius(), profile)) {
				continue;
			}
			double dot = look.x * d.getStepX() + look.z * d.getStepZ();
			if (dot > bestDot) {
				bestDot = dot;
				best = d;
			}
		}
		return best;
	}

	/** True when there is climbable geometry within {@code reach} of the hull in direction {@code d}. */
	public static boolean touching(Level level, AABB box, Direction d, double reach, Profile profile) {
		AABB probe = switch (d) {
			case UP -> new AABB(box.minX + 0.02, box.maxY, box.minZ + 0.02,
					box.maxX - 0.02, box.maxY + reach, box.maxZ - 0.02);
			case DOWN -> new AABB(box.minX + 0.02, box.minY - reach, box.minZ + 0.02,
					box.maxX - 0.02, box.minY, box.maxZ - 0.02);
			case NORTH -> new AABB(box.minX + 0.02, box.minY + 0.05, box.minZ - reach,
					box.maxX - 0.02, box.maxY - 0.05, box.minZ);
			case SOUTH -> new AABB(box.minX + 0.02, box.minY + 0.05, box.maxZ,
					box.maxX - 0.02, box.maxY - 0.05, box.maxZ + reach);
			case WEST -> new AABB(box.minX - reach, box.minY + 0.05, box.minZ + 0.02,
					box.minX, box.maxY - 0.05, box.maxZ - 0.02);
			case EAST -> new AABB(box.maxX, box.minY + 0.05, box.minZ + 0.02,
					box.maxX + reach, box.maxY - 0.05, box.maxZ + 0.02);
		};
		return hasGrip(level, probe);
	}

	/**
	 * Whether anything inside {@code probe} can be gripped. Deliberately generous: <em>any</em>
	 * collision geometry counts, which is what makes slabs, stairs, fences, logs, leaves with
	 * collision and irregular cliff faces work without a per-block allow-list. Passable decoration
	 * (grass, torches) has no collision shape and correctly does not hold you up.
	 */
	private static boolean hasGrip(Level level, AABB probe) {
		int minX = (int) Math.floor(probe.minX);
		int maxX = (int) Math.floor(probe.maxX - 1.0E-7);
		int minY = (int) Math.floor(probe.minY);
		int maxY = (int) Math.floor(probe.maxY - 1.0E-7);
		int minZ = (int) Math.floor(probe.minZ);
		int maxZ = (int) Math.floor(probe.maxZ - 1.0E-7);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = minX; x <= maxX; x++) {
			for (int y = minY; y <= maxY; y++) {
				for (int z = minZ; z <= maxZ; z++) {
					pos.set(x, y, z);
					if (!level.hasChunkAt(pos)) {
						continue;
					}
					BlockState state = level.getBlockState(pos);
					if (state.isAir()) {
						continue;
					}
					VoxelShape shape = state.getCollisionShape(level, pos);
					if (shape.isEmpty()) {
						continue;
					}
					if (shape.bounds().move(x, y, z).intersects(probe)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	// ---------------- attachment bookkeeping (runs on whichever side owns the player) ----------------

	/**
	 * Advance the attachment for one tick and report the surface the player is stuck to, or
	 * {@code null}. Sticky: an established attachment is kept for {@link Profile#graceTicks} after the
	 * surface goes out of reach, which is what carries the player around inside corners, over block
	 * boundaries and across the lip of a slab instead of dropping them.
	 */
	public static Direction updateAttachment(Player player, Profile profile, SpiderClimbLocal local) {
		if (profile == null || blocked(player) || local.lockedOut()) {
			// Landing, entering water, going into a swing or losing the power all end the grab outright:
			// re-sticking then needs a fresh double-tap-jump, which is exactly the control the player asked
			// for. A leap's brief lock-out is the one case that keeps the intent -- SpiderClimbActions
			// clears it there itself.
			if (profile == null || blocked(player)) {
				local.setGrabIntent(false);
			}
			local.reset();
			return null;
		}
		// The player has to be in a wall-crawl mode to stick (v0.9.3): either the toggleable wall-crawl
		// mode being on (Spider-Man's slot 6, v0.6.17), the Spider Adhesion power's Adhesion Mode /
		// Wall Grip toggle being on (that is the only way {@code profile} is {@link Profile#ADHESION}
		// at all), or a one-off grab intent set by another ability such as a sneak + Web Zip. With no
		// mode on and no intent, brushing a wall while running or jumping past a building does nothing.
		// The old double-tap-jump grab gesture is gone -- it fought with Spider-Man's own double jump.
		boolean autoStick = wallCrawlEnabled(player) || profile == Profile.ADHESION;
		if (!local.grabIntent() && !autoStick) {
			local.reset();
			return null;
		}

		Direction current = local.face();
		Direction found = findSurface(player, profile, current);

		if (found != null) {
			// Changing face is a transition (wall to ceiling, ceiling to wall, wall to wall around a
			// corner). Recorded so the movement code can smooth the hand-over instead of snapping.
			if (current != null && current != found) {
				local.markTransition();
			}
			local.attach(found);
			return found;
		}

		if (current != null && local.grace() < profile.graceTicks()) {
			local.tickGrace();
			return current;
		}
		local.reset();
		return null;
	}

	/**
	 * The one thing the <em>server</em> must own: the synced climb state other players render from,
	 * plus fall-distance upkeep so hanging on a wall can never bank up a killing fall. Cheap -- at
	 * most a handful of small AABB probes, and it only writes the attachment when the mode changes.
	 */
	public static void serverTick(net.minecraft.server.level.ServerPlayer player) {
		Profile profile = profile(player);
		if (profile == null) {
			// Nothing to probe and, for the overwhelming majority of players, nothing to clear either.
			SpiderManState existing = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
			if (existing != null && existing.climbState != 0) {
				SpiderManState c = existing.copy();
				c.climbState = 0;
				SpiderMan.save(player, c);
			}
			return;
		}
		SpiderClimbLocal local = player.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL);
		local.tickLock();
		local.tickTransition();
		Direction face = updateAttachment(player, profile, local);

		int mode = SpiderManState.CLIMB_NONE;
		if (face != null) {
			mode = face == Direction.UP ? SpiderManState.CLIMB_CEILING : SpiderManState.CLIMB_WALL;
			player.resetFallDistance();
		}
		// Written for Spider Adhesion too, not just the Hero Class: the state is only touched on a
		// genuine transition, and it is what lets every other client draw an adhered player the right
		// way up regardless of which of the two powers is holding them there.
		int packed = SpiderManState.packClimb(mode, face == null ? 0 : face.get3DDataValue());
		SpiderManState s = SpiderMan.state(player);
		if (s.climbState != packed) {
			SpiderManState c = s.copy();
			c.climbState = packed;
			SpiderMan.save(player, c);
		}
	}

	/**
	 * True when {@code player} is currently stuck to a ceiling. Reads the <em>synced</em> state, so it
	 * answers correctly for anybody a client can see -- which is what the upside-down pose needs.
	 */
	public static boolean onCeiling(Player player) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.climbMode() == SpiderManState.CLIMB_CEILING;
	}

	/**
	 * True when {@code player} is stuck to any surface, according to the copy of the engine running on
	 * <em>this</em> side. The local attachment is the authority for the side that owns the player;
	 * the synced field is the fallback for anyone else.
	 */
	public static boolean attached(Player player) {
		SpiderClimbLocal local = player.getAttachedOrElse(ModAttachments.SPIDER_CLIMB_LOCAL, null);
		if (local != null && local.face() != null) {
			return true;
		}
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.climbMode() != SpiderManState.CLIMB_NONE;
	}

	/**
	 * The player double-tapped jump against a surface: arm adhesion. Re-validated here -- a client that
	 * sends this without the power, or while on the ground, gets nothing (the next
	 * {@link #updateAttachment} clears an intent that cannot apply anyway).
	 */
	public static void requestGrab(net.minecraft.server.level.ServerPlayer player) {
		if (profile(player) == null) {
			return;
		}
		player.getAttachedOrCreate(ModAttachments.SPIDER_CLIMB_LOCAL).setGrabIntent(true);
	}

	/** The player double-tapped sneak: let go, with a short lock-out so grace does not immediately re-grab. */
	public static void requestRelease(net.minecraft.server.level.ServerPlayer player) {
		SpiderClimbLocal local = player.getAttachedOrElse(ModAttachments.SPIDER_CLIMB_LOCAL, null);
		if (local != null) {
			local.setGrabIntent(false);
			local.lockOut(6);
		}
	}

	/** True when stuck to a vertical surface specifically (never a ceiling). */
	public static boolean attachedToWall(Player player) {
		SpiderClimbLocal local = player.getAttachedOrElse(ModAttachments.SPIDER_CLIMB_LOCAL, null);
		if (local != null && local.face() != null) {
			return local.face() != Direction.UP;
		}
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.climbMode() == SpiderManState.CLIMB_WALL;
	}
}
