package com.projecthero.mod.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * v0.17.2 testing aid (user: "add an egg that spawns in 10 husks at once ... so that i dont have to manually spawn in husks anymore
 * with commands"): used on a block it drops {@link #COUNT} husks in a ring round that spot. Creative tab only, no recipe; the
 * stack is spent outside creative like any spawn egg.
 */
public class HuskHordeEggItem extends Item {
	public static final int COUNT = 10;

	public HuskHordeEggItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos base = ctx.getClickedPos().relative(ctx.getClickedFace());
		for (int i = 0; i < COUNT; i++) {
			float a = i * Mth.TWO_PI / COUNT;
			double r = i % 2 == 0 ? 1.6 : 2.6;
			BlockPos at = BlockPos.containing(base.getX() + 0.5 + Mth.cos(a) * r, base.getY(), base.getZ() + 0.5 + Mth.sin(a) * r);
			if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
				at = base; // a wall in the way: stack it on the clicked spot instead
			}
			Husk husk = EntityType.HUSK.spawn(level, at, MobSpawnType.SPAWN_EGG);
			if (husk != null && ctx.getPlayer() != null) {
				husk.setYRot(ctx.getPlayer().getYRot() + 180f);
			}
		}
		level.playSound(null, base, SoundEvents.HUSK_AMBIENT, SoundSource.HOSTILE, 1.0f, 0.8f);
		level.gameEvent(ctx.getPlayer(), GameEvent.ENTITY_PLACE, base);
		if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) {
			ctx.getItemInHand().shrink(1);
		}
		return InteractionResult.CONSUME;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("item.projecthero.husk_horde_egg.desc").withStyle(ChatFormatting.GRAY));
	}
}
