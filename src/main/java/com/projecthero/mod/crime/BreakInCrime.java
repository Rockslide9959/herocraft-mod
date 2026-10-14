package com.projecthero.mod.crime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.syndicate.SyndicateEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: a break-in. 2-4 crooks walk in from {@code breakIn.approachDistance} blocks out, rob a chest in one of the
 * village's houses (or the bell square, if no chest is near), rummage for {@code breakIn.lootSeconds}, and then run for
 * it, a loot sack each. A crook who is hit before he has finished runs straight away, empty-handed.
 *
 * <p>They never fight back. Put one down while he carries a sack and the stolen goods spill at his feet; one that gets
 * {@code breakIn.escapeRadius} blocks from the bell is gone. The crime is stopped if at least as many crooks were put
 * down as got away (the reward scales with how many were stopped); otherwise they got away with it.
 *
 * <p>The sacks hold made-up village goods (emeralds, bread, crops, a little iron or gold), never what is really in the
 * chest -- a village chest may be a player's own storage, and a crime should never cost anybody their things.
 */
public class BreakInCrime extends CrimeEvent {
	private enum Step { APPROACH, LOOT, FLEE }

	private static final class Crook {
		Step step = Step.APPROACH;
		long since;

		Crook(long since) {
			this.since = since;
		}
	}

	private BlockPos target;
	private boolean chestTarget;
	private final Map<UUID, Crook> crooks = new HashMap<>();
	private int total;
	private int stopped;
	private int escaped;
	private int recovered;

	public BreakInCrime(UUID id) {
		super(id);
	}

	@Override
	public CrimeType crimeType() {
		return CrimeType.BREAK_IN;
	}

	public BlockPos target() {
		return target;
	}

	public int total() {
		return total;
	}

	public int stopped() {
		return stopped;
	}

	public int escaped() {
		return escaped;
	}

	@Override
	protected int timeLimitTicks() {
		return CrimeConfig.breakIn().timeLimitSeconds * 20;
	}

