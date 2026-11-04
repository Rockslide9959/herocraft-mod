package com.projecthero.mod.galactus.orbit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.projecthero.mod.galactus.GalactusCombat;
import com.projecthero.mod.galactus.GalactusConfig;
import com.projecthero.mod.galactus.GalactusEvent;
import com.projecthero.mod.galactus.GalactusFx;
import com.projecthero.mod.galactus.entity.SilverSurferEntity;
import com.projecthero.mod.quirk.fx.QuirkCamera;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 Galactus raid v2: the rules of the orbit, for every fighter up there.
 * <ul>
 * <li><b>Orbit gravity</b>: Jump Boost II while in the arena, so a sprint-jump clears the 5-block gaps.</li>
 * <li><b>Falling</b> (the friend's notes): drop {@link GalactusConfig.Orbit#fallDepth} below the plane and the Silver
 * Surfer streaks in, catches you and sets you down on the nearest safe ground for a little damage.</li>
 * <li><b>Flying</b> (the notes, Stage 1): hang in the air over {@link GalactusConfig.Orbit#flyHeight} blocks above the
 * plane for 1.5 s and his eyes lock on (a red warning); keep at it and the beam slams you down and grounds you for 5 s
 * (anyone grounded is pushed back down whenever they climb). Gravity-pad flights are exempt.</li>
 * <li><b>Gravity pads</b>: step on one and it throws you along an arc to its far end.</li>
 * <li>No building or breaking in the arena (nobody pillars out of the fight).</li>
 * </ul>
 * Per-player scratch (air time, grounding, pad immunity): static, cleared with the raid statics.
 */
public final class OrbitRules {
	private static final Map<UUID, Integer> AIR = new HashMap<>();
	private static final Map<UUID, Long> GROUNDED = new HashMap<>();
	private static final Map<UUID, Long> PAD_IMMUNE = new HashMap<>();
	private static final Map<UUID, Long> RESCUE_LINE = new HashMap<>();

	private OrbitRules() {
	}

	public static void initialize() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !(GalactusOrbit.inOrbit(player) && !player.isCreative()));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (GalactusOrbit.inOrbit(player) && !player.isCreative() && player.getItemInHand(hand).getItem() instanceof BlockItem) {
				return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
	}

	public static void clearStatics() {
		AIR.clear();
		GROUNDED.clear();
		PAD_IMMUNE.clear();
		RESCUE_LINE.clear();
	}

	public static boolean grounded(ServerPlayer p) {
		Long until = GROUNDED.get(p.getUUID());
		return until != null && p.serverLevel().getGameTime() < until;
	}

	public static void ground(ServerPlayer p, int ticks) {
		GROUNDED.put(p.getUUID(), p.serverLevel().getGameTime() + ticks);
		p.getAbilities().flying = false;
		p.onUpdateAbilities();
		if (p.isFallFlying()) {
			p.stopFallFlying();
		}
		if (p.isPassenger()) {
			p.stopRiding();
		}
	}

	/** Gravity + falling + pads, every tick for each fighter up here. */
	public static void tickPlayer(GalactusEvent ev, ServerLevel orbit, ServerPlayer p, Function<Vec3, Vec3> safeSpot, List<OrbitArena.Pad> pads,
			boolean flyBeam, DevourerEntity d) {
		GalactusConfig.Orbit c = GalactusConfig.orbit();
		long now = orbit.getGameTime();
		if (now % 40 == 0 && c.jumpBoostAmplifier >= 0) {
			p.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, c.jumpBoostAmplifier, true, false, true));
		}
		// pushed back down while grounded
		if (grounded(p) && !p.onGround() && p.getDeltaMovement().y > 0 && p.getY() > GalactusOrbit.PLANE_Y + 3) {
			p.setDeltaMovement(p.getDeltaMovement().x, -0.6, p.getDeltaMovement().z);
			p.hurtMarked = true;
		}
		// the fall: caught by the Surfer
		if (p.getY() < GalactusOrbit.PLANE_Y - c.fallDepth) {
			rescue(ev, orbit, p, safeSpot.apply(p.position()));
			return;
		}
		// pads
		if (p.onGround() && pads != null) {
			for (OrbitArena.Pad pad : pads) {
				double dx = p.getX() - (pad.at().getX() + 0.5), dz = p.getZ() - (pad.at().getZ() + 0.5);
				if (dx * dx + dz * dz < 2.0 && Math.abs(p.getY() - (pad.at().getY() + 1)) < 0.6) {
					Long imm = PAD_IMMUNE.get(p.getUUID());
					if (imm != null && now < imm - 40) {
						continue;
					}
					Vec3 v = OrbitArena.launch(p.position(), pad.to(), 32);
					p.setDeltaMovement(v);
					p.hurtMarked = true;
					p.fallDistance = 0;
					PAD_IMMUNE.put(p.getUUID(), now + 60);
					orbit.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 1.5f, 1.3f);
					GalactusFx.forced(orbit, ParticleTypes.END_ROD, p.getX(), p.getY() + 0.2, p.getZ(), 30, 0.6, 0.2, 0.6, 0.15);
					break;
				}
			}
		}
		if (pads != null && now % 6 == 0) {
			for (OrbitArena.Pad pad : pads) {
				if (p.distanceToSqr(Vec3.atCenterOf(pad.at())) < 40 * 40) {
					orbit.sendParticles(p, ParticleTypes.END_ROD, true, pad.at().getX() + 0.5, pad.at().getY() + 1.2, pad.at().getZ() + 0.5, 2, 0.5, 0.4, 0.5, 0.03);
				}
			}
		}
		// the flyer's beam
		if (flyBeam && d != null) {
			boolean padFlight = PAD_IMMUNE.getOrDefault(p.getUUID(), 0L) > now;
			boolean high = !p.onGround() && p.getY() > GalactusOrbit.PLANE_Y + 1 + c.flyHeight;
			boolean flying = p.getAbilities().flying || p.isFallFlying() || p.isPassenger() || p.hasEffect(MobEffects.LEVITATION);
			int air = AIR.getOrDefault(p.getUUID(), 0);
			if (!padFlight && (high || flying && !p.onGround())) {
				air++;
			} else {
				air = Math.max(0, air - 2);
			}
			AIR.put(p.getUUID(), air);
			if (air == c.flyWarnTicks) {
				p.displayClientMessage(Component.translatable("message.projecthero.galactus.sees_you").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
				orbit.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0f, 1.4f);
			}
			if (air >= c.flyWarnTicks && air < c.flyBeamTicks && now % 2 == 0) {
				// the lock-on: a red ring round them
				GalactusFx.ring(orbit, GalactusFx.EMBER, p.position().add(0, 1, 0), 1.6, 16);
			}
			if (air >= c.flyBeamTicks) {
				AIR.put(p.getUUID(), 0);
				beam(orbit, d, p, (float) c.flyBeamDamage);
				ground(p, c.groundedTicks);
			}
		} else {
			AIR.remove(p.getUUID());
		}
	}

	/** His eye beam onto {@code p}: a line of light from his face, the hit, and down they go. */
	public static void beam(ServerLevel orbit, DevourerEntity d, ServerPlayer p, float damage) {
		Vec3 face = d.pose().face;
		GalactusFx.line(orbit, GalactusFx.COSMIC, face, p.position().add(0, 1, 0), 1.5);
		GalactusFx.line(orbit, ParticleTypes.END_ROD, face, p.position().add(0, 1, 0), 3.0);
		orbit.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 3.0f, 0.7f);
		GalactusCombat.hit(orbit, d, p, damage);
		p.setDeltaMovement(p.getDeltaMovement().x * 0.2, -2.2, p.getDeltaMovement().z * 0.2);
		p.hurtMarked = true;
		QuirkCamera.shake(p, 5f, 14);
	}

	/** The Surfer catches {@code p} and sets them down at {@code at}. */
	public static void rescue(GalactusEvent ev, ServerLevel orbit, ServerPlayer p, Vec3 at) {
		GalactusConfig.Orbit c = GalactusConfig.orbit();
		Vec3 from = p.position();
		p.fallDistance = 0;
		p.teleportTo(at.x, at.y + 0.2, at.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		if (c.fallDamage > 0) {
			p.hurt(orbit.damageSources().magic(), (float) c.fallDamage);
		}
		SilverSurferEntity s = ev.orbitSurfer(orbit);
		if (s != null) {
			GalactusFx.line(orbit, GalactusFx.SILVER, from.add(0, 4, 0), at.add(0, 1, 0), 1.0);
			s.teleportTo(at.x + 1.5, at.y + 1.5, at.z + 1.5);
		}
		GalactusFx.forced(orbit, GalactusFx.SILVER, at.x, at.y + 1, at.z, 40, 0.8, 1, 0.8, 0.1);
		orbit.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.5f, 1.6f);
		QuirkCamera.vignette(p, 0xFFFFFF, 0.6f, 10);
		long now = orbit.getGameTime();
		Long last = RESCUE_LINE.get(p.getUUID());
		if (last == null || now - last > 400) {
			RESCUE_LINE.put(p.getUUID(), now);
			p.sendSystemMessage(Component.translatable("boss.projecthero.galactus.chat",
					Component.translatable("entity.projecthero.silver_surfer").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
					Component.translatable("boss.projecthero.galactus.surfer.say.catch").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
	}
}
