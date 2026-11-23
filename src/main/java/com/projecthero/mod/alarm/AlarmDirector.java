package com.projecthero.mod.alarm;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.alarm.block.AlarmBlocks;
import com.projecthero.mod.alarm.block.AlarmPanelBlock;
import com.projecthero.mod.alarm.block.BlastDoorBlock;
import com.projecthero.mod.hideout.HideoutGuards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7: runs every active alarm zone (a player within 8 blocks of its box, or the zone is Alert) every
 * {@link #STEP} ticks: guard suspicion, alerting and shouting, the Alarm Runner, roles, the Alert (siren, lamps, doors,
 * waves) and the calm-down. All state lives in the {@link AlarmZone} (world storage + level-owned transient fields).
 */
public final class AlarmDirector {
	/** What raised an alarm. */
	public enum Cause { RUNNER, SENSOR, SCRIPT }

	public static final int STEP = 2;
	/** Guards within this many blocks of an alerted guard join the fight (the shout). */
	public static final double SHOUT_RADIUS = 12.0;
	/** Meter level that shows a "?". */
	public static final float QUESTION = 0.15f;
	/** Navigation speed of a running Alarm Runner (on top of his +30% speed). */
	public static final double RUNNER_SPEED = 1.2;

	private AlarmDirector() {
	}

	public static boolean targetable(Player p) {
		return p != null && p.isAlive() && !p.isSpectator() && !p.isCreative();
	}

	// ---------------------------------------------------------------- per level

	public static void tickLevel(ServerLevel level) {
		if (level.players().isEmpty()) {
			return;
		}
		long now = level.getGameTime();
		AlarmZones zs = AlarmZones.get(level);
		if (now % 20 == 0) {
			for (ServerPlayer p : level.players()) {
				AlarmZones.find(level, p.blockPosition());
			}
		}
		for (AlarmZone z : new ArrayList<>(zs.all())) {
			boolean active = z.state == AlarmState.ALERT || playerNear(level, z, 8);
			if (!active) {
				if (!z.suspicion.isEmpty() || !z.guards.isEmpty()) {
					z.suspicion.clear();
					z.guards = new ArrayList<>();
					z.sabotage.clear();
				}
				continue;
			}
			z.age++;
			if (z.age % 40 == 1) {
				AlarmZones.scan(level, z);
				syncBlocks(level, z);
			}
			if (now % STEP == 0) {
				step(level, zs, z);
			}
		}
		if (now % 4 == 0) {
			AlarmNet.sync(level, zs);
		}
	}

	static boolean playerNear(ServerLevel level, AlarmZone z, double pad) {
		AABB box = z.aabb().inflate(pad);
		for (ServerPlayer p : level.players()) {
			if (!p.isSpectator() && box.contains(p.position())) {
				return true;
			}
		}
		return false;
	}

