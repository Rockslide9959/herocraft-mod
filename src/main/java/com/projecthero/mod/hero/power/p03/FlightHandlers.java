package com.projecthero.mod.hero.power.p03;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.HeroFlight;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.revamp.batcha.FlightFxEntity;
import com.projecthero.mod.hero.visual.MutationMeters;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Power 03 -- Flight (v0.16.5 rework, docs/V0165_POWERS_PROPOSAL.md section 7): a Superman / Homelander style flier. Flight
 * itself is free and starts with a double-tap of jump ({@link TogglePayload}); a <b>Lift</b> meter (0-100) pays for
 * sprint-flying, the dashes and the big moves, and refills 12 a second on the ground or while cruising.
 *
 * <p>The movement is the client's (the shared directional flight, {@code client.flight.DirectionalFlight}, reading
 * {@code DirectionalFlightModel#flightPower}): 20 blocks a second cruising, 50 sprint-flying (user numbers), x1.6 with
 * Afterburner, x1.5 in a Slipstream. The server drives the dashes by sending the velocity every tick of the move
 * ({@link #drive}), which the client's flight adopts.
 *
 * <p>R Flying Punch / Shift+R Dive Bomb. G Grab &amp; Carry (press again to throw) / Shift+G Orbital Drop. X Air Dash /
 * Shift+X Barrel Roll. Z Sonic Boom / Shift+Z Takeoff Blast. V Hover (toggle) / Shift+V Slipstream (toggle). C
 * Afterburner (held) / Shift+C Updraft. No H / N. All six slots are HOLD, so the handlers own their Shift / release logic.
 */
public final class FlightHandlers {
	public static final String KEY = "power_03_flight";

	// ---- Lift ----
	public static final float MAX_LIFT = 100.0f;
	public static final float LIFT_REGEN_PER_SECOND = 12.0f;
	/** v0.16.5: sprint-flying (50 b/s) spends 6 Lift a second; with the meter empty you can only cruise. */
	public static final float SPRINT_LIFT_PER_SECOND = 6.0f;
	public static final float AFTERBURNER_LIFT_PER_SECOND = 8.0f;
	public static final float SLIPSTREAM_LIFT_PER_SECOND = 4.0f;

	public static final float COST_PUNCH = 15.0f;
	public static final float COST_DIVE = 25.0f;
	public static final float COST_GRAB = 15.0f;
	public static final float COST_ORBITAL = 40.0f;
	public static final float COST_DASH = 10.0f;
	public static final float COST_ROLL = 10.0f;
	public static final float COST_BOOM = 30.0f;
	public static final float COST_TAKEOFF = 20.0f;
	public static final float COST_UPDRAFT = 25.0f;

	// ---- speeds (blocks a second; the client's flight tune reads these) ----
	public static final double CRUISE_BPS = 20.0;
	public static final double SPRINT_BPS = 50.0;
	public static final double AFTERBURNER_MULT = 1.6;
	public static final double SLIPSTREAM_MULT = 1.5;
	/** Space / Sneak straight up and down. */
	public static final double VERTICAL_BPS = 12.0;

	// ---- R: Flying Punch / Dive Bomb ----
	public static final double PUNCH_RANGE = 8.0;
	public static final float PUNCH_DAMAGE = 14.0f;
	public static final double PUNCH_KNOCKBACK = 3.0;
	public static final float DIVE_DAMAGE_MIN = 12.0f;
	public static final float DIVE_DAMAGE_MAX = 30.0f;
	/** The full 30 lands from this high up. */
	public static final double DIVE_FULL_HEIGHT = 30.0;
	public static final double DIVE_RADIUS = 4.0;
	private static final double DIVE_SPEED = 3.5;
	private static final double DIVE_AIM_RANGE = 96.0;

	// ---- G: Grab & Carry / Orbital Drop ----
	public static final double GRAB_RANGE = 5.0;
	public static final int CARRY_MAX_TICKS = 30 * 20;
	public static final double ORBITAL_HEIGHT = 40.0;
	public static final int ORBITAL_CD = 15 * 20;
	private static final double ORBITAL_SPEED = 3.0;

	// ---- X: Air Dash / Barrel Roll ----
	public static final double DASH_BLOCKS = 12.0;
	public static final int ROLL_IFRAMES = 10;
	private static final double ROLL_BLOCKS = 5.0;

	// ---- Z: Sonic Boom / Takeoff Blast ----
	public static final double BOOM_RANGE = 10.0;
	public static final float BOOM_DAMAGE = 16.0f;
	/** cos of the cone's half-angle (~40 degrees). */
	public static final double BOOM_CONE = 0.76;
	public static final double TAKEOFF_HEIGHT = 20.0;
	public static final double TAKEOFF_RADIUS = 5.0;
	/** v0.16.6: the lowest the Takeoff Blast may aim (sine of the angle above the horizon): looking down still lifts off. */
	public static final double TAKEOFF_MIN_RISE = 0.35;

	// ---- C: Updraft ----
	public static final double UPDRAFT_RADIUS = 6.0;
	public static final double UPDRAFT_HEIGHT = 6.0;

	// ---- server drives (sent as v + DRIVE_SHIFT: resources clamp at 0) ----
	private static final float DRIVE_SHIFT = 64.0f;
	public static final int DRIVE_PUNCH = 1;
	public static final int DRIVE_DASH = 2;
	public static final int DRIVE_ROLL = 3;
	public static final int DRIVE_DIVE = 4;
	public static final int DRIVE_RISE = 5;
	public static final int DRIVE_BOOM = 6;

	private static final ResourceLocation HOVER_KB = com.projecthero.mod.ProjectHeroMod.id("flight_hover_kb");

	private FlightHandlers() {
	}

	// ================================================================ resources / queries

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v);
	}

	/** Reads a resource from the synced attachment (both sides; the client only has its own player's). */
	public static float resAny(Player p, String name) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	public static boolean flying(Player p) {
		return p.getAttachedOrElse(ModAttachments.HERO_FLYING, false);
	}

	public static float lift(ServerPlayer p) {
		seed(p);
		return res(p, "lift");
	}

	public static boolean hovering(Player p) {
		return owns(p) && resAny(p, "hover") > 0.5f;
	}

	public static boolean slipstream(Player p) {
		return owns(p) && resAny(p, "slip") > 0.5f;
	}

	public static boolean afterburner(Player p) {
		return owns(p) && resAny(p, "burn") > 0.5f;
	}

	/** True during a Barrel Roll's projectile-immunity window. */
	public static boolean rolling(Player p) {
		return owns(p) && resAny(p, "roll_ticks") > 0.5f;
	}

	/** The id of the creature held (Grab &amp; Carry or the Orbital Drop lift), or 0. */
	public static float carriedId(Player p) {
		if (!owns(p)) {
			return 0.0f;
		}
		float id = resAny(p, "carry_id");
		return id > 0.5f ? id : resAny(p, "orbit_id");
	}

	public static int driveKind(Player p) {
		return owns(p) && resAny(p, "drive_ticks") > 0.5f ? Math.round(resAny(p, "drive_kind")) : 0;
	}

	private static void seed(ServerPlayer p) {
		if (res(p, "seeded") < 0.5f) {
			set(p, "seeded", 1);
			set(p, "lift", MAX_LIFT);
		}
	}

	/** Spends {@code amount} Lift, or refuses (with a message) if there is not that much. */
	private static boolean spend(AbilityContext ctx, float amount) {
		ServerPlayer p = ctx.player();
		seed(p);
		float have = res(p, "lift");
		if (have + 1.0e-3f < amount && !p.getAbilities().instabuild) {
			ctx.actionBar("message.projecthero.flight.no_lift", String.format(java.util.Locale.ROOT, "%.0f", amount));
			return false;
		}
		set(p, "lift", Math.max(0.0f, have - amount));
		return true;
	}

	private static void drain(ServerPlayer p, float perSecond) {
		set(p, "lift", Math.max(0.0f, res(p, "lift") - perSecond / 20.0f));
	}

	private static boolean noBoss(LivingEntity t) {
		return !com.projecthero.mod.titanshifter.TitanCombat.isBoss(t) && t.getMaxHealth() <= 200.0f;
	}

	private static boolean grabbable(ServerPlayer p, LivingEntity t) {
		return t != null && AbilityHelpers.isValidGrabTarget(t, p) && noBoss(t);
	}

	private static Vec3 mid(Entity e) {
		return e.position().add(0, e.getBbHeight() * 0.5, 0);
	}

	/** Takes off (hero flight) if not already up. */
	private static void ensureFlying(ServerPlayer p) {
		if (!HeroFlight.isFlying(p)) {
			Power power = Powers.byKey(KEY);
			HeroFlight.startFlying(p, power);
		}
	}

	private static void fx(ServerPlayer p, int kind, Vec3 at, Vec3 dir, float radius, BlockState block) {
		FlightFxEntity.spawn(AbilityHelpers.level(p), kind, at, dir, radius, block);
	}

	private static BlockState groundBelow(ServerPlayer p) {
		BlockPos below = p.blockPosition().below();
		BlockState s = p.level().getBlockState(below);
		return s.isAir() ? p.level().getBlockState(below.below()) : s;
	}

	// ================================================================ double-tap jump: flight on / off

	/** Client to server: "I double-tapped jump" -- flight on / off (the server re-validates). */
	public record TogglePayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<TogglePayload> TYPE = new CustomPacketPayload.Type<>(
				com.projecthero.mod.ProjectHeroMod.id("flight_power_toggle"));
		public static final StreamCodec<RegistryFriendlyByteBuf, TogglePayload> CODEC = StreamCodec.unit(new TogglePayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Double-tap jump: takes off, or drops out of flight. Only while Flight is the selected mutation. */
	public static boolean toggleFlight(ServerPlayer p) {
		Power power = Powers.byKey(KEY);
		if (power == null || !power.equals(ExperimentalPowers.getActive(p)) || p.isSpectator() || p.isPassenger()) {
			return false;
		}
		if (HeroFlight.isFlying(p)) {
			HeroFlight.setFlying(p, false);
			return false;
		}
		seed(p);
		boolean up = HeroFlight.startFlying(p, power);
		if (up) {
			fx(p, FlightFxEntity.VAPOR_RING, p.position().add(0, 0.1, 0), new Vec3(0, 1, 0), 1.2f, null);
		}
		return up;
	}

	// ================================================================ registration

	public static void register() {
		PayloadTypeRegistry.playC2S().register(TogglePayload.TYPE, TogglePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TogglePayload.TYPE, (payload, context) -> toggleFlight(context.player()));

		// R -- Flying Punch / Dive Bomb (Shift). Also runs the power-wide upkeep.
		AbilityHandlers.register(KEY, "flying_punch", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					diveBomb(ctx);
				} else {
					flyingPunch(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				upkeep(ctx.player(), ctx.level());
			}
		});

		// G -- Grab & Carry (press again to throw) / Orbital Drop (Shift)
		AbilityHandlers.register(KEY, "grab_carry", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "carry_id") > 0.5f) {
					throwCarried(p);
					return;
				}
				if (p.isShiftKeyDown()) {
					orbitalDrop(ctx);
				} else {
					grab(ctx);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				carryTick(ctx.player(), ctx.level());
			}
		});

		// X -- Air Dash / Barrel Roll (Shift)
		AbilityHandlers.register(KEY, "air_dash", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					barrelRoll(ctx);
				} else {
					airDash(ctx);
				}
			}
		});

		// Z -- Sonic Boom / Takeoff Blast (Shift)
		AbilityHandlers.register(KEY, "sonic_boom", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.player().isShiftKeyDown()) {
					takeoffBlast(ctx);
				} else {
					sonicBoom(ctx);
				}
			}
		});

		// V -- Hover (toggle) / Slipstream (Shift, toggle)
		AbilityHandlers.register(KEY, "hover", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					setSlipstream(p, res(p, "slip") < 0.5f);
					ctx.actionBar(res(p, "slip") > 0.5f ? "message.projecthero.flight.slip_on" : "message.projecthero.flight.slip_off");
					return;
				}
				setHover(p, res(p, "hover") < 0.5f);
			}
		});

		// C -- Afterburner (held) / Updraft (Shift)
		AbilityHandlers.register(KEY, "afterburner", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					updraft(ctx);
					return;
				}
				seed(p);
				if (res(p, "lift") < 1.0f && !p.getAbilities().instabuild) {
					ctx.actionBar("message.projecthero.flight.no_lift", "1");
					return;
				}
				ensureFlying(p);
				setHover(p, false);
				set(p, "burn", 1);
				fx(p, FlightFxEntity.VAPOR_RING, p.position().add(0, 0.9, 0), p.getLookAngle(), 1.4f, null);
				AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.6f);
				AbilityHelpers.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8f, 0.7f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				set(ctx.player(), "burn", 0);
			}
		});

		registerPassives();
	}

	// ================================================================ upkeep (every tick for an owner)

	private static void upkeep(ServerPlayer p, ServerLevel level) {
		seed(p);
		for (String timer : new String[] { "orbital_cd", "roll_ticks", "fx_ticks" }) {
			float v = res(p, timer);
			if (v > 0) {
				set(p, timer, v - 1);
			}
		}
		boolean flying = HeroFlight.isFlying(p);
		// the holds end with the flight
		if (!flying) {
			if (res(p, "hover") > 0.5f) {
				setHover(p, false);
			}
			if (res(p, "burn") > 0.5f) {
				set(p, "burn", 0);
			}
		}
		// Lift: sprint-flying, Afterburner and Slipstream spend it; otherwise it refills on the ground or cruising
		float lift = res(p, "lift");
		boolean sprinting = flying && p.isSprinting() && res(p, "hover") < 0.5f;
		boolean burn = res(p, "burn") > 0.5f;
		boolean slip = res(p, "slip") > 0.5f;
		boolean creative = p.getAbilities().instabuild;
		if (!creative && (sprinting || burn || slip)) {
			float per = (sprinting ? SPRINT_LIFT_PER_SECOND : 0f) + (burn ? AFTERBURNER_LIFT_PER_SECOND : 0f)
					+ (slip ? SLIPSTREAM_LIFT_PER_SECOND : 0f);
			drain(p, per);
			if (res(p, "lift") <= 0.01f) {
				if (burn) {
					set(p, "burn", 0);
				}
				if (slip) {
					setSlipstream(p, false);
					p.displayClientMessage(Component.translatable("message.projecthero.flight.slip_off"), true);
				}
			}
		} else if (lift < MAX_LIFT && (p.onGround() || flying) && res(p, "drive_ticks") < 0.5f) {
			set(p, "lift", Math.min(MAX_LIFT, lift + LIFT_REGEN_PER_SECOND / 20.0f));
		}
		if (burn && flying && p.tickCount % 2 == 0) {
			Vec3 back = p.position().add(0, 0.6, 0).subtract(p.getLookAngle().scale(0.8));
			level.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 1, 0.08, 0.08, 0.08, 0.0);
		}
		driveTick(p, level);
		poseTick(p);
	}

	/** The looping stances: carrying (the target held out in front), hovering. Flying itself is the shared lean. */
	private static void poseTick(ServerPlayer p) {
		if (res(p, "drive_ticks") > 0.5f) {
			return; // a dash / dive / rise owns the pose
		}
		if (res(p, "carry_id") > 0.5f) {
			BatchA.stance(p, KEY, "p03.carry");
		} else if (res(p, "hover") > 0.5f) {
			BatchA.stance(p, KEY, "p03.hover");
		} else if (HeroFlight.isFlying(p)) {
			// legs together and trailing, arms at the sides (the body lean is the shared flight pose)
			BatchA.stance(p, KEY, "p03.fly");
		} else {
			MutationVisuals.stopIf(p, "p03.carry");
			MutationVisuals.stopIf(p, "p03.hover");
			MutationVisuals.stopIf(p, "p03.fly");
		}
	}

	// ================================================================ server drives

	/** Moves the player at {@code v} blocks a tick for {@code ticks} ticks (re-sent every tick; dives run till they land). */
	public static void drive(ServerPlayer p, Vec3 v, int ticks, int kind) {
		set(p, "drive_vx", (float) v.x + DRIVE_SHIFT);
		set(p, "drive_vy", (float) v.y + DRIVE_SHIFT);
		set(p, "drive_vz", (float) v.z + DRIVE_SHIFT);
		set(p, "drive_ticks", ticks);
		set(p, "drive_kind", kind);
		set(p, "drive_age", 0);
		set(p, "drive_y", (float) p.getY() + 1000.0f);
		setHover(p, false);
		AbilityHelpers.launchSelf(p, v);
	}

	private static Vec3 driveVelocity(ServerPlayer p) {
		return new Vec3(res(p, "drive_vx") - DRIVE_SHIFT, res(p, "drive_vy") - DRIVE_SHIFT, res(p, "drive_vz") - DRIVE_SHIFT);
	}

	private static void endDrive(ServerPlayer p, double keep) {
		Vec3 v = driveVelocity(p);
		set(p, "drive_ticks", 0);
		set(p, "drive_kind", 0);
		AbilityHelpers.launchSelf(p, v.scale(keep));
	}

	private static void driveTick(ServerPlayer p, ServerLevel level) {
		float left = res(p, "drive_ticks");
		if (left < 0.5f) {
			return;
		}
		int kind = Math.round(res(p, "drive_kind"));
		int age = (int) res(p, "drive_age") + 1;
		set(p, "drive_age", age);
		Vec3 v = driveVelocity(p);
		p.resetFallDistance();
		switch (kind) {
			case DRIVE_PUNCH -> {
				if (punchContact(p, level, v)) {
					return;
				}
			}
			case DRIVE_DIVE -> {
				if (age > 1 && (p.onGround() || p.verticalCollision || p.horizontalCollision || p.isInWater() || age > 60)) {
					diveImpact(p, level);
					return;
				}
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.15, 0.3, 0.15, 0.01);
				AbilityHelpers.launchSelf(p, v);
				return;
			}
			case DRIVE_RISE -> {
				// a ceiling / wall stops it (a block in the way along the launch, not merely a late movement packet)
				if (age > 1 && !level.noCollision(p, p.getBoundingBox().move(v.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : v.normalize()))) {
					left = 1;
				}
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() - 0.3, p.getZ(), 3, 0.15, 0.2, 0.15, 0.01);
			}
			case DRIVE_DASH, DRIVE_BOOM -> {
				Vec3 behind = p.position().add(0, 0.9, 0).subtract(v.normalize().scale(0.6));
				level.sendParticles(ParticleTypes.CLOUD, behind.x, behind.y, behind.z, 2, 0.1, 0.1, 0.1, 0.0);
			}
			default -> {
			}
		}
		if (left <= 1.0f) {
			finishDrive(p, level, kind);
			return;
		}
		set(p, "drive_ticks", left - 1);
		AbilityHelpers.launchSelf(p, v);
	}

	private static void finishDrive(ServerPlayer p, ServerLevel level, int kind) {
		switch (kind) {
			case DRIVE_PUNCH -> {
				// the dash ran out: a target still in reach takes the punch anyway
				Entity t = level.getEntity((int) res(p, "punch_id"));
				if (t instanceof LivingEntity le && le.isAlive() && le.distanceTo(p) < 3.0f) {
					punchHit(p, level, le, driveVelocity(p));
				}
				set(p, "punch_id", 0);
				endDrive(p, 0.15);
			}
			case DRIVE_RISE -> {
				endDrive(p, 0.0);
				if (res(p, "orbit_id") > 0.5f) {
					orbitalRelease(p, level);
				}
				ensureFlying(p);
			}
			case DRIVE_DASH, DRIVE_BOOM -> endDrive(p, 0.35);
			default -> endDrive(p, 0.2);
		}
	}

	// ================================================================ R: Flying Punch / Dive Bomb

	private static void flyingPunch(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (!spend(ctx, COST_PUNCH)) {
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, PUNCH_RANGE + 0.5);
		Vec3 dir;
		double dist;
		if (t != null && com.projecthero.mod.combat.HeroTargets.canHarm(p, t)) {
			Vec3 to = mid(t).subtract(p.position().add(0, p.getBbHeight() * 0.5, 0));
			dist = Math.max(0.5, to.length() - 1.1);
			dir = to.normalize();
			set(p, "punch_id", t.getId());
		} else {
			dir = p.getLookAngle();
			dist = PUNCH_RANGE;
			set(p, "punch_id", 0);
		}
		int ticks = Math.max(2, (int) Math.ceil(dist / 2.2));
		drive(p, dir.scale(dist / ticks), ticks, DRIVE_PUNCH);
		MutationVisuals.play(p, "p03.punch");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.9f, 1.3f);
		fx(p, FlightFxEntity.VAPOR_RING, p.position().add(0, 0.9, 0), dir, 1.0f, null);
	}

	/** During the punch dash: the first enemy in front takes the hit. True if it landed (the dash ends). */
	private static boolean punchContact(ServerPlayer p, ServerLevel level, Vec3 v) {
		Vec3 front = p.position().add(0, p.getBbHeight() * 0.5, 0).add(v.normalize().scale(0.9));
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 1.6)) {
			punchHit(p, level, e, v);
			set(p, "punch_id", 0);
			endDrive(p, 0.1);
			return true;
		}
		return false;
	}

	/** 14 and a big knockback along the dash. */
	public static void punchHit(ServerPlayer p, ServerLevel level, LivingEntity e, Vec3 dir) {
		AbilityHelpers.hurt(p, e, PUNCH_DAMAGE);
		Vec3 d = dir.lengthSqr() < 1.0e-4 ? p.getLookAngle() : dir.normalize();
		e.setDeltaMovement(e.getDeltaMovement().add(d.x * PUNCH_KNOCKBACK, Math.max(0.45, d.y * PUNCH_KNOCKBACK * 0.5 + 0.45),
				d.z * PUNCH_KNOCKBACK));
		e.hurtMarked = true;
		e.hasImpulse = true;
		fx(p, FlightFxEntity.PUNCH, mid(e).subtract(d.scale(e.getBbWidth() * 0.5)), d, 1.2f, null);
		level.sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, net.minecraft.sounds.SoundSource.PLAYERS,
				1.2f, 0.7f);
		level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS,
				0.4f, 1.6f);
	}

	private static void diveBomb(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (p.onGround()) {
			ctx.actionBar("message.projecthero.flight.need_air");
			return;
		}
		if (!spend(ctx, COST_DIVE)) {
			return;
		}
		Vec3 aim = AbilityHelpers.aimPoint(p, DIVE_AIM_RANGE);
		Vec3 dir = aim.subtract(p.position());
		dir = dir.lengthSqr() < 1.0e-4 ? new Vec3(0, -1, 0) : dir.normalize();
		if (dir.y > -0.35) {
			// "rocket down": an aim above the horizon still comes down steeply toward it
			dir = new Vec3(dir.x * 0.6, -1.0, dir.z * 0.6).normalize();
		}
		set(p, "dive_from_y", (float) p.getY() + 1000.0f);
		drive(p, dir.scale(DIVE_SPEED), 60, DRIVE_DIVE);
		MutationVisuals.play(p, "p03.dive");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.9f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f);
	}

	/** Dive Bomb damage for a dive from {@code height} blocks up: 12, rising to 30 at 30 blocks. */
	public static float diveDamage(double height) {
		double f = Math.max(0.0, Math.min(1.0, height / DIVE_FULL_HEIGHT));
		return (float) (DIVE_DAMAGE_MIN + (DIVE_DAMAGE_MAX - DIVE_DAMAGE_MIN) * f);
	}

	/** The dive lands: the 4-block ring. Returns how many were hit. */
	public static int diveImpact(ServerPlayer p, ServerLevel level) {
		double height = Math.max(0.0, res(p, "dive_from_y") - 1000.0f - p.getY());
		set(p, "dive_from_y", 0);
		set(p, "drive_ticks", 0);
		set(p, "drive_kind", 0);
		AbilityHelpers.launchSelf(p, Vec3.ZERO);
		float dmg = diveDamage(height) + com.projecthero.mod.hero.power.PowerCombos.meteorSlamBonus(p, KEY);
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), DIVE_RADIUS)) {
			if (AbilityHelpers.hurtBurst(p, e, dmg)) {
				hit++;
			}
			AbilityHelpers.knockbackFrom(e, p.position(), 1.2 + height * 0.03);
			AbilityHelpers.push(e, new Vec3(0, 0.45, 0));
		}
		p.resetFallDistance();
		MutationVisuals.play(p, "p03.land");
		fx(p, FlightFxEntity.CRATER, p.position().add(0, 0.05, 0), new Vec3(0, 1, 0), (float) DIVE_RADIUS, groundBelow(p));
		level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.3, p.getZ(), 2, 0.6, 0.1, 0.6, 0);
		level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 30, DIVE_RADIUS * 0.5, 0.1, DIVE_RADIUS * 0.5, 0.06);
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.2f, 0.8f);
		return hit;
	}

	// ================================================================ G: Grab & Carry / Orbital Drop

	private static void grab(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "orbit_id") > 0.5f) {
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, GRAB_RANGE);
		if (!grabbable(p, t)) {
			ctx.actionBar("message.projecthero.flight.no_target");
			return;
		}
		if (!spend(ctx, COST_GRAB)) {
			return;
		}
		set(p, "carry_id", t.getId());
		set(p, "carry_ticks", CARRY_MAX_TICKS);
		MutationVisuals.play(p, "p03.carry");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
		ctx.actionBar("message.projecthero.ability.grabbed");
	}

	private static LivingEntity heldBy(ServerPlayer p, ServerLevel level, String key) {
		int id = (int) res(p, key);
		if (id != 0 && level.getEntity(id) instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 24 * 24) {
			return le;
		}
		return null;
	}

	/** Parks the held creature at {@code at}. */
	private static void park(LivingEntity le, Vec3 at) {
		if (le instanceof ServerPlayer sp) {
			sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot());
		} else {
			le.setPos(at.x, at.y, at.z);
		}
		le.setDeltaMovement(Vec3.ZERO);
		le.fallDistance = 0;
		le.hurtMarked = true;
	}

	/** Where Grab &amp; Carry holds its creature: out in front at arm's length, along the look. */
	public static Vec3 carryPoint(Player p, LivingEntity le) {
		Vec3 look = p.getLookAngle();
		double reach = 1.25 + le.getBbWidth() * 0.5;
		return p.position().add(0, p.getBbHeight() * 0.55, 0).add(look.scale(reach)).subtract(0, le.getBbHeight() * 0.5, 0);
	}

	private static void carryTick(ServerPlayer p, ServerLevel level) {
		if (res(p, "carry_id") > 0.5f) {
			LivingEntity le = heldBy(p, level, "carry_id");
			float left = res(p, "carry_ticks");
			if (le == null || left <= 1.0f || !p.isAlive()) {
				dropCarried(p, level);
			} else {
				set(p, "carry_ticks", left - 1);
				park(le, carryPoint(p, le));
			}
		}
		if (res(p, "orbit_id") > 0.5f) {
			LivingEntity le = heldBy(p, level, "orbit_id");
			if (le == null || !p.isAlive()) {
				set(p, "orbit_id", 0);
			} else {
				// overhead, both hands
				park(le, p.position().add(0, p.getBbHeight() + 0.15, 0));
			}
		}
	}

	private static void dropCarried(ServerPlayer p, ServerLevel level) {
		set(p, "carry_id", 0);
		set(p, "carry_ticks", 0);
		MutationVisuals.stopIf(p, "p03.carry");
	}

	/** Grab &amp; Carry's second press: the creature is hurled along the look (the fall does the rest). */
	public static boolean throwCarried(ServerPlayer p) {
		ServerLevel level = AbilityHelpers.level(p);
		LivingEntity le = heldBy(p, level, "carry_id");
		dropCarried(p, level);
		if (le == null) {
			return false;
		}
		Vec3 v = p.getLookAngle().scale(2.4).add(0, 0.25, 0);
		le.setDeltaMovement(v);
		le.hurtMarked = true;
		le.hasImpulse = true;
		le.fallDistance = 0;
		MutationVisuals.play(p, "p03.throw");
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.8f, 0.9f);
		return true;
	}

	private static void orbitalDrop(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "orbit_id") > 0.5f || res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (res(p, "orbital_cd") > 0.5f) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(res(p, "orbital_cd") / 20.0f)));
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, GRAB_RANGE);
		if (!grabbable(p, t)) {
			ctx.actionBar("message.projecthero.flight.no_target");
			return;
		}
		if (!spend(ctx, COST_ORBITAL)) {
			return;
		}
		set(p, "orbital_cd", ORBITAL_CD);
		set(p, "orbit_id", t.getId());
		int ticks = (int) Math.round(ORBITAL_HEIGHT / ORBITAL_SPEED);
		drive(p, new Vec3(0, ORBITAL_SPEED, 0), ticks, DRIVE_RISE);
		park(t, p.position().add(0, p.getBbHeight() + 0.15, 0));
		MutationVisuals.play(p, "p03.orbit");
		fx(p, FlightFxEntity.TAKEOFF, p.position().add(0, 0.05, 0), new Vec3(0, 1, 0), 3.0f, groundBelow(p));
		AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 1.0f, 0.5f);
	}

	/** At the top of the Orbital Drop: the creature is let go -- a full-height fall. */
	public static void orbitalRelease(ServerPlayer p, ServerLevel level) {
		LivingEntity le = heldBy(p, level, "orbit_id");
		set(p, "orbit_id", 0);
		if (le == null) {
			return;
		}
		le.setDeltaMovement(0, -0.2, 0);
		le.hurtMarked = true;
		le.hasImpulse = true;
		le.fallDistance = 0;
		MutationVisuals.play(p, "p03.release");
		fx(p, FlightFxEntity.VAPOR_RING, p.position().add(0, p.getBbHeight(), 0), new Vec3(0, 1, 0), 2.0f, null);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.6f, 1.6f);
	}

	// ================================================================ X: Air Dash / Barrel Roll

	/** Which way the player is moving (their last known movement), or their look while still. */
	private static Vec3 movingDir(ServerPlayer p) {
		Vec3 mv = p.getKnownMovement();
		if (p.onGround()) {
			mv = new Vec3(mv.x, 0, mv.z);
		}
		if (mv.lengthSqr() > 0.0025) {
			return mv.normalize();
		}
		Vec3 look = p.getLookAngle();
		return p.onGround() ? BatchA.flatLook(p) : look;
	}

	private static void airDash(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (!spend(ctx, COST_DASH)) {
			return;
		}
		Vec3 dir = movingDir(p);
		if (p.onGround()) {
			dir = dir.add(0, 0.12, 0).normalize();
		}
		drive(p, dir.scale(DASH_BLOCKS / 4.0), 4, DRIVE_DASH);
		MutationVisuals.play(p, "p03.dash");
		fx(p, FlightFxEntity.VAPOR_RING, p.position().add(0, 0.9, 0), dir, 1.3f, null);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 0.9f, 1.5f);
	}

	private static void barrelRoll(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (!spend(ctx, COST_ROLL)) {
			return;
		}
		Vec3 look = p.getLookAngle();
		Vec3 right = look.cross(new Vec3(0, 1, 0));
		right = right.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : right.normalize();
		double lateral = p.getKnownMovement().dot(right);
		float side;
		if (Math.abs(lateral) > 0.05) {
			side = lateral > 0 ? 1f : -1f;
		} else {
			side = res(p, "roll_side") > 0.5f ? -1f : 1f; // alternate when not strafing
		}
		set(p, "roll_side", side > 0 ? 1 : 0);
		Vec3 forward = p.getKnownMovement().subtract(right.scale(lateral));
		Vec3 v = right.scale(side * ROLL_BLOCKS / 5.0).add(forward.scale(0.8));
		drive(p, v, 5, DRIVE_ROLL);
		set(p, "roll_ticks", ROLL_IFRAMES);
		MutationVisuals.play(p, side > 0 ? "p03.roll_right" : "p03.roll_left");
		AbilityHelpers.sound(p, SoundEvents.BREEZE_SLIDE, 1.0f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WHIRL, 0.6f, 1.6f);
	}

	// ================================================================ Z: Sonic Boom / Takeoff Blast

	private static void sonicBoom(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!spend(ctx, COST_BOOM)) {
			return;
		}
		sonicBoomBlast(p, ctx.level());
		if (HeroFlight.isFlying(p) && res(p, "drive_ticks") < 0.5f) {
			drive(p, p.getLookAngle().scale(2.6), 3, DRIVE_BOOM); // breaking the barrier: a surge forward
		}
		MutationVisuals.play(p, "p03.boom");
	}

	/** The 10-block cone: 16 and a launch. Returns how many were hit. */
	public static int sonicBoomBlast(ServerPlayer p, ServerLevel level) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		int hit = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(BOOM_RANGE * 0.5)), BOOM_RANGE * 0.5 + 2.0)) {
			Vec3 to = mid(e).subtract(eye);
			double d = to.length();
			if (d > BOOM_RANGE + e.getBbWidth() || d < 1.0e-3 || to.scale(1.0 / d).dot(look) < BOOM_CONE) {
				continue;
			}
			if (AbilityHelpers.hurtBurst(p, e, BOOM_DAMAGE)) {
				hit++;
			}
			Vec3 push = to.normalize().scale(1.8).add(0, 0.65, 0);
			e.setDeltaMovement(e.getDeltaMovement().add(push));
			e.hurtMarked = true;
			e.hasImpulse = true;
		}
		fx(p, FlightFxEntity.SONIC_CONE, eye.add(look.scale(0.6)), look, (float) BOOM_RANGE, null);
		level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * 2, eye.y + look.y * 2, eye.z + look.z * 2, 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, net.minecraft.sounds.SoundSource.PLAYERS, 1.2f, 1.2f);
		level.playSound(null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.7f);
		return hit;
	}

	private static void takeoffBlast(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (res(p, "drive_ticks") > 0.5f) {
			return;
		}
		if (!p.onGround()) {
			ctx.actionBar("message.projecthero.flight.need_ground");
			return;
		}
		if (!spend(ctx, COST_TAKEOFF)) {
			return;
		}
		takeoffKnockback(p);
		ensureFlying(p);
		int ticks = (int) Math.round(TAKEOFF_HEIGHT / 3.0);
		// v0.16.6 (user): the blast throws you the way you look (20 blocks along it), not just straight up -- never into the
		// ground you stand on, so the look is tipped up to at least TAKEOFF_MIN_RISE
		Vec3 dir = takeoffDirection(p.getLookAngle());
		drive(p, dir.scale(3.0), ticks, DRIVE_RISE);
		MutationVisuals.play(p, "p03.rise");
		fx(p, FlightFxEntity.TAKEOFF, p.position().add(0, 0.05, 0), dir, (float) TAKEOFF_RADIUS, groundBelow(p));
		AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 1.3f);
		AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.4f, 0.7f);
	}

	/** The Takeoff Blast's launch direction: the look, tipped up so it always leaves the ground (unit vector). */
	public static Vec3 takeoffDirection(Vec3 look) {
		Vec3 l = look.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : look.normalize();
		if (l.y >= TAKEOFF_MIN_RISE) {
			return l;
		}
		double flat = Math.sqrt(Math.max(0.0, l.x * l.x + l.z * l.z));
		double k = flat < 1.0e-6 ? 0.0 : Math.sqrt(1.0 - TAKEOFF_MIN_RISE * TAKEOFF_MIN_RISE) / flat;
		return flat < 1.0e-6 ? new Vec3(0, 1, 0) : new Vec3(l.x * k, TAKEOFF_MIN_RISE, l.z * k);
	}

	/** Everything within 5 blocks is blown away from the launch point. Returns how many. */
	public static int takeoffKnockback(ServerPlayer p) {
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), TAKEOFF_RADIUS)) {
			AbilityHelpers.knockbackFrom(e, p.position(), 2.0);
			AbilityHelpers.push(e, new Vec3(0, 0.5, 0));
			n++;
		}
		return n;
	}

	// ================================================================ V: Hover / Slipstream

	public static void setHover(ServerPlayer p, boolean on) {
		boolean was = res(p, "hover") > 0.5f;
		if (on == was) {
			return;
		}
		if (on) {
			ensureFlying(p);
			if (!HeroFlight.isFlying(p)) {
				return;
			}
			set(p, "hover", 1);
			set(p, "burn", 0);
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, HOVER_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			AbilityHelpers.launchSelf(p, Vec3.ZERO);
			MutationVisuals.play(p, "p03.hover");
			AbilityHelpers.sound(p, SoundEvents.BEACON_ACTIVATE, 0.4f, 1.8f);
		} else {
			set(p, "hover", 0);
			PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, HOVER_KB);
			MutationVisuals.stopIf(p, "p03.hover");
		}
	}

	public static void setSlipstream(ServerPlayer p, boolean on) {
		set(p, "slip", on ? 1 : 0);
		if (on) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.8f, 1.2f);
		}
	}

	// ================================================================ C: Updraft

	private static void updraft(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!spend(ctx, COST_UPDRAFT)) {
			return;
		}
		updraftLift(p);
		MutationVisuals.play(p, "p03.updraft");
		fx(p, FlightFxEntity.UPDRAFT, p.position(), new Vec3(0, 1, 0), (float) UPDRAFT_RADIUS, null);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.2f, 0.6f);
		AbilityHelpers.sound(p, SoundEvents.ELYTRA_FLYING, 0.5f, 1.6f);
	}

	/** v ~1.05 straight up carries a mob about 6 blocks (gravity 0.08, drag 0.98). Returns how many were lifted. */
	public static int updraftLift(ServerPlayer p) {
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), UPDRAFT_RADIUS)) {
			Vec3 v = e.getDeltaMovement();
			e.setDeltaMovement(v.x * 0.3, 1.05, v.z * 0.3);
			e.hurtMarked = true;
			e.hasImpulse = true;
			e.fallDistance = 0;
			n++;
		}
		return n;
	}

	// ================================================================ damage

	/**
	 * Flight's damage rule, read by {@link com.projecthero.mod.hero.power.HeroDamageRules}: -1 for "immune" -- falls
	 * always, projectiles during a Barrel Roll -- otherwise 1.
	 */
	public static float damageFactor(ServerPlayer p, DamageSource source) {
		if (source.is(DamageTypeTags.IS_FALL)) {
			p.resetFallDistance();
			return -1.0f;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE) && rolling(p)) {
			return -1.0f;
		}
		return 1.0f;
	}

	// ================================================================ passives

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, HOVER_KB);
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the transient states too
					set(player, "hover", 0);
					set(player, "slip", 0);
					set(player, "burn", 0);
					set(player, "carry_id", 0);
					set(player, "orbit_id", 0);
					set(player, "drive_ticks", 0);
				}
				MutationVisuals.stop(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// respawn-safe re-apply of Hover's knockback immunity
			if (res(player, "hover") > 0.5f) {
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, HOVER_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			} else {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, HOVER_KB);
			}
		});
	}

	// ================================================================ synced visuals + HUD (called from RevampBatchA)

	public static void registerVisuals() {
		MutationVisuals.registerFlag("p03.hover", FlightHandlers::hovering);
		MutationVisuals.registerFlag("p03.slip", p -> slipstream(p) && flying(p));
		MutationVisuals.registerFlag("p03.burn", p -> afterburner(p) && flying(p));
		// the wind-streak sheath sprint-flying / on Afterburner
		MutationVisuals.registerFlag("p03.wind", p -> owns(p) && flying(p) && (afterburner(p) || (p.isSprinting() && !hovering(p))));
		MutationVisuals.registerValue("p03.speed", p -> owns(p) && flying(p) ? (afterburner(p) ? 4 : p.isSprinting() ? 3 : 0) : 0);
		MutationVisuals.registerValue("p03.carry", FlightHandlers::carriedId);
		MutationVisuals.registerValue("p03.drive", FlightHandlers::driveKind);
	}

	public static void registerMeters() {
		int text = 0xFFDDEBFF;
		MutationMeters.register(new MutationMeters.Spec(KEY, "lift", MutationMeters.Kind.RESERVE, MutationMeters.Style.HAIRLINE,
				"Lift", MAX_LIFT, 0xFF8FD3FF, true, true, false, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "orbital_cd", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Orbital Drop", ORBITAL_CD, 0xFF7A8A9A, false, false, true, text));
		MutationMeters.register(new MutationMeters.Spec(KEY, "carry_ticks", MutationMeters.Kind.TIMER, MutationMeters.Style.HAIRLINE,
				"Carry", CARRY_MAX_TICKS, 0xFF8FD3FF, false, false, true, text));
	}

	/** For the gametests: the Lift meter set directly. */
	public static void setLift(ServerPlayer p, float value) {
		seed(p);
		set(p, "lift", Math.max(0, Math.min(MAX_LIFT, value)));
	}
}
