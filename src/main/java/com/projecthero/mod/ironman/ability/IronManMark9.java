package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.IronManAbilityFx;
import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManIntegrity;
import com.projecthero.mod.ironman.IronManSounds;
import com.projecthero.mod.ironman.IronManTargeting;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.7 (user) Era II stage 3: the <b>Mk IX Inferno</b>'s kit -- the Nether suit ({@code docs/IRONMAN_ERA2_KITS.md}).
 * Same form as its Era II siblings ({@link IronManComicKit} {@code kit("mark_9", ...)}, no energy: cooldowns only), plus
 * the <b>heat sink</b>: a 0-100 meter that fills from lava, fire and the suit's own fire moves and vents slowly (fast in
 * water / rain). 50+ = Hot Plating (fire moves x1.25); 100 = saturated (the heat-adding moves refuse until it is under 85).
 * V (Heat Dump) vents it all as a fire nova, Shift+V (Thermal Siphon) draws heat in from the fires around you.
 *
 * <ul>
 *   <li>R Fire Blast / Shift Inferno Stream (hold)</li>
 *   <li>G Magma Fist / Shift Eruption</li>
 *   <li>X Afterburner / Shift Lava Geyser</li>
 *   <li>Z Magma Bomb / Shift Firestorm</li>
 *   <li>V Heat Dump / Shift Thermal Siphon</li>
 * </ul>
 *
 * <p>The meter is authoritative here ({@link P#heat}) and mirrored into the synced {@link ModAttachments#IRON_MAN_HEAT_SINK}
 * (target only) for the HUD when it moves by half a point. Static state (players, bombs in flight, magma pools) is cleared
 * by {@code ServerStateReset} and per player on disconnect. Lava swimming is client-side ({@code IronManMk9Client}).
 */
public final class IronManMark9 {
	public static final String SUIT = "mark_9";

	public static final float HEAT_MAX = 100f;
	/** Hot Plating from here: the fire moves hit {@link #HOT_MULT} harder. */
	public static final float HOT = 50f;
	public static final float HOT_MULT = 1.25f;
	/** Once saturated (100), the heat-adding moves stay locked until the sink is back under this. */
	public static final float RESUME = 85f;
	public static final float DUMP_MIN = 20f;

	public static final float LAVA_HEAT_PER_SECOND = 4f;
	public static final float FIRE_HEAT_PER_SECOND = 2f;
	public static final float VENT_PER_SECOND = 3f;
	public static final float WET_VENT_PER_SECOND = 10f;
	/** Per point of fire damage from an attacker (blaze / ghast fire) the suit shrugs off. */
	public static final float HIT_HEAT_PER_DAMAGE = 1.5f;

	// move heat
	public static final float BLAST_HEAT = 3f;
	public static final float STREAM_HEAT_PER_SECOND = 10f;
	public static final float FIST_HEAT = 3f;
	public static final float ERUPTION_HEAT = 12f;
	public static final float BURNER_HEAT = 6f;
	public static final float GEYSER_HEAT = 6f;
	public static final float BOMB_HEAT = 8f;
	public static final float FIRESTORM_HEAT = 10f;
	/** v0.16.7 (user): the Molten Lash (Shift+V, the new attack once Thermal Siphon moved to C). */
	public static final float LASH_HEAT = 6f;
	public static final double LASH_REACH = 7.0;
	public static final double LASH_HALF_DEG = 60.0;
	public static final int LASH_HIT = 4;

	// tuning
	public static final double BLAST_RANGE = 40.0;
	public static final float BLAST_SPLASH = 8f;
	public static final double STREAM_RANGE = 8.0;
	public static final double FIST_REACH = 4.0;
	public static final double ERUPTION_RADIUS = 5.0;
	public static final double GEYSER_RADIUS = 3.0;
	public static final double BOMB_RADIUS = 3.5;
	public static final int POOL_TICKS = 5 * 20;
	public static final double POOL_RADIUS = 2.5;
	public static final float POOL_DAMAGE = 4f;
	public static final int FIRESTORM_COUNT = 8;
	public static final double SIPHON_RADIUS = 8.0;
	public static final int SIPHON_MAX_SOURCES = 12;
	public static final float SIPHON_HEAT_PER_SOURCE = 5f;
	public static final float SIPHON_MAX_HEAT = 40f;
	public static final float SIPHON_REPAIR_PER_SOURCE = 15f;
	public static final float SIPHON_BURN_DAMAGE = 6f;

	/** Heat Dump: radius / damage from the heat vented. */
	public static double dumpRadius(float heat) {
		return 4.0 + heat * 0.08;
	}

	public static float dumpDamage(float heat) {
		return 10f + heat * 0.36f;
	}

	/** Per-player runtime. */
	static final class P {
		float heat;
		float synced = -1f;
		boolean saturated;
		boolean streaming;
		long streamNextHit;
		long noFallUntil;
	}

	/** A Magma Bomb in flight (a simulated point: arcs, lands on a block or a mob, or bursts at 3 s). */
	static final class Bomb {
		final UUID owner;
		final ResourceKey<Level> dim;
		Vec3 pos;
		Vec3 vel;
		final long born;
		final float damage;

		Bomb(UUID owner, ResourceKey<Level> dim, Vec3 pos, Vec3 vel, long born, float damage) {
			this.owner = owner;
			this.dim = dim;
			this.pos = pos;
			this.vel = vel;
			this.born = born;
			this.damage = damage;
		}
	}

	/** A burning magma pool left by a Magma Bomb. */
	record Pool(UUID owner, ResourceKey<Level> dim, Vec3 centre, long born, long until) {
	}

	private static final Map<UUID, P> STATE = new HashMap<>();
	static final List<Bomb> BOMBS = new ArrayList<>();
	static final List<Pool> POOLS = new ArrayList<>();
	private static boolean initialized;

	private IronManMark9() {
	}

	/** Mod init: the server tick and the logout cleanup. */
	public static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(IronManMark9::serverTick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	public static void clearSessionState() {
		STATE.clear();
		BOMBS.clear();
		POOLS.clear();
	}

	static void forget(UUID id) {
		STATE.remove(id);
		BOMBS.removeIf(b -> b.owner.equals(id));
		POOLS.removeIf(p -> p.owner().equals(id));
	}

	private static P of(ServerPlayer p) {
		return STATE.computeIfAbsent(p.getUUID(), k -> new P());
	}

	// ================================================================== the heat sink

	/** Server: the heat sink (0..100). */
	public static float heat(ServerPlayer p) {
		P s = STATE.get(p.getUUID());
		return s == null ? 0f : s.heat;
	}

	/** Client / any side: the synced copy (for the HUD). */
	public static float syncedHeat(Player p) {
		Float v = p.getAttachedOrElse(ModAttachments.IRON_MAN_HEAT_SINK, 0f);
		return v == null ? 0f : v;
	}

	public static boolean saturated(ServerPlayer p) {
		P s = STATE.get(p.getUUID());
		return s != null && s.saturated;
	}

	public static void setHeat(ServerPlayer p, float value) {
		P s = of(p);
		s.heat = Math.max(0f, Math.min(HEAT_MAX, value));
		if (s.heat >= HEAT_MAX - 1.0e-3f && !s.saturated) {
			s.saturated = true;
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.mk9.saturated").withStyle(ChatFormatting.GOLD), true);
			IronManSounds.play(p, IronManSounds.POWER_FAIL, 0.5f, 1.4f);
		} else if (s.saturated && s.heat < RESUME) {
			s.saturated = false;
		}
		sync(p, s);
	}

	public static void addHeat(ServerPlayer p, float delta) {
		if (delta != 0f) {
			setHeat(p, heat(p) + delta);
		}
	}

	private static void sync(ServerPlayer p, P s) {
		float shown = s.heat;
		if (Math.abs(shown - s.synced) >= 0.5f || shown == 0f && s.synced != 0f || shown >= HEAT_MAX && s.synced < HEAT_MAX) {
			s.synced = shown;
			p.setAttached(ModAttachments.IRON_MAN_HEAT_SINK, shown);
		}
	}

	/** Hot Plating: x1.25 at 50+ heat. */
	public static float hotMultiplier(ServerPlayer p) {
		return heat(p) >= HOT ? HOT_MULT : 1f;
	}

	/**
	 * {@code IronManDamage}: the full Mk IX just shrugged off a fire hit. Fire an attacker threw at you (blaze / ghast fire)
	 * heats the sink; the environment (lava, fire blocks, burning) is counted by the tick instead, once a second.
	 */
	public static void onFireShrugged(ServerPlayer p, DamageSource source, float amount) {
		if (source.getEntity() != null && SUIT.equals(IronManArmor.wornSuitId(p))) {
			addHeat(p, amount * HIT_HEAT_PER_DAMAGE);
		}
	}

	/** The moves that put heat in refuse while the sink is saturated. */
	private static boolean addsHeat(String id) {
		return switch (id) {
			case "mk9_fire_blast", "mk9_inferno_stream", "mk9_magma_fist", "mk9_eruption", "mk9_magma_bomb", "mk9_firestorm",
					"mk9_molten_lash" -> true;
			default -> false;
		};
	}

	// ================================================================== dispatch

	/** {@link IronManComicMoves#fire}: one edge of a Mk IX move. True when it went off (start its cooldown). */
	static boolean fire(ServerPlayer p, IronManSuit suit, IronManComicKit.Move m, boolean pressed) {
		if ("mk9_inferno_stream".equals(m.id())) {
			return stream(p, pressed);
		}
		if (!pressed) {
			return false;
		}
		if (addsHeat(m.id()) && saturated(p)) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.mk9.saturated").withStyle(ChatFormatting.GOLD), true);
			IronManComicFx.soundAt(IronManComicFx.level(p), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.6f, 1.6f);
			return false;
		}
		float d = m.damage();
		return switch (m.id()) {
			case "mk9_fire_blast" -> fireBlast(p, d);
			case "mk9_magma_fist" -> magmaFist(p, d);
			case "mk9_eruption" -> eruption(p, d);
			case "mk9_afterburner" -> afterburner(p, d);
			case "mk9_lava_geyser" -> geyser(p, d);
			case "mk9_magma_bomb" -> magmaBomb(p, d);
			case "mk9_firestorm" -> firestorm(p, d);
			case "mk9_heat_dump" -> heatDump(p);
			case "mk9_thermal_siphon" -> siphon(p);
			case "mk9_molten_lash" -> moltenLash(p, d); // v0.16.7 (user)
			default -> false;
		};
	}

	// ================================================================== R

	private static boolean fireBlast(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		float dmg = damage * hotMultiplier(p);
		Vec3 from = IronManComicFx.palm(p);
		Vec3 dir = IronManTargeting.aim(p, from, p.getLookAngle(), BLAST_RANGE).normalize();
		// the bolt: drawn as the orange fire beam, the hit handled here (fire damage, burning)
		List<LivingEntity> hits = IronManComicFx.beam(p, from, dir, BLAST_RANGE, 0.6, false, IronManComicFx.BEAM_FIRE, 0f, 0.0);
		LivingEntity direct = hits.isEmpty() ? null : hits.get(0);
		Vec3 end = direct != null ? IronManComicFx.centre(direct) : from.add(dir.scale(IronManComicFx.wallDistance(p, from, dir, BLAST_RANGE)));
		if (direct != null) {
			IronManComicFx.burn(p, direct, dmg, 80);
			IronManComicFx.shove(direct, from, 0.6, 0.15);
		}
		for (LivingEntity e : IronManComicFx.around(p, end, 2.0)) {
			if (e != direct) {
				IronManComicFx.burn(p, e, BLAST_SPLASH * hotMultiplier(p), 60);
			}
		}
		level.sendParticles(ParticleTypes.FLAME, end.x, end.y, end.z, 18, 0.3, 0.3, 0.3, 0.08);
		level.sendParticles(ParticleTypes.LAVA, end.x, end.y, end.z, 4, 0.2, 0.2, 0.2, 0.0);
		level.sendParticles(ParticleTypes.SMALL_FLAME, from.x, from.y, from.z, 8, 0.05, 0.05, 0.05, 0.04);
		IronManComicFx.pose(p, IronManAbilityFx.MK9_FIRE_BLAST, 10);
		IronManComicFx.sound(p, SoundEvents.BLAZE_SHOOT, 0.9f, 0.8f + p.getRandom().nextFloat() * 0.15f);
		IronManComicFx.sound(p, IronManSounds.REPULSOR_BLAST, 0.6f, 0.7f);
		IronManComicFx.soundAt(level, end, SoundEvents.FIRECHARGE_USE, 0.7f, 1.0f);
		addHeat(p, BLAST_HEAT);
		return true;
	}

	/** Shift+R, hold: the press lights it (no cooldown yet), the release ends it and starts the cooldown. */
	private static boolean stream(ServerPlayer p, boolean pressed) {
		P s = of(p);
		if (pressed) {
			if (s.saturated) {
				p.displayClientMessage(Component.translatable("message.projecthero.ironman.mk9.saturated").withStyle(ChatFormatting.GOLD), true);
				return false;
			}
			s.streaming = true;
			s.streamNextHit = p.level().getGameTime();
			IronManComicFx.pose(p, IronManAbilityFx.FLAME, 6);
			IronManSounds.move(p, IronManSounds.FLAMETHROWER_IGNITE, 1.0f, 0.85f);
			return false;
		}
		if (s.streaming) {
			s.streaming = false;
			IronManComicFx.soundAt(IronManComicFx.level(p), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.5f, 1.3f);
			return true;
		}
		return false;
	}

	/** True while the Inferno Stream is held (tests / client checks). */
	public static boolean streaming(ServerPlayer p) {
		P s = STATE.get(p.getUUID());
		return s != null && s.streaming;
	}

	private static void streamTick(ServerPlayer p, P s, long now) {
		ServerLevel level = IronManComicFx.level(p);
		// v0.16.7 (user) Era II energy: the stream drains 4/s from the reactor; a dry reactor chokes it like a full sink
		if (!com.projecthero.mod.ironman.ability.era1.IronManEraOne.drainTick(p,
				com.projecthero.mod.ironman.suit.IronManSuits.byId(SUIT), "mk9_inferno_stream")) {
			s.streaming = false;
			IronManComicKit.Move m = IronManComicKit.byId("mk9_inferno_stream");
			if (m != null) {
				TonyStark.triggerCooldown(p, SUIT, m.cooldownKey(), m.cooldownTicks());
			}
			IronManComicFx.soundAt(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.8f);
			return;
		}
		IronManAbilityFx.hold(p, IronManAbilityFx.FLAME);
		Vec3 look = p.getLookAngle();
		Vec3 nozzle = IronManComicFx.palm(p);
		for (int i = 0; i < 3; i++) {
			double t = 1.0 + p.getRandom().nextDouble() * (STREAM_RANGE - 1.0);
			Vec3 at = nozzle.add(look.scale(t));
			double spread = 0.12 * t;
			level.sendParticles(i == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z, 2, spread, spread, spread, 0.02);
		}
		if (now % 10 == 0) {
			IronManSounds.move(p, IronManSounds.FLAMETHROWER, 0.8f, 0.85f);
		}
		if (now >= s.streamNextHit) {
			s.streamNextHit = now + 10;
			IronManComicKit.Move m = IronManComicKit.byId("mk9_inferno_stream");
			float dmg = (m == null ? 9f : m.damage()) * hotMultiplier(p);
			for (LivingEntity e : IronManComicFx.cone(p, nozzle, look, STREAM_RANGE, 25.0)) {
				IronManComicFx.burn(p, e, dmg, 100);
			}
		}
		addHeat(p, STREAM_HEAT_PER_SECOND / 20f);
		if (s.saturated) {
			// the sink is full: the stream chokes off and its cooldown starts
			s.streaming = false;
			IronManComicKit.Move m = IronManComicKit.byId("mk9_inferno_stream");
			if (m != null) {
				TonyStark.triggerCooldown(p, SUIT, m.cooldownKey(), m.cooldownTicks());
			}
			IronManComicFx.soundAt(level, p.position(), SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.8f);
		}
	}

	// ================================================================== Shift+V (v0.16.7 (user))

	/**
	 * Molten Lash: a whip of molten alloy cracked across the front -- every enemy in a {@link #LASH_REACH}-block,
	 * 120-degree arc takes the move's damage (Hot Plating x1.25) + 5 s of fire and is yanked a step toward you.
	 */
	private static boolean moltenLash(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.MK9_LASH, 14);
		IronManComicFx.sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.6f);
		addHeat(p, LASH_HEAT);
		final float dmg = damage * hotMultiplier(p);
		// the whip uncoiling: a short trail of drips off the right wrist while the arm cocks back
		for (int t = 1; t < LASH_HIT; t++) {
			IronManComicState.later(p, t, w -> {
				Vec3 h = IronManComicFx.palm(w);
				IronManComicFx.level(w).sendParticles(ParticleTypes.DRIPPING_LAVA, h.x, h.y, h.z, 3, 0.1, 0.1, 0.1, 0.0);
			});
		}
		IronManComicState.later(p, LASH_HIT, w -> {
			if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			Vec3 eye = w.getEyePosition().add(0, -0.35, 0);
			Vec3 look = w.getLookAngle();
			for (LivingEntity e : IronManComicFx.cone(w, eye, look, LASH_REACH, LASH_HALF_DEG)) {
				IronManComicFx.burn(w, e, dmg, 100);
				IronManComicFx.pull(e, w.position(), 0.45);
				Vec3 c = IronManComicFx.centre(e);
				level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0.0);
			}
			// the lash itself: a glowing arc swept right-to-left across the front, thickening toward the tip
			Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
			for (int k = 0; k <= 14; k++) {
				double a = Math.toRadians(-LASH_HALF_DEG + k * (2 * LASH_HALF_DEG / 14.0));
				Vec3 dir = new Vec3(flat.x * Math.cos(a) - flat.z * Math.sin(a), 0, flat.x * Math.sin(a) + flat.z * Math.cos(a));
				for (double r = 1.5; r <= LASH_REACH; r += 1.4) {
					Vec3 at = eye.add(dir.scale(r)).add(0, Math.sin(k / 14.0 * Math.PI) * 0.3, 0);
					level.sendParticles(r > LASH_REACH - 1.5 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
				}
			}
			IronManComicFx.sound(w, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
			IronManComicFx.sound(w, SoundEvents.BLAZE_SHOOT, 0.7f, 1.3f);
			IronManComicFx.soundAt(level, eye.add(look.scale(LASH_REACH)), SoundEvents.FIRECHARGE_USE, 0.6f, 1.6f);
		});
		return true;
	}

	// ================================================================== G

	private static boolean magmaFist(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.MK9_MAGMA_FIST, 12);
		IronManComicFx.sound(p, SoundEvents.FIRECHARGE_USE, 0.7f, 0.7f);
		addHeat(p, FIST_HEAT);
		final float dmg = damage * hotMultiplier(p);
		IronManComicState.later(p, 3, w -> {
			if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			Vec3 eye = w.getEyePosition();
			Vec3 look = w.getLookAngle();
			Vec3 fist = eye.add(look.scale(1.4)).add(0, -0.35, 0);
			List<LivingEntity> in = IronManComicFx.cone(w, eye, look, FIST_REACH, 40.0);
			LivingEntity e = IronManComicFx.target(w, FIST_REACH);
			if (e == null && !in.isEmpty()) {
				e = in.get(0);
			}
			if (e != null) {
				IronManComicFx.burn(w, e, dmg, 100);
				IronManComicFx.shove(e, w.position(), 1.6, 0.35);
				fist = IronManComicFx.centre(e);
			}
			level.sendParticles(ParticleTypes.LAVA, fist.x, fist.y, fist.z, 6, 0.2, 0.2, 0.2, 0.0);
			level.sendParticles(ParticleTypes.FLAME, fist.x, fist.y, fist.z, 16, 0.25, 0.25, 0.25, 0.1);
			level.sendParticles(ParticleTypes.CRIT, fist.x, fist.y, fist.z, 8, 0.2, 0.2, 0.2, 0.3);
			IronManComicFx.sound(w, IronManSounds.PUNCH, 1.0f, 0.75f);
			if (e != null) {
				IronManComicFx.soundAt(level, fist, SoundEvents.GENERIC_EXTINGUISH_FIRE, 0.6f, 0.6f);
			}
		});
		return true;
	}

	private static boolean eruption(ServerPlayer p, float damage) {
		IronManComicFx.pose(p, IronManAbilityFx.MK9_ERUPTION, 18);
		IronManComicFx.sound(p, IronManSounds.SERVO, 0.7f, 0.7f);
		addHeat(p, ERUPTION_HEAT);
		final float dmg = damage * hotMultiplier(p);
		IronManComicState.later(p, 6, w -> {
			if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			Vec3 c = IronManComicFx.groundAt(w, w.position());
			for (LivingEntity e : IronManComicFx.around(w, c, ERUPTION_RADIUS)) {
				IronManComicFx.burn(w, e, dmg, 120);
				IronManComicFx.shove(e, c, 0.8, 0.9);
			}
			for (int r = 1; r <= 5; r++) {
				IronManComicFx.ring(level, c.add(0, 0.15, 0), r, ParticleTypes.FLAME, 10 + r * 6);
			}
			IronManComicFx.groundRing(level, c, 2.5, 14);
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.2, c.z, 30, 2.0, 0.2, 2.0, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5, c.z, 6, 2.0, 0.4, 2.0, 0.02);
			IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.0f, 0.7f);
			IronManComicFx.soundAt(level, c, SoundEvents.LAVA_POP, 1.2f, 0.6f);
			IronManComicFx.soundAt(level, c, SoundEvents.BLAZE_SHOOT, 1.0f, 0.5f);
		});
		return true;
	}

	// ================================================================== X

	private static boolean afterburner(ServerPlayer p, float damage) {
		if (!IronManDash.start(p, damage * hotMultiplier(p), 0f, 0)) {
			return false;
		}
		addHeat(p, BURNER_HEAT);
		IronManComicFx.sound(p, SoundEvents.FIRECHARGE_USE, 1.0f, 0.6f);
		for (int i = 1; i <= IronManDash.DASH_TICKS; i++) {
			IronManComicState.later(p, i, w -> {
				ServerLevel level = IronManComicFx.level(w);
				Vec3 at = w.position().add(0, 0.6, 0);
				level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.02);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 2, 0.2, 0.2, 0.2, 0.01);
				for (LivingEntity e : IronManComicFx.around(w, at, 1.8)) {
					e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), 80));
				}
			});
		}
		return true;
	}

	private static boolean geyser(ServerPlayer p, float damage) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 base = p.position();
		float dmg = damage * hotMultiplier(p);
		for (LivingEntity e : IronManComicFx.around(p, base, GEYSER_RADIUS)) {
			IronManComicFx.burn(p, e, dmg, 100);
			IronManComicFx.shove(e, base, 0.7, 0.3);
		}
		IronManComicFx.launch(p, new Vec3(p.getDeltaMovement().x * 0.3, p.isInLava() ? 2.2 : 1.3, p.getDeltaMovement().z * 0.3));
		of(p).noFallUntil = p.level().getGameTime() + 80;
		for (int i = 0; i < 10; i++) {
			level.sendParticles(ParticleTypes.FLAME, base.x, base.y + i * 0.5, base.z, 6, 0.25, 0.1, 0.25, 0.03);
		}
		level.sendParticles(ParticleTypes.LAVA, base.x, base.y, base.z, 14, 0.8, 0.1, 0.8, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, base.x, base.y, base.z, 6, 1.0, 0.2, 1.0, 0.03);
		IronManComicFx.pose(p, IronManAbilityFx.COMIC_LAUNCH, 10);
		IronManComicFx.sound(p, IronManSounds.FLIGHT_BURST, 1.0f, 0.8f);
		IronManComicFx.soundAt(level, base, SoundEvents.BLAZE_SHOOT, 1.0f, 0.6f);
		addHeat(p, GEYSER_HEAT);
		return true;
	}

	// ================================================================== Z

	private static boolean magmaBomb(ServerPlayer p, float damage) {
		Vec3 look = p.getLookAngle();
		Vec3 from = IronManComicFx.palm(p);
		Vec3 vel = look.scale(1.1).add(0, 0.35, 0);
		BOMBS.add(new Bomb(p.getUUID(), p.level().dimension(), from, vel, p.level().getGameTime(), damage * hotMultiplier(p)));
		IronManComicFx.pose(p, IronManAbilityFx.MK9_LOB, 12);
		IronManComicFx.sound(p, SoundEvents.FIRECHARGE_USE, 0.8f, 0.8f);
		IronManComicFx.sound(p, SoundEvents.LAVA_POP, 0.8f, 1.2f);
		addHeat(p, BOMB_HEAT);
		return true;
	}

	private static boolean firestorm(ServerPlayer p, float damage) {
		final float dmg = damage * hotMultiplier(p);
		List<LivingEntity> targets = IronManComicFx.frontTargets(p, 48.0, 6);
		IronManComicFx.pose(p, IronManAbilityFx.MISSILES, FIRESTORM_COUNT * 2 + 10);
		IronManComicFx.sound(p, IronManSounds.MISSILE_POD, 1.0f, 0.85f);
		for (int i = 0; i < FIRESTORM_COUNT; i++) {
			final int n = i;
			IronManComicState.later(p, 2 + i * 2, w -> {
				if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
					return;
				}
				Vec3 from = IronManComicFx.shoulder(w, n);
				LivingEntity t = targets.isEmpty() ? null : targets.get(n % targets.size());
				Vec3 dir = t != null ? IronManComicFx.centre(t).subtract(from).normalize().add(0, 0.25, 0).normalize()
						: w.getLookAngle().add(0, 0.15, 0).normalize();
				IronManMissileEntity m = IronManComicFx.missile(w, from, dir, 0.9, dmg, dmg * 0.4f, 1.3f).withIncendiary().withRedTint();
				if (t != null && t.isAlive()) {
					m.withTarget(t);
				} else {
					m.withHoming();
				}
				IronManComicFx.spawn(w, m);
				IronManComicFx.level(w).sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 4, 0.05, 0.05, 0.05, 0.03);
				IronManSounds.play(w, IronManSounds.MISSILE_LAUNCH, 0.7f, 0.95f + (n % 3) * 0.08f);
			});
		}
		addHeat(p, FIRESTORM_HEAT);
		return true;
	}

	// ================================================================== V

	private static boolean heatDump(ServerPlayer p) {
		float h = heat(p);
		if (h < DUMP_MIN) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.mk9.dump_low", (int) DUMP_MIN)
					.withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		final double radius = dumpRadius(h);
		final float dmg = dumpDamage(h);
		final float vented = h;
		setHeat(p, 0f);
		// the client draws the nova rings; their radius rides in the pose length (14 + radius ticks)
		IronManComicFx.pose(p, IronManAbilityFx.MK9_HEAT_DUMP, IronManAbilityFx.MK9_HEAT_DUMP_BASE_TICKS + (int) Math.round(radius));
		IronManComicFx.sound(p, IronManSounds.SERVO, 0.8f, 0.6f);
		IronManComicFx.sound(p, SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.5f);
		IronManComicState.later(p, 4, w -> {
			if (!SUIT.equals(IronManArmor.wornSuitId(w))) {
				return;
			}
			ServerLevel level = IronManComicFx.level(w);
			Vec3 c = IronManComicFx.chest(w);
			for (LivingEntity e : IronManComicFx.around(w, c, radius)) {
				IronManComicFx.burn(w, e, dmg, 120);
				IronManComicFx.shove(e, c, 1.0 + vented / 100.0, 0.4);
			}
			for (double r = 1.0; r <= radius; r += 1.0) {
				IronManComicFx.ring(level, c, r, r % 2 < 1 ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, (int) (8 + r * 5));
			}
			level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, (int) (10 + vented / 4), radius * 0.3, 0.5, radius * 0.3, 0.0);
			// (in-client check: an explosion emitter's puffs swamped the nova rings -- one flash, a little smoke)
			level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0, c.z, 6, 0.4, 0.8, 0.4, 0.04);
			level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
			level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, (int) (20 + vented / 2), 0.3, 0.3, 0.3, 0.25 + radius * 0.02);
			IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 1.2f, 0.75f);
			IronManComicFx.soundAt(level, c, SoundEvents.BLAZE_SHOOT, 1.2f, 0.5f);
			IronManComicFx.soundAt(level, c, SoundEvents.FIRECHARGE_USE, 1.0f, 0.5f);
		});
		return true;
	}

	private static boolean siphon(ServerPlayer p) {
		ServerLevel level = IronManComicFx.level(p);
		Vec3 chest = IronManComicFx.chest(p);
		List<Vec3> sources = new ArrayList<>();
		// burning mobs: put out, scorched
		for (LivingEntity e : IronManComicFx.around(p, chest, SIPHON_RADIUS)) {
			if (sources.size() >= SIPHON_MAX_SOURCES) {
				break;
			}
			if (e.isOnFire()) {
				e.clearFire();
				IronManComicFx.hit(p, e, SIPHON_BURN_DAMAGE);
				sources.add(IronManComicFx.centre(e));
			}
		}
		// hot blocks: fire goes out, lava / magma / lit campfires give heat and stay
		int r = (int) SIPHON_RADIUS;
		BlockPos centre = p.blockPosition();
		BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
		outer:
		for (int dy = -r; dy <= r; dy++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (sources.size() >= SIPHON_MAX_SOURCES) {
						break outer;
					}
					if (dx * dx + dy * dy + dz * dz > r * r) {
						continue;
					}
					mp.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					BlockState st = level.getBlockState(mp);
					if (st.is(BlockTags.FIRE)) {
						level.removeBlock(mp, false);
						sources.add(Vec3.atCenterOf(mp));
					} else if (hotBlock(st)) {
						sources.add(Vec3.atCenterOf(mp));
					}
				}
			}
		}
		if (sources.isEmpty()) {
			p.displayClientMessage(Component.translatable("message.projecthero.ironman.mk9.siphon_none").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		for (Vec3 s : sources) {
			IronManComicFx.line(level, s, chest, ParticleTypes.FLAME, 2.0);
			level.sendParticles(ParticleTypes.SMOKE, s.x, s.y, s.z, 4, 0.2, 0.2, 0.2, 0.01);
		}
		level.sendParticles(ParticleTypes.FLAME, chest.x, chest.y, chest.z, 20, 0.2, 0.2, 0.2, 0.02);
		addHeat(p, Math.min(SIPHON_MAX_HEAT, sources.size() * SIPHON_HEAT_PER_SOURCE));
		float integ = IronManEnergy.integrity(p, SUIT);
		IronManEnergy.setIntegrity(p, SUIT, integ + sources.size() * SIPHON_REPAIR_PER_SOURCE);
		IronManComicFx.pose(p, IronManAbilityFx.MK9_SIPHON, 20);
		IronManComicFx.sound(p, SoundEvents.FIRE_EXTINGUISH, 1.0f, 0.7f);
		IronManComicFx.sound(p, SoundEvents.BEACON_POWER_SELECT, 0.7f, 1.4f);
		return true;
	}

	private static boolean hotBlock(BlockState st) {
		return st.is(Blocks.LAVA) || st.is(Blocks.MAGMA_BLOCK) || st.is(Blocks.LAVA_CAULDRON)
				|| st.getBlock() instanceof CampfireBlock && st.getValue(CampfireBlock.LIT);
	}

	// ================================================================== the tick

	private static void serverTick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			tickPlayer(p, now);
		}
		if (!BOMBS.isEmpty()) {
			tickBombs(server, now);
		}
		if (!POOLS.isEmpty()) {
			tickPools(server, now);
		}
	}

	/** Gametests: Magma Bombs in flight / magma pools burning. */
	public static int activeBombs() {
		return BOMBS.size();
	}

	public static int activePools() {
		return POOLS.size();
	}

	/** Gametests: one tick of the Mk IX state for {@code p} and the world objects. */
	public static void tickForTests(ServerPlayer p) {
		long now = p.level().getGameTime();
		tickPlayer(p, now);
		tickBombs(p.getServer(), now);
		tickPools(p.getServer(), now);
	}

	private static void tickPlayer(ServerPlayer p, long now) {
		String worn = IronManArmor.wornSuitId(p);
		boolean wearing = p.isAlive() && SUIT.equals(worn) && IronManArmor.hasChestplate(p, SUIT);
		P s = STATE.get(p.getUUID());
		if (!wearing) {
			if (s != null) {
				s.streaming = false;
				if (s.heat > 0f || s.synced > 0f) {
					setHeat(p, 0f);
				}
			}
			return;
		}
		s = of(p);
		if (s.noFallUntil > now) {
			p.fallDistance = 0f;
		}
		boolean disabled = IronManIntegrity.disabled(p, SUIT);
		if (s.streaming) {
			if (disabled) {
				s.streaming = false;
			} else {
				streamTick(p, s, now);
			}
		}
		// the sink: heated by lava / fire, else venting (fast in water / rain)
		ServerLevel level = IronManComicFx.level(p);
		float perSecond;
		if (p.isInLava()) {
			perSecond = LAVA_HEAT_PER_SECOND;
		} else if (inFire(p)) {
			perSecond = FIRE_HEAT_PER_SECOND;
		} else if (s.streaming) {
			perSecond = 0f;
		} else if (p.isInWaterOrRain()) {
			perSecond = -WET_VENT_PER_SECOND;
			if (s.heat > 0f && now % 5 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 1.2, p.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
		} else {
			perSecond = -VENT_PER_SECOND;
		}
		if (perSecond != 0f && !(perSecond < 0f && s.heat <= 0f)) {
			addHeat(p, perSecond / 20f);
		}
		// the heat-sink fins smoke / glow once the plating is hot
		if (s.heat >= HOT && now % 4 == 0) {
			Vec3 back = p.position().add(0, 1.3, 0).subtract(p.getLookAngle().multiply(1, 0, 1).normalize().scale(0.35));
			level.sendParticles(s.heat >= HEAT_MAX - 1f ? ParticleTypes.FLAME : ParticleTypes.SMOKE, back.x, back.y, back.z, 2, 0.15, 0.2, 0.15, 0.01);
		}
	}

	private static boolean inFire(ServerPlayer p) {
		if (p.level().getBlockStatesIfLoaded(p.getBoundingBox().deflate(1.0e-3)).anyMatch(st -> st.is(BlockTags.FIRE) || hotBlock(st))) {
			return true;
		}
		BlockState below = p.level().getBlockState(p.getOnPos());
		return below.is(Blocks.MAGMA_BLOCK) || below.getBlock() instanceof CampfireBlock && below.getValue(CampfireBlock.LIT);
	}

	private static void tickBombs(MinecraftServer server, long now) {
		var it = BOMBS.iterator();
		while (it.hasNext()) {
			Bomb b = it.next();
			ServerLevel level = server.getLevel(b.dim);
			ServerPlayer owner = server.getPlayerList().getPlayer(b.owner);
			if (level == null || owner == null || owner.level() != level) {
				it.remove();
				continue;
			}
			Vec3 next = b.pos.add(b.vel);
			b.vel = b.vel.scale(0.98).add(0, -0.05, 0);
			level.sendParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 2, 0.05, 0.05, 0.05, 0.01);
			level.sendParticles(ParticleTypes.DRIPPING_LAVA, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0.0);
			boolean burst = now - b.born > 60;
			var clip = level.clip(new net.minecraft.world.level.ClipContext(b.pos, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.ANY, owner));
			if (clip.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
				next = clip.getLocation();
				burst = true;
			}
			if (!burst && !IronManComicFx.around(owner, next, 0.9).isEmpty()) {
				burst = true;
			}
			b.pos = next;
			if (burst) {
				it.remove();
				explodeBomb(owner, level, b);
			}
		}
	}

	private static void explodeBomb(ServerPlayer owner, ServerLevel level, Bomb b) {
		Vec3 c = b.pos;
		for (LivingEntity e : IronManComicFx.around(owner, c, BOMB_RADIUS)) {
			IronManComicFx.burn(owner, e, b.damage, 100);
			IronManComicFx.shove(e, c, 0.5, 0.3);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, 16, 1.0, 0.3, 1.0, 0.0);
		level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 30, 1.2, 0.4, 1.2, 0.05);
		IronManComicFx.soundAt(level, c, SoundEvents.GENERIC_EXPLODE, 0.8f, 1.1f);
		IronManComicFx.soundAt(level, c, SoundEvents.LAVA_EXTINGUISH, 0.9f, 0.7f);
		long now = level.getGameTime();
		Vec3 ground = IronManComicFx.groundAt(owner, c);
		POOLS.add(new Pool(owner.getUUID(), level.dimension(), ground, now, now + POOL_TICKS));
	}

	private static void tickPools(MinecraftServer server, long now) {
		var it = POOLS.iterator();
		while (it.hasNext()) {
			Pool pool = it.next();
			ServerLevel level = server.getLevel(pool.dim());
			ServerPlayer owner = server.getPlayerList().getPlayer(pool.owner());
			if (level == null || owner == null || now >= pool.until()) {
				it.remove();
				continue;
			}
			Vec3 c = pool.centre();
			long age = now - pool.born();
			if (age % 2 == 0) {
				level.sendParticles(ParticleTypes.FLAME, c.x, c.y + 0.1, c.z, 8, POOL_RADIUS * 0.45, 0.02, POOL_RADIUS * 0.45, 0.01);
				level.sendParticles(ParticleTypes.LAVA, c.x, c.y + 0.1, c.z, 1, POOL_RADIUS * 0.4, 0.02, POOL_RADIUS * 0.4, 0.0);
			}
			if (age % 10 == 0 && owner.level() == level) {
				for (LivingEntity e : IronManComicFx.around(owner, c, POOL_RADIUS)) {
					if (Math.abs(e.getY() - c.y) > 2.0) {
						continue;
					}
					IronManComicFx.burn(owner, e, POOL_DAMAGE, 60);
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false, true));
				}
			}
		}
	}
}
