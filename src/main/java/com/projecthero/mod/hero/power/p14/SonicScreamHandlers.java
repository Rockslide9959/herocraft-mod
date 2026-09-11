package com.projecthero.mod.hero.power.p14;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hero.AbilityContext;
import com.projecthero.mod.hero.AbilityHandler;
import com.projecthero.mod.hero.AbilityHandlers;
import com.projecthero.mod.hero.ExperimentalPowers;
import com.projecthero.mod.hero.Power;
import com.projecthero.mod.hero.Powers;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.Handlers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Power 14 — Sonic Scream.
 *
 * <p>v0.10.14: enhanced hearing (client-side: nearby movers flash for the wielder alone) and a real
 * echolocation ping (Shift + right-click) sit alongside the kit; the old Echolocation toggle is now
 * <b>Enhanced Senses</b> (C) -- a draining-bar mode that blinds the wielder but sharpens every other
 * sense: +15 ability damage, +8 melee, longer ability range, and blindness for anything that gets
 * within 5 blocks.
 */
public final class SonicScreamHandlers {
	private static final String KEY = "power_14_sonic_scream";

	private static final ResourceLocation SENSES_MELEE = com.projecthero.mod.ProjectHeroMod.id("sonic_senses_melee");
	private static final ResourceLocation SENSES_REACH = com.projecthero.mod.ProjectHeroMod.id("sonic_senses_reach");

	/** Enhanced Senses: how much ability damage/range it adds, and how long a full bar lasts. */
	public static final float SENSES_ABILITY_BONUS = 15.0f;
	private static final float SENSES_MELEE_BONUS = 8.0f;
	private static final float SENSES_MAX = 100.0f;
	private static final float SENSES_DRAIN = SENSES_MAX / (15 * 20); // 15 s of active use
	private static final float SENSES_REGEN = SENSES_MAX / (12 * 20);
	private static final int SENSES_CD = 20 * 20;

	private SonicScreamHandlers() {
	}

	private static boolean inCone(ServerPlayer p, LivingEntity e, double angleDot) {
		Vec3 to = e.position().subtract(p.getEyePosition()).normalize();
		return to.dot(p.getLookAngle()) > angleDot;
	}

	/** True while Enhanced Senses is toggled on -- read from the synced attachment. */
	public static boolean sensesActive(Player p) {
		var st = p.getAttachedOrElse(ModAttachments.EXPERIMENTAL_STATE, null);
		return st != null && st.ownedPowers.contains(KEY) && st.activeToggles.contains(KEY + "/echolocation");
	}

	/** Extra damage every Sonic Scream ability deals while Enhanced Senses is running. */
	public static float sensesBonus(Player p) {
		return sensesActive(p) ? SENSES_ABILITY_BONUS : 0.0f;
	}

