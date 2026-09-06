package com.projecthero.mod.spider;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.spider.data.SpiderManState;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Web swinging: the anchor lifecycle and the rope model.
 *
 * <h2>Division of labour</h2>
 * The rope maths in {@link #applyRope} is deterministic and lives here, in common code, so the owning
 * client can run it on its own player every tick and get movement with no round-trip latency -- the
 * same arrangement Super Speed already uses (see {@code LocalPlayerMixin}). The server does not push
 * velocity packets at a swinging player, because doing that once per tick is precisely what produces
 * rubber-banding.
 *
 * <p>The server still decides everything that matters. It runs its own {@link SpiderAnchorSearch} on
 * key-down and writes the authoritative anchor into the synced state; it charges the web reserve; it
 * enforces the artificial-altitude rule in {@link #serverTick}; and it drops the line the moment the
 * anchor stops being valid. A client that lies about swinging gets no reserve spent and no anchor
 * synced, so nobody else sees a web and nothing about it persists.
 *
 * <h2>Why an altitude rule exists</h2>
 * Real anchors may take the player as high as the terrain does -- that is the reward for swinging
 * through a mountain range or a city. Fabricated anchors may not, or repeatedly firing one straight
 * up would be free flight. Each fabricated sequence records the altitude it began at and stops
 * <em>adding</em> lift once the player is {@link #AIR_SWING_CEILING} above it. Crucially it never
 * slams the player down or cuts their speed: horizontal swinging continues exactly as before, the
 * arcs simply stop climbing.
 */
public final class SpiderSwing {
	/** How far a chain of fabricated anchors may raise the player above where the chain started. */
	public static final double AIR_SWING_CEILING = 12.0;
	/** Lift fades out across the last few blocks rather than stopping dead. */
	private static final double CEILING_FADE = 4.0;

	/** Rope may be reeled in / let out between these bounds. */
	private static final double MIN_ROPE = 4.0;
	private static final double MAX_ROPE = SpiderAnchorSearch.MAX_RANGE + 2.0;

	private SpiderSwing() {
	}

	// ---------------- lifecycle (server) ----------------

	/**
	 * Fire a web. Picks an anchor, charges the reserve and writes the synced state the client and
	 * every viewer render from. A no-op when the player is already swinging or has no webbing left.
	 */
	public static boolean fire(ServerPlayer player) {
		SpiderManState s = SpiderMan.state(player);
		if (s.swinging) {
			return false;
		}
		if (!SpiderWebReserve.spend(player, SpiderWebReserve.COST_SWING_FIRE, false)) {
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.spider_man.no_webbing"), true);
			return false;
		}
		SpiderAnchorSearch.Anchor anchor = SpiderAnchorSearch.find(player);

		SpiderManState c = SpiderMan.state(player).copy();
		c.swinging = true;
		c.swingHandRight = !c.swingHandRight; // alternate hands each shot for the swing animation
		c.anchorX = anchor.pos().x;
		c.anchorY = anchor.pos().y;
		c.anchorZ = anchor.pos().z;
		c.artificialAnchor = anchor.artificial();
		c.ropeLength = Math.max(MIN_ROPE, Math.min(MAX_ROPE, anchor.pos().distanceTo(player.getEyePosition())));
		// A fabricated sequence measures itself from where it began. A real anchor resets the baseline
		// to here, which is how climbing genuine terrain legitimately raises the ceiling for later
		// fabricated swings.
		if (!anchor.artificial() || !isAirSequenceRunning(player)) {
			c.airSwingBaselineY = player.getY();
		}
		SpiderMan.save(player, c);

		grantFloatTolerance(player, true);

		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.55f, 1.7f);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.pos().x, anchor.pos().y, anchor.pos().z,
				4, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/**
	 * Release the line. Velocity is deliberately untouched -- the whole point of a swing is that
	 * letting go at the right moment launches you, so a well-timed release keeps every bit of the
	 * speed the arc built.
	 */
	public static void detach(ServerPlayer player, boolean playSound) {
		SpiderManState s = SpiderMan.state(player);
		grantFloatTolerance(player, false);
		if (!s.swinging) {
			return;
		}
		SpiderManState c = s.copy();
		c.swinging = false;
		c.artificialAnchor = false;
		// Stamp the release so the traversal fall grace in SpiderPassives covers the landing this
		// swing was aimed at. It also holds reserve regeneration back for the usual moment, which is
		// the right behaviour for a line that has just been spending.
		c.lastWebUseTick = player.level().getGameTime();
		SpiderMan.save(player, c);
		if (playSound) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.LEASH_KNOT_BREAK, SoundSource.PLAYERS, 0.4f, 1.6f);
		}
	}

	/** True while the player is inside an unbroken run of fabricated swings. */
	private static boolean isAirSequenceRunning(ServerPlayer player) {
		SpiderManState s = SpiderMan.state(player);
		return s.artificialAnchor || player.level().getGameTime() - s.lastWebUseTick < 60;
	}

	// ---------------- per-tick (server) ----------------

	/**
	 * Server upkeep for an attached line: charge the drain, drop the line if the anchor has become
	 * nonsense (block mined, player teleported, dimension changed, rope stretched far past its
	 * length), and keep the anti-float tolerance alive.
	 */
	public static void serverTick(ServerPlayer player) {
		SpiderManState s = SpiderMan.state(player);
		if (!s.swinging) {
			// v0.6.17: hand the anti-float mayfly grant straight back the moment a swing is not the
			// thing keeping the player up. Without this a grant that outlived its swing (or a stray
			// one) left vanilla's double-tap-jump free to toggle creative flight -- "double jumping
			// sometimes makes you fly". grantFloatTolerance(false) only revokes if nothing else in the
			// mod still wants mayfly, so this is safe for a Thor / Iron Man Spider-Man too.
			if (!SpiderClimb.attached(player) && player.getAbilities().mayfly && !player.getAbilities().instabuild) {
				grantFloatTolerance(player, false);
			}
			return;
		}
		Vec3 anchor = new Vec3(s.anchorX, s.anchorY, s.anchorZ);
		double dist = anchor.distanceTo(player.getEyePosition());
		if (dist > MAX_ROPE + 12.0) {
			detach(player, false);
			return;
		}
		// A real anchor whose block has been removed should not keep holding a web up.
		if (!s.artificialAnchor && player.tickCount % 10 == 0 && !anchorStillSolid(player, anchor)) {
			detach(player, true);
			return;
		}
		if (!SpiderWebReserve.spend(player, SpiderWebReserve.DRAIN_SWING_PER_TICK, false)) {
			detach(player, true);
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.spider_man.no_webbing"), true);
			return;
		}
		// Server-side backstop for the fabricated-anchor altitude rule. The client fades its own lift
		// out smoothly well before this, so a legitimate player never reaches it; a client that
		// ignored that simply loses the line.
		if (s.artificialAnchor && player.getY() - s.airSwingBaselineY > AIR_SWING_CEILING + 4.0) {
			detach(player, true);
			return;
		}
		player.resetFallDistance();
		grantFloatTolerance(player, true);

		// One web particle every few ticks along the line, so onlookers can see the web without the
		// server drawing a hundred particles a second.
		if (player.tickCount % 4 == 0 && player.level() instanceof ServerLevel level) {
			Vec3 mid = player.getEyePosition().lerp(anchor, 0.5);
			level.sendParticles(ParticleTypes.ITEM_COBWEB, mid.x, mid.y, mid.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
	}

	private static boolean anchorStillSolid(ServerPlayer player, Vec3 anchor) {
		// Clip from just short of the anchor to just past it: if there is still geometry there the
		// clip stops, if the block is gone it passes straight through.
		Vec3 eye = player.getEyePosition();
		Vec3 dir = anchor.subtract(eye);
		if (dir.lengthSqr() < 1.0E-4) {
			return true;
		}
		Vec3 unit = dir.normalize();
		Vec3 from = anchor.subtract(unit.scale(0.6));
		Vec3 to = anchor.add(unit.scale(0.6));
		return player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, player)).getType() == HitResult.Type.BLOCK;
	}

	/**
	 * Vanilla disconnects a player who spends four straight seconds airborne with no downward motion
	 * and no blocks near them ("flying is not enabled on this server") -- which a good swing over open
	 * ground looks exactly like. Granting {@code mayfly} for the duration suppresses that check
	 * without changing any physics; {@code flying} itself is forced off every tick, so this never
	 * turns into creative flight.
	 */
	static void grantFloatTolerance(ServerPlayer player, boolean on) {
		if (player.getAbilities().instabuild || player.isSpectator()) {
			return;
		}
		if (on) {
			if (!player.getAbilities().mayfly) {
				player.getAbilities().mayfly = true;
				player.onUpdateAbilities();
			}
			if (player.getAbilities().flying) {
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
			return;
		}
		// Only give the permission back if nothing else in the mod is relying on it.
		if (player.getAbilities().mayfly
				&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(player)
				&& !com.projecthero.mod.power.ThorPowers.isFlying(player)
				&& !player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)
				&& !player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			player.getAbilities().mayfly = false;
			player.getAbilities().flying = false;
			player.onUpdateAbilities();
		}
	}

	/** Join / death / dimension-change safety net for the tolerance grant above. */
	public static void clearFlightGrant(ServerPlayer player) {
		grantFloatTolerance(player, false);
	}

	// ---------------- the rope model (shared; the owning client runs this every tick) ----------------

	/**
	 * One tick of rope physics, returning the velocity the player should carry into the move.
	 *
	 * <p>The model is a soft pendulum rather than a rigid rope: outward radial velocity is removed
	 * once the line is taut and a spring pulls the player back toward rope length, which gives the arc
	 * its shape while never producing the instant velocity reversal a hard constraint would. Gravity
	 * still comes from vanilla, so the drop into the bottom of a swing is genuinely gravity doing it.
	 *
	 * <p>On top of that sits the assistance the design asks for -- a nudge toward where the player is
	 * facing, extra push through the bottom of the arc, and damping of the sideways wobble that a
	 * bare pendulum develops. It is assistance, not a script: timing and momentum still decide how far
	 * a swing carries.
	 *
	 * @param input packed movement intent -- see {@link SpiderSwingInput}
	 */
	public static Vec3 applyRope(Player player, Vec3 anchor, double ropeLength, Vec3 velocity, int input,
			boolean artificial, double baselineY) {
		Vec3 eye = player.getEyePosition();
		Vec3 toAnchor = anchor.subtract(eye);
		double dist = toAnchor.length();
		if (dist < 1.0E-3) {
			return velocity;
		}
		Vec3 up = toAnchor.scale(1.0 / dist);
		Vec3 v = velocity;

		// --- rope tension ---
		if (dist > ropeLength) {
			double radial = v.dot(up);
			if (radial < 0.0) {
				v = v.subtract(up.scale(radial)); // cancel the part pulling away from the anchor
			}
			double overshoot = Math.min(dist - ropeLength, 6.0);
			v = v.add(up.scale(overshoot * 0.14));
		}

		// --- rider input ---
		// Black Suit Spider-Man: web swing acceleration +15% (spec) -- applies to the rider's own
		// steering input, not the passive rope physics above.
		double accel = com.projecthero.mod.symbiote.Symbiote.isActive(player)
				&& com.projecthero.mod.symbiote.SymbioteHostType.of(player) == com.projecthero.mod.symbiote.SymbioteHostType.SPIDER_MAN
				? 1.15 : 1.0;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);

		if (SpiderSwingInput.forward(input)) {
			v = v.add(flat.scale(0.100 * accel));
		}
		if (SpiderSwingInput.back(input)) {
			// braking, not reversal -- shed speed rather than flipping the arc inside out
			v = new Vec3(v.x * 0.93, v.y, v.z * 0.93);
		}
		if (SpiderSwingInput.left(input)) {
			v = v.add(side.scale(-0.040 * accel));
		}
		if (SpiderSwingInput.right(input)) {
			v = v.add(side.scale(0.040 * accel));
		}

		// --- swing assist ---
		// Push through the bottom of the arc, where a real pendulum is fastest and where a player
		// most wants the speed. Strongest when the anchor is directly overhead.
		double overhead = Math.max(0.0, up.y);
		double horizontalSpeed = Math.sqrt(v.x * v.x + v.z * v.z);
		if (horizontalSpeed > 0.05) {
			Vec3 tangentDir = new Vec3(v.x, 0, v.z).normalize();
			// v0.9.13: another push through the bottom of the arc -- swinging was still reading as
			// sluggish next to the rest of the traversal kit, so a chain of swings builds momentum
			// noticeably faster now.
			v = v.add(tangentDir.scale(0.042 * overhead));
			// favour the facing direction slightly, so the swing goes where the player is looking
			// instead of drifting off on whatever heading it happened to start with
			if (flat.lengthSqr() > 0.0) {
				double align = tangentDir.dot(flat);
				if (align > 0.0) {
					v = v.add(flat.scale(0.022 * align));
				}
			}
		}
		// Damp the lateral wobble a bare pendulum builds up, so swings do not spin.
		Vec3 lateral = new Vec3(v.x, 0, v.z);
		if (flat.lengthSqr() > 0.0 && lateral.length() > 0.05) {
			double sideways = lateral.dot(side);
			v = v.subtract(side.scale(sideways * 0.10));
		}

		// --- fabricated-anchor altitude rule ---
		if (artificial && v.y > 0.0) {
			double gained = player.getY() - baselineY;
			if (gained > AIR_SWING_CEILING) {
				v = new Vec3(v.x, 0.0, v.z);
			} else if (gained > AIR_SWING_CEILING - CEILING_FADE) {
				double fade = (AIR_SWING_CEILING - gained) / CEILING_FADE;
				v = new Vec3(v.x, v.y * fade, v.z);
			}
		}

		// Terminal guard: keep a swing fast but never absurd (v0.6.19: 2.4 -> 2.7; v0.9.13: 2.7 -> 3.2 to
		// go with the stronger push above).
		double speed = v.length();
		if (speed > 3.2) {
			v = v.scale(3.2 / speed);
		}
		return v;
	}

	/** New rope length after this tick's reel-in (jump) / pay-out (sneak). */
	public static double adjustRope(double ropeLength, double distance, int input) {
		double r = ropeLength;
		if (SpiderSwingInput.jump(input)) {
			r -= 0.22;
		}
		if (SpiderSwingInput.sneak(input)) {
			r += 0.22;
		}
		// never let the rope trail so far behind the player that the line goes slack forever
		r = Math.min(r, distance + 3.0);
		return Math.max(MIN_ROPE, Math.min(MAX_ROPE, r));
	}

	public static boolean isSwinging(Player player) {
		SpiderManState s = player.getAttachedOrElse(ModAttachments.SPIDER_MAN_STATE, null);
		return s != null && s.swinging;
	}
}
