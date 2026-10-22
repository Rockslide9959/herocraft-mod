package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;
import com.projecthero.mod.ironman.crate.HulkbusterOrbit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>Hulkbuster Uplink</b> -- the beacon that calls the Hulkbuster I (mark_8)
 * down from orbit.
 *
 * <p>v0.16.7 (user) "the uplink is reusable and the suit returns to orbit": it is a <b>key</b>, never used up. The suit
 * itself waits in its orbit slot ({@link HulkbusterOrbit}: the four real stacks, integrity / per-part damage / charge on
 * them), so the Uplink drops on death and moves between chests like any item without ever holding (or doubling) the suit.
 * <ul>
 *   <li>Suit in orbit -- on the ground the drop crate comes down where you look; in the air a pod builds the Hulkbuster
 *       round you over whatever you wear ({@link HulkbusterCrate#use}).</li>
 *   <li>Suit down (standing in its crate, Sentry Mode, a wreck) -- it is recalled to orbit.</li>
 *   <li>Folding (hold Shift+C) or recalling it sends it back up as it is: no healing in orbit (only the Replacement Pod
 *       repairs it in the field).</li>
 *   <li>Put back into a Suit Platform (right-click the pad or shift-click it in the screen): the suit comes home and is
 *       racked there -- where the platform repairs it -- and the Uplink is used up.</li>
 *   <li>Lost it: the suit stays in orbit; any of your Suit Platforms can cut a new one ("Re-link"), and the lost one goes
 *       dead.</li>
 * </ul>
 */
public class HulkbusterUplinkItem extends Item {
	/** Higher than this above the ground counts as "in the air" for the mid-air build. */
	public static final double AIRBORNE_HEIGHT = 2.5;

	public HulkbusterUplinkItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		if (!(player instanceof ServerPlayer sp)) {
			return InteractionResultHolder.pass(stack);
		}
		if (!TonyStark.hasPower(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.armor_rejects").withStyle(ChatFormatting.RED), true);
			return InteractionResultHolder.fail(stack);
		}
		// v0.16.7 (user): its own crate down with the suit in it -- send it back up (unchanged)
		if (!com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(sp) && HulkbusterCrate.recall(sp)) {
			return InteractionResultHolder.success(stack);
		}
		boolean ok = HulkbusterCrate.use(sp, stack, airborne(sp));
		return ok ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
	}

	/** Flying, or off the ground with a real drop under you (not a hop, not swimming). */
	public static boolean airborne(ServerPlayer p) {
		if (IronManFlight.isFlying(p)) {
			return true;
		}
		if (p.onGround() || p.isInWater() || p.isPassenger() || p.getAbilities().flying && !p.isCreative()) {
			return false;
		}
		var hit = p.level().clip(new net.minecraft.world.level.ClipContext(p.position(), p.position().add(0, -AIRBORNE_HEIGHT, 0),
				net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, p));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
	}

	/** Server: keep the tooltip's view of the suit current (and bind an older Uplink that still holds its suit). */
	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level.isClientSide() || !(entity instanceof ServerPlayer sp) || sp.tickCount % 20 != slot % 20) {
			return;
		}
		if (HulkbusterOrbit.isKey(stack)) {
			HulkbusterOrbit.view(stack, HulkbusterOrbit.get(sp.getServer()).slotOfKey(stack));
		} else if (!SuitcaseContents.isLegacyEmpty(stack) && !SuitcaseContents.isEmpty(stack)) {
			HulkbusterCrate.resolve(sp, stack); // a pre-key Uplink holding the stacks: they move up into an orbit slot
		}
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.reusable").withStyle(ChatFormatting.GOLD));
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		int view = data == null ? 0 : data.copyTag().getInt(HulkbusterOrbit.VIEW_KEY);
		if (HulkbusterOrbit.isKey(stack)) {
			switch (view) {
				case HulkbusterOrbit.VIEW_ORBIT -> tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.in_orbit",
						data.copyTag().getInt(HulkbusterOrbit.PCT_KEY)).withStyle(ChatFormatting.AQUA));
				case HulkbusterOrbit.VIEW_DOWN -> tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.down")
						.withStyle(ChatFormatting.YELLOW));
				case HulkbusterOrbit.VIEW_UNLINKED -> tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.unlinked")
						.withStyle(ChatFormatting.RED));
				default -> {
				}
			}
		} else if (SuitcaseContents.isLegacyEmpty(stack) || !SuitcaseContents.isEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.fresh").withStyle(ChatFormatting.AQUA));
		} else {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.spent_tip").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
