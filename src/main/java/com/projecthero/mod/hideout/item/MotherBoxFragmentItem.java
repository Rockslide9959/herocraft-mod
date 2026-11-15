package com.projecthero.mod.hideout.item;

import java.util.List;

import com.projecthero.mod.darkseid.entity.BoomTubeEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 artifact (Apokolips Outpost): <b>Mother Box Fragment</b> -- a cracked shard of a Mother Box that can still open
 * a short Boom Tube. Use: you step through to the furthest safe spot up to {@link #RANGE} blocks ahead along your
 * line of sight (stopping short of walls; it needs solid ground to land on). 30 s cooldown. If there is nowhere to land,
 * nothing happens and the cooldown is not spent.
 */
public class MotherBoxFragmentItem extends Item {
	public static final double RANGE = 30.0;
	public static final int COOLDOWN = 600;

	public MotherBoxFragmentItem(Properties props) {
		super(props);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}
		Vec3 dest = destination(server, player);
		if (dest == null) {
			player.displayClientMessage(Component.translatable("item.projecthero.mother_box_fragment.blocked").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		Vec3 from = player.position();
		BoomTubeEntity.open(server, from.add(0, 1.0, 0), BoomTubeEntity.Kind.FLASH, 1.4f, 14, null);
		BoomTubeEntity.open(server, dest.add(0, 1.0, 0), BoomTubeEntity.Kind.MELEE, 1.6f, 24, null);
		sp.teleportTo(server, dest.x, dest.y, dest.z, player.getYRot(), player.getXRot());
		sp.resetFallDistance();
		server.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.7f);
		player.getCooldowns().addCooldown(this, COOLDOWN);
		return InteractionResultHolder.success(stack);
	}

	/** Where the tube lets out: the furthest standable spot along the look ray (horizontal-ish), or null. */
	public static Vec3 destination(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		Vec3 dir = new Vec3(look.x, Math.max(-0.5, Math.min(0.5, look.y)), look.z).normalize();
		Vec3 eye = player.getEyePosition();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		double reach = hit.getType() == HitResult.Type.MISS ? RANGE : Math.max(0, hit.getLocation().distanceTo(eye) - 1.0);
		for (double d = reach; d >= 4.0; d -= 1.0) {
			Vec3 p = eye.add(dir.scale(d));
			BlockPos column = BlockPos.containing(p);
			for (int dy = 1; dy >= -6; dy--) {
				BlockPos feet = column.offset(0, dy, 0);
				if (standable(level, feet)) {
					return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
				}
			}
		}
		return null;
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)
				&& level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
				&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
				&& level.getFluidState(feet).isEmpty();
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mother_box_fragment.tooltip").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.hideout.artifact.from", Component.translatable("structure.projecthero.apokolips_outpost"))
				.withStyle(ChatFormatting.DARK_GRAY));
	}
}
