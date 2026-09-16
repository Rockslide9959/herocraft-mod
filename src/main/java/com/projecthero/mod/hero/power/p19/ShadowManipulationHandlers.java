package com.projecthero.mod.hero.power.p19;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.SafeTeleport;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/** Power 19 — Shadow Manipulation. Stronger in darkness, weaker in sunlight. */
public final class ShadowManipulationHandlers {
	public static final String KEY = "power_19_shadow_manipulation";

	/** entityId -> game time the shadow-bind visual expires (drawn from the passive tick). */
	private static final Map<Integer, Long> BOUND = new ConcurrentHashMap<>();
	/** entityId -> game time Shadow Tendrils' immobilise wears off (re-applied every tick). */
	private static final Map<Integer, Long> ROOTED = new ConcurrentHashMap<>();

	private static final int ZONE_TICKS = 11 * 20;
	private static final float MAX_CLOAK = 100.0f;
	private static final float CLOAK_DRAIN = MAX_CLOAK / (35 * 20); // ~35s from full
	private static final float CLOAK_REGEN = MAX_CLOAK / (25 * 20);
	private static final Vector3f BLACK = new Vector3f(0.02f, 0.02f, 0.03f);
	private static final int BLIND_4S = 80;

	/** Live Shadow Zones: one per caster, ticked from the passive. */
	private static final Map<java.util.UUID, Zone> ZONES = new ConcurrentHashMap<>();

	/**
	 * Expire stale shadow-bind / root markers. The passive tick that normally prunes these only runs
	 * while some player has Shadow Manipulation selected, so an entry left behind by a player who
	 * switched power or logged out would otherwise stay forever -- see {@code ServerStateReset}.
	 */
	public static void pruneExpired(long now) {
		if (!BOUND.isEmpty()) {
			BOUND.values().removeIf(expiry -> expiry <= now);
		}
		if (!ROOTED.isEmpty()) {
			ROOTED.values().removeIf(expiry -> expiry <= now);
		}
	}

	public static void clearSessionState() {
		BOUND.clear();
		ROOTED.clear();
		ZONES.clear();
	}

	private ShadowManipulationHandlers() {
	}

	// ================================================================= shared light-tier math

