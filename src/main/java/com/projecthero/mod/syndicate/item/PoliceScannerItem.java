package com.projecthero.mod.syndicate.item;

import java.util.List;

import com.projecthero.mod.syndicate.SyndicateHideout;
import com.projecthero.mod.syndicate.SyndicateItems;
import com.projecthero.mod.syndicate.SyndicateStashBlock;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * v0.14.25: how a Syndicate Bust begins. Use the scanner and it picks up chatter about a Syndicate hideout 60-110 blocks
 * away -- and the warehouse is there, built on open dry ground with its stash inside. While you hold the scanner its
 * action bar points the way (bearing and distance). Use it again before the bust and it repeats the location; once the
 * stash is busted it can find the next one. It won't work on Peaceful (the Syndicate doesn't do business there).
 */
public class PoliceScannerItem extends Item {
	private static final String KEY = "SyndicateHideout";
	private static final String[] ARROWS = { "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };

	public PoliceScannerItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.success(stack);
		}
		player.getCooldowns().addCooldown(this, 60);
		if (server.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL) {
			sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.peaceful").withStyle(ChatFormatting.GRAY), false);
			return InteractionResultHolder.fail(stack);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.6f, 1.6f);
		BlockPos known = hideout(stack, server);
		if (known != null && stillThere(server, known)) {
			sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.repeat", known.getX(), known.getY(), known.getZ(),
					(int) Math.sqrt(player.blockPosition().distSqr(known))).withStyle(ChatFormatting.GOLD), false);
			return InteractionResultHolder.success(stack);
		}
		SyndicateHideout.Site site = SyndicateHideout.findSite(server, player.blockPosition(), 60, 110, server.random);
		if (site == null) {
			sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.static").withStyle(ChatFormatting.GRAY), false);
			return InteractionResultHolder.fail(stack);
		}
		BlockPos stash = SyndicateHideout.build(server, site, server.random);
		server.setBlock(stash, SyndicateItems.STASH.defaultBlockState().setValue(SyndicateStashBlock.FACING, site.forward()), 3);
		remember(stack, server, stash);
		sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.found").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);
		sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.where", stash.getX(), stash.getY(), stash.getZ(),
				(int) Math.sqrt(player.blockPosition().distSqr(stash))).withStyle(ChatFormatting.YELLOW), false);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 0.8f, 0.7f);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (!(level instanceof ServerLevel server) || !(entity instanceof ServerPlayer sp) || server.getGameTime() % 10 != 0) {
			return;
		}
		boolean held = selected || sp.getOffhandItem() == stack;
		if (!held) {
			return;
		}
		BlockPos h = hideout(stack, server);
		if (h == null) {
			return;
		}
		double dx = h.getX() + 0.5 - sp.getX(), dz = h.getZ() + 0.5 - sp.getZ();
		int dist = (int) Math.sqrt(dx * dx + dz * dz);
		float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float rel = Mth.wrapDegrees(bearing - sp.getYRot());
		String arrow = ARROWS[Math.floorMod(Math.round(rel / 45f), 8)];
		sp.displayClientMessage(Component.translatable("item.projecthero.police_scanner.bar", arrow, dist).withStyle(ChatFormatting.GOLD), true);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.police_scanner.tooltip").withStyle(ChatFormatting.GRAY));
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.contains(KEY)) {
			CompoundTag t = data.copyTag().getCompound(KEY);
			tooltip.add(Component.translatable("item.projecthero.police_scanner.tooltip.tracking", t.getInt("X"), t.getInt("Y"), t.getInt("Z"))
					.withStyle(ChatFormatting.GOLD));
		}
	}

	/** The hideout this scanner is tracking, if it's in {@code level}'s dimension. */
	static BlockPos hideout(ItemStack stack, ServerLevel level) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !data.contains(KEY)) {
			return null;
		}
		CompoundTag t = data.copyTag().getCompound(KEY);
		if (!level.dimension().location().toString().equals(t.getString("Dim"))) {
			return null;
		}
		return new BlockPos(t.getInt("X"), t.getInt("Y"), t.getInt("Z"));
	}

	/** True while the stash at {@code pos} hasn't been busted (an unloaded chunk counts as still there). */
	private static boolean stillThere(ServerLevel level, BlockPos pos) {
		return !level.isLoaded(pos) || level.getBlockState(pos).getBlock() instanceof SyndicateStashBlock;
	}

	private static void remember(ItemStack stack, ServerLevel level, BlockPos pos) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			CompoundTag t = new CompoundTag();
			t.putInt("X", pos.getX());
			t.putInt("Y", pos.getY());
			t.putInt("Z", pos.getZ());
			t.putString("Dim", level.dimension().location().toString());
			tag.put(KEY, t);
		});
	}
}
