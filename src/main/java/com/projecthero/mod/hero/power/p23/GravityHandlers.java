package com.projecthero.mod.hero.power.p23;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Power 23 — Gravity Manipulation.
 *
 * <p>v0.13.22 revamp (batch D): <b>changing which way gravity pulls</b>. R Gravity Push (sneak: grab/throw),
 * G Gravity Crush (sneak: a small Gravity Well), X Zero-G, Z Black Hole, V Gravity Lift / slam, C Gravitational
 * Nexus (a thin violet field shell), H Invert (the target falls UP, then crashes back down), N Heavy Ground (a zone
 * where nothing can jump and fliers are dragged out of the sky).
 */
public final class GravityHandlers {
	private static final String KEY = "power_23_gravity_manipulation";
	private static final net.minecraft.resources.ResourceLocation ZERO_G = com.projecthero.mod.ProjectHeroMod.id("zero_g_grav");
	private static final net.minecraft.resources.ResourceLocation KB = com.projecthero.mod.ProjectHeroMod.id("gravity_kb");
	private static final net.minecraft.resources.ResourceLocation NEXUS_ATK = com.projecthero.mod.ProjectHeroMod.id("gravity_nexus_atk");
	private static final float MAX_NEXUS = 115.0f;
	private static final float NEXUS_DRAIN = 100.0f / (35 * 20);
	private static final float NEXUS_REGEN = MAX_NEXUS / (25 * 20);

	private static final float PUSH_DAMAGE = 12.0f;
	private static final float THROW_DAMAGE = 10.0f;
	private static final float CRUSH_DPS = 5.0f;
	private static final float WELL_DPS = 2.5f;
	private static final float HOLE_DPS = 7.0f;
	private static final float SLAM_DAMAGE = 17.0f;
	private static final float NEXUS_BONUS = 12.0f;
	private static final int PUSH_CD = 34;
	private static final int CRUSH_CD = 17 * 20;
	private static final int REPULSE_CD = 170;
	private static final int HOLE_CD = 102 * 20;
	private static final int SLAM_CD = 21 * 20;
	private static final int NEXUS_CD = 17 * 20;

	// --- H: Invert ---
	public static final int INVERT_RISE_TICKS = 30;
	private static final double INVERT_RANGE = 30.0;
	private static final float INVERT_CRASH_DAMAGE = 10.0f;
	private static final float INVERT_CEILING_DAMAGE = 6.0f;

	// --- N: Heavy Ground ---
	public static final int HEAVY_TICKS = 8 * 20;
	private static final double HEAVY_RADIUS = 7.0;
	private static final double HEAVY_HEIGHT = 14.0;
	private static final float HEAVY_DPS = 3.0f;

	/** Live Gravity Lifts (V, plain) -- one list of up to 10 targets per caster. */
	private static final Map<UUID, List<Lift>> LIFTS = new HashMap<>();
	/** Live Black Holes (Z) -- one per caster. */
	private static final Map<UUID, BlackHole> HOLES = new HashMap<>();
	/** Inverted creatures (H): entity id -> state. */
	private static final Map<Integer, Invert> INVERTS = new HashMap<>();
	/** Heavy Ground zones (N) -- one per caster. */
	private static final Map<UUID, HeavyZone> HEAVY = new HashMap<>();

	private GravityHandlers() {
	}

	public static void clearSessionState() {
		LIFTS.clear();
		HOLES.clear();
		INVERTS.clear();
		HEAVY.clear();
	}

