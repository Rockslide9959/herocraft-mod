package com.projecthero.mod.cyborg;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.cyborg.block.CyborgBlocks;
import com.projecthero.mod.cyborg.data.CyborgState;
import com.projecthero.mod.hero.power.AbilityHelpers;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.16.5 (user: "make V another ability that works with redstone somehow, make it work well"): <b>Circuit Link</b>.
 * Cyborg reaches into the redstone of whatever he aims at, up to {@link CyborgConfig#LINK_RANGE} blocks away, through a
 * red data line from his hand:
 * <ul>
 *   <li><b>a switch</b> -- a lever flips, a button presses, a door / trapdoor / fence gate (iron ones too) opens or shuts,
 *       a bell rings: exactly as if he had used it by hand;</li>
 *   <li><b>anything else</b> -- he <b>powers it</b>: an invisible signal ({@link com.projecthero.mod.cyborg.block.CyborgSignalBlock})
 *       appears in the air on the side he aims at, so the block gets full redstone power (a solid block passes it on like a
 *       block of redstone): pistons push, dispensers and droppers fire, lamps light, TNT primes, rails power, note blocks
 *       play, dust carries it on. A tap is a {@link CyborgConfig#LINK_PULSE_TICKS 1.5 s} pulse; <b>holding V</b> keeps the
 *       power on (1 Power Core a second, up to a minute) until he lets go.</li>
 * </ul>
 * 2 Power Core a use. Nothing to work (open air, or no room for the signal) costs nothing.
 */
public final class CyborgCircuit {
	/** Signal blocks alive: "dimension|pos" -> the game time they go out. */
	private static final Map<String, Long> POWERED = new ConcurrentHashMap<>();

	/** The signal a player is holding on with V: where, and since when. */
	private record Hold(ServerLevel level, BlockPos pos, long start) {
	}

	private static final Map<UUID, Hold> HOLDS = new ConcurrentHashMap<>();

	private static final DustParticleOptions REDSTONE = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.1f, 0.05f), 1.0f);

	private CyborgCircuit() {
	}

	static void clearSessionState() {
		POWERED.clear();
		HOLDS.clear();
	}

	static void forget(UUID id) {
		HOLDS.remove(id);
	}

	private static String key(Level level, BlockPos pos) {
		return level.dimension().location() + "|" + pos.asLong();
	}

	/** Whether the signal block at {@code pos} should still be there. */
	public static boolean powered(Level level, BlockPos pos) {
		Long until = POWERED.get(key(level, pos));
		if (until == null) {
			return false;
		}
		if (until <= level.getGameTime()) {
			POWERED.remove(key(level, pos));
			return false;
		}
		return true;
	}

	// ---------------------------------------------------------------- V

	/** V pressed: work or power the block under the crosshair. */
	public static void press(ServerPlayer p) {
		if (HOLDS.containsKey(p.getUUID())) {
			return;
		}
		ServerLevel level = (ServerLevel) p.level();
		Vec3 eye = p.getEyePosition();
		BlockHitResult bhr = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(CyborgConfig.LINK_RANGE)), ClipContext.Block.OUTLINE,
				ClipContext.Fluid.NONE, p));
		if (bhr.getType() != HitResult.Type.BLOCK) {
			if (Cyborg.canAct(p)) {
				Cyborg.say(p, "message.projecthero.cyborg.link_nothing", ChatFormatting.GRAY);
			}
			return;
		}
		BlockPos pos = bhr.getBlockPos();
		BlockState st = level.getBlockState(pos);
		if (!p.mayBuild() || !level.mayInteract(p, pos)) {
			Cyborg.say(p, "message.projecthero.cyborg.link_protected", ChatFormatting.GRAY);
			return;
		}
		boolean isSwitch = st.getBlock() instanceof LeverBlock || st.getBlock() instanceof ButtonBlock || st.getBlock() instanceof DoorBlock
				|| st.getBlock() instanceof TrapDoorBlock || st.getBlock() instanceof FenceGateBlock || st.getBlock() instanceof BellBlock;
		BlockPos spot = isSwitch ? null : signalSpot(level, pos, bhr.getDirection());
		if (!isSwitch && spot == null) {
			Cyborg.say(p, "message.projecthero.cyborg.link_no_room", ChatFormatting.GRAY);
			return;
		}
		if (!CyborgAbilities.begin(p, CyborgAbilities.LINK, CyborgConfig.LINK_COST)) {
			return;
		}
		CyborgAbilities.cooldown(p, CyborgAbilities.LINK, CyborgConfig.LINK_COOLDOWN);
		Cyborg.setAnim(p, CyborgState.ANIM_LINK);
		Vec3 hit = bhr.getLocation();
		AbilityHelpers.line(level, CyborgAbilities.hand(p), hit, REDSTONE, 4.0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.x, hit.y, hit.z, 10, 0.15, 0.15, 0.15, 0.1);
		level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.5f, 2.0f);
		if (isSwitch) {
			work(p, level, pos, st, bhr.getDirection());
			return;
		}
		long now = level.getGameTime();
		POWERED.put(key(level, spot), now + CyborgConfig.LINK_PULSE_TICKS);
		if (!level.getBlockState(spot).is(CyborgBlocks.CYBORG_SIGNAL)) {
			level.setBlock(spot, CyborgBlocks.CYBORG_SIGNAL.defaultBlockState(), 3);
		}
		HOLDS.put(p.getUUID(), new Hold(level, spot, now));
		level.playSound(null, pos, SoundEvents.REDSTONE_TORCH_BURNOUT, SoundSource.BLOCKS, 0.4f, 2.0f);
	}

	/** V released: a held signal goes out (a tap's pulse runs its course). */
	public static void release(ServerPlayer p) {
		HOLDS.remove(p.getUUID());
	}

	/** The air beside {@code pos} the signal can sit in: the face he aims at first, then any other side. */
	static BlockPos signalSpot(Level level, BlockPos pos, Direction face) {
		BlockPos first = pos.relative(face);
		if (free(level, first)) {
			return first;
		}
		for (Direction d : Direction.values()) {
			BlockPos q = pos.relative(d);
			if (free(level, q)) {
				return q;
			}
		}
		return null;
	}

	private static boolean free(Level level, BlockPos q) {
		BlockState s = level.getBlockState(q);
		return (s.isAir() || s.is(CyborgBlocks.CYBORG_SIGNAL)) && level.isInWorldBounds(q);
	}

	/** A switch, worked as by hand. */
	private static void work(ServerPlayer p, ServerLevel level, BlockPos pos, BlockState st, Direction face) {
		if (st.getBlock() instanceof LeverBlock lever) {
			lever.pull(st, level, pos, p);
		} else if (st.getBlock() instanceof ButtonBlock button) {
			if (!st.getValue(BlockStateProperties.POWERED)) {
				button.press(st, level, pos, p);
				level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, 1.0f);
			}
		} else if (st.getBlock() instanceof DoorBlock door) {
			door.setOpen(p, level, st, pos, !st.getValue(BlockStateProperties.OPEN));
		} else if (st.getBlock() instanceof BellBlock bell) {
			bell.attemptToRing(p, level, pos, face.getAxis().isHorizontal() ? face : p.getDirection().getOpposite());
		} else if (st.hasProperty(BlockStateProperties.OPEN)) {
			boolean open = !st.getValue(BlockStateProperties.OPEN);
			level.setBlock(pos, st.setValue(BlockStateProperties.OPEN, open), 10);
			level.playSound(null, pos, open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
			level.gameEvent(p, open ? net.minecraft.world.level.gameevent.GameEvent.BLOCK_OPEN : net.minecraft.world.level.gameevent.GameEvent.BLOCK_CLOSE,
					pos);
		}
	}

	/** Each tick for a Cyborg: a held signal stays on (paid for), and every live signal of his sparks red. */
	static void tick(ServerPlayer p, long now) {
		Hold h = HOLDS.get(p.getUUID());
		if (h == null) {
			return;
		}
		if (h.level() != p.level() || !p.isAlive() || !h.level().getBlockState(h.pos()).is(CyborgBlocks.CYBORG_SIGNAL)) {
			HOLDS.remove(p.getUUID());
			return;
		}
		long held = now - h.start();
		if (held >= CyborgConfig.LINK_PULSE_TICKS - 2) {
			// still holding past the tap's pulse: keep it on, a Power Core a second
			if (held > CyborgConfig.LINK_MAX_HOLD_TICKS
					|| (now % 5 == 0 && !Cyborg.spendCore(p, CyborgConfig.LINK_HOLD_COST_PER_SECOND / 4f))) {
				HOLDS.remove(p.getUUID());
				return;
			}
			POWERED.put(key(h.level(), h.pos()), now + 4);
		}
		if (now % 4 == 0) {
			Vec3 c = Vec3.atCenterOf(h.pos());
			h.level().sendParticles(REDSTONE, c.x, c.y, c.z, 3, 0.25, 0.25, 0.25, 0.0);
			if (now % 8 == 0) {
				AbilityHelpers.line(h.level(), CyborgAbilities.hand(p), c, REDSTONE, 2.0);
			}
		}
	}

	/** Gametests: whether {@code p} is holding a signal on. */
	public static boolean holding(ServerPlayer p) {
		return HOLDS.containsKey(p.getUUID());
	}
}
