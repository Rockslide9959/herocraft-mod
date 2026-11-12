package com.projecthero.mod.doctorfate;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.doctorfate.data.FateState;
import com.projecthero.mod.doctorfate.network.FatePossessPayload;
import com.projecthero.mod.squad.Squads;


import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: Possession. At 100 Dominion with the helmet on, Nabu takes the body for 15 s: he flies it and casts on his
 * own at the strongest hostile nearby (huge damage), never a squadmate, and the player can only watch. Each Space press (sent by
 * the client) struggles: 0.5 s off. Afterward the helmet falls off by itself and can't go back on for 60 s, and Dominion settles
 * at 70. On a server nobody is possessed inside spawn protection (Dominion waits at 99 until they leave).
 *
 * <p>The drive is the Ghostfreak pattern: the server streams the body's movement keys and look to the possessed client every
 * tick ({@link FatePossessPayload}); the client plays them as its own input, so the flight stays smooth.
 */
public final class DoctorFatePossession {
	private static final Map<UUID, Integer> CAST_STEP = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_STRUGGLE = new ConcurrentHashMap<>();

	private DoctorFatePossession() {
	}

	static void clearSessionState() {
		CAST_STEP.clear();
		LAST_STRUGGLE.clear();
	}

	static void forget(UUID id) {
		CAST_STEP.remove(id);
		LAST_STRUGGLE.remove(id);
	}

	/** On a dedicated server, inside spawn protection (never on single player: vanilla answers false there). */
	public static boolean inSpawnProtection(ServerPlayer p) {
		return p.getServer() != null && p.getServer().isUnderSpawnProtection((ServerLevel) p.level(), p.blockPosition(), p);
	}

	static void tick(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		long now = p.level().getGameTime();
		if (s.possessedUntil > 0L) {
			if (now >= s.possessedUntil) {
				end(p);
			} else {
				drive(p, s, now);
			}
			return;
		}
		if (s.helmetOn && s.helmetChangeAt == 0L && s.dominion >= DoctorFateConfig.DOMINION_MAX) {
			if (inSpawnProtection(p)) {
				s.dominion = DoctorFateConfig.DOMINION_MAX - 1f;
				return;
			}
			start(p);
		}
	}

