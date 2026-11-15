package com.projecthero.mod.hideout;

import java.util.HashSet;
import java.util.Set;

import com.projecthero.mod.event.boss.BossThreat;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * v0.17.5: what every hideout mini-boss shares, as a component (each boss extends its faction's own mob class, so this
 * can't be a base class):
 * <ul>
 * <li>its hideout's <b>core</b> position (saved), told when the boss dies ({@link HideoutClearing#bossDefeated});</li>
 * <li>a <b>boss bar</b> shown to players within {@link #BAR_RANGE} blocks (not the whole tracking range -- the hideout
 * is a place, the bar shouldn't follow you out of it);</li>
 * <li>the shared <b>threat table</b> ({@link BossThreat}): whoever hurts it most takes its attention;</li>
 * <li>a <b>leash</b>: it walks home when it has strayed {@link #LEASH} blocks from its core with nobody to fight, and is
 * put back if it ends up {@link #TETHER} blocks away.</li>
 * </ul>
 * No static state; instance fields only.
 */
public final class HideoutBossLink {
	public static final double BAR_RANGE = 40.0;
	public static final double LEASH = 24.0;
	public static final double TETHER = 64.0;

	private final Mob boss;
	private final HideoutFaction faction;
	private final BossThreat threat;
	private final ServerBossEvent bar;
	private final Set<ServerPlayer> viewers = new HashSet<>();
	private BlockPos core;
	private boolean reported;

	public HideoutBossLink(Mob boss, HideoutFaction faction, BossEvent.BossBarColor colour, java.util.function.BooleanSupplier busy) {
		this.boss = boss;
		this.faction = faction;
		this.threat = new BossThreat(boss).range(48).holdWhile(busy);
		this.bar = new ServerBossEvent(faction.bossName(), colour, BossEvent.BossBarOverlay.NOTCHED_10);
	}

	public HideoutFaction faction() {
		return faction;
	}

	public BossThreat threat() {
		return threat;
	}

	public ServerBossEvent bar() {
		return bar;
	}

	/** The core this boss guards, or null (a spawn-egg boss). */
	public BlockPos core() {
		return core;
	}

	public void bindCore(BlockPos core) {
		this.core = core == null ? null : core.immutable();
	}

	/** Call from {@code hurt} after a hit landed. */
	public void onHurt(DamageSource source, float amount) {
		threat.record(source, amount);
	}

	/**
	 * Once per server tick: the threat table, the bar's progress and viewers (every 10 ticks), the leash (every 40).
	 * Returns the threat table's new target if it just switched, else null.
	 */
	public LivingEntity tick(ServerLevel level) {
		LivingEntity switched = threat.tick();
		bar.setProgress(Mth.clamp(boss.getHealth() / boss.getMaxHealth(), 0f, 1f));
		if (boss.tickCount % 10 == 0) {
			refreshViewers(level);
		}
		if (core != null && boss.tickCount % 40 == 0) {
			leash(level);
		}
		return switched;
	}

	private void refreshViewers(ServerLevel level) {
		viewers.removeIf(p -> {
			boolean keep = p.isAlive() && !p.isRemoved() && p.level() == level && p.distanceToSqr(boss) <= (BAR_RANGE + 8) * (BAR_RANGE + 8);
			if (!keep) {
				bar.removePlayer(p);
			}
			return !keep;
		});
		for (ServerPlayer p : level.players()) {
			if (!viewers.contains(p) && !p.isSpectator() && p.distanceToSqr(boss) <= BAR_RANGE * BAR_RANGE) {
				viewers.add(p);
				bar.addPlayer(p);
			}
		}
	}

	private void leash(ServerLevel level) {
		double d = Math.sqrt(boss.distanceToSqr(core.getX() + 0.5, core.getY(), core.getZ() + 0.5));
		LivingEntity t = boss.getTarget();
		boolean engaged = t != null && t.isAlive() && t.distanceToSqr(boss) < 16 * 16;
		if (d > TETHER) {
			boss.moveTo(core.getX() + 0.5, core.getY() + 1, core.getZ() + 2.5, boss.getYRot(), 0f);
			boss.getNavigation().stop();
			boss.setTarget(null);
			threat.clear();
		} else if (d > LEASH && !engaged) {
			boss.getNavigation().moveTo(core.getX() + 0.5, core.getY() + 1, core.getZ() + 2.5, 1.0);
		}
	}

	/** Call from {@code die}. Reports the death to the hideout once. */
	public void onDeath(ServerLevel level) {
		bar.removeAllPlayers();
		viewers.clear();
		if (!reported) {
			reported = true;
			HideoutClearing.bossDefeated(level, core, faction);
		}
	}

	/** Call from {@code remove} / {@code stopSeenByPlayer}-style cleanup. */
	public void detach() {
		bar.removeAllPlayers();
		viewers.clear();
	}

	public void save(CompoundTag tag) {
		if (core != null) {
			tag.put("HideoutCore", NbtUtils.writeBlockPos(core));
		}
	}

	public void load(CompoundTag tag) {
		core = NbtUtils.readBlockPos(tag, "HideoutCore").orElse(null);
	}
}
