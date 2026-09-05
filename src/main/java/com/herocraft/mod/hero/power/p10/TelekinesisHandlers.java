package com.herocraft.mod.hero.power.p10;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.GrabHelper;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.HeroFlight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

/** Power 10 — Telekinesis. Every ability draws on a shared Telekinetic Energy meter ("psi", max 500). */
public final class TelekinesisHandlers {
	private static final String KEY = "power_10_telekinesis";
	static final float MAX_PSI = 500.0f;
	private static final float PSI_REGEN_PER_TICK = 2.5f;

	private TelekinesisHandlers() {
	}

	/** Spend {@code amount} of the Telekinetic Energy meter, or refuse (with a message) if it is too low. */
	private static boolean spendPsi(AbilityContext ctx, float amount) {
		if (ctx.resource("psi") < amount) {
			ctx.actionBar("message.herocraft.telekinesis.drained");
			return false;
		}
		ctx.addResource("psi", -amount, MAX_PSI);
		return true;
	}

	public static void register() {
		AbilityHandlers.register(KEY, "force_push", Handlers.instant(ctx -> {
			if (!spendPsi(ctx, 40.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			Vec3 cone = p.getEyePosition().add(p.getLookAngle().scale(4.0));
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, cone, 4.5)) {
				// a hard shove -- several blocks of launch on a clear line (tune in playtest)
				Vec3 dir = e.position().subtract(p.getEyePosition()).normalize().scale(2.0).add(0, 0.5, 0);
				AbilityHelpers.push(e, dir);
				AbilityHelpers.knockbackFrom(e, p.position(), 1.6);
				AbilityHelpers.hurt(p, e, 8.0f);
			}
			AbilityHelpers.burst(ctx.level(), cone, ParticleTypes.SCULK_SOUL, 20, 0.6);
			AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.7f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "force_pull", Handlers.instant(ctx -> {
			if (!spendPsi(ctx, 30.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 20.0);
			if (t != null) {
				Vec3 dir = p.position().subtract(t.position()).normalize().scale(1.6).add(0, 0.3, 0);
				AbilityHelpers.push(t, dir);
			}
			for (ItemEntity item : ctx.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(16.0))) {
				item.setDeltaMovement(p.position().subtract(item.position()).normalize().scale(0.8));
			}
			AbilityHelpers.sound(p, SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 1.3f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "psychic_flight", new AbilityHandler() {
			@Override
			public void onToggleOn(AbilityContext ctx) {
				if (!HeroFlight.startFlying(ctx.player(), ctx.power())) {
					ctx.setToggled(false);
				}
			}

			@Override
			public void onToggleOff(AbilityContext ctx) {
				HeroFlight.setFlying(ctx.player(), false);
			}

			@Override
			public void onToggleTick(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				if (!HeroFlight.isFlying(p)) {
					ctx.setToggled(false);
					return;
				}
				// Telekinetic flight runs purely off the Telekinetic Energy meter (~3.3 s per 100 psi,
				// ~17 s from a full bar) -- no separate flight-stamina drain.
				ctx.addResource("psi", -1.0f, MAX_PSI);
				if (ctx.resource("psi") <= 0.0f) {
					ctx.setToggled(false);
					HeroFlight.setFlying(p, false);
					ctx.actionBar("message.herocraft.telekinesis.drained");
					return;
				}
				ctx.level().sendParticles(ParticleTypes.SCULK_SOUL, p.getX(), p.getY() + 0.2, p.getZ(), 2, 0.3, 0.3, 0.3, 0.0);
			}
		});

		AbilityHandlers.register(KEY, "telekinetic_explosion", Handlers.instant(ctx -> {
			if (!spendPsi(ctx, 200.0f)) {
				return;
			}
			ServerPlayer p = ctx.player();
			ServerLevel level = ctx.level();
			double r = 8.0;
			for (LivingEntity e : AbilityHelpers.enemiesAround(p, p.position(), r)) {
				Vec3 dir = e.position().subtract(p.position()).normalize().scale(2.8).add(0, 0.6, 0);
				AbilityHelpers.push(e, dir);
				AbilityHelpers.hurt(p, e, 30.0f);
			}
			level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, p.getX(), p.getY() + 1, p.getZ(), 60, r / 2, 0.5, r / 2, 0.2);
			AbilityHelpers.sound(p, SoundEvents.WARDEN_SONIC_BOOM, 1.0f, 1.6f);
			ctx.triggerCooldown();
		}));

		AbilityHandlers.register(KEY, "telekinetic_grab", Handlers.instantTicking(ctx -> {
			if (GrabHelper.isHolding(ctx)) {
				GrabHelper.throwHeld(ctx, 2.4, 4.0f);
				AbilityHelpers.sound(ctx.player(), SoundEvents.ILLUSIONER_CAST_SPELL, 1.0f, 0.6f);
				ctx.triggerCooldown();
			} else if (spendPsi(ctx, 60.0f) && GrabHelper.tryGrab(ctx, 12.0, 160)) {
				ctx.actionBar("message.herocraft.ability.grabbed");
			}
		}, ctx -> {
			if (GrabHelper.isHolding(ctx)) {
				ctx.addResource("psi", -1.0f, MAX_PSI);
				if (ctx.resource("psi") <= 0.0f) {
					GrabHelper.throwHeld(ctx, 0.0, 0.0f);
				}
			}
			GrabHelper.tick(ctx, 3.0);
		}));

		AbilityHandlers.register(KEY, "block_manipulation", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				ServerPlayer p = ctx.player();
				var hit = AbilityHelpers.raycastBlock(p, 8.0);
				if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK || !AbilityHelpers.canGrief()) {
					return;
				}
				if (!spendPsi(ctx, 50.0f)) {
					return;
				}
				var bp = hit.getBlockPos();
				var state = p.level().getBlockState(bp);
				if (state.getDestroySpeed(p.level(), bp) < 0 || state.getDestroySpeed(p.level(), bp) > 3.0f || state.isAir()) {
					return;
				}
				var fb = net.minecraft.world.entity.item.FallingBlockEntity.fall((ServerLevel) p.level(), bp, state);
				fb.setNoGravity(true);
				fb.time = 1;
				ctx.setResource("block_id", fb.getId(), 1_000_000);
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				int id = (int) ctx.resource("block_id");
				ctx.setResource("block_id", 0, 1_000_000);
				if (id == 0) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (p.level().getEntity(id) instanceof net.minecraft.world.entity.item.FallingBlockEntity fb) {
					fb.setNoGravity(false);
					fb.setDeltaMovement(p.getLookAngle().scale(1.8));
					fb.setHurtsEntities(2.0f, 20);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				int id = (int) ctx.resource("block_id");
				if (id == 0) {
					return;
				}
				ServerPlayer p = ctx.player();
				if (!(p.level().getEntity(id) instanceof net.minecraft.world.entity.item.FallingBlockEntity fb) || !fb.isAlive()) {
					ctx.setResource("block_id", 0, 1_000_000);
					return;
				}
				// holding the block aloft steadily bleeds Telekinetic Energy
				ctx.addResource("psi", -1.0f, MAX_PSI);
				if (ctx.resource("psi") <= 0.0f) {
					fb.setNoGravity(false);
					ctx.setResource("block_id", 0, 1_000_000);
					return;
				}
				Vec3 hold = p.getEyePosition().add(p.getLookAngle().scale(2.5));
				fb.setPos(hold.x, hold.y, hold.z);
				fb.setDeltaMovement(Vec3.ZERO);
				fb.time = 1;
			}
		});

		com.herocraft.mod.hero.PowerPassives.register(KEY, (player, active) -> {
			if (active) {
				var power = com.herocraft.mod.hero.Powers.byKey(KEY);
				if (com.herocraft.mod.hero.ExperimentalPowers.getResource(player, power, "psi") <= 0.0f) {
					com.herocraft.mod.hero.ExperimentalPowers.setResource(player, power, "psi", MAX_PSI, MAX_PSI);
				}
			}
		});
		com.herocraft.mod.hero.PowerPassives.registerTick(KEY, player -> {
			var power = com.herocraft.mod.hero.Powers.byKey(KEY);
			if (power != null
					&& com.herocraft.mod.hero.ExperimentalPowers.getResource(player, power, "psi") < MAX_PSI) {
				com.herocraft.mod.hero.ExperimentalPowers.addResource(player, power, "psi", PSI_REGEN_PER_TICK, MAX_PSI);
			}
			if (player.level() instanceof ServerLevel sl) {
				for (ItemEntity item : sl.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(5.0))) {
					item.setDeltaMovement(item.getDeltaMovement().add(
							player.position().subtract(item.position()).normalize().scale(0.02)));
				}
			}
		});
	}
}
