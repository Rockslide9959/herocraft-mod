package com.projecthero.mod.shazam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.quirk.fx.QuirkFx;
import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.shazam.network.ShazamFxPayload;
import com.projecthero.mod.squad.Squads;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam, Planet tier: every move of the S-H-A-Z-A-M kit (keys in {@link ShazamAbilityManager}, numbers in
 * {@link ShazamConfig}). Damage goes through {@link Shazam#hit} (a family member's halved there) and then {@link ShazamCombat} (the
 * Wisdom crit, the 40 cap, the boss cap); areas never touch him or a squadmate ({@link Shazam#areaVictim}); bosses are hit but never
 * lifted, launched or knocked about.
 *
 * <ul>
 *   <li><b>R, the Strength of Hercules</b>: Hercules Combo (17 / 17 / 19, the third knocks back), Shift Herculean Uppercut (18, launches),
 *   Hold Haymaker of Hercules (charges 1.5 s, 16 -> 20, a shockwave of 16 round the impact).</li>
 *   <li><b>G, the Power of Zeus</b>: Lightning of Zeus (a bolt from the sky 18, chaining to 2 more for 16), Shift Thunderhead Slam (18
 *   ring), Hold Zeus's Wrath (a beam from both hands, 16 every quarter second, reports to {@code BeamClash} every live tick).</li>
 *   <li><b>C, the Speed of Mercury</b>: Mercury Blitz (through up to 3 in a line, 16 each), Shift Thunderclap (16 cone), Hold Mercury
 *   Rush (hops between the enemies round him, 5 hits of 16).</li>
 *   <li><b>Z, the Stamina of Atlas</b>: Atlas Quake (19 within 5), Hold Titan's Grip (overhead, hurled for 20), Shift "SHAZAM!"
 *   Thunderstrike, the ultimate (40 within 8, then 8 s of chain lightning on every hit).</li>
 *   <li><b>X</b>: Mercury Dash (flying: Mercury Burst), Shift Sonic Leap (a huge leap into flight).</li>
 *   <li><b>V, the Wisdom of Solomon</b> (enemies within 30 glow, his hits crit x1.25), Shift <b>Courage of Achilles</b> (Resistance II, no
 *   knockback), Hold Share the Power.</li>
 * </ul>
 *
 * <p>Per player there is a small server session (the combo step, the held creature, creatures in flight, the delayed slams, the beam,
 * the rush, the ultimate's beats). It holds entity ids, never entities, and is dropped by {@code ServerStateReset} through
 * {@link #clearSessionState}.
 */
public final class ShazamMoves {
	/** A creature in flight from his throw or punch: it takes {@code damage} when it hits a wall (or, thrown, the ground). */
	private record Flight(long until, float damage, boolean groundToo, long since) {
	}

	private static final class Session {
		int comboStep;
		long comboAt = Long.MIN_VALUE / 2;
		int lifted = -1;
		long liftUntil;
		final Map<Integer, Flight> flying = new HashMap<>();
		long fallSafeUntil;
		/** Thunderhead Slam: lands at this time (0 none); diving while airborne. */
		long slamAt;
		boolean slamDiving;
		long slamDiveUntil;
		/** Atlas Quake: the stomp lands at this time (0 none). */
		long quakeAt;
		/** Sonic Leap: flight starts at this time (0 none). */
		long leapAt;
		boolean ultBolted;
		long wrathNext;
		int rushHits;
		long rushNext;
		int rushLast = -1;
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ShazamMoves() {
	}

	static void clearSessionState() {
		SESSIONS.clear();
	}

	static void forget(UUID id) {
		SESSIONS.remove(id);
		ShazamAbilityManager.forget(id);
		ShazamCombat.forget(id);
	}

	private static Session session(ServerPlayer p) {
		return SESSIONS.computeIfAbsent(p.getUUID(), k -> new Session());
	}

	private static long now(ServerPlayer p) {
		return p.level().getGameTime();
	}

	/** No fall damage just after a dash, a leap, a slam or the Thunderstrike. */
	public static boolean fallSafe(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.fallSafeUntil > now(p);
	}

	private static void fallSafe(ServerPlayer p, int ticks) {
		session(p).fallSafeUntil = Math.max(session(p).fallSafeUntil, now(p) + ticks);
		p.resetFallDistance();
	}

	/** The creature Titan's Grip is holding up (null when none). For the tests and the HUD. */
	public static Entity lifted(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.lifted < 0 ? null : p.level().getEntity(s.lifted);
	}

	/** Which punch of the Hercules Combo comes next (0, 1, 2). For the tests. */
	public static int comboStep(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || now(p) - s.comboAt > ShazamConfig.COMBO_WINDOW ? 0 : s.comboStep;
	}

	/** Mercury Rush's hits so far. For the tests. */
	public static int rushHits(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? 0 : s.rushHits;
	}

	/** The hold move running (a {@code ShazamAnim.HOLD_*}). Client-safe. */
	public static int holding(Player p) {
		ShazamState s = Shazam.peek(p);
		return s == null || !s.transformed ? ShazamAnim.HOLD_NONE : s.hold;
	}

	// ---------------------------------------------------------------- the synced body clip and hold

	static void anim(ServerPlayer p, int anim) {
		ShazamState n = Shazam.state(p).copy();
		n.anim = anim;
		n.animStart = now(p);
		Shazam.save(p, n);
	}

	private static void setHold(ServerPlayer p, int hold, long start) {
		ShazamState n = Shazam.state(p).copy();
		n.hold = hold;
		n.holdStart = start;
		Shazam.save(p, n);
	}

	private static void endHold(ServerPlayer p) {
		if (Shazam.state(p).hold != ShazamAnim.HOLD_NONE) {
			setHold(p, ShazamAnim.HOLD_NONE, 0L);
		}
	}

	// ---------------------------------------------------------------- dispatch

	/** Fires a tap or Shift move. True if it went off (cooldown and cost taken). */
	public static boolean use(ServerPlayer p, String id) {
		if (!ready(p, id)) {
			return false;
		}
		return switch (id) {
			case ShazamAbilityManager.HERCULES_COMBO -> combo(p);
			case ShazamAbilityManager.UPPERCUT -> uppercut(p);
			case ShazamAbilityManager.ZEUS_LIGHTNING -> zeusLightning(p);
			case ShazamAbilityManager.THUNDERHEAD_SLAM -> thunderheadSlam(p);
			case ShazamAbilityManager.MERCURY_BLITZ -> mercuryBlitz(p);
			case ShazamAbilityManager.THUNDERCLAP -> thunderclap(p);
			case ShazamAbilityManager.ATLAS_QUAKE -> atlasQuake(p);
			case ShazamAbilityManager.THUNDERSTRIKE -> thunderstrike(p);
			case ShazamAbilityManager.MERCURY_DASH -> Shazam.isFlying(p) ? mercuryBurst(p) : mercuryDash(p);
			case ShazamAbilityManager.SONIC_LEAP -> sonicLeap(p);
			case ShazamAbilityManager.WISDOM -> wisdom(p);
			case ShazamAbilityManager.COURAGE -> courage(p);
			case ShazamAbilityManager.SHARE_POWER -> ShazamFamily.shareAimed(p);
			case ShazamAbilityManager.HAYMAKER, ShazamAbilityManager.ZEUS_WRATH, ShazamAbilityManager.MERCURY_RUSH,
					ShazamAbilityManager.TITANS_GRIP -> holdStart(p, id, now(p));
			default -> false;
		};
	}

	/** Transformed, allowed (family), off cooldown, no ultimate running. */
	private static boolean ready(ServerPlayer p, String id) {
		if (!Shazam.transformed(p)) {
			return false;
		}
		if (ShazamAbilityManager.championOnly(id) && Shazam.isFamily(p)) {
			Shazam.say(p, "message.projecthero.shazam.champion_only", ChatFormatting.GRAY);
			return false;
		}
		return Shazam.cooldownRemaining(p, id) <= 0 && Shazam.eternityAge(p) < 0;
	}

	/** A key's hold move begins ({@code downAt}: when the key went down). True if it started. */
	public static boolean holdStart(ServerPlayer p, String id, long downAt) {
		if (!ready(p, id) || holding(p) != ShazamAnim.HOLD_NONE) {
			return false;
		}
		return switch (id) {
			case ShazamAbilityManager.HAYMAKER -> haymakerStart(p, downAt);
			case ShazamAbilityManager.ZEUS_WRATH -> wrathStart(p, downAt);
			case ShazamAbilityManager.MERCURY_RUSH -> rushStart(p, downAt);
			case ShazamAbilityManager.TITANS_GRIP -> gripStart(p, downAt);
			case ShazamAbilityManager.SHARE_POWER -> ShazamFamily.shareAimed(p);
			default -> false;
		};
	}

	/** A key's hold move is let go. */
	public static void holdRelease(ServerPlayer p, String id) {
		int h = holding(p);
		switch (id) {
			case ShazamAbilityManager.HAYMAKER -> {
				if (h == ShazamAnim.HOLD_HAYMAKER) {
					haymakerRelease(p);
				}
			}
			case ShazamAbilityManager.ZEUS_WRATH -> {
				if (h == ShazamAnim.HOLD_WRATH) {
					wrathEnd(p);
				}
			}
			case ShazamAbilityManager.MERCURY_RUSH -> {
				if (h == ShazamAnim.HOLD_RUSH) {
					rushEnd(p);
				}
			}
			case ShazamAbilityManager.TITANS_GRIP -> {
				if (h == ShazamAnim.HOLD_GRIP) {
					hurl(p);
				}
			}
			default -> {
			}
		}
	}

	// ---------------------------------------------------------------- aiming

	/** Where his look meets a block within {@code range} (or the end of the range). */
	static Vec3 lookEnd(ServerPlayer p, double range) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = eye.add(p.getViewVector(1f).scale(range));
		BlockHitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** The first creature along his look within {@code range} (blocks in the way stop it), boxes grown by {@code pad}. */
	static LivingEntity aimed(ServerPlayer p, double range, double pad, Predicate<LivingEntity> filter) {
		Vec3 eye = p.getEyePosition();
		Vec3 end = lookEnd(p, range);
		AABB sweep = new AABB(eye, end).inflate(pad + 1.0);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, sweep, e -> e != p && e.isAlive() && !e.isSpectator() && filter.test(e))) {
			AABB box = e.getBoundingBox().inflate(pad);
			Vec3 at = box.contains(eye) ? eye : box.clip(eye, end).orElse(null);
			if (at == null) {
				continue;
			}
			double d = eye.distanceToSqr(at);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** Something his moves may hit on purpose (not a squadmate, not protected). */
	private static boolean foe(ServerPlayer p, LivingEntity e) {
		return Shazam.areaVictim(p, e);
	}

	private static LivingEntity target(ServerPlayer p, double reach) {
		return aimed(p, reach, 0.4, e -> foe(p, e));
	}

	private static Vec3 flatLook(ServerPlayer p) {
		Vec3 look = p.getViewVector(1f);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : flat.normalize();
	}

	/** Knocks {@code e} along {@code dir} (flat) and up; bosses are never moved. */
	private static void knock(LivingEntity e, Vec3 dir, double horizontal, double up) {
		if (ShazamCombat.isBoss(e) || !e.isAlive()) {
			return;
		}
		Vec3 flat = new Vec3(dir.x, 0, dir.z);
		flat = flat.lengthSqr() < 1.0e-4 ? Vec3.ZERO : flat.normalize().scale(horizontal);
		e.setDeltaMovement(e.getDeltaMovement().scale(0.3).add(flat).add(0, up, 0));
		e.hurtMarked = true;
		e.hasImpulse = true;
	}

	private static Vec3 away(Vec3 from, LivingEntity e) {
		Vec3 d = e.position().subtract(from);
		d = new Vec3(d.x, 0, d.z);
		return d.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : d.normalize();
	}

	/** Every foe within {@code radius} of {@code c} (a squat cylinder, 3 blocks up and down). */
	private static List<LivingEntity> around(ServerPlayer p, Vec3 c, double radius) {
		return p.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius, 3.0, radius), e -> {
			if (!foe(p, e)) {
				return false;
			}
			double dx = e.getX() - c.x, dz = e.getZ() - c.z;
			return dx * dx + dz * dz <= radius * radius;
		});
	}

	// ---------------------------------------------------------------- effects

	/** A jagged lightning arc from {@code a} to {@code b}. */
	static void arc(ServerLevel level, Vec3 a, Vec3 b) {
		ShazamFx.arc(level, a, b, 1f);
		level.playSound(null, b.x, b.y, b.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.25f, 1.7f + level.random.nextFloat() * 0.2f);
	}

	/** One of his hands in front of him ({@code right}: the right hand). */
	static Vec3 hand(ServerPlayer p, boolean right) {
		Vec3 look = p.getViewVector(1f);
		float yaw = p.getYRot() * ((float) Math.PI / 180f);
		Vec3 side = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
		return p.getEyePosition().add(0, -0.45, 0).add(look.scale(0.55)).add(side.scale(right ? 0.38 : -0.38));
	}

	private static void crackle(ServerPlayer p, float volume, float pitch) {
		Shazam.sound(p, ShazamSounds.CRACKLE, volume, pitch);
	}

	private static boolean griefing(ServerLevel level) {
		return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
	}

	/** A punch landing: a gold-white shock ring standing up at the fist, crit sparks, a little gold dust. */
	private static void punchFx(ServerLevel level, Vec3 at, Vec3 dir, float size) {
		QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFFC93A).size(1.4f + 1.4f * size).thickness(0.22f).life(7).vec(dir)
				.flags(QuirkFx.F_VERTICAL).ease(QuirkFx.Ease.OUT_EXPO).spawn();
		ShazamFx.send(level, ShazamFxPayload.IMPACT, at, dir, size, 6, -1);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, (int) (6 + 6 * size), 0.2, 0.2, 0.2, 0.35);
		level.sendParticles(Shazam.dust(Shazam.GOLD, 1.1f), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.0);
	}

	/** A ground slam: glowing cracks, a gold shock ring, scorch, sparks, the block's debris and a camera shake. */
	private static void groundFx(ServerPlayer p, Vec3 c, double radius, float strength) {
		ServerLevel level = p.serverLevel();
		ShazamFx.send(level, ShazamFxPayload.CRACK, c.add(0, 0.05, 0), p.getLookAngle(), (float) radius, 36, -1);
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xFFC93A).size((float) radius * 2.3f).thickness(0.45f).life(12).ease(QuirkFx.Ease.OUT_EXPO)
				.ground().spawn();
		QuirkFx.decal(level, c).style(QuirkFx.DECAL_CRACK).colour(0x1A1814).size((float) radius * 0.7f).life(160)
				.yaw(level.random.nextFloat() * 360f).spawn();
		BlockState under = level.getBlockState(BlockPos.containing(c).below());
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.2, c.z, (int) (30 * strength), radius * 0.4, 0.15,
					radius * 0.4, 0.25);
			QuirkFx.shards(level, c.add(0, 0.2, 0)).block(under).size((float) radius * 0.6f).count((int) (10 + 10 * strength)).life(26).spawn();
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.3, c.z, (int) (40 * strength), radius * 0.4, 0.3, radius * 0.4, 0.35);
		shake(level, c, 0.35f + 0.45f * strength, 10 + (int) (8 * strength));
	}

	/** The camera shake for players near {@code at} (the Titan tremor packet, purely cosmetic). */
	static void shake(ServerLevel level, Vec3 at, float intensity, int ticks) {
		double r = 24.0;
		for (ServerPlayer pl : level.players()) {
			double d2 = pl.distanceToSqr(at);
			if (d2 <= r * r && pl.connection != null && ServerPlayNetworking.canSend(pl, com.projecthero.mod.network.TitanShakePayload.TYPE)) {
				float falloff = (float) (1.0 - Math.sqrt(d2) / r * 0.8);
				ServerPlayNetworking.send(pl, new com.projecthero.mod.network.TitanShakePayload(intensity * falloff, ticks));
			}
		}
	}

	private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent s, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, volume, pitch);
	}

	// ================================================================ R: Hercules Combo

	public static boolean combo(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		int step = now - s.comboAt <= ShazamConfig.COMBO_WINDOW ? s.comboStep : 0;
		LivingEntity t = target(p, ShazamConfig.COMBO_REACH);
		ServerLevel level = p.serverLevel();
		anim(p, ShazamAnim.COMBO_1 + step);
		p.swing(step == 1 ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, true);
		if (t == null) {
			s.comboStep = 0;
			s.comboAt = now;
			Shazam.sound(p, SoundEvents.PLAYER_ATTACK_SWEEP, 0.5f, 1.4f);
			Shazam.startCooldown(p, ShazamAbilityManager.HERCULES_COMBO, ShazamConfig.COMBO_GAP);
			return true;
		}
		float dmg = step == 0 ? ShazamConfig.COMBO_DAMAGE_1 : step == 1 ? ShazamConfig.COMBO_DAMAGE_2 : ShazamConfig.COMBO_DAMAGE_3;
		Shazam.hit(p, t, dmg, false);
		Vec3 dir = flatLook(p);
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0).subtract(dir.scale(t.getBbWidth() * 0.5));
		if (step == 2) {
			knock(t, dir, ShazamConfig.COMBO_FINISH_KNOCKBACK, 0.35);
			punchFx(level, at, dir, 1.4f);
			level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
			sound(level, at, SoundEvents.MACE_SMASH_AIR, 0.8f, 0.9f);
			sound(level, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
			sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.3f, 1.6f);
			shake(level, at, 0.3f, 6);
			s.comboStep = 0;
			Shazam.startCooldown(p, ShazamAbilityManager.HERCULES_COMBO, ShazamConfig.COMBO_FINISH_COOLDOWN);
		} else {
			knock(t, dir, 0.25, 0.08);
			punchFx(level, at, dir, 0.7f);
			sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, step == 0 ? 0.8f : 0.7f);
			s.comboStep = step + 1;
			Shazam.startCooldown(p, ShazamAbilityManager.HERCULES_COMBO, ShazamConfig.COMBO_GAP);
		}
		s.comboAt = now;
		return true;
	}

	// ================================================================ Shift+R: Herculean Uppercut

	public static boolean uppercut(ServerPlayer p) {
		LivingEntity t = target(p, ShazamConfig.COMBO_REACH);
		if (t == null) {
			Shazam.say(p, "message.projecthero.shazam.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Shazam.spend(p, ShazamConfig.UPPERCUT_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		anim(p, ShazamAnim.UPPERCUT);
		p.swing(InteractionHand.MAIN_HAND, true);
		Shazam.hit(p, t, ShazamConfig.UPPERCUT_DAMAGE, false);
		if (!ShazamCombat.isBoss(t)) {
			t.setDeltaMovement(t.getDeltaMovement().x * 0.2, ShazamConfig.UPPERCUT_LAUNCH, t.getDeltaMovement().z * 0.2);
			t.hurtMarked = true;
			t.hasImpulse = true;
		}
		Vec3 base = t.position().add(0, t.getBbHeight() * 0.3, 0);
		punchFx(level, base.add(0, 0.3, 0), new Vec3(0, 1, 0), 1.1f);
		QuirkFx.ribbon(level, base, base.add(0, 4.5, 0)).style(QuirkFx.RIBBON_LIGHT).colours(0xFFFFFF, 0xFFC93A).size(0.35f).life(9).spawn();
		ShazamFx.arc(level, base, base.add(0.3, 3.5, -0.2), 0.8f);
		level.sendParticles(ParticleTypes.CLOUD, base.x, base.y, base.z, 8, 0.3, 0.1, 0.3, 0.05);
		sound(level, base, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.55f);
		sound(level, base, SoundEvents.WIND_CHARGE_BURST.value(), 0.6f, 1.2f);
		shake(level, base, 0.25f, 6);
		Shazam.startCooldown(p, ShazamAbilityManager.UPPERCUT, ShazamConfig.UPPERCUT_COOLDOWN);
		return true;
	}

	// ================================================================ Hold R: Haymaker of Hercules

	private static boolean haymakerStart(ServerPlayer p, long downAt) {
		if (!Shazam.hasLightning(p, ShazamConfig.HAYMAKER_COST)) {
			Shazam.say(p, "message.projecthero.shazam.no_lightning", ChatFormatting.GRAY);
			return false;
		}
		setHold(p, ShazamAnim.HOLD_HAYMAKER, downAt);
		Shazam.sound(p, SoundEvents.BREEZE_INHALE, 0.6f, 0.7f);
		crackle(p, 0.4f, 0.7f);
		return true;
	}

	/** The Haymaker's charge 0..1 right now (1.5 s of charging after the 8-tick hold). */
	public static float haymakerCharge(Player p) {
		ShazamState s = Shazam.peek(p);
		if (s == null || s.hold != ShazamAnim.HOLD_HAYMAKER) {
			return 0f;
		}
		long held = p.level().getGameTime() - s.holdStart - ShazamConfig.HOLD_TICKS;
		return Math.max(0f, Math.min(1f, held / (float) ShazamConfig.HAYMAKER_CHARGE_TICKS));
	}

	/** The Haymaker's damage at charge {@code f}: 16 -> 20. */
	public static float haymakerDamage(float f) {
		return ShazamConfig.HAYMAKER_MIN + (ShazamConfig.HAYMAKER_MAX - ShazamConfig.HAYMAKER_MIN) * Math.max(0f, Math.min(1f, f));
	}

	/** Let go (or held 3 s): the punch, then the shockwave round the impact. Returns how many it hit. */
	public static int haymakerRelease(ServerPlayer p) {
		float f = haymakerCharge(p);
		endHold(p);
		if (!Shazam.spend(p, ShazamConfig.HAYMAKER_COST)) {
			return 0;
		}
		ServerLevel level = p.serverLevel();
		anim(p, ShazamAnim.HAYMAKER);
		p.swing(InteractionHand.MAIN_HAND, true);
		Vec3 dir = flatLook(p);
		LivingEntity t = aimed(p, ShazamConfig.HAYMAKER_REACH, 0.5, e -> foe(p, e));
		Vec3 at;
		int n = 0;
		if (t != null) {
			at = t.position().add(0, t.getBbHeight() * 0.55, 0);
			if (Shazam.hit(p, t, haymakerDamage(f), false)) {
				n++;
			}
			knock(t, dir, 0.9 + 1.4 * f, 0.3 + 0.25 * f);
		} else {
			at = p.getEyePosition().add(p.getViewVector(1f).scale(2.2));
		}
		for (LivingEntity e : around(p, at, ShazamConfig.HAYMAKER_SHOCK_RADIUS)) {
			if (e == t) {
				continue;
			}
			if (Shazam.hit(p, e, ShazamConfig.HAYMAKER_SHOCK_DAMAGE, false)) {
				n++;
			}
			knock(e, away(at, e), 0.8 + 0.6 * f, 0.35);
		}
		// the release: a standing shock ring, a clean gold blast, lightning bursting round the fist, the sonic crack
		QuirkFx.ring(level, at).colours(0xFFFFFF, 0xFFC93A).size(4f + 4f * f).thickness(0.4f).life(10).vec(dir).flags(QuirkFx.F_VERTICAL)
				.ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.blast(level, at).colours(0xFFFFFF, 0xFFC93A).size(1.6f + 1.6f * f).life(10).flags(QuirkFx.F_NO_SMOKE | QuirkFx.F_NO_RING).spawn();
		ShazamFx.burst(level, at.subtract(0, 0.8, 0), 0.8f + f);
		ShazamFx.send(level, ShazamFxPayload.IMPACT, at, dir, 1.8f + f, 8, -1);
		level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.4);
		sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 0.5f);
		sound(level, at, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.7f + 0.3f * f, 0.9f);
		sound(level, at, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.8f);
		sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.45f, 1.3f);
		shake(level, at, 0.45f + 0.5f * f, 12);
		Shazam.startCooldown(p, ShazamAbilityManager.HAYMAKER, ShazamConfig.HAYMAKER_COOLDOWN);
		return n;
	}

	// ================================================================ G: Lightning of Zeus

	public static boolean zeusLightning(ServerPlayer p) {
		LivingEntity first = aimed(p, ShazamConfig.ZEUS_RANGE, 0.6, e -> foe(p, e));
		if (!Shazam.spend(p, ShazamConfig.ZEUS_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		anim(p, ShazamAnim.ZEUS_CALL);
		if (first == null) {
			Vec3 at = lookEnd(p, ShazamConfig.ZEUS_RANGE);
			Shazam.bolt(level, at);
			for (LivingEntity e : around(p, at, ShazamConfig.ZEUS_SPLASH)) {
				Shazam.hit(p, e, ShazamConfig.ZEUS_DAMAGE, true);
			}
		} else {
			Shazam.bolt(level, first.position());
			Shazam.hit(p, first, ShazamConfig.ZEUS_DAMAGE, true);
			List<LivingEntity> struck = new ArrayList<>();
			struck.add(first);
			LivingEntity from = first;
			for (int i = 0; i < ShazamConfig.ZEUS_CHAINS; i++) {
				LivingEntity next = nearestChain(p, from, struck);
				if (next == null) {
					break;
				}
				arc(level, from.position().add(0, from.getBbHeight() * 0.6, 0), next.position().add(0, next.getBbHeight() * 0.5, 0));
				Shazam.hit(p, next, ShazamConfig.ZEUS_CHAIN_DAMAGE, true);
				struck.add(next);
				from = next;
			}
		}
		crackle(p, 0.6f, 0.9f);
		Shazam.startCooldown(p, ShazamAbilityManager.ZEUS_LIGHTNING, ShazamConfig.ZEUS_COOLDOWN);
		return true;
	}

	/** The next creature a chain jumps to: the nearest threat within 8 blocks of {@code from} not struck yet. */
	private static LivingEntity nearestChain(ServerPlayer p, LivingEntity from, List<LivingEntity> struck) {
		double r = ShazamConfig.ZEUS_CHAIN_RANGE;
		LivingEntity best = null;
		double bestD = r * r;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, from.getBoundingBox().inflate(r),
				e -> !struck.contains(e) && foe(p, e) && HeroTargets.isHostile(p, e))) {
			double d = e.distanceToSqr(from);
			if (d <= bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	// ================================================================ Shift+G: Thunderhead Slam

	public static boolean thunderheadSlam(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.SLAM_COST)) {
			return false;
		}
		Session s = session(p);
		long now = now(p);
		anim(p, ShazamAnim.SLAM);
		if (p.onGround()) {
			s.slamAt = now + ShazamConfig.SLAM_WINDUP;
			s.slamDiving = false;
			Shazam.sound(p, SoundEvents.BREEZE_INHALE, 0.5f, 0.6f);
		} else {
			ShazamFlight.stop(p);
			s.slamDiving = true;
			s.slamDiveUntil = now + 60;
			s.slamAt = 0L;
		}
		// lightning gathers on his fists as he raises them
		ShazamFx.send(p.serverLevel(), ShazamFxPayload.AURA, p.position(), p.position(), 0.6f, ShazamConfig.SLAM_WINDUP + 6, p.getId());
		crackle(p, 0.5f, 0.8f);
		fallSafe(p, 80);
		Shazam.startCooldown(p, ShazamAbilityManager.THUNDERHEAD_SLAM, ShazamConfig.SLAM_COOLDOWN);
		return true;
	}

	/** The slam lands: a bolt onto him and an 18 ring within 5. Returns how many it hit. */
	public static int slamLand(ServerPlayer p) {
		Session s = session(p);
		s.slamAt = 0L;
		s.slamDiving = false;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		int n = 0;
		for (LivingEntity e : around(p, c, ShazamConfig.SLAM_RADIUS)) {
			if (Shazam.hit(p, e, ShazamConfig.SLAM_DAMAGE, true)) {
				n++;
			}
			knock(e, away(c, e), 0.8, 0.6);
		}
		Shazam.bolt(level, c);
		ShazamFx.burst(level, c, 1.6f);
		groundFx(p, c, ShazamConfig.SLAM_RADIUS, 1f);
		sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.8f);
		sound(level, c, SoundEvents.TRIDENT_THUNDER.value(), 0.5f, 1.1f);
		anim(p, ShazamAnim.SLAM);
		ShazamState st = Shazam.state(p).copy();
		st.animStart = now(p) - 7; // straight into the strike half of the clip
		Shazam.save(p, st);
		fallSafe(p, 20);
		return n;
	}

	// ================================================================ Hold G: Zeus's Wrath

	private static boolean wrathStart(ServerPlayer p, long downAt) {
		if (!Shazam.hasLightning(p, ShazamConfig.WRATH_COST)) {
			Shazam.say(p, "message.projecthero.shazam.no_lightning", ChatFormatting.GRAY);
			return false;
		}
		setHold(p, ShazamAnim.HOLD_WRATH, now(p));
		session(p).wrathNext = now(p);
		Shazam.sound(p, SoundEvents.BEACON_ACTIVATE, 0.5f, 1.6f);
		crackle(p, 0.7f, 1.1f);
		return true;
	}

	/** Where the stream starts: between his hands. Client-safe through {@link Shazam#wrathOrigin}. */
	static Vec3 wrathFrom(ServerPlayer p) {
		return hand(p, true).add(hand(p, false)).scale(0.5);
	}

	/** Every tick while streaming: the beam clash report, and a pulse of damage every quarter second. */
	private static void wrathTick(ServerPlayer p) {
		ShazamState st = Shazam.state(p);
		long now = now(p);
		if (now - st.holdStart >= ShazamConfig.WRATH_MAX_TICKS) {
			wrathEnd(p);
			return;
		}
		Vec3 from = wrathFrom(p);
		Vec3 dir = p.getViewVector(1f);
		com.projecthero.mod.beamclash.BeamClash.report(p, from, dir, ShazamConfig.WRATH_RANGE, ShazamConfig.WRATH_CLASH_POWER, 0xFFD24A);
		if (com.projecthero.mod.beamclash.BeamClash.locked(p)) {
			return; // a locked clash draws both beams itself; no drain, no damage meanwhile
		}
		Session s = session(p);
		if (now >= s.wrathNext) {
			s.wrathNext = now + ShazamConfig.WRATH_EVERY;
			if (!wrathPulse(p)) {
				wrathEnd(p);
			}
		}
	}

	/** One pulse of the stream: 4 lightning, 16 to the first creature along it. False when the bar ran dry. For the tests. */
	public static boolean wrathPulse(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.WRATH_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		LivingEntity t = aimed(p, ShazamConfig.WRATH_RANGE, 0.5, e -> foe(p, e));
		Vec3 end = t != null ? t.position().add(0, t.getBbHeight() * 0.55, 0) : lookEnd(p, ShazamConfig.WRATH_RANGE);
		if (t != null) {
			Shazam.hit(p, t, ShazamConfig.WRATH_DAMAGE, true);
			knock(t, p.getViewVector(1f), 0.15, 0.05);
		}
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, end.x, end.y, end.z, 10, 0.25, 0.25, 0.25, 0.3);
		level.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0, 0, 0, 0);
		crackle(p, 0.55f, 0.9f + level.random.nextFloat() * 0.3f);
		sound(level, end, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.25f, 1.8f);
		return true;
	}

	private static void wrathEnd(ServerPlayer p) {
		if (holding(p) != ShazamAnim.HOLD_WRATH) {
			return;
		}
		endHold(p);
		Shazam.sound(p, SoundEvents.BEACON_DEACTIVATE, 0.4f, 1.6f);
		Shazam.startCooldown(p, ShazamAbilityManager.ZEUS_WRATH, ShazamConfig.WRATH_COOLDOWN);
	}

	// ================================================================ C: Mercury Blitz

	public static boolean mercuryBlitz(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 to = dashTo(p, ShazamConfig.BLITZ_DISTANCE);
		if (to.distanceToSqr(from) < 1.0) {
			return false;
		}
		if (!Shazam.spend(p, ShazamConfig.BLITZ_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 mid = new Vec3(0, p.getBbHeight() * 0.5, 0);
		Vec3 a = from.add(mid), b = to.add(mid);
		List<LivingEntity> hit = new ArrayList<>(p.level().getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(ShazamConfig.BLITZ_WIDTH + 1.0),
				e -> foe(p, e) && e.getBoundingBox().inflate(ShazamConfig.BLITZ_WIDTH).clip(a, b).isPresent()));
		hit.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
		p.teleportTo(to.x, to.y, to.z);
		Vec3 dir = to.subtract(from).normalize();
		p.setDeltaMovement(dir.scale(0.4));
		p.hurtMarked = true;
		anim(p, ShazamAnim.BLITZ);
		blur(level, from, to, p.getBbHeight());
		int n = 0;
		for (LivingEntity e : hit) {
			if (n >= ShazamConfig.BLITZ_MAX_HITS) {
				break;
			}
			Shazam.hit(p, e, ShazamConfig.BLITZ_DAMAGE, false);
			Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(level.random.nextBoolean() ? 1 : -1);
			knock(e, side.add(dir.scale(0.5)), 0.6, 0.3);
			Vec3 at = e.position().add(0, e.getBbHeight() * 0.6, 0);
			punchFx(level, at, dir, 0.8f);
			ShazamFx.arc(level, at, at.add(side.scale(1.2)).add(0, 0.6, 0), 0.6f);
			sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.8f, 1.0f + 0.15f * n);
			n++;
		}
		sound(level, to, SoundEvents.WIND_CHARGE_BURST.value(), 0.5f, 1.5f);
		crackle(p, 0.5f, 1.4f);
		fallSafe(p, 30);
		Shazam.startCooldown(p, ShazamAbilityManager.MERCURY_BLITZ, ShazamConfig.BLITZ_COOLDOWN);
		return true;
	}

	// ================================================================ Shift+C: Thunderclap

	/** Inside the clap's cone from his eyes. */
	static boolean inCone(ServerPlayer p, Vec3 point) {
		Vec3 eye = p.getEyePosition();
		Vec3 to = point.subtract(eye);
		double len = to.length();
		if (len > ShazamConfig.CLAP_RANGE || len < 1.0e-4) {
			return len < 1.0e-4;
		}
		double cos = to.scale(1.0 / len).dot(p.getViewVector(1f));
		return cos >= Math.cos(Math.toRadians(ShazamConfig.CLAP_HALF_ANGLE));
	}

	public static boolean thunderclap(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.CLAP_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		double r = ShazamConfig.CLAP_RANGE;
		Vec3 look = p.getViewVector(1f);
		anim(p, ShazamAnim.CLAP);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && (inCone(p, e.position().add(0, e.getBbHeight() * 0.5, 0)) || inCone(p, e.getEyePosition())))) {
			Shazam.hit(p, e, ShazamConfig.CLAP_DAMAGE, false);
			knock(e, look, ShazamConfig.CLAP_KNOCKBACK, 0.35);
		}
		if (griefing(level)) {
			int broken = 0;
			BlockPos c = BlockPos.containing(p.getEyePosition());
			int ir = (int) Math.ceil(r);
			outer:
			for (BlockPos pos : BlockPos.betweenClosed(c.offset(-ir, -ir, -ir), c.offset(ir, ir, ir))) {
				BlockState st = level.getBlockState(pos);
				if ((st.is(ConventionalBlockTags.GLASS_BLOCKS) || st.is(ConventionalBlockTags.GLASS_PANES)) && inCone(p, Vec3.atCenterOf(pos))) {
					level.destroyBlock(pos.immutable(), false, p);
					if (++broken >= ShazamConfig.CLAP_GLASS_MAX) {
						break outer;
					}
				}
			}
		}
		Vec3 eye = p.getEyePosition();
		ShazamFx.send(level, ShazamFxPayload.CLAP, eye.add(0, -0.3, 0).add(look.scale(0.5)), look, (float) ShazamConfig.CLAP_RANGE, 12, -1);
		for (int i = 2; i <= 8; i += 2) {
			Vec3 at = eye.add(look.scale(i));
			level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 2 + i / 2, i * 0.15, i * 0.15, i * 0.15, 0.02);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 2, i * 0.15, i * 0.15, i * 0.15, 0.05);
		}
		level.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x, eye.y + look.y, eye.z + look.z, 1, 0, 0, 0, 0);
		Shazam.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.7f);
		Shazam.sound(p, SoundEvents.GENERIC_EXPLODE.value(), 0.35f, 1.6f);
		Shazam.sound(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.25f, 1.8f);
		com.projecthero.mod.quirk.fx.QuirkCamera.fovPunch(p, 0.06f, 6);
		shake(level, eye, 0.3f, 8);
		Shazam.startCooldown(p, ShazamAbilityManager.THUNDERCLAP, ShazamConfig.CLAP_COOLDOWN);
		return true;
	}

	// ================================================================ Hold C: Mercury Rush

	private static boolean rushStart(ServerPlayer p, long downAt) {
		if (!Shazam.hasLightning(p, ShazamConfig.RUSH_COST)) {
			Shazam.say(p, "message.projecthero.shazam.no_lightning", ChatFormatting.GRAY);
			return false;
		}
		if (rushPick(p, -1) == null) {
			Shazam.say(p, "message.projecthero.shazam.no_target", ChatFormatting.GRAY);
			return false;
		}
		Session s = session(p);
		s.rushHits = 0;
		s.rushNext = now(p);
		s.rushLast = -1;
		setHold(p, ShazamAnim.HOLD_RUSH, now(p));
		crackle(p, 0.6f, 1.5f);
		return true;
	}

	/** The next enemy to hop to: the nearest within 10, preferring one he didn't just hit. */
	private static LivingEntity rushPick(ServerPlayer p, int last) {
		double r = ShazamConfig.RUSH_RANGE;
		List<LivingEntity> all = p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && (e instanceof Enemy || HeroTargets.isHostile(p, e)) && e.distanceToSqr(p) <= r * r);
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : all) {
			double d = e.distanceToSqr(p) + (e.getId() == last && all.size() > 1 ? 1000.0 : 0.0);
			if (d < bestD) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** One hop of the rush: beside the next enemy, a 16 punch. False when nobody is left (or the bar ran dry). For the tests. */
	public static boolean rushHop(ServerPlayer p) {
		Session s = session(p);
		LivingEntity t = rushPick(p, s.rushLast);
		if (t == null || !Shazam.spend(p, ShazamConfig.RUSH_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		Vec3 from = p.position();
		Vec3 back = away(t.position(), p); // from it toward him
		Vec3 spot = t.position().add(back.scale(t.getBbWidth() * 0.5 + 0.9));
		if (level.noCollision(p, p.getBoundingBox().move(spot.subtract(p.position())))) {
			p.teleportTo(spot.x, spot.y, spot.z);
		}
		Vec3 toT = t.position().subtract(p.position());
		p.setYRot((float) (Math.atan2(-toT.x, toT.z) * 180.0 / Math.PI));
		p.setYHeadRot(p.getYRot());
		p.setDeltaMovement(Vec3.ZERO);
		p.hurtMarked = true;
		blur(level, from, p.position(), p.getBbHeight());
		Shazam.hit(p, t, ShazamConfig.RUSH_DAMAGE, false);
		knock(t, back.scale(-1), 0.35, 0.15);
		Vec3 at = t.position().add(0, t.getBbHeight() * 0.6, 0);
		punchFx(level, at, back.scale(-1), 0.9f);
		sound(level, at, SoundEvents.PLAYER_ATTACK_STRONG, 0.9f, 0.9f + 0.1f * s.rushHits);
		sound(level, at, SoundEvents.TRIDENT_RIPTIDE_1.value(), 0.3f, 1.8f);
		anim(p, s.rushHits % 2 == 0 ? ShazamAnim.RUSH_A : ShazamAnim.RUSH_B);
		p.swing(s.rushHits % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
		s.rushHits++;
		s.rushLast = t.getId();
		fallSafe(p, 30);
		return true;
	}

	private static void rushTick(ServerPlayer p) {
		Session s = session(p);
		long now = now(p);
		if (now < s.rushNext) {
			return;
		}
		s.rushNext = now + ShazamConfig.RUSH_EVERY;
		if (!rushHop(p) || s.rushHits >= ShazamConfig.RUSH_MAX_HITS) {
			rushEnd(p);
		}
	}

	private static void rushEnd(ServerPlayer p) {
		if (holding(p) != ShazamAnim.HOLD_RUSH) {
			return;
		}
		endHold(p);
		if (session(p).rushHits > 0) {
			Shazam.startCooldown(p, ShazamAbilityManager.MERCURY_RUSH, ShazamConfig.RUSH_COOLDOWN);
		}
	}

	// ================================================================ Z: Atlas Quake

	public static boolean atlasQuake(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.QUAKE_COST)) {
			return false;
		}
		session(p).quakeAt = now(p) + ShazamConfig.QUAKE_WINDUP;
		anim(p, ShazamAnim.QUAKE);
		Shazam.sound(p, SoundEvents.BREEZE_INHALE, 0.5f, 0.5f);
		Shazam.startCooldown(p, ShazamAbilityManager.ATLAS_QUAKE, ShazamConfig.QUAKE_COOLDOWN);
		return true;
	}

	/** The stomp lands: 19 within 5, a stagger and a knock up. Returns how many it hit. */
	public static int quakeLand(ServerPlayer p) {
		session(p).quakeAt = 0L;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		int n = 0;
		for (LivingEntity e : around(p, c, ShazamConfig.QUAKE_RADIUS)) {
			if (Shazam.hit(p, e, ShazamConfig.QUAKE_DAMAGE, false)) {
				n++;
			}
			knock(e, away(c, e), 0.35, 0.45);
			if (!ShazamCombat.isBoss(e)) {
				e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), p);
			}
		}
		groundFx(p, c, ShazamConfig.QUAKE_RADIUS, 1.1f);
		sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
		sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.4f, 0.6f);
		sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.2f, 0.7f);
		return n;
	}

	// ================================================================ Hold Z: Titan's Grip

	private static boolean gripStart(ServerPlayer p, long downAt) {
		LivingEntity t = aimed(p, ShazamConfig.GRIP_REACH, 0.5, e -> foe(p, e) && !(e instanceof EnderDragon));
		if (t == null) {
			Shazam.say(p, "message.projecthero.shazam.no_target", ChatFormatting.GRAY);
			return false;
		}
		if (!Shazam.spend(p, ShazamConfig.GRIP_COST)) {
			return false;
		}
		ServerLevel level = p.serverLevel();
		if (ShazamCombat.isBoss(t)) {
			// a boss takes the hit but isn't lifted: he heaves at it and shoves
			anim(p, ShazamAnim.SHOVE);
			Shazam.hit(p, t, ShazamConfig.GRIP_DAMAGE, false);
			Vec3 at = t.position().add(0, t.getBbHeight() * 0.4, 0);
			punchFx(level, at, flatLook(p), 1.4f);
			sound(level, at, SoundEvents.IRON_GOLEM_ATTACK, 0.9f, 0.7f);
			sound(level, at, SoundEvents.MACE_SMASH_GROUND, 0.7f, 0.8f);
			shake(level, at, 0.35f, 8);
			Shazam.startCooldown(p, ShazamAbilityManager.TITANS_GRIP, ShazamConfig.GRIP_COOLDOWN);
			return true;
		}
		Session s = session(p);
		s.lifted = t.getId();
		s.liftUntil = now(p) + ShazamConfig.GRIP_MAX_HOLD;
		setHold(p, ShazamAnim.HOLD_GRIP, now(p));
		holdUp(p, t);
		Shazam.sound(p, SoundEvents.IRON_GOLEM_ATTACK, 0.8f, 0.8f);
		level.sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY(), t.getZ(), 8, 0.3, 0.1, 0.3, 0.05);
		return true;
	}

	private static void holdUp(ServerPlayer p, Entity e) {
		Vec3 flat = flatLook(p).scale(0.25);
		Vec3 at = p.position().add(flat).add(0, p.getBbHeight() + 0.25, 0);
		e.teleportTo(at.x, at.y, at.z);
		e.setDeltaMovement(Vec3.ZERO);
		e.resetFallDistance();
		e.hurtMarked = true;
	}

	/** Throws the held creature where he looks; it takes 20 when it lands or hits a wall. */
	public static boolean hurl(ServerPlayer p) {
		Session s = session(p);
		Entity e = lifted(p);
		s.lifted = -1;
		endHold(p);
		if (!(e instanceof LivingEntity le) || !le.isAlive()) {
			return false;
		}
		launch(p, le, p.getViewVector(1f).scale(ShazamConfig.GRIP_THROW_SPEED).add(0, 0.25, 0), ShazamConfig.GRIP_DAMAGE, true);
		anim(p, ShazamAnim.HURL);
		ServerLevel level = p.serverLevel();
		Shazam.sound(p, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.6f);
		Shazam.sound(p, SoundEvents.WIND_CHARGE_THROW, 0.8f, 0.7f);
		QuirkFx.trail(level, le).colours(0xFFFFFF, 0xFFC93A).style(QuirkFx.RIBBON_LIGHT).size(0.35f).life(24).spawn();
		p.swing(InteractionHand.MAIN_HAND, true);
		Shazam.startCooldown(p, ShazamAbilityManager.TITANS_GRIP, ShazamConfig.GRIP_COOLDOWN);
		return true;
	}

	/** Sends {@code target} flying at {@code velocity}, tracked so a wall (or, thrown, the ground) hurts it. */
	static void launch(ServerPlayer p, LivingEntity target, Vec3 velocity, float impactDamage, boolean groundToo) {
		if (!target.isAlive()) {
			return;
		}
		target.setDeltaMovement(velocity);
		target.hurtMarked = true;
		target.hasImpulse = true;
		long now = now(p);
		session(p).flying.put(target.getId(), new Flight(now + ShazamConfig.LAUNCH_TICKS, impactDamage, groundToo, now));
	}

	/** The creature flying from {@code p}'s throw (for the tests). */
	public static boolean inFlight(ServerPlayer p, Entity e) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.flying.containsKey(e.getId());
	}

	// ================================================================ Shift+Z: "SHAZAM!" Thunderstrike

	public static boolean thunderstrike(ServerPlayer p) {
		if (!Shazam.hasLightning(p, ShazamConfig.ULT_COST)) {
			Shazam.say(p, "message.projecthero.shazam.needs_full", ChatFormatting.GRAY);
			return false;
		}
		Shazam.spend(p, ShazamConfig.ULT_COST);
		ShazamFlight.stop(p);
		endAllHolds(p);
		ShazamState n = Shazam.state(p).copy();
		n.eternityStart = now(p);
		n.anim = ShazamAnim.ULT;
		n.animStart = now(p);
		Shazam.save(p, n);
		session(p).ultBolted = false;
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		// he shouts it: everyone near sees "SHAZAM!"
		Component sub = Component.translatable("message.projecthero.shazam.word").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(p) <= ShazamConfig.WORD_HEARD_RANGE * ShazamConfig.WORD_HEARD_RANGE) {
				other.connection.send(new ClientboundSetTitlesAnimationPacket(2, 24, 8));
				other.connection.send(new ClientboundSetTitleTextPacket(Component.empty()));
				other.connection.send(new ClientboundSetSubtitleTextPacket(sub));
			}
		}
		sound(level, c, ShazamSounds.WORD, 0.9f, 0.9f);
		sound(level, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35f, 0.6f);
		ShazamFx.send(level, ShazamFxPayload.AURA, c, c, 1.4f, ShazamConfig.ULT_RELEASE_AT + 12, p.getId());
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xFFC93A).size(6f).thickness(0.3f).life(14).ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		fallSafe(p, ShazamConfig.ULT_RELEASE_AT + 80);
		Shazam.startCooldown(p, ShazamAbilityManager.THUNDERSTRIKE, ShazamConfig.ULT_COOLDOWN);
		return true;
	}

	private static void ultTick(ServerPlayer p, int age) {
		Session s = session(p);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		if (age < ShazamConfig.ULT_RISE_TICKS) {
			double up = 0.42 * (1.0 - age / (double) ShazamConfig.ULT_RISE_TICKS) + 0.05;
			p.setDeltaMovement(p.getDeltaMovement().x * 0.5, up, p.getDeltaMovement().z * 0.5);
			p.hurtMarked = true;
			p.resetFallDistance();
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 5, 0.5, 0.9, 0.5, 0.06);
			if (age % 4 == 0) {
				ShazamFx.arc(level, c.add(level.random.nextDouble() * 6 - 3, 6 + level.random.nextDouble() * 3, level.random.nextDouble() * 6 - 3),
						c.add(0, 1.2, 0), 0.7f);
			}
			return;
		}
		if (!s.ultBolted) {
			s.ultBolted = true;
			// the colossal bolt calls down onto him (harmless to him)
			Shazam.bigBolt(level, c);
			Shazam.bolt(level, c.add(0.5, 0, -0.4));
			Shazam.bolt(level, c.add(-0.4, 0, 0.5));
			level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 3, 0, 0, 0, 0);
			level.sendParticles(Shazam.dust(Shazam.GOLD, 2.0f), c.x, c.y + 1, c.z, 50, 0.6, 1.2, 0.6, 0.0);
			sound(level, c, SoundEvents.TRIDENT_THUNDER.value(), 0.8f, 0.8f);
			shake(level, c, 0.4f, 10);
		}
		p.setDeltaMovement(0, 0, 0);
		p.hurtMarked = true;
		p.resetFallDistance();
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 8, 0.8, 1.0, 0.8, 0.12);
		if (age >= ShazamConfig.ULT_RELEASE_AT) {
			thunderstrikeRelease(p);
		}
	}

	/** He releases the bolt outward: 40 to every foe within 8, then 8 s thunder-charged. Returns how many it hit. */
	public static int thunderstrikeRelease(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		double r = ShazamConfig.ULT_RADIUS;
		int n = 0;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r), e -> foe(p, e) && e.distanceToSqr(p) <= r * r)) {
			if (Shazam.hit(p, e, ShazamConfig.ULT_DAMAGE, true)) {
				n++;
			}
			knock(e, away(c, e), 1.4, 0.6);
		}
		// where the ground is under him (he is up in the air)
		BlockHitResult down = level.clip(new ClipContext(c, c.add(0, -12, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
		Vec3 ground = down.getType() == HitResult.Type.MISS ? c : down.getLocation();
		for (int i = 0; i < 6; i++) {
			double a = i * Math.PI / 3 + level.random.nextDouble() * 0.4;
			Shazam.bolt(level, ground.add(Math.cos(a) * r * 0.7, 0, Math.sin(a) * r * 0.7));
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4;
			ShazamFx.arc(level, c.add(0, 1.0, 0), c.add(Math.cos(a) * r, 0.4, Math.sin(a) * r), 1.3f);
		}
		ShazamFx.burst(level, ground, 3f);
		QuirkFx.ring(level, c.add(0, 1, 0)).colours(0xFFFFFF, 0xFFC93A).size((float) r * 2.4f).thickness(0.6f).life(16).ease(QuirkFx.Ease.OUT_EXPO)
				.spawn();
		QuirkFx.blast(level, c.add(0, 1, 0)).colours(0xFFFFFF, 0xFFC93A).size(5f).life(14).flags(QuirkFx.F_NO_SMOKE).spawn();
		groundFx(p, ground, r, 1.6f);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 2, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 0.5, c.z, 100, r * 0.5, 0.6, r * 0.5, 0.4);
		sound(level, c, SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 0.7f);
		sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.9f, 0.7f);
		sound(level, c, SoundEvents.MACE_SMASH_GROUND_HEAVY, 1.0f, 0.6f);
		sound(level, c, SoundEvents.WIND_CHARGE_BURST.value(), 1.0f, 0.6f);
		shake(level, c, 1.0f, 22);
		ShazamState st = Shazam.state(p).copy();
		st.eternityStart = 0L;
		st.thunderUntil = now(p) + ShazamConfig.ULT_CHARGED_TICKS;
		Shazam.save(p, st);
		session(p).ultBolted = false;
		fallSafe(p, 80);
		// the charge stays on him: gold arcs crawl over him for the 8 s
		ShazamFx.send(level, ShazamFxPayload.AURA, c, c, 0.7f, ShazamConfig.ULT_CHARGED_TICKS, p.getId());
		return n;
	}

	// ================================================================ X: Mercury Dash / Mercury Burst

	static boolean mercuryDash(ServerPlayer p) {
		Vec3 from = p.position();
		Vec3 to = dashTo(p, ShazamConfig.DASH_DISTANCE);
		if (to.distanceToSqr(from) < 1.0) {
			return false;
		}
		p.teleportTo(to.x, to.y, to.z);
		Vec3 dir = to.subtract(from).normalize();
		p.setDeltaMovement(dir.scale(0.4));
		p.hurtMarked = true;
		anim(p, ShazamAnim.DASH);
		blur(p.serverLevel(), from, to, p.getBbHeight());
		crackle(p, 0.5f, 1.4f);
		fallSafe(p, 30);
		Shazam.startCooldown(p, ShazamAbilityManager.MERCURY_DASH, ShazamConfig.DASH_COOLDOWN);
		return true;
	}

	/** X while flying: flight x1.6 for 2 s, and a kick along the look. */
	static boolean mercuryBurst(ServerPlayer p) {
		ShazamState n = Shazam.state(p).copy();
		n.boostUntil = now(p) + ShazamConfig.BURST_TICKS;
		Shazam.save(p, n);
		Vec3 look = p.getViewVector(1f);
		p.setDeltaMovement(look.scale(2.0));
		p.hurtMarked = true;
		anim(p, ShazamAnim.DASH);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position().add(0, p.getBbHeight() * 0.5, 0);
		QuirkFx.ring(level, c.subtract(look.scale(0.6))).colours(0xFFFFFF, 0xFFC93A).size(3.2f).thickness(0.3f).life(9).vec(look)
				.flags(QuirkFx.F_VERTICAL).ease(QuirkFx.Ease.OUT_EXPO).spawn();
		QuirkFx.trail(level, p).colours(0xFFFFFF, 0xFFC93A).style(QuirkFx.RIBBON_LIGHT).size(0.3f).life(ShazamConfig.BURST_TICKS).spawn();
		level.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		Shazam.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.7f, 1.3f);
		Shazam.sound(p, SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.5f, 1.4f);
		Shazam.startCooldown(p, ShazamAbilityManager.MERCURY_DASH, ShazamConfig.BURST_COOLDOWN);
		return true;
	}

	/** Mercury Burst running. Client-safe. */
	public static boolean boosting(Player p) {
		ShazamState s = Shazam.peek(p);
		return s != null && s.transformed && s.flying && s.boostUntil > p.level().getGameTime();
	}

	/** The farthest point along his look, up to {@code distance}, his body fits at (blocks stop him). */
	static Vec3 dashTo(ServerPlayer p, double distance) {
		Vec3 look = p.getViewVector(1f);
		Vec3 best = sweep(p, look, distance);
		// v0.17.3: looking a little down while standing used to run the dash into the floor at once (nothing happened); then dash flat
		if (best.distanceToSqr(p.position()) < 1.0 && Math.abs(look.y) < 0.85) {
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() > 1.0e-4) {
				best = sweep(p, flat.normalize(), distance);
			}
		}
		return best;
	}

	private static Vec3 sweep(ServerPlayer p, Vec3 look, double distance) {
		Vec3 from = p.position();
		AABB box = p.getBoundingBox();
		Vec3 best = from;
		for (double d = 0.25; d <= distance + 1.0e-6; d += 0.25) {
			Vec3 off = look.scale(d);
			if (!p.level().noCollision(p, box.move(off))) {
				break;
			}
			best = from.add(off);
		}
		return best;
	}

	/** The gold-white Mercury blur from {@code a} to {@code b}: streaks, after-images, crackle. */
	static void blur(ServerLevel level, Vec3 a, Vec3 b, double height) {
		ShazamFx.send(level, ShazamFxPayload.BLUR, a, b, 1f, 14, -1);
		Vec3 d = b.subtract(a);
		int steps = Math.max(2, (int) (d.length()));
		for (int i = 0; i <= steps; i++) {
			Vec3 at = a.add(d.scale(i / (double) steps)).add(0, height * 0.5, 0);
			level.sendParticles(Shazam.dust(Shazam.GOLD, 1.0f), at.x, at.y, at.z, 1, 0.15, height * 0.3, 0.15, 0.0);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 1, 0.2, 0.3, 0.2, 0.02);
			}
		}
		level.playSound(null, b.x, b.y, b.z, SoundEvents.TRIDENT_RIPTIDE_1.value(), SoundSource.PLAYERS, 0.35f, 1.6f);
	}

	// ================================================================ Shift+X: Sonic Leap

	public static boolean sonicLeap(ServerPlayer p) {
		if (Shazam.isFlying(p)) {
			return false;
		}
		Vec3 f = flatLook(p).scale(ShazamConfig.LEAP_FORWARD);
		p.setDeltaMovement(f.x, ShazamConfig.LEAP_UP, f.z);
		p.hurtMarked = true;
		p.hasImpulse = true;
		session(p).leapAt = now(p) + ShazamConfig.LEAP_TO_FLIGHT;
		fallSafe(p, 100);
		anim(p, ShazamAnim.LEAP);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		ShazamFx.send(level, ShazamFxPayload.CRACK, c.add(0, 0.05, 0), p.getLookAngle(), 2.5f, 26, -1);
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xFFC93A).size(4.5f).thickness(0.3f).life(10).ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, c.x, c.y + 0.1, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.1, c.z, 14, 0.6, 0.05, 0.6, 0.08);
		Shazam.sound(p, SoundEvents.WIND_CHARGE_BURST.value(), 0.8f, 0.9f);
		Shazam.sound(p, SoundEvents.BREEZE_JUMP, 0.6f, 0.8f);
		crackle(p, 0.4f, 1.0f);
		shake(level, c, 0.2f, 6);
		Shazam.startCooldown(p, ShazamAbilityManager.SONIC_LEAP, ShazamConfig.LEAP_COOLDOWN);
		return true;
	}

	// ================================================================ V: Wisdom of Solomon

	public static boolean wisdom(ServerPlayer p) {
		long until = now(p) + ShazamConfig.WISDOM_TICKS;
		ShazamState n = Shazam.state(p).copy();
		n.wisdomUntil = until;
		Shazam.save(p, n);
		double r = ShazamConfig.WISDOM_RANGE;
		for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
				e -> foe(p, e) && e.distanceToSqr(p) <= r * r && (e instanceof Enemy || e instanceof Player || HeroTargets.isHostile(p, e)))) {
			ShazamCombat.mark(p, e, until);
			e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ShazamConfig.WISDOM_TICKS, 0, false, false), p);
		}
		anim(p, ShazamAnim.WISDOM);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		// a wave of knowing sweeps out across the ground
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xFFE59A).style(QuirkFx.RING_SOFT).size((float) r * 2f).thickness(0.25f).life(22)
				.ease(QuirkFx.Ease.OUT_CUBIC).ground().spawn();
		Vec3 eye = p.getEyePosition();
		level.sendParticles(ParticleTypes.ENCHANT, eye.x, eye.y, eye.z, 40, 0.6, 0.6, 0.6, 0.6);
		level.sendParticles(ParticleTypes.END_ROD, eye.x, eye.y + 0.3, eye.z, 8, 0.3, 0.3, 0.3, 0.02);
		Shazam.sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, 0.9f, 1.2f);
		Shazam.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 1.4f);
		Shazam.startCooldown(p, ShazamAbilityManager.WISDOM, ShazamConfig.WISDOM_COOLDOWN);
		return true;
	}

	// ================================================================ Shift+V: Courage of Achilles

	public static boolean courage(ServerPlayer p) {
		if (!Shazam.spend(p, ShazamConfig.COURAGE_COST)) {
			return false;
		}
		ShazamState n = Shazam.state(p).copy();
		n.standUntil = now(p) + ShazamConfig.COURAGE_TICKS;
		Shazam.save(p, n);
		p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ShazamConfig.COURAGE_TICKS, ShazamConfig.COURAGE_RESISTANCE, false, true, true));
		Shazam.reconcile(p); // no knockback
		anim(p, ShazamAnim.COURAGE);
		ServerLevel level = p.serverLevel();
		Vec3 c = p.position();
		level.sendParticles(Shazam.dust(Shazam.GOLD, 1.5f), c.x, c.y + 1, c.z, 30, 0.8, 1.0, 0.8, 0.0);
		ShazamFx.send(level, ShazamFxPayload.AURA, c, c, 1f, ShazamConfig.COURAGE_TICKS, p.getId());
		QuirkFx.ring(level, c).colours(0xFFF6D0, 0xFFC93A).size(5f).thickness(0.35f).life(12).ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		Shazam.sound(p, SoundEvents.SHIELD_BLOCK, 1.0f, 0.7f);
		Shazam.sound(p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0f, 0.8f);
		Shazam.sound(p, SoundEvents.RAVAGER_ROAR, 0.35f, 1.5f);
		Shazam.startCooldown(p, ShazamAbilityManager.COURAGE, ShazamConfig.COURAGE_COOLDOWN);
		return true;
	}

	// ================================================================ per tick (transformed)

	/** Ends every hold move (the ultimate, turning back). */
	static void endAllHolds(ServerPlayer p) {
		switch (holding(p)) {
			case ShazamAnim.HOLD_HAYMAKER -> endHold(p);
			case ShazamAnim.HOLD_WRATH -> wrathEnd(p);
			case ShazamAnim.HOLD_RUSH -> rushEnd(p);
			case ShazamAnim.HOLD_GRIP -> hurl(p);
			default -> {
			}
		}
	}

	static void tick(ServerPlayer p) {
		long now = now(p);
		ShazamState st = Shazam.state(p);
		ShazamCombat.tick(p);
		int age = Shazam.eternityAge(p);
		if (age >= 0) {
			ultTick(p, age);
		}
		// the hold moves
		switch (st.hold) {
			case ShazamAnim.HOLD_HAYMAKER -> {
				if (now - st.holdStart >= ShazamConfig.HAYMAKER_MAX_HOLD) {
					haymakerRelease(p);
				} else if (p.tickCount % 4 == 0) {
					Vec3 h = hand(p, true);
					p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, h.x, h.y, h.z, 2, 0.12, 0.12, 0.12, 0.05);
					if (p.tickCount % 8 == 0) {
						crackle(p, 0.25f, 0.6f + haymakerCharge(p) * 0.8f);
					}
				}
			}
			case ShazamAnim.HOLD_WRATH -> wrathTick(p);
			case ShazamAnim.HOLD_RUSH -> rushTick(p);
			default -> {
			}
		}
		// Courage of Achilles: the knockback immunity ends with it
		if (st.standUntil != 0L && st.standUntil <= now) {
			ShazamState n = st.copy();
			n.standUntil = 0L;
			Shazam.save(p, n);
			Shazam.reconcile(p);
		}
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		// Thunderhead Slam: the windup ends, or the dive reaches the ground
		if (s.slamAt != 0L && now >= s.slamAt) {
			slamLand(p);
		} else if (s.slamDiving) {
			p.setDeltaMovement(p.getDeltaMovement().x * 0.3, -2.4, p.getDeltaMovement().z * 0.3);
			p.hurtMarked = true;
			p.resetFallDistance();
			Vec3 c = p.position();
			p.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1.0, c.z, 5, 0.3, 0.8, 0.3, 0.05);
			if (p.onGround() || now >= s.slamDiveUntil) {
				slamLand(p);
			}
		}
		// Atlas Quake: the stomp lands
		if (s.quakeAt != 0L && now >= s.quakeAt) {
			quakeLand(p);
		}
		// Sonic Leap: into flight near the top
		if (s.leapAt != 0L && now >= s.leapAt) {
			s.leapAt = 0L;
			if (!Shazam.isFlying(p) && Shazam.transformed(p)) {
				ShazamFlight.start(p);
			}
		}
		// Titan's Grip: held overhead, thrown when time is up
		if (s.lifted >= 0) {
			Entity e = p.level().getEntity(s.lifted);
			if (e == null || !e.isAlive() || e.distanceToSqr(p) > 64.0) {
				s.lifted = -1;
				endHold(p);
				Shazam.startCooldown(p, ShazamAbilityManager.TITANS_GRIP, ShazamConfig.GRIP_COOLDOWN);
			} else if (now >= s.liftUntil) {
				hurl(p);
			} else {
				holdUp(p, e);
			}
		}
		// creatures flying from a throw
		if (!s.flying.isEmpty()) {
			s.flying.entrySet().removeIf(entry -> flightTick(p, entry.getKey(), entry.getValue(), now));
		}
	}

	/** True when the flight is over. */
	private static boolean flightTick(ServerPlayer p, int id, Flight f, long now) {
		Entity e = p.level().getEntity(id);
		if (!(e instanceof LivingEntity le) || !le.isAlive()) {
			return true;
		}
		if (now > f.until()) {
			if (f.groundToo()) {
				Shazam.hit(p, le, f.damage(), false); // a throw that never came down still lands its blow
			}
			return true;
		}
		if (now - f.since() < 2) {
			return false;
		}
		boolean wall = le.horizontalCollision;
		boolean ground = f.groundToo() && le.onGround();
		if (wall || ground) {
			impactFx(p, le, f.damage());
			return true;
		}
		return !f.groundToo() && le.onGround() && now - f.since() > 6;
	}

	private static boolean impactFx(ServerPlayer p, LivingEntity le, float damage) {
		boolean hit = Shazam.hit(p, le, damage, false);
		ServerLevel level = p.serverLevel();
		Vec3 c = le.position();
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
		QuirkFx.ring(level, c).colours(0xFFFFFF, 0xFFC93A).size(4f).thickness(0.3f).life(10).ease(QuirkFx.Ease.OUT_EXPO).ground().spawn();
		BlockState under = level.getBlockState(le.blockPosition().below());
		if (!under.isAir()) {
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), c.x, c.y + 0.2, c.z, 20, 0.6, 0.1, 0.6, 0.2);
		}
		level.playSound(null, c.x, c.y, c.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, 0.8f, 0.9f);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.3f, 1.5f);
		shake(level, c, 0.3f, 8);
		return hit;
	}

	/** For the tests: a thrown creature's impact, as if it had just landed. */
	public static boolean impact(ServerPlayer p, LivingEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		Flight f = s == null ? null : s.flying.remove(e.getId());
		if (f == null) {
			return false;
		}
		return impactFx(p, e, f.damage());
	}

	/** Never targets a squadmate (Share the Power aims at them instead). */
	static boolean squadmate(ServerPlayer p, Entity e) {
		return e instanceof ServerPlayer other && other != p && Squads.areAllies(p, other);
	}
}
