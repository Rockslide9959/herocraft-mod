package com.projecthero.mod.hero.power.p13;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.hero.revamp.batcha.BatchA;
import com.projecthero.mod.hero.visual.MutationVisuals;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * Power 13 — Super Durability (v0.13.22 revamp: <b>blocked damage fills Impact</b>).
 *
 * <p>All of this power's mitigation is applied as explicit multiplicative factors in
 * {@link com.projecthero.mod.hero.power.HeroDamageRules} (passive 40% off, Block 60% more off the front, Tank
 * Mode 30% more, Unbreakable 100%, projectile immunity while guarding). Every point of damage those layers stop is
 * banked as <b>Impact</b> (0..115, 3 per point blocked), which Impact Release (H) dumps as a ground slam.
 *
 * <p>R Heavy Strike, G Shoulder Charge, X Block (hold), Z Unbreakable, V Deflection (hold: projectiles are
 * reflected back at whoever shot them), C Tank Mode, H Impact Release, N Taunt. Block and Deflection share the
 * {@code guard} stamina bar (0..575), which refills while neither is held.
 */
public final class SuperDurabilityHandlers {
	public static final String KEY = "power_13_super_durability";
	private static final net.minecraft.resources.ResourceLocation TANK_KB = com.projecthero.mod.ProjectHeroMod.id("tank_kb");
	private static final net.minecraft.resources.ResourceLocation TANK_SPD = com.projecthero.mod.ProjectHeroMod.id("tank_slow");
	private static final net.minecraft.resources.ResourceLocation BLOCK_KB = com.projecthero.mod.ProjectHeroMod.id("block_kb");
	private static final net.minecraft.resources.ResourceLocation UNBREAK_KB = com.projecthero.mod.ProjectHeroMod.id("unbreakable_kb");
	private static final net.minecraft.resources.ResourceLocation PASSIVE_KB = com.projecthero.mod.ProjectHeroMod.id("durability_passive_kb");
	private static final net.minecraft.resources.ResourceLocation PASSIVE_HP = com.projecthero.mod.ProjectHeroMod.id("durability_passive_health");

	public static final String GUARD = "guard";
	public static final float GUARD_MAX = 575.0f;
	private static final float GUARD_DRAIN = 1.0f;
	private static final float GUARD_REGEN = 2.3f;
	public static final String IMPACT = "impact";
	public static final float MAX_IMPACT = 115f;
	private static final int UNBREAKABLE_TICKS = 300;

	private SuperDurabilityHandlers() {
	}

	private static float res(ServerPlayer p, String name) {
		return BatchA.res(p, KEY, name);
	}

	private static void set(ServerPlayer p, String name, float v) {
		BatchA.set(p, KEY, name, v, 1e12f);
	}

	/** Banks {@code blocked} points of prevented damage as Impact (called from HeroDamageRules). */
	public static void gainImpact(ServerPlayer p, float blocked) {
		if (blocked <= 0f || !ExperimentalPowers.owns(p, KEY)) {
			return;
		}
		BatchA.set(p, KEY, IMPACT, Math.min(MAX_IMPACT, res(p, IMPACT) + blocked * 3f), MAX_IMPACT);
	}

	public static boolean tankMode(ServerPlayer p) {
		return ExperimentalPowers.state(p).activeToggles.contains(KEY + "/tank_mode");
	}

	public static boolean unbreakable(ServerPlayer p) {
		return res(p, "unbreakable_until") > p.level().getGameTime();
	}

