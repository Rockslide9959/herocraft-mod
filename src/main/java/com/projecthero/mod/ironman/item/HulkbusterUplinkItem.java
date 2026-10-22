package com.projecthero.mod.ironman.item;

import java.util.List;

import com.projecthero.mod.ironman.IronManArmor;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.IronManFlight;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.crate.HulkbusterCrate;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.16.7 (user) Iron Man Era II stage 2: the <b>Hulkbuster Uplink</b> -- the beacon that calls the Hulkbuster I (mark_8)
 * down. It holds the suit's four real armour stacks ({@link SuitcaseContents}, stamped with integrity and charge).
 *
 * <p>v0.16.7 (user) Veronica overhaul: <b>single use</b> -- "make it a one use item, it gets used up once you call in the
 * armour and thats it". Calling the suit (right-click, or C with no suit on) spends it. Afterwards the suit lives on its
 * Suit Platform: a fold (hold Shift+C) or a recall sends it home there in its crate ({@link HulkbusterCrate#toOrbit}), and
 * that platform's Uplink tab programs a new one. A never-used Uplink (creative tab) stands for a fresh suit.
 *
 * <ul>
 *   <li>On the ground: the drop crate comes down where you look ({@link HulkbusterCrate#call}).</li>
 *   <li>In the air (flying, or a good drop below you): a pod comes in above and behind you and the Hulkbuster builds
 *       itself round you piece by piece ({@code HulkbusterPodEntity#callBuild}).</li>
 *   <li>Wearing another suit (any Iron Man suit or armour) is fine: it is kept underneath and comes back when the
 *       Hulkbuster comes off ({@code HulkbusterInnerSuit}).</li>
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
		if (com.projecthero.mod.ironman.HulkbusterInnerSuit.wearingHulkbusterPiece(sp)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.portable_fold_hint").withStyle(ChatFormatting.GRAY), true);
			return InteractionResultHolder.fail(stack);
		}
		if (HulkbusterCrate.recall(sp)) {
			return InteractionResultHolder.success(stack);
		}
		if (!SuitcaseContents.isLegacyEmpty(stack) && SuitcaseContents.isEmpty(stack)) {
			sp.displayClientMessage(Component.translatable("message.projecthero.ironman.hb_uplink.spent").withStyle(ChatFormatting.GOLD), true);
			return InteractionResultHolder.fail(stack);
		}
		boolean ok = airborne(sp) ? HulkbusterCrate.buildInAir(sp) : HulkbusterCrate.call(sp);
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

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.single_use").withStyle(ChatFormatting.GOLD));
		tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		if (SuitcaseContents.isLegacyEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.fresh").withStyle(ChatFormatting.AQUA));
		} else if (SuitcaseContents.isEmpty(stack)) {
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.spent_tip").withStyle(ChatFormatting.GRAY));
		} else {
			int pct = Math.round(100f * IronManEnergy.stackIntegrity(stack, HulkbusterCrate.SUIT_ID)
					/ Math.max(1f, IronManEnergy.maxIntegrity(HulkbusterCrate.SUIT_ID)));
			tooltip.add(Component.translatable("item.projecthero.hulkbuster_uplink.in_orbit", pct).withStyle(ChatFormatting.AQUA));
		}
		tooltip.add(Component.translatable("item.projecthero.ironman.requires_tony_stark").withStyle(ChatFormatting.DARK_AQUA));
	}
}
