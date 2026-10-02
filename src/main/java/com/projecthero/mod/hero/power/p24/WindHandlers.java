package com.projecthero.mod.hero.power.p24;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.PowerPassives;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.power.StanceMode;
import com.projecthero.mod.hero.revamp.BatchCFx;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Power 24 -- Wind Manipulation (v0.13.22 revamp).
 *
 * <h2>Signature: riding air currents</h2>
 * No more plain wind flight. A wind user <b>glides</b>: sprint-jump off anything with a few blocks of air below (or
 * press X in mid-air) and you ride the air with your arms spread, steering with your look. <b>Updrafts</b> (X on the
 * ground) are rising columns that throw you up and keep lifting anyone gliding through or over them, so chaining
 * updrafts is how you gain height. H summons a tornado you can ride and steer.
 */
public final class WindHandlers {
	public static final String KEY = "power_24_wind_manipulation";
	/** +15% over the pre-revamp 500. */
	public static final float MAX_WIND = 575.0f;
	private static final float WIND_DRAIN = MAX_WIND / (20 * 20); // tailwind holds ~20 s
	private static final float WIND_REGEN = MAX_WIND / (26 * 20);

	public static final float BLADE_DAMAGE = 10.0f;
	private static final float WIDE_BLADE_DAMAGE = 19.0f;
	public static final float BURST_DAMAGE = 14.0f;
	private static final float HURRICANE_DPS = 10.0f;
	public static final float TORNADO_DPS = 4.0f;
	private static final float VACUUM_TICK_DAMAGE = 2.5f;

	public static final float COST_UPDRAFT = 50.0f;
	public static final float COST_TORNADO = 120.0f;
	private static final int UPDRAFT_CD = 100;
	private static final int UPDRAFT_TICKS = 240;
	private static final double UPDRAFT_RADIUS = 1.8;
	private static final double UPDRAFT_HEIGHT = 14.0;

	private static final ResourceLocation JUMP_ID = com.projecthero.mod.ProjectHeroMod.id("wind_jump");
	private static final ResourceLocation SPRINT_ID = com.projecthero.mod.ProjectHeroMod.id("wind_sprint");
	private static final ResourceLocation TAILWIND_STEP = com.projecthero.mod.ProjectHeroMod.id("tailwind_step");
	private static final ResourceLocation TAILWIND_ATK = com.projecthero.mod.ProjectHeroMod.id("tailwind_atk");

	/** Sneak + hold R ~1.7 s: the wide wind blade. */
	private static final int BLADE_CHARGE = 34;
	private static final int BLADE_CD = 19 * 20;
	/** Hurricane: hold Z ~4.25 s, then 11 s of storm. */
	private static final int HURR_CHARGE = 85;
	private static final int HURR_DURATION = 11 * 20;
	private static final int VACUUM_TICKS = 40;

	static final int AIR = 0xE6F4FF;

	/** Per-player glide state (server side). */
	private static final class Glide {
		boolean wasGround = true;
		boolean armed;
		boolean gliding;
		double lastY;
		double vy;
	}

	/** A rising air column. */
	public record Updraft(ServerLevel level, Vec3 base, long expires, UUID owner) {
		boolean contains(Vec3 pos, double extraTop) {
			double dx = pos.x - base.x;
			double dz = pos.z - base.z;
			return dx * dx + dz * dz <= (UPDRAFT_RADIUS + 0.4) * (UPDRAFT_RADIUS + 0.4)
					&& pos.y >= base.y - 1.0 && pos.y <= base.y + UPDRAFT_HEIGHT + extraTop;
		}
	}

	private record Vacuum(ServerLevel level, Vec3 centre, long ends) {
	}

	private static final Map<UUID, Glide> GLIDE = new ConcurrentHashMap<>();
	private static final List<Updraft> UPDRAFTS = new CopyOnWriteArrayList<>();
	private static final Map<UUID, Vacuum> VACUUMS = new ConcurrentHashMap<>();

	private WindHandlers() {
	}

	private static Power power() {
		return Powers.byKey(KEY);
	}

	private static boolean tailwindActive(ServerPlayer p) {
		return BatchCFx.toggledQuick(p, KEY, "tailwind");
	}

	/** Tailwind adds a flat bonus to every Wind ability's damage (see {@link StanceMode}). */
	private static float windBonus(ServerPlayer p) {
		return tailwindActive(p) ? StanceMode.ABILITY_BONUS : 0.0f;
	}

	public static boolean isGliding(ServerPlayer p) {
		Glide g = GLIDE.get(p.getUUID());
		return g != null && g.gliding;
	}

