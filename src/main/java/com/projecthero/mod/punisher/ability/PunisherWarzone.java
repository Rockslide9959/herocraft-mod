package com.projecthero.mod.punisher.ability;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Vector3f;

import com.projecthero.mod.combat.HeroTargets;
import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.punisher.Punisher;
import com.projecthero.mod.punisher.PunisherConfig;
import com.projecthero.mod.punisher.PunisherFeedback;
import com.projecthero.mod.squad.Squads;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.15.18 -- Shift+Z, <b>Warzone</b>. Hold Shift+Z for 5 seconds (let go of either key and the call is off) to call an
 * artillery barrage on the block you are aiming at (up to 100 blocks away). The 15-block area is marked for everyone --
 * a ring of red smoke on the ground and a red smoke column at its centre -- and for 10 seconds missiles rain onto random
 * spots inside it, about three a second. Each one is a visible falling rocket (a fire-and-smoke trail drawn for every
 * player in sight of it) that bursts on impact: 30 damage at the centre of a 5-block blast, falling off to 40% at the
 * edge. It never breaks a block and never touches the caller or his squad. 2-minute cooldown, spent when the call goes in.
 *
 * <p>The missiles are simulated server-side (no entity, nothing saved): a strike lives in a static list, ticked from
 * {@link Punisher#initialize}'s server tick and cleared on server stop ({@code ServerStateReset}). A strike ends early if
 * its caller logs out or leaves the dimension.
 */
public final class PunisherWarzone {
	public static final String ABILITY = "warzone";

	/** How far away (blocks) a player still gets the trail / ring particles. */
	private static final double VIEW_RANGE = 192.0;
	private static final DustParticleOptions RED_SMOKE = new DustParticleOptions(new Vector3f(0.85f, 0.05f, 0.05f), 2.6f);

	/** Player -> game time Shift+Z went down (the call is being radioed in). */
	private static final Map<UUID, Long> CHARGING = new ConcurrentHashMap<>();
	private static final List<Strike> STRIKES = new CopyOnWriteArrayList<>();

	private static final class Missile {
		Vec3 pos;
		final Vec3 step;

		Missile(Vec3 pos, Vec3 step) {
			this.pos = pos;
			this.step = step;
		}
	}

	private static final class Strike {
		final ServerLevel level;
		final UUID owner;
		final Vec3 centre;
		final long endsAt;
		final List<Missile> missiles = new ArrayList<>();
		double owed;

		Strike(ServerLevel level, UUID owner, Vec3 centre, long endsAt) {
			this.level = level;
			this.owner = owner;
			this.centre = centre;
			this.endsAt = endsAt;
		}
	}

	private PunisherWarzone() {
	}

	public static void clearSessionState() {
		CHARGING.clear();
		STRIKES.clear();
	}

	// ---------------- the call (hold Shift+Z) ----------------

	/** Shift+Z pressed: start radioing it in. Returns true if the charge started. */
	public static boolean beginCharge(ServerPlayer player) {
		if (!Punisher.hasPower(player)) {
			return false;
		}
		if (!Punisher.abilityReady(player, ABILITY)) {
			int secs = (Punisher.cooldownRemaining(player, ABILITY) + 19) / 20;
			player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
					"message.projecthero.punisher.warzone_cooldown", secs).withStyle(net.minecraft.ChatFormatting.GRAY), true);
			return false;
		}
		CHARGING.put(player.getUUID(), player.level().getGameTime());
		player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.7f, 0.7f);
		PunisherFeedback.message(player, "warzone_calling");
		return true;
	}

	public static boolean charging(ServerPlayer player) {
		return CHARGING.containsKey(player.getUUID());
	}

	/** Z released, or Shift let go, before the 5 s were up. */
	public static void cancelCharge(ServerPlayer player) {
		if (CHARGING.remove(player.getUUID()) != null) {
			PunisherFeedback.message(player, "warzone_cancelled");
			player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 0.5f);
		}
	}

	public static void forget(UUID player) {
		CHARGING.remove(player);
	}

	/** Every server tick for a Punisher: keep the call going, cancel it, or send it in. */
	public static void tickCharge(ServerPlayer player) {
		Long since = CHARGING.get(player.getUUID());
		if (since == null) {
			return;
		}
		if (!player.isShiftKeyDown() || !player.isAlive()) {
			cancelCharge(player);
			return;
		}
		long held = player.level().getGameTime() - since;
		if (held > 0 && held % 20 == 0 && held < PunisherConfig.WARZONE_CHARGE_TICKS) {
			player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5f, 0.8f + held / 100f);
		}
		if (held >= PunisherConfig.WARZONE_CHARGE_TICKS) {
			CHARGING.remove(player.getUUID());
			BlockHitResult hit = player.level().clip(new ClipContext(player.getEyePosition(),
					player.getEyePosition().add(player.getLookAngle().scale(PunisherConfig.WARZONE_RANGE)),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
			if (hit.getType() == HitResult.Type.MISS) {
				PunisherFeedback.message(player, "warzone_no_target");
				player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 0.5f);
				return;
			}
			call(player, hit.getLocation());
		}
	}

	/** Send the barrage in on {@code centre} (no charge, no checks beyond the power -- the gametests call this). */
	public static void call(ServerPlayer player, Vec3 centre) {
		ServerLevel level = player.serverLevel();
		STRIKES.add(new Strike(level, player.getUUID(), centre, level.getGameTime() + PunisherConfig.WARZONE_DURATION_TICKS));
		Punisher.triggerCooldown(player, ABILITY, PunisherConfig.WARZONE_COOLDOWN_TICKS);
		player.playNotifySound(SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.9f, 1.6f);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 3.0f, 1.4f);
		PunisherFeedback.message(player, "warzone_inbound");
	}

	public static int activeStrikes() {
		return STRIKES.size();
	}

	// ---------------- the barrage ----------------

	/** Once per server tick. */
	public static void tick(MinecraftServer server) {
		for (Strike s : STRIKES) {
			ServerPlayer owner = server.getPlayerList().getPlayer(s.owner);
			long now = s.level.getGameTime();
			boolean firing = now < s.endsAt;
			if (owner == null || owner.level() != s.level || (!firing && s.missiles.isEmpty())) {
				STRIKES.remove(s);
				continue;
			}
			if (firing) {
				if (now % 4 == 0) {
					drawZone(s);
				}
				s.owed += PunisherConfig.WARZONE_MISSILES_PER_SECOND / 20.0;
				while (s.owed >= 1.0) {
					s.owed -= 1.0;
					launch(s);
				}
			}
			for (Iterator<Missile> it = s.missiles.iterator(); it.hasNext(); ) {
				Missile m = it.next();
				Vec3 next = m.pos.add(m.step);
				BlockHitResult hit = s.level.clip(new ClipContext(m.pos, next, ClipContext.Block.COLLIDER,
						ClipContext.Fluid.ANY, net.minecraft.world.phys.shapes.CollisionContext.empty()));
				trail(s.level, m.pos, hit.getType() == HitResult.Type.MISS ? next : hit.getLocation());
				if (hit.getType() != HitResult.Type.MISS) {
					detonate(s.level, owner, hit.getLocation());
					it.remove();
				} else if (next.y < s.centre.y - 40.0) {
					it.remove(); // fell into a hole off the bottom of the zone -- let it go
				} else {
					m.pos = next;
				}
			}
		}
	}

	private static void launch(Strike s) {
		RandomSource r = s.level.getRandom();
		double ang = r.nextDouble() * Math.PI * 2.0;
		double dist = Math.sqrt(r.nextDouble()) * PunisherConfig.WARZONE_RADIUS;
		Vec3 land = s.centre.add(Math.cos(ang) * dist, 0, Math.sin(ang) * dist);
		// they come in at a slight angle, all from the same side, like a real battery would
		Vec3 from = land.add(-6.0, PunisherConfig.WARZONE_DROP_HEIGHT, -3.0);
		Vec3 step = land.subtract(from).normalize().scale(PunisherConfig.WARZONE_FALL_SPEED);
		s.missiles.add(new Missile(from, step));
		s.level.playSound(null, from.x, from.y - 10, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 3.0f, 0.6f);
	}

	/** The marked area: a ring of red smoke on the ground and a red column at the centre, for everyone in sight. */
	private static void drawZone(Strike s) {
		double r = PunisherConfig.WARZONE_RADIUS;
		int points = 40;
		long t = s.level.getGameTime();
		for (int i = 0; i < points; i++) {
			double a = (i + (t / 4 % 2) * 0.5) * Math.PI * 2.0 / points;
			broadcast(s.level, RED_SMOKE, s.centre.add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0.15, 0.1, 0.15, 0.0);
		}
		broadcast(s.level, RED_SMOKE, s.centre.add(0, 1.2, 0), 6, 0.3, 1.0, 0.3, 0.0);
		broadcast(s.level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, s.centre.add(0, 0.4, 0), 1, 0.2, 0.1, 0.2, 0.01);
	}

	private static void trail(ServerLevel level, Vec3 a, Vec3 b) {
		Vec3 d = b.subtract(a);
		for (int i = 0; i < 3; i++) {
			Vec3 p = a.add(d.scale(i / 3.0));
			broadcast(level, ParticleTypes.FLAME, p, 2, 0.05, 0.05, 0.05, 0.01);
			broadcast(level, ParticleTypes.LARGE_SMOKE, p, 1, 0.08, 0.08, 0.08, 0.01);
		}
		broadcast(level, ParticleTypes.FIREWORK, a, 1, 0.02, 0.02, 0.02, 0.0);
	}

	/**
	 * One missile bursts at {@code at}: the blast's particles and sound, and damage to everything {@link #canHit} inside
	 * 5 blocks. Blocks are never touched.
	 */
	public static void detonate(ServerLevel level, ServerPlayer owner, Vec3 at) {
		broadcast(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0, 0, 0);
		broadcast(level, ParticleTypes.EXPLOSION, at.add(0, 0.5, 0), 6, 1.4, 0.8, 1.4, 0.0);
		broadcast(level, ParticleTypes.LARGE_SMOKE, at.add(0, 0.6, 0), 24, 1.5, 0.8, 1.5, 0.04);
		broadcast(level, ParticleTypes.LAVA, at, 8, 0.6, 0.2, 0.6, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 4.0f,
				0.75f + level.getRandom().nextFloat() * 0.2f);

		DamageSource src = level.damageSources().explosion(null, owner);
		double r = PunisherConfig.WARZONE_BLAST_RADIUS;
		for (LivingEntity e : blastTargets(level, owner, at)) {
			double dist = Math.sqrt(AbilityHelpers.distanceSqToBox(e, at));
			float falloff = (float) (1.0 - (1.0 - PunisherConfig.WARZONE_EDGE_DAMAGE) * Math.min(1.0, dist / r));
			if (!(e instanceof Player)) {
				e.invulnerableTime = 0; // three a second: every missile that lands on you counts
			}
			if (AbilityHelpers.hurt(owner, e, src, PunisherConfig.WARZONE_DAMAGE * falloff)) {
				Vec3 push = e.position().subtract(at);
				Vec3 flat = new Vec3(push.x, 0, push.z);
				flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(0.6 * falloff);
				AbilityHelpers.push(e, new Vec3(flat.x, 0.35 * falloff, flat.z));
			}
		}
	}

	/** Everything one missile landing at {@code at} hurts: never the caller, his squad, or his pets. */
	public static List<LivingEntity> blastTargets(ServerLevel level, ServerPlayer owner, Vec3 at) {
		return AbilityHelpers.living(level, at, PunisherConfig.WARZONE_BLAST_RADIUS, e -> canHit(owner, e));
	}

	public static boolean canHit(ServerPlayer owner, LivingEntity e) {
		return e != owner && HeroTargets.canHarm(owner, e) && !Squads.areAllies(owner, e);
	}

	/** Particles every player within {@link #VIEW_RANGE} sees, however far -- the barrage is seen from a distance. */
	private static void broadcast(ServerLevel level, ParticleOptions p, Vec3 at, int count, double dx, double dy, double dz,
			double speed) {
		double r2 = VIEW_RANGE * VIEW_RANGE;
		for (ServerPlayer viewer : level.players()) {
			if (viewer.distanceToSqr(at) <= r2) {
				level.sendParticles(viewer, p, true, at.x, at.y, at.z, count, dx, dy, dz, speed);
			}
		}
	}
}