	/** Nabu takes the body. */
	public static void start(ServerPlayer p) {
		long now = p.level().getGameTime();
		FateState n = DoctorFate.state(p).copy();
		n.possessedUntil = now + DoctorFateConfig.POSSESSION_TICKS;
		n.shieldUp = false;
		n.dominion = DoctorFateConfig.DOMINION_MAX;
		n.bargain = n.bargain.withPending(FateState.Bargain.NONE_KIND, 0L);
		DoctorFate.save(p, n);
		CAST_STEP.put(p.getUUID(), 0);
		DoctorFateFlight.start(p);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 2.0f), p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.2, 0.6, 0.0);
		DoctorFate.sound(level, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.35f, 0.7f);
		// (no title: the HUD's own "Nabu holds you" panel says it, with the time left and the struggle hint)
		p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.possessed_subtitle").withStyle(ChatFormatting.GOLD), false);
	}

	/** A Space press while possessed: 0.5 s off (at most one every 2 ticks). True if it counted. */
	public static boolean struggle(ServerPlayer p) {
		FateState s = DoctorFate.state(p);
		long now = p.level().getGameTime();
		if (s.possessedUntil <= now) {
			return false;
		}
		Long last = LAST_STRUGGLE.get(p.getUUID());
		if (last != null && now - last < DoctorFateConfig.STRUGGLE_MIN_GAP && now >= last) {
			return false;
		}
		LAST_STRUGGLE.put(p.getUUID(), now);
		FateState n = s.copy();
		n.possessedUntil = Math.max(now, s.possessedUntil - DoctorFateConfig.STRUGGLE_TICKS);
		DoctorFate.save(p, n);
		((ServerLevel) p.level()).sendParticles(DoctorFate.dust(DoctorFate.BLUE, 1.0f), p.getX(), p.getY() + 1, p.getZ(), 4, 0.4, 0.6, 0.4, 0.0);
		if (n.possessedUntil <= now) {
			end(p);
		}
		return true;
	}

	/** Nabu lets go: the helmet falls off, 60 s lockout, Dominion 70, a slow fall to the ground. */
	public static void end(ServerPlayer p) {
		long now = p.level().getGameTime();
		FateState n = DoctorFate.state(p).copy();
		n.possessedUntil = 0L;
		n.lockoutUntil = now + DoctorFateConfig.LOCKOUT_TICKS;
		n.dominion = DoctorFateConfig.AFTER_POSSESSION_DOMINION;
		DoctorFate.save(p, n);
		DoctorFate.setHelmet(p, false);
		DoctorFateFlight.stop(p);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0, false, false));
		release(p);
		CAST_STEP.remove(p.getUUID());
		DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.7f, 0.6f);
		((ServerLevel) p.level()).sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.2f), p.getX(), p.getEyeY(), p.getZ(), 20, 0.3, 0.3, 0.3, 0.0);
		p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.released").withStyle(ChatFormatting.GOLD), false);
	}

	/** Tells the client it's its own again. */
	static void release(ServerPlayer p) {
		{
			DoctorFate.send(p, FatePossessPayload.RELEASE);
		}
	}

	/** The strongest hostile within 32 blocks Nabu will fight: never a squadmate, never someone Nabu protects. */
	public static LivingEntity target(ServerPlayer p) {
		double r = DoctorFateConfig.POSSESSION_RANGE;
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> e != p && e.distanceTo(p) <= r && !Squads.areAllies(p, e) && !DoctorFateChaos.isProtected(e)
						&& DoctorFateSpells.targetable(p, e, true) && (HeroTargets.isHostile(p, e) || DoctorFateChaos.isChaos(e)))
				.stream().max(Comparator.comparingDouble((LivingEntity e) -> e.getMaxHealth()).thenComparingDouble(e -> -e.distanceTo(p)))
				.orElse(null);
	}

	private static void drive(ServerPlayer p, FateState s, long now) {
		LivingEntity t = target(p);
		float yaw = p.getYRot(), pitch = 0f, forward = 0f, strafe = 0f;
		boolean up = false, down = false;
		if (!s.flying) {
			DoctorFateFlight.start(p);
		}
		if (t != null) {
			Vec3 eye = p.getEyePosition();
			Vec3 tc = t.getBoundingBox().getCenter();
			Vec3 look = tc.subtract(eye);
			yaw = (float) (Mth.atan2(-look.x, look.z) * (180.0 / Math.PI));
			pitch = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * (180.0 / Math.PI));
			// hover 6 blocks off it and 3.5 above
			Vec3 away = p.position().subtract(t.position()).multiply(1, 0, 1);
			away = away.lengthSqr() < 1.0e-4 ? new Vec3(1, 0, 0) : away.normalize();
			Vec3 want = t.position().add(away.scale(6.0)).add(0, 3.5, 0);
			Vec3 d = want.subtract(p.position());
			double yr = Math.toRadians(yaw);
			Vec3 fwd = new Vec3(-Math.sin(yr), 0, Math.cos(yr));
			Vec3 left = new Vec3(fwd.z, 0, -fwd.x);
			forward = (float) Mth.clamp(d.dot(fwd) / 3.0, -1.0, 1.0);
			strafe = (float) Mth.clamp(d.dot(left) / 3.0, -1.0, 1.0);
			up = d.y > 0.6;
			down = d.y < -0.6;
			p.setYRot(yaw);
			p.setXRot(pitch);
			p.setYHeadRot(yaw);
			int every = DoctorFateConfig.POSSESSION_CAST_EVERY;
			if ((s.possessedUntil - now) % every == 0) {
				cast(p, t);
			}
		}
		DoctorFate.send(p, new FatePossessPayload(s.possessedUntil, forward, strafe, up, down, yaw, pitch));
		if (now % 3 == 0) {
			((ServerLevel) p.level()).sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.3f), p.getX(), p.getY() + 1, p.getZ(), 3, 0.4, 0.8, 0.4, 0.0);
		}
	}

	/** Nabu's own spell, in turn: Ankh Bolt, Golden Chains, Ankh Bolt, then Order Blast up close or a Glyph Storm on a pack. */
	private static void cast(ServerPlayer p, LivingEntity t) {
		int step = CAST_STEP.merge(p.getUUID(), 1, Integer::sum);
		String id = switch (step % 4) {
			case 1 -> DoctorFateAbilityManager.ANKH_BOLT;
			case 2 -> DoctorFateAbilityManager.GOLDEN_CHAINS;
			case 3 -> DoctorFateAbilityManager.ANKH_BOLT;
			default -> t.distanceTo(p) <= DoctorFateConfig.BLAST_RANGE ? DoctorFateAbilityManager.ORDER_BLAST : DoctorFateAbilityManager.GLYPH_STORM;
		};
		DoctorFateSpells.castSpell(p, id, t, true, true);
	}

	/** Tests: one Possession tick now. */
	public static void tickForTest(ServerPlayer p) {
		tick(p);
	}

	/** Tests: cast Nabu's next spell at {@code t} now. */
	public static void castNow(ServerPlayer p, LivingEntity t) {
		cast(p, t);
	}
}
