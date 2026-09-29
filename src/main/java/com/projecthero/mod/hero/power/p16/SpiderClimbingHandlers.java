package com.projecthero.mod.hero.power.p16;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.data.ExperimentalState;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.visual.MutationVisuals;
import com.projecthero.mod.spider.SpiderClimb;
import com.projecthero.mod.spider.SpiderClimbActions;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Power 16 -- Spider Climbing / Adhesion. The early arachnid power: real surface adhesion, but the
 * plain version of it. {@link com.projecthero.mod.spider.SpiderMan Spider-Man} is what this evolves
 * into, and the difference is deliberate -- Adhesion climbs more slowly, holds on less stubbornly and
 * has none of the web kit.
 *
 * <p>The adhesion itself is no longer implemented here. It moved to {@link SpiderClimb}, which both
 * powers share, so a player who has had Spider Adhesion since before the overhaul simply gets the
 * better climbing on their existing save with nothing to migrate: the same two toggles, read from the
 * same attachment, now drive a real engine instead of a {@code onClimbable} override.
 *
 * <p>v0.13.22 revamp (batch E): the lead-in to Spider-Man. Every move has a body animation, damage is up
 * ~20% and cooldowns down ~15%, Pounce now strikes whatever it lands on, and two new utility moves:
 * H <b>Spider-Sense Dodge</b> (for ~1 s the next attack is side-stepped outright) and N <b>Venom Bite</b>
 * (a poisoning, slowing melee bite). Owners also see a faint red threat glow over nearby hostiles
 * (client-side, {@code RevampClientE}).
 */
public final class SpiderClimbingHandlers {
	private static final String KEY = "power_16_spider_climbing_adhesion";

	static final float STRIKE_DAMAGE = 8.5f;
	static final float POUNCE_DAMAGE = 7.0f;
	static final float BITE_DAMAGE = 7.0f;
	/** Spider-Sense window, in ticks (the next hit inside it is dodged). */
	public static final int SENSE_TICKS = 24;
	static final int RUSH_TICKS = 700;
	private static final int POUNCE_WINDOW = 30;

	private SpiderClimbingHandlers() {
	}

	/**
	 * True when the player should be sticking to surfaces right now. Kept as the power's own public
	 * entry point (it was here before the overhaul), but the actual answer comes from the shared
	 * engine so the two powers can never disagree about it.
	 */
	public static boolean wallClinging(Player p) {
		return SpiderClimb.profile(p) == SpiderClimb.Profile.ADHESION;
	}

	private static ExperimentalState st(Player p) {
		return p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
	}

	/** Whether {@code p} owns Spider Adhesion (synced -- safe on either side). */
	public static boolean owns(Player p) {
		ExperimentalState s = st(p);
		return s != null && s.ownedPowers.contains(KEY);
	}

	private static float res(Player p, String name) {
		ExperimentalState s = st(p);
		return s == null ? 0.0f : s.resources.getOrDefault(KEY + "/" + name, 0.0f);
	}

	/** Spider-Sense is primed: the next attack inside the window is dodged. */
	public static boolean senseActive(Player p) {
		return owns(p) && res(p, "sense_ticks") > 0.5f;
	}

	/** Predator Rush is running. */
	public static boolean rushing(Player p) {
		return owns(p) && res(p, "rush_ticks") > 0.5f;
	}

	/** Either grip toggle is on (the web-palm overlay). */
	public static boolean gripping(Player p) {
		ExperimentalState s = st(p);
		return s != null && s.ownedPowers.contains(KEY)
				&& (s.activeToggles.contains(KEY + "/wall_grip") || s.activeToggles.contains(KEY + "/adhesion_mode"));
	}

