package com.projecthero.mod.hero.power.p17;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.GrabHelper;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Power 17 — Elasticity.
 *
 * <p>v0.10.13: a rubber body is now a package of always-on passives (no fall damage, a small bounce
 * on any real fall, squeeze through a one-block gap, 25% less melee damage, 50% knockback
 * resistance), the primary is a charge-up stretch punch, the secondary doubles as a ground-slam from
 * height, and Elastic Form is one of three body shapes chosen from a weapon wheel on Shift + C.
 */
public final class ElasticityHandlers {
	private static final String KEY = "power_17_elasticity";

	private static final ResourceLocation FORM_REACH = com.projecthero.mod.ProjectHeroMod.id("elastic_form_reach");
	private static final ResourceLocation FORM_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_form_speed");
	private static final ResourceLocation FORM_SCALE = com.projecthero.mod.ProjectHeroMod.id("elastic_form_scale");
	private static final ResourceLocation FORM_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_form_kb");
	/** Always-on passives. */
	private static final ResourceLocation PASSIVE_MELEE = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_melee");
	private static final ResourceLocation PASSIVE_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_kb");

	/** Slingshot: how far it looks for an anchor, and how long the anchored dash may run. */
	private static final double SLING_RANGE = 45.0;
	private static final float SLING_TICKS = 40.0f;
	private static final double SLING_IMPACT_RANGE = 2.6;
	private static final float SLING_DAMAGE = 14.0f;

	/** Stretch Punch charge: +5 damage per second, up to 2 s; every extra second held is +1 s cooldown. */
	private static final int STRETCH_BASE_DMG = 12;
	private static final int STRETCH_DMG_PER_SEC = 5;
	private static final int STRETCH_MAX_DMG_SECONDS = 2;
	private static final int STRETCH_MAX_TRACK_SECONDS = 6;

	/** Dive slam (double_fist_slam from height). */
	private static final double SLAM_MIN_HEIGHT = 5.0;
	private static final float SLAM_DAMAGE = 20.0f;

	/** The three body shapes chosen from the Shift + C wheel. Wire index == ordinal. */
	public enum Form { ELASTIC, INFLATED, COMPRESSION }

	private ElasticityHandlers() {
	}

