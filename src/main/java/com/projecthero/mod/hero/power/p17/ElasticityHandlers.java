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
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Power 17 — Elasticity.
 *
 * <p>v0.10.13: a rubber body is now a package of always-on passives (no fall damage, a small bounce
 * on any real fall, squeeze through a one-block gap, 25% less melee damage, 50% knockback
 * resistance), the primary is a charge-up stretch punch, the secondary doubles as a ground-slam from
 * height, and Elastic Form is one of three body shapes chosen from a weapon wheel on Shift + C.
 *
 * <p>v0.13.22 revamp (batch E): the arm now visibly STRETCHES to the target on every stretch / grab move
 * (a synced arm length + start tick drive {@code RevampClientE}'s stretched-arm overlay), every move has a
 * body animation, damage ~+20% / cooldowns ~-15%, and two new utility moves: H <b>Rubber Shield</b> (hold:
 * inflate into a balloon that bounces projectiles back at their shooter and flings melee attackers away)
 * and N <b>Parachute Glide</b> (flatten out into a canopy: slow fall plus a steered forward glide, 8 s max).
 */
public final class ElasticityHandlers {
	private static final String KEY = "power_17_elasticity";

	private static final ResourceLocation FORM_REACH = com.projecthero.mod.ProjectHeroMod.id("elastic_form_reach");
	private static final ResourceLocation FORM_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_form_speed");
	private static final ResourceLocation FORM_SCALE = com.projecthero.mod.ProjectHeroMod.id("elastic_form_scale");
	private static final ResourceLocation FORM_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_form_kb");
	private static final ResourceLocation FORM_MELEE = com.projecthero.mod.ProjectHeroMod.id("elastic_form_melee");
	private static final ResourceLocation SHIELD_SPEED = com.projecthero.mod.ProjectHeroMod.id("elastic_shield_speed");
	private static final ResourceLocation SHIELD_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_shield_kb");
	/** Elastic Form adds this much to every Elasticity ability's damage. */
	public static final float FORM_ABILITY_BONUS = 9.5f;
	/** Always-on passives. */
	private static final ResourceLocation PASSIVE_MELEE = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_melee");
	private static final ResourceLocation PASSIVE_KB = com.projecthero.mod.ProjectHeroMod.id("elastic_passive_kb");

	/** Slingshot: how far it looks for an anchor, and how long the anchored dash may run. */
	private static final double SLING_RANGE = 45.0;
	private static final float SLING_TICKS = 40.0f;
	private static final double SLING_IMPACT_RANGE = 2.6;
	private static final float SLING_DAMAGE = 17.0f;

	/** Stretch Punch charge: +6 damage per second, up to 2 s; every extra second held is +0.85 s cooldown. */
	private static final float STRETCH_BASE_DMG = 14.5f;
	private static final float STRETCH_DMG_PER_SEC = 6.0f;
	private static final int STRETCH_MAX_DMG_SECONDS = 2;
	private static final int STRETCH_MAX_TRACK_SECONDS = 6;

	/** Dive slam (double_fist_slam from height). */
	private static final double SLAM_MIN_HEIGHT = 5.0;
	private static final float SLAM_DAMAGE = 24.0f;
	private static final float SLAM_ARC_DAMAGE = 20.5f;
	private static final float HAMMER_DAMAGE = 41.0f;
	private static final float GRAB_THROW_DAMAGE = 8.5f;

	/** Rubber Shield stamina (the HUD's "Rubber" bar) -- 5 s of full inflation, refills in ~9 s. */
	public static final float MAX_RUBBER = 115.0f;
	private static final float RUBBER_DRAIN = MAX_RUBBER / 100.0f;
	private static final float RUBBER_REGEN = MAX_RUBBER / 180.0f;
	private static final float RUBBER_MIN = 20.0f;
	/** Parachute Glide: at most 8 s aloft. */
	public static final int GLIDE_TICKS = 160;

	/** How long a stretched arm stays drawn after a strike (the client animates out and back in this window). */
	private static final int ARM_STRIKE_TICKS = 14;
	/** Arm draw modes (sent +1 as the {@code p17.arm_mode} visual value). */
	public static final int ARM_RIGHT = 0;
	public static final int ARM_BOTH = 1;
	public static final int ARM_HAMMER = 2;
	public static final int ARM_HOLD = 3;

	/** The three body shapes chosen from the Shift + C wheel. Wire index == ordinal. */
	public enum Form { ELASTIC, INFLATED, COMPRESSION }

	private ElasticityHandlers() {
	}

	// ---- ownership / form queries -------------------------------------------------------------

	public static boolean owns(Player p) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY);
	}

	private static float res(Player p, String name) {
		ExperimentalState st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st == null ? 0.0f : st.resources.getOrDefault(KEY + "/" + name, 0.0f);
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

	/** Rubber Shield is inflated right now (synced -- the client overlay and HeroDamageRules both read it). */
	public static boolean shielding(Player p) {
		return owns(p) && res(p, "shielding") > 0.5f;
	}

	/** Parachute Glide is running (synced -- the client glide physics read it). */
	public static boolean gliding(Player p) {
		return owns(p) && res(p, "glide_ticks") > 0.5f;
	}

	/** A stretched arm is currently drawn. */
	public static boolean armStretched(Player p) {
		return owns(p) && res(p, "arm_ticks") > 0.5f;
	}

	/** Stretched-arm length in blocks (0 when none) -- the {@code p17.arm_len} visual value. */
	public static float armLength(Player p) {
		return armStretched(p) ? res(p, "arm_len") : 0.0f;
	}

	/** Stretch start tick (game time mod 8192, +1 so it is never 0) -- the {@code p17.arm_t} visual value. */
	public static float armStart(Player p) {
		return armStretched(p) ? res(p, "arm_t") : 0.0f;
	}

	/** Arm draw mode + 1 -- the {@code p17.arm_mode} visual value. */
	public static float armMode(Player p) {
		return armStretched(p) ? res(p, "arm_mode") + 1.0f : 0.0f;
	}

	/** Any active body shape shrugs projectiles off outright -- read by {@link com.projecthero.mod.hero.power.HeroDamageRules}. */
	public static boolean deflectsProjectiles(ServerPlayer p) {
		return formActive(p) || shielding(p);
	}

	/** Extra damage every Elasticity ability deals while Elastic Form (the default shape) is on. */
	public static float formAbilityBonus(ServerPlayer p) {
		return formActive(p) && form(p) == Form.ELASTIC ? FORM_ABILITY_BONUS : 0.0f;
	}

	/** Damage-taken multiplier from the current form (Inflated: half) and the Rubber Shield (melee: -65%). */
	public static float damageTakenFactor(ServerPlayer p) {
		float f = formActive(p) && form(p) == Form.INFLATED ? 0.5f : 1.0f;
		if (shielding(p)) {
			f *= 0.35f;
		}
		return f;
	}

	/**
	 * Start drawing a stretched arm: {@code blocks} long, for {@code ticks} ticks, in draw mode {@code mode}.
	 * Re-calling with {@link #ARM_HOLD} every tick keeps a held grab's arm out.
	 */
	public static void stretchArm(ServerPlayer p, double blocks, int ticks, int mode) {
		Power power = Powers.byKey(KEY);
		boolean renewing = mode == ARM_HOLD && res(p, "arm_ticks") > 0.5f && Math.round(res(p, "arm_mode")) == ARM_HOLD;
		if (renewing && res(p, "arm_ticks") > 2.5f && Math.abs(res(p, "arm_len") - blocks) < 0.4) {
			return; // a held arm that has not moved: skip the resync
		}
		if (!renewing) {
			ExperimentalPowers.setResource(p, power, "arm_t", (float) (p.level().getGameTime() % 8192L) + 1.0f, 9000.0f);
		}
		ExperimentalPowers.setResource(p, power, "arm_len", (float) Math.max(0.5, Math.min(48.0, blocks)), 64.0f);
		ExperimentalPowers.setResource(p, power, "arm_ticks", ticks, 200.0f);
		ExperimentalPowers.setResource(p, power, "arm_mode", mode, 10.0f);
	}

	// ---- registration ----------------------------------------------------------------------------

	public static void register() {
		// R -- Stretch Punch. Tap for the base hit; hold to charge (+6 dmg/s, capped at +12). Holding
		// past 2 s adds nothing to the damage but each extra second adds 0.85 s of cooldown.
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
				MutationVisuals.play(ctx.player(), "p17.windup");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				armTick(ctx);
				if (ctx.resource("stretch_on") < 0.5f) {
					return;
				}
				float start = ctx.resource("stretch_start");
				long held = ctx.player().level().getGameTime() - (long) start;
				if (held > STRETCH_MAX_TRACK_SECONDS * 20 + 20) {
					fireStretch(ctx); // safety: released event lost
					return;
				}
				MutationVisuals.ensure(ctx.player(), "p17.windup");
				ctx.setResource("stretch_charge",
						(float) Math.min(100.0, held / (double) (STRETCH_MAX_DMG_SECONDS * 20) * 100.0), 100);
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

		// G -- Double Fist Slam. On the ground both arms shoot out for a heavy forward smash; more than 5
		// blocks up it becomes a straight-down ground pound.
		AbilityHandlers.register(KEY, "double_fist_slam", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (heightAboveGround(p) > SLAM_MIN_HEIGHT) {
				p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -2.6, p.getDeltaMovement().z * 0.2);
				p.hurtMarked = true;
				p.hasImpulse = true;
				p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
				ctx.setResource("slamming", 1, 1);
				MutationVisuals.play(p, "slam_two_hand");
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.5f);
				ctx.triggerCooldown();
				return;
			}
			Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(7));
			MutationVisuals.play(p, "p17.stretch_both");
			stretchArm(p, 6.5, ARM_STRIKE_TICKS, ARM_BOTH);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, front, 4.0)) {
				AbilityHelpers.hurt(p, e, SLAM_ARC_DAMAGE + formAbilityBonus(p));
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
				AbilityHelpers.hurt(p, e, SLAM_DAMAGE + formAbilityBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			}
			MutationVisuals.play(p, "hero_landing");
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
				stretchArm(p, pull.length(), 6, ARM_HOLD);
				MutationVisuals.play(p, "p17.sling");
				AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 3.0);
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.8f);
				ctx.triggerCooldown();
				return;
			}
			var hit = AbilityHelpers.raycastBlock(p, SLING_RANGE);
			if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
				return;
			}
			Vec3 anchor = Vec3.atCenterOf(hit.getBlockPos());
			Vec3 pull = anchor.subtract(p.position()).normalize().scale(2.8);
			AbilityHelpers.launchSelf(p, pull.add(0, 0.3, 0));
			stretchArm(p, anchor.distanceTo(p.getEyePosition()), 10, ARM_RIGHT);
			MutationVisuals.play(p, "p17.stretch_right");
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}, ElasticityHandlers::slingTick));

		// Z -- Giant Hammer Fist: both arms stretch out and swell into giant fists that slam the aim point.
		AbilityHandlers.register(KEY, "giant_hammer_fist", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 at = AbilityHelpers.aimPoint(p, 8.0);
			MutationVisuals.play(p, "p17.hammer");
			stretchArm(p, at.distanceTo(p.getEyePosition()) - 0.6, 18, ARM_HAMMER);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, at, 4.5)) {
				AbilityHelpers.hurt(p, e, HAMMER_DAMAGE + formAbilityBonus(p));
				AbilityHelpers.knockbackFrom(e, at, 1.5);
				AbilityHelpers.push(e, new Vec3(0, -0.4, 0));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 50, 2);
			}
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, at.x, at.y, at.z, 80, 2.5, 0.5, 2.5, 0.2);
			ctx.level().sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.4f, 0.4f);
			ctx.triggerCooldown();
		}));

		// V -- Elastic Grab: the arm shoots out, wraps the target and reels it in; press again to hurl it.
		AbilityHandlers.register(KEY, "elastic_grab", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.4, GRAB_THROW_DAMAGE + formAbilityBonus(p));
				MutationVisuals.play(p, "throw_right");
				stretchArm(p, 3.0, 8, ARM_RIGHT);
				ctx.triggerCooldown();
				return;
			}
			LivingEntity aimed = AbilityHelpers.raycastEntity(p, 15.0);
			if (GrabHelper.tryGrab(ctx, 15.0, 120)) {
				if (aimed != null) {
					stretchArm(p, aimed.distanceTo(p), 4, ARM_HOLD);
					AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), aimed.position().add(0, aimed.getBbHeight() * 0.5, 0),
							ParticleTypes.ITEM_SLIME, 2.0);
				}
				MutationVisuals.play(p, "p17.grab_hold");
				AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.3f);
				ctx.actionBar("message.projecthero.ability.grabbed");
			} else {
				MutationVisuals.play(p, "p17.stretch_right");
				stretchArm(p, 12.0, 10, ARM_RIGHT);
			}
		}, ctx -> {
			boolean was = GrabHelper.isHolding(ctx);
			GrabHelper.tick(ctx, 2.5);
			ServerPlayer p = ctx.player();
			if (GrabHelper.isHolding(ctx)) {
				LivingEntity held = GrabHelper.held(ctx);
				if (held != null) {
					stretchArm(p, Math.max(1.0, held.distanceTo(p) - 0.4), 6, ARM_HOLD);
				}
				MutationVisuals.ensure(p, "p17.grab_hold");
			} else if (was) {
				MutationVisuals.stopIf(p, "p17.grab_hold");
			}
		}));

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
				MutationVisuals.play(p, "p17.form_stretch");
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

		// H -- Rubber Shield (hold): inflate into a balloon. Projectiles bounce straight back at whoever fired
		// them, melee attackers are flung away, melee damage is cut 65%, but you are slow and it drains Rubber.
		AbilityHandlers.register(KEY, "rubber_shield", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.1f", ctx.cooldownRemaining() / 20.0f));
					return;
				}
				seedRubber(p);
				if (ctx.resource("rubber") < RUBBER_MIN) {
					ctx.actionBar("message.projecthero.elasticity.rubber_low");
					return;
				}
				ctx.setResource("shielding", 1, 1);
				PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SPEED, -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, SHIELD_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
				MutationVisuals.play(p, "p17.inflate");
				ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.6, 0.7, 0.6, 0.1);
				AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.2f, 0.45f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endShield(ctx);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				shieldTick(ctx);
			}
		});

		// N -- Parachute Glide: flatten into a canopy -- slow fall plus a forward glide steered by where you
		// look, up to 8 s or until you land. Press again to fold up early. No sneak variant (Sneak+N is combos).
		AbilityHandlers.register(KEY, "parachute_glide", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			if (ctx.resource("glide_ticks") > 0.5f) {
				endGlide(ctx);
				return;
			}
			startGlide(ctx);
		}, ElasticityHandlers::glideTick));

		// Enemies that land a melee hit on Elastic Form get bounced back -- gently in Elastic, ~10 blocks in
		// Inflated, and hardest of all off the inflated Rubber Shield.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!(entity instanceof ServerPlayer sp) || !(formActive(sp) || shielding(sp))) {
				return;
			}
			if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
					|| sp.distanceToSqr(attacker) >= 100.0) {
				return;
			}
			bounceAttacker(sp, attacker);
		});
		// the same bounce when the whole hit was swallowed (Rubber Shield immune projectile / low damage)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer sp && shielding(sp) && source.getEntity() instanceof LivingEntity attacker
					&& source.getDirectEntity() == attacker && sp.distanceToSqr(attacker) < 100.0 && amount * 0.35f < 0.5f) {
				bounceAttacker(sp, attacker);
			}
			return true;
		});

		registerPassives();
	}

	/** Flings a melee attacker away -- the Elastic / Inflated form bounce, or the Rubber Shield's much harder one. */
	public static void bounceAttacker(ServerPlayer sp, LivingEntity attacker) {
		boolean shield = shielding(sp);
		Form f = form(sp);
		if (!shield && f == Form.COMPRESSION) {
			return;
		}
		Vec3 away = attacker.position().subtract(sp.position());
		away = away.lengthSqr() < 1.0e-4 ? sp.getLookAngle().reverse() : away.normalize();
		double power = shield ? 3.0 : f == Form.INFLATED ? 2.6 : 0.9;
		attacker.setDeltaMovement(away.scale(power).add(0, shield || f == Form.INFLATED ? 0.6 : 0.35, 0));
		attacker.hurtMarked = true;
		attacker.hasImpulse = true;
		if (shield || f == Form.INFLATED) {
			AbilityHelpers.sound(sp, SoundEvents.SLIME_SQUISH, 1.2f, 0.6f);
			AbilityHelpers.level(sp).sendParticles(ParticleTypes.ITEM_SLIME, attacker.getX(), attacker.getY() + 1.0,
					attacker.getZ(), 12, 0.3, 0.4, 0.3, 0.1);
		}
	}

	// ---- stretched arm upkeep --------------------------------------------------------------------

	private static void armTick(AbilityContext ctx) {
		float left = ctx.resource("arm_ticks");
		if (left > 0.5f) {
			ctx.setResource("arm_ticks", left - 1, 200.0f);
		}
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
		float damage = STRETCH_BASE_DMG + dmgSeconds * STRETCH_DMG_PER_SEC + formAbilityBonus(p);
		ctx.setResource("stretch_charge", 0, 100);

		double range = 15.0 + dmgSeconds * 2.0;
		LivingEntity t = AbilityHelpers.raycastEntity(p, range);
		Vec3 end = AbilityHelpers.aimPoint(p, range);
		MutationVisuals.play(p, "p17.stretch_right");
		stretchArm(p, end.distanceTo(p.getEyePosition()) - 0.5, ARM_STRIKE_TICKS, ARM_RIGHT);
		AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), end, ParticleTypes.ITEM_SLIME, 2.0);
		if (t != null) {
			AbilityHelpers.hurt(p, t, damage);
			AbilityHelpers.knockbackFrom(t, p.position(), 0.9 + dmgSeconds * 0.4);
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(),
					16, 0.3, 0.3, 0.3, 0.12);
		}
		AbilityHelpers.sound(p, SoundEvents.SLIME_ATTACK, 1.0f, 1.2f - heldSeconds * 0.15f);
		// base 1.7 s cooldown, +0.85 s for every second the punch was held past release-immediately.
		ctx.triggerCooldown(34 + heldSeconds * 17);
	}

	// ---- Slingshot upkeep ---------------------------------------------------------------------

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
			AbilityHelpers.hurt(p, target, SLING_DAMAGE + formAbilityBonus(p));
			AbilityHelpers.knockbackFrom(target, p.position(), 1.2);
			AbilityHelpers.applyControl(target, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			p.setDeltaMovement(p.getDeltaMovement().scale(-0.15));
			p.hurtMarked = true;
			MutationVisuals.play(p, "double_punch");
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY() + 1.0, target.getZ(),
					40, 0.5, 0.5, 0.5, 0.15);
			AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH, 1.3f, 0.8f);
			endSling(ctx);
			return;
		}
		stretchArm(p, dist, 4, ARM_HOLD);
		MutationVisuals.ensure(p, "p17.sling");
		AbilityHelpers.launchSelf(p, to.normalize().scale(Math.min(2.6, 0.9 + dist * 0.12)).add(0, 0.08, 0));
		p.resetFallDistance();
		AbilityHelpers.line(ctx.level(), p.getEyePosition(), target.position(), ParticleTypes.ITEM_SLIME, 2.0);
	}

	private static void endSling(AbilityContext ctx) {
		ctx.setResource("sling_ticks", 0, SLING_TICKS);
		ctx.setResource("sling_id", 0, 1.0e9f);
		MutationVisuals.stopIf(ctx.player(), "p17.sling");
	}

	// ---- H: Rubber Shield -----------------------------------------------------------------------

	private static void seedRubber(ServerPlayer p) {
		if (!ExperimentalPowers.state(p).resources.containsKey(KEY + "/rubber")) {
			ExperimentalPowers.setResource(p, Powers.byKey(KEY), "rubber", MAX_RUBBER, MAX_RUBBER);
		}
	}

	private static void shieldTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("shielding") < 0.5f) {
			return;
		}
		ctx.addResource("rubber", -RUBBER_DRAIN, MAX_RUBBER);
		if (ctx.resource("rubber") <= 0.0f || !p.isAlive()) {
			endShield(ctx);
			return;
		}
		MutationVisuals.ensure(p, "p17.inflate");
		p.resetFallDistance();
		reflectProjectiles(p);
		if (p.tickCount % 5 == 0) {
			ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 4, 0.7, 0.8, 0.7, 0.02);
		}
	}

	/** Every enemy projectile heading into the balloon bounces back -- at its shooter when there is one. */
	public static int reflectProjectiles(ServerPlayer p) {
		int bounced = 0;
		Vec3 centre = p.position().add(0, p.getBbHeight() * 0.5, 0);
		for (Projectile pr : p.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5),
				pr -> pr.isAlive() && pr.getOwner() != p)) {
			Vec3 v = pr.getDeltaMovement();
			Vec3 to = centre.subtract(pr.position());
			if (v.lengthSqr() < 1.0e-3 || v.dot(to) <= 0.0) {
				continue;
			}
			Entity owner = pr.getOwner();
			double speed = Math.max(0.9, v.length() * 1.15);
			Vec3 out;
			if (owner instanceof LivingEntity le && le.isAlive() && le.distanceToSqr(p) < 48.0 * 48.0) {
				out = le.getEyePosition().subtract(pr.position()).normalize().scale(speed);
			} else {
				out = v.normalize().reverse().scale(speed);
			}
			pr.setDeltaMovement(out);
			pr.setOwner(p);
			pr.hasImpulse = true;
			pr.hurtMarked = true;
			bounced++;
			AbilityHelpers.level(p).sendParticles(ParticleTypes.ITEM_SLIME, pr.getX(), pr.getY(), pr.getZ(), 8, 0.15, 0.15, 0.15, 0.1);
		}
		if (bounced > 0) {
			AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP_SMALL, 1.2f, 1.6f);
		}
		return bounced;
	}

	private static void endShield(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("shielding") < 0.5f) {
			return;
		}
		ctx.setResource("shielding", 0, 1);
		clearShieldModifiers(p);
		MutationVisuals.stopIf(p, "p17.inflate");
		AbilityHelpers.sound(p, SoundEvents.SLIME_SQUISH_SMALL, 1.0f, 1.3f);
		ctx.triggerCooldown();
	}

	private static void clearShieldModifiers(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, SHIELD_SPEED);
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, SHIELD_KB);
	}

	// ---- N: Parachute Glide ---------------------------------------------------------------------

	private static void startGlide(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (p.onGround()) {
			AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 0.85, p.getDeltaMovement().z));
		}
		ctx.setResource("glide_ticks", GLIDE_TICKS, GLIDE_TICKS);
		MutationVisuals.play(p, "p17.glide");
		ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, p.getX(), p.getY() + 1.0, p.getZ(), 20, 0.8, 0.3, 0.8, 0.05);
		AbilityHelpers.sound(p, SoundEvents.SLIME_JUMP, 1.0f, 0.7f);
	}

	private static void glideTick(AbilityContext ctx) {
		float left = ctx.resource("glide_ticks");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("glide_ticks", left - 1, GLIDE_TICKS);
		boolean landed = (p.onGround() || p.isInWater()) && left < GLIDE_TICKS - 8;
		if (left - 1 <= 0.5f || landed || !p.isAlive() || p.getAbilities().flying) {
			endGlide(ctx);
			return;
		}
		p.resetFallDistance();
		// the server-side half: a hidden Slow Falling so the canopy holds even before the client's glide
		// steering (RevampClientE) kicks in
		if (!p.hasEffect(MobEffects.SLOW_FALLING) || p.getEffect(MobEffects.SLOW_FALLING).getDuration() < 3) {
			p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 8, 0, false, false, false));
		}
		MutationVisuals.ensure(p, "p17.glide");
		if (p.tickCount % 3 == 0) {
			ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.6, 0.05, 0.6, 0.0);
		}
	}

	private static void endGlide(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ctx.setResource("glide_ticks", 0, GLIDE_TICKS);
		MutationVisuals.stopIf(p, "p17.glide");
		p.resetFallDistance();
		ctx.triggerCooldown();
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
		MutationVisuals.play(p, f == Form.INFLATED ? "flex" : f == Form.COMPRESSION ? "p17.compress" : "p17.form_stretch");
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
			case INFLATED -> -0.3; // a big beach ball is slow
			case COMPRESSION -> 0.25;
		};
		double melee = f == Form.ELASTIC ? 5.0 : 0.0; // +5 melee in the default stretchy shape
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
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, FORM_MELEE, melee, AttributeModifier.Operation.ADD_VALUE);

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
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, FORM_MELEE);
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
				PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, FORM_MELEE);
				clearShieldModifiers(player);
				clearForm(player);
				Power power = Powers.byKey(KEY);
				if (power != null && ExperimentalPowers.owns(player, power)) {
					// still owned while forget() runs its teardown: drop the transient states too
					ExperimentalPowers.setResource(player, power, "shielding", 0, 1);
					ExperimentalPowers.setResource(player, power, "glide_ticks", 0, GLIDE_TICKS);
					ExperimentalPowers.setResource(player, power, "arm_ticks", 0, 200.0f);
				}
				MutationVisuals.stopIf(player, "p17.inflate");
				MutationVisuals.stopIf(player, "p17.glide");
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
			// the Rubber reserve refills while the shield is down
			seedRubber(player);
			Power power = Powers.byKey(KEY);
			if (!shielding(player)) {
				clearShieldModifiers(player);
				if (ExperimentalPowers.getResource(player, power, "rubber") < MAX_RUBBER) {
					ExperimentalPowers.addResource(player, power, "rubber", RUBBER_REGEN, MAX_RUBBER);
				}
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
