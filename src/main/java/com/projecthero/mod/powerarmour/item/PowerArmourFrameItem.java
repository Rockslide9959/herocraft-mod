package com.projecthero.mod.powerarmour.item;

import java.util.List;

import com.projecthero.mod.powerarmour.ArmourTier;
import com.projecthero.mod.powerarmour.ModuleType;
import com.projecthero.mod.powerarmour.PowerArmourAttachments;
import com.projecthero.mod.powerarmour.PowerArmourMod;
import com.projecthero.mod.powerarmour.SuitData;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 Power Armour: a folded suit of one tier. Right-click the top of a block to stand the frame there, facing you. A frame
 * picked back up (the editor's Pick Up button) keeps its whole {@link SuitData} in the {@link PowerArmourAttachments#SUIT}
 * component -- core, modules, paint, integrity -- and so does a smithing upgrade to the next tier (the item decides the tier).
 */
public class PowerArmourFrameItem extends Item {
	private final ArmourTier tier;

	public PowerArmourFrameItem(ArmourTier tier, Properties properties) {
		super(properties);
		this.tier = tier;
	}

	public ArmourTier tier() {
		return tier;
	}

	/** The suit this stack carries (a fresh frame of the item's tier when it has none); the item's tier always wins. */
	public static SuitData suitOf(ItemStack stack) {
		if (!(stack.getItem() instanceof PowerArmourFrameItem item)) {
			return null;
		}
		SuitData d = stack.get(PowerArmourAttachments.SUIT);
		return d == null ? SuitData.fresh(item.tier) : d.retiered(item.tier);
	}

	public static ItemStack stackOf(SuitData suit) {
		ItemStack stack = new ItemStack(PowerArmourMod.frame(suit.tier));
		stack.set(PowerArmourAttachments.SUIT, suit.copy());
		return stack;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		if (context.getClickedFace() == Direction.DOWN) {
			return InteractionResult.FAIL;
		}
		Level level = context.getLevel();
		BlockPos pos = new BlockPlaceContext(context).getClickedPos();
		ItemStack stack = context.getItemInHand();
		Vec3 feet = Vec3.atBottomCenterOf(pos);
		AABB box = PowerArmourMod.FRAME.getDimensions().makeBoundingBox(feet.x(), feet.y(), feet.z());
		if (!level.noCollision(null, box) || !level.getEntitiesOfClass(PowerArmourFrameEntity.class, box.inflate(0.2)).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			PowerArmourFrameEntity frame = PowerArmourMod.FRAME.create(server);
			if (frame == null) {
				return InteractionResult.FAIL;
			}
			// faces whoever placed it, snapped to 45-degree steps
			float yaw = Mth.wrapDegrees(Math.round((context.getRotation() + 180.0f) / 45.0f) * 45.0f);
			frame.moveTo(feet.x(), feet.y(), feet.z(), yaw, 0.0f);
			frame.setSuit(suitOf(stack));
			if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
				frame.setCustomName(stack.getHoverName());
			}
			server.addFreshEntity(frame);
			level.playSound(null, feet.x(), feet.y(), feet.z(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.BLOCKS, 0.9f, 0.7f);
			level.playSound(null, feet.x(), feet.y(), feet.z(), SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5f, 1.4f);
			frame.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
		}
		stack.shrink(1);
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		SuitData d = suitOf(stack);
		tooltip.add(Component.translatable("item.projecthero.power_armour_frame.desc").withStyle(ChatFormatting.GRAY));
		tooltip.add(Component.translatable("projecthero.power_armour.tooltip.stats", Math.round(tier.armour), tier.slots)
				.withStyle(ChatFormatting.DARK_AQUA));
		tooltip.add(Component.translatable("projecthero.power_armour.tooltip.integrity", Math.round(d.integrity), Math.round(tier.integrity))
				.withStyle(d.wrecked() ? ChatFormatting.RED : ChatFormatting.DARK_AQUA));
		if (d.core != null) {
			tooltip.add(Component.translatable("projecthero.power_armour.tooltip.core",
					Component.translatable("item.projecthero." + d.core.id()), Math.round(d.coreCharge), Math.round(d.capacity()))
					.withStyle(ChatFormatting.AQUA));
		} else {
			tooltip.add(Component.translatable("projecthero.power_armour.tooltip.no_core").withStyle(ChatFormatting.DARK_GRAY));
		}
		for (ModuleType m : d.modules) {
			if (m != null) {
				tooltip.add(Component.literal(" - ").append(Component.translatable(m.translationKey())).withStyle(ChatFormatting.GRAY));
			}
		}
		tooltip.add(Component.translatable("item.projecthero.power_armour_frame.tier_hint." + tier.id()).withStyle(ChatFormatting.GOLD));
	}
}
