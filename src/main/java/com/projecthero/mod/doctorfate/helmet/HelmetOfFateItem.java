package com.projecthero.mod.doctorfate.helmet;

import java.util.List;

import com.projecthero.mod.doctorfate.DoctorFateHelmet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.4 the Helmet of Fate as an item (only ever while it's off its owner's head).
 * <ul>
 *   <li><b>Shift+right-click</b> (in the air or on a block): its owner puts it on ({@link DoctorFateHelmet#wearFromItem}, the put-on
 *   sequence). Anyone else: Nabu doesn't answer.</li>
 *   <li><b>Right-click a block</b>: set it down there as a hovering {@link FateHelmetEntity}.</li>
 *   <li>Q drops it like any item; it's fireproof and the dropped item never despawns ({@link FateHelmetGuard}).</li>
 *   <li>Every tick in a player's inventory it checks it isn't a ghost of a summoned-away helmet (and goes if it is), and tells the
 *   registry who carries it.</li>
 * </ul>
 * The slot is emptied outright (never {@code shrink}), so creative mode's count restore can't leave a second helmet behind.
 */
public class HelmetOfFateItem extends Item {
	public HelmetOfFateItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!player.isShiftKeyDown()) {
			return InteractionResultHolder.pass(stack);
		}
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		return DoctorFateHelmet.wearFromItem((ServerPlayer) player, stack, hand) ? InteractionResultHolder.success(ItemStack.EMPTY)
				: InteractionResultHolder.fail(stack);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		Player player = ctx.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown()) {
			if (ctx.getLevel().isClientSide()) {
				return InteractionResult.SUCCESS;
			}
			if (DoctorFateHelmet.wearFromItem((ServerPlayer) player, ctx.getItemInHand(), ctx.getHand())) {
				player.setItemInHand(ctx.getHand(), ItemStack.EMPTY);
				return InteractionResult.CONSUME;
			}
			return InteractionResult.FAIL;
		}
		if (!(ctx.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = ctx.getItemInHand();
		if (FateHelmetRegistry.get(level).isStale(stack)) {
			player.setItemInHand(ctx.getHand(), ItemStack.EMPTY);
			return InteractionResult.FAIL;
		}
		Vec3 at = placeSpot(ctx.getClickedPos(), ctx.getClickedFace(), ctx.getClickLocation());
		FateHelmetEntity e = FateHelmetEntity.place(level, at, stack.get(FateHelmet.TAG), player.getYRot() + 180f, false);
		if (!level.noCollision(e, e.getBoundingBox())) {
			return InteractionResult.FAIL; // no room there
		}
		level.addFreshEntity(e);
		player.setItemInHand(ctx.getHand(), ItemStack.EMPTY);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.2f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.6f, 0.8f);
		return InteractionResult.CONSUME;
	}

	/** Where a helmet set down on {@code face} of {@code pos} sits: on top of the block, or against its side. */
	static Vec3 placeSpot(BlockPos pos, Direction face, Vec3 click) {
		if (face == Direction.UP) {
			return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
		}
		if (face == Direction.DOWN) {
			return new Vec3(pos.getX() + 0.5, pos.getY() - 0.65, pos.getZ() + 0.5);
		}
		Vec3 n = Vec3.atLowerCornerOf(face.getNormal());
		Vec3 c = Vec3.atCenterOf(pos).add(n.scale(0.5 + 0.32));
		return new Vec3(c.x, Math.floor(click.y) + 0.2, c.z);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level instanceof ServerLevel sl && entity instanceof ServerPlayer holder) {
			FateHelmetGuard.inventoryTick(sl, holder, stack);
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		FateHelmetTag tag = stack.get(FateHelmet.TAG);
		if (tag != null && !tag.ownerName().isEmpty()) {
			tooltip.add(Component.translatable("item.projecthero.helmet_of_fate.owner", tag.ownerName()).withStyle(ChatFormatting.GOLD));
		}
		tooltip.add(Component.translatable("item.projecthero.helmet_of_fate.tip.wear").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.helmet_of_fate.tip.place").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("item.projecthero.helmet_of_fate.tip.summon").withStyle(ChatFormatting.DARK_GRAY));
	}
}