	public static boolean ridingTornado(ServerPlayer p) {
		return p.getVehicle() instanceof WindTornadoEntity;
	}

	/** Protects {@code p} from fall damage for {@code ticks} (a Wind-owned no-fall window). */
	public static void protectFall(ServerPlayer p, int ticks) {
		Power power = power();
		if (power != null && ExperimentalPowers.owns(p, power)) {
			ExperimentalPowers.setResource(p, power, "no_fall_until", p.level().getGameTime() + ticks, 1.0e12f);
		}
		p.resetFallDistance();
	}

	private static ParticleOptions air(float size) {
		return BatchCFx.dust(AIR, size);
	}

	private static void seedWind(AbilityContext ctx) {
		ModeMeter.ensureSeeded(ctx, "tailwind", MAX_WIND);
	}

	private static boolean spendWind(AbilityContext ctx, float amount) {
		seedWind(ctx);
		if (ctx.resource("tailwind") < amount) {
			ctx.actionBar("message.projecthero.wind.wind_low");
			return false;
		}
		ctx.addResource("tailwind", -amount, MAX_WIND);
		return true;
	}

	public static void register() {
		// R -- Wind Blade: a quick slash of air. Sneak + hold R ~1.7 s: a wide piercing blade.
		AbilityHandlers.register(KEY, "wind_blade", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
						ctx.setResource("blade_hold", 0.01f, BLADE_CHARGE);
						AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.0f, 1.0f);
					}
					return;
				}
				if (!ctx.cooldownReady()) {
					return;
				}
				LivingEntity t = AbilityHelpers.raycastEntity(p, 26.0);
				Vec3 end = AbilityHelpers.aimPoint(p, 26.0);
				Vec3 from = AbilityHelpers.handPosition(p);
				AbilityHelpers.line(ctx.level(), from, end, ParticleTypes.SWEEP_ATTACK, 0.6);
				AbilityHelpers.line(ctx.level(), from, end, air(0.9f), 2.5);
				crescent(ctx.level(), from.add(p.getLookAngle().scale(2.0)), p.getLookAngle(), 1.1);
				if (t != null) {
					AbilityHelpers.hurt(p, t, BLADE_DAMAGE + windBonus(p));
					AbilityHelpers.knockbackFrom(t, p.position(), 0.7);
				}
				MutationVisuals.play(p, "slash_right");
				AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.0f, 1.4f);
				ctx.triggerCooldown();
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("blade_hold") > 0.001f) {
					ctx.setResource("blade_hold", 0, BLADE_CHARGE);
					MutationVisuals.stopIf(ctx.player(), "guard");
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				windBladeTick(ctx);
			}
		});

		// G -- Wind Burst (id "tornado" kept from the old slot): a 6-block cone of pressure.
		AbilityHandlers.register(KEY, "tornado", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 eye = p.getEyePosition();
			Vec3 look = p.getLookAngle();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(3.0)), 6.5)) {
				Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
				if (to.length() > 6.5 || to.normalize().dot(look) < 0.55) {
					continue;
				}
				AbilityHelpers.hurt(p, e, BURST_DAMAGE + windBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 3.2);
				AbilityHelpers.push(e, look.scale(1.6).add(0, 0.35, 0));
			}
			for (var proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0))) {
				if (proj.getOwner() != p) {
					proj.setDeltaMovement(look.scale(2.5));
					proj.hurtMarked = true;
				}
			}
			BatchCFx.ringTrail(ctx.level(), eye.add(0, -0.3, 0), look, 6.0, 1.2, 0.4, 0.35, air(1.2f));
			for (int i = 1; i <= 6; i++) {
				Vec3 pt = eye.add(look.scale(i));
				ctx.level().sendParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 4, 0.2 * i, 0.2 * i, 0.2 * i, 0.05);
			}
			ctx.level().sendParticles(ParticleTypes.GUST, eye.x + look.x * 2.5, eye.y + look.y * 2.5, eye.z + look.z * 2.5,
					1, 0, 0, 0, 0);
			MutationVisuals.play(p, "cast_two_hand");
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.3f, 0.7f);
			ctx.triggerCooldown();
		}));

		// X -- Glide / Updraft. On the ground: an updraft column that throws you up into a glide.
		// In the air: start (or stop) gliding.
		AbilityHandlers.register(KEY, "glide", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				Glide g = GLIDE.computeIfAbsent(p.getUUID(), k -> new Glide());
				if (!p.onGround() && !p.isInWater() && !p.isPassenger()) {
					if (g.gliding) {
						endGlide(p, g);
					} else {
						startGlide(p, g);
					}
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				if (!spendWind(ctx, COST_UPDRAFT)) {
					return;
				}
				spawnUpdraft(p);
				AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 1.25, p.getDeltaMovement().z));
				g.armed = true;
				protectFall(p, 200);
				MutationVisuals.play(p, "leap");
				ctx.triggerCooldown(UPDRAFT_CD);
			}
		});

		// Z -- Hurricane: hold ~4.25 s to charge, then an 11-second storm around you.
		AbilityHandlers.register(KEY, "hurricane", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				hurricanePress(ctx);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				hurricaneRelease(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				hurricaneChargeTick(ctx);
				hurricaneStormTick(ctx);
			}
		});

		// V -- Wind Push: a wall of air that shoves everything in front of you away.
		AbilityHandlers.register(KEY, "wind_push", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 look = p.getLookAngle();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(look.scale(4)), 4.5)) {
				AbilityHelpers.knockbackFrom(e, p.position(), 4.5);
				AbilityHelpers.push(e, look.scale(2.2).add(0, 0.3, 0));
				if (windBonus(p) > 0) {
					AbilityHelpers.hurt(p, e, windBonus(p));
				}
			}
			for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(6.0))) {
				if (proj.getOwner() != p) {
					proj.setDeltaMovement(look.scale(2.0));
					proj.hurtMarked = true;
				}
			}
			for (int i = 1; i <= 3; i++) {
				BatchCFx.ring(ctx.level(), p.getEyePosition().add(look.scale(i * 1.2)).add(0, -0.4, 0), look, 0.6 + i * 0.5,
						16 + i * 4, air(1.1f), 0.1);
			}
			ctx.level().sendParticles(ParticleTypes.SWEEP_ATTACK, p.getX() + look.x * 2, p.getY() + 1, p.getZ() + look.z * 2,
					4, 1.0, 0.5, 1.0, 0.0);
			MutationVisuals.play(p, "double_punch");
			AbilityHelpers.sound(p, SoundEvents.BREEZE_SHOOT, 1.2f, 0.9f);
			ctx.triggerCooldown();
		}));

		// C -- Tailwind: faster, higher jumps, a 2-block step, +10 ability / +8 melee, glide faster. Drains Wind.
		AbilityHandlers.register(KEY, "tailwind", Handlers.toggle(
				ctx -> {
					if (StanceMode.blockedByCooldown(ctx)) {
						return;
					}
					seedWind(ctx);
					if (!ModeMeter.hasCharge(ctx, "tailwind", 40.0f)) {
						ctx.setToggled(false);
						ctx.actionBar("message.projecthero.wind.wind_low");
						return;
					}
					MutationVisuals.play(ctx.player(), "power_up");
				},
				ctx -> {
					tailwindOff(ctx.player());
					StanceMode.startDeactivateCooldown(ctx);
				},
				ctx -> {
					ServerPlayer p = ctx.player();
					if (p.tickCount % 10 == 0) {
						p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 1, false, false, false));
						p.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 3, false, false, false));
					}
					PowerToggles.modifier(p, Attributes.STEP_HEIGHT, TAILWIND_STEP, 2.0, AttributeModifier.Operation.ADD_VALUE);
					PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, TAILWIND_ATK, StanceMode.MELEE_BONUS,
							AttributeModifier.Operation.ADD_VALUE);
					for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 2.0)) {
						Vec3 away = e.position().subtract(p.position());
						double d = away.horizontalDistance();
						if (d > 0.05) {
							AbilityHelpers.push(e, new Vec3(away.x / d, 0.15, away.z / d));
						}
					}
					if (p.tickCount % 4 == 0) {
						swirl(ctx.level(), p, 0.9, 3);
					}
					if (p.tickCount % 5 == 0 && !ModeMeter.drain(ctx, "tailwind", MAX_WIND, WIND_DRAIN * 5)) {
						ctx.setToggled(false);
						tailwindOff(p);
						StanceMode.startDeactivateCooldown(ctx);
						ctx.actionBar("message.projecthero.wind.wind_out");
					}
				}));

		// H -- Rideable Tornado: summon a tornado under you and ride it; press H again to blow it out.
		AbilityHandlers.register(KEY, "rideable_tornado", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.getVehicle() instanceof WindTornadoEntity t) {
					t.dissipate();
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (p.isPassenger()) {
					return;
				}
				if (!spendWind(ctx, COST_TORNADO)) {
					return;
				}
				summonTornado(p);
				MutationVisuals.play(p, "cast_raise_both");
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ridingTornado(ctx.player())) {
					MutationVisuals.ensure(ctx.player(), "p24.ride");
				} else {
					MutationVisuals.stopIf(ctx.player(), "p24.ride");
				}
			}
		});

		// N -- Vacuum: rip the air out in front of you for 2 s -- everything is dragged in and suffocates.
		AbilityHandlers.register(KEY, "vacuum", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				ServerPlayer p = ctx.player();
				Vec3 centre = p.getEyePosition().add(p.getLookAngle().scale(4.0));
				VACUUMS.put(p.getUUID(), new Vacuum(ctx.level(), centre, ctx.level().getGameTime() + VACUUM_TICKS));
				MutationVisuals.play(p, "grab_pull");
				AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.4f, 0.6f);
				ctx.triggerCooldown();
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				Vacuum v = VACUUMS.get(ctx.player().getUUID());
				if (v != null) {
					vacuumTick(ctx.player(), v);
				}
			}
		});

		// Elytra: right-click (holding any item) while gliding to boost forward -- no rocket needed.
		UseItemCallback.EVENT.register((player, level, hand) -> {
			InteractionResultHolder<net.minecraft.world.item.ItemStack> pass =
					InteractionResultHolder.pass(player.getItemInHand(hand));
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !sp.isFallFlying()) {
				return pass;
			}
			Power power = power();
			if (power == null || !ExperimentalPowers.owns(sp, power)
					|| !sp.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
				return pass;
			}
			long now = level.getGameTime();
			if (ExperimentalPowers.getResource(sp, power, "elytra_cd") >= now) {
				return pass;
			}
			ExperimentalPowers.setResource(sp, power, "elytra_cd", now + 15, 1e12f);
			AbilityHelpers.addImpulse(sp, sp.getLookAngle().scale(1.35));
			((ServerLevel) level).sendParticles(ParticleTypes.CLOUD, sp.getX(), sp.getY(), sp.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
			level.playSound(null, sp.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.0f, 1.3f);
			return pass;
		});

		PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
				PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SPRINT_ID);
				tailwindOff(player);
				Glide g = GLIDE.remove(player.getUUID());
				if (g != null && g.gliding) {
					MutationVisuals.stopIf(player, "p24.glide");
				}
				VACUUMS.remove(player.getUUID());
				if (player.getVehicle() instanceof WindTornadoEntity t) {
					t.dissipate();
				}
			}
		});

		PowerPassives.registerTick(KEY, WindHandlers::passiveTick);
	}

	private static void tailwindOff(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.STEP_HEIGHT, TAILWIND_STEP);
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, TAILWIND_ATK);
	}

	// ---- passive traits (run for every owned Wind player) --------------------------------------

	private static final Set<String> STALE_RES = Set.of("blade_charge", "blade_show", "flight");
	private static final Set<String> STALE_TOGGLES = Set.of("wind_flight");

	private static void passiveTick(ServerPlayer p) {
		Power power = power();
		if (power == null) {
			return;
		}
		if (p.tickCount % 100 == 0) {
			BatchCFx.purge(p, KEY, STALE_RES, STALE_TOGGLES);
		}
		if (p.tickCount % 5 == 0) {
			ModeMeter.regen(p, power, "tailwind", MAX_WIND, WIND_REGEN * 5, tailwindActive(p));
		}

		// A 3-block standing jump.
		PowerToggles.modifier(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0.55, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);

		// 30% quicker while sprinting.
		if (p.isSprinting()) {
			PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID, 0.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SPRINT_ID);
		}

		// Hold sneak while falling (not gliding): drift down under Slow Falling.
		if (p.isShiftKeyDown() && !p.onGround() && !p.isFallFlying() && p.getDeltaMovement().y < 0.0 && !isGliding(p)) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 10, 0, false, false, false));
		}

		// Drowns 50% slower -- feed the air bar back up every other tick while it is draining.
		if (p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply() && p.getAirSupply() > -18 && p.tickCount % 2 == 0) {
			p.setAirSupply(Math.min(p.getMaxAirSupply(), p.getAirSupply() + 1));
		}

		glideTick(p);
	}

	// ---- gliding ----------------------------------------------------------------------------

	private static void glideTick(ServerPlayer p) {
		Glide g = GLIDE.computeIfAbsent(p.getUUID(), k -> {
			Glide n = new Glide();
			n.lastY = p.getY();
			return n;
		});
		boolean ground = p.onGround();
		double dyPos = p.getY() - g.lastY;
		g.lastY = p.getY();
		boolean blocked = p.isInWater() || p.isPassenger() || p.isFallFlying() || p.getAbilities().flying
				|| p.isSpectator();
		if (ground || blocked) {
			if (g.gliding) {
				endGlide(p, g);
			}
			g.armed = false;
			g.wasGround = ground;
			return;
		}
		// sprint-jump take-off arms the glide
		if (g.wasGround && p.isSprinting() && dyPos > 0.05) {
			g.armed = true;
		}
		g.wasGround = false;
		if (!g.gliding && g.armed && dyPos < -0.05 && !p.isShiftKeyDown() && airBelow(p, 3)) {
			startGlide(p, g);
		}
		if (!g.gliding) {
			return;
		}
		if (p.isShiftKeyDown()) {
			endGlide(p, g);
			return;
		}
		// the glide itself: steer by look, a slow sink (a dive if you look down), lift in an updraft
		Vec3 look = p.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize();
		double speed = tailwindActive(p) ? 0.78 : 0.6;
		double sink = -0.09 + Math.min(0.0, look.y) * 0.45;
		boolean lifting = inUpdraft(p, 6.0);
		g.vy = lifting ? Math.min(0.7, Math.max(g.vy, 0) + 0.14) : Math.max(sink, g.vy - 0.04);
		if (!lifting && g.vy > sink) {
			g.vy = Math.max(sink, g.vy * 0.85 - 0.02);
		}
		Vec3 v = new Vec3(flat.x * speed, g.vy, flat.z * speed);
		p.setDeltaMovement(v);
		p.hurtMarked = true;
		p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
		p.resetFallDistance();
		MutationVisuals.ensure(p, "p24.glide");
		if (p.tickCount % 3 == 0 && p.level() instanceof ServerLevel sl) {
			Vec3 back = p.position().add(0, 1.0, 0).subtract(flat.scale(0.8));
			sl.sendParticles(ParticleTypes.CLOUD, back.x, back.y, back.z, 1, 0.2, 0.1, 0.2, 0.0);
			sl.sendParticles(air(0.8f), p.getX() + flat.z * 0.9, p.getY() + 1.3, p.getZ() - flat.x * 0.9, 1, 0, 0, 0, 0);
			sl.sendParticles(air(0.8f), p.getX() - flat.z * 0.9, p.getY() + 1.3, p.getZ() + flat.x * 0.9, 1, 0, 0, 0, 0);
		}
	}

	private static boolean airBelow(ServerPlayer p, int blocks) {
		BlockPos at = p.blockPosition();
		for (int i = 1; i <= blocks; i++) {
			if (!p.level().getBlockState(at.below(i)).getCollisionShape(p.level(), at.below(i)).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** Starts a glide (public for tests). */
	public static void startGlide(ServerPlayer p) {
		startGlide(p, GLIDE.computeIfAbsent(p.getUUID(), k -> new Glide()));
	}

	private static void startGlide(ServerPlayer p, Glide g) {
		g.gliding = true;
		g.armed = false;
		g.vy = Math.min(0.0, p.getDeltaMovement().y);
		protectFall(p, 60);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_JUMP, 0.6f, 1.4f);
	}

	private static void endGlide(ServerPlayer p, Glide g) {
		g.gliding = false;
		g.armed = false;
		protectFall(p, 60);
		MutationVisuals.stopIf(p, "p24.glide");
	}

	// ---- updrafts ---------------------------------------------------------------------------

	private static void spawnUpdraft(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		// at most three live updrafts per player: a fourth replaces the oldest
		List<Updraft> mine = new ArrayList<>();
		for (Updraft u : UPDRAFTS) {
			if (u.owner().equals(p.getUUID())) {
				mine.add(u);
			}
		}
		if (mine.size() >= 3) {
			UPDRAFTS.remove(mine.get(0));
		}
		UPDRAFTS.add(new Updraft(level, p.position(), level.getGameTime() + UPDRAFT_TICKS, p.getUUID()));
		BatchCFx.flatRing(level, p.position().add(0, 0.1, 0), 1.2, 24, air(1.3f), 0.25);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, p.getX(), p.getY() + 0.2, p.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 1.2f, 0.8f);
	}

	/** Whether {@code p} is inside (or within {@code extraTop} blocks above) any live updraft in its level. */
	public static boolean inUpdraft(ServerPlayer p, double extraTop) {
		for (Updraft u : UPDRAFTS) {
			if (u.level() == p.level() && u.contains(p.position(), extraTop)) {
				return true;
			}
		}
		return false;
	}

	public static List<Updraft> updrafts() {
		return List.copyOf(UPDRAFTS);
	}

	/** Server tick (from RevampBatchC): updraft columns lift what is inside them and draw themselves. */
	public static void serverTick(MinecraftServer server) {
		if (UPDRAFTS.isEmpty()) {
			return;
		}
		List<Updraft> dead = new ArrayList<>();
		for (Updraft u : UPDRAFTS) {
			long now = u.level().getGameTime();
			if (now >= u.expires()) {
				dead.add(u);
				continue;
			}
			AABB box = new AABB(u.base().x - UPDRAFT_RADIUS, u.base().y - 1, u.base().z - UPDRAFT_RADIUS,
					u.base().x + UPDRAFT_RADIUS, u.base().y + UPDRAFT_HEIGHT, u.base().z + UPDRAFT_RADIUS);
			for (LivingEntity e : u.level().getEntitiesOfClass(LivingEntity.class, box)) {
				if (!u.contains(e.position(), 0) || e.isPassenger()) {
					continue;
				}
				if (e instanceof ServerPlayer sp) {
					if (isGliding(sp)) {
						continue; // handled by the glide itself
					}
					if (sp.isShiftKeyDown() || sp.getAbilities().flying) {
						continue;
					}
				}
				Vec3 cur = e.getDeltaMovement();
				e.setDeltaMovement(cur.x * 0.9, Math.min(0.55, Math.max(cur.y, 0) + 0.12), cur.z * 0.9);
				e.hurtMarked = true;
				e.resetFallDistance();
				if (e instanceof ServerPlayer sp) {
					sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
					Glide g = GLIDE.get(sp.getUUID());
					if (g != null && BatchCFx.ownsQuick(sp, KEY)) {
						g.armed = true; // leaving the top of your updraft rolls straight into a glide
					}
				}
			}
			if (now % 2 == 0) {
				for (int i = 0; i < 4; i++) {
					double h = ((now / 2 + i * 3) % 14);
					double a = now * 0.35 + i * (Math.PI / 2);
					double r = UPDRAFT_RADIUS * (0.6 + 0.4 * ((i + now / 4) % 2));
					u.level().sendParticles(air(0.9f), u.base().x + Math.cos(a) * r, u.base().y + h,
							u.base().z + Math.sin(a) * r, 0, 0, 1, 0, 0.25);
				}
				if (now % 8 == 0) {
					u.level().sendParticles(ParticleTypes.CLOUD, u.base().x, u.base().y + 0.3, u.base().z, 3, 0.5, 0.1, 0.5, 0.08);
				}
			}
		}
		UPDRAFTS.removeAll(dead);
	}

	// ---- R: wide wind blade -------------------------------------------------------------------

	private static void windBladeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float hold = ctx.resource("blade_hold");
		if (hold <= 0.001f) {
			return;
		}
		if (!p.isShiftKeyDown()) {
			ctx.setResource("blade_hold", 0, BLADE_CHARGE);
			MutationVisuals.stopIf(p, "guard");
			return;
		}
		hold += 1.0f;
		MutationVisuals.ensure(p, "guard");
		p.setDeltaMovement(p.getDeltaMovement().multiply(0.4, 1.0, 0.4));
		if (p.tickCount % 3 == 0) {
			ctx.setResource("blade_hold", hold, BLADE_CHARGE);
			swirl(ctx.level(), p, 1.2, 4);
		} else {
			ctx.setResource("blade_hold", hold, BLADE_CHARGE);
		}
		if (hold >= BLADE_CHARGE) {
			ctx.setResource("blade_hold", 0, BLADE_CHARGE);
			fireWindBlade(ctx);
		}
	}

	private static void fireWindBlade(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, eye.add(look.scale(13.0)), 15.0)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double along = to.dot(look);
			if (along < 1.0 || along > 26.0) {
				continue;
			}
			double perp = to.subtract(look.scale(along)).length();
			if (perp > 3.0) {
				continue; // the blade is wide but still a corridor
			}
			AbilityHelpers.hurt(p, e, WIDE_BLADE_DAMAGE + windBonus(p));
			AbilityHelpers.knockbackFrom(e, p.position(), 1.2);
		}
		for (double d = 2.0; d <= 26.0; d += 2.0) {
			crescent(level, eye.add(look.scale(d)), look, 3.0);
		}
		MutationVisuals.play(p, "slash_right");
		level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 1.6f, 0.6f);
		ctx.triggerCooldown(BLADE_CD);
	}

	/** A flat crescent of air perpendicular to {@code dir}, {@code halfWidth} blocks each side. */
	private static void crescent(ServerLevel level, Vec3 c, Vec3 dir, double halfWidth) {
		Vec3 side = new Vec3(-dir.z, 0, dir.x);
		side = side.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : side.normalize();
		Vec3 back = dir.normalize();
		for (double s = -halfWidth; s <= halfWidth; s += 0.35) {
			double bow = (s / halfWidth) * (s / halfWidth) * 0.6;
			Vec3 pt = c.add(side.scale(s)).subtract(back.scale(bow));
			level.sendParticles(air(0.9f), pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
		}
	}

	/** Swirling wind particles around a player. */
	private static void swirl(ServerLevel level, ServerPlayer p, double r, int n) {
		for (int i = 0; i < n; i++) {
			double a = p.tickCount * 0.5 + i * (Math.PI * 2 / n);
			double y = 0.2 + ((p.tickCount + i * 5) % 20) / 10.0;
			level.sendParticles(air(0.7f), p.getX() + Math.cos(a) * r, p.getY() + y, p.getZ() + Math.sin(a) * r,
					0, -Math.sin(a), 0.1, Math.cos(a), 0.15);
		}
	}

	// ---- H: tornado -------------------------------------------------------------------------

	/** Spawns a rideable tornado under {@code p} and mounts them on it. */
	public static WindTornadoEntity summonTornado(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		WindTornadoEntity t = new WindTornadoEntity(level, p);
		level.addFreshEntity(t);
		p.startRiding(t, true);
		BatchCFx.flatRing(level, p.position().add(0, 0.2, 0), 1.5, 28, air(1.4f), 0.35);
		level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 1.6f, 0.4f);
		return t;
	}

	// ---- N: vacuum ----------------------------------------------------------------------------

	private static void vacuumTick(ServerPlayer p, Vacuum v) {
		long now = v.level().getGameTime();
		if (p.level() != v.level() || now >= v.ends()) {
			VACUUMS.remove(p.getUUID());
			MutationVisuals.stopIf(p, "channel_two_hand");
			return;
		}
		MutationVisuals.ensure(p, "channel_two_hand");
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, v.centre(), 12.0)) {
			Vec3 to = v.centre().subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
			double d = to.length();
			if (d > 0.8) {
				Vec3 pull = to.normalize().scale(Math.min(0.45, 0.15 + d * 0.04));
				Vec3 cur = e.getDeltaMovement();
				e.setDeltaMovement(cur.x * 0.5 + pull.x, cur.y * 0.5 + pull.y + 0.03, cur.z * 0.5 + pull.z);
				e.hurtMarked = true;
			}
			if (now % 10 == 0 && d < 5.0) {
				AbilityHelpers.hurt(p, e, p.damageSources().source(DamageTypes.DROWN, p), VACUUM_TICK_DAMAGE);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 1);
				if (e.getAirSupply() > 0) {
					e.setAirSupply(Math.max(0, e.getAirSupply() - 60));
				}
			}
		}
		if (now % 2 == 0) {
			BatchCFx.inwardSpiral(v.level(), v.centre(), 4.5, 16, air(1.0f), 0.35, now);
			v.level().sendParticles(ParticleTypes.CLOUD, v.centre().x, v.centre().y, v.centre().z, 2, 0.3, 0.3, 0.3, 0.01);
		}
		if (now % 10 == 0) {
			v.level().playSound(null, BlockPos.containing(v.centre()), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.8f, 0.5f);
		}
	}

	/** True while a Vacuum is running for {@code p}. */
	public static boolean vacuumActive(ServerPlayer p) {
		return VACUUMS.containsKey(p.getUUID());
	}

	// ---- Z: Hurricane charge + storm --------------------------------------------------------

	private static void hurricanePress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("hurr_start") > 0.5f || ctx.resource("hurr") > 0.5f) {
			return;
		}
		if (!ExperimentalPowers.cooldownReady(p, ctx.power(), ctx.ability())) {
			ctx.actionBar("message.projecthero.ability.on_cooldown", Component.translatable(ctx.ability().nameKey()),
					String.format(java.util.Locale.ROOT, "%.0f",
							Math.ceil(ExperimentalPowers.cooldownRemainingTicks(p, ctx.power(), ctx.ability()) / 20.0f)));
			return;
		}
		ctx.setResource("hurr_start", p.level().getGameTime(), 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		AbilityHelpers.sound(p, SoundEvents.BREEZE_INHALE, 1.0f, 0.5f);
	}

	private static void hurricaneRelease(AbilityContext ctx) {
		if (ctx.resource("hurr_start") <= 0.5f) {
			return;
		}
		long held = ctx.player().level().getGameTime() - (long) ctx.resource("hurr_start");
		if (held >= HURR_CHARGE) {
			hurricaneFire(ctx);
		} else {
			ctx.setResource("hurr_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			MutationVisuals.stopIf(ctx.player(), "float_arms");
			AbilityHelpers.sound(ctx.player(), SoundEvents.BREEZE_DEFLECT, 0.5f, 1.2f);
		}
	}

	private static void hurricaneChargeTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float start = ctx.resource("hurr_start");
		if (start <= 0.5f) {
			return;
		}
		long held = p.level().getGameTime() - (long) start;
		if (held < 0 || held > HURR_CHARGE + 100) {
			ctx.setResource("hurr_start", 0, 1e12f);
			ctx.setResource("ult_charge", 0, 100);
			return;
		}
		MutationVisuals.ensure(p, "float_arms");
		if (held % 3 == 0) {
			ctx.setResource("ult_charge", Math.min(100f, held * 100f / HURR_CHARGE), 100);
		}
		double frac = Math.min(1.0, held / (double) HURR_CHARGE);
		for (int i = 0; i < 4 + (int) (frac * 10); i++) {
			double a = p.level().random.nextDouble() * Math.PI * 2;
			double rad = 0.8 + p.level().random.nextDouble() * (1.0 + frac * 3.0);
			ctx.level().sendParticles(ParticleTypes.CLOUD,
					p.getX() + Math.cos(a) * rad, p.getY() + p.level().random.nextDouble() * 2.0,
					p.getZ() + Math.sin(a) * rad, 0, -Math.sin(a), 0.1, Math.cos(a), 0.2);
		}
		if (held % 12 == 0) {
			AbilityHelpers.sound(p, SoundEvents.BREEZE_IDLE_GROUND, 0.6f, 0.4f + (float) frac);
		}
		if (held >= HURR_CHARGE) {
			hurricaneFire(ctx);
		}
	}

	private static void hurricaneFire(AbilityContext ctx) {
		ctx.setResource("hurr_start", 0, 1e12f);
		ctx.setResource("ult_charge", 0, 100);
		ctx.setResource("hurr", HURR_DURATION, HURR_DURATION);
		ctx.triggerCooldown();
		AbilityHelpers.sound(ctx.player(), SoundEvents.BREEZE_IDLE_GROUND, 1.6f, 0.3f);
	}

	private static void hurricaneStormTick(AbilityContext ctx) {
		int t = (int) ctx.resource("hurr");
		if (t <= 0) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("hurr", t - 1, HURR_DURATION);
		if (t <= 1) {
			MutationVisuals.stopIf(p, "spin_arms");
			return;
		}
		MutationVisuals.ensure(p, "spin_arms");
		ServerLevel level = ctx.level();
		if (t % 20 == 0) {
			for (LivingEntity e : AbilityHelpers.hostilesAround(p, p.position(), 15.0)) {
				Vec3 tangent = new Vec3(-(e.getZ() - p.getZ()), 0.35, e.getX() - p.getX()).normalize().scale(0.8);
				AbilityHelpers.push(e, tangent);
				AbilityHelpers.hurt(p, e, HURRICANE_DPS + windBonus(p));
			}
		}
		if (t % 5 == 0) {
			for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(15.0))) {
				if (proj.getOwner() != p) {
					proj.setDeltaMovement(proj.getDeltaMovement().reverse());
					proj.hurtMarked = true;
				}
			}
		}
		double a = t * 0.5;
		for (double rr = 3; rr <= 15; rr += 3) {
			for (double off = 0; off < Math.PI * 2; off += Math.PI / 2) {
				double ang = a + off + rr * 0.2;
				level.sendParticles(ParticleTypes.CLOUD, p.getX() + Math.cos(ang) * rr, p.getY() + (t % 6) * 0.4,
						p.getZ() + Math.sin(ang) * rr, 0, -Math.sin(ang), 0.15, Math.cos(ang), 0.3);
			}
		}
		if (t % 3 == 0) {
			swirl(level, p, 1.4, 4);
		}
		if (t % 14 == 0) {
			level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_IDLE_GROUND, SoundSource.PLAYERS, 1.4f, 0.35f);
			level.playSound(null, p.blockPosition(), SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 1.0f, 0.6f);
		}
	}

	public static void clearSessionState() {
		GLIDE.clear();
		UPDRAFTS.clear();
		VACUUMS.clear();
	}

	/** Visual predicate: wearing the pale air shell (gliding, Tailwind or riding a tornado). */
	public static boolean airShell(ServerPlayer p) {
		return BatchCFx.ownsQuick(p, KEY) && (tailwindActive(p) || isGliding(p) || ridingTornado(p));
	}
}
