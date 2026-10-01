package com.projecthero.mod.hero.power.p19;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.ModeMeter;
import com.projecthero.mod.hero.power.SafeTeleport;
import com.projecthero.mod.hero.revamp.d.BatchDContent;
import com.projecthero.mod.hero.revamp.d.BatchDFx;
import com.projecthero.mod.hero.revamp.d.ShadowServantEntity;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.squad.Squads;

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

/**
 * Power 19 — Shadow Manipulation. Stronger in darkness, weaker in sunlight (every number scales by the light tier).
 *
 * <p>v0.13.22 revamp (batch D): <b>shadow travel</b>. R Shadow Bolt, G Shadow Tendrils, X Shadow Step, Z Shadow
 * Zone, V Shadow Bind (sneak: Shadow Grab), C Shadow Cloak / Shadow Form (a thick black silhouette with glowing
 * purple eyes), H Shadow Walk (sink into a moving shadow puddle: invisible, fast, cannot attack, bounded by a bar),
 * N Shadow Servant (a shadow minion that fights for you for 20 s and never touches you or your squad).
 */
public final class ShadowManipulationHandlers {
	public static final String KEY = "power_19_shadow_manipulation";

	/** entityId -> game time the shadow-bind visual expires (drawn from the passive tick). */
	private static final Map<Integer, Long> BOUND = new ConcurrentHashMap<>();
	/** entityId -> game time a root (Tendrils cone, Shadow Bind) wears off (re-applied every tick). */
	private static final Map<Integer, Long> ROOTED = new ConcurrentHashMap<>();

	private static final int ZONE_TICKS = 11 * 20;
	private static final float ZONE_DPS = 10.0f;
	private static final float MAX_CLOAK = 115.0f;
	private static final float CLOAK_DRAIN = 100.0f / (35 * 20);
	private static final float CLOAK_REGEN = MAX_CLOAK / (25 * 20);
	private static final Vector3f BLACK = new Vector3f(0.02f, 0.02f, 0.03f);
	private static final int BLIND_4S = 80;

	private static final float BOLT_DAMAGE = 13.0f;
	private static final float TENDRIL_DAMAGE = 18.0f;
	private static final float STEP_DAMAGE = 6.0f;
	private static final float BIND_DAMAGE = 9.0f;
	private static final int BIND_TICKS = 5 * 20;

	// --- H: Shadow Walk ---
	public static final float MAX_WALK = 115.0f;
	private static final float WALK_DRAIN = MAX_WALK / (10 * 20);
	private static final float WALK_REGEN = MAX_WALK / (25 * 20);

	// --- N: Shadow Servant ---
	private static final float SERVANT_DAMAGE = 8.0f;

	/** Live Shadow Zones: one per caster, ticked from the passive. */
	private static final Map<java.util.UUID, Zone> ZONES = new ConcurrentHashMap<>();

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

	private static Power power() {
		return Powers.byKey(KEY);
	}

	// ================================================================= shared light-tier math

	/**
	 * How strong Shadow Manipulation is right now, from the ambient light at {@code pos}: sunlight/bright light
	 * (13-15) 50%, indoor lighting (7-12) 75%, darkness/nighttime (0-6) 100%, and the Deep Dark biome always 130%.
	 * Callable from both the server ability code and the client HUD / outline render.
	 */
	public static float tier(Level level, BlockPos pos) {
		if (isDeepDark(level, pos)) {
			return 1.3f;
		}
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
		var power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.getResource(p, power, "cloak_mode") == 1.0f;
	}

	/** +12 ability damage while Shadow Cloak (not the legacy Shadow Form) is active. */
	private static float cloakAbilityBonus(ServerPlayer p) {
		return cloakActive(p) ? 12.0f : 0.0f;
	}