	/** Targetable players inside the zone's box (+1). */
	public static List<ServerPlayer> intruders(ServerLevel level, AlarmZone z) {
		AABB box = z.aabb().inflate(1);
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (targetable(p) && box.contains(p.position())) {
				out.add(p);
			}
		}
		return out;
	}

	static void refreshGuards(ServerLevel level, AlarmZone z) {
		List<Mob> list = level.getEntitiesOfClass(Mob.class, z.aabb().inflate(4), m -> m.isAlive() && AlarmGuards.isGuard(m));
		for (Mob m : list) {
			if (!z.alerted.contains(m.getUUID())) {
				AlarmGuards.pacify(m);
			}
		}
		z.guards = list;
	}

	/** Test / harness seam: scan, refresh the guard list and run one {@link #STEP} of {@code z} now. */
	public static void stepNow(ServerLevel level, AlarmZone z) {
		AlarmZones.scan(level, z);
		refreshGuards(level, z);
		step(level, AlarmZones.get(level), z);
	}

	// ---------------------------------------------------------------- the step

	static void step(ServerLevel level, AlarmZones zs, AlarmZone z) {
		if (z.age % 20 == 1 || z.guards.isEmpty() && z.age % 10 == 1) {
			refreshGuards(level, z);
		}
		List<ServerPlayer> players = intruders(level, z);
		boolean anyAlerted = false;
		float maxMeter = 0f;
		for (Mob g : z.guards) {
			if (!g.isAlive() || g.isRemoved()) {
				continue;
			}
			UUID id = g.getUUID();
			boolean alerted = z.alerted.contains(id);
			LivingEntity t = g.getTarget();
			if (!alerted) {
				if (t instanceof Player tp && targetable(tp)) {
					alert(level, z, g, tp, true); // hurt, or pulled into a fight by its friends
					alerted = true;
				} else {
					if (t instanceof Player) {
						g.setTarget(null);
					}
					float best = 0f;
					Player seen = null;
					for (ServerPlayer p : players) {
						float r = AlarmVision.guardRate(level, g, p);
						if (r > best) {
							best = r;
							seen = p;
						}
					}
					float meter = z.suspicion.getOrDefault(id, 0f);
					meter = best > 0 ? meter + best * STEP : Math.max(0f, meter - AlarmVision.DRAIN * STEP);
					if (meter <= 0f) {
						z.suspicion.remove(id);
					} else {
						z.suspicion.put(id, Math.min(1f, meter));
					}
					if (meter >= 1f && seen != null) {
						alert(level, z, g, seen, true);
						alerted = true;
					} else if (seen != null && meter > 0.3f) {
						investigate(g, seen, meter, z.age);
					}
					maxMeter = Math.max(maxMeter, meter);
				}
			}
			if (alerted) {
				anyAlerted = true;
				if (z.state == AlarmState.ALERT && !(g.getTarget() instanceof Player p2 && targetable(p2)) && !AlarmGuards.hasRole(g, GuardRole.RUNNER)) {
					Player near = nearest(players, g.position());
					if (near != null) {
						g.setTarget(near);
					}
				}
			}
		}
		if (z.state == AlarmState.ALERT) {
			alertStep(level, zs, z, players);
		} else {
			AlarmState s = anyAlerted || maxMeter > 0.05f ? AlarmState.SUSPICIOUS : AlarmState.CALM;
			if (s != z.state) {
				z.state = s;
				syncBlocks(level, z);
			}
			if (anyAlerted) {
				runners(level, z, players);
			} else {
				z.runnerWarned = false;
			}
		}
		AlarmRoles.step(level, z, players);
	}

	private static void investigate(Mob g, Player seen, float meter, int age) {
		g.getLookControl().setLookAt(seen, 30f, 30f);
		g.setYHeadRot(g.getYHeadRot());
		if (meter > 0.6f) {
			if (age % 10 == 0) {
				g.getNavigation().moveTo(seen, 0.6);
			}
		} else {
			g.getNavigation().stop();
		}
	}

	static Player nearest(List<? extends Player> players, Vec3 at) {
		Player best = null;
		double bestSq = Double.MAX_VALUE;
		for (Player p : players) {
			double d = p.distanceToSqr(at);
			if (d < bestSq) {
				bestSq = d;
				best = p;
			}
		}
		return best;
	}

	/** Alerts {@code g} at {@code player}; with {@code shout} the guards within {@link #SHOUT_RADIUS} join in. */
	public static void alert(ServerLevel level, AlarmZone z, Mob g, Player player, boolean shout) {
		boolean fresh = z.alerted.add(g.getUUID());
		z.suspicion.put(g.getUUID(), 1f);
		if (!AlarmGuards.hasRole(g, GuardRole.RUNNER) || z.livePanels(level).isEmpty()) {
			g.setTarget(player);
		}
		if (!fresh || !shout) {
			return;
		}
		level.playSound(null, g.getX(), g.getEyeY(), g.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.HOSTILE, 1.4f, 1.9f);
		for (Mob other : z.guards) {
			if (other != g && other.isAlive() && other.distanceToSqr(g) <= SHOUT_RADIUS * SHOUT_RADIUS) {
				alert(level, z, other, player, false);
			}
		}
	}

	// ---------------------------------------------------------------- the runner

	static void runners(ServerLevel level, AlarmZone z, List<ServerPlayer> players) {
		List<BlockPos> live = null;
		for (Mob g : z.guards) {
			if (!g.isAlive() || !AlarmGuards.hasRole(g, GuardRole.RUNNER)) {
				continue;
			}
			if (live == null) {
				live = z.livePanels(level);
			}
			if (live.isEmpty()) {
				return; // nothing to run to: he fights like everyone else
			}
			z.alerted.add(g.getUUID());
			BlockPos panel = nearestPanel(live, g.position());
			Vec3 c = Vec3.atCenterOf(panel);
			if (g.position().add(0, 0.9, 0).distanceTo(c) <= 2.6) {
				raise(level, z, Cause.RUNNER, panel, null);
				return;
			}
			if (g.getTarget() != null) {
				g.setTarget(null);
			}
			if (g.hasRestriction()) {
				g.clearRestriction(); // his post's leash must not pull him back
			}
			if (z.age % 10 == 0 || g.getNavigation().isDone()) {
				g.getNavigation().moveTo(c.x, panel.getY(), c.z, RUNNER_SPEED);
			}
			if (!z.runnerWarned) {
				z.runnerWarned = true;
				Component msg = Component.translatable("projecthero.alarm.runner").withStyle(ChatFormatting.GOLD);
				for (ServerPlayer p : players) {
					p.displayClientMessage(msg, true);
				}
			}
		}
	}

	private static BlockPos nearestPanel(List<BlockPos> panels, Vec3 at) {
		BlockPos best = panels.get(0);
		double bestSq = Double.MAX_VALUE;
		for (BlockPos p : panels) {
			double d = Vec3.atCenterOf(p).distanceToSqr(at);
			if (d < bestSq) {
				bestSq = d;
				best = p;
			}
		}
		return best;
	}

	// ---------------------------------------------------------------- raising the alarm

	/**
	 * Raises the alarm of the zone at {@code pos} (finding / creating it from a structure). A {@link Cause#SENSOR} only
	 * counts while the sensor's wired panel is live. Returns true if a zone went Alert (false if there is no zone, it was
	 * already Alert, or the sensor is dead).
	 */
	public static boolean raise(ServerLevel level, BlockPos pos, Cause cause, Player culprit) {
		AlarmZone z = AlarmZones.find(level, pos);
		if (z == null) {
			return false;
		}
		if (cause == Cause.SENSOR && !z.sensorLive(level, pos)) {
			return false;
		}
		return raise(level, z, cause, pos, culprit);
	}

	/** Raises {@code z}'s alarm. False if it already was Alert. */
	public static boolean raise(ServerLevel level, AlarmZone z, Cause cause, BlockPos at, Player culprit) {
		if (z.state == AlarmState.ALERT) {
			return false;
		}
		z.state = AlarmState.ALERT;
		z.everAlert = true;
		z.doorsShut = true;
		z.keyOpened.clear();
		z.quietTicks = 0;
		z.sirenIn = 0;
		z.wave = 1;
		z.nextWaveIn = -1;
		AlarmZones.scan(level, z);
		if (z.guards.isEmpty()) {
			refreshGuards(level, z);
		}
		List<ServerPlayer> players = intruders(level, z);
		for (Mob g : z.guards) {
			if (g.isAlive()) {
				z.alerted.add(g.getUUID());
				z.suspicion.put(g.getUUID(), 1f);
				Player near = culprit != null && targetable(culprit) ? culprit : nearest(players, g.position());
				if (near != null) {
					g.setTarget(near);
				}
			}
		}
		spawnWave(level, z, players);
		syncBlocks(level, z);
		AlarmProfile prof = z.profile();
		int colour = prof == null ? 0xFF3030 : prof.colour();
		Component name = prof == null ? Component.translatable("projecthero.alarm.place") : prof.placeName();
		Component msg = Component.translatable("projecthero.alarm.raised." + cause.name().toLowerCase(java.util.Locale.ROOT), name)
				.withStyle(s -> s.withColor(0xFF3B30).withBold(true));
		for (ServerPlayer p : AlarmZones.playersIn(level, z)) {
			p.displayClientMessage(msg, false);
		}
		if (at != null) {
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, 20, 0.4, 0.4, 0.4, 0.2);
		}
		AlarmZones.get(level).changed();
		return true;
	}

	static void alertStep(ServerLevel level, AlarmZones zs, AlarmZone z, List<ServerPlayer> players) {
		AlarmProfile prof = z.profile();
		if (z.wave >= 1 && z.wave <= AlarmProfiles.WAVES) {
			z.sirenIn -= STEP;
			if (z.sirenIn <= 0) {
				z.sirenIn = 40;
				var sound = prof == null || prof.siren() == null ? SoundEvents.BELL_BLOCK : prof.siren();
				float pitch = prof == null ? 1.2f : prof.sirenPitch();
				for (ServerPlayer p : AlarmZones.playersIn(level, z)) {
					level.playSound(null, p.getX(), p.getY() + 4, p.getZ(), sound, SoundSource.HOSTILE, 1.1f, pitch);
				}
			}
			if (z.nextWaveIn > 0) {
				z.nextWaveIn -= STEP;
				if (z.nextWaveIn <= 0) {
					z.nextWaveIn = -1;
					spawnWave(level, z, players);
				}
			} else {
				pruneWave(level, z);
				if (z.waveMobs.isEmpty()) {
					waveCleared(level, z, players);
				}
			}
			return;
		}
		// all waves done: calm down after CALM_DOWN_TICKS of nobody being seen
		boolean seen = false;
		for (Mob g : z.guards) {
			if (g.isAlive() && g.getTarget() instanceof Player p && targetable(p) && g.distanceToSqr(p) < 24 * 24 && g.getSensing().hasLineOfSight(p)) {
				seen = true;
				break;
			}
		}
		z.quietTicks = seen ? 0 : z.quietTicks + STEP;
		if (z.quietTicks >= AlarmZone.CALM_DOWN_TICKS) {
			calm(level, z);
		}
	}

	private static void pruneWave(ServerLevel level, AlarmZone z) {
		Iterator<UUID> it = z.waveMobs.iterator();
		while (it.hasNext()) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity le) || !le.isAlive() || e.isRemoved()) {
				it.remove();
			}
		}
	}

	static void waveCleared(ServerLevel level, AlarmZone z, List<ServerPlayer> players) {
		boolean wasShut = z.doorsShut;
		z.doorsShut = false;
		// clearing a wave also forces the keycard doors
		for (long l : z.doors) {
			BlockPos p = BlockPos.of(l);
			if (level.isLoaded(p)) {
				BlockState s = level.getBlockState(p);
				if (s.is(AlarmBlocks.BLAST_DOOR) && s.getValue(BlastDoorBlock.LOCKED)) {
					level.setBlock(p, s.setValue(BlastDoorBlock.LOCKED, false), 3);
				}
			}
		}
		int done = z.wave;
		if (z.wave < AlarmProfiles.WAVES) {
			z.wave++;
			z.nextWaveIn = AlarmZone.WAVE_GAP_TICKS;
		} else {
			z.wave = AlarmProfiles.WAVES + 1;
			z.quietTicks = 0;
		}
		syncBlocks(level, z);
		Component msg = Component.translatable(done < AlarmProfiles.WAVES ? "projecthero.alarm.wave_cleared" : "projecthero.alarm.waves_done")
				.withStyle(ChatFormatting.YELLOW);
		for (ServerPlayer p : AlarmZones.playersIn(level, z)) {
			p.displayClientMessage(msg, true);
			if (wasShut) {
				level.playSound(null, p.blockPosition(), SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 0.8f, 0.6f);
			}
		}
		AlarmZones.get(level).changed();
	}

	/** Spawns the current wave. */
	static void spawnWave(ServerLevel level, AlarmZone z, List<ServerPlayer> players) {
		AlarmProfile prof = z.profile();
		if (prof == null || prof.reinforcer() == null) {
			z.waveMobs.clear();
			return;
		}
		int n = AlarmProfiles.waveSize(prof.tier(), Math.max(1, players.size()));
		List<BlockPos> points = new ArrayList<>();
		for (long l : z.spawns) {
			BlockPos p = BlockPos.of(l);
			if (!level.isLoaded(p)) {
				continue;
			}
			boolean tooClose = false;
			boolean inReach = players.isEmpty();
			for (ServerPlayer pl : players) {
				double d = pl.distanceToSqr(Vec3.atBottomCenterOf(p));
				tooClose |= d < 5 * 5;
				inReach |= d < 48 * 48;
			}
			if (!tooClose && inReach) {
				points.add(p);
			}
		}
		java.util.Collections.shuffle(points, new java.util.Random(level.random.nextLong()));
		for (int i = 0; i < n; i++) {
			BlockPos spot = null;
			if (!points.isEmpty()) {
				BlockPos at = points.get(i % points.size());
				spot = free(level, at) ? at : HideoutGuards.findSpot(level, at, 0, 2, level.random);
			}
			if (spot == null) {
				List<BlockPos> around = z.livePanels(level);
				if (around.isEmpty()) {
					for (long l : z.lamps) {
						around.add(BlockPos.of(l));
					}
				}
				if (!around.isEmpty()) {
					spot = HideoutGuards.findSpot(level, around.get(level.random.nextInt(around.size())), 2, 6, level.random);
				}
			}
			if (spot == null && !players.isEmpty()) {
				spot = HideoutGuards.findSpot(level, players.get(i % players.size()).blockPosition(), 8, 14, level.random);
			}
			if (spot == null) {
				continue;
			}
			Mob m = prof.reinforcer().spawn(level, spot, level.random);
			if (m == null) {
				continue;
			}
			m.addTag(AlarmGuards.WAVE_TAG);
			AlarmGuards.makeGuard(m);
			z.waveMobs.add(m.getUUID());
			z.alerted.add(m.getUUID());
			Player near = nearest(players, m.position());
			if (near != null) {
				m.setTarget(near);
			}
			if (!z.guards.contains(m)) {
				List<Mob> g = new ArrayList<>(z.guards);
				g.add(m);
				z.guards = g;
			}
		}
	}

	private static boolean free(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP);
	}

	/** The zone calms down: lamps off, doors open, guards forget (sabotaged panels stay dead). */
	public static void calm(ServerLevel level, AlarmZone z) {
		z.state = AlarmState.CALM;
		z.wave = 0;
		z.nextWaveIn = -1;
		z.doorsShut = false;
		z.quietTicks = 0;
		z.runnerWarned = false;
		z.alerted.clear();
		z.suspicion.clear();
		z.waveMobs.clear();
		for (Mob g : z.guards) {
			if (g.isAlive() && g.getTarget() instanceof Player) {
				g.setTarget(null);
			}
			if (g.isAlive()) {
				AlarmGuards.pacify(g);
			}
		}
		syncBlocks(level, z);
		Component msg = Component.translatable("projecthero.alarm.calmed").withStyle(ChatFormatting.GRAY);
		for (ServerPlayer p : AlarmZones.playersIn(level, z)) {
			p.displayClientMessage(msg, true);
		}
		AlarmZones.get(level).changed();
	}

	// ---------------------------------------------------------------- block sync

	/** Puts lamps, doors and panels in the state the zone wants (loaded positions only). */
	public static void syncBlocks(ServerLevel level, AlarmZone z) {
		boolean alert = z.state == AlarmState.ALERT;
		for (long l : z.lamps) {
			BlockPos p = BlockPos.of(l);
			if (level.isLoaded(p)) {
				AlarmBlocks.setLampLit(level, p, alert);
			}
		}
		for (long l : z.panels) {
			BlockPos p = BlockPos.of(l);
			if (level.isLoaded(p)) {
				BlockState s = level.getBlockState(p);
				if (s.is(AlarmBlocks.PANEL) && s.getValue(AlarmPanelBlock.ALERT) != alert) {
					level.setBlock(p, s.setValue(AlarmPanelBlock.ALERT, alert), 3);
				}
			}
		}
		java.util.List<BlockPos> sounded = new ArrayList<>();
		for (long l : z.doors) {
			BlockPos p = BlockPos.of(l);
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockState s = level.getBlockState(p);
			if (!s.is(AlarmBlocks.BLAST_DOOR)) {
				continue;
			}
			boolean open = !s.getValue(BlastDoorBlock.LOCKED) && !(alert && z.doorsShut && !z.keyOpened.contains(l));
			if (open != s.getValue(BlastDoorBlock.OPEN)) {
				if (!open && !level.getEntitiesOfClass(LivingEntity.class, new AABB(p)).isEmpty()) {
					continue; // never shut on someone
				}
				BlastDoorBlock.setOpen(level, p, s, open);
				if (sounded.stream().noneMatch(q -> q.distSqr(p) < 36)) {
					sounded.add(p);
					BlastDoorBlock.sound(level, p, open);
				}
			}
		}
	}
}
