package com.projecthero.mod.syndicate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.projecthero.mod.event.EventBossBar;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventState;
import com.projecthero.mod.syndicate.entity.KingpinEntity;
import com.projecthero.mod.syndicate.entity.SyndicateGunman;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * v0.14.25: a Syndicate Bust -- the raid on a {@link SyndicateHideout}. It starts when somebody walks into the
 * warehouse (or opens the stash), and runs:
 * <ol>
 *   <li><b>Spotted</b> (4 s): "The Syndicate spotted you!"</li>
 *   <li><b>Five waves</b>, each bigger and better armed, walking in through the roller door and side doors (and, early
 *       on, already waiting in the back room). Snipers take the catwalk from wave 4; Enforcers arrive from wave 4.
 *       Each wave grows by half for every extra player.</li>
 *   <li><b>The Kingpin</b> walks out of his office with a bodyguard or two, drops off the balcony and fights. He calls
 *       more crooks at two-thirds and one-third health.</li>
 * </ol>
 * Beat him and the stash becomes the loot chest ({@link SyndicateRewards}). Everybody leaves (or dies and respawns far
 * away) and the bust pauses, then fails after the framework's abandon timer: the crooks clear out and the stash locks
 * again, ready for another try. The warehouse stays either way.
 */
public class SyndicateBust extends EventInstance {
	public static final String TYPE_ID = "syndicate_bust";
	public static final int WAVES = 5;
	static final int SPOTTED_TICKS = 4 * 20;
	static final int BREATHER_TICKS = 7 * 20;
	static final int STRAGGLER_TICKS = 60 * 20;
	static final double ARENA = 40.0;

	/** Per wave: thugs, pistols, shotguns, snipers, enforcers (for one player). */
	static final int[][] WAVE_MIX = {
			{ 6, 0, 0, 0, 0 },
			{ 5, 3, 0, 0, 0 },
			{ 4, 3, 2, 0, 0 },
			{ 2, 3, 1, 2, 1 },
			{ 4, 3, 2, 2, 1 } };

	private enum Phase { SPOTTED, WAVE, BREATHER, BOSS }

	private Direction forward = Direction.NORTH;
	private Phase phase = Phase.SPOTTED;
	private long phaseStart = -1;
	private int wave;
	private int waveTotal;
	private UUID bossId;
	private final List<Pending> queue = new ArrayList<>();
	private final Set<UUID> fighters = new HashSet<>();
	private transient EventBossBar bar;

	/** One crook still to walk in this wave. */
	private record Pending(int role, int where) {
	}

	private static final int THUG = 0, PISTOL = 1, SHOTGUN = 2, SNIPER = 3, ENFORCER = 4;
	private static final int DOOR = 0, BACK = 1, CATWALK = 2;

	public SyndicateBust(UUID id) {
		super(id);
	}

	SyndicateBust(UUID id, Direction forward) {
		super(id);
		this.forward = forward;
	}

	/** Starts a bust on the stash at {@code stash}. False if one is already running there. */
	public static boolean start(ServerLevel level, BlockPos stash) {
		var state = level.getBlockState(stash);
		if (!(state.getBlock() instanceof SyndicateStashBlock) || state.getValue(SyndicateStashBlock.ACTIVE)) {
			return false;
		}
		SyndicateBust bust = new SyndicateBust(UUID.randomUUID(), state.getValue(SyndicateStashBlock.FACING));
		if (!EventManager.start(level, bust, stash)) {
			return false;
		}
		level.setBlock(stash, state.setValue(SyndicateStashBlock.ACTIVE, true), 3);
		return true;
	}

	@Override
	public String typeId() {
		return TYPE_ID;
	}

	@Override
	public Component displayName() {
		return Component.translatable("event.projecthero.syndicate_bust");
	}

	@Override
	public double radius() {
		return ARENA + 16;
	}

	public Direction forward() {
		return forward;
	}

	public BlockPos origin() {
		return SyndicateHideout.originFromStash(center(), forward);
	}

