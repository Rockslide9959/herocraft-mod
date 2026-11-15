package com.projecthero.mod.gear;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * v0.17.5 Tier I gadgets: the Smoke Bomb and the Flashbang (and their Vibranium upgrades). Right-click from the
 * hotbar throws one ({@link GadgetProjectile}); it goes off where it lands. Stack to 16, short cooldown.
 */
public class ThrownGadgetItem extends Item {
	public static final int SMOKE_COOLDOWN = 100;
	public static final int FLASH_COOLDOWN = 140;

	private final GadgetCooldowns.Kind kind;
	private final boolean upgraded;

	public ThrownGadgetItem(GadgetCooldowns.Kind kind, boolean upgraded, Properties properties) {
		super(properties);
		this.kind = kind;
		this.upgraded = upgraded;
	}

	public GadgetCooldowns.Kind kind() {
		return kind;
	}

	public boolean upgraded() {
		return upgraded;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6f,
				0.5f / (level.getRandom().nextFloat() * 0.4f + 0.8f));
		if (!level.isClientSide) {
			GadgetProjectile p = new GadgetProjectile(level, player);
			p.setItem(stack.copyWithCount(1));
			p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.2f, 1.0f);
			level.addFreshEntity(p);
			boolean smoke = kind == GadgetCooldowns.Kind.SMOKE;
			GadgetCooldowns.afterUse(player, kind, smoke ? SMOKE_COOLDOWN : FLASH_COOLDOWN,
					smoke ? GearItems.SMOKE_BOMB : GearItems.FLASHBANG, smoke ? GearItems.VIBRANIUM_SMOKE_BOMB : GearItems.VIBRANIUM_FLASHBANG);
		}
		stack.consume(1, player);
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this).getPath();
		GearTooltip.add(tooltip, upgraded ? 3 : 1, "item.projecthero." + id + ".hint");
	}
}