	/** A chest within 40 blocks of {@code around}, in already-loaded chunks only, or {@code null}. */
	static BlockPos findChest(ServerLevel level, BlockPos around) {
		List<BlockPos> found = new ArrayList<>();
		ChunkPos c = new ChunkPos(around);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(c.x + dx, c.z + dz);
				if (chunk == null) {
					continue;
				}
				for (BlockEntity be : chunk.getBlockEntities().values()) {
					if (be instanceof ChestBlockEntity && be.getBlockPos().distSqr(around) <= 40 * 40) {
						found.add(be.getBlockPos());
					}
				}
			}
		}
		return found.isEmpty() ? null : found.get(level.random.nextInt(found.size()));
	}

	@Override
	protected void begin(ServerLevel level) {
		target = findChest(level, village);
		chestTarget = target != null;
		if (target == null) {
			target = village;
		}
		CrimeConfig.BreakIn cfg = CrimeConfig.breakIn();
		int lo = Math.max(1, Math.min(cfg.minCrooks, cfg.maxCrooks));
		int hi = Math.max(lo, cfg.maxCrooks);
		int n = lo + level.random.nextInt(hi - lo + 1);
		long now = level.getGameTime();
		for (int i = 0; i < n; i++) {
			BlockPos spot = CrimeVillages.spotNear(level, target, Math.max(2, cfg.approachDistance - 4), cfg.approachDistance + 4, level.random);
			if (spot == null) {
				spot = CrimeVillages.spotNear(level, target, 2, 6, level.random);
			}
			Mob m = spawnCrook(level, SyndicateEntityTypes.THUG, spot == null ? target.above() : spot, CrimeAI.Role.RUNNER, null);
			if (m != null) {
				crooks.put(m.getUUID(), new Crook(now));
				total++;
			}
		}
		if (total == 0) {
			failCrime(level, "event.projecthero.crime.fizzled");
		}
	}

	@Override
	protected void tickCrime(ServerLevel level) {
		long now = level.getGameTime();
		CrimeConfig.BreakIn cfg = CrimeConfig.breakIn();
		List<Mob> live = crooks(level);
		boolean anyLooting = false;
		for (Mob m : live) {
			Crook c = crooks.computeIfAbsent(m.getUUID(), id -> new Crook(now));
			switch (c.step) {
				case APPROACH -> {
					if (recentlyHitByPlayer(m)) {
						flee(level, m, c, now, false);
						break;
					}
					Vec3 t = Vec3.atBottomCenterOf(target);
					double dh = Math.hypot(m.getX() - t.x, m.getZ() - t.z);
					if (dh <= 2.5 && Math.abs(m.getY() - t.y) < 3.0 || now - c.since > 25 * 20) {
						c.step = Step.LOOT;
						c.since = now;
						m.getNavigation().stop();
						if (chestTarget) {
							level.blockEvent(target, level.getBlockState(target).getBlock(), 1, 1);
							level.playSound(null, target, SoundEvents.CHEST_OPEN, SoundSource.BLOCKS, 0.8f, 0.9f);
						}
					} else if (m.getNavigation().isDone() || m.tickCount % 40 == 0) {
						m.getNavigation().moveTo(t.x, t.y, t.z, 0.85);
					}
				}
				case LOOT -> {
					anyLooting = true;
					m.getLookControl().setLookAt(Vec3.atCenterOf(target));
					long in = now - c.since;
					if (recentlyHitByPlayer(m)) {
						flee(level, m, c, now, in > cfg.lootSeconds * 10L);
						break;
					}
					if (in % 30 == 0) {
						level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.HOSTILE, 0.8f, 0.8f);
					}
					if (in >= cfg.lootSeconds * 20L) {
						flee(level, m, c, now, true);
					}
				}
				case FLEE -> {
					if (horizontalFromVillage(m.position()) >= cfg.escapeRadius) {
						escaped++;
						crooks.remove(m.getUUID());
						vanish(level, m);
						tellNearby(level, Component.translatable("event.projecthero.crime.break_in.escaped").withStyle(ChatFormatting.RED), true);
						break;
					}
					runAway(level, m, cfg.fleeSpeed);
				}
			}
		}
		if (chestTarget && !anyLooting && crooks.values().stream().noneMatch(c -> c.step == Step.LOOT)) {
			closeChest(level);
		}
		if (crooks(level).isEmpty()) {
			resolve(level);
		}
	}

	private void flee(ServerLevel level, Mob m, Crook c, long now, boolean withSack) {
		c.step = Step.FLEE;
		c.since = now;
		if (withSack) {
			giveSack(m);
		}
		m.getNavigation().stop();
		level.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.PILLAGER_CELEBRATE, SoundSource.HOSTILE, 1.0f, 1.2f);
	}

	private void closeChest(ServerLevel level) {
		if (target != null && level.isLoaded(target) && level.getBlockEntity(target) instanceof ChestBlockEntity) {
			level.blockEvent(target, level.getBlockState(target).getBlock(), 1, 0);
		}
	}

	/** Every crook is down or gone: stopped if at least as many were put down as got away. */
	private void resolve(ServerLevel level) {
		// anyone still on the books but no longer in the world (his chunk unloaded behind him) got away
		escaped += crooks.size();
		crooks.clear();
		if (stopped > 0 && stopped >= escaped) {
			succeed(level, total <= 0 ? 1.0 : stopped / (double) total);
		} else {
			failCrime(level, "event.projecthero.crime.got_away");
		}
	}

	@Override
	protected void timeUp(ServerLevel level) {
		for (Mob m : crooks(level)) {
			escaped++;
			vanish(level, m);
		}
		crooks.clear();
		resolve(level);
	}

	@Override
	protected void onCrookDown(ServerLevel level, Mob crook, ServerPlayer killer) {
		stopped++;
		crooks.remove(crook.getUUID());
		if (hasSack(crook)) {
			recovered++;
			spillSack(level, crook);
		}
	}

	@Override
	protected void release(ServerLevel level) {
		if (chestTarget) {
			closeChest(level);
		}
	}

	@Override
	protected Vec3 scene(ServerLevel level) {
		Mob best = null;
		double far = -1;
		for (Mob m : crooks(level)) {
			double d = horizontalFromVillage(m.position());
			if (d > far) {
				far = d;
				best = m;
			}
		}
		return best != null ? best.position() : Vec3.atBottomCenterOf(target != null ? target : center());
	}

	@Override
	protected Component status(ServerLevel level) {
		return Component.translatable("event.projecthero.crime.break_in.status", crooks(level).size(), recovered)
				.withStyle(ChatFormatting.WHITE);
	}

	@Override
	protected float progress(ServerLevel level) {
		return total <= 0 ? 0f : crooks(level).size() / (float) total;
	}

	/** Tests: send every crook running with a sack now. */
	public void fleeAllForTest(ServerLevel level) {
		long now = level.getGameTime();
		for (Mob m : crooks(level)) {
			flee(level, m, crooks.computeIfAbsent(m.getUUID(), id -> new Crook(now)), now, true);
		}
	}

	@Override
	protected void saveCrime(CompoundTag tag) {
		if (target != null) {
			tag.put("BreakTarget", NbtUtils.writeBlockPos(target));
		}
		tag.putBoolean("BreakChest", chestTarget);
		tag.putInt("BreakTotal", total);
		tag.putInt("BreakStopped", stopped);
		tag.putInt("BreakEscaped", escaped);
		tag.putInt("BreakRecovered", recovered);
		ListTag list = new ListTag();
		for (Map.Entry<UUID, Crook> e : crooks.entrySet()) {
			CompoundTag t = new CompoundTag();
			t.putUUID("Id", e.getKey());
			t.putString("Step", e.getValue().step.name());
			t.putLong("Since", e.getValue().since);
			list.add(t);
		}
		tag.put("BreakCrooks", list);
	}

	@Override
	protected void loadCrime(CompoundTag tag) {
		target = NbtUtils.readBlockPos(tag, "BreakTarget").orElse(null);
		chestTarget = tag.getBoolean("BreakChest");
		total = tag.getInt("BreakTotal");
		stopped = tag.getInt("BreakStopped");
		escaped = tag.getInt("BreakEscaped");
		recovered = tag.getInt("BreakRecovered");
		crooks.clear();
		ListTag list = tag.getList("BreakCrooks", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = list.getCompound(i);
			Crook c = new Crook(t.getLong("Since"));
			try {
				c.step = Step.valueOf(t.getString("Step"));
			} catch (IllegalArgumentException ignored) {
				c.step = Step.FLEE;
			}
			crooks.put(t.getUUID("Id"), c);
		}
	}
}