	// ---- ownership / form queries -------------------------------------------------------------

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	/** True while the Elastic Form toggle is on (any of the three shapes). Read from the synced attachment. */
	public static boolean formActiveClient(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY)
				&& st.activeToggles.contains(KEY + "/elastic_form");
	}

	private static boolean formActive(Player p) {
		return formActiveClient(p);
	}

	/** The currently selected body shape (only meaningful while Elastic Form is toggled on). */
	public static Form form(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		if (st == null) {
			return Form.ELASTIC;
		}
		int idx = Math.round(st.resources.getOrDefault(KEY + "/form", 0.0f));
		Form[] all = Form.values();
		return all[Math.floorMod(idx, all.length)];
	}

	/** Elastic form (only) shrugs projectiles off outright -- read by {@link com.projecthero.mod.hero.power.HeroDamageRules}. */
	public static boolean deflectsProjectiles(ServerPlayer p) {
		return formActive(p) && form(p) == Form.ELASTIC;
	}

	/** Damage-taken multiplier from the current form (Inflated: half). */
	public static float damageTakenFactor(ServerPlayer p) {
		return formActive(p) && form(p) == Form.INFLATED ? 0.5f : 1.0f;
	}

	// ---- registration ----------------------------------------------------------------------------

	public static void register() {
		// R -- Stretch Punch. Tap for the base hit; hold to charge (+5 dmg/s, capped at +10). Holding
		// past 2 s adds nothing to the damage but each extra second adds a second of cooldown.
		AbilityHandlers.register(KEY, "stretch_punch", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				ctx.setResource("stretch_on", 1, 1);
				ctx.setResource("stretch_start", ctx.player().level().getGameTime(), 1.0e12f);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("stretch_on") < 0.5f) {
					return;
				}
				float start = ctx.resource("stretch_start");
				long held = ctx.player().level().getGameTime() - (long) start;
				if (held > STRETCH_MAX_TRACK_SECONDS * 20 + 20) {
					fireStretch(ctx); // safety: released event lost
					return;
				}
				if (held % 4 == 0) {
					ServerPlayer p = ctx.player();
					ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(),
							3 + (int) Math.min(12, held / 3), 0.3, 0.4, 0.3, 0.02);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				fireStretch(ctx);
			}
		});

		// G -- Double Fist Slam. On the ground it is the old forward arc; more than 5 blocks up it becomes
		// a straight-down ground pound for 20 damage.
		AbilityHandlers.register(KEY, "double_fist_slam", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (heightAboveGround(p) > SLAM_MIN_HEIGHT) {
				p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -2.6, p.getDeltaMovement().z * 0.2);
				p.hurtMarked = true;
				p.hasImpulse = true;
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				ctx.setResource("slamming", 1, 1);
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.5f);
				ctx.triggerCooldown();
				return;
			}
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(7));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, 17.0f);
				AbilityHelpers.knockbackFrom(e, p.getEyePosition(), 2.0);
			}
			AbilityHelpers.burst(ctx.level(), front, ParticleTypes.ITEM_SLIME, 30, 0.6);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.2f, 0.7f);
			ctx.triggerCooldown();
		}, ctx -> {
			if (ctx.resource("slamming") < 0.5f) {
				return;
			}
			ServerPlayer p = ctx.player();
			if (!p.onGround() && !p.isInWater()) {
				p.setDeltaMovement(p.getDeltaMovement().x * 0.2, Math.min(p.getDeltaMovement().y, -2.6),
						p.getDeltaMovement().z * 0.2);
				p.hasImpulse = true;
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				p.resetFallDistance();
				return;
			}
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 5.0)) {
				AbilityHelpers.hurt(p, e, SLAM_DAMAGE);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			}
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY(), p.getZ(), 60, 2.5, 0.3, 2.5, 0.15);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
			ctx.setResource("slamming", 0, 1);
		}));

		AbilityHandlers.register(KEY, "slingshot", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity target = AbilityHelpers.raycastEntity(p, SLING_RANGE);
			if (target != null) {
				Vec3 pull = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
				AbilityHelpers.launchSelf(p, pull.normalize().scale(2.6).add(0, 0.25, 0));
				ctx.setResource("sling_id", target.getId(), 1.0e9f);
				ctx.setResource("sling_ticks", SLING_TICKS, SLING_TICKS);
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 3.0);
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.8f);
				ctx.triggerCooldown();
				return;
			}
			var hit = AbilityHelpers.raycastBlock(p, SLING_RANGE);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 pull = Vec3.atCenterOf(hit.getBlockPos()).subtract(p.position()).normalize().scale(2.8);
			AbilityHelpers.launchSelf(p, pull.add(0, 0.3, 0));
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}, ElasticityHandlers::slingTick));

		AbilityHandlers.register(KEY, "giant_hammer_fist", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 8.0);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.5)) {
				AbilityHelpers.hurt(p, e, 34.0f);
				AbilityHelpers.knockbackFrom(e, at, 1.5);
				AbilityHelpers.push(e, new Vec3(0, -0.4, 0));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 2);
			}
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 80, 2.5, 0.5, 2.5, 0.2);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "elastic_grab", Handlers.instantTicking(ctx -> {
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.4, 7.0f);
				ctx.triggerCooldown();
			} else if (GrabHelper.tryGrab(ctx, 15.0, 120)) {
				ctx.actionBar("message.projecthero.ability.grabbed");
			}
		}, ctx -> GrabHelper.tick(ctx, 2.5)));

		// C -- Elastic Form. Plain C toggles Elastic (the default shape); Shift + C opens the wheel to
		// pick Elastic / Inflated / Compression (and turns the form on if it was off).
		AbilityHandlers.register(KEY, "elastic_form", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					// keep the toggle on but let the wheel choose the shape
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
							com.projecthero.mod.network.ElasticFormWheelPayload.INSTANCE);
				} else {
					ctx.setResource("form", Form.ELASTIC.ordinal(), 10);
				}
				applyForm(p, form(p));
				AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 0.8f, 1.4f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (p.isShiftKeyDown()) {
					// Shift + C always means "open the form wheel", never "turn the form off".
					ctx.setToggled(true);
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p,
							com.projecthero.mod.network.ElasticFormWheelPayload.INSTANCE);
					return;
				}
				clearForm(p);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				applyForm(p, form(p));
				AbilityHelpers.modeAura(p, ParticleTypes.ITEM_SLIME, 3);
			}
		});

		// Enemies that land a melee hit on Elastic Form get bounced back -- gently in Elastic, and flung
		// ~10 blocks in Inflated.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(entity instanceof ServerPlayer sp) || !formActive(sp)) {
				return;
			}
			if (!(source.getEntity() instanceof LivingEntity attacker) || sp.distanceToSqr(attacker) >= 100.0) {
				return;
			}
			Form f = form(sp);
			if (f == Form.COMPRESSION) {
				return;
			}
			Vec3 away = attacker.position().subtract(sp.position());
			away = away.lengthSqr() < 1.0e-4 ? sp.getLookAngle().reverse() : away.normalize();
			double power = f == Form.INFLATED ? 2.6 : 0.9;
			attacker.setDeltaMovement(away.scale(power).add(0, f == Form.INFLATED ? 0.6 : 0.35, 0));
			attacker.hurtMarked = true;
			attacker.hasImpulse = true;
			if (f == Form.INFLATED) {
				AbilityHelpers.sound(sp, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
			}
		});

		registerPassives();
	}

	// ---- Stretch Punch charge -------------------------------------------------------------------

	private static void fireStretch(AbilityContext ctx) {
		if (ctx.resource("stretch_on") < 0.5f) {
			return;
		}
		float start = ctx.resource("stretch_start");
		ctx.setResource("stretch_on", 0, 1);
		ctx.setResource("stretch_start", 0, 1.0e12f);
		ServerPlayer p = ctx.player();
		long heldTicks = Math.max(0L, p.level().getGameTime() - (long) start);
		int heldSeconds = (int) Math.min(STRETCH_MAX_TRACK_SECONDS, heldTicks / 20);
		int dmgSeconds = Math.min(STRETCH_MAX_DMG_SECONDS, heldSeconds);
		float damage = STRETCH_BASE_DMG + dmgSeconds * STRETCH_DMG_PER_SEC;

		LivingEntity t = AbilityHelpers.raycastEntity(p, 15.0 + dmgSeconds * 2.0);
		AbilityHelpers.line(ctx.level(), p.getEyePosition(),
				AbilityHelpers.aimPoint(p, 15.0 + dmgSeconds * 2.0), ParticleTypes.ITEM_SLIME, 3.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, damage);
			AbilityHelpers.knockbackFrom(t, p.position(), 0.9 + dmgSeconds * 0.4);
		}
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.2f - heldSeconds * 0.15f);
		// base 2 s cooldown, +1 s for every second the punch was held past release-immediately.
		ctx.triggerCooldown((2 + heldSeconds) * 20);
	}

	// ---- Slingshot upkeep (unchanged) ---------------------------------------------------------

	private static void slingTick(AbilityContext ctx) {
		float left = ctx.resource("sling_ticks");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("sling_ticks", left - 1.0f, SLING_TICKS);
		int id = (int) ctx.resource("sling_id");
		if (id == 0 || !(p.level().getEntity(id) instanceof LivingEntity target) || !target.isAlive()) {
			endSling(ctx);
			return;
		}
		Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
		double dist = to.length();
		if (dist <= SLING_IMPACT_RANGE) {
			AbilityHelpers.hurt(p, target, SLING_DAMAGE);
			AbilityHelpers.knockbackFrom(target, p.position(), 1.2);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			p.setDeltaMovement(p.getDeltaMovement().scale(-0.15));
			p.hurtMarked = true;
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 1.0, target.getZ(),
					40, 0.5, 0.5, 0.5, 0.15);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.3f, 0.8f);
			endSling(ctx);
			return;
		}
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(2.6, 0.9 + dist * 0.12)).add(0, 0.08, 0));
		p.resetFallDistance();
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 2.0);
	}

	private static void endSling(AbilityContext ctx) {
		ctx.setResource("sling_ticks", 0, SLING_TICKS);
		ctx.setResource("sling_id", 0, 1.0e9f);
	}

	// ---- forms --------------------------------------------------------------------------------

	/** Choose a form from the weapon wheel (C2S). Ensures Elastic Form is on and applies the shape. */
	public static void chooseForm(ServerPlayer p, int index) {
		Power power = Powers.byKey(KEY);
		if (power == null || !ExperimentalPowers.owns(p, power)) {
			return;
		}
		Form[] all = Form.values();
		Form f = all[Math.floorMod(index, all.length)];
		var ability = power.ability(AbilitySlot.SLOT_6);
		if (!ExperimentalPowers.isToggled(p, power, ability)) {
			ExperimentalPowers.setToggled(p, power, ability, true);
		}
		ExperimentalPowers.setResource(p, power, "form", f.ordinal(), 10);
		applyForm(p, f);
		p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
				"message.projecthero.elasticity.form_" + f.name().toLowerCase(java.util.Locale.ROOT)), true);
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.0f, f == Form.COMPRESSION ? 1.6f : (f == Form.INFLATED ? 0.5f : 1.1f));
	}

	private static void applyForm(ServerPlayer p, Form f) {
		// reach: Elastic stretches; Inflated is average; Compression is short-armed
		double reach = switch (f) {
			case ELASTIC -> 5.0;
			case INFLATED -> 0.0;
			case COMPRESSION -> -1.0;
		};
		double speed = switch (f) {
			case ELASTIC -> 0.3;
			case INFLATED -> 0.0;
			case COMPRESSION -> 0.25;
		};
		double scale = switch (f) {
			case ELASTIC -> 0.0;
			case INFLATED -> 0.25;   // rounder / bigger (kept modest so it does not clip low ceilings)
			case COMPRESSION -> -0.45; // ~1 block tall
		};
		double kb = f == Form.INFLATED ? 1.0 : 0.0; // 100% knockback resistance (on top of the 50% passive)
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, FORM_REACH, reach, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, FORM_SPEED, speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.SCALE, FORM_SCALE, scale, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, FORM_KB, kb, AttributeModifier.Operation.ADD_VALUE);

		if (f == Form.ELASTIC) {
			ensureInfiniteEffect(p, MobEffects.JUMP, 3);
			p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		} else {
			p.removeEffect(MobEffects.JUMP);
		}
		if (f == Form.INFLATED) {
			ensureInfiniteEffect(p, MobEffects.MOVEMENT_SLOWDOWN, 1); // Slowness II
		}
	}

	private static void clearForm(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, FORM_REACH);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, FORM_SPEED);
		PowerToggles.clearModifier(p, Attributes.SCALE, FORM_SCALE);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, FORM_KB);
		p.removeEffect(MobEffects.JUMP);
		p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
		p.removeEffect(MobEffects.SLOW_FALLING);
	}

	private static void ensureInfiniteEffect(ServerPlayer p, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amp) {
		var cur = p.getEffect(effect);
		if (cur == null || !cur.isInfiniteDuration() || cur.getAmplifier() != amp) {
			p.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, amp, false, false, false));
		}
	}

	// ---- passives ---------------------------------------------------------------------------------

	private static void registerPassives() {
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, owned) -> {
			if (!owned) {
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_MELEE);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB);
				clearForm(player);
			}
		});
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			// 25% less melee damage, 50% knockback resistance -- always on for the rubber body.
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, PASSIVE_MELEE, -0.25,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB, 0.5,
					AttributeModifier.Operation.ADD_VALUE);
			// respawn-safe re-apply of the active shape
			if (formActive(player)) {
				applyForm(player, form(player));
			}
		});
	}

	private static double heightAboveGround(ServerPlayer p) {
		net.minecraft.core.BlockPos.MutableBlockPos c = p.blockPosition().mutable();
		for (int i = 0; i <= 24; i++) {
			if (!p.level().getBlockState(c).getCollisionShape(p.level(), c).isEmpty()) {
				return p.getY() - (c.getY() + 1.0);
			}
			c.move(0, -1, 0);
			if (c.getY() < p.level().getMinBuildHeight()) {
				break;
			}
		}
		return 64.0;
	}
}