	private static boolean nexusActive(ServerPlayer p) {
		var power = Powers.byKey(KEY);
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** True while Gravitational Nexus (C) is on -- drives the violet field shell. */
	public static boolean fieldActive(ServerPlayer p) {
		return nexusActive(p);
	}

	/** +12 ability damage while Gravitational Nexus is active. */
	private static float nexusBonus(ServerPlayer p) {
		return nexusActive(p) ? NEXUS_BONUS : 0.0f;
	}

	private static boolean friendly(ServerPlayer p, LivingEntity e) {
		// v0.14.20: squadmates, plus everything the shared rule 1 spares (own pets, PvP-off players ...)
		return Squads.areAllies(p, e) || !com.projecthero.mod.combat.HeroTargets.canHarm(p, e);
	}

	public static void register() {
		// R -- Gravity Push, 50-block range. Shift+R is a grab: press again (either way) to throw.
		AbilityHandlers.register(KEY, "gravity_push", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.2, THROW_DAMAGE + nexusBonus(p));
				MutationVisuals.play(p, "throw_right");
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
				ctx.triggerCooldown(PUSH_CD);
				return;
			}
			if (p.isShiftKeyDown()) {
				if (GrabHelper.tryGrab(ctx, 16.0, 160)) {
					ctx.actionBar("message.projecthero.ability.grabbed");
					BatchDFx.distortion(ctx.level(), p.position().add(0, 1, 0), 0.5, 12);
					MutationVisuals.play(p, "grab_pull");
				}
				return;
			}
			LivingEntity direct = AbilityHelpers.raycastEntity(p, 50.0);
			Vec3 front = direct != null ? direct.position() : p.getEyePosition().add(p.getLookAngle().scale(50.0));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				if (friendly(p, e)) {
					continue;
				}
				AbilityHelpers.push(e, e.position().subtract(p.getEyePosition()).normalize().scale(2.6).add(0, 0.5, 0));
				AbilityHelpers.hurt(p, e, PUSH_DAMAGE + nexusBonus(p));
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.PORTAL, 20, 0.6);
			BatchDFx.distortion(ctx.level(), front.add(0, 1, 0), 0.8, 14);
			AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), front.add(0, 1, 0), BatchDFx.GRAVITY, 1.0);
			MutationVisuals.play(p, "cast_right");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.4f);
			ctx.triggerCooldown(PUSH_CD);
		}, ctx -> GrabHelper.tick(ctx, 3.0)));

		// G -- Gravity Crush: hold to crush a target for up to 8s. Shift+G is a smaller, separate Gravity Well.
		AbilityHandlers.register(KEY, "gravity_crush", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						return;
					}
					Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(20));
					ctx.setResource("well_cx", (float) center.x, 1.0e9f);
					ctx.setResource("well_cy", (float) center.y, 1.0e9f);
					ctx.setResource("well_cz", (float) center.z, 1.0e9f);
					ctx.setResource("well_ticks2", 120, 120);
					MutationVisuals.play(p, "cast_two_hand");
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
					ctx.triggerCooldown(CRUSH_CD);
					return;
				}
				if (ctx.resource("crush_hold") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("crush_hold", 1, 1);
				ctx.setResource("crush_hold_ticks", 0, 160);
				MutationVisuals.play(p, "p23.crush");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("crush_hold") > 0.5f) {
					ctx.setResource("crush_hold", 0, 1);
					ctx.setResource("crush_hold_ticks", 0, 160);
					MutationVisuals.stopIf(ctx.player(), "p23.crush");
					ctx.triggerCooldown(CRUSH_CD);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				int wt = (int) ctx.resource("well_ticks2");
				if (wt > 0) {
					wt--;
					ctx.setResource("well_ticks2", wt, 120);
					Vec3 center = new Vec3(ctx.resource("well_cx"), ctx.resource("well_cy"), ctx.resource("well_cz"));
					for (LivingEntity e : AbilityHelpers.hostilesAround(p, center, 10.0)) {
						if (friendly(p, e)) {
							continue;
						}
						Vec3 pull = center.subtract(e.position());
						if (pull.lengthSqr() > 0.25) {
							e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.25)));
							e.hurtMarked = true;
						}
						if ((120 - wt) % 20 == 0) {
							AbilityHelpers.hurt(p, e, WELL_DPS + nexusBonus(p));
						}
					}
					if (wt % 4 == 0) {
						level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 12, 1.0, 1.0, 1.0, 0.2);
						BatchDFx.distortion(level, center, 1.0, 8);
					}
				}
				if (ctx.resource("crush_hold") < 0.5f) {
					return;
				}
				int held = (int) ctx.resource("crush_hold_ticks") + 1;
				if (held >= 8 * 20) {
					ctx.setResource("crush_hold", 0, 1);
					ctx.setResource("crush_hold_ticks", 0, 160);
					MutationVisuals.stopIf(p, "p23.crush");
					ctx.triggerCooldown(CRUSH_CD);
					return;
				}
				ctx.setResource("crush_hold_ticks", held, 160);
				MutationVisuals.ensure(p, "p23.crush");
				LivingEntity crushTarget = AbilityHelpers.raycastEntity(p, 50.0);
				Vec3 center = crushTarget != null ? crushTarget.position()
						: p.getEyePosition().add(p.getLookAngle().scale(50.0));
				for (LivingEntity e : AbilityHelpers.living(level, center, 2.8, le -> le != p && !friendly(p, le))) {
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
					if (held % 20 == 0) {
						AbilityHelpers.hurt(p, e, CRUSH_DPS + nexusBonus(p));
					}
				}
				if (held % 5 == 0) {
					level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, center.x, center.y, center.z, 8, 1.5, 1.0, 1.5, 0.0);
					BatchDFx.distortion(level, center.add(0, 0.8, 0), 1.2, 8);
				}
			}
		});

		// X -- Zero-G: reduced personal gravity. Shift+toggle also fires Gravity Repulsion.
		AbilityHandlers.register(KEY, "zero_g", Handlers.toggle(
				ctx -> {
					PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					MutationVisuals.play(ctx.player(), "leap");
					BatchDFx.distortion(ctx.level(), ctx.player().position().add(0, 0.2, 0), 0.6, 12);
					if (ctx.player().isShiftKeyDown()) {
						repulse(ctx);
					}
				},
				ctx -> {
					PowerToggles.clearModifier(ctx.player(), Attributes.GRAVITY, ZERO_G);
					MutationVisuals.stopIf(ctx.player(), "float_arms");
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					PowerToggles.modifier(p, Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					p.resetFallDistance();
					if (!p.onGround()) {
						BatchDFx.ensureIdle(p, "float_arms");
						if (p.tickCount % 4 == 0) {
							ctx.level().sendParticles(BatchDFx.GRAVITY, p.getX(), p.getY() + 0.1, p.getZ(), 2, 0.3, 0.05, 0.3, 0.0);
						}
					} else {
						MutationVisuals.stopIf(p, "float_arms");
					}
				}));

		// Z -- hold for 5 seconds to charge a Black Hole 5 blocks ahead.
		AbilityHandlers.register(KEY, "gravity_well", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("bh_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("bh_charging", 1, 1);
				ctx.setResource("bh_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
				MutationVisuals.play(ctx.player(), "p23.well_charge");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("bh_charging") > 0.5f) {
					ctx.setResource("bh_charging", 0, 1);
					ctx.setResource("bh_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "p23.well_charge");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("bh_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("bh_charge_start");
				ctx.setResource("bh_charge", Math.min(100f, held), 100);
				MutationVisuals.ensure(p, "p23.well_charge");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
					Vec3 core = p.getEyePosition().add(p.getLookAngle().scale(1.2));
					BatchDFx.distortion(ctx.level(), core, 0.15 + held / 400.0, 4);
				}
				if (held >= 5 * 20) {
					ctx.setResource("bh_charging", 0, 1);
					ctx.setResource("bh_charge", 0, 100);
					Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(5));
					HOLES.put(p.getUUID(), new BlackHole(ctx.level(), p, center));
					MutationVisuals.play(p, "cast_two_hand");
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.3f);
					ctx.triggerCooldown(HOLE_CD);
				}
			}
		});

		// V -- Gravity Lift: mark up to 10 targets. Shift+V slams every marked target down.
		AbilityHandlers.register(KEY, "levitate", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			List<Lift> mine = LIFTS.computeIfAbsent(p.getUUID(), id -> new ArrayList<>());
			if (p.isShiftKeyDown()) {
				if (mine.isEmpty()) {
					return;
				}
				for (Lift l : mine) {
					if (ctx.level().getEntity(l.id) instanceof LivingEntity le && le.isAlive()) {
						le.removeEffect(MobEffects.LEVITATION);
						le.setDeltaMovement(le.getDeltaMovement().x, -1.6, le.getDeltaMovement().z);
						le.hurtMarked = true;
						AbilityHelpers.hurt(p, le, SLAM_DAMAGE + nexusBonus(p));
						BatchDFx.distortion(ctx.level(), BatchDFx.centre(le), 0.5, 8);
					}
				}
				mine.clear();
				MutationVisuals.play(p, "ground_pound");
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.6f);
				ctx.triggerCooldown(SLAM_CD);
				return;
			}
			if (mine.size() >= 10) {
				ctx.actionBar("message.projecthero.gravity.lift_full");
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
			if (t == null || friendly(p, t)) {
				return;
			}
			mine.add(new Lift(t.getId(), p.level().getGameTime() + 400));
			t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 400, 0, false, false, true));
			ctx.level().sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.2);
			BatchDFx.distortion(ctx.level(), BatchDFx.centre(t), 0.4, 8);
			MutationVisuals.play(p, "p23.lift");
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.7f);
		}));

		// C -- Gravitational Nexus: a drain-bar combat stance.
		AbilityHandlers.register(KEY, "gravity_field", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					ctx.setToggled(false);
					return;
				}
				ModeMeter.ensureSeeded(ctx, "nexus_bar", MAX_NEXUS);
				if (!ModeMeter.hasCharge(ctx, "nexus_bar", 5.0f)) {
					ctx.actionBar("message.projecthero.gravity.nexus_low");
					ctx.setToggled(false);
					return;
				}
				PowerToggles.modifier(ctx.player(), Attributes.ATTACK_DAMAGE, NEXUS_ATK, 8.0, AttributeModifier.Operation.ADD_VALUE);
				MutationVisuals.play(ctx.player(), "power_up");
				BatchDFx.distortion(ctx.level(), ctx.player().position().add(0, 1, 0), 0.8, 30);
				AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.6f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				endNexus(ctx);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				AbilityHelpers.modeAura(p, ParticleTypes.REVERSE_PORTAL, 4);
				if (p.tickCount % 5 == 0) {
					BatchDFx.ring(ctx.level(), p.position().add(0, 0.1, 0), 3.0, BatchDFx.GRAVITY, 12, p.tickCount * 0.05);
				}
				p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false));
				p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20, 0, false, false, false));
				for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 3.0)) {
					if (friendly(p, e)) {
						continue;
					}
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
					if (p.tickCount % 20 == 0) {
						AbilityHelpers.hurt(p, e, 2.5f);
					}
				}
				if (!ModeMeter.drain(ctx, "nexus_bar", MAX_NEXUS, NEXUS_DRAIN)) {
					ctx.actionBar("message.projecthero.gravity.nexus_out");
					endNexus(ctx);
					ctx.setToggled(false);
				}
			}
		});

		// H -- Invert: the looked-at target falls UP, then crashes back down.
		AbilityHandlers.register(KEY, "invert", Handlers.instant(GravityHandlers::invert));

		// N -- Heavy Ground: a crushing gravity zone at your aim point.
		AbilityHandlers.register(KEY, "heavy_ground", Handlers.instant(GravityHandlers::heavyGround));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB);
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, NEXUS_ATK);
				PowerToggles.clearModifier(player, Attributes.GRAVITY, ZERO_G);
				player.setInvulnerable(false);
			} else {
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KB, 0.5, AttributeModifier.Operation.ADD_VALUE);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player ->
				ModeMeter.regen(player, Powers.byKey(KEY), "nexus_bar", MAX_NEXUS, NEXUS_REGEN, nexusActive(player)));
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			List<Lift> mine = LIFTS.get(player.getUUID());
			if (mine != null && !mine.isEmpty() && player.level() instanceof ServerLevel level) {
				long now = level.getGameTime();
				double floor = player.getY();
				mine.removeIf(l -> l.expiry <= now || !(level.getEntity(l.id) instanceof LivingEntity le) || !le.isAlive());
				for (Lift l : mine) {
					if (level.getEntity(l.id) instanceof LivingEntity le && le.getY() > floor + 10.0) {
						le.setPos(le.getX(), floor + 10.0, le.getZ());
						le.setDeltaMovement(le.getDeltaMovement().x, Math.min(0, le.getDeltaMovement().y), le.getDeltaMovement().z);
					}
				}
			}
			BlackHole hole = HOLES.get(player.getUUID());
			if (hole != null && !hole.tick()) {
				HOLES.remove(player.getUUID());
			}
			var power = Powers.byKey(KEY);
			HeavyZone zone = HEAVY.get(player.getUUID());
			if (power != null) {
				ExperimentalPowers.setResource(player, power, "heavy_left", zone == null ? 0 : zone.left, HEAVY_TICKS);
			}
		});
	}

	private static void repulse(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 7.0)) {
			if (friendly(p, e)) {
				continue;
			}
			AbilityHelpers.push(e, e.position().subtract(p.position()).normalize().scale(1.8).add(0, 0.3, 0));
			e.hurtMarked = true;
		}
		AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.PORTAL, 30, 1.2);
		BatchDFx.distortion(ctx.level(), p.position().add(0, 1, 0), 2.0, 30);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
		ctx.triggerCooldown(REPULSE_CD);
	}

	private static void endNexus(AbilityContext ctx) {
		PowerToggles.clearModifier(ctx.player(), Attributes.ATTACK_DAMAGE, NEXUS_ATK);
		ctx.triggerCooldown(NEXUS_CD);
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}

	// ================================================================= H: Invert

	private static void invert(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, INVERT_RANGE);
		if (t == null || friendly(p, t) || !AbilityHelpers.isValidGrabTarget(t, p) || INVERTS.containsKey(t.getId())) {
			ctx.actionBar("message.projecthero.gravity.no_target");
			return;
		}
		startInvert(p, t);
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), BatchDFx.centre(t), BatchDFx.GRAVITY, 1.5);
		MutationVisuals.play(p, "p23.invert");
		AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.5f);
		ctx.triggerCooldown();
	}

	/** Flips gravity on {@code t} (public for tests). */
	public static void startInvert(ServerPlayer owner, LivingEntity t) {
		INVERTS.put(t.getId(), new Invert((ServerLevel) t.level(), owner.getUUID()));
		t.setDeltaMovement(t.getDeltaMovement().x * 0.3, 0.6, t.getDeltaMovement().z * 0.3);
		t.hurtMarked = true;
	}

	public static boolean isInverted(LivingEntity t) {
		return INVERTS.containsKey(t.getId());
	}

	private static final class Invert {
		private final ServerLevel level;
		private final UUID owner;
		private int age;
		private boolean falling;

		Invert(ServerLevel level, UUID owner) {
			this.level = level;
			this.owner = owner;
		}

		/** @return true to keep going. */
		boolean tick(MinecraftServer server, int id) {
			age++;
			if (!(level.getEntity(id) instanceof LivingEntity le) || !le.isAlive() || age > INVERT_RISE_TICKS + 80) {
				return false;
			}
			ServerPlayer p = server.getPlayerList().getPlayer(owner);
			if (!falling) {
				// "falling" upward: steady acceleration into the sky, no fall damage building up yet
				double vy = Math.min(0.75, le.getDeltaMovement().y + 0.09);
				le.setDeltaMovement(le.getDeltaMovement().x * 0.85, Math.max(0.35, vy), le.getDeltaMovement().z * 0.85);
				le.fallDistance = 0.0f;
				le.hurtMarked = true;
				if (le.verticalCollision && age > 2) {
					// slammed into a ceiling on the way up
					if (p != null) {
						AbilityHelpers.hurtBurst(p, le, INVERT_CEILING_DAMAGE);
					}
					age = INVERT_RISE_TICKS;
				}
				if (age % 2 == 0) {
					level.sendParticles(BatchDFx.GRAVITY, le.getX(), le.getY(), le.getZ(), 4, 0.3, 0.1, 0.3, 0.0);
					level.sendParticles(ParticleTypes.REVERSE_PORTAL, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(),
							3, 0.3, 0.4, 0.3, 0.05);
				}
				if (age >= INVERT_RISE_TICKS) {
					falling = true;
					le.setDeltaMovement(le.getDeltaMovement().x * 0.3, -1.8, le.getDeltaMovement().z * 0.3);
					le.hurtMarked = true;
					level.sendParticles(BatchDFx.GRAVITY, le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 16, 0.4, 0.2, 0.4, 0.0);
					level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
							net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.6f);
				}
				return true;
			}
			// the crash: gravity comes back doubled until they hit the ground
			le.setDeltaMovement(le.getDeltaMovement().x, Math.min(le.getDeltaMovement().y - 0.15, -1.2), le.getDeltaMovement().z);
			le.hurtMarked = true;
			if (le.onGround()) {
				if (p != null) {
					AbilityHelpers.hurtBurst(p, le, INVERT_CRASH_DAMAGE);
					for (LivingEntity near : AbilityHelpers.enemiesAround(p, le.position(), 3.0)) {
						if (near != le && !Squads.areAllies(p, near)) {
							AbilityHelpers.hurt(p, near, INVERT_CRASH_DAMAGE * 0.5f);
							AbilityHelpers.knockbackFrom(near, le.position(), 0.8);
						}
					}
				}
				level.sendParticles(ParticleTypes.EXPLOSION, le.getX(), le.getY() + 0.2, le.getZ(), 1, 0, 0, 0, 0);
				BatchDFx.ring(level, le.position().add(0, 0.1, 0), 1.6, BatchDFx.GRAVITY, 16, 0);
				level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
						net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.6f);
				return false;
			}
			return true;
		}
	}

	// ================================================================= N: Heavy Ground

	private static void heavyGround(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, 24.0);
		Vec3 center = bhr.getType() == HitResult.Type.BLOCK ? bhr.getLocation() : p.position();
		HEAVY.put(p.getUUID(), new HeavyZone(ctx.level(), center));
		BatchDFx.ring(ctx.level(), center.add(0, 0.1, 0), HEAVY_RADIUS, BatchDFx.GRAVITY, 40, 0);
		ctx.level().sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.3, center.z, 1, 0, 0, 0, 0);
		MutationVisuals.play(p, "ground_pound");
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.4f);
		AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
		ctx.triggerCooldown();
	}

	/** Test hook: whether {@code p} has a live Heavy Ground zone. */
	public static boolean hasHeavyGround(ServerPlayer p) {
		return HEAVY.containsKey(p.getUUID());
	}

	private static final class HeavyZone {
		private final ServerLevel level;
		private final Vec3 center;
		private int left = HEAVY_TICKS;

		HeavyZone(ServerLevel level, Vec3 center) {
			this.level = level;
			this.center = center;
		}

		boolean tick(ServerPlayer owner) {
			if (--left <= 0 || owner == null || !owner.isAlive() || owner.level() != level) {
				return false;
			}
			int age = HEAVY_TICKS - left;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
					new net.minecraft.world.phys.AABB(center.x - HEAVY_RADIUS, center.y - 2, center.z - HEAVY_RADIUS,
							center.x + HEAVY_RADIUS, center.y + HEAVY_HEIGHT, center.z + HEAVY_RADIUS),
					x -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, x))) { // v0.14.20: lingering field, rule 2
				double dx = e.getX() - center.x;
				double dz = e.getZ() - center.z;
				if (dx * dx + dz * dz > HEAVY_RADIUS * HEAVY_RADIUS || Squads.areAllies(owner, e)) {
					continue;
				}
				if (e instanceof net.minecraft.world.entity.player.Player && !AbilityHelpers.enemiesAround(owner, e.position(), 0.5).contains(e)) {
					continue; // players only when PvP ability effects are on
				}
				if (!e.onGround()) {
					// dragged out of the sky
					e.setDeltaMovement(e.getDeltaMovement().x * 0.8, Math.min(e.getDeltaMovement().y - 0.25, -0.6),
							e.getDeltaMovement().z * 0.8);
					e.hurtMarked = true;
				} else if (e.getDeltaMovement().y > 0.0) {
					e.setDeltaMovement(e.getDeltaMovement().x, 0.0, e.getDeltaMovement().z);
					e.hurtMarked = true;
				}
				if (age % 10 == 0) {
					AbilityHelpers.applyControl(e, MobEffects.JUMP, 20, -10);
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
				}
				if (age % 20 == 0) {
					AbilityHelpers.hurt(owner, e, HEAVY_DPS);
				}
			}
			if (age % 3 == 0) {
				for (int i = 0; i < 6; i++) {
					double a = level.random.nextDouble() * Math.PI * 2;
					double r = Math.sqrt(level.random.nextDouble()) * HEAVY_RADIUS;
					level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, center.x + Math.cos(a) * r,
							center.y + 3 + level.random.nextDouble() * 4, center.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
				}
				BatchDFx.ring(level, center.add(0, 0.15, 0), HEAVY_RADIUS, BatchDFx.GRAVITY, 20, age * 0.05);
				level.sendParticles(BatchDFx.GRAVITY, center.x, center.y + 0.1, center.z, 10, HEAVY_RADIUS * 0.5, 0.02,
						HEAVY_RADIUS * 0.5, 0.0);
			}
			return true;
		}
	}

	// ================================================================= world tick

	/** Inverts and Heavy Ground zones (from {@code RevampBatchD.serverTick}). */
	public static void worldTick(MinecraftServer server) {
		if (!INVERTS.isEmpty()) {
			INVERTS.entrySet().removeIf(e -> !e.getValue().tick(server, e.getKey()));
		}
		if (!HEAVY.isEmpty()) {
			HEAVY.entrySet().removeIf(e -> !e.getValue().tick(server.getPlayerList().getPlayer(e.getKey())));
		}
	}

	private record Lift(int id, long expiry) {
	}

	/** One active Black Hole: 30-block pull, 7 damage inside 10 blocks, destructive, 15s. */
	private static final class BlackHole {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final Vec3 center;
		private int age;

		BlackHole(ServerLevel level, ServerPlayer owner, Vec3 center) {
			this.level = level;
			this.owner = owner;
			this.center = center;
		}

		boolean tick() {
			if (age >= 15 * 20 || !owner.isAlive() || owner.hasDisconnected()) {
				return false;
			}
			age++;
			// v0.14.20: a 30-block black hole is rule 2 -- it never swallows the farm or the village
			for (LivingEntity e : AbilityHelpers.living(level, center, 30.0,
					le -> com.projecthero.mod.combat.HeroTargets.isHostile(owner, le) && !Squads.areAllies(owner, le))) {
				Vec3 pull = center.subtract(e.position());
				double dist = pull.length();
				if (dist > 0.3) {
					e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.5)));
					e.hurtMarked = true;
				}
				if (dist <= 10.0 && age % 20 == 0) {
					AbilityHelpers.hurt(owner, e, HOLE_DPS + nexusBonus(owner));
				}
			}
			if (AbilityHelpers.canGrief() && age % 4 == 0) {
				BlockPos c = BlockPos.containing(center);
				for (BlockPos bp : BlockPos.betweenClosed(c.offset(-2, -2, -2), c.offset(2, 2, 2))) {
					if (bp.distToCenterSqr(center.x, center.y, center.z) <= 4.5
							&& !level.getBlockState(bp).isAir()
							&& level.getBlockState(bp).getDestroySpeed(level, bp) >= 0
							&& level.getBlockState(bp).getDestroySpeed(level, bp) < 50.0f) {
						level.destroyBlock(bp, false);
					}
				}
			}
			if (age % 2 == 0) {
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 20, 1.5, 1.5, 1.5, 0.4);
				level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 8, 0.6, 0.6, 0.6, 0.02);
				BatchDFx.ring(level, center, 2.2, BatchDFx.GRAVITY, 12, age * 0.2);
			}
			if (age % 20 == 0) {
				level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_HEARTBEAT,
						net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 0.3f);
			}
			return true;
		}
	}
}
