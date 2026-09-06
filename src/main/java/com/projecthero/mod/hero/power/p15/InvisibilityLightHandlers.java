package com.projecthero.mod.hero.power.p15;

import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.Vec3;

/** Power 15 — Invisibility / Light Manipulation. */
public final class InvisibilityLightHandlers {
	private static final String KEY = "power_15_invisibility_light_manipulation";

	private InvisibilityLightHandlers() {
	}

	public static void register() {
		AbilityHandlers.register(KEY, "light_blast", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 22.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.END_ROD, 3.0);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, 22.0), ParticleTypes.GLOW, 2.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 10.0f);
				AbilityHelpers.applyControl(t, MobEffects.GLOWING, 100, 0);
			}
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.8f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "flash", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 8.0)) {
				AbilityHelpers.hurt(p, e, 8.0f);
				AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 200, 0);
				AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 200, 1);
				if (e instanceof Mob mob) {
					mob.setTarget(null);
				}
			}
			ctx.level().sendParticles(ParticleTypes.FLASH, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
			ctx.level().sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 60, 4, 1, 4, 0.2);
			AbilityHelpers.sound(p, SoundEvents.FIREWORK_ROCKET_BLAST, 1.0f, 1.5f);
			ctx.triggerCooldown();
		}));

		// Sparkling Flight (id kept as mirage_dash): HOLD to fly wherever you look. Your body dissolves
		// into light while it lasts. Drains the sparkle meter (~25 s), which refills on its own; 3 s
		// cooldown once you land.
		AbilityHandlers.register(KEY, "mirage_dash", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!ctx.cooldownReady()) {
					return;
				}
				if (!ExperimentalPowers.state(p).resources.containsKey(KEY + "/sparkle")) {
					ctx.setResource("sparkle", MAX_SPARKLE, MAX_SPARKLE);
				}
				if (ctx.resource("sparkle") < 10.0f) {
					ctx.actionBar("message.projecthero.light.sparkle_low");
					return;
				}
				if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p)) {
					return;
				}
				ctx.setResource("sparkling", 1, 1);
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				p.onUpdateAbilities();
				AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8f, 1.7f);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				endSparkle(ctx, true);
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("sparkling") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.getAbilities().instabuild || com.projecthero.mod.power.ThorPowers.isFlying(p)) {
					endSparkle(ctx, false);
					return;
				}
				p.getAbilities().mayfly = true;
				p.getAbilities().flying = true;
				AbilityHelpers.addImpulse(p, p.getLookAngle().scale(0.42));
				p.resetFallDistance();
				// the body scatters into light particles
				p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6, 0, false, false, false));
				ServerLevel level = ctx.level();
				double h = Math.max(1.0, p.getBbHeight());
				for (int s = 0; s < 4; s++) {
					double yy = p.getY() + h * (s / 3.0);
					level.sendParticles(ParticleTypes.END_ROD, p.getX(), yy, p.getZ(), 2, 0.22, 0.12, 0.22, 0.004);
					level.sendParticles(ParticleTypes.GLOW, p.getX(), yy, p.getZ(), 1, 0.2, 0.12, 0.2, 0.0);
				}
				ctx.addResource("sparkle", -SPARKLE_DRAIN, MAX_SPARKLE);
				if (ctx.resource("sparkle") <= 0.0f) {
					endSparkle(ctx, true);
					ctx.actionBar("message.projecthero.light.sparkle_out");
				}
			}
		});

		// Holy Light (id kept as perfect_cloak): a single sustained pillar of blinding light, like Laser
		// Vision's Maximum Output but one thick beam of light instead of twin heat beams.
		AbilityHandlers.register(KEY, "perfect_cloak", Handlers.instantTicking(ctx -> {
			ctx.setResource("holy_ticks", 60, 60);
			AbilityHelpers.sound(ctx.player(), SoundEvents.BEACON_ACTIVATE, 1.2f, 1.5f);
			ctx.triggerCooldown();
		}, ctx -> {
			int t = (int) ctx.resource("holy_ticks");
			if (t <= 0) {
				return;
			}
			ctx.setResource("holy_ticks", t - 1, 60);
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			Vec3 start = p.getEyePosition();
			LivingEntity target = AbilityHelpers.raycastEntity(p, 40.0);
			Vec3 end;
			if (target != null) {
				end = target.position().add(0, target.getBbHeight() * 0.5, 0);
				AbilityHelpers.hurt(p, target, 12.0f);
			} else {
				var bhr = AbilityHelpers.raycastBlock(p, 40.0);
				end = bhr.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
						? bhr.getLocation() : start.add(p.getLookAngle().scale(40.0));
			}
			Vec3 from = start.add(p.getLookAngle().scale(0.3));
			AbilityHelpers.line(level, from, end, ParticleTypes.END_ROD, 4.0);
			AbilityHelpers.line(level, from, end, ParticleTypes.GLOW, 2.5);
			if (t % 10 == 0) {
				Vec3 impact = AbilityHelpers.aimPoint(p, 40.0);
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, impact, 5.0)) {
					AbilityHelpers.hurt(p, e, 24.0f);
					AbilityHelpers.applyControl(e, MobEffects.GLOWING, 60, 0);
					AbilityHelpers.applyControl(e, MobEffects.BLINDNESS, 40, 0);
				}
				level.sendParticles(ParticleTypes.FLASH, impact.x, impact.y, impact.z, 1, 0, 0, 0, 0);
				level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z, 30, 1.0, 1.0, 1.0, 0.05);
			}
		}));

		AbilityHandlers.register(KEY, "decoy", Handlers.instantTicking(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			ArmorStand decoy = new ArmorStand(EntityType.ARMOR_STAND, level);
			decoy.setPos(p.getX(), p.getY(), p.getZ());
			decoy.setCustomName(p.getName());
			decoy.setCustomNameVisible(false);
			decoy.setInvulnerable(false);
			decoy.setNoGravity(false);
			level.addFreshEntity(decoy);
			ctx.setResource("decoy_id", decoy.getId(), 1_000_000);
			ctx.setResource("decoy_until", p.level().getGameTime() + 360, 1e12f);
			for (Monster m : level.getEntitiesOfClass(Monster.class, p.getBoundingBox().inflate(16.0))) {
				m.setTarget(decoy);
			}
			p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
			AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.3f);
			ctx.triggerCooldown();
		}, ctx -> {
			int id = (int) ctx.resource("decoy_id");
			if (id == 0) {
				return;
			}
			if (ctx.resource("decoy_until") <= ctx.player().level().getGameTime()
					|| !(ctx.level().getEntity(id) instanceof ArmorStand as) || !as.isAlive()) {
				if (ctx.level().getEntity(id) instanceof ArmorStand as2) {
					as2.discard();
				}
				ctx.setResource("decoy_id", 0, 1_000_000);
			}
		}));

		AbilityHandlers.register(KEY, "cloaking_toggle", Handlers.toggle(
				ctx -> ctx.player().addEffect(inf()),
				ctx -> ctx.player().removeEffect(MobEffects.INVISIBILITY),
				ctx -> {
					ServerPlayer p = ctx.player();
					// reveal briefly on attack / heavy damage: handled by re-applying only if not "revealed"
					if (ctx.resource("revealed_until") > p.level().getGameTime()) {
						p.removeEffect(MobEffects.INVISIBILITY);
					} else {
						MobEffectInstance cur = p.getEffect(MobEffects.INVISIBILITY);
						if (cur == null || !cur.isInfiniteDuration()) {
							p.addEffect(inf());
						}
					}
				}));

		net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!world.isClientSide() && player instanceof ServerPlayer sp) {
				var power = com.projecthero.mod.hero.Powers.byKey(KEY);
				if (power != null && com.projecthero.mod.hero.ExperimentalPowers.owns(sp, power)
						&& com.projecthero.mod.hero.ExperimentalPowers.isToggled(sp, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6))) {
					com.projecthero.mod.hero.ExperimentalPowers.setResource(sp, power, "revealed_until",
							sp.level().getGameTime() + 40, 1e12f);
				}
			}
			return net.minecraft.world.InteractionResult.PASS;
		});

		// Clear a stuck Sparkling Flight on join / respawn / power switch.
		com.projecthero.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			var power = com.projecthero.mod.hero.Powers.byKey(KEY);
			if (power != null) {
				com.projecthero.mod.hero.ExperimentalPowers.setResource(player, power, "sparkling", 0, 1);
			}
			if (!player.getAbilities().instabuild && !com.projecthero.mod.power.ThorPowers.isFlying(player)
					&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(player)) {
				player.getAbilities().mayfly = false;
				player.getAbilities().flying = false;
				player.onUpdateAbilities();
			}
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var blind = player.getEffect(MobEffects.BLINDNESS);
			if (blind != null && blind.getDuration() > 40) {
				player.removeEffect(MobEffects.BLINDNESS);
			}
			var power = com.projecthero.mod.hero.Powers.byKey(KEY);
			if (power == null) {
				return;
			}
			boolean sparkling = com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "sparkling") > 0.5f;
			if (!sparkling && com.projecthero.mod.hero.ExperimentalPowers.state(player).resources.containsKey(KEY + "/sparkle")
					&& com.projecthero.mod.hero.ExperimentalPowers.getResource(player, power, "sparkle") < MAX_SPARKLE) {
				com.projecthero.mod.hero.ExperimentalPowers.addResource(player, power, "sparkle", SPARKLE_REGEN, MAX_SPARKLE);
			}
		});
	}

	/** End Sparkling Flight: drop flight, restore the body, cushion the landing, start the cooldown. */
	private static void endSparkle(AbilityContext ctx, boolean startCooldown) {
		ServerPlayer p = ctx.player();
		if (ctx.resource("sparkling") < 0.5f) {
			return;
		}
		ctx.setResource("sparkling", 0, 1);
		if (!p.getAbilities().instabuild && !com.projecthero.mod.power.ThorPowers.isFlying(p)
				&& !com.projecthero.mod.hero.power.HeroFlight.isFlying(p)) {
			p.getAbilities().mayfly = false;
			p.getAbilities().flying = false;
			p.onUpdateAbilities();
		}
		if (!cloaked(p)) {
			p.removeEffect(MobEffects.INVISIBILITY);
		}
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false, false));
		AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
		if (startCooldown) {
			ctx.triggerCooldown();
		}
	}

	/** True while the cloaking toggle is on -- read from the synced attachment (server + client). */
	public static boolean cloaked(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/cloaking_toggle");
	}

	/** True while Sparkling Flight is channelling -- read from the synced attachment (server + client). */
	public static boolean sparkleFlying(net.minecraft.world.entity.player.Player p) {
		var st = p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY)
				&& st.resources.getOrDefault(KEY + "/sparkling", 0.0f) > 0.5f;
	}

	/** The player's whole appearance (armour included) is hidden while cloaked or sparkle-flying. */
	public static boolean hideArmor(net.minecraft.world.entity.player.Player p) {
		return cloaked(p) || sparkleFlying(p);
	}

	private static final float MAX_SPARKLE = 100.0f;
	private static final float SPARKLE_DRAIN = 100.0f / (25 * 20); // ~25 s of flight from full
	private static final float SPARKLE_REGEN = 100.0f / (18 * 20); // refills in ~18 s

	private static MobEffectInstance inf() {
		return new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
	}
}
