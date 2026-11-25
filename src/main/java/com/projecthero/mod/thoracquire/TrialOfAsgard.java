package com.projecthero.mod.thoracquire;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.entity.MjolnirEntity;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.thoracquire.entity.StormwardenBoss;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (user: "a trial at the crater"): the <b>Trial of Asgard</b>. A player with full Valor who tries to lift Mjolnir in a
 * thunderstorm is tested first: the storm darkens, a lightning pillar strikes the plinth and {@link StormwardenBoss The
 * Stormwarden} forms beside the hammer.
 *
 * <ul>
 * <li><b>Unpowered only</b> (user: "doing it with powers would make it too easy"): a player with any power
 * ({@link HeroTiers#hasAnyPower}) is refused and keeps their attempt; gaining a power mid-trial is a loss.</li>
 * <li><b>Win</b> (the Stormwarden dies): {@link ThorAcquireAttachments#TRIAL_PASSED} is saved, and if it is still storming the
 * lift cutscene starts straight away ({@link #LIFT_DELAY} ticks after the kill). If the storm has gone, the next storm lets
 * them lift it without fighting again.</li>
 * <li><b>Loss</b>: dying, going more than {@link #LEASH} blocks from the crater, logging out, the storm ending, or gaining a
 * power. The guardian dissipates, the hammer stays, and {@link ThorAcquireAttachments#TRIAL_FAILED_STORM} records this storm:
 * one attempt per storm per player.</li>
 * </ul>
 * One trial per hammer at a time. The running trials are a static map keyed by player UUID holding only UUIDs, positions and
 * a dimension key (no worlds or entities), cleared by {@code ServerStateReset}; a server stop mid-trial simply ends it
 * without a loss (the guardian dissipates when loaded, {@link #owns}).
 */
public final class TrialOfAsgard {
	public static final double LEASH = 48.0;
	/** The intro: storm darkens (0), pillars strike the plinth (0, 14, 28), the guardian forms (30). */
	public static final int INTRO_TICKS = 30;
	public static final int LIFT_DELAY = 50;

	public enum Stage {
		INTRO, FIGHT, WON
	}

	public static final class Trial {
		public final UUID player;
		public final ResourceKey<Level> dimension;
		public final BlockPos center;
		public UUID boss;
		public UUID hammer;
		public Stage stage = Stage.INTRO;
		public int ticks;

		Trial(UUID player, ResourceKey<Level> dimension, BlockPos center, UUID hammer) {
			this.player = player;
			this.dimension = dimension;
			this.center = center;
			this.hammer = hammer;
		}
	}

	private static final Map<UUID, Trial> RUNS = new HashMap<>();

	private TrialOfAsgard() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(TrialOfAsgard::tick);
	}

	public static void clearSessionState() {
		RUNS.clear();
	}

	public static boolean anyRunning() {
		return !RUNS.isEmpty();
	}

	public static Trial running(java.util.UUID player) {
		return RUNS.get(player);
	}

	/** Trial centres in {@code level} (for the storm cloud's trial look). */
	public static List<BlockPos> centers(ServerLevel level) {
		List<BlockPos> out = new ArrayList<>();
		for (Trial t : RUNS.values()) {
			if (t.dimension == level.dimension() && t.stage != Stage.WON) {
				out.add(t.center);
			}
		}
		return out;
	}

	/** Whether a running trial owns {@code boss} (an unowned trial guardian dissipates). */
	public static boolean owns(StormwardenBoss boss) {
		for (Trial t : RUNS.values()) {
			if (boss.getUUID().equals(t.boss)) {
				return true;
			}
		}
		return false;
	}

	/** The player has full Valor and it is storming: start the trial, or say why not. */
	public static MjolnirLift.Result tryStart(ServerPlayer player, MjolnirEntity hammer) {
		Trial mine = RUNS.get(player.getUUID());
		if (mine != null) {
			MjolnirLift.feedback(player, hammer.position(), Component.translatable("message.projecthero.trial.running"), false);
			return MjolnirLift.Result.TRIAL_RUNNING;
		}
		for (Trial t : RUNS.values()) {
			if (hammer.getUUID().equals(t.hammer) || t.center.closerThan(hammer.blockPosition(), 3)) {
				MjolnirLift.feedback(player, hammer.position(), Component.translatable("message.projecthero.trial.someone_else"), false);
				return MjolnirLift.Result.TRIAL_RUNNING;
			}
		}
		if (HeroTiers.hasAnyPower(player)) {
			MjolnirLift.feedback(player, hammer.position(), Component.translatable("message.projecthero.trial.powered"), true);
			return MjolnirLift.Result.POWERED;
		}
		long storm = Storms.current(player.server);
		if (player.getAttachedOrElse(ThorAcquireAttachments.TRIAL_FAILED_STORM, -1L) == storm) {
			MjolnirLift.feedback(player, hammer.position(), Component.translatable("message.projecthero.trial.next_storm"), true);
			return MjolnirLift.Result.RETRY_NEXT_STORM;
		}
		start(player, hammer.blockPosition(), hammer.getUUID());
		return MjolnirLift.Result.TRIAL_STARTED;
	}

	/** Starts a trial at {@code center} (the hammer's spot). Public for tests and the harness. */
	public static Trial start(ServerPlayer player, BlockPos center, UUID hammer) {
		Trial t = new Trial(player.getUUID(), player.level().dimension(), center.immutable(), hammer);
		RUNS.put(player.getUUID(), t);
		ServerLevel level = player.serverLevel();
		Component title = Component.translatable("projecthero.trial.title").withStyle(s -> s.withColor(0x9FD8FF).withBold(true));
		Component sub = Component.translatable("projecthero.trial.sub").withStyle(ChatFormatting.GRAY);
		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(title));
		player.connection.send(new ClientboundSetSubtitleTextPacket(sub));
		player.sendSystemMessage(Component.translatable("message.projecthero.trial.start").withStyle(ChatFormatting.AQUA));
		level.playSound(null, center, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 3.0f, 0.5f);
		return t;
	}

	private static void tick(MinecraftServer server) {
		if (RUNS.isEmpty()) {
			return;
		}
		Iterator<Trial> it = RUNS.values().iterator();
		while (it.hasNext()) {
			Trial t = it.next();
			if (step(server, t)) {
				it.remove();
			}
		}
	}

	/** One tick of a trial; true when it is over (won and lifted, or lost). */
	private static boolean step(MinecraftServer server, Trial t) {
		ServerLevel level = server.getLevel(t.dimension);
		ServerPlayer p = server.getPlayerList().getPlayer(t.player);
		t.ticks++;
		if (level == null) {
			return true;
		}
		StormwardenBoss boss = t.boss == null ? null : level.getEntity(t.boss) instanceof StormwardenBoss b ? b : null;
		if (t.stage == Stage.WON) {
			return stepWon(level, p, t);
		}
		String loss = lossReason(level, p, t, boss);
		if (loss != null) {
			lose(level, p, t, boss, loss);
			return true;
		}
		Vec3 c = Vec3.atBottomCenterOf(t.center);
		if (t.stage == Stage.INTRO) {
			if (t.ticks == 1 || t.ticks == 14 || t.ticks == 28) {
				MjolnirLift.bolt(level, c);
				level.playSound(null, t.center, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 2.0f, 0.7f);
				level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
			}
			if (t.ticks % 2 == 0) {
				level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.2, c.z, 10, 2.5, 0.05, 2.5, 0.05);
			}
			if (t.ticks >= INTRO_TICKS) {
				boss = spawnGuardian(level, t, p);
				t.stage = Stage.FIGHT;
			}
			return false;
		}
		if (boss == null || !boss.isAlive()) {
			win(level, p, t);
		}
		return false;
	}

	/** Why the trial is lost right now, or null. */
	static String lossReason(ServerLevel level, ServerPlayer p, Trial t, StormwardenBoss boss) {
		if (p == null || p.isRemoved()) {
			return "left";
		}
		if (!p.isAlive()) {
			return "died";
		}
		if (p.level() != level || p.distanceToSqr(Vec3.atCenterOf(t.center)) > LEASH * LEASH) {
			return "fled";
		}
		if (!Storms.isStorming(level, t.center)) {
			return "storm";
		}
		if (HeroTiers.hasAnyPower(p)) {
			return "powered";
		}
		return null;
	}

	private static StormwardenBoss spawnGuardian(ServerLevel level, Trial t, ServerPlayer p) {
		StormwardenBoss boss = ThorAcquire.STORMWARDEN.create(level);
		if (boss == null) {
			return null;
		}
		Vec3 c = Vec3.atBottomCenterOf(t.center);
		Vec3 away = p != null ? p.position().subtract(c).multiply(1, 0, 1) : new Vec3(0, 0, 1);
		away = away.lengthSqr() < 1.0e-3 ? new Vec3(0, 0, 1) : away.normalize();
		// it forms between the hammer and the challenger, a few blocks from the plinth
		Vec3 at = c.add(away.scale(4.0));
		int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(at.x), (int) Math.floor(at.z));
		boss.moveTo(at.x, Math.abs(y - c.y) < 6 ? y : c.y, at.z, (float) Math.toDegrees(Math.atan2(-away.x, away.z)), 0f);
		boss.bindToTrial(t.center);
		if (p != null) {
			boss.setTarget(p);
		}
		level.addFreshEntity(boss);
		t.boss = boss.getUUID();
		MjolnirLift.bolt(level, boss.position());
		level.playSound(null, boss.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.0f, 1.2f);
		return boss;
	}

	private static void win(ServerLevel level, ServerPlayer p, Trial t) {
		t.stage = Stage.WON;
		t.ticks = 0;
		if (p == null) {
			return;
		}
		p.setAttached(ThorAcquireAttachments.TRIAL_PASSED, true);
		Component title = Component.translatable("projecthero.trial.won.title").withStyle(s -> s.withColor(0xFFD866).withBold(true));
		p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 20));
		p.connection.send(new ClientboundSetTitleTextPacket(title));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("projecthero.trial.won.sub").withStyle(ChatFormatting.GRAY)));
		level.playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
	}

	/** After a win: wait for the guardian's fall, then the hammer answers -- if it is still storming and the hero is near. */
	private static boolean stepWon(ServerLevel level, ServerPlayer p, Trial t) {
		if (t.ticks < LIFT_DELAY) {
			return false;
		}
		if (p == null || !p.isAlive() || p.level() != level) {
			return true;
		}
		if (!Storms.isStorming(level, t.center)) {
			p.sendSystemMessage(Component.translatable("message.projecthero.trial.won_no_storm").withStyle(ChatFormatting.AQUA));
			return true;
		}
		MjolnirEntity hammer = findHammer(level, t);
		if (hammer == null || p.distanceToSqr(hammer) > LEASH * LEASH) {
			p.sendSystemMessage(Component.translatable("message.projecthero.trial.won_no_storm").withStyle(ChatFormatting.AQUA));
			return true;
		}
		// it flies to the champion's feet first, then the lift plays
		MjolnirLift.begin(p, hammer);
		return true;
	}

	private static MjolnirEntity findHammer(ServerLevel level, Trial t) {
		if (t.hammer != null && level.getEntity(t.hammer) instanceof MjolnirEntity m && !m.isRemoved()) {
			return m;
		}
		return level.getEntitiesOfClass(MjolnirEntity.class, new AABB(t.center).inflate(12),
				m -> m.getState() == MjolnirEntity.State.RESTING
						&& m.getItem().get(com.projecthero.mod.item.ModDataComponents.BOUND_OWNER) == null).stream().findFirst().orElse(null);
	}

	private static void lose(ServerLevel level, ServerPlayer p, Trial t, StormwardenBoss boss, String reason) {
		com.projecthero.mod.ProjectHeroMod.LOGGER.info("[TrialOfAsgard] {} lost the trial at {}: {}", p != null ? p.getGameProfile().getName() : t.player, t.center, reason);
		if (boss != null) {
			boss.dissipate();
		}
		for (Entity e : level.getEntitiesOfClass(com.projecthero.mod.thoracquire.entity.StormSpiritEntity.class, new AABB(t.center).inflate(48))) {
			e.discard();
		}
		if (p != null) {
			p.setAttached(ThorAcquireAttachments.TRIAL_FAILED_STORM, Storms.current(level.getServer()));
			p.sendSystemMessage(Component.translatable("message.projecthero.trial.lost." + reason).withStyle(ChatFormatting.RED));
			p.sendSystemMessage(Component.translatable("message.projecthero.trial.lost_hint").withStyle(ChatFormatting.GRAY));
		}
	}

	/** Test hook: ends a running trial as a loss for {@code reason} right now. */
	public static void loseForTest(ServerLevel level, ServerPlayer p, String reason) {
		Trial t = RUNS.remove(p.getUUID());
		if (t != null) {
			StormwardenBoss boss = t.boss == null ? null : level.getEntity(t.boss) instanceof StormwardenBoss b ? b : null;
			lose(level, p, t, boss, reason);
		}
	}
}
