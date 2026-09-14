package com.projecthero.mod.hero.power.p23;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/** Power 23 — Gravity Manipulation. C cycles Low / Normal / High personal gravity. */
public final class GravityHandlers {
	private static final String KEY = "power_23_gravity_manipulation";
	private static final net.minecraft.resources.ResourceLocation GRAV = com.projecthero.mod.ProjectHeroMod.id("gravity_field_grav");
	private static final net.minecraft.resources.ResourceLocation ZERO_G = com.projecthero.mod.ProjectHeroMod.id("zero_g_grav");
	private static final net.minecraft.resources.ResourceLocation KB = com.projecthero.mod.ProjectHeroMod.id("gravity_kb");
	// cycle starts at Normal (0): Normal -> High -> Low -> Normal ...
	private static final String[] MODES = {"normal", "high", "low"};

	/** Live Gravity Lifts (V, plain) -- one list of up to 10 targets per caster. */
	private static final java.util.Map<java.util.UUID, List<Lift>> LIFTS = new java.util.HashMap<>();
	/** Live Black Holes (Z) -- one per caster. */
	private static final java.util.Map<java.util.UUID, BlackHole> HOLES = new java.util.HashMap<>();

	private GravityHandlers() {
	}

	public static void clearSessionState() {
		LIFTS.clear();
		HOLES.clear();
	}

