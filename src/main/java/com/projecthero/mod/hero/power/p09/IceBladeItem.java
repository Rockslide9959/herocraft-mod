package com.projecthero.mod.hero.power.p09;

import java.util.UUID;

import com.projecthero.mod.hero.ExperimentalPowers;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

/**
 * v0.13.22 Cryokinesis N -- the Ice Blade: a sword of conjured ice that exists only in its maker's hand.
 *
 * <p>It is never crafted, never stored and never duplicated. Each blade is stamped (custom data) with its owner and
 * the game time it melts; it deletes itself the moment it is anywhere but its owner's main hand (switched away from,
 * moved in the inventory, put in a container, dropped -- a dropped copy is discarded as it enters the world), when
 * its holder no longer owns Cryokinesis, or when the 30 s run out. Every hit adds a frost stack.
 */
public class IceBladeItem extends Item {
	public static final float DAMAGE = 10.0f;
	public static final int LIFE_TICKS = 30 * 20;
	private static final String TAG_OWNER = "IceBladeOwner";
	private static final String TAG_MELT = "IceBladeMeltAt";

	public IceBladeItem() {
		super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, DAMAGE - 1.0,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.2,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build()));
	}

	/** A fresh blade for {@code owner}, melting at {@code meltAt} (game time). */
	public static ItemStack create(Item item, ServerPlayer owner, long meltAt) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putUUID(TAG_OWNER, owner.getUUID());
		tag.putLong(TAG_MELT, meltAt);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	public static boolean isBlade(ItemStack stack) {
		return stack.getItem() instanceof IceBladeItem;
	}

	private static CompoundTag data(ItemStack stack) {
		CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
		return cd == null ? new CompoundTag() : cd.copyTag();
	}

	public static UUID owner(ItemStack stack) {
		CompoundTag tag = data(stack);
		return tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
	}

	public static long meltAt(ItemStack stack) {
		return data(stack).getLong(TAG_MELT);
	}

	/** May this stack keep existing in {@code player}'s main hand right now? */
	public static boolean valid(ItemStack stack, ServerPlayer player) {
		UUID o = owner(stack);
		return o != null && o.equals(player.getUUID()) && player.getMainHandItem() == stack
				&& player.level().getGameTime() < meltAt(stack)
				&& ExperimentalPowers.owns(player, CryokinesisHandlers.KEY);
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level.isClientSide) {
			return;
		}
		if (!(entity instanceof ServerPlayer player) || !valid(stack, player)) {
			if (entity instanceof ServerPlayer player && level instanceof ServerLevel sl) {
				melt(sl, player);
			}
			stack.setCount(0);
		}
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		if (attacker instanceof ServerPlayer p && target.level() instanceof ServerLevel sl) {
			FrostStacks.add(p, target, 1);
			sl.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + target.getBbHeight() * 0.6,
					target.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
		}
		return true;
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return false;
	}

	static void melt(ServerLevel level, ServerPlayer player) {
		level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + 1.0, player.getZ(), 12,
				0.3, 0.3, 0.3, 0.02);
		level.sendParticles(ParticleTypes.DRIPPING_WATER, player.getX(), player.getY() + 1.0, player.getZ(), 6,
				0.3, 0.3, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS,
				0.6f, 1.6f);
	}

	/** Deletes every Ice Blade {@code player} has anywhere (inventory, cursor, an open container). */
	public static int removeAll(ServerPlayer player) {
		int removed = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (isBlade(inv.getItem(i))) {
				inv.setItem(i, ItemStack.EMPTY);
				removed++;
			}
		}
		if (isBlade(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed++;
		}
		removed += sweepMenu(player);
		return removed;
	}

	/**
	 * Clears Ice Blades out of any slot of the player's open menu (a chest, a crafting grid ...) other than the one
	 * legitimately in their own main hand, and off the cursor. Cheap when no blade is around.
	 */
	public static int sweepMenu(ServerPlayer player) {
		int removed = 0;
		ItemStack hand = player.getMainHandItem();
		for (Slot slot : player.containerMenu.slots) {
			ItemStack st = slot.getItem();
			if (isBlade(st) && st != hand) {
				slot.set(ItemStack.EMPTY);
				removed++;
			}
		}
		if (player.containerMenu != player.inventoryMenu) {
			for (Slot slot : player.inventoryMenu.slots) {
				ItemStack st = slot.getItem();
				if (isBlade(st) && st != hand) {
					slot.set(ItemStack.EMPTY);
					removed++;
				}
			}
		}
		ItemStack carried = player.containerMenu.getCarried();
		if (isBlade(carried)) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed++;
		}
		return removed;
	}
}