	public int wave() {
		return wave;
	}

	public boolean bossPhase() {
		return phase == Phase.BOSS;
	}

	// ---------------------------------------------------------------- tick

	@Override
	protected void onTick(ServerLevel level) {
		long now = level.getGameTime();
		if (state() == EventState.PENDING) {
			setState(EventState.RUNNING);
			phaseStart = now;
			broadcastTitle(level, Component.translatable("event.projecthero.syndicate.spotted").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
					Component.translatable("event.projecthero.syndicate.spotted.sub").withStyle(ChatFormatting.GRAY));
			level.playSound(null, center(), SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0f, 0.7f);
		}
		if (phaseStart < 0) {
			phaseStart = now;
		}
		keepTheStash(level);
		noteFighters(level);
		tetherOwnedMobs(level, ARENA);
		long inPhase = now - phaseStart;
		if (phase != Phase.SPOTTED) {
			retarget(level);
		}
		switch (phase) {
			case SPOTTED -> {
				if (inPhase >= SPOTTED_TICKS) {
					startWave(level, now, 1);
				}
			}
			case WAVE -> tickWave(level, now, inPhase);
			case BREATHER -> {
				if (inPhase >= BREATHER_TICKS) {
					if (wave >= WAVES) {
						startBoss(level, now);
					} else {
						startWave(level, now, wave + 1);
					}
				}
			}
			case BOSS -> tickBoss(level);
		}
		if (!state().finished()) {
			updateBar(level, now);
		}
	}

	private void keepTheStash(ServerLevel level) {
		var state = level.getBlockState(center());
		if (!(state.getBlock() instanceof SyndicateStashBlock)) {
			level.setBlock(center(), SyndicateItems.STASH.defaultBlockState().setValue(SyndicateStashBlock.FACING, forward)
					.setValue(SyndicateStashBlock.ACTIVE, true), 3);
		} else if (!state.getValue(SyndicateStashBlock.ACTIVE)) {
			level.setBlock(center(), state.setValue(SyndicateStashBlock.ACTIVE, true), 3);
		}
	}

