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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Doctor Fate: Possession. At 100 Dominion with the helmet on, Nabu takes the body: he flies it and casts on his own at the
 * strongest foe nearby (huge damage), never a squadmate. On a server nobody is possessed inside spawn protection (Dominion waits at
 * 99 until they leave).
 *
 * <p>v0.17.4 round 2 (user: "when nabu takes control of you there is no time limit you have to convince him to let you go ... in this
 * mode the player is twice as strong ... also nabu attack nearby players even if they have commited bad actions in the past, player
 * cant die in this state"):
 * <ul>
 *   <li><b>No time limit</b>: it ends only when the player wins the trial in Nabu's library ({@link DoctorFateNabuTrial}); Space no
 *   longer shortens it (it's the willpower test's push now).</li>
 *   <li><b>Twice as strong</b>: his spells already hit x{@link DoctorFateConfig#POSSESSION_DAMAGE_MULT}; the body's melee is doubled
 *   too ({@link DoctorFate#reconcile}).</li>
 *   <li><b>Every player</b> near him is a target (no grudge needed), with hostile mobs and creatures of Chaos; still never a
 *   squadmate, a villager, a golem or a tamed animal.</li>
 *   <li><b>He can't die</b>: {@code DoctorFateDamage} holds him at 1 health (only /kill gets through), and if the body falls out of the
 *   world Nabu lifts it back to the last ground it stood on.</li>
 * </ul>
 * Afterward the helmet falls off by itself (it lands as the hovering helmet) and can't go back on for 60 s, and Dominion settles at 70.
 *
 * <p>The drive is the Ghostfreak pattern: the server streams the body's movement keys and look to the possessed client every
 * tick ({@link FatePossessPayload}); the client plays them as its own input, so the flight stays smooth.
 */
public final class DoctorFatePossession {
	private static final Map<UUID, Integer> CAST_STEP = new ConcurrentHashMap<>();
	/** The last spot the body stood on solid ground while possessed (a position only, never a world). */
	private static final Map<UUID, Vec3> LAST_GROUND = new ConcurrentHashMap<>();

	private DoctorFatePossession() {
	}

	static void clearSessionState() {
		CAST_STEP.clear();
		LAST_GROUND.clear();
	}

	static void forget(UUID id) {
		CAST_STEP.remove(id);
		LAST_GROUND.remove(id);
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
				end(p); // (only an old save's timed Possession can still run out)
				return;
			}
			keepOutOfTheVoid(p);
			drive(p, s, now);
			DoctorFateNabuTrial.tick(p);
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

	/** Nabu takes the body, with no end time: Kent's mind goes to the library. */
	public static void start(ServerPlayer p) {
		FateState n = DoctorFate.state(p).copy();
		n.possessedUntil = DoctorFateConfig.POSSESSED_FOREVER;
		n.shieldUp = false;
		n.dominion = DoctorFateConfig.DOMINION_MAX;
		n.bargain = n.bargain.withPending(FateState.Bargain.NONE_KIND, 0L);
		DoctorFate.save(p, n);
		CAST_STEP.put(p.getUUID(), 0);
		LAST_GROUND.put(p.getUUID(), p.position());
		DoctorFateFlight.start(p);
		DoctorFate.reconcile(p);
		DoctorFateMindscape.close(p);
		ServerLevel level = (ServerLevel) p.level();
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 2.0f), p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 1.2, 0.6, 0.0);
		DoctorFate.sound(level, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.35f, 0.7f);
		p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.possessed_subtitle").withStyle(ChatFormatting.GOLD), false);
		DoctorFateNabuTrial.begin(p);
	}

	/**
	 * Space held / let go while possessed: the willpower test's hold state (nothing else; v0.17.4 round 2 rebuild, the client sends it
	 * when it changes). True if it counted.
	 */
	public static boolean struggle(ServerPlayer p, boolean down) {
		return DoctorFate.possessed(p) && DoctorFateNabuTrial.hold(p, down);
	}

	/** Space held down (tests). */
	public static boolean struggle(ServerPlayer p) {
		return struggle(p, true);
	}

	/** Nabu lets go: the helmet falls off, 60 s lockout, Dominion 70. v0.17.4 round 2: no Slow Falling any more (no fall damage instead). */
	public static void end(ServerPlayer p) {
		long now = p.level().getGameTime();
		FateState n = DoctorFate.state(p).copy();
		n.possessedUntil = 0L;
		n.lockoutUntil = now + DoctorFateConfig.LOCKOUT_TICKS;
		n.dominion = DoctorFateConfig.AFTER_POSSESSION_DOMINION;
		boolean wasOn = n.helmetOn;
		DoctorFate.save(p, n);
		DoctorFate.setHelmet(p, false);
		if (wasOn) {
			DoctorFateHelmet.fallOff(p); // v0.17.4: it lands as the Helmet of Fate on the ground (H summons it back after the lockout)
		}
		DoctorFateFlight.stop(p);
		DoctorFateNabuTrial.end(p);
		release(p);
		CAST_STEP.remove(p.getUUID());
		LAST_GROUND.remove(p.getUUID());
		DoctorFate.reconcile(p);
		DoctorFate.sound(p, SoundEvents.ARMOR_EQUIP_GOLD.value(), 0.7f, 0.6f);
		((ServerLevel) p.level()).sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.2f), p.getX(), p.getEyeY(), p.getZ(), 20, 0.3, 0.3, 0.3, 0.0);
		p.displayClientMessage(Component.translatable("message.projecthero.doctor_fate.released").withStyle(ChatFormatting.GOLD), false);
	}

	/** Tells the client it's its own again. */
	static void release(ServerPlayer p) {
		DoctorFate.send(p, FatePossessPayload.RELEASE);
	}

	/**
	 * Who Nabu fights, within 32 blocks: every player (v0.17.4 round 2: whatever they have or haven't done), hostile mobs and creatures
	 * of Chaos; never a squadmate, never someone Nabu protects. Players first, then the strongest.
	 */
	public static LivingEntity target(ServerPlayer p) {
		double r = DoctorFateConfig.POSSESSION_RANGE;
		return p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> e != p && e.distanceTo(p) <= r && !Squads.areAllies(p, e) && !DoctorFateChaos.isProtected(e)
						&& DoctorFateSpells.targetable(p, e, true)
						&& (e instanceof Player || HeroTargets.isHostile(p, e) || DoctorFateChaos.isChaos(e)))
				.stream().max(Comparator.comparingInt((LivingEntity e) -> e instanceof Player ? 1 : 0)
						.thenComparingDouble(e -> e.getMaxHealth()).thenComparingDouble(e -> -e.distanceTo(p)))
				.orElse(null);
	}

	/** v0.17.4 round 2: the body never falls out of the world while Nabu holds it. */
	private static void keepOutOfTheVoid(ServerPlayer p) {
		ServerLevel level = (ServerLevel) p.level();
		if (p.onGround()) {
			LAST_GROUND.put(p.getUUID(), p.position());
		}
		if (p.getY() > level.getMinBuildHeight() - 2) {
			return;
		}
		Vec3 back = LAST_GROUND.get(p.getUUID());
		if (back == null || back.y < level.getMinBuildHeight()) {
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, p.blockPosition());
			if (top.getY() <= level.getMinBuildHeight()) {
				top = level.getSharedSpawnPos();
			}
			back = Vec3.atBottomCenterOf(top);
		}
		p.teleportTo(back.x, back.y + 1.0, back.z);
		p.setDeltaMovement(Vec3.ZERO);
		p.resetFallDistance();
		level.sendParticles(DoctorFate.dust(DoctorFate.GOLD, 1.6f), back.x, back.y + 1.5, back.z, 30, 0.5, 1.0, 0.5, 0.0);
		DoctorFate.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.8f);
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
			if (now % DoctorFateConfig.POSSESSION_CAST_EVERY == 0) {
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

	/** v0.17.4 round 2: Nabu's wrath in the library: the body lashes out at once (an Order Blast or a Glyph Storm at its foe). */
	static void lashOut(ServerPlayer p) {
		LivingEntity t = target(p);
		if (t == null) {
			return;
		}
		DoctorFateSpells.castSpell(p, t.distanceTo(p) <= DoctorFateConfig.BLAST_RANGE ? DoctorFateAbilityManager.ORDER_BLAST
				: DoctorFateAbilityManager.GLYPH_STORM, t, true, true);
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
