package com.projecthero.mod.andy;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.andy.data.AndyState;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * v0.17.3 (user: "if the players torso gets destroyed their armour falls off, if an arm gets destroyed that shoulder pad doesnt
 * grow back and players armour value is lowered slightly for having 1 less shoulder pad, if leg gets destroyed remove leggings,
 * if feet get destroyed remove boots"): Andy's worn armour -- any armour, not only his suit -- follows the body under it.
 * <ul>
 *   <li><b>Torso</b> lost: the chestplate falls off (dropped at his feet with a little pop). <b>A leg</b> lost: the leggings
 *       fall off. <b>A foot</b> lost (and a lost leg takes its foot): the boots fall off.</li>
 *   <li>While that part is gone or still regrowing the slot refuses armour (NBC's restricted slots): a piece put on goes back
 *       into the inventory (or drops if it is full) -- nothing is lost or duplicated, creative or not.</li>
 *   <li><b>An arm</b> lost: the chestplate stays on but that arm's shoulder pad is torn off -- the chestplate takes a tenth of
 *       its durability in damage, the pad is not drawn on that side ({@code AndyArmorPartsMixin}) and he loses
 *       {@link #padArmour} armour (and a little toughness on diamond / netherite) per missing pad. The pad does NOT grow back
 *       with the arm: it is remembered on the chestplate itself ({@link #PADS}) and comes back when the chestplate is repaired
 *       (an anvil, Mending, or Andy's Suit's own self-mending) -- for a piece that can't be damaged, when it is taken off and
 *       put on again.</li>
 * </ul>
 */
public final class AndyArmour {
	/** On the chestplate's custom data: the torn-off pads (1 right, 2 left) and its damage just after they went. */
	public static final String PADS = "projecthero_andy_lost_pads";
	public static final String PADS_DAMAGE = "projecthero_andy_pads_damage";
	public static final int PAD_RIGHT = 1, PAD_LEFT = 2;

	private static final ResourceLocation PAD_ARMOUR_ID = PowerToggles.id("andy_lost_pads");
	private static final ResourceLocation PAD_TOUGH_ID = PowerToggles.id("andy_lost_pads_toughness");
	/** The chestplate last seen in the chest slot (an undamageable one gets its pads back when it is put on again). */
	private static final Map<UUID, ItemStack> LAST_CHEST = new ConcurrentHashMap<>();

	private AndyArmour() {
	}

	static void clearSessionState() {
		LAST_CHEST.clear();
	}

	static void forget(UUID id) {
		LAST_CHEST.remove(id);
	}

	/** From {@link Andy#breakPart} and the shatter: the armour on the lost part. */
	static void partLost(ServerPlayer p, int part, int kind) {
		switch (part) {
			case AndyState.BODY -> fallOff(p, EquipmentSlot.CHEST);
			case AndyState.RIGHT_LEG, AndyState.LEFT_LEG -> fallOff(p, EquipmentSlot.LEGS);
			case AndyState.RIGHT_FOOT, AndyState.LEFT_FOOT -> fallOff(p, EquipmentSlot.FEET);
			case AndyState.RIGHT_ARM -> losePad(p, PAD_RIGHT);
			case AndyState.LEFT_ARM -> losePad(p, PAD_LEFT);
			default -> {
			}
		}
	}

	/** The slot can hold armour: what it sits on is there and whole. */
	public static boolean allowed(AndyState s, EquipmentSlot slot) {
		if (s == null || !s.hasPower) {
			return true;
		}
		return switch (slot) {
			case CHEST -> !s.broken(AndyState.BODY);
			case LEGS -> !s.broken(AndyState.RIGHT_LEG) && !s.broken(AndyState.LEFT_LEG);
			case FEET -> !s.broken(AndyState.RIGHT_FOOT) && !s.broken(AndyState.LEFT_FOOT);
			default -> true;
		};
	}

	/** The piece in {@code slot} drops at his feet (a little pop). */
	static void fallOff(ServerPlayer p, EquipmentSlot slot) {
		ItemStack st = p.getItemBySlot(slot);
		if (st.isEmpty()) {
			return;
		}
		p.setItemSlot(slot, ItemStack.EMPTY);
		p.drop(st, true, false);
		p.level().playSound(null, p.getX(), p.getY() + 0.8, p.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.9f, 0.6f);
	}

	/** An arm went: its shoulder pad is torn off the worn chestplate (if it has one and still has that pad). */
	static void losePad(ServerPlayer p, int pad) {
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		if (!(chest.getItem() instanceof ArmorItem) || (lostPads(chest) & pad) != 0) {
			return;
		}
		if (chest.isDamageableItem()) {
			chest.hurtAndBreak(Math.max(1, chest.getMaxDamage() / 10), p, EquipmentSlot.CHEST);
			chest = p.getItemBySlot(EquipmentSlot.CHEST);
			if (chest.isEmpty()) {
				return; // that broke it outright
			}
		}
		int mask = lostPads(chest) | pad;
		int damage = chest.getDamageValue();
		CustomData.update(DataComponents.CUSTOM_DATA, chest, tag -> {
			tag.putInt(PADS, mask);
			tag.putInt(PADS_DAMAGE, damage);
		});
		p.level().playSound(null, p.getX(), p.getY() + 1.3, p.getZ(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8f, 0.5f);
		reconcilePads(p);
	}

	/** The pads torn off {@code chest} (bits {@link #PAD_RIGHT}, {@link #PAD_LEFT}). Client-safe. */
	public static int lostPads(ItemStack chest) {
		if (chest.isEmpty()) {
			return 0;
		}
		CustomData d = chest.get(DataComponents.CUSTOM_DATA);
		return d == null ? 0 : d.copyTag().getInt(PADS);
	}

	/** The armour lost per missing pad: 15% of the chestplate's own, to the nearest half point, at least half a point. */
	public static double padArmour(ItemStack chest) {
		if (!(chest.getItem() instanceof ArmorItem a)) {
			return 0.0;
		}
		return Math.max(0.5, Math.round(a.getDefense() * 0.15 * 2.0) / 2.0);
	}

	/** And 15% of its toughness (diamond 0.3, netherite 0.45 a pad). */
	public static double padToughness(ItemStack chest) {
		return chest.getItem() instanceof ArmorItem a ? a.getToughness() * 0.15 : 0.0;
	}

	private static void clearPads(ItemStack chest) {
		CustomData.update(DataComponents.CUSTOM_DATA, chest, tag -> {
			tag.remove(PADS);
			tag.remove(PADS_DAMAGE);
		});
		if (chest.get(DataComponents.CUSTOM_DATA) != null && chest.get(DataComponents.CUSTOM_DATA).isEmpty()) {
			chest.remove(DataComponents.CUSTOM_DATA);
		}
	}

	/** The armour penalty for the pads missing off the worn chestplate. */
	static void reconcilePads(ServerPlayer p) {
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		int pads = Integer.bitCount(lostPads(chest));
		if (pads == 0) {
			PowerToggles.clearModifier(p, Attributes.ARMOR, PAD_ARMOUR_ID);
			PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, PAD_TOUGH_ID);
			return;
		}
		PowerToggles.modifier(p, Attributes.ARMOR, PAD_ARMOUR_ID, -pads * padArmour(chest), AttributeModifier.Operation.ADD_VALUE);
		double tough = pads * padToughness(chest);
		if (tough > 0.0) {
			PowerToggles.modifier(p, Attributes.ARMOR_TOUGHNESS, PAD_TOUGH_ID, -tough, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ARMOR_TOUGHNESS, PAD_TOUGH_ID);
		}
	}

	/** Every tick, from {@link Andy#tick}: refused slots emptied, repaired chestplates get their pads back, the penalty kept. */
	static void tick(ServerPlayer p) {
		AndyState s = Andy.state(p);
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!allowed(s, slot) && !p.getItemBySlot(slot).isEmpty()) {
				ItemStack st = p.getItemBySlot(slot);
				p.setItemSlot(slot, ItemStack.EMPTY);
				if (!p.getInventory().add(st)) {
					p.drop(st, false, false);
				}
				Andy.say(p, "message.projecthero.andy.armour_refused", ChatFormatting.GRAY);
			}
		}
		ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack last = LAST_CHEST.get(p.getUUID());
		if (chest != last) {
			// put on (or swapped): an undamageable chestplate gets its pads back when it is put on again
			if (!chest.isEmpty() && !chest.isDamageableItem() && lostPads(chest) != 0) {
				clearPads(chest);
			}
			if (chest.isEmpty()) {
				LAST_CHEST.remove(p.getUUID());
			} else {
				LAST_CHEST.put(p.getUUID(), chest);
			}
		}
		if (lostPads(chest) != 0 && chest.isDamageableItem()) {
			CompoundTag tag = chest.get(DataComponents.CUSTOM_DATA).copyTag();
			if (chest.getDamageValue() < tag.getInt(PADS_DAMAGE)) {
				clearPads(chest); // repaired: the pads are back
			}
		}
		if (p.tickCount % 10 == 0 || chest != last) {
			reconcilePads(p);
		}
	}

	/** Gametests: one armour tick. */
	public static void forTestsTick(ServerPlayer p) {
		tick(p);
	}

	/** Gametests / renderers: the worn chestplate's missing pads. */
	public static int wornLostPads(LivingEntity e) {
		return lostPads(e.getItemBySlot(EquipmentSlot.CHEST));
	}
}
