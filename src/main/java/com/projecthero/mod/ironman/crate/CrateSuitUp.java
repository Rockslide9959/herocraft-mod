package com.projecthero.mod.ironman.crate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManManualSuitUp;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * v0.16.7 (user) ERA I: <b>suiting up from a Suit Crate</b> -- Shift+right-click a crate and the suit goes on, anywhere,
 * over 30 s: the Mark 1's cave hand build ({@link IronManManualSuitUp#KIND_MK1}: sit, fit, hammer, strap, stand, the
 * faceplate pulled down last) for every crated suit, each piece coming out of the opened crate in the tick it is fitted.
 * This is the one way an Era I suit goes on outside a Stark Gantry ({@code EraOneGantry}).
 *
 * <p>The crate is the pieces' only home until each is fitted ({@link #takePiece}), so nothing is ever in two places:
 * death stops the build with the rest still in the crate (it closes again); logout / a dimension change finish it at once
 * from the crate; breaking the crate mid-build drops the rest in the item. When the build ends with the crate empty the
 * crate block is gone. The player -> crate map is static per-world state, cleared in {@code ServerStateReset}.
 */
public final class CrateSuitUp {
	private record Source(ResourceKey<Level> dimension, BlockPos pos) {
	}

	private static final Map<UUID, Source> SOURCES = new ConcurrentHashMap<>();

	private CrateSuitUp() {
	}

	public static void clearSessionState() {
		SOURCES.clear();
	}

	/** Is somebody suiting up from this crate right now? */
	public static boolean busy(SuitCrateBlockEntity be) {
		if (be.getLevel() == null) {
			return false;
		}
		Source s = new Source(be.getLevel().dimension(), be.getBlockPos());
		return SOURCES.containsValue(s);
	}

	/** Is this player's hand build fed from a crate? */
	public static boolean fromCrate(ServerPlayer player) {
		return SOURCES.containsKey(player.getUUID());
	}

	/** Shift+right-click: start the 30 s suit-up from {@code be}. Returns whether it started (else a message says why). */
	public static boolean begin(ServerPlayer player, SuitCrateBlockEntity be) {
		String suitId = be.suitId();
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		if (suit == null) {
			SuitCrateBlock.tell(player, "message.projecthero.ironman.era1.crate_empty", ChatFormatting.GOLD);
			return false;
		}
		if (!TonyStark.hasPower(player)) {
			SuitCrateBlock.tell(player, "message.projecthero.ironman.armor_rejects", ChatFormatting.RED);
			return false;
		}
		if (busy(be)) {
			SuitCrateBlock.tell(player, "message.projecthero.ironman.era1.crate_busy", ChatFormatting.GOLD);
			return false;
		}
		if (IronManSuitUpManager.inTransition(player) || IronManManualSuitUp.running(player)) {
			return false;
		}
		if (IronManArmor.wearingAnyIronMan(player)) {
			SuitCrateBlock.tell(player, "message.projecthero.gantry.already_suited", ChatFormatting.GOLD);
			return false;
		}
		// the suit's charge travels with the armour: adopt the crate's integrity and energy before the first piece goes on
		IronManEnergy.loadFromStack(player, suitId, be.reference());
		TonyStark.setActiveSuit(player, suitId);
		SOURCES.put(player.getUUID(), new Source(be.getLevel().dimension(), be.getBlockPos()));
		setOpen(be, true);
		if (be.getLevel() instanceof ServerLevel sl) {
			BlockPos p = be.getBlockPos();
			sl.playSound(null, p, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.9f, 0.7f);
			sl.playSound(null, p, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 1.3f);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 8, 0.35, 0.1, 0.35, 0.1);
		}
		IronManManualSuitUp.startFromCrate(player, suit, be.mask());
		return true;
	}

	/**
	 * The hand build wants piece {@code bit} (0 HEAD .. 3 FEET): out of the player's crate. Null when this build is not
	 * fed from a crate (the caller uses the pack); EMPTY when the crate is gone or the piece is not in it (it is skipped).
	 */
	public static ItemStack takePiece(ServerPlayer player, int bit) {
		Source s = SOURCES.get(player.getUUID());
		if (s == null) {
			return null;
		}
		SuitCrateBlockEntity be = crate(player, s);
		if (be == null) {
			return ItemStack.EMPTY;
		}
		ItemStack piece = be.take(bit);
		if (!piece.isEmpty() && be.getLevel() instanceof ServerLevel sl) {
			BlockPos p = be.getBlockPos();
			// the piece comes up out of the crate: a clank and a few sparks at the lid
			sl.playSound(null, p, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 0.6f, 0.9f + bit * 0.05f);
			sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5, 4, 0.25, 0.1, 0.25, 0.08);
		}
		return piece;
	}

	/**
	 * The hand build ended (finished, aborted or completed at once): forget the crate; an emptied crate is gone (it was
	 * the suit's box), one with pieces left in it closes again.
	 */
	public static void ended(ServerPlayer player) {
		Source s = SOURCES.remove(player.getUUID());
		if (s == null) {
			return;
		}
		SuitCrateBlockEntity be = crate(player, s);
		if (be == null) {
			return;
		}
		Level level = be.getLevel();
		BlockPos p = be.getBlockPos();
		if (be.isEmptyCrate()) {
			level.removeBlock(p, false);
			if (level instanceof ServerLevel sl) {
				sl.playSound(null, p, SoundEvents.NETHERITE_BLOCK_BREAK, SoundSource.BLOCKS, 0.7f, 1.2f);
				sl.sendParticles(ParticleTypes.CLOUD, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.02);
			}
		} else {
			setOpen(be, false);
		}
	}

	private static SuitCrateBlockEntity crate(ServerPlayer player, Source s) {
		ServerLevel level = player.server.getLevel(s.dimension());
		return level != null && level.isLoaded(s.pos()) && level.getBlockEntity(s.pos()) instanceof SuitCrateBlockEntity be ? be : null;
	}

	private static void setOpen(SuitCrateBlockEntity be, boolean open) {
		BlockState st = be.getBlockState();
		if (be.getLevel() != null && st.hasProperty(SuitCrateBlock.OPEN) && st.getValue(SuitCrateBlock.OPEN) != open) {
			be.getLevel().setBlock(be.getBlockPos(), st.setValue(SuitCrateBlock.OPEN, open), Block.UPDATE_ALL);
		}
	}
}
