package com.projecthero.mod.crime;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.projecthero.mod.syndicate.SyndicateEntityTypes;

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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.2: arson. A firebug with a flint and steel wanders the streets around the bell and lights a small fire every
 * {@code arson.fireIntervalSeconds}. He gets away -- the crime fails -- once he has lit {@code arson.maxFires}. Put him
 * down, then make sure his fires are out (punch them, or wait {@code arson.fireOutGraceSeconds} for the rest to die);
 * when the last one is out the crime is stopped.
 *
 * <p>He only ever lights a fire on bare, non-flammable ground with nothing that can burn within two blocks (no planks,
 * logs, wool, hay, leaves, crops...), so a fire can't spread to the houses, and every fire he lit is put out when the
 * crime ends, either way. With the {@code mobGriefing} gamerule off he makes no real fire at all (smoke and sparks only).
 */
public class ArsonCrime extends CrimeEvent {
	private UUID arsonistId;
	private final List<BlockPos> fires = new ArrayList<>();
	private int lit;
	private long nextFire;
	private boolean down;
	private long downAt;
	private BlockPos wander;
	private long wanderSince;

	public ArsonCrime(UUID id) {
		super(id);
	}

	@Override
	public CrimeType crimeType() {
		return CrimeType.ARSON;
	}

	public int lit() {
		return lit;
	}

	public List<BlockPos> fires() {
		return fires;
	}

	public boolean arsonistDown() {
		return down;
	}

	@Override
	protected int timeLimitTicks() {
		return CrimeConfig.arson().timeLimitSeconds * 20;
	}

	private Mob arsonist(ServerLevel level) {
		return arsonistId != null && level.getEntity(arsonistId) instanceof Mob m && m.isAlive() ? m : null;
	}

