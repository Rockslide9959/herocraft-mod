package com.projecthero.mod.greenlantern.construct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.GreenLanternConfig;
import com.projecthero.mod.greenlantern.GreenLanternEnergy;
import com.projecthero.mod.greenlantern.GreenLanternRanks;
import com.projecthero.mod.greenlantern.GreenLanternVisuals;
import com.projecthero.mod.greenlantern.data.GreenLanternFx;
import com.projecthero.mod.greenlantern.entity.GreenLanternEntityTypes;
import com.projecthero.mod.greenlantern.entity.HardLightCarEntity;
import com.projecthero.mod.greenlantern.entity.HardLightJetEntity;
import com.projecthero.mod.greenlantern.entity.HardLightVehicleEntity;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: the Carry Platform's three forms. Pressing the Carry Platform's key again while it stands cycles
 * <b>Platform -> Car -> Jet -> Platform</b> ({@link #cycle}); each new form costs its own Ring Charge to shape and its own
 * upkeep while it stands ({@link #upkeepPerSec}). Everyone riding it is carried over into the new form's seats (owner first,
 * so they drive).
 *
 * <p>v0.17.1 round 2 (user: "if you press the carry platform button again ... and not looking at it then the platform
 * follows you like how it used to ... only shifts between its forms when you press the carry platform button while looking
 * at the construct itself"): the key again goes through {@link #keyPress} --
 * <ul>
 *   <li><b>looking at it</b> (the crosshair within {@link GreenLanternConfig#VEHICLE_LOOK_REACH} blocks of its box, padded
 *   {@link GreenLanternConfig#VEHICLE_LOOK_PAD}): the next form ({@link #cycle});</li>
 *   <li><b>not looking at it</b>: follow / park, the pre-vehicle toggle exactly (same chime and sparks,
 *   {@code GreenLanternConstructs#toggleConstruct}). A following Platform chases a point 5 blocks ahead of your aim, as it
 *   always did; a following Car / Jet makes its own way after you ({@link HardLightVehicleEntity#setFollowTarget}) and
 *   parks itself the moment anyone gets in;</li>
 *   <li><b>riding it</b>: on the Platform the look rule above still holds (look down at it to reshape, ahead to fly it); in
 *   the Car or Jet you are inside it, so the key always reshapes. A reshape never drops anyone: an airborne Platform skips
 *   the Car and goes straight to the Jet, a Jet in mid-air becomes a parked Platform hovering where it was with everyone
 *   seated on it, a form that cannot be built rebuilds the old one, and anyone left unseated in mid-air gets Slow
 *   Falling.</li>
 * </ul>
 *
 * <p>The Car and Jet are entities ({@link HardLightCarEntity}, {@link HardLightJetEntity}) held by the construct in
 * {@link Construct#vehicle}; the construct is still the one source of truth -- N, an empty ring or the vehicle being
 * destroyed ends it, and the vehicle dissolves with it. The vehicles themselves are never saved, and one no live construct
 * owns removes itself ({@link HardLightVehicleEntity#tracked}).
 *
 * <p>R while driving goes through {@link #ringKey}: in the Jet R (hold) fires the twin wing cannons, in either Shift+R is
 * the horn / afterburner boost; plain R in the Car is still the Ring Bolt.
 */
public final class GreenLanternVehicles {
	public static final int FORM_PLATFORM = 0;
	public static final int FORM_CAR = 1;
	public static final int FORM_JET = 2;
	private static final String[] FORM_KEYS = { "platform", "car", "jet" };

	/** Every live vehicle entity's UUID (a stray copy -- not in here -- removes itself). */
	private static final Set<UUID> LIVE = ConcurrentHashMap.newKeySet();
	/** Pilots holding R in a Jet. */
	private static final Set<UUID> FIRING = ConcurrentHashMap.newKeySet();
	/** Player -> game time their next Shift+R boost is ready. */
	private static final Map<UUID, Long> BOOST_READY = new ConcurrentHashMap<>();

	static {
		HardLightVehicleEntity.tracked = e -> LIVE.contains(e.getUUID());
	}

	private GreenLanternVehicles() {
	}

	public static void clearSessionState() {
		LIVE.clear();
		FIRING.clear();
		BOOST_READY.clear();
	}

	public static void onCleanup(UUID id) {
		FIRING.remove(id);
		BOOST_READY.remove(id);
	}

	public static String formKey(int form) {
		return "projecthero.green_lantern.vehicle." + FORM_KEYS[Math.floorMod(form, 3)];
	}

	public static float formCost(int form) {
		return switch (form) {
			case FORM_CAR -> GreenLanternConfig.VEHICLE_CAR_COST;
			case FORM_JET -> GreenLanternConfig.VEHICLE_JET_COST;
			default -> GreenLanternConfig.CARRY_PLATFORM_COST;
		};
	}

	/** The construct's upkeep in its current form. */
	public static float upkeepPerSec(Construct c) {
		return switch (c.vehicleForm) {
			case FORM_CAR -> GreenLanternConfig.VEHICLE_CAR_UPKEEP_PER_SEC;
			case FORM_JET -> GreenLanternConfig.VEHICLE_JET_UPKEEP_PER_SEC;
			default -> GreenLanternConfig.CARRY_PLATFORM_UPKEEP_PER_SEC;
		};
	}

	// ---------------------------------------------------------------- the key

	/** The Carry Platform's key pressed while it stands: reshape it (looking at it / driving it) or follow / park. */
	static void keyPress(ServerPlayer p, Construct c) {
		boolean inside = c.vehicleForm != FORM_PLATFORM && c.vehicle != null && p.getVehicle() == c.vehicle;
		if (inside || lookingAt(p, c)) {
			cycle(p, c);
		} else {
			toggleFollow(p, c);
		}
	}

	/** Follow / park, as the key did before the vehicles: the toggle's chime and sparks, plus the action-bar line. */
	static void toggleFollow(ServerPlayer p, Construct c) {
		GreenLanternConstructs.toggleConstruct(p, c);
		String key = !c.toggledOn ? "message.projecthero.green_lantern.vehicle.park"
				: c.vehicleForm == FORM_PLATFORM ? "message.projecthero.green_lantern.vehicle.follow"
						: "message.projecthero.green_lantern.vehicle.follow_vehicle";
		p.displayClientMessage(Component.translatable(key, Component.translatable(formKey(c.vehicleForm))), true);
	}

	/**
	 * Whether {@code p}'s crosshair is on the construct: the ray from the eyes, out to {@link GreenLanternConfig#VEHICLE_LOOK_REACH}
	 * and stopped by any other block, meets its box padded {@link GreenLanternConfig#VEHICLE_LOOK_PAD} sideways and down and
	 * {@link GreenLanternConfig#VEHICLE_LOOK_PAD_UP} up (so a platform at your feet is not "looked at" by a level gaze over it).
	 */
	public static boolean lookingAt(ServerPlayer p, Construct c) {
		AABB box = formBox(c);
		if (box == null) {
			return false;
		}
		double pad = GreenLanternConfig.VEHICLE_LOOK_PAD;
		AABB padded = new AABB(box.minX - pad, box.minY - pad, box.minZ - pad, box.maxX + pad,
				box.maxY + GreenLanternConfig.VEHICLE_LOOK_PAD_UP, box.maxZ + pad);
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getLookAngle().scale(GreenLanternConfig.VEHICLE_LOOK_REACH));
		if (padded.contains(eye)) {
			padded = box.inflate(0.1); // standing in the padding: only the form itself counts
			if (padded.contains(eye)) {
				return true;
			}
		}
		double limit = GreenLanternConfig.VEHICLE_LOOK_REACH;
		BlockHitResult bh = c.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		if (bh.getType() != HitResult.Type.MISS) {
			for (Construct.Cell cell : c.cells) {
				if (cell.pos().equals(bh.getBlockPos())) {
					return true; // right on one of the platform's own blocks
				}
			}
			limit = bh.getLocation().distanceTo(eye) + pad; // the padding may poke out past the block in front
		}
		java.util.Optional<Vec3> hit = padded.clip(eye, end);
		return hit.isPresent() && hit.get().distanceTo(eye) <= limit;
	}

	/** The current form's box: the platform's cells, or the vehicle's bounding box. */
	private static AABB formBox(Construct c) {
		if (c.vehicleForm != FORM_PLATFORM) {
			return c.vehicle != null ? c.vehicle.getBoundingBox() : null;
		}
		AABB box = null;
		for (Construct.Cell cell : c.cells) {
			AABB b = new AABB(cell.pos());
			box = box == null ? b : box.minmax(b);
		}
		return box;
	}

	/** Whether the platform has no ground under it (two blocks of nothing below its 3x3): no Car here. */
	static boolean platformAirborne(Construct c) {
		BlockPos centre = c.platformBlockCenter != null ? c.platformBlockCenter : BlockPos.containing(c.anchor);
		AABB below = new AABB(centre.getX() - 1, centre.getY() - 2, centre.getZ() - 1, centre.getX() + 2, centre.getY(),
				centre.getZ() + 2);
		return c.level.noCollision(below);
	}

	// ---------------------------------------------------------------- cycling

	/** On to the next form: Platform -> Car -> Jet -> Platform (an airborne Platform skips the Car). */
	static void cycle(ServerPlayer p, Construct c) {
		int next = (c.vehicleForm + 1) % 3;
		if (next == FORM_CAR && c.vehicleForm == FORM_PLATFORM && platformAirborne(c)) {
			next = FORM_JET; // a car would only fall: up here it becomes the Jet
		}
		float cost = formCost(next);
		if (!GreenLanternEnergy.spend(p, cost)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		ServerLevel level = c.level;
		int was = c.vehicleForm;
		// where it is now, and who is aboard (the owner first, so they take the wheel)
		Vec3 at;
		float yaw;
		List<Entity> riders = new ArrayList<>();
		if (c.vehicleForm == FORM_PLATFORM || c.vehicle == null) {
			BlockPos centre = c.platformBlockCenter != null ? c.platformBlockCenter : BlockPos.containing(c.anchor);
			at = Vec3.atBottomCenterOf(centre);
			yaw = p.getYRot();
			for (int id : c.seatEntityIds) {
				Entity seat = level.getEntity(id);
				if (seat != null) {
					riders.addAll(seat.getPassengers());
				}
			}
		} else {
			at = c.vehicle.position();
			yaw = c.vehicle.getYRot();
			riders.addAll(c.vehicle.getPassengers());
		}
		riders.sort((a, b) -> Boolean.compare(b.getUUID().equals(c.owner), a.getUUID().equals(c.owner)));

		// take the old form down (riders stay where they are for the moment)
		if (c.vehicleForm == FORM_PLATFORM) {
			GreenLanternConstructs.removeCarrySeats(c);
			GreenLanternConstructs.restore(c);
			c.cells.clear();
			c.built = 0;
		} else {
			removeVehicle(c, false);
		}

		boolean ok = next == FORM_PLATFORM ? buildPlatform(c, at, riders) : buildVehicle(p, c, next, at, yaw, riders);
		if (!ok) {
			// no room for the new form here: the old form goes back where it stood, charge refunded
			GreenLanternEnergy.refund(p, cost);
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.invalid_target");
			boolean back = was == FORM_PLATFORM ? buildPlatform(c, at, riders) : buildVehicle(p, c, was, at, yaw, riders);
			if (!back && was != FORM_PLATFORM) {
				back = buildPlatform(c, at, riders);
			}
			if (!back) {
				c.vehicleForm = FORM_PLATFORM;
			}
			cushion(riders);
			return;
		}
		c.vehicleForm = next;
		c.toggledOn = false; // every new form starts parked
		cushion(riders);
		GreenLanternVisuals.anim(p, GreenLanternFx.ANIM_CONSTRUCT);
		Vec3 mid = c.vehicle != null ? c.vehicle.position().add(0, 0.6, 0) : at.add(0, 0.5, 0);
		AbilityHelpers.line(level, AbilityHelpers.handPosition(p), mid, GreenLanternConstructs.SPARK, 1.5);
		level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9f, 1.2f);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 1.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.green_lantern.vehicle.form",
				Component.translatable(formKey(next))), true);
	}

	/** The reshape's safety net: anyone who came out of it without a seat, in mid-air, floats down. */
	private static void cushion(List<Entity> riders) {
		for (Entity r : riders) {
			if (r.getVehicle() == null && !r.onGround() && r instanceof LivingEntity le
					&& le.level().noCollision(le, le.getBoundingBox().move(0, -1.0, 0))) {
				le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOW_FALLING,
						120, 0, false, false, true));
			}
		}
	}

	/** Back to a parked 3x3 platform around {@code at}, riders seated on it. */
	private static boolean buildPlatform(Construct c, Vec3 at, List<Entity> riders) {
		BlockPos centre = BlockPos.containing(at);
		// riders are lifted clear of the new blocks first (a cell never forms inside a player)
		for (Entity r : riders) {
			r.stopRiding();
			r.teleportTo(r.getX(), centre.getY() + 1.05, r.getZ());
		}
		for (BlockPos pos : GreenLanternConstructs.carryPlatformCells(centre)) {
			GreenLanternConstructs.add(c, pos, GreenLanternConstructs.carryPlatformBlockState());
		}
		if (c.cells.isEmpty()) {
			return false;
		}
		c.built = 0;
		GreenLanternConstructs.placeCells(c, c.cells.size(), true);
		c.platformCenter = Vec3.atBottomCenterOf(centre);
		c.platformBlockCenter = centre;
		c.toggledOn = false; // parked
		GreenLanternConstructs.spawnCarryPlatformSeats(c);
		int seat = 0;
		for (Entity r : riders) {
			while (seat < c.seatEntityIds.size()) {
				Entity s = c.level.getEntity(c.seatEntityIds.get(seat++));
				if (s != null && s.getPassengers().isEmpty()) {
					r.startRiding(s, true);
					break;
				}
			}
		}
		c.vehicleForm = FORM_PLATFORM;
		return true;
	}

	/** A Car or Jet at {@code at}, facing {@code yaw}, with the riders aboard. */
	private static boolean buildVehicle(ServerPlayer owner, Construct c, int form, Vec3 at, float yaw, List<Entity> riders) {
		ServerLevel level = c.level;
		HardLightVehicleEntity v = form == FORM_CAR ? GreenLanternEntityTypes.HARD_LIGHT_CAR.create(level)
				: GreenLanternEntityTypes.HARD_LIGHT_JET.create(level);
		if (v == null) {
			return false;
		}
		v.setOwner(c.owner);
		v.setGolden(GreenLanternRanks.golden(owner));
		boolean placed = false;
		for (int dy = 0; dy <= 3 && !placed; dy++) {
			v.moveTo(at.x, at.y + dy, at.z, yaw, 0f);
			placed = level.noCollision(v, v.getBoundingBox());
		}
		if (!placed) {
			return false;
		}
		v.setYHeadRot(yaw);
		v.setYBodyRot(yaw);
		LIVE.add(v.getUUID());
		level.addFreshEntity(v);
		c.vehicle = v;
		for (Entity r : riders) {
			r.stopRiding();
			if (v.getPassengers().size() < v.seats()) {
				r.startRiding(v, true);
			}
		}
		// it shapes itself out of the ring: the renderer sweeps it in over VEHICLE_BUILD_TICKS, sparks run over it here
		AABB box = v.getBoundingBox();
		level.sendParticles(GreenLanternConstructs.SPARK, box.getCenter().x, box.getCenter().y, box.getCenter().z, 40,
				box.getXsize() * 0.4, box.getYsize() * 0.4, box.getZsize() * 0.4, 0.02);
		return true;
	}

	/** The vehicle goes: riders out, a scatter of green sparks (unless it is just being reshaped). */
	private static void removeVehicle(Construct c, boolean dissolveFx) {
		Entity e = c.vehicle;
		c.vehicle = null;
		if (e == null) {
			return;
		}
		LIVE.remove(e.getUUID());
		if (dissolveFx && e.level() instanceof ServerLevel level) {
			AABB box = e.getBoundingBox();
			Vec3 m = box.getCenter();
			level.sendParticles(GreenLanternConstructs.SPARK, m.x, m.y, m.z, 60, box.getXsize() * 0.45, box.getYsize() * 0.45,
					box.getZsize() * 0.45, 0.03);
			level.sendParticles(ParticleTypes.END_ROD, m.x, m.y, m.z, 14, box.getXsize() * 0.35, box.getYsize() * 0.3,
					box.getZsize() * 0.35, 0.04);
			level.playSound(null, m.x, m.y, m.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.9f, 1.3f);
		}
		for (Entity r : e.getPassengers()) {
			FIRING.remove(r.getUUID());
		}
		if (e instanceof HardLightVehicleEntity v) {
			v.reshaping = !dissolveFx; // a reshape re-seats everyone at once (cycle's cushion covers a miss)
			v.dissolve();
		} else {
			e.discard();
		}
	}

	/** The construct ended (N, logout, out of charge, destroyed). */
	static void onEnd(Construct c) {
		removeVehicle(c, true);
	}

	/** Per server tick while the construct is a Car or Jet. False = it is gone (destroyed / lost), end the construct. */
	static boolean tick(ServerPlayer owner, Construct c) {
		Entity e = c.vehicle;
		if (!(e instanceof HardLightVehicleEntity v) || e.isRemoved() || !v.isAlive() || e.level() != c.level) {
			return false;
		}
		if (c.toggledOn && !v.getPassengers().isEmpty()) {
			c.toggledOn = false; // someone got in: it is theirs to drive now, and stays parked when they get out
		}
		v.setFollowTarget(c.toggledOn && owner.level() == v.level() && !owner.isSpectator() ? owner : null);
		if (v instanceof HardLightJetEntity jet && jet.getControllingPassenger() instanceof ServerPlayer pilot
				&& FIRING.contains(pilot.getUUID())) {
			if (c.level.getGameTime() % GreenLanternConfig.VEHICLE_JET_CANNON_INTERVAL_TICKS == 0) {
				fireCannons(pilot, jet);
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- R while driving

	/** The GL R key while riding a hard-light vehicle. True = handled here (the ring bolt / beam does not fire). */
	public static boolean ringKey(ServerPlayer p, boolean pressed) {
		if (!(p.getVehicle() instanceof HardLightVehicleEntity v) || v.getControllingPassenger() != p) {
			FIRING.remove(p.getUUID());
			return false;
		}
		if (v instanceof HardLightJetEntity) {
			if (!pressed) {
				FIRING.remove(p.getUUID());
			} else if (p.isShiftKeyDown()) {
				v.noteShiftUsed(p);
				boost(p, v, GreenLanternConfig.VEHICLE_JET_BOOST_COST, GreenLanternConfig.VEHICLE_JET_BOOST_TICKS,
						GreenLanternConfig.VEHICLE_JET_BOOST_COOLDOWN_TICKS);
			} else {
				FIRING.add(p.getUUID());
				if (p.level().getGameTime() % GreenLanternConfig.VEHICLE_JET_CANNON_INTERVAL_TICKS != 0) {
					fireCannons(p, (HardLightJetEntity) v); // the first pair goes the moment R does
				}
			}
			return true;
		}
		if (pressed && p.isShiftKeyDown()) {
			v.noteShiftUsed(p);
			boost(p, v, GreenLanternConfig.VEHICLE_CAR_BOOST_COST, GreenLanternConfig.VEHICLE_CAR_BOOST_TICKS,
					GreenLanternConfig.VEHICLE_CAR_BOOST_COOLDOWN_TICKS);
			return true;
		}
		return false; // plain R in the car: the Ring Bolt as usual
	}

	/** Whether {@code p} is holding the Jet's cannons (gametests). */
	public static boolean firing(ServerPlayer p) {
		return FIRING.contains(p.getUUID());
	}

	private static void boost(ServerPlayer p, HardLightVehicleEntity v, float cost, int ticks, int cooldown) {
		long now = p.level().getGameTime();
		Long ready = BOOST_READY.get(p.getUUID());
		if (ready != null && now < ready) {
			return;
		}
		if (!GreenLanternEnergy.spend(p, cost)) {
			GreenLanternEnergy.feedback(p, "message.projecthero.ability.low_charge");
			return;
		}
		BOOST_READY.put(p.getUUID(), now + cooldown);
		v.boost(ticks);
		ServerLevel level = p.serverLevel();
		if (v instanceof HardLightCarEntity) {
			// the horn: two bright notes
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS,
					1.6f, 1.25f);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0f,
					0.9f);
		} else {
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.6f,
					0.6f);
		}
		level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6f, 1.8f);
		Vec3 back = v.local(0, 0.5, v instanceof HardLightJetEntity ? -2.7 : -2.3);
		level.sendParticles(GreenLanternConstructs.SPARK, back.x, back.y, back.z, 16, 0.3, 0.2, 0.3, 0.05);
	}

	/**
	 * One pair of hard-light bolts from the wingtip cannons, converging on whatever the pilot's crosshair is on (up to
	 * {@link GreenLanternConfig#VEHICLE_JET_CANNON_RANGE} blocks). Each bolt that finds a target hits for
	 * {@link GreenLanternConfig#VEHICLE_JET_CANNON_DAMAGE} x the ring's power (Oath, Lantern Rank, Willpower Surge).
	 */
	public static void fireCannons(ServerPlayer pilot, HardLightJetEntity jet) {
		if (!GreenLanternEnergy.spend(pilot, GreenLanternConfig.VEHICLE_JET_CANNON_COST)) {
			FIRING.remove(pilot.getUUID());
			GreenLanternEnergy.feedback(pilot, "message.projecthero.ability.low_charge");
			return;
		}
		ServerLevel level = pilot.serverLevel();
		Vec3 eye = pilot.getEyePosition();
		Vec3 look = pilot.getLookAngle();
		Vec3 end = eye.add(look.scale(GreenLanternConfig.VEHICLE_JET_CANNON_RANGE));
		BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, pilot));
		if (bh.getType() != HitResult.Type.MISS) {
			end = bh.getLocation();
		}
		EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, pilot, eye, end,
				new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity le && le.isAlive() && e != jet
						&& !jet.hasPassenger(e) && GreenLanternConstructs.isHostileTarget(pilot, le), 0.6f);
		LivingEntity target = eh != null && eh.getEntity() instanceof LivingEntity le ? le : null;
		Vec3 aim = target != null ? target.position().add(0, target.getBbHeight() * 0.5, 0) : end;
		float dmg = GreenLanternConfig.VEHICLE_JET_CANNON_DAMAGE * GreenLanternRanks.power(pilot);
		for (int s = -1; s <= 1; s += 2) {
			Vec3 muzzle = jet.local(s * 3.05, 0.45, 0.6);
			com.projecthero.mod.greenlantern.GreenLanternConstructAttacks.boltStreak(pilot, muzzle, aim, 0.16f, 4);
			if (target != null) {
				AbilityHelpers.hurtBurst(pilot, target, dmg);
			}
		}
		level.playSound(null, jet.getX(), jet.getY(), jet.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7f,
				1.8f);
	}
}
