package com.projecthero.mod.wardrobe;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
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
 * v0.16.5: places a {@link MannequinEntity} exactly the way the vanilla armour stand item places an armour stand -- on the
 * clicked face, turned to face you (in 45-degree steps), refusing a spot something already fills.
 */
public class MannequinItem extends Item {
	public MannequinItem(Properties properties) {
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
		AABB box = WardrobeMod.MANNEQUIN.getDimensions().makeBoundingBox(feet.x(), feet.y(), feet.z());
		if (!level.noCollision(null, box) || !level.getEntities(null, box).isEmpty()) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel server) {
			Consumer<MannequinEntity> config = EntityType.createDefaultStackConfig(server, stack, context.getPlayer());
			MannequinEntity mannequin = WardrobeMod.MANNEQUIN.create(server, config, pos, MobSpawnType.SPAWN_EGG, true, true);
			if (mannequin == null) {
				return InteractionResult.FAIL;
			}
			float yaw = Mth.floor((Mth.wrapDegrees(context.getRotation() - 180.0f) + 22.5f) / 45.0f) * 45.0f;
			mannequin.moveTo(mannequin.getX(), mannequin.getY(), mannequin.getZ(), yaw, 0.0f);
			server.addFreshEntityWithPassengers(mannequin);
			level.playSound(null, mannequin.getX(), mannequin.getY(), mannequin.getZ(), SoundEvents.ARMOR_STAND_PLACE,
					SoundSource.BLOCKS, 0.75f, 0.8f);
			mannequin.gameEvent(GameEvent.ENTITY_PLACE, context.getPlayer());
		}
		stack.shrink(1);
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.mannequin.desc").withStyle(ChatFormatting.GRAY));
	}
}
