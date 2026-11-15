package com.projecthero.mod.targetdummy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user): the Target Dummy item. Right-click the top or side of a block to stand a {@link TargetDummyEntity} there,
 * facing you (22.5-degree steps); it refuses a spot a block or another dummy fills. A dummy picked back up (sneak +
 * right-click it) keeps its name ({@code custom_name}), its armour ({@code container}: feet, legs, chest, head) and its
 * hit-cooldown setting ({@code custom_data} "HitCooldown"), and gets them back when placed again.
 */
public class TargetDummyItem extends Item {
	private static final EquipmentSlot[] ARMOR = { EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD };

	public TargetDummyItem(Properties properties) {
		super(properties);
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
		AABB box = TargetDummyMod.TARGET_DUMMY.getDimensions().makeBoundingBox(feet.x(), feet.y(), feet.z());
		if (!level.noCollision(null, box) || !level.getEntitiesOfClass(TargetDummyEntity.class, box.inflate(0.1)).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			Consumer<TargetDummyEntity> config = EntityType.createDefaultStackConfig(server, stack, context.getPlayer());
			TargetDummyEntity dummy = TargetDummyMod.TARGET_DUMMY.create(server, config, pos, MobSpawnType.SPAWN_EGG, true, false);
			if (dummy == null) {
				return InteractionResult.FAIL;
			}
			float yaw = TargetDummyEntity.snapYaw(context.getRotation() + 180.0f);
			dummy.moveTo(feet.x(), feet.y(), feet.z(), yaw, 0.0f);
			dummy.setYHeadRot(yaw);
			dummy.setYBodyRot(yaw);
			apply(stack, dummy);
			server.addFreshEntity(dummy);
			level.playSound(null, dummy.getX(), dummy.getY(), dummy.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);
			dummy.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
		}
		stack.shrink(1);
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	/** The armour and setting a picked-up dummy carried. */
	static void apply(ItemStack stack, TargetDummyEntity dummy) {
		ItemContainerContents armour = stack.get(DataComponents.CONTAINER);
		if (armour != null) {
			net.minecraft.core.NonNullList<ItemStack> slots = net.minecraft.core.NonNullList.withSize(4, ItemStack.EMPTY);
			armour.copyInto(slots);
			for (int i = 0; i < 4; i++) {
				if (!slots.get(i).isEmpty()) {
					dummy.setItemSlot(ARMOR[i], slots.get(i).copy());
				}
			}
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().getBoolean("HitCooldown")) {
			dummy.setHitCooldown(true);
		}
	}

	/** The item a picked-up dummy turns into. */
	public static ItemStack stackOf(TargetDummyEntity dummy) {
		ItemStack stack = new ItemStack(TargetDummyMod.ITEM);
		if (dummy.hasCustomName()) {
			stack.set(DataComponents.CUSTOM_NAME, dummy.getCustomName());
		}
		List<ItemStack> armour = new ArrayList<>(4);
		boolean any = false;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = dummy.getItemBySlot(slot).copy();
			any |= !piece.isEmpty();
			armour.add(piece);
		}
		if (any) {
			stack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(armour));
		}
		if (dummy.hitCooldown()) {
			CompoundTag tag = new CompoundTag();
			tag.putBoolean("HitCooldown", true);
			stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		return stack;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.target_dummy.desc").withStyle(ChatFormatting.GRAY));
		ItemContainerContents armour = stack.get(DataComponents.CONTAINER);
		if (armour != null) {
			armour.nonEmptyItems().forEach(piece -> tooltip.add(Component.translatable("item.projecthero.target_dummy.wearing", piece.getHoverName())
					.withStyle(ChatFormatting.DARK_AQUA)));
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null && data.copyTag().getBoolean("HitCooldown")) {
			tooltip.add(Component.translatable("item.projecthero.target_dummy.cooldown").withStyle(ChatFormatting.DARK_AQUA));
		}
	}
}
