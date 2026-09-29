package com.projecthero.mod.hero.revamp.d;

import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;

/**
 * v0.13.22 (Light / Invisibility, H): the Hard-Light Blade -- a sword of solid light conjured straight into the
 * caster's hand for {@link #LIFETIME_TICKS}. It is never a real item:
 * <ul>
 *   <li>it is bound to its caster and an expiry time (custom data), and deletes itself the moment it is not in
 *       its caster's main hand, has expired, or is held by anyone else;</li>
 *   <li>any blade that appears in the world as an item entity (dropped, thrown, death drop) is deleted on load
 *       ({@code BatchDContent});</li>
 *   <li>opening any container while one exists dismisses it ({@code InvisibilityLightHandlers}), so it cannot be
 *       shift-clicked into a chest; it cannot be put into an item frame / armour stand either.</li>
 * </ul>
 * The blade renders full-bright (an emissive item model on the client).
 */
public class HardLightBladeItem extends SwordItem {
	public static final int LIFETIME_TICKS = 30 * 20;
	private static final String OWNER = "owner";
	private static final String UNTIL = "until";

	public HardLightBladeItem() {
		super(Tiers.DIAMOND, new Properties()
				.stacksTo(1)
				.rarity(Rarity.RARE)
				.fireResistant()
				.component(DataComponents.UNBREAKABLE, new Unbreakable(false))
				.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 5, -2.2f)));
	}

	/** A fresh blade bound to {@code owner}, expiring at {@code until} (game time). */
	public ItemStack create(Player owner, long until) {
		ItemStack stack = new ItemStack(this);
		CompoundTag tag = new CompoundTag();
		tag.putUUID(OWNER, owner.getUUID());
		tag.putLong(UNTIL, until);
		CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
		return stack;
	}

	public static boolean isBlade(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof HardLightBladeItem;
	}

	private static CompoundTag data(ItemStack stack) {
		CustomData d = stack.get(DataComponents.CUSTOM_DATA);
		return d == null ? new CompoundTag() : d.copyTag();
	}

	public static long until(ItemStack stack) {
		return data(stack).getLong(UNTIL);
	}

	public static boolean ownedBy(ItemStack stack, Player p) {
		CompoundTag t = data(stack);
		if (!t.hasUUID(OWNER)) {
			return false;
		}
		UUID id = t.getUUID(OWNER);
		return id.equals(p.getUUID());
	}

	@Override
	public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
		if (level.isClientSide) {
			return;
		}
		// in the caster's own main hand, unexpired -- anything else and the light simply goes out
		if (!(entity instanceof Player p) || !selected || !ownedBy(stack, p) || level.getGameTime() > until(stack)
				|| !isBlade(p.getMainHandItem()) || p.getMainHandItem() != stack) {
			stack.setCount(0);
			return;
		}
		if (level instanceof ServerLevel sl && p.tickCount % 3 == 0) {
			var hand = com.projecthero.mod.hero.power.AbilityHelpers.handPosition(p);
			sl.sendParticles(BatchDFx.LIGHT, hand.x, hand.y + 0.3, hand.z, 1, 0.08, 0.25, 0.08, 0.0);
		}
	}

	@Override
	public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		target.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.GLOWING, 60, 0, false, true, true));
		if (attacker.level() instanceof ServerLevel sl) {
			sl.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					8, 0.25, 0.35, 0.25, 0.05);
			sl.sendParticles(BatchDFx.LIGHT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					6, 0.3, 0.4, 0.3, 0.0);
		}
		return true;
	}

	@Override
	public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
		// unbreakable light: never damage the stack
	}

	@Override
	public boolean isEnchantable(ItemStack stack) {
		return false;
	}

	@Override
	public boolean canFitInsideContainerItems() {
		return false;
	}

	/** Deletes every blade the player carries anywhere (hands, inventory, cursor, crafting grid). */
	public static boolean purge(Player player) {
		boolean removed = false;
		Inventory inv = player.getInventory();
		for (var list : java.util.List.of(inv.items, inv.offhand, inv.armor)) {
			for (int i = 0; i < list.size(); i++) {
				if (isBlade(list.get(i))) {
					list.set(i, ItemStack.EMPTY);
					removed = true;
				}
			}
		}
		if (isBlade(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed = true;
		}
		CraftingContainer craft = player.inventoryMenu.getCraftSlots();
		for (int i = 0; i < craft.getContainerSize(); i++) {
			if (isBlade(craft.getItem(i))) {
				craft.setItem(i, ItemStack.EMPTY);
				removed = true;
			}
		}
		return removed;
	}

	/** True if the player carries a blade anywhere. */
	public static boolean carries(Player player) {
		Inventory inv = player.getInventory();
		for (var list : java.util.List.of(inv.items, inv.offhand, inv.armor)) {
			for (ItemStack s : list) {
				if (isBlade(s)) {
					return true;
				}
			}
		}
		return isBlade(player.containerMenu.getCarried());
	}
}