	public static void register() {
		// R -- Gravity Push. Shift+R is a grab: press again (either way) to throw.
		AbilityHandlers.register(KEY, "gravity_push", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.2, 8.0f);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.2f);
				ctx.triggerCooldown(2 * 20);
				return;
			}
			if (p.isShiftKeyDown()) {
				if (GrabHelper.tryGrab(ctx, 16.0, 160)) {
					ctx.actionBar("message.projecthero.ability.grabbed");
					ctx.level().sendParticles(ParticleTypes.PORTAL, p.getX(), p.getY() + 1, p.getZ(), 15, 0.4, 0.6, 0.4, 0.2);
				}
				return;
			}
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(4));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.push(e, e.position().subtract(p.getEyePosition()).normalize().scale(2.6).add(0, 0.5, 0));
				AbilityHelpers.hurt(p, e, 10.0f);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.PORTAL, 20, 0.6);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.4f);
			ctx.triggerCooldown(2 * 20);
		}, ctx -> GrabHelper.tick(ctx, 3.0)));

		// G -- Gravity Crush: hold to crush everything in a 4-block zone in front of you, up to 8s, shown
		// on a bar; 20s cooldown once released. Shift+G is a smaller, separate Gravity Well.
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
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
					ctx.triggerCooldown(20 * 20);
					return;
				}
				if (ctx.resource("crush_hold") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("crush_hold", 1, 1);
				ctx.setResource("crush_hold_ticks", 0, 160);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("crush_hold") > 0.5f) {
					ctx.setResource("crush_hold", 0, 1);
					ctx.setResource("crush_hold_ticks", 0, 160);
					ctx.triggerCooldown(20 * 20);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				ServerLevel level = ctx.level();
				// Shift+G: the smaller Gravity Well.
				int wt = (int) ctx.resource("well_ticks2");
				if (wt > 0) {
					wt--;
					ctx.setResource("well_ticks2", wt, 120);
					Vec3 center = new Vec3(ctx.resource("well_cx"), ctx.resource("well_cy"), ctx.resource("well_cz"));
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, center, 10.0)) {
						Vec3 pull = center.subtract(e.position());
						if (pull.lengthSqr() > 0.25) {
							e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.25)));
							e.hurtMarked = true;
						}
						if ((120 - wt) % 20 == 0) {
							AbilityHelpers.hurt(p, e, 2.0f);
						}
					}
					if (wt % 4 == 0) {
						level.sendParticles(ParticleTypes.PORTAL, center.x, center.y, center.z, 12, 1.0, 1.0, 1.0, 0.2);
					}
				}
				// plain G: the crush hold.
				if (ctx.resource("crush_hold") < 0.5f) {
					return;
				}
				int held = (int) ctx.resource("crush_hold_ticks") + 1;
				if (held >= 8 * 20) {
					ctx.setResource("crush_hold", 0, 1);
					ctx.setResource("crush_hold_ticks", 0, 160);
					ctx.triggerCooldown(20 * 20);
					return;
				}
				ctx.setResource("crush_hold_ticks", held, 160);
				Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(4));
				for (LivingEntity e : AbilityHelpers.living(level, center, 2.8, le -> le != p)) {
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 3);
					if (held % 20 == 0) {
						AbilityHelpers.hurt(p, e, 4.0f);
					}
				}
				if (held % 5 == 0) {
					level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, center.x, center.y, center.z, 8, 1.5, 1.0, 1.5, 0.0);
				}
			}
		});

		// X -- Gravity Float. Sneak-jump for a 10-block jump. Shift+X is Gravity Repulsion.
		AbilityHandlers.register(KEY, "zero_g", Handlers.toggle(
				ctx -> {
					PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					if (ctx.player().isShiftKeyDown()) {
						repulse(ctx);
					}
				},
				ctx -> PowerToggles.clearModifier(ctx.player(), Attributes.GRAVITY, ZERO_G),
				ctx -> {
					PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, ZERO_G, -0.85, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					ctx.player().resetFallDistance();
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
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("bh_charging") > 0.5f) {
					ctx.setResource("bh_charging", 0, 1);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("bh_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("bh_charge_start");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0.1);
				}
				if (held >= 5 * 20) {
					ctx.setResource("bh_charging", 0, 1);
					Vec3 center = p.getEyePosition().add(p.getLookAngle().scale(5));
					HOLES.put(p.getUUID(), new BlackHole(ctx.level(), p, center));
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.6f, 0.3f);
					ctx.triggerCooldown(120 * 20);
				}
			}
		});

		// V -- Gravity Lift: select up to 10 targets. Shift+V slams every selected target down.
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
						AbilityHelpers.hurt(p, le, 14.0f);
					}
				}
				mine.clear();
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 0.6f);
				ctx.triggerCooldown(25 * 20);
				return;
			}
			if (mine.size() >= 10) {
				ctx.actionBar("message.projecthero.gravity.lift_full");
				return;
			}
			LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
			if (t == null) {
				return;
			}
			mine.add(new Lift(t.getId(), p.level().getGameTime() + 400));
			t.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 400, 0, false, false, true));
			ctx.level().sendParticles(ParticleTypes.PORTAL, t.getX(), t.getY() + 1, t.getZ(), 15, 0.4, 0.6, 0.4, 0.2);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.7f);
			ctx.triggerCooldown(6 * 20);
		}));

		AbilityHandlers.register(KEY, "gravity_field", Handlers.cycle(ctx -> {
			ctx.advanceCycle(3);
			applyField(ctx);
			ctx.actionBar("message.projecthero.gravity.field_" + MODES[ctx.cycleMode() % 3]);
			AbilityHelpers.sound(ctx.player(), SoundEvents.AMETHYST_BLOCK_HIT, 1.0f, 0.5f + (ctx.cycleMode() % 3) * 0.4f);
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.GRAVITY, GRAV);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, KB);
				player.setInvulnerable(false);
			} else {
				var power = Powers.byKey(KEY);
				applyField(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KB, 0.5, AttributeModifier.Operation.ADD_VALUE);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = Powers.byKey(KEY);
			if (power != null && player.tickCount % 20 == 0) {
				applyField(new AbilityContext(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6), true));
			}
			// keep Gravity Lift targets pinned under their height cap
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
		});
	}

	private static void repulse(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 7.0)) {
			AbilityHelpers.push(e, e.position().subtract(p.position()).normalize().scale(1.8).add(0, 0.3, 0));
			e.hurtMarked = true;
		}
		AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.PORTAL, 30, 1.2);
		AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.3f);
		ctx.triggerCooldown(10 * 20);
	}

	private static void applyField(AbilityContext ctx) {
		int m = ctx.cycleMode() % 3; // 0 normal, 1 high, 2 low
		double grav = m == 1 ? 0.6 : (m == 2 ? -0.5 : 0.0);
		PowerToggles.modifier(ctx.player(), Attributes.GRAVITY, GRAV, grav, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		if (m == 2) {
			ctx.player().resetFallDistance();
		}
	}

	private record Lift(int id, long expiry) {
	}

	/** One active Black Hole: 30-block pull, 6 damage inside 10 blocks, destructive, 15s. */
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
			for (LivingEntity e : AbilityHelpers.living(level, center, 30.0, le -> le != owner)) {
				Vec3 pull = center.subtract(e.position());
				double dist = pull.length();
				if (dist > 0.3) {
					e.setDeltaMovement(e.getDeltaMovement().add(pull.normalize().scale(0.5)));
					e.hurtMarked = true;
				}
				if (dist <= 10.0 && age % 20 == 0) {
					AbilityHelpers.hurt(owner, e, 6.0f);
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
			}
			if (age % 20 == 0) {
				level.playSound(null, BlockPos.containing(center), SoundEvents.WARDEN_HEARTBEAT,
						net.minecraft.sounds.SoundSource.HOSTILE, 3.0f, 0.3f);
			}
			return true;
		}
	}
}