	/** Everyone who comes within the arena counts as having been on the bust (for the reward's party size). */
	private void noteFighters(ServerLevel level) {
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(Vec3.atCenterOf(center())) < ARENA * ARENA) {
				fighters.add(p.getUUID());
			}
		}
	}

	private int presentFighters(ServerLevel level) {
		int n = 0;
		for (ServerPlayer p : level.players()) {
			if (p.isAlive() && !p.isSpectator() && !p.isCreative() && p.distanceToSqr(Vec3.atCenterOf(center())) < ARENA * ARENA) {
				n++;
			}
		}
		return Math.max(1, n);
	}

	private void retarget(ServerLevel level) {
		for (Mob m : liveOwnedMobs(level)) {
			LivingEntity t = m.getTarget();
			if (t == null || !t.isAlive() || t instanceof Player p && (p.isCreative() || p.isSpectator())) {
				Player next = level.getNearestPlayer(m.getX(), m.getY(), m.getZ(), ARENA + 8,
						e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive());
				if (next != null) {
					m.setTarget(next);
				}
			}
		}
	}

	// ---------------------------------------------------------------- waves

	/** How many of each role wave {@code n} brings for {@code players}. */
	public static int[] waveMix(int n, int players) {
		int[] base = WAVE_MIX[Mth.clamp(n, 1, WAVES) - 1];
		double scale = 1.0 + 0.5 * Math.max(0, players - 1);
		int[] out = new int[5];
		for (int i = 0; i < 5; i++) {
			out[i] = (int) Math.round(base[i] * scale);
		}
		out[SNIPER] = Math.min(out[SNIPER], 4);
		out[ENFORCER] = Math.min(out[ENFORCER], 4);
		return out;
	}

	private void startWave(ServerLevel level, long now, int n) {
		wave = n;
		phase = Phase.WAVE;
		phaseStart = now;
		queue.clear();
		int[] mix = waveMix(n, presentFighters(level));
		for (int role = 0; role < 5; role++) {
			for (int i = 0; i < mix[role]; i++) {
				int where = role == SNIPER ? CATWALK : (n == 1 || (n >= 3 && level.random.nextInt(3) == 0)) && role != ENFORCER ? BACK : DOOR;
				queue.add(new Pending(role, where));
			}
		}
		java.util.Collections.shuffle(queue, new java.util.Random(level.random.nextLong()));
		waveTotal = queue.size();
		broadcastTitle(level, Component.translatable("event.projecthero.syndicate.wave", n, WAVES).withStyle(ChatFormatting.GOLD),
				Component.translatable("event.projecthero.syndicate.wave" + n + ".sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.6f, 0.7f);
	}

	private void tickWave(ServerLevel level, long now, long inPhase) {
		int alive = ownedAlive(level);
		if (!queue.isEmpty()) {
			int room = EventConfig.framework().maxLiveMobs - alive;
			for (int i = 0; i < 3 && !queue.isEmpty() && room > 0; i++, room--) {
				Pending p = queue.remove(queue.size() - 1);
				spawn(level, p.role(), p.where());
			}
			return;
		}
		if (alive == 0) {
			phase = Phase.BREATHER;
			phaseStart = now;
			broadcast(level, Component.translatable(wave >= WAVES ? "event.projecthero.syndicate.boss_soon" : "event.projecthero.syndicate.wave_cleared",
					wave).withStyle(ChatFormatting.GOLD));
			level.playSound(null, center(), SoundEvents.UI_TOAST_IN, SoundSource.PLAYERS, 1.0f, 1.0f);
			return;
		}
		if (alive <= 4 && inPhase > STRAGGLER_TICKS) {
			for (Mob m : liveOwnedMobs(level)) {
				m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
				if (inPhase > STRAGGLER_TICKS * 2) {
					disown(m.getUUID());
					m.discard();
				}
			}
		}
	}

	/** One crook of {@code role}, walking in from {@code where}. */
	private Mob spawn(ServerLevel level, int role, int where) {
		Mob mob = switch (role) {
			case THUG -> SyndicateEntityTypes.THUG.create(level);
			case ENFORCER -> SyndicateEntityTypes.ENFORCER.create(level);
			default -> SyndicateEntityTypes.GUNMAN.create(level);
		};
		if (mob == null) {
			return null;
		}
		BlockPos o = origin();
		List<Vec3> spots = switch (where) {
			case BACK -> SyndicateHideout.backRoomSpawns(o, forward);
			case CATWALK -> SyndicateHideout.catwalkSpawns(o, forward);
			default -> SyndicateHideout.doorSpawns(level, o, forward);
		};
		Vec3 at = spots.get(level.random.nextInt(spots.size()));
		mob.moveTo(at.x + (level.random.nextDouble() - 0.5) * 0.6, at.y, at.z + (level.random.nextDouble() - 0.5) * 0.6,
				level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		if (mob instanceof SyndicateGunman g) {
			g.setKind(switch (role) {
				case SHOTGUN -> SyndicateGunman.Kind.SHOTGUN;
				case SNIPER -> SyndicateGunman.Kind.SNIPER;
				default -> SyndicateGunman.Kind.PISTOL;
			});
		}
		mob.restrictTo(center(), (int) ARENA);
		own(mob);
		level.addFreshEntity(mob);
		Player target = level.getNearestPlayer(mob.getX(), mob.getY(), mob.getZ(), ARENA + 8,
				e -> e instanceof Player p && !p.isCreative() && !p.isSpectator() && p.isAlive());
		if (target != null) {
			mob.setTarget(target);
		}
		if (where == DOOR) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
		}
		return mob;
	}

	/** The Kingpin's "Boys!": more crooks through the doors, more for a bigger party. */
	public void reinforce(ServerLevel level, int players) {
		int n = 2 + players;
		for (int i = 0; i < n; i++) {
			int role = i % 3 == 2 ? SHOTGUN : (i % 3 == 1 ? PISTOL : THUG);
			spawn(level, role, DOOR);
		}
		if (players >= 3) {
			spawn(level, ENFORCER, DOOR);
		}
	}

	// ---------------------------------------------------------------- boss

	private void startBoss(ServerLevel level, long now) {
		phase = Phase.BOSS;
		phaseStart = now;
		if (bar != null) {
			bar.clear(level); // the Kingpin has his own bar
		}
		KingpinEntity kingpin = SyndicateEntityTypes.KINGPIN.create(level);
		if (kingpin == null) {
			complete(level);
			return;
		}
		Vec3 at = SyndicateHideout.officeSpot(origin(), forward);
		Vec3 look = SyndicateHideout.floorCentre(origin(), forward);
		float yaw = (float) Math.toDegrees(Math.atan2(-(look.x - at.x), look.z - at.z));
		kingpin.moveTo(at.x, at.y, at.z, yaw, 0f);
		kingpin.finalizeSpawn(level, level.getCurrentDifficultyAt(kingpin.blockPosition()), MobSpawnType.EVENT, null);
		kingpin.configure(presentFighters(level));
		kingpin.restrictTo(center(), (int) ARENA);
		own(kingpin);
		level.addFreshEntity(kingpin);
		bossId = kingpin.getUUID();
		Player target = level.getNearestPlayer(kingpin, ARENA + 8);
		if (target != null && !target.isCreative() && !target.isSpectator()) {
			kingpin.setTarget(target);
		}
		int guards = presentFighters(level) >= 3 ? 2 : 1;
		for (int i = 0; i < guards; i++) {
			spawn(level, ENFORCER, BACK);
		}
		broadcastTitle(level, Component.translatable("entity.projecthero.kingpin").withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD),
				Component.translatable("event.projecthero.syndicate.boss.sub").withStyle(ChatFormatting.GRAY));
		level.playSound(null, center(), SoundEvents.IRON_DOOR_OPEN, SoundSource.HOSTILE, 2.0f, 0.6f);
		level.playSound(null, center(), SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 1.4f, 0.8f);
	}

	private LivingEntity boss(ServerLevel level) {
		return bossId == null ? null : level.getEntity(bossId) instanceof LivingEntity l ? l : null;
	}

	private void tickBoss(ServerLevel level) {
		LivingEntity boss = boss(level);
		if (boss == null || !boss.isAlive() || boss.isDeadOrDying()) {
			broadcastTitle(level, Component.translatable("event.projecthero.syndicate.victory").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
					Component.translatable("event.projecthero.syndicate.victory.sub").withStyle(ChatFormatting.GRAY));
			complete(level);
		}
	}

	/** {@code /projecthero raid advancetimer syndicate}: on to the next stage now. */
	public void debugAdvance(ServerLevel level) {
		long now = level.getGameTime();
		switch (phase) {
			case SPOTTED -> startWave(level, now, 1);
			case WAVE -> {
				queue.clear();
				for (Mob m : liveOwnedMobs(level)) {
					disown(m.getUUID());
					m.discard();
				}
				phase = Phase.BREATHER;
				phaseStart = now - BREATHER_TICKS;
			}
			case BREATHER -> phaseStart = now - BREATHER_TICKS;
			case BOSS -> {
				LivingEntity boss = boss(level);
				if (boss != null) {
					boss.kill();
				}
			}
		}
	}

	// ---------------------------------------------------------------- the end

	@Override
	protected void onFinished(ServerLevel level, boolean success) {
		tearDown(level);
		BlockPos c = center();
		if (success) {
			int party = 0;
			for (UUID u : fighters) {
				if (level.getPlayerByUUID(u) != null) {
					party++;
				}
			}
			SyndicateRewards.placeChest(level, c, forward, Math.max(1, party));
			level.playSound(null, c, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0f, 1.0f);
		} else {
			relock(level);
		}
	}

	@Override
	protected void onAborted(ServerLevel level) {
		tearDown(level);
		relock(level);
	}

	@Override
	protected void onPaused(ServerLevel level) {
		super.onPaused(level);
		if (bar != null) {
			bar.clear(level);
		}
	}

	private void relock(ServerLevel level) {
		var state = level.getBlockState(center());
		if (state.getBlock() instanceof SyndicateStashBlock) {
			level.setBlock(center(), state.setValue(SyndicateStashBlock.ACTIVE, false), 3);
		}
	}

	private void tearDown(ServerLevel level) {
		if (bar != null) {
			bar.clear(level);
		}
		LivingEntity boss = boss(level);
		if (boss != null && boss.isAlive()) {
			boss.discard();
		}
	}

	private void updateBar(ServerLevel level, long now) {
		if (phase == Phase.BOSS) {
			return;
		}
		if (bar == null) {
			bar = new EventBossBar(id(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_10, false);
		}
		long inPhase = now - phaseStart;
		Component name;
		float progress;
		switch (phase) {
			case SPOTTED -> {
				name = Component.translatable("event.projecthero.syndicate.bar.spotted", displayName());
				progress = Mth.clamp(inPhase / (float) SPOTTED_TICKS, 0f, 1f);
			}
			case BREATHER -> {
				name = Component.translatable("event.projecthero.syndicate.bar.breather", displayName(), wave, WAVES);
				progress = Mth.clamp(inPhase / (float) BREATHER_TICKS, 0f, 1f);
			}
			default -> {
				int left = queue.size() + ownedAlive(level);
				name = Component.translatable("event.projecthero.syndicate.bar.wave", displayName(), wave, WAVES, left);
				progress = waveTotal <= 0 ? 0f : Mth.clamp(left / (float) waveTotal, 0f, 1f);
			}
		}
		bar.update(level, center(), ARENA + 16, name.copy().withStyle(ChatFormatting.GOLD), progress);
	}

	// ---------------------------------------------------------------- save

	@Override
	protected void saveExtra(CompoundTag tag) {
		tag.putString("Forward", forward.getName());
		tag.putString("BustPhase", phase.name());
		tag.putLong("BustPhaseStart", phaseStart);
		tag.putInt("BustWave", wave);
		tag.putInt("BustWaveTotal", waveTotal);
		if (bossId != null) {
			tag.putUUID("BustBoss", bossId);
		}
		int[] q = new int[queue.size() * 2];
		for (int i = 0; i < queue.size(); i++) {
			q[i * 2] = queue.get(i).role();
			q[i * 2 + 1] = queue.get(i).where();
		}
		tag.putIntArray("BustQueue", q);
		ListTag list = new ListTag();
		for (UUID u : fighters) {
			list.add(NbtUtils.createUUID(u));
		}
		tag.put("BustFighters", list);
	}

	@Override
	protected void loadExtra(CompoundTag tag) {
		Direction d = Direction.byName(tag.getString("Forward"));
		forward = d == null || d.getAxis().isVertical() ? Direction.NORTH : d;
		try {
			phase = Phase.valueOf(tag.getString("BustPhase"));
		} catch (IllegalArgumentException e) {
			phase = Phase.SPOTTED;
		}
		phaseStart = tag.getLong("BustPhaseStart");
		wave = tag.getInt("BustWave");
		waveTotal = tag.getInt("BustWaveTotal");
		bossId = tag.hasUUID("BustBoss") ? tag.getUUID("BustBoss") : null;
		queue.clear();
		int[] q = tag.getIntArray("BustQueue");
		for (int i = 0; i + 1 < q.length; i += 2) {
			queue.add(new Pending(q[i], q[i + 1]));
		}
		fighters.clear();
		ListTag list = tag.getList("BustFighters", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			fighters.add(NbtUtils.loadUUID(list.get(i)));
		}
	}
}