	/** Range multiplier for Sonic Scream abilities while Enhanced Senses is running. */
	public static double sensesRange(Player p, double base) {
		return sensesActive(p) ? base * 1.5 : base;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "sonic_blast", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			float bonus = sensesBonus(p);
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(p.getLookAngle().scale(3)),
					sensesRange(p, 4.0))) {
				if (inCone(p, e, 0.5)) {
					AbilityHelpers.hurt(p, e, 10.0f + bonus);
					AbilityHelpers.knockbackFrom(e, p.position(), 1.4);
					staticStun(e);
				}
			}
			cone(ctx.level(), p, 4);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.7f, 1.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "focused_scream", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			double range = sensesRange(p, 30.0);
			LivingEntity t = AbilityHelpers.raycastEntity(p, range);
			AbilityHelpers.line(ctx.level(), p.getEyePosition(), AbilityHelpers.aimPoint(p, range), ParticleTypes.SONIC_BOOM, 1.0);
			if (t != null) {
				AbilityHelpers.hurt(p, t, 16.0f + sensesBonus(p));
				AbilityHelpers.knockbackFrom(t, p.position(), 1.0);
				AbilityHelpers.applyControl(t, MobEffects.CONFUSION, 80, 0);
				staticStun(t);
			}
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.0f);
			ctx.triggerCooldown();
		}));

		// X -- Sonic Jump. Also boosts an open elytra.
		AbilityHandlers.register(KEY, "sonic_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			if (p.isFallFlying()) {
				AbilityHelpers.addImpulse(p, p.getLookAngle().scale(1.6).add(0, 0.25, 0));
				ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
			} else {
				AbilityHelpers.launchSelf(p, new Vec3(p.getDeltaMovement().x, 1.3, p.getDeltaMovement().z));
				ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY(), p.getZ(), 2, 0.2, 0.0, 0.2, 0.0);
			}
			// no fall damage from this launch -- protected until shortly after the next landing
			ctx.setResource("no_fall_until", p.level().getGameTime() + 200, 1.0e12f);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 0.8f, 1.9f);
			ctx.triggerCooldown();
		}));

		// Z -- Supersonic Scream. Hold 5 s (sound cues while it charges) then unleash: plain = 50 damage
		// in the forward cone, 70s cd. Shift = 40 damage in a 20-block radius around you, 80s cd.
		AbilityHandlers.register(KEY, "supersonic_scream", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.resource("scream_start") > 0.5f) {
					return;
				}
				if (!ctx.cooldownReady()) {
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				ctx.setResource("scream_start", ctx.player().level().getGameTime(), 1.0e12f);
				ctx.setResource("scream_shift", ctx.player().isShiftKeyDown() ? 1 : 0, 1);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				float start = ctx.resource("scream_start");
				if (start <= 0.5f) {
					return;
				}
				long held = ctx.player().level().getGameTime() - (long) start;
				ctx.setResource("scream_start", 0, 1.0e12f);
				ctx.setResource("sonic_charge", 0, 100);
				if (held >= 5 * 20) {
					fireSupersonic(ctx);
				} else {
					AbilityHelpers.sound(ctx.player(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 0.8f);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				float start = ctx.resource("scream_start");
				if (start <= 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				long held = p.level().getGameTime() - (long) start;
				if (held > 5 * 20 + 40) {
					fireSupersonic(ctx);
					return;
				}
				double frac = Math.min(1.0, held / 100.0);
				ctx.setResource("sonic_charge", (float) (frac * 100.0), 100);
				ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1.4, p.getZ(),
						1, 0.15 + frac * 0.3, 0.15, 0.15 + frac * 0.3, 0.0);
				if (held % 8 == 0) {
					AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_CHARGE, 0.7f, 0.6f + (float) frac);
				}
				if (held >= 5 * 20 && held % 20 == 0) {
					p.displayClientMessage(net.minecraft.network.chat.Component.translatable(
							"message.projecthero.sonic.scream_ready"), true);
				}
			}
		});

		AbilityHandlers.register(KEY, "resonance", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			var hit = AbilityHelpers.raycastBlock(p, sensesRange(p, 12.0));
			if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && AbilityHelpers.canGrief()) {
				for (BlockPos bp : BlockPos.betweenClosed(hit.getBlockPos().offset(-1, -1, -1), hit.getBlockPos().offset(1, 1, 1))) {
					if (isFragile(level, bp)) {
						level.destroyBlock(bp, true, p);
					}
				}
			}
			ctx.level().sendParticles(ParticleTypes.NOTE, Vec3.atCenterOf(hit.getBlockPos()).x,
					Vec3.atCenterOf(hit.getBlockPos()).y, Vec3.atCenterOf(hit.getBlockPos()).z, 10, 0.5, 0.5, 0.5, 1.0);
			AbilityHelpers.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.5f);
			ctx.triggerCooldown();
		}));

		// C -- Enhanced Senses. Toggle: the wielder goes blind but everything else sharpens. Drains over
		// 15 s, regenerates while off, and always pays a 20 s cooldown when it ends (early or not).
		AbilityHandlers.register(KEY, "echolocation", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!ctx.cooldownReady()) {
					ctx.setToggled(false);
					ctx.actionBar("message.projecthero.ability.on_cooldown",
							net.minecraft.network.chat.Component.translatable(ctx.ability().nameKey()),
							String.format(java.util.Locale.ROOT, "%.0f", Math.ceil(ctx.cooldownRemaining() / 20.0f)));
					return;
				}
				if (!ExperimentalPowers.state(ctx.player()).resources.containsKey(KEY + "/senses")) {
					ctx.setResource("senses", SENSES_MAX, SENSES_MAX);
				}
				if (ctx.resource("senses") < 5.0f) {
					ctx.setToggled(false);
					return;
				}
				sensesOn(ctx.player());
				AbilityHelpers.sound(ctx.player(), SoundEvents.WARDEN_SONIC_CHARGE, 0.8f, 0.7f);
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				sensesOff(ctx.player());
				// Clear blindness the instant the mode ends instead of letting the last applied instance
				// simply run out -- with the fix below it now outlives a single tick, so leaving this out
				// would keep the wielder blind for a moment after toggling off.
				ctx.player().removeEffect(MobEffects.BLINDNESS);
				ctx.triggerCooldown(SENSES_CD);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				sensesOn(p);
				// v0.10.15: was re-applied with exactly a 20-tick duration every tick. Vanilla's blindness
				// fog fades in/out over its last 20 ticks of remaining duration, so sitting right at that
				// threshold every single tick (any one-tick hiccup in when this runs relative to the
				// entity's own effect countdown) pushed the fade ratio below 1.0 and back -- a visible
				// strobe. Refreshing to well above the fade window keeps it a flat, solid blind.
				p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));
				ctx.addResource("senses", -SENSES_DRAIN, SENSES_MAX);
				if (ctx.resource("senses") <= 0.0f) {
					ctx.setToggled(false);
					sensesOff(p);
					ctx.triggerCooldown(SENSES_CD);
					ctx.actionBar("message.projecthero.sonic.senses_out");
					return;
				}
				for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), 5.0)) {
					e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, true, true));
				}
				if (p.tickCount % 4 == 0) {
					ctx.level().sendParticles(ParticleTypes.SONIC_BOOM, p.getX(), p.getY() + 1, p.getZ(), 1, 0.3, 0.3, 0.3, 0.0);
				}
			}
		});

		// Shift + right-click: a sonar ping. Entities within 20 blocks glow for the echolocator alone
		// (see EntityGlowMixin) for a few seconds. UseBlockCallback fires on an ordinary aimed right-click
		// (empty-hand clicks that hit nothing send no packet at all -- the same limitation Wind's elytra
		// boost hit), so this triggers whenever you sneak + right-click any block.
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			if (level.isClientSide || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			Power power = Powers.byKey(KEY);
			if (power == null || !ExperimentalPowers.owns(sp, power)) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			long now = sp.level().getGameTime();
			if (ExperimentalPowers.getResource(sp, power, "echo_cd") > now) {
				return net.minecraft.world.InteractionResult.PASS;
			}
			ExperimentalPowers.setResource(sp, power, "echo_until", now + 3 * 20, 1.0e12f);
			ExperimentalPowers.setResource(sp, power, "echo_cd", now + 4 * 20, 1.0e12f);
			if (sp.level() instanceof ServerLevel sl) {
				sl.sendParticles(ParticleTypes.SONIC_BOOM, sp.getX(), sp.getY() + 1, sp.getZ(), 1, 0, 0, 0, 0);
			}
			AbilityHelpers.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 0.5f, 1.9f);
			return net.minecraft.world.InteractionResult.SUCCESS;
		});

		com.projecthero.mod.hero.PowerPassives.registerTick(KEY, player -> {
			Power power = Powers.byKey(KEY);
			if (power == null) {
				return;
			}
			boolean active = ExperimentalPowers.isToggled(player, power, power.ability(com.projecthero.mod.hero.AbilitySlot.SLOT_6));
			if (!active && ExperimentalPowers.getResource(player, power, "senses") < SENSES_MAX) {
				ExperimentalPowers.addResource(player, power, "senses", SENSES_REGEN, SENSES_MAX);
			}
		});
	}

	private static void sensesOn(ServerPlayer p) {
		PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, SENSES_MELEE, SENSES_MELEE_BONUS, AttributeModifier.Operation.ADD_VALUE);
		PowerToggles.modifier(p, Attributes.ENTITY_INTERACTION_RANGE, SENSES_REACH, 5.0, AttributeModifier.Operation.ADD_VALUE);
	}

	private static void sensesOff(ServerPlayer p) {
		PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, SENSES_MELEE);
		PowerToggles.clearModifier(p, Attributes.ENTITY_INTERACTION_RANGE, SENSES_REACH);
	}

	/** A short, sharp confusion + slow -- the "stun" every offensive scream now leaves behind. */
	private static void staticStun(LivingEntity e) {
		AbilityHelpers.applyControl(e, MobEffects.MOVEMENT_SLOWDOWN, 30, 3);
		AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 30, 0);
	}

	private static void fireSupersonic(AbilityContext ctx) {
		ServerPlayer p = ctx.player();
		ServerLevel level = ctx.level();
		boolean shift = ctx.resource("scream_shift") > 0.5f;
		ctx.setResource("scream_shift", 0, 1);
		if (shift) {
			double r = 20.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				AbilityHelpers.hurt(p, e, 40.0f + sensesBonus(p));
				AbilityHelpers.knockbackFrom(e, p.position(), 2.4);
				AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
				staticStun(e);
			}
			for (int i = 0; i < 60; i++) {
				double a = level.random.nextDouble() * Math.PI * 2;
				double rr = level.random.nextDouble() * r;
				level.sendParticles(ParticleTypes.SONIC_BOOM, p.getX() + Math.cos(a) * rr, p.getY() + 1.0,
						p.getZ() + Math.sin(a) * rr, 1, 0, 0, 0, 0);
			}
			level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.8f, 0.5f);
			ctx.triggerCooldown(80 * 20);
			return;
		}
		double r = sensesRange(p, 20.0);
		Vec3 look = p.getLookAngle();
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.getEyePosition().add(look.scale(r * 0.5)), r * 0.55)) {
			Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
			if (to.length() > r || to.normalize().dot(look) < 0.6) {
				continue;
			}
			AbilityHelpers.hurt(p, e, 50.0f + sensesBonus(p));
			AbilityHelpers.knockbackFrom(e, p.position(), 3.0);
			AbilityHelpers.applyControl(e, MobEffects.CONFUSION, 120, 0);
			staticStun(e);
		}
		if (AbilityHelpers.canGrief()) {
			for (int i = 1; i <= (int) r; i++) {
				BlockPos step = BlockPos.containing(p.getEyePosition().add(look.scale(i)));
				for (BlockPos bp : BlockPos.betweenClosed(step.offset(-1, -1, -1), step.offset(1, 1, 1))) {
					if (isFragile(level, bp)) {
						level.destroyBlock(bp, false, p);
					}
				}
			}
		}
		for (int i = 1; i <= (int) r; i++) {
			Vec3 pt = p.getEyePosition().add(look.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.12 * i, 0.12 * i, 0.12 * i, 0.0);
		}
		level.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.6f, 0.6f);
		ctx.triggerCooldown(70 * 20);
	}

	private static void cone(ServerLevel level, ServerPlayer p, int dist) {
		Vec3 look = p.getLookAngle();
		Vec3 origin = p.getEyePosition();
		for (int i = 1; i <= dist; i++) {
			Vec3 pt = origin.add(look.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, pt.x, pt.y, pt.z, 1, 0.1 * i, 0.1 * i, 0.1 * i, 0.0);
		}
	}

	private static boolean isFragile(ServerLevel level, BlockPos pos) {
		BlockState st = level.getBlockState(pos);
		if (st.isAir()) {
			return false;
		}
		if (st.is(Blocks.GLASS) || st.is(Blocks.GLASS_PANE) || st.is(Blocks.TINTED_GLASS)
				|| st.is(Blocks.ICE) || st.is(Blocks.GLOWSTONE) || st.is(Blocks.SEA_LANTERN)
				|| st.is(net.minecraft.tags.BlockTags.IMPERMEABLE)) {
			return true;
		}
		float hardness = st.getDestroySpeed(level, pos);
		return hardness >= 0.0f && hardness <= 0.35f && !st.is(net.minecraft.tags.BlockTags.LEAVES);
	}
}