	public static void register() {
		// R -- Heavy Strike: a reinforced blow that staggers.
		AbilityHandlers.register(KEY, "heavy_strike", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 4.5);
			if (t == null) {
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position().add(BatchA.flatLook(p).scale(1.2)), 1.8)) {
					t = e;
					break;
				}
			}
			BatchA.play(p, KEY, "haymaker", 16);
			if (t != null) {
				AbilityHelpers.hurtBurst(p, t, 12.0f);
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
				AbilityHelpers.applyControl(t, MobEffects.MOVEMENT_SLOWDOWN, 40, 3);
				ctx.level().sendParticles(ParticleTypes.CRIT, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 14, 0.3, 0.3, 0.3, 0.3);
				ctx.level().sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 1, 0, 0, 0, 0);
			}
			AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.5f, 0.7f);
			AbilityHelpers.sound(p, SoundEvents.PLAYER_ATTACK_CRIT, 1.0f, 0.5f);
			ctx.triggerCooldown();
		}));

		// G -- Shoulder Charge: barrel forward through anything in the way.
		AbilityHandlers.register(KEY, "shoulder_charge", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			AbilityHelpers.launchSelf(p, p.getLookAngle().scale(1.8).add(0, 0.1, 0));
			set(p, "charging", 12);
			BatchA.play(p, KEY, "p13.shoulder", 14);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 0.7f, 1.2f);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			int t = (int) res(p, "charging");
			if (t <= 0) {
				return;
			}
			set(p, "charging", t - 1);
			ctx.level().sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 3, 0.2, 0.1, 0.2, 0.02);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 1.7)) {
				if (AbilityHelpers.hurt(p, e, 10.0f)) {
					AbilityHelpers.knockbackFrom(e, p.position(), 1.5);
					AbilityHelpers.push(e, new Vec3(0, 0.3, 0));
					ctx.level().sendParticles(ParticleTypes.CRIT, e.getX(), e.getY() + 1, e.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
				}
			}
		}));

		// X -- Block (hold): 60% more off anything from the front, total projectile immunity; drains the guard bar.
		AbilityHandlers.register(KEY, "block", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource(GUARD) <= 0.0f) {
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				boolean alreadyBlocking = ctx.resource("blocking") > 0.5f;
				PowerToggles.modifier(ctx.player(), Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB, 1.0,
						AttributeModifier.Operation.ADD_VALUE);
				set(ctx.player(), "blocking", 1);
				MutationVisuals.play(ctx.player(), "guard");
				if (!alreadyBlocking) {
					AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				stopBlocking(ctx.player());
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "blocking") > 0.5f) {
					BatchA.add(p, KEY, GUARD, -GUARD_DRAIN, GUARD_MAX);
					MutationVisuals.ensure(p, "guard");
					pushAway(ctx);
					if (res(p, GUARD) <= 0.0f) {
						stopBlocking(p);
						ctx.actionBar("message.projecthero.durability.guard_spent");
					}
				}
			}
		});

		// Z -- Unbreakable: 15 seconds of total damage negation (see HeroDamageRules); everything stopped is Impact.
		AbilityHandlers.register(KEY, "unbreakable", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, UNBREAKABLE_TICKS, 4, false, true, true));
			PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB, 1.0, AttributeModifier.Operation.ADD_VALUE);
			set(p, "unbreakable_until", p.level().getGameTime() + UNBREAKABLE_TICKS);
			set(p, "unbreakable_left", UNBREAKABLE_TICKS);
			AbilityHelpers.burst(ctx.level(), p.position().add(0, 1, 0), ParticleTypes.ENCHANTED_HIT, 30, 0.5);
			AbilityHelpers.burst(ctx.level(), p.position().add(0, 1, 0), ParticleTypes.WAX_ON, 20, 0.5);
			BatchA.play(p, KEY, "power_up", 20);
			AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.8f, 1.4f);
			AbilityHelpers.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
			ctx.triggerCooldown();
		}, ctx -> {
			ServerPlayer p = ctx.player();
			BatchA.countDown(p, KEY, "unbreakable_left");
			float until = res(p, "unbreakable_until");
			if (until > 0 && until <= p.level().getGameTime()) {
				PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB);
				set(p, "unbreakable_until", 0);
				set(p, "unbreakable_left", 0);
			}
		}));

		// V -- Deflection (hold): anything fired at you is sent straight back at whoever fired it.
		AbilityHandlers.register(KEY, "projectile_deflection", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource(GUARD) <= 0.0f) {
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				set(ctx.player(), "deflecting", 1);
				MutationVisuals.play(ctx.player(), "shield_brace");
				AbilityHelpers.sound(ctx.player(), SoundEvents.SHIELD_BLOCK, 1.0f, 0.8f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				set(ctx.player(), "deflecting", 0);
				MutationVisuals.stopIf(ctx.player(), "shield_brace");
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (res(p, "deflecting") <= 0.5f) {
					return;
				}
				BatchA.add(p, KEY, GUARD, -GUARD_DRAIN, GUARD_MAX);
				if (res(p, GUARD) <= 0.0f) {
					set(p, "deflecting", 0);
					MutationVisuals.stopIf(p, "shield_brace");
					ctx.actionBar("message.projecthero.durability.guard_spent");
					return;
				}
				MutationVisuals.ensure(p, "shield_brace");
				reflect(ctx);
			}
		});

		// C -- Tank Mode: a braced stance -- another 30% off and near-total knockback resistance, 30% slower.
		AbilityHandlers.register(KEY, "tank_mode", Handlers.toggle(ctx -> {
			tankOn(ctx);
			BatchA.play(ctx.player(), KEY, "flex", 18);
			AbilityHelpers.sound(ctx.player(), SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0f, 0.7f);
		}, SuperDurabilityHandlers::tankOff, ctx -> {
			tankOn(ctx);
			AbilityHelpers.modeAura(ctx.player(), new net.minecraft.core.particles.BlockParticleOption(
					ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DEEPSLATE.defaultBlockState()), 4);
		}));

		// H -- Impact Release: everything you have absorbed, slammed back out through the ground.
		AbilityHandlers.register(KEY, "impact_release", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float impact = res(p, IMPACT);
			if (impact < 20f) {
				ctx.actionBar("message.projecthero.durability.no_impact", 20);
				return;
			}
			set(p, IMPACT, 0);
			float frac = impact / MAX_IMPACT;
			double radius = 4.0 + 4.0 * frac;
			float dmg = 8f + impact * 0.3f;
			ServerLevel level = ctx.level();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), radius)) {
				AbilityHelpers.hurtBurst(p, e, dmg);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.2 + frac);
				AbilityHelpers.push(e, new Vec3(0, 0.45 + 0.3 * frac, 0));
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 40, 2);
			}
			BatchA.play(p, KEY, "ground_pound", 18);
			level.sendParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 0.1, p.getZ(), 2 + (int) (frac * 3), radius * 0.3, 0.1,
					radius * 0.3, 0);
			BatchA.debrisRing(level, p.position(), radius * 0.4, 16);
			BatchA.debrisRing(level, p.position(), radius * 0.8, 26);
			BatchA.ring(level, p.position().add(0, 0.2, 0), 0.6, ParticleTypes.CRIT, 30, 0.5 + frac * 0.5);
			AbilityHelpers.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 1.0f, 0.7f);
			AbilityHelpers.sound(p, SoundEvents.ANVIL_LAND, 0.8f, 0.5f);
			ctx.triggerCooldown();
		}));

		// N -- Taunt: every mob within 16 blocks turns on you (and you brace for it).
		AbilityHandlers.register(KEY, "taunt", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			int drawn = 0;
			for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16.0), m -> m.isAlive()
					&& m.distanceToSqr(p) <= 256 && !BatchA.isAlly(p, m)
					&& !(m instanceof TamableAnimal t && t.isTame()))) {
				m.setTarget(p);
				if (m instanceof NeutralMob neutral) {
					neutral.setPersistentAngerTarget(p.getUUID());
					neutral.startPersistentAngerTimer();
				}
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 2, 0.2, 0.1, 0.2, 0);
				drawn++;
			}
			p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 0, false, true, true));
			set(p, "taunt_drawn", drawn);
			BatchA.play(p, KEY, "p13.taunt", 20);
			BatchA.ring(level, p.position().add(0, 0.2, 0), 1.0, ParticleTypes.CRIT, 24, 0.4);
			AbilityHelpers.sound(p, SoundEvents.RAVAGER_ROAR, 1.0f, 0.8f);
			AbilityHelpers.sound(p, SoundEvents.ANVIL_PLACE, 0.6f, 0.6f);
			ctx.triggerCooldown();
		}));

		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				// 85% knockback resistance and a 14-heart base health pool.
				PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB, 0.85,
						AttributeModifier.Operation.ADD_VALUE);
				PowerToggles.modifier(player, Attributes.MAX_HEALTH, PASSIVE_HP, 8.0,
						AttributeModifier.Operation.ADD_VALUE);
				var s = ExperimentalPowers.state(player);
				if (!s.resources.containsKey(KEY + "/" + GUARD)) {
					ExperimentalPowers.setResource(player, Powers.byKey(KEY), GUARD, GUARD_MAX, GUARD_MAX);
				}
			} else {
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, PASSIVE_KB);
				PowerToggles.clearModifier(player, Attributes.MAX_HEALTH, PASSIVE_HP);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB);
				PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, UNBREAK_KB);
				set(player, "blocking", 0);
				set(player, "deflecting", 0);
			}
		});

		// Refill the shared guard bar while neither block nor deflection is being held; Impact fades very slowly.
		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			float guard = res(player, GUARD);
			boolean guarding = res(player, "blocking") > 0.5f || res(player, "deflecting") > 0.5f;
			if (!guarding && guard < GUARD_MAX) {
				BatchA.set(player, KEY, GUARD, guard + GUARD_REGEN, GUARD_MAX);
			}
			float impact = res(player, IMPACT);
			if (impact > 0f && !guarding && player.tickCount % 4 == 0) {
				set(player, IMPACT, Math.max(0f, impact - 0.2f));
			}
			if (impact > MAX_IMPACT * 0.75f && player.tickCount % 6 == 0 && player.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1, player.getZ(), 2, 0.3, 0.05, 0.3, 0.02);
			}
		});
	}

	/** Block: shove nearby projectiles away from you, with the block feedback. */
	private static void pushAway(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		for (Projectile proj : ctx.level().getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.0),
				pr -> pr.getOwner() != p)) {
			Vec3 away = proj.position().subtract(p.position()).normalize().scale(1.2);
			proj.setDeltaMovement(away);
			proj.hurtMarked = true;
		}
		if (p.tickCount % 6 == 0) {
			AbilityHelpers.burst(ctx.level(), p.position().add(0, 1, 0), ParticleTypes.CRIT, 6, 0.5);
		}
	}

	/**
	 * Deflection: every incoming projectile within 3.5 blocks is turned round and fired back at its shooter
	 * (a little faster than it came), and becomes yours, so the hit is credited to you.
	 */
	private static void reflect(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		for (Projectile proj : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.5),
				pr -> pr.getOwner() != p && pr.isAlive())) {
			Entity shooter = proj.getOwner();
			double speed = Math.max(1.6, proj.getDeltaMovement().length() * 1.2);
			Vec3 dir;
			if (shooter instanceof LivingEntity le && le.isAlive() && le.level() == level && le.distanceToSqr(p) < 64 * 64) {
				dir = le.getEyePosition().subtract(proj.position());
			} else {
				dir = proj.getDeltaMovement().reverse();
			}
			if (dir.lengthSqr() < 1.0e-4) {
				dir = p.getLookAngle();
			}
			proj.setOwner(p);
			proj.setDeltaMovement(dir.normalize().scale(speed));
			proj.hurtMarked = true;
			proj.hasImpulse = true;
			if (proj instanceof AbstractArrow arrow) {
				arrow.setCritArrow(true);
			}
			gainImpact(p, 1.0f);
			level.sendParticles(ParticleTypes.ENCHANTED_HIT, proj.getX(), proj.getY(), proj.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
			level.playSound(null, proj.blockPosition(), SoundEvents.SHIELD_BLOCK, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.4f);
		}
		if (p.tickCount % 6 == 0) {
			AbilityHelpers.burst(level, p.position().add(0, 1, 0), ParticleTypes.CRIT, 6, 0.5);
		}
	}

	private static void stopBlocking(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, BLOCK_KB);
		set(p, "blocking", 0);
		MutationVisuals.stopIf(p, "guard");
	}

	private static void tankOn(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.modifier(p, Attributes.KNOCKBACK_RESISTANCE, TANK_KB, 0.8, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.MOVEMENT_SPEED, TANK_SPD, -0.3, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	private static void tankOff(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		PowerToggles.clearModifier(p, Attributes.KNOCKBACK_RESISTANCE, TANK_KB);
		PowerToggles.clearModifier(p, Attributes.MOVEMENT_SPEED, TANK_SPD);
	}
}
