package com.projecthero.mod.gear;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
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
 * v0.17.5 Tier I gadget: the Grapple Line (and its Vibranium upgrade). Right-click from the hotbar fires a hook at the
 * block you look at, up to {@link #range()} blocks, and reels you to it ({@link GrappleHookEntity}). Sneak to let go;
 * firing again re-hooks from mid-air. Each hook costs 1 durability; repaired with Salvaged Tech (Vibranium Shards for
 * the upgrade). A miss costs nothing.
 */
public class GrappleLineItem extends Item {
	public static final double RANGE = 24.0;
	public static final double UPGRADED_RANGE = 40.0;
	public static final int COOLDOWN = 20;
	public static final int UPGRADED_COOLDOWN = 12;

	private final boolean upgraded;

	public GrappleLineItem(boolean upgraded, Properties properties) {
		super(properties);
		this.upgraded = upgraded;
	}

	public boolean upgraded() {
		return upgraded;
	}

	public double range() {
		return upgraded ? UPGRADED_RANGE : RANGE;
	}

	public double pullSpeed() {
		return upgraded ? 1.5 : 1.1;
	}

	/** Where a hook fired by this player would stick, or null when there is nothing in range. */
	public Vec3 aim(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(range()));
		BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		// just outside the face, so the hook sits on the surface
		return hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.08));
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			return InteractionResultHolder.success(stack);
		}
		return fire(player, hand) ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
	}

	/** Server: fires the hook (replacing an old one). Returns false on a miss. */
	public boolean fire(Player player, InteractionHand hand) {
		Level level = player.level();
		ItemStack stack = player.getItemInHand(hand);
		Vec3 anchor = aim(player);
		if (anchor == null) {
			player.displayClientMessage(Component.translatable("projecthero.gear.grapple.no_hold").withStyle(ChatFormatting.GRAY), true);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.5f, 0.6f);
			return false;
		}
		for (GrappleHookEntity old : level.getEntitiesOfClass(GrappleHookEntity.class, player.getBoundingBox().inflate(UPGRADED_RANGE + 8),
				h -> h.isOwnedBy(player))) {
			old.discard();
		}
		level.addFreshEntity(GrappleHookEntity.create(level, player, anchor, pullSpeed(), upgraded));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8f, 1.3f);
		level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 0.9f, 1.2f);
		if (!player.getAbilities().instabuild) {
			stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
		}
		GadgetCooldowns.afterUse(player, GadgetCooldowns.Kind.GRAPPLE, upgraded ? UPGRADED_COOLDOWN : COOLDOWN,
				GearItems.GRAPPLE_LINE, GearItems.VIBRANIUM_GRAPPLE);
		return true;
	}

	@Override
	public boolean isValidRepairItem(ItemStack stack, ItemStack repair) {
		return repair.is(upgraded ? com.projecthero.mod.loot.LootItems.VIBRANIUM_SHARD : com.projecthero.mod.loot.LootItems.SALVAGED_TECH);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		GearTooltip.add(tooltip, upgraded ? 3 : 1, upgraded ? "item.projecthero.vibranium_grapple.hint" : "item.projecthero.grapple_line.hint");
	}
}
