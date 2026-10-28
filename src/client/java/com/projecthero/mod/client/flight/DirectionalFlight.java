package com.projecthero.mod.client.flight;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.flight.DirectionalFlightModel;
import com.projecthero.mod.flight.DirectionalFlightModel.Tune;
import com.projecthero.mod.flight.PhysicsFlightModel;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.kryptonian.Kryptonian;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.16: directional flight for <b>every</b> flight in the mod, local player only (explicit user request -- "make all
 * flights in the mod the new directional flight system", "fly backwards with S ... the same as green lantern flight").
 * The Green Lantern Ring Flight controller (v0.13.21) and the Kryptonian one (v0.14.8) folded into one, parameterised per
 * flight through {@link DirectionalFlightModel.Tune}. While any mod flight is engaged it replaces vanilla's
 * creative-flight travel step:
 * <ul>
 *   <li><b>W / S</b> fly forward / backward along the full 3D look vector (look up or down to climb or dive);</li>
 *   <li><b>A / D</b> slide sideways, <b>Space / Sneak</b> rise and sink straight up and down;</li>
 *   <li><b>no input</b> eases to a dead hover -- no gravity, no drift.</li>
 * </ul>
 * The velocity eases toward the wanted one from this class's own record of last tick's velocity (not
 * {@code getDeltaMovement()}, which vanilla's own creative Space/Sneak nudge has already been added to), so steering is
 * smooth. A big outside push (a knockback, a dash, the server launching the player) is adopted and carried for a moment
 * instead of fought.
 *
 * <p>The client moves itself and the server accepts the result, like every other client-simulated movement in this mod;
 * every flight's activation gesture, energy / stamina upkeep, landing rules and effects stay server-side and unchanged.
 *
 * <p>Not flights, so left alone: Earth Swim and Phase (vanilla's flight flags only to move through solid ground),
 * Light's held Sparkle dash (a scripted server drive), and every glide.
 */
public final class DirectionalFlight {
	private static Vec3 velocity;
	private static int pushTicks;
	/** Identity of the LocalPlayer the record above belongs to -- a new world / relog is a fresh player. */
	private static int playerIdentity;

	/** The server sent this player a velocity since the last travel step (see {@code DirectionalFlightMotionMixin}). */
	private static volatile boolean serverPushed;

	private DirectionalFlight() {
	}

	private static void reset() {
		velocity = null;
		pushTicks = 0;
	}

	/** True while a mod flight is driving the local player (its velocity record is live). */
	public static boolean engaged() {
		return velocity != null;
	}

	/** Called when a {@code ClientboundSetEntityMotionPacket} for the local player has been applied. */
	public static void onServerMotion() {
		serverPushed = true;
	}

	/** @return true if a flight moved the player this tick (vanilla's travel step must then be skipped). */
	public static boolean travel(LocalPlayer player) {
		boolean serverPush = serverPushed;
		serverPushed = false;
		int identity = System.identityHashCode(player);
		if (identity != playerIdentity) {
			playerIdentity = identity;
			reset();
		}
		Tune tune = player.getAbilities().flying && !player.isPassenger() && !player.isSpectator() && !player.isFallFlying()
				? tune(player) : null;
		if (tune == null) {
			reset();
			return false;
		}
		// v0.16.5 Flight's Slipstream: a squadmate flying in its wake goes 50% faster, whatever flight they fly
		tune = com.projecthero.mod.client.mutation.FlightClient.drafting(player, tune);

		boolean sneak = player.input.shiftKeyDown;
		boolean jump = player.input.jumping;
		Vec3 current = player.getDeltaMovement();
		// vanilla's LocalPlayer.aiStep has already nudged the vertical velocity by +-flyingSpeed x 3 for Space / Sneak
		double vanillaNudge = ((jump ? 1 : 0) - (sneak ? 1 : 0)) * player.getAbilities().getFlyingSpeed() * 3.0;
		boolean pushedNow = false;
		if (velocity == null) {
			velocity = current;
		} else if (serverPush || tune.adoptPushes() && DirectionalFlightModel.outsidePush(velocity, current, vanillaNudge)) {
			// the server set this player's velocity this tick (a dash, a dive, a launch, a knockback) -- or something
			// else shoved it hard: go with it this tick exactly as vanilla would, then carry it for a moment
			velocity = DirectionalFlightModel.adoptedPush(current);
			pushTicks = tune.pushCarryTicks();
			pushedNow = true;
		}

		boolean boosting = tune.sneakBoosts() && sneak && player.isSprinting();
		float forward = player.input.forwardImpulse;
		float strafe = player.input.leftImpulse;
		int vertical = DirectionalFlightModel.verticalInput(jump, sneak, boosting);

		Vec3 wanted = DirectionalFlightModel.wantedVelocity(player.getLookAngle(), player.getYRot(), forward, strafe,
				vertical, tune);
		boolean steering = DirectionalFlightModel.steering(forward, strafe, vertical, tune);
		boolean carrying = pushTicks > 0;
		if (carrying && !pushedNow) {
			pushTicks--;
		}
		// v0.16.2: Physics Flight (the , menu) -- momentum, throttle, dive / climb and banked turns instead of the eased
		// directional step; A / D belong to PhysicsFlight's yaw / roll there, so no strafe
		Vec3 next = pushedNow ? velocity
				: FlightSettings.physics()
						? PhysicsFlightModel.step(velocity, player.getLookAngle(), forward, vertical, tune, carrying)
						: DirectionalFlightModel.step(velocity, wanted, steering, tune, carrying);

		player.setDeltaMovement(next);
		player.move(MoverType.SELF, next);
		applyHoverFloor(player);
		// Entity.move zeroes whichever components collided, so this is the velocity that actually happened.
		velocity = player.getDeltaMovement();
		player.resetFallDistance();
		return true;
	}

	/** The engaged flight's numbers this tick, or null if no mod flight is engaged (vanilla / creative flight). */
	private static Tune tune(LocalPlayer player) {
		if (com.projecthero.mod.hero.power.p18.DensityManipulationHandlers.phasing(player)
				|| com.projecthero.mod.hero.power.p05.GeokinesisHandlers.earthSwimming(player)
				|| resource(player, "power_15_invisibility_light_manipulation/sparkling") > 0.5f) {
			return null;
		}
		boolean sprint = player.isSprinting();
		float flyingSpeed = player.getAbilities().getFlyingSpeed();

		// Green Lantern Ring Flight -- Sneak+Sprint is Boost
		if (player.getAttachedOrElse(ModAttachments.GREEN_LANTERN_FLYING, false)) {
			return DirectionalFlightModel.greenLantern(flyingSpeed, sprint, player.input.shiftKeyDown && sprint);
		}
		// Kryptonian flight -- X is Flight Boost
		if (Kryptonian.isFlying(player)) {
			var ks = player.getAttachedOrElse(ModAttachments.KRYPTONIAN_STATE, null);
			return DirectionalFlightModel.kryptonian(sprint, ks != null && ks.flightBoost);
		}
		// v0.15.13: Nova flight -- 20 b/s, 40 sprinting
		if (com.projecthero.mod.nova.Nova.isFlying(player)) {
			return DirectionalFlightModel.nova(sprint);
		}
		// v0.17.1: the Falcon's jet flight -- 19 b/s, 35 sprinting, 40 along the look while X burns the jets
		if (com.projecthero.mod.falcon.Falcon.flying(player)) {
			return com.projecthero.mod.falcon.FalconFlight.tune(sprint, com.projecthero.mod.falcon.Falcon.boosting(player));
		}
		// v0.17.1: the Silver Surfer's board -- 60 b/s, 110 sprinting, 150 at Light-Speed
		if (com.projecthero.mod.silversurfer.SilverSurfer.surfing(player)) {
			return com.projecthero.mod.silversurfer.SilverSurferFlight.tune(sprint, com.projecthero.mod.silversurfer.SilverSurfer.lightSpeed(player));
		}
		// v0.16.5: Cyborg's Jet Flight (double-tap jump) -- 18 b/s, 33 sprinting
		if (com.projecthero.mod.cyborg.Cyborg.flying(player)) {
			return DirectionalFlightModel.cyborg(flyingSpeed, sprint);
		}
		// v0.17.1 (user): Blue Beetle's wings, Hawk Moth's Swarm Flight and the Omnitrix's flying aliens, on the shared model
		if (com.projecthero.mod.bluebeetle.BlueBeetle.flying(player)) {
			return DirectionalFlightModel.cruise(flyingSpeed, sprint, DirectionalFlightModel.BLUE_BEETLE_CRUISE_BPS,
					DirectionalFlightModel.BLUE_BEETLE_SPRINT_BPS);
		}
		if (player.getAbilities().flying && !player.getAbilities().instabuild) {
			if (com.projecthero.mod.miraculous.hawkmoth.HawkMothKit.flying(player)) {
				return DirectionalFlightModel.cruise(flyingSpeed, sprint, DirectionalFlightModel.HAWK_MOTH_CRUISE_BPS,
						DirectionalFlightModel.HAWK_MOTH_SPRINT_BPS);
			}
			switch (com.projecthero.mod.omnitrix.Omnitrix.active(player)) {
				case "heatblast" -> {
					return DirectionalFlightModel.cruise(flyingSpeed, sprint, DirectionalFlightModel.HEATBLAST_CRUISE_BPS,
							DirectionalFlightModel.HEATBLAST_SPRINT_BPS);
				}
				case "stinkfly" -> {
					return DirectionalFlightModel.cruise(flyingSpeed, sprint, DirectionalFlightModel.STINKFLY_CRUISE_BPS,
							DirectionalFlightModel.STINKFLY_SPRINT_BPS);
				}
				case "ghostfreak" -> {
					return DirectionalFlightModel.cruise(flyingSpeed, sprint, DirectionalFlightModel.GHOSTFREAK_CRUISE_BPS,
							DirectionalFlightModel.GHOSTFREAK_SPRINT_BPS);
				}
				default -> {
				}
			}
		}
		// Iron Man suit flight (double-tap jump, Mark 1 timed flight, supersonic burst)
		if (player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			IronManSuit suit = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof IronManArmorItem piece
					? IronManSuits.byId(piece.suitId()) : null;
			TonyStarkState ts = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
			boolean supersonic = ts != null && ts.supersonicUntil > player.level().getGameTime();
			// v0.14.26: boots alone can lift you but not sprint-fly -- sprint flight needs the chestplate too
			if (!(player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof IronManArmorItem)) {
				sprint = false;
			}
			if (suit == null) {
				return DirectionalFlightModel.ironMan(1.0f, 0.08f, 0.0, sprint, supersonic);
			}
			// v0.14.27: per-suit fixed cruise (Mark 1: 8 b/s), no sprint flight, and the Mark 2 supersonic boost (x2)
			boolean boost = com.projecthero.mod.ironman.ability.IronManFlares.boosting(ts, suit.id(), player.level().getGameTime());
			return DirectionalFlightModel.ironManSuit(suit.flightSpeed(), suit.flightAcceleration(), suit.maxFlightSpeedMps(),
					suit.flightCruiseFor(sprint && suit.sprintFlight()), sprint && suit.sprintFlight(), supersonic, // v0.15.11: exact per-mark speeds
					boost ? com.projecthero.mod.ironman.ability.IronManFlares.BOOST_SPEED_MULTIPLIER // v0.15.4: no Mark 6 surge
							// v0.16.7 Era I (user): the Mk III Flight Boost, x1.8 for 3 s
							: com.projecthero.mod.ironman.ability.era1.EraOneMk3Moves.flightBoostMultiplier(ts, suit.id(), player.level().getGameTime())
									// v0.16.7 (user) Veronica: a broken Hulkbuster leg's boot jet -- x0.7, x0.45 with both
									* com.projecthero.mod.ironman.HulkbusterParts.flightFactor(player)
									// v0.16.7 (user): the Mk VIII Flight Boost -- x2 while sprint-flying
									* com.projecthero.mod.ironman.ability.IronManSpaceBoost.speedMultiplier(ts, suit.id(), true,
											sprint && suit.sprintFlight()));
		}
		if (player.getAttachedOrElse(ModAttachments.REPULSOR_BOOTS_FLYING, false)) {
			if (com.projecthero.mod.ironman.suit.ThrusterPackFlight.packFlying(player)) {
				return DirectionalFlightModel.thrusterPack(); // v0.16.7 (user): the Mk VIII Thruster Pack's slow flight
			}
			return DirectionalFlightModel.repulsorBoots(false); // v0.14.26: boots only -- no sprint flight
		}
		// Thor's flight
		if (player.getAttachedOrElse(ModAttachments.FLYING, false)) {
			return DirectionalFlightModel.thor(flyingSpeed, sprint);
		}
		// Max Steel Turbo Flight
		if (player.getAttachedOrElse(ModAttachments.MAX_STEEL_FLYING, false)) {
			return DirectionalFlightModel.maxSteel(flyingSpeed, sprint); // v0.15.15: 18 b/s, 36 sprinting
		}
		// the experimental hero flights: the Flight power, Wind / Psychic / Telekinetic / Magnetic flight, rock / flame flight
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (player.getAttachedOrElse(ModAttachments.HERO_FLYING, false)) {
			if (st != null && st.ownedPowers.contains("power_03_flight")) {
				// v0.16.5 rework: 20 b/s, 50 sprinting on Lift, Afterburner x1.6, Slipstream x1.5, Hover dead still
				return com.projecthero.mod.client.mutation.FlightClient.tune(player, sprint);
			}
			return DirectionalFlightModel.vanilla(flyingSpeed, sprint);
		}
		// Magnetic hover over metal
		if (resource(player, "power_26_magnetic_manipulation/hover_src") > 0.5f) {
			return DirectionalFlightModel.vanilla(flyingSpeed, sprint);
		}
		return null;
	}

	/**
	 * v0.14.27: during a Mark 1 flight burst the wearer can't sink lower than the suit's hover floor (0.5 blocks) above
	 * the ground -- lift them back up and stop any further sinking.
	 */
	private static void applyHoverFloor(LocalPlayer player) {
		if (!player.getAttachedOrElse(ModAttachments.IRON_MAN_FLYING, false)) {
			return;
		}
		TonyStarkState ts = player.getAttachedOrElse(ModAttachments.TONY_STARK_STATE, null);
		if (ts == null || ts.timedFlightUntil <= player.level().getGameTime()) {
			return;
		}
		IronManSuit suit = player.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof IronManArmorItem piece
				? IronManSuits.byId(piece.suitId()) : null;
		if (suit == null || suit.hoverFloor() <= 0.0) {
			return;
		}
		double floorY = DirectionalFlightModel.hoverFloorY(player.level(), player.position(), suit.hoverFloor());
		if (player.getY() < floorY) {
			player.setPos(player.getX(), floorY, player.getZ());
			Vec3 v = player.getDeltaMovement();
			if (v.y < 0) {
				player.setDeltaMovement(v.x, 0, v.z);
			}
		}
	}

	private static float resource(LocalPlayer player, String key) {
		ExperimentalState st = player.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(key, 0.0f);
	}
}