	/** True while C (either Shadow Cloak or Shadow Form) is on -- drives the silhouette overlay. */
	public static boolean shadowFormActive(ServerPlayer p) {
		var power = power();
		return power != null && ExperimentalPowers.owns(p, power)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_6));
	}

	/** True while Shadow Walk (H) is on. */
	public static boolean shadowWalking(ServerPlayer p) {
		var power = power();
		return power != null && ExperimentalPowers.owns(p, power) && power.hasSlot(AbilitySlot.SLOT_7)
				&& ExperimentalPowers.isToggled(p, power, power.ability(AbilitySlot.SLOT_7));
	}

	/** Any offensive shadow move surfaces you from a Shadow Walk first. */
	private static void surface(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (shadowWalking(p)) {
			Power power = ctx.power();
			ExperimentalPowers.setToggled(p, power, power.ability(AbilitySlot.SLOT_7), false);
			endWalk(p, false);
		}
	}

	private static boolean friendly(ServerPlayer p, LivingEntity e) {
		return Squads.areAllies(p, e) || (e instanceof ShadowServantEntity s && s.ownerId().map(p.getUUID()::equals).orElse(false));
	}

	public static void register() {
		// R -- Shadow Bolt. Shift+R fires all 5 at once, fanned out in front of the caster.
		AbilityHandlers.register(KEY, "shadow_bolt", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!ctx.cooldownReady()) {
					onCooldownMessage(ctx);
					return;
				}
				surface(ctx);
				if (p.isShiftKeyDown()) {
					fireBoltVolley(ctx);
					MutationVisuals.play(p, "cast_two_hand");
					ctx.triggerCooldown(Math.max(1, Math.round(255 / tier(p))));
					return;
				}
				fireBolt(ctx);
				MutationVisuals.play(p, "cast_right");
				cd(ctx, 34);
			}
		});

		// G -- Shadow Tendrils. Shift+G is a cone that roots.
		AbilityHandlers.register(KEY, "shadow_tendrils", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			surface(ctx);
			float damage = dmg(p, TENDRIL_DAMAGE) + cloakAbilityBonus(p);
			if (p.isShiftKeyDown()) {
				Vec3 look = p.getLookAngle();
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 10.0)) {
					Vec3 dir = e.position().subtract(p.position());
					if (friendly(p, e) || dir.lengthSqr() < 1.0e-4 || dir.normalize().dot(look) < 0.5) {
						continue;
					}
					AbilityHelpers.hurt(p, e, damage);
					AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 160, 0);
					root(p, e, 160);
					tendril(ctx.level(), e);
				}
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), p.getEyePosition().add(look.scale(10)),
						ParticleTypes.SQUID_INK, 1.5);
				MutationVisuals.play(p, "summon_ground");
				AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 1.0f, 0.5f);
				ctx.triggerCooldown(Math.max(1, Math.round(340 / tier(p))));
				return;
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(4)), 4.0)) {
				if (friendly(p, e)) {
					continue;
				}
				AbilityHelpers.hurt(p, e, damage);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 160, 0);
				tendril(ctx.level(), e);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(4)), ParticleTypes.SQUID_INK, 30, 0.8);
			MutationVisuals.play(p, "whip_right");
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.7f);
			cd(ctx, 136);
		}));

		// X -- Shadow Step, up to 75 blocks. Out of darkness only Shift+X works (it seeks a dark spot to land in).
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
			puddleBurst(level, from);
			puddleBurst(level, p.position());
			MutationVisuals.play(p, "dash_forward");
			AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 0.5f);
			for (LivingEntity hit : AbilityHelpers.living(level, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.6,
					e -> e != p && !friendly(p, e))) {
				AbilityHelpers.hurt(p, hit, dmg(p, STEP_DAMAGE));
				AbilityHelpers.knockbackFrom(hit, p.position(), 0.6);
				blind4s(hit);
			}
			ctx.triggerCooldown(shift ? Math.max(1, Math.round(136 / tier(p))) : Math.max(1, Math.round(51 / tier(p))));
		}));

		// Z -- hold for 5s to charge, then unleash Shadow Zone.
		AbilityHandlers.register(KEY, "total_darkness", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("zone_charging") > 0.5f || !ctx.cooldownReady()) {
					return;
				}
				surface(ctx);
				ctx.setResource("zone_charging", 1, 1);
				ctx.setResource("zone_charge_start", ctx.player().level().getGameTime(), 1.0e12f);
				MutationVisuals.play(ctx.player(), "p19.gather");
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("zone_charging") > 0.5f) {
					ctx.setResource("zone_charging", 0, 1);
					ctx.setResource("zone_charge", 0, 100);
					MutationVisuals.stopIf(ctx.player(), "p19.gather");
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
				MutationVisuals.ensure(p, "p19.gather");
				if (p.tickCount % 3 == 0) {
					ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(),
							3, 0.4, 0.6, 0.4, 0.01);
					BatchDFx.ring(ctx.level(), p.position().add(0, 0.05, 0), 3.0 - held / 50.0, BatchDFx.SHADOW, 10, held * 0.2);
				}
				if (held >= 100) {
					ctx.setResource("zone_charging", 0, 1);
					ctx.setResource("zone_charge", 0, 100);
					ZONES.put(p.getUUID(), new Zone(ctx.level(), p, p.position()));
					MutationVisuals.play(p, "slam_two_hand");
					AbilityHelpers.sound(p, SoundEvents.WARDEN_HEARTBEAT, 1.4f, 0.4f);
					ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 60, 8, 1, 8, 0.02);
					ctx.triggerCooldown(51 * 20);
				}
			}
		});

		// V -- Shadow Bind (sneak: Shadow Grab, the old V).
		AbilityHandlers.register(KEY, "shadow_bind", Handlers.instantTicking(ShadowManipulationHandlers::bindPress,
				ctx -> GrabHelper.tick(ctx, 3.0)));

		// C -- Shadow Cloak (drains a bar, +12 ability / +8 melee damage). Shift+C is the indefinite Shadow Form.
		AbilityHandlers.register(KEY, "shadow_form", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					ctx.setResource("cloak_mode", 2, 2);
					MutationVisuals.play(p, "p19.cloak");
					puddleBurst(ctx.level(), p.position());
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
				MutationVisuals.play(p, "p19.cloak");
				puddleBurst(ctx.level(), p.position());
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
						if (friendly(p, e)) {
							continue;
						}
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
							dropAggro(ctx.level(), p, 12.0);
						}
					}
				}
			}
		});

		// H -- Shadow Walk.
		AbilityHandlers.register(KEY, "shadow_walk", Handlers.toggle(
				ShadowManipulationHandlers::walkOn,
				ctx -> endWalk(ctx.player(), true),
				ShadowManipulationHandlers::walkTick));

		// N -- Shadow Servant.
		AbilityHandlers.register(KEY, "shadow_servant", Handlers.instant(ShadowManipulationHandlers::summonServant));

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			if (!(player.level() instanceof ServerLevel level)) {
				return;
			}
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false, false));
			Power power = power();
			ModeMeter.regen(player, power, "shadow_cloak", MAX_CLOAK, CLOAK_REGEN, cloakActive(player));
			if (power != null && !shadowWalking(player)) {
				if (!ExperimentalPowers.state(player).resources.containsKey(KEY + "/shadow_walk")) {
					ExperimentalPowers.setResource(player, power, "shadow_walk", MAX_WALK, MAX_WALK);
				} else if (ExperimentalPowers.getResource(player, power, "shadow_walk") < MAX_WALK) {
					ExperimentalPowers.addResource(player, power, "shadow_walk", WALK_REGEN, MAX_WALK);
				}
			}
			long now = level.getGameTime();
			if (!ROOTED.isEmpty()) {
				ROOTED.forEach((id, expiry) -> {
					if (expiry > now && level.getEntity(id) instanceof LivingEntity le && le.isAlive()) {
						le.setDeltaMovement(le.getDeltaMovement().multiply(0, 1, 0));
						le.hurtMarked = true;
					}
				});
			}
			if (player.tickCount % 4 != 0) {
				return;
			}
			if (!BOUND.isEmpty()) {
				BOUND.entrySet().removeIf(e -> {
					if (e.getValue() <= now) {
						return true;
					}
					if (level.getEntity(e.getKey()) instanceof LivingEntity le && le.isAlive()) {
						shackles(level, le, now);
						return false;
					}
					return true;
				});
			}
		});

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (!active) {
				player.removeEffect(MobEffects.NIGHT_VISION);
				com.projecthero.mod.hero.power.PowerToggles.clearModifier(player,
						net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE, CLOAK_ATK);
				// (Shadow Walk's own toggle-off already ran if the power was removed -- never strip Invisibility here,
				// this runs for every player who does not own the power)
			}
		});

		// Deep Dark: +4 unarmed melee damage. Shadow Walk: a puddle cannot throw a punch.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp && entity instanceof LivingEntity le) {
				Power power = power();
				if (power != null && ExperimentalPowers.owns(sp, power)) {
					if (shadowWalking(sp)) {
						return InteractionResult.FAIL;
					}
					if (isDeepDark(sp.level(), sp.blockPosition())) { // v0.14.11: whatever is held, not just bare-handed
						AbilityHelpers.hurt(sp, le, 4.0f);
					}
				}
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
			ctx.triggerCooldown(17 * 20);
		}
		puddleBurst(ctx.level(), ctx.player().position());
	}

	private static void dropAggro(ServerLevel level, ServerPlayer p, double radius) {
		for (LivingEntity le : AbilityHelpers.living(level, p.position(), radius, x -> x instanceof Mob)) {
			Mob m = (Mob) le;
			if (m.getTarget() == p) {
				m.setTarget(null);
			}
		}
	}

	private static void puddleBurst(ServerLevel level, Vec3 feet) {
		level.sendParticles(ParticleTypes.SQUID_INK, feet.x, feet.y + 1, feet.z, 20, 0.3, 0.5, 0.3, 0.1);
		level.sendParticles(BatchDFx.SHADOW, feet.x, feet.y + 0.05, feet.z, 20, 0.6, 0.02, 0.6, 0.0);
	}

	/** A black tendril erupting from the ground under {@code e}. */
	private static void tendril(ServerLevel level, LivingEntity e) {
		Vec3 base = e.position();
		for (int i = 0; i < 8; i++) {
			double f = i / 7.0;
			double wob = Math.sin(f * Math.PI * 2) * 0.25;
			level.sendParticles(BatchDFx.SHADOW, base.x + wob, base.y + f * e.getBbHeight(), base.z - wob, 1, 0.03, 0.03, 0.03, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, base.x, base.y + 0.2, base.z, 6, 0.3, 0.1, 0.3, 0.02);
	}

	/** Roots {@code e} for {@code ticks} (softened against players by {@link AbilityHelpers#applyControl}). */
	private static void root(ServerPlayer p, LivingEntity e, int ticks) {
		if (!AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, ticks, 9)) {
			return;
		}
		AbilityHelpers.applyControl(e, MobEffects.JUMP, ticks, -10);
		e.setDeltaMovement(e.getDeltaMovement().multiply(0, 1, 0));
		e.hurtMarked = true;
		int t = e instanceof net.minecraft.world.entity.player.Player ? Math.max(10, ticks / 2) : ticks;
		ROOTED.put(e.getId(), p.level().getGameTime() + t);
	}

	/** The Shadow Bind chains: three black strands from the ground spiralling up the bound creature. */
	private static void shackles(ServerLevel level, LivingEntity le, long now) {
		double h = le.getBbHeight();
		double r = le.getBbWidth() * 0.7 + 0.2;
		for (int s = 0; s < 3; s++) {
			for (int i = 0; i < 5; i++) {
				double f = i / 4.0;
				double a = now * 0.25 + s * (Math.PI * 2 / 3) + f * 3.0;
				level.sendParticles(BatchDFx.SHADOW, le.getX() + Math.cos(a) * r, le.getY() + f * h,
						le.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, le.getX(), le.getY() + h * 0.5, le.getZ(), 2, 0.3, 0.4, 0.3, 0.01);
	}

	private static void fireBolt(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), AbilityHelpers.aimPoint(p, 24.0), ParticleTypes.SQUID_INK, 3.0);
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), AbilityHelpers.aimPoint(p, 24.0), BatchDFx.SHADOW_EYE, 0.6);
		if (t != null && !friendly(p, t)) {
			AbilityHelpers.hurt(p, t, dmg(p, BOLT_DAMAGE) + cloakAbilityBonus(p));
			blind4s(t);
		}
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.6f);
	}

	private static void fireBoltVolley(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		double[] yawOffsets = {-20.0, -10.0, 0.0, 10.0, 20.0};
		for (double offset : yawOffsets) {
			fireBoltInDirection(ctx, yawOffsetLook(p, offset));
		}
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.2f, 0.5f);
	}

	private static Vec3 yawOffsetLook(ServerPlayer p, double degrees) {
		double yaw = Math.toRadians(p.getYRot() + degrees);
		double pitch = Math.toRadians(p.getXRot());
		double xz = Math.cos(pitch);
		return new Vec3(-Math.sin(yaw) * xz, -Math.sin(pitch), Math.cos(yaw) * xz).normalize();
	}

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
		for (LivingEntity e : AbilityHelpers.living(ctx.level(), eye, range, le -> le != p && !friendly(p, le))) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
			double dist = to.length();
			if (dist < 1.0e-4 || dist > maxDist) {
				continue;
			}
			if (to.normalize().dot(dir) < 0.94) {
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
			AbilityHelpers.hurtBurst(p, best, dmg(p, BOLT_DAMAGE) + cloakAbilityBonus(p));
			blind4s(best);
		}
	}

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

	// ================================================================= V: Shadow Bind / Shadow Grab

	private static void bindPress(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (GrabHelper.isHolding(ctx)) {
			if (p.isShiftKeyDown()) {
				GrabHelper.dropHeld(ctx);
			} else {
				GrabHelper.throwHeld(ctx, 2.2, dmg(p, 7.0f));
				MutationVisuals.play(p, "throw_right");
			}
			AbilityHelpers.sound(p, SoundEvents.WARDEN_ATTACK_IMPACT, 0.8f, 0.6f);
			ctx.triggerCooldown(20);
			return;
		}
		surface(ctx);
		if (p.isShiftKeyDown()) {
			if (GrabHelper.tryGrab(ctx, 20.0, 200)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
				ctx.level().sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 1, p.getZ(), 15, 0.4, 0.6, 0.4, 0.1);
				MutationVisuals.play(p, "grab_pull");
				AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.7f);
			}
			return;
		}
		LivingEntity t = AbilityHelpers.raycastEntity(p, 24.0);
		if (t == null || friendly(p, t)) {
			ctx.actionBar("message.projecthero.shadow.no_target");
			return;
		}
		int ticks = Math.max(20, Math.round(BIND_TICKS * tier(p)));
		int bound = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, t.position(), 2.5)) {
			if (bound >= 3 || friendly(p, e) || (e != t && e.distanceToSqr(t) > 2.5 * 2.5)) {
				continue;
			}
			bind(p, e, ticks);
			bound++;
		}
		if (bound == 0) {
			bind(p, t, ticks);
		}
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), BatchDFx.centre(t), BatchDFx.SHADOW, 1.5);
		MutationVisuals.play(p, "grab_pull");
		AbilityHelpers.sound(p, SoundEvents.CHAIN_PLACE, 1.0f, 0.5f);
		AbilityHelpers.sound(p, SoundEvents.SCULK_CLICKING, 1.0f, 0.5f);
		ctx.triggerCooldown(Math.max(1, Math.round(ctx.ability().cooldownTicks() / tier(p))));
	}

	/** Shadow Bind on one target: damage, root, slow, weaken, blind, and the chain visual (public for tests). */
	public static void bind(ServerPlayer p, LivingEntity e, int ticks) {
		AbilityHelpers.hurt(p, e, dmg(p, BIND_DAMAGE) + cloakAbilityBonus(p));
		root(p, e, ticks);
		AbilityHelpers.applyControl(e, MobEffects.WEAKNESS, ticks, 1);
		AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, Math.min(ticks, 60), 0);
		BOUND.put(e.getId(), p.level().getGameTime() + ticks);
		if (e instanceof Mob m) {
			m.getNavigation().stop();
		}
		tendril((ServerLevel) p.level(), e);
	}

	public static boolean isBound(LivingEntity e) {
		Long until = BOUND.get(e.getId());
		return until != null && until > e.level().getGameTime();
	}

	// ================================================================= H: Shadow Walk

	private static void walkOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!ExperimentalPowers.state(p).resources.containsKey(KEY + "/shadow_walk")) {
			ctx.setResource("shadow_walk", MAX_WALK, MAX_WALK);
		}
		if (ctx.resource("shadow_walk") < 10.0f) {
			ctx.actionBar("message.projecthero.shadow.walk_low");
			ctx.setToggled(false);
			return;
		}
		MutationVisuals.play(p, "p19.sink");
		puddleBurst(ctx.level(), p.position());
		dropAggro(ctx.level(), p, 16.0);
		AbilityHelpers.sound(p, SoundEvents.SCULK_BLOCK_SPREAD, 1.2f, 0.5f);
	}

	private static void walkTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		float t = tier(p);
		float drain = WALK_DRAIN * (t < 0.75f ? 2.0f : t < 1.0f ? 1.3f : 1.0f);
		ctx.addResource("shadow_walk", -drain, MAX_WALK);
		if (ctx.resource("shadow_walk") <= 0.0f || !p.isAlive()) {
			ctx.setToggled(false);
			endWalk(p, true);
			ctx.actionBar("message.projecthero.shadow.walk_out");
			return;
		}
		p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 10, 0, false, false, false));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 10, 2, false, false, false));
		p.resetFallDistance();
		if (p.tickCount % 10 == 0) {
			dropAggro(ctx.level(), p, 16.0);
		}
		// the puddle: a flat black disc sliding along the ground, with a wisp of ink now and then
		ServerLevel level = ctx.level();
		for (int i = 0; i < 6; i++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double r = Math.sqrt(level.random.nextDouble()) * 0.75;
			level.sendParticles(BatchDFx.SHADOW, p.getX() + Math.cos(a) * r, p.getY() + 0.05, p.getZ() + Math.sin(a) * r,
					1, 0, 0, 0, 0);
		}
		if (p.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.02, 0.3, 0.0);
			level.sendParticles(BatchDFx.SHADOW_EYE, p.getX() + Math.sin(p.tickCount * 0.3) * 0.12, p.getY() + 0.12,
					p.getZ() + Math.cos(p.tickCount * 0.3) * 0.12, 2, 0.08, 0.0, 0.08, 0.0);
		}
	}

	private static void endWalk(ServerPlayer p, boolean fx) {
		Power power = power();
		if (power == null || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		p.removeEffect(MobEffects.INVISIBILITY);
		if (fx) {
			puddleBurst(level, p.position());
			MutationVisuals.play(p, "p19.emerge");
			AbilityHelpers.sound(p, SoundEvents.SCULK_CATALYST_BLOOM, 1.0f, 0.7f);
		}
	}

	// ================================================================= N: Shadow Servant

	private static void summonServant(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		surface(ctx);
		for (ShadowServantEntity old : level.getEntitiesOfClass(ShadowServantEntity.class, p.getBoundingBox().inflate(64.0),
				s -> s.ownerId().map(p.getUUID()::equals).orElse(false))) {
			old.dissolve();
		}
		ShadowServantEntity s = BatchDContent.SHADOW_SERVANT.create(level);
		if (s == null) {
			return;
		}
		Vec3 fwd = p.getLookAngle().multiply(1, 0, 1);
		fwd = fwd.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : fwd.normalize();
		Vec3 at = p.position().add(fwd.scale(1.8));
		if (!level.noCollision(s, s.getType().getDimensions().makeBoundingBox(at))) {
			at = p.position();
		}
		s.moveTo(at.x, at.y, at.z, p.getYRot(), 0.0f);
		s.setYHeadRot(p.getYRot());
		s.bind(p, dmg(p, SERVANT_DAMAGE) + cloakAbilityBonus(p) * 0.5f);
		level.addFreshEntity(s);
		for (int i = 0; i < 12; i++) {
			level.sendParticles(BatchDFx.SHADOW, at.x, at.y + i * 0.16, at.z, 3, 0.3, 0.05, 0.3, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1.0, at.z, 20, 0.3, 0.8, 0.3, 0.05);
		MutationVisuals.play(p, "summon_ground");
		AbilityHelpers.sound(p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.7f, 0.6f);
		ctx.triggerCooldown();
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
			for (LivingEntity e : AbilityHelpers.living(level, center, 20.0, le -> le != owner && !friendly(owner, le))) {
				e.setDeltaMovement(e.getDeltaMovement().x, Math.min(e.getDeltaMovement().y, -0.05), e.getDeltaMovement().z);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 20, 2);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 20, 0);
				if (age % 20 == 0) {
					AbilityHelpers.hurt(owner, e, ZONE_DPS);
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