	/**
	 * How strong Shadow Manipulation is right now, from the ambient light at {@code pos}: sunlight/
	 * bright light (13-15) 50%, indoor lighting (7-12) 75%, darkness/nighttime (0-6) 100%, and the Deep
	 * Dark biome always 130% regardless of measured light. Callable from both the server ability code
	 * and the client HUD / outline render, since it only reads light + biome data.
	 */
	public static float tier(Level level, BlockPos pos) {
		if (isDeepDark(level, pos)) {
			return 1.3f;
		}
		// v0.10.21: guarantee full darkness outdoors at night away from any light source, rather than
		// leaving it to the raw sky-darken math -- which is already low at night but can still sit in
		// the 7-12 range during the dusk/dawn transition, reading as merely "indoor lighting".
		if (!level.isDay() && level.canSeeSky(pos)
				&& level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) == 0) {
			return 1.0f;
		}
		int light = level.getMaxLocalRawBrightness(pos);
		if (light >= 13) {
			return 0.5f;
		}
		if (light >= 7) {
			return 0.75f;
		}
		return 1.0f;
	}

	public static boolean isDeepDark(Level level, BlockPos pos) {
		return level.getBiome(pos).is(net.minecraft.world.level.biome.Biomes.DEEP_DARK);
	}

	private static float tier(ServerPlayer p) {
		return tier(p.level(), p.blockPosition());
	}

	/** Damage scaled by the current light tier. */
	private static float dmg(ServerPlayer p, float base) {
		return base * tier(p);
	}

	/** Cooldown scaled inversely to the light tier: shorter in darkness, longer in sunlight. */
	private static void cd(AbilityContext ctx, int base) {
		ctx.triggerCooldown(Math.max(1, Math.round(base / tier(ctx.player()))));
	}

	private static void blind4s(LivingEntity t) {
		AbilityHelpers.applyControl(t, MobEffects.BLINDNESS, BLIND_4S, 0);
	}

	private static int cloakMode(AbilityContext ctx) {
		return (int) ctx.resource("cloak_mode");
	}

	private static boolean cloakActive(ServerPlayer p) {
		var power = com.projecthero.mod.hero.Powers.byKey(KEY);
		return power != null && com.projecthero.mod.hero.ExperimentalPowers.owns(p, power)
				&& com.projecthero.mod.hero.ExperimentalPowers.getResource(p, power, "cloak_mode") == 1.0f;
	}

	/** +10 ability damage while Shadow Cloak (not the legacy Shadow Form) is active. */
	private static float cloakAbilityBonus(ServerPlayer p) {
		return cloakActive(p) ? 10.0f : 0.0f;
	}

	public static void register() {
		// R -- Shadow Bolt. Shift+R fires all 5 at once, fanned out in front of the caster.
		AbilityHandlers.register(KEY, "shadow_bolt", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					if (!ctx.cooldownReady()) {
						onCooldownMessage(ctx);
						return;
					}
					fireBoltVolley(ctx);
					ctx.triggerCooldown(Math.max(1, Math.round(15 * 20 / tier(p))));
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				fireBolt(ctx);
				cd(ctx, 40);
			}
		});

		// G -- Shadow Tendrils. Shift+G is a cone AoE variant that does not slow.
		AbilityHandlers.register(KEY, "shadow_tendrils", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float damage = dmg(p, 15.0f) + cloakAbilityBonus(p);
			if (p.isShiftKeyDown()) {
				Vec3 look = p.getLookAngle();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
					Vec3 dir = e.position().subtract(p.position());
					if (dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(look) < 0.5) {
						continue;
					}
					AbilityHelpers.hurt(p, e, damage);
					AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 160, 0); // 8s
					AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 160, 9);
					AbilityHelpers.applyControl(e, MobEffects.JUMP, 160, -10);
					e.setDeltaMovement(e.getDeltaMovement().multiply(0, 1, 0));
					e.hurtMarked = true;
					ROOTED.put(e.getId(), p.level().getGameTime() + 160);
				}
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), p.getEyePosition().add(look.scale(10)),
						ParticleTypes.SQUID_INK, 1.5);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0f, 0.5f);
				ctx.triggerCooldown(Math.max(1, Math.round(20 * 20 / tier(p))));
				return;
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(look(p).scale(4)), 4.0)) {
				AbilityHelpers.hurt(p, e, damage);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 160, 0); // 8s
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(look(p).scale(4)), ParticleTypes.SQUID_INK, 30, 0.8);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.7f);
			cd(ctx, 160);
		}));

		// X -- Shadow Step, up to 75 blocks. While NOT in darkness, only Shift+X works (it seeks out a
		// dark spot to land in instead of a plain blink).
		AbilityHandlers.register(KEY, "shadow_step", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			boolean dark = tier(p) >= 1.0f;
			boolean shift = p.isShiftKeyDown();
			if (!dark && !shift) {
				ctx.actionBar("message.projecthero.shadow.need_darkness");
				return;
			}
			Vec3 from = p.position();
			boolean moved;
			if (shift) {
				BlockPos dest = findDarkSpot(p, 75.0);
				if (dest == null) {
					ctx.actionBar("message.projecthero.shadow.no_darkness_found");
					return;
				}
				moved = SafeTeleport.blink(p, Vec3.atCenterOf(dest).subtract(p.position()).normalize(),
						p.position().distanceTo(Vec3.atCenterOf(dest)));
			} else {
				moved = SafeTeleport.blink(p, p.getLookAngle(), 75.0);
			}
			if (!moved) {
				return;
			}
			level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y + 1, from.z, 20, 0.3, 0.5, 0.3, 0.1);
			level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.5, 0.3, 0.1);
			AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
			for (LivingEntity hit : AbilityHelpers.living(level, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.6,
					e -> e != p)) {
				AbilityHelpers.hurt(p, hit, dmg(p, 5.0f));
				AbilityHelpers.knockbackFrom(hit, p.position(), 0.6);
				blind4s(hit);
			}
			ctx.triggerCooldown(shift ? Math.max(1, Math.round(8 * 20 / tier(p))) : Math.max(1, Math.round(3 * 20 / tier(p))));
		}));

		// Z -- hold for 5s to charge, then unleash Shadow Zone.
		AbilityHandlers.register(KEY, "total_darkness", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("zone_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				ctx.setResource("zone_charging", 1, 1);
				ctx.setResource("zone_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("zone_charging") > 0.5f) {
					ctx.setResource("zone_charging", 0, 1);
					ctx.setResource("zone_charge", 0, 100);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("zone_charging") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) ctx.resource("zone_charge_start");
				ctx.setResource("zone_charge", Math.min(100f, held / 100.0f * 100.0f), 100);
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(),
							3, 0.4, 0.6, 0.4, 0.01);
				}
				if (held >= 100) {
					ctx.setResource("zone_charging", 0, 1);
					ctx.setResource("zone_charge", 0, 100);
					ZONES.put(p.getUUID(), new Zone(ctx.level(), p, p.position()));
					AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.4f);
					ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 60, 8, 1, 8, 0.02);
					ctx.triggerCooldown(60 * 20);
				}
			}
		});

		// V -- Shadow Grab.
		AbilityHandlers.register(KEY, "shadow_clone", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				if (p.isShiftKeyDown()) {
					GrabHelper.dropHeld(ctx);
				} else {
					GrabHelper.throwHeld(ctx, 2.2, dmg(p, 6.0f));
				}
				AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.6f);
				ctx.triggerCooldown(20);
			} else if (GrabHelper.tryGrab(ctx, 20.0, 200)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
				ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
				AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.7f);
			}
		}, ctx -> GrabHelper.tick(ctx, 3.0)));

		// C -- Shadow Cloak (drains a bar, +10 ability / +8 melee damage). Shift+C is the old, indefinite
		// Shadow Form stealth mode.
		AbilityHandlers.register(KEY, "shadow_form", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("cloak_mode", 2, 2);
					return;
				}
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					ctx.setToggled(false);
					return;
				}
				ModeMeter.ensureSeeded(ctx, "shadow_cloak", MAX_CLOAK);
				if (!ModeMeter.hasCharge(ctx, "shadow_cloak", 5.0f)) {
					ctx.actionBar("message.projecthero.shadow.cloak_low");
					ctx.setToggled(false);
					return;
				}
				ctx.setResource("cloak_mode", 1, 2);
				com.projecthero.mod.hero.power.PowerToggles.modifier(p,
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, CLOAK_ATK, 8.0,
						net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
				AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 1.0f, 0.5f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				endCloak(ctx);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				int mode = cloakMode(ctx);
				if (mode == 1) {
					AbilityHelpers.modeAura(p, ParticleTypes.SQUID_INK, 3);
					for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 4.0)) {
						AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
						blind4s(e);
					}
					if (!ModeMeter.drain(ctx, "shadow_cloak", MAX_CLOAK, CLOAK_DRAIN)) {
						ctx.actionBar("message.projecthero.shadow.cloak_out");
						endCloak(ctx);
						ctx.setToggled(false);
					}
				} else if (mode == 2) {
					AbilityHelpers.modeAura(p, ParticleTypes.SMOKE, 4);
					if (tier(p) >= 1.0f) {
						p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20, 1, false, false, false));
						p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 20, 0, false, false, false));
						if (p.tickCount % 40 == 0) {
							for (Mob m : AbilityHelpers.living(ctx.level(), p.position(), 12.0, x -> x instanceof Mob).stream()
									.map(x -> (Mob) x).toList()) {
								if (m.getTarget() == p) {
									m.setTarget(null);
								}
							}
						}
					}
				}
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (!(player.level() instanceof ServerLevel level)) {
				return;
			}
			// Always-on passives: night vision, deep-dark unarmed bonus is applied via the attack
			// callback below.
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
			ModeMeter.regen(player, com.projecthero.mod.hero.Powers.byKey(KEY), "shadow_cloak", MAX_CLOAK, CLOAK_REGEN,
					cloakActive(player));
			if (player.tickCount % 4 != 0) {
				return;
			}
			long now = level.getGameTime();
			if (!BOUND.isEmpty()) {
				BOUND.entrySet().removeIf(e -> {
					if (e.getValue() <= now) {
						return true;
					}
					if (level.getEntity(e.getKey()) instanceof LivingEntity le && le.isAlive()) {
						level.sendParticles(ParticleTypes.SQUID_INK,
								le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 5, 0.4, 0.6, 0.4, 0.02);
						level.sendParticles(ParticleTypes.SMOKE,
								le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 3, 0.4, 0.6, 0.4, 0.02);
						return false;
					}
					return true;
				});
			}
			if (!ROOTED.isEmpty()) {
				ROOTED.forEach((id, expiry) -> {
					if (expiry > now && level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
						le.setDeltaMovement(le.getDeltaMovement().multiply(0, 1, 0));
					}
				});
			}
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				player.removeEffect(MobEffects.NIGHT_VISION);
				com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, CLOAK_ATK);
			}
		});

		// Deep Dark: +4 unarmed melee damage.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le
					&& com.projecthero.mod.hero.ExperimentalPowers.owns(sp, com.projecthero.mod.hero.Powers.byKey(KEY))
					&& sp.getMainHandItem().isEmpty() && isDeepDark(sp.level(), sp.blockPosition())) {
				AbilityHelpers.hurt(sp, le, 4.0f);
			}
			return InteractionResult.PASS;
		});

		// Tick every live Shadow Zone (independent of who currently holds the ability slots).
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (ZONES.isEmpty() || !ZONES.containsKey(player.getUUID())) {
				return;
			}
			Zone z = ZONES.get(player.getUUID());
			if (!z.tick()) {
				ZONES.remove(player.getUUID());
			}
		});
	}

	private static void onCooldownMessage(AbilityContext ctx) {
		ctx.actionBar("message.projecthero.ability.on_cooldown",
				net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
				String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
	}

	private static void endCloak(AbilityContext ctx) {
		int mode = cloakMode(ctx);
		ctx.setResource("cloak_mode", 0, 2);
		if (mode == 1) {
			com.projecthero.mod.hero.power.PowerToggles.clearModifier(ctx.player(),
					net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, CLOAK_ATK);
			ctx.triggerCooldown(20 * 20);
		}
	}

	private static Vec3 look(ServerPlayer p) {
		return p.getLookAngle();
	}

	private static void fireBolt(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 24.0), ParticleTypes.SQUID_INK, 3.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, dmg(p, 11.0f) + cloakAbilityBonus(p));
			blind4s(t);
		}
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.6f);
	}

	/**
	 * Shift+R: all 5 Shadow Bolts fire in the same instant as a fan spread (-20/-10/0/+10/+20 degrees
	 * off the caster's yaw), rather than trickling out one every 0.75s. Each bolt independently
	 * raycasts along its own fanned direction, so a wide group of enemies can be hit in one press.
	 */
	private static void fireBoltVolley(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		double[] yawOffsets = {-20.0, -10.0, 0.0, 10.0, 20.0};
		for (double offset : yawOffsets) {
			Vec3 dir = yawOffsetLook(p, offset);
			fireBoltInDirection(ctx, dir);
		}
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.2f, 0.5f);
	}

	/** The player's look direction, rotated {@code degrees} around the vertical (yaw) axis. */
	private static Vec3 yawOffsetLook(ServerPlayer p, double degrees) {
		double yaw = Math.toRadians(p.getYRot() + degrees);
		double pitch = Math.toRadians(p.getXRot());
		double xz = Math.cos(pitch);
		return new Vec3(-Math.sin(yaw) * xz, -Math.sin(pitch), Math.cos(yaw) * xz).normalize();
	}

	/** One fanned bolt: raycasts blocks and living entities along {@code dir} rather than the player's exact look. */
	private static void fireBoltInDirection(AbilityContext ctx, Vec3 dir) {
		ServerPlayer p = ctx.player();
		Vec3 eye = p.getEyePosition();
		double range = 24.0;
		Vec3 end = eye.add(dir.scale(range));
		net.minecraft.world.phys.BlockHitResult blockHit = ctx.level().clip(new net.minecraft.world.level.ClipContext(
				eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, p));
		double maxDist = blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
				? eye.distanceTo(blockHit.getLocation()) : range;

		LivingEntity best = null;
		double bestDist = maxDist;
		for (LivingEntity e : AbilityHelpers.living(ctx.level(), eye, range, le -> le != p)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0e-4 || dist > maxDist) {
				continue;
			}
			if (to.normalize().dot(dir) < 0.94) { // ~20 degree cone around this fan direction
				continue;
			}
			if (dist < bestDist) {
				best = e;
				bestDist = dist;
			}
		}

		Vec3 endPoint = best != null ? best.position().add(0, best.getBbHeight() * 0.5, 0) : eye.add(dir.scale(maxDist));
		AbilityHelpers.line(ctx.level(), eye, endPoint, ParticleTypes.SQUID_INK, 3.0);
		if (best != null) {
			// hurtBurst, not hurt: the fan's 20-degree-wide lanes overlap, so a centred enemy is
			// legitimately picked by more than one bolt -- each must still land its own damage rather
			// than being silently absorbed by the first bolt's hit-invulnerability window.
			AbilityHelpers.hurtBurst(p, best, dmg(p, 11.0f) + cloakAbilityBonus(p));
			blind4s(best);
		}
	}

	/** Scan the look ray up to {@code range} for the first sufficiently dark block position. */
	private static BlockPos findDarkSpot(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 look = p.getLookAngle();
		Level level = p.level();
		for (double d = 2.0; d <= range; d += 2.0) {
			BlockPos bp = BlockPos.containing(eye.add(look.scale(d)));
			if (!level.getBlockState(bp).isSolid() && level.getMaxLocalRawBrightness(bp) <= 6) {
				return bp;
			}
		}
		return null;
	}

	private static final net.minecraft.resources.ResourceLocation CLOAK_ATK =
			com.projecthero.mod.ProjectHeroMod.id("shadow_cloak_atk");

	/** One active Shadow Zone: 20-block radius, 11s, sinks/slows/blinds/damages everything caught. */
	private static final class Zone {
		private final ServerLevel level;
		private final ServerPlayer owner;
		private final Vec3 center;
		private int age;

		Zone(ServerLevel level, ServerPlayer owner, Vec3 center) {
			this.level = level;
			this.owner = owner;
			this.center = center;
		}

		boolean tick() {
			if (age >= ZONE_TICKS || !owner.isAlive() || owner.hasDisconnected()) {
				return false;
			}
			age++;
			for (LivingEntity e : AbilityHelpers.living(level, center, 20.0, le -> le != owner)) {
				e.setDeltaMovement(e.getDeltaMovement().x, Math.min(e.getDeltaMovement().y, -0.05), e.getDeltaMovement().z);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 20, 0);
				if (age % 20 == 0) {
					AbilityHelpers.hurt(owner, e, 8.0f);
				}
			}
			if (age % 4 == 0) {
				level.sendParticles(new DustParticleOptions(BLACK, 3.0f), center.x, center.y + 0.1, center.z,
						40, 9.0, 0.2, 9.0, 0.0);
				level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y + 1, center.z, 10, 9.0, 1.0, 9.0, 0.0);
			}
			return true;
		}
	}
}