	@Override
	protected void begin(ServerLevel level) {
		BlockPos spot = CrimeVillages.spotNear(level, village, 10, 18, level.random);
		Mob m = spawnCrook(level, SyndicateEntityTypes.THUG, spot == null ? center() : spot, CrimeAI.Role.RUNNER,
				t -> t.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FLINT_AND_STEEL)));
		if (m == null) {
			failCrime(level, "event.projecthero.crime.fizzled");
			return;
		}
		arsonistId = m.getUUID();
		nextFire = runTicks + Math.max(20, CrimeConfig.arson().fireIntervalSeconds * 20L) / 2;
	}

	@Override
	protected void tickCrime(ServerLevel level) {
		CrimeConfig.Arson cfg = CrimeConfig.arson();
		pruneFires(level);
		if (down) {
			if (fires.isEmpty() || level.getGameTime() - downAt >= cfg.fireOutGraceSeconds * 20L) {
				succeed(level, 1.0);
			}
			return;
		}
		Mob m = arsonist(level);
		if (m == null) {
			failCrime(level, "event.projecthero.crime.got_away");
			return;
		}
		long now = level.getGameTime();
		if (wander == null || m.getNavigation().isDone() && now - wanderSince > 40 || now - wanderSince > 300) {
			wander = CrimeVillages.spotNear(level, village, 3, 22, level.random);
			wanderSince = now;
			if (wander != null) {
				m.getNavigation().moveTo(wander.getX() + 0.5, wander.getY(), wander.getZ() + 0.5, 0.9);
			}
		}
		if (runTicks >= nextFire) {
			nextFire = runTicks + Math.max(20, cfg.fireIntervalSeconds * 20L);
			if (lightNear(level, m)) {
				lit++;
				if (lit >= cfg.maxFires) {
					vanish(level, m);
					failCrime(level, "event.projecthero.crime.arson.burned");
				}
			}
		}
	}

	/** Drop fires that have gone out (punched, rained on, burnt out). */
	private void pruneFires(ServerLevel level) {
		fires.removeIf(p -> !level.isLoaded(p) || !level.getBlockState(p).is(Blocks.FIRE));
	}

	/** Lights a fire on a safe spot within two blocks of {@code m}. False if there was nowhere safe. */
	private boolean lightNear(ServerLevel level, Mob m) {
		BlockPos base = m.blockPosition();
		boolean real = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		for (int attempt = 0; attempt < 12; attempt++) {
			BlockPos p = base.offset(level.random.nextInt(5) - 2, level.random.nextInt(3) - 1, level.random.nextInt(5) - 2);
			if (p.equals(base) || !safeFireSpot(level, p)) {
				continue;
			}
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			m.getLookControl().setLookAt(Vec3.atCenterOf(p));
			level.playSound(null, p, SoundEvents.FLINTANDSTEEL_USE, SoundSource.HOSTILE, 1.0f, 0.9f + level.random.nextFloat() * 0.2f);
			if (real) {
				level.setBlock(p, BaseFireBlock.getState(level, p), 11);
				fires.add(p.immutable());
			} else {
				level.sendParticles(ParticleTypes.FLAME, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 12, 0.25, 0.2, 0.25, 0.01);
				level.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 6, 0.2, 0.3, 0.2, 0.01);
			}
			return true;
		}
		return false;
	}

	/** Air with a sturdy, non-flammable floor and nothing that can burn within two blocks. */
	public static boolean safeFireSpot(ServerLevel level, BlockPos p) {
		if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) {
			return false;
		}
		BlockState floor = level.getBlockState(p.below());
		if (!floor.isFaceSturdy(level, p.below(), Direction.UP) || floor.ignitedByLava()) {
			return false;
		}
		for (BlockPos q : BlockPos.betweenClosed(p.offset(-2, -1, -2), p.offset(2, 2, 2))) {
			BlockState s = level.getBlockState(q);
			if (s.ignitedByLava() || s.is(Blocks.FIRE) && !q.equals(p) || s.is(Blocks.HAY_BLOCK)) {
				return false;
			}
		}
		return true;
	}

	private void putOutFires(ServerLevel level) {
		for (BlockPos p : fires) {
			if (level.isLoaded(p) && level.getBlockState(p).is(Blocks.FIRE)) {
				level.removeBlock(p, false);
				level.sendParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
			}
		}
		fires.clear();
	}

	@Override
	protected void onCrookDown(ServerLevel level, Mob crook, ServerPlayer killer) {
		if (crook.getUUID().equals(arsonistId)) {
			down = true;
			downAt = level.getGameTime();
			pruneFires(level);
			tellNearby(level, Component.translatable(fires.isEmpty() ? "event.projecthero.crime.stopped" : "event.projecthero.crime.arson.put_out",
					displayName(), fires.size()).withStyle(ChatFormatting.GOLD), true);
		}
	}

	@Override
	protected void release(ServerLevel level) {
		putOutFires(level);
	}

	@Override
	protected Vec3 scene(ServerLevel level) {
		Mob m = arsonist(level);
		if (m != null) {
			return m.position();
		}
		return fires.isEmpty() ? Vec3.atBottomCenterOf(village) : Vec3.atBottomCenterOf(fires.get(0));
	}

	@Override
	protected Component status(ServerLevel level) {
		return Component.translatable(down ? "event.projecthero.crime.arson.status_down" : "event.projecthero.crime.arson.status",
				lit, CrimeConfig.arson().maxFires, fires.size()).withStyle(ChatFormatting.WHITE);
	}

	@Override
	protected float progress(ServerLevel level) {
		int max = Math.max(1, CrimeConfig.arson().maxFires);
		return down ? 1f - Math.min(1f, fires.size() / 4f) * 0.5f : 1f - lit / (float) max;
	}

	@Override
	protected void saveCrime(CompoundTag tag) {
		if (arsonistId != null) {
			tag.putUUID("ArsonBug", arsonistId);
		}
		tag.putInt("ArsonLit", lit);
		tag.putLong("ArsonNext", nextFire);
		tag.putBoolean("ArsonDown", down);
		tag.putLong("ArsonDownAt", downAt);
		ListTag list = new ListTag();
		for (BlockPos p : fires) {
			list.add(NbtUtils.writeBlockPos(p));
		}
		tag.put("ArsonFires", list);
	}

	@Override
	protected void loadCrime(CompoundTag tag) {
		arsonistId = tag.hasUUID("ArsonBug") ? tag.getUUID("ArsonBug") : null;
		lit = tag.getInt("ArsonLit");
		nextFire = tag.getLong("ArsonNext");
		down = tag.getBoolean("ArsonDown");
		downAt = tag.getLong("ArsonDownAt");
		fires.clear();
		ListTag list = tag.getList("ArsonFires", Tag.TAG_INT_ARRAY);
		for (int i = 0; i < list.size(); i++) {
			fires.add(NbtUtils.readBlockPos(wrap(list.get(i)), "P").orElse(BlockPos.ZERO));
		}
	}

	private static CompoundTag wrap(Tag t) {
		CompoundTag c = new CompoundTag();
		c.put("P", t);
		return c;
	}
}
