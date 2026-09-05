package com.herocraft.mod.hero.power.p11;

import com.herocraft.mod.hero.AbilityContext;
import com.herocraft.mod.hero.AbilityHandler;
import com.herocraft.mod.hero.AbilityHandlers;
import com.herocraft.mod.hero.ExperimentalPowers;
import com.herocraft.mod.hero.power.AbilityHelpers;
import com.herocraft.mod.hero.power.Handlers;
import com.herocraft.mod.hero.power.SafeTeleport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Power 11 — Teleportation. All destinations validated by {@link SafeTeleport}. */
public final class TeleportationHandlers {
	private static final String KEY = "power_11_teleportation";
	private static final double BLINK_RANGE = 75.0;

	private TeleportationHandlers() {
	}

	private static void poof(ServerLevel level, Vec3 at) {
		level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 30, 0.3, 0.6, 0.3, 0.4);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.1);
	}

	/** Where a blink aimed right now would land: on top of the block being looked at, else max range. */
	private static Vec3 blinkDest(ServerPlayer p) {
		BlockHitResult bhr = AbilityHelpers.raycastBlock(p, BLINK_RANGE);
		if (bhr.getType() == HitResult.Type.BLOCK) {
			BlockPos face = bhr.getBlockPos().relative(bhr.getDirection());
			// drop onto the nearest floor if aiming at a wall face over open air
			for (int i = 0; i < 4; i++) {
				BlockPos below = face.below(i + 1);
				if (!p.level().getBlockState(below).getCollisionShape(p.level(), below).isEmpty()) {
					return Vec3.atBottomCenterOf(face.below(i));
				}
			}
			return Vec3.atBottomCenterOf(face);
		}
		return p.position().add(p.getLookAngle().scale(BLINK_RANGE));
	}

	public static void register() {
		// Blink: hold R to paint the destination, release to teleport onto that block.
		AbilityHandlers.register(KEY, "blink", new AbilityHandler() {
			@Override
			public void onActivate(AbilityContext ctx) {
				if (ctx.cooldownReady()) {
					ctx.setResource("aiming", 1, 1);
				}
			}

			@Override
			public void onServerTick(AbilityContext ctx) {
				if (ctx.resource("aiming") < 0.5f) {
					return;
				}
				ServerPlayer p = ctx.player();
				Vec3 d = blinkDest(p);
				if (p.tickCount % 2 == 0) {
					ctx.level().sendParticles(ParticleTypes.PORTAL, d.x, d.y + 0.1, d.z, 14, 0.35, 0.1, 0.35, 0.05);
					ctx.level().sendParticles(ParticleTypes.END_ROD, d.x, d.y + 0.9, d.z, 3, 0.08, 0.45, 0.08, 0.0);
				}
			}

			@Override
			public void onRelease(AbilityContext ctx) {
				if (ctx.resource("aiming") < 0.5f) {
					return;
				}
				ctx.setResource("aiming", 0, 1);
				ServerPlayer p = ctx.player();
				Vec3 from = p.position();
				Vec3 dest = blinkDest(p);
				if (SafeTeleport.tryTeleport(p, dest) || SafeTeleport.blink(p, p.getLookAngle(), BLINK_RANGE)) {
					poof(ctx.level(), from);
					poof(ctx.level(), p.position());
					AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.2f);
					ctx.triggerCooldown();
				}
			}
		});

		AbilityHandlers.register(KEY, "target_teleport", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			LivingEntity t = AbilityHelpers.raycastEntity(p, 50.0);
			if (t == null) {
				return;
			}
			Vec3 behind = t.position().subtract(t.getLookAngle().scale(1.5));
			Vec3 from = p.position();
			if (SafeTeleport.tryTeleport(p, behind)) {
				p.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, t.getEyePosition());
				poof(ctx.level(), from);
				poof(ctx.level(), p.position());
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.0f);
				ctx.triggerCooldown();
			}
		}));

		AbilityHandlers.register(KEY, "escape_blink", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 from = p.position();
			Vec3 back = p.getLookAngle().reverse();
			if (SafeTeleport.blink(p, back, 8.0)) {
				p.addEffect(new net.minecraft.world.effect.MobEffectInstance(
						net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 20, 2, false, false, false));
				poof(ctx.level(), from);
				poof(ctx.level(), p.position());
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 1.6f);
				ctx.triggerCooldown();
			}
		}));

		// Spatial Frenzy: 15-damage teleport-strikes on everything in a 25-block area.
		AbilityHandlers.register(KEY, "spatial_frenzy", Handlers.instantTicking(ctx -> {
			ctx.setResource("frenzy", 40, 40);
			ctx.triggerCooldown();
			AbilityHelpers.sound(ctx.player(), SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.7f);
		}, ctx -> {
			int t = (int) ctx.resource("frenzy");
			if (t <= 0) {
				return;
			}
			ctx.setResource("frenzy", t - 1, 40);
			if (t % 6 != 0) {
				return;
			}
			ServerPlayer p = ctx.player();
			var near = AbilityHelpers.enemiesAround(p, p.position(), 25.0);
			if (near.isEmpty()) {
				return;
			}
			LivingEntity target = near.get(p.getRandom().nextInt(near.size()));
			Vec3 behind = target.position().subtract(target.getLookAngle().scale(1.2));
			Vec3 from = p.position();
			if (SafeTeleport.tryTeleport(p, behind)) {
				poof(ctx.level(), from);
				AbilityHelpers.hurt(p, target, 15.0f);
				AbilityHelpers.burst(ctx.level(), target.position().add(0, 1, 0), ParticleTypes.CRIT, 12, 0.3);
			}
		}));

		// Return Marker: first press records where you stand; next press teleports you back there.
		AbilityHandlers.register(KEY, "teleport_mark", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			BlockPos mark = ExperimentalPowers.getMarker(p, ctx.power(), "return");
			if (mark != null) {
				// The marker remembers the dimension it was set in and always returns you there,
				// not to those coordinates in whatever dimension you happen to be standing in.
				var dimKey = ExperimentalPowers.getMarkerDimension(p, ctx.power(), "return");
				ServerLevel target = (dimKey == null || p.getServer() == null)
						? ctx.level() : p.getServer().getLevel(dimKey);
				if (target == null) {
					target = ctx.level();
				}
				ServerLevel originLevel = ctx.level();
				Vec3 from = p.position();
				if (SafeTeleport.tryTeleport(p, target, Vec3.atBottomCenterOf(mark))) {
					poof(originLevel, from);
					poof((ServerLevel) p.level(), p.position());
					AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 1.0f, 0.9f);
					ExperimentalPowers.clearMarker(p, ctx.power(), "return");
					ctx.triggerCooldown();
				} else {
					ctx.actionBar("message.herocraft.teleport.mark_blocked");
					ExperimentalPowers.clearMarker(p, ctx.power(), "return");
				}
			} else {
				ExperimentalPowers.setMarker(p, ctx.power(), "return", p.blockPosition());
				ctx.actionBar("message.herocraft.teleport.mark_placed");
				AbilityHelpers.burst(ctx.level(), p.position(), ParticleTypes.REVERSE_PORTAL, 20, 0.3);
			}
		}));

		AbilityHandlers.register(KEY, "phase_jump", Handlers.instant(ctx -> {
			ServerPlayer p = ctx.player();
			Vec3 from = p.position();
			// far enough to clear a wall up to 3 blocks thick (player half-width on each side + 3)
			if (SafeTeleport.phaseThrough(p, p.getLookAngle(), 4.5)) {
				poof(ctx.level(), from);
				poof(ctx.level(), p.position());
				AbilityHelpers.sound(p, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.4f);
				ctx.triggerCooldown();
			} else {
				ctx.actionBar("message.herocraft.teleport.no_room");
			}
		}));
	}
}
