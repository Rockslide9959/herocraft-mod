package com.projecthero.mod.thoracquire.forge;

import java.util.ArrayList;
import java.util.List;

import org.joml.Vector3f;

import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.thoracquire.ThorAcquire;
import com.projecthero.mod.worthiness.Worthiness;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 (replaces the Nether-lava forging): <b>Stormbreaker, forged in a dying star</b>.
 * <ol>
 * <li>Build the Star Forge inside a ring of eight blackstone blocks (any blackstone: plain, polished, bricks, cracked,
 * chiseled or gilded) on its own level. An incomplete ring answers any use with how many are missing and marks the gaps.</li>
 * <li>Right-click it with the Stormbreaker Head (the old "Unforged Stormbreaker" item, same id -- old ones in inventories
 * simply go in) to set it in the forge. Empty hand takes it back out.</li>
 * <li>A worthy Thor <b>holds right-click on the forge with Mjolnir</b> for {@link #CHANNEL_TICKS} (10 s), standing within
 * {@link #REACH} blocks. A forge-specific hold-to-channel: the client repeats the use every 4 ticks while the button is held,
 * each one a "ping"; a gap of more than {@link #PING_GRACE} ticks breaks the channel and the progress resets. The forge burns
 * him while he channels ({@link #BURN} every {@link #BURN_EVERY} ticks, survivable at full Thor health) with sparks, a white-hot
 * core, a roar that builds and a slight screen shake. Done: the head is marked forged ({@link #isForged}).</li>
 * <li>Right-click with a {@link com.projecthero.mod.thoracquire.LivingBranch Living Branch}: the branch grows into a haft,
 * lightning strikes the forge, Stormbreaker rises out of it in a burst of light ({@link #FINISH_TICKS}) and drops as the
 * normal Stormbreaker item (which becomes the usual resting axe).</li>
 * </ol>
 */
public final class StarForge {
	public static final int CHANNEL_TICKS = 200;
	public static final int PING_GRACE = 8;
	public static final double REACH = 4.5;
	public static final float BURN = 3.0f;
	public static final int BURN_EVERY = 10;
	public static final int FINISH_TICKS = 70;
	public static final int FINISH_STRIKE = 22;
	public static final String FORGED_TAG = "StormbreakerForged";

	private static final DustParticleOptions MISSING = new DustParticleOptions(new Vector3f(1f, 0.25f, 0.2f), 1.6f);
	private static final DustParticleOptions LEAF = new DustParticleOptions(new Vector3f(0.35f, 1f, 0.45f), 1.2f);

	private StarForge() {
	}

	// ---------------------------------------------------------------- the multiblock

	/** Any blackstone counts for the ring. */
	public static boolean isRingBlock(BlockState s) {
		return s.is(Blocks.BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE) || s.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
				|| s.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) || s.is(Blocks.CHISELED_POLISHED_BLACKSTONE) || s.is(Blocks.GILDED_BLACKSTONE);
	}

	/** The eight ring spots that are not blackstone yet (empty = complete). */
	public static List<BlockPos> missingRing(ServerLevel level, BlockPos forge) {
		List<BlockPos> out = new ArrayList<>();
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) {
					continue;
				}
				BlockPos p = forge.offset(dx, 0, dz);
				if (!isRingBlock(level.getBlockState(p))) {
					out.add(p);
				}
			}
		}
		return out;
	}

	public static boolean ringComplete(ServerLevel level, BlockPos forge) {
		return missingRing(level, forge).isEmpty();
	}

	private static boolean hintIfIncomplete(ServerLevel level, BlockPos pos, ServerPlayer player) {
		List<BlockPos> missing = missingRing(level, pos);
		if (missing.isEmpty()) {
			return false;
		}
		player.displayClientMessage(Component.translatable("message.projecthero.star_forge.ring", 8 - missing.size(), missing.size())
				.withStyle(ChatFormatting.GOLD), true);
		for (BlockPos m : missing) {
			level.sendParticles(MISSING, m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0);
		}
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 0.8f);
		return true;
	}

	// ---------------------------------------------------------------- the head

	public static boolean isForged(ItemStack head) {
		CustomData d = head.get(DataComponents.CUSTOM_DATA);
		return d != null && d.copyTag().getBoolean(FORGED_TAG);
	}

	public static ItemStack forgedHead() {
		ItemStack s = new ItemStack(ModItems.UNFORGED_STORMBREAKER);
		CompoundTag t = new CompoundTag();
		t.putBoolean(FORGED_TAG, true);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		return s;
	}

	// ---------------------------------------------------------------- use

	public static void use(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer player, ItemStack stack) {
		if (hintIfIncomplete(level, pos, player)) {
			return;
		}
		if (be.finish > 0) {
			return;
		}
		if (stack.is(ModItems.UNFORGED_STORMBREAKER)) {
			if (!be.head.isEmpty()) {
				player.displayClientMessage(Component.translatable("message.projecthero.star_forge.full").withStyle(ChatFormatting.GOLD), true);
				return;
			}
			be.head = stack.copyWithCount(1);
			stack.consume(1, player);
			setHeat(level, pos, isForged(be.head) ? 2 : 1);
			be.sync();
			level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, 0.7f, 0.6f);
			level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8f, 0.7f);
			player.displayClientMessage(Component.translatable(isForged(be.head) ? "message.projecthero.star_forge.forged_in"
					: "message.projecthero.star_forge.head_in").withStyle(ChatFormatting.AQUA), true);
			return;
		}
		if (stack.is(ThorAcquire.LIVING_BRANCH)) {
			if (be.head.isEmpty() || !isForged(be.head)) {
				player.displayClientMessage(Component.translatable("message.projecthero.star_forge.branch_early").withStyle(ChatFormatting.GOLD), true);
				return;
			}
			stack.consume(1, player);
			startFinish(level, pos, be);
			return;
		}
		if (stack.is(ModItems.MJOLNIR)) {
			ping(level, pos, be, player);
		}
	}

	public static void useEmptyHand(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer player) {
		if (hintIfIncomplete(level, pos, player)) {
			return;
		}
		if (be.head.isEmpty() || be.channeler != null || be.finish > 0) {
			player.displayClientMessage(Component.translatable(be.head.isEmpty() ? "message.projecthero.star_forge.empty"
					: "message.projecthero.star_forge.busy").withStyle(ChatFormatting.GOLD), true);
			return;
		}
		ItemStack out = be.head;
		be.head = ItemStack.EMPTY;
		be.channel = 0;
		setHeat(level, pos, 0);
		be.sync();
		if (!player.getInventory().add(out)) {
			player.drop(out, false);
		}
	}

	/** One "still holding right-click" from the channeller (also the test hook). */
	public static boolean ping(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer player) {
		if (!ringComplete(level, pos)) {
			return false;
		}
		if (be.head.isEmpty()) {
			player.displayClientMessage(Component.translatable("message.projecthero.star_forge.empty").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (isForged(be.head)) {
			player.displayClientMessage(Component.translatable("message.projecthero.star_forge.need_branch").withStyle(ChatFormatting.AQUA), true);
			return false;
		}
		if (!Worthiness.isWorthy(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.star_forge.unworthy").withStyle(ChatFormatting.GOLD), true);
			return false;
		}
		if (be.channeler != null && !be.channeler.equals(player.getUUID())) {
			return false;
		}
		if (be.channeler == null) {
			be.channeler = player.getUUID();
			be.channel = 0;
			level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
			player.displayClientMessage(Component.translatable("message.projecthero.star_forge.channel_start").withStyle(ChatFormatting.AQUA), true);
			setHeat(level, pos, 2);
			be.sync();
		}
		be.lastPing = level.getGameTime();
		return true;
	}

	// ---------------------------------------------------------------- tick

	static void tick(ServerLevel level, BlockPos pos, BlockState state, StarForgeBlockEntity be) {
		if (be.finish > 0) {
			tickFinish(level, pos, be);
			return;
		}
		if (be.channeler == null) {
			if (!be.head.isEmpty() && level.getGameTime() % 10 == 0) {
				level.sendParticles(isForged(be.head) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SMALL_FLAME, pos.getX() + 0.5,
						pos.getY() + 1.1, pos.getZ() + 0.5, 1, 0.15, 0.05, 0.15, 0.01);
			}
			return;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(be.channeler);
		String broke = null;
		if (p == null || !p.isAlive() || p.level() != level) {
			broke = "gone";
		} else if (p.distanceToSqr(Vec3.atCenterOf(pos)) > REACH * REACH) {
			broke = "far";
		} else if (!p.getMainHandItem().is(ModItems.MJOLNIR)) {
			broke = "dropped";
		} else if (level.getGameTime() - be.lastPing > PING_GRACE) {
			broke = "released";
		} else if (!ringComplete(level, pos)) {
			broke = "ring";
		}
		if (broke != null) {
			breakChannel(level, pos, be, p);
			return;
		}
		be.channel++;
		channelEffects(level, pos, be, p);
		if (be.channel % BURN_EVERY == 0) {
			p.hurt(level.damageSources().inFire(), BURN);
		}
		if (be.channel % 5 == 0) {
			be.sync();
		}
		if (be.channel >= CHANNEL_TICKS) {
			forged(level, pos, be, p);
		}
	}

	private static void channelEffects(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer p) {
		float heat = be.channel / (float) CHANNEL_TICKS;
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.2, z, 2 + (int) (heat * 8), 0.25, 0.25, 0.25, 0.08 + heat * 0.2);
		if (be.channel % 2 == 0) {
			level.sendParticles(ParticleTypes.FLAME, x, y, z, 1 + (int) (heat * 4), 0.3, 0.1, 0.3, 0.02 + heat * 0.04);
			level.sendParticles(ParticleTypes.LAVA, x, y, z, heat > 0.5f ? 1 : 0, 0.2, 0.05, 0.2, 0);
		}
		if (be.channel % 4 == 0) {
			level.sendParticles(ParticleTypes.SMOKE, x, y + 0.5, z, 2, 0.3, 0.3, 0.3, 0.02);
			// sparks flying off the anvil-hot head
			level.sendParticles(ParticleTypes.FIREWORK, x, y + 0.2, z, (int) (heat * 4), 0.1, 0.1, 0.1, 0.25);
		}
		if (be.channel % 20 == 0) {
			level.playSound(null, pos, SoundEvents.FIRE_AMBIENT, SoundSource.BLOCKS, 1.0f + heat, 0.5f + heat * 0.5f);
			level.playSound(null, pos, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.4f + heat * 0.6f, 0.4f + heat * 0.4f);
			level.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.3f + heat * 0.4f, 0.5f + heat * 0.6f);
			if (ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
				ServerPlayNetworking.send(p, new TitanShakePayload(0.12f + heat * 0.25f, 12));
			}
		}
		if (be.channel % 10 == 5) {
			level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 1.4f, 0.6f + heat * 1.2f);
		}
		if (be.channel == CHANNEL_TICKS / 2) {
			level.playSound(null, pos, SoundEvents.WITHER_SHOOT, SoundSource.BLOCKS, 0.8f, 0.5f);
		}
	}

	private static void breakChannel(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer p) {
		be.channeler = null;
		be.channel = 0;
		setHeat(level, pos, be.head.isEmpty() ? 0 : 1);
		be.sync();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.0f, 0.6f);
		if (p != null) {
			p.displayClientMessage(Component.translatable("message.projecthero.star_forge.broken").withStyle(ChatFormatting.RED), true);
		}
	}

	private static void forged(ServerLevel level, BlockPos pos, StarForgeBlockEntity be, ServerPlayer p) {
		be.channeler = null;
		be.channel = 0;
		be.head = forgedHead();
		setHeat(level, pos, 2);
		be.sync();
		double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
		bolt(level, new Vec3(x, pos.getY() + 1, z));
		level.sendParticles(ParticleTypes.FLASH, x, y, z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 80, 0.4, 0.4, 0.4, 0.4);
		level.sendParticles(ParticleTypes.FLAME, x, y, z, 60, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 1.0f, 0.5f);
		level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.6f, 0.8f);
		level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.2f, 0.5f);
		p.displayClientMessage(Component.translatable("message.projecthero.star_forge.forged").withStyle(ChatFormatting.GOLD), false);
	}

	private static void startFinish(ServerLevel level, BlockPos pos, StarForgeBlockEntity be) {
		be.finish = 1;
		be.sync();
		level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 1.2f, 0.6f);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 1.2f, 0.6f);
	}

	/** Test hook: runs the finishing sequence to its end at once. */
	public static void finishNowForTest(ServerLevel level, BlockPos pos, StarForgeBlockEntity be) {
		if (be.finish == 0) {
			be.finish = 1;
		}
		while (be.finish > 0) {
			tickFinish(level, pos, be);
		}
	}

	/** Test hook: puts a head in without a player. */
	public static void insertForTest(StarForgeBlockEntity be, ItemStack head) {
		be.head = head.copyWithCount(1);
		be.sync();
	}

	private static void tickFinish(ServerLevel level, BlockPos pos, StarForgeBlockEntity be) {
		int t = be.finish++;
		double x = pos.getX() + 0.5, y = pos.getY() + 1.0, z = pos.getZ() + 0.5;
		if (t < FINISH_STRIKE) {
			// the branch grows into a haft: green motes spiralling up
			double a = t * 0.6;
			level.sendParticles(LEAF, x + Math.cos(a) * 0.35, y + t * 0.04, z + Math.sin(a) * 0.35, 2, 0.03, 0.03, 0.03, 0);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.3, z, 1, 0.25, 0.25, 0.25, 0);
			if (t % 6 == 0) {
				level.playSound(null, pos, SoundEvents.CAVE_VINES_PLACE, SoundSource.BLOCKS, 1.0f, 0.6f + t * 0.03f);
			}
		}
		if (t == FINISH_STRIKE) {
			bolt(level, new Vec3(x, pos.getY() + 1, z));
			level.sendParticles(ParticleTypes.FLASH, x, y + 0.5, z, 1, 0, 0, 0, 0);
			level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 2.0f, 0.7f);
			level.playSound(null, pos, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.BLOCKS, 1.6f, 0.8f);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.5, z, 70, 0.4, 0.6, 0.4, 0.4);
		}
		if (t > FINISH_STRIKE && t < FINISH_TICKS - 8) {
			// the axe rises out of the forge (the renderer draws it); light pours up around it
			double h = (t - FINISH_STRIKE) / (double) (FINISH_TICKS - 8 - FINISH_STRIKE);
			level.sendParticles(ParticleTypes.END_ROD, x, y + h * 1.4, z, 2, 0.25, 0.2, 0.25, 0.03);
			if (t % 3 == 0) {
				level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + h * 1.4 + 0.6, z, 4, 0.3, 0.5, 0.3, 0.15);
			}
			if (t % 10 == 0) {
				level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 1.0f, 0.5f + (float) h);
			}
		}
		if (t == FINISH_TICKS - 8) {
			level.sendParticles(ParticleTypes.FLASH, x, y + 1.6, z, 2, 0.1, 0.1, 0.1, 0);
			level.sendParticles(ParticleTypes.END_ROD, x, y + 1.6, z, 160, 0.1, 0.1, 0.1, 0.5);
			level.sendParticles(ParticleTypes.FIREWORK, x, y + 1.6, z, 60, 0.2, 0.2, 0.2, 0.3);
			level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.BLOCKS, 1.0f, 0.8f);
			level.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.0f, 1.0f);
			for (ServerPlayer q : level.getPlayers(q -> q.distanceToSqr(x, y, z) < 24 * 24)) {
				if (ServerPlayNetworking.canSend(q, TitanShakePayload.TYPE)) {
					ServerPlayNetworking.send(q, new TitanShakePayload(0.5f, 12));
				}
				q.displayClientMessage(Component.translatable("message.projecthero.star_forge.done").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
			}
			ItemEntity axe = new ItemEntity(level, x, y + 1.4, z, new ItemStack(ModItems.STORMBREAKER));
			axe.setDeltaMovement(0, 0.25, 0);
			axe.setPickUpDelay(10);
			level.addFreshEntity(axe);
			be.head = ItemStack.EMPTY;
		}
		if (t >= FINISH_TICKS) {
			be.finish = 0;
			setHeat(level, pos, 0);
		}
		if (t % 5 == 0 || be.finish == 0) {
			be.sync();
		}
	}

	private static void bolt(ServerLevel level, Vec3 at) {
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
		if (bolt != null) {
			bolt.moveTo(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
	}

	private static void setHeat(ServerLevel level, BlockPos pos, int heat) {
		BlockState s = level.getBlockState(pos);
		if (s.getBlock() instanceof StarForgeBlock && s.getValue(StarForgeBlock.HEAT) != heat) {
			level.setBlock(pos, s.setValue(StarForgeBlock.HEAT, heat), Block.UPDATE_ALL);
		}
	}
}