	public static void register() {
		// R -- Adhesive Strike: a sticky palm strike that pins the target in place.
		AbilityHandlers.register(KEY, "adhesive_strike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			MutationVisuals.play(p, "p16.palm_strike");
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5);
			if (t != null) {
				AbilityHelpers.hurt(p, t, STRIKE_DAMAGE);
				// pinned in place for 7 seconds
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 140, 200);
				AbilityHelpers.applyControl(t, MobEffects.WEAKNESS, 140, 2);
				t.setDeltaMovement(Vec3.ZERO);
				t.hurtMarked = true;
				ctx.level().sendParticles(ParticleTypes.ITEM_SLIME, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(),
						14, 0.3, 0.4, 0.3, 0.05);
				AbilityHelpers.line(ctx.level(), AbilityHelpers.handPosition(p), t.position().add(0, t.getBbHeight() * 0.5, 0),
						ParticleTypes.WHITE_ASH, 4.0);
			}
			AbilityHelpers.burst(ctx.level(), p.getEyePosition().add(p.getLookAngle().scale(2)), ParticleTypes.POOF, 8, 0.2);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_HURT, 0.8f, 1.4f);
			ctx.triggerCooldown();
		}));

		// G -- Pounce: a predatory leap; the first enemy it lands on (within 1.5 s) takes the hit.
		AbilityHandlers.register(KEY, "pounce", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 dir = p.getLookAngle();
			AbilityHelpers.launchSelf(p, new Vec3(dir.x * 1.9, Math.max(0.45, dir.y * 1.6 + 0.35), dir.z * 1.9));
			MutationVisuals.play(p, "p16.pounce");
			AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CRIT, 12, 0.3);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_AMBIENT, 0.7f, 1.5f);
			ctx.setResource("pounce_ticks", POUNCE_WINDOW, POUNCE_WINDOW);
			ctx.triggerCooldown();
		}, SpiderClimbingHandlers::pounceTick));

		// Wall Leap is now the ability form of the same push-off the jump key performs while adhered,
		// so it launches along the real surface normal instead of simply backwards from the camera --
		// which used to send you into the wall whenever you were looking away from it.
		AbilityHandlers.register(KEY, "wall_leap", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (SpiderClimbActions.leap(p)) {
				MutationVisuals.play(p, "leap");
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 10, 0.3);
				ctx.triggerCooldown();
				return;
			}
			if (!p.onGround()) {
				Vec3 away = p.getLookAngle().reverse().scale(0.8).add(0, 0.9, 0);
				AbilityHelpers.launchSelf(p, away);
				MutationVisuals.play(p, "leap");
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.CLOUD, 10, 0.3);
				ctx.triggerCooldown();
			}
		}));

		// Z -- Predator Rush: 35 s of speed, haste and leaping, with the red eyes to show for it.
		AbilityHandlers.register(KEY, "predator_rush", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, RUSH_TICKS, 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, RUSH_TICKS, 1, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.JUMP, RUSH_TICKS, 2, false, true, true));
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, RUSH_TICKS, 0, false, true, true));
			ctx.setResource("rush_ticks", RUSH_TICKS, RUSH_TICKS);
			MutationVisuals.play(p, "p16.crouch_roar");
			ctx.level().sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.8f, 0.05f, 0.05f), 1.2f),
					p.getX(), p.getY() + 1.0, p.getZ(), 30, 0.5, 0.6, 0.5, 0.02);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_AMBIENT, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}, ctx -> {
			float left = ctx.resource("rush_ticks");
			if (left > 0.5f) {
				ctx.setResource("rush_ticks", left - 1, RUSH_TICKS);
			}
		}));

		// Both toggles simply switch adhesion on; SpiderClimb does the rest. The tick is only feedback
		// plus the fall-distance reset, which has to happen on the server whichever side is moving.
		AbilityHandlers.register(KEY, "wall_grip", Handlers.toggle(
				ctx -> setGripping(ctx, true), ctx -> setGripping(ctx, false), SpiderClimbingHandlers::clingTick));

		AbilityHandlers.register(KEY, "adhesion_mode", Handlers.toggle(
				ctx -> MutationVisuals.play(ctx.player(), "p16.cling"), Handlers.noop(),
				SpiderClimbingHandlers::clingTick));

		// H -- Spider-Sense Dodge: for about a second the next attack is side-stepped outright.
		AbilityHandlers.register(KEY, "spider_sense", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ctx.setResource("sense_ticks", SENSE_TICKS, SENSE_TICKS);
			MutationVisuals.play(p, "p16.sense");
			ctx.level().sendParticles(new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.15f, 0.15f), 0.8f),
					p.getX(), p.getEyeY() + 0.35, p.getZ(), 12, 0.35, 0.1, 0.35, 0.0);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 1.9f);
			ctx.triggerCooldown();
		}, ctx -> {
			float left = ctx.resource("sense_ticks");
			if (left > 0.5f) {
				ctx.setResource("sense_ticks", left - 1, SENSE_TICKS);
			}
		}));

		// N -- Venom Bite: a lunging melee bite -- poison and a heavy slow. No sneak variant (Sneak+N is combos).
		AbilityHandlers.register(KEY, "venom_bite", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 3.8);
			MutationVisuals.play(p, "p16.bite");
			if (t == null) {
				AbilityHelpers.sound(p, SoundEvents.SPIDER_STEP, 0.6f, 1.6f);
				return; // a whiff costs nothing
			}
			bite(p, t);
			ctx.triggerCooldown();
		}));

		// Spider-Sense: an attack that lands inside the window is dodged with a sidestep instead.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(SpiderClimbingHandlers::allowDamage);
	}

	/** Venom Bite's payload, public for the gametests. */
	public static void bite(ServerPlayer p, LivingEntity t) {
		AbilityHelpers.hurt(p, t, BITE_DAMAGE);
		AbilityHelpers.applyControl(t, MobEffects.POISON, 120, 1);
		AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1);
		Vec3 to = t.position().subtract(p.position());
		if (to.lengthSqr() > 1.0e-4) {
			AbilityHelpers.addImpulse(p, new Vec3(to.x, 0, to.z).normalize().scale(0.35));
		}
		AbilityHelpers.level(p).sendParticles(new DustParticleOptions(new org.joml.Vector3f(0.35f, 0.85f, 0.2f), 1.0f),
				t.getX(), t.getY() + t.getBbHeight() * 0.7, t.getZ(), 16, 0.25, 0.25, 0.25, 0.0);
		AbilityHelpers.level(p).sendParticles(ParticleTypes.DAMAGE_INDICATOR, t.getX(), t.getY() + t.getBbHeight() * 0.7,
				t.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
		AbilityHelpers.sound(p, SoundEvents.SPIDER_HURT, 1.0f, 0.6f);
	}

	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer p) || !senseActive(p)) {
			return true;
		}
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		if ((attacker == null && direct == null) || attacker == p
				|| source.is(DamageTypes.GENERIC_KILL) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return true; // environmental damage is not an "attack" the sense can read
		}
		Power power = Powers.byKey(KEY);
		ExperimentalPowers.setResource(p, power, "sense_ticks", 0, SENSE_TICKS);
		dodge(p, direct != null ? direct : attacker);
		return false;
	}

	/** The sidestep itself: perpendicular to the incoming attack, with a short burst of speed. */
	public static void dodge(ServerPlayer p, Entity from) {
		Vec3 in = from == null ? p.getLookAngle() : from.position().subtract(p.position());
		Vec3 flat = new Vec3(in.x, 0, in.z);
		if (flat.lengthSqr() < 1.0e-4) {
			flat = new Vec3(0, 0, 1);
		}
		flat = flat.normalize();
		Vec3 side = new Vec3(-flat.z, 0, flat.x);
		if (p.getRandom().nextBoolean()) {
			side = side.reverse();
		}
		AbilityHelpers.launchSelf(p, side.scale(0.95).add(flat.scale(-0.25)).add(0, 0.28, 0));
		p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false, true));
		MutationVisuals.play(p, "p16.dodge");
		AbilityHelpers.level(p).sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.9, p.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		AbilityHelpers.level(p).sendParticles(new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.1f, 0.1f), 1.0f),
				p.getX(), p.getEyeY() + 0.3, p.getZ(), 10, 0.3, 0.15, 0.3, 0.0);
		AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0f, 1.4f);
	}

	private static void pounceTick(AbilityContext ctx) {
		float left = ctx.resource("pounce_ticks");
		if (left <= 0.5f) {
			return;
		}
		ServerPlayer p = ctx.player();
		ctx.setResource("pounce_ticks", left - 1, POUNCE_WINDOW);
		p.resetFallDistance();
		if (p.onGround() && left < POUNCE_WINDOW - 6) {
			// landed with nothing under the claws
			ctx.setResource("pounce_ticks", 0, POUNCE_WINDOW);
			return;
		}
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(0, p.getBbHeight() * 0.5, 0), 1.7)) {
			if (com.projecthero.mod.squad.Squads.areAllies(p, e)) {
				continue;
			}
			AbilityHelpers.hurt(p, e, POUNCE_DAMAGE);
			AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 1);
			AbilityHelpers.knockbackFrom(e, p.position(), 0.6);
			ctx.level().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(),
					14, 0.3, 0.3, 0.3, 0.2);
			AbilityHelpers.sound(p, SoundEvents.SPIDER_HURT, 1.0f, 1.1f);
			ctx.setResource("pounce_ticks", 0, POUNCE_WINDOW);
			p.setDeltaMovement(p.getDeltaMovement().multiply(0.2, 0.5, 0.2));
			p.hurtMarked = true;
			return;
		}
	}

	private static void setGripping(AbilityContext ctx, boolean on) {
		ctx.setResource("gripping", on ? 1 : 0, 1);
		if (on) {
			MutationVisuals.play(ctx.player(), "p16.cling");
		}
	}

	private static void clingTick(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		if (!SpiderClimb.attached(p)) {
			return;
		}
		p.resetFallDistance();
		if (p.tickCount % 8 == 0) {
			ctx.level().sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 1, p.getZ(), 1, 0.2, 0.4, 0.2, 0.0);
		}
	}
}
