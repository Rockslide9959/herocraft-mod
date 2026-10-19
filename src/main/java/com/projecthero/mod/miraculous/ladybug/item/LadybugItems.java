package com.projecthero.mod.miraculous.ladybug.item;

import java.util.UUID;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.miraculous.Kwami;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.ladybug.LadybugConfig;
import com.projecthero.mod.miraculous.ladybug.LadybugKeys;
import com.projecthero.mod.miraculous.ladybug.entity.LuckyTntEntity;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.level.Level;

/**
 * v0.16.6: the Lucky Charms -- red with black spots, each one what the moment needs ({@code LuckyCharm.choose}). They
 * belong to the Ladybug who summoned them and to that one transformation: every charm stack carries her id and the
 * transformation's start ({@code lb_owner} / {@code lb_form}) and disappears the first tick it sits in anyone else's
 * inventory, after the transformation ends, or once Miraculous Ladybug has used it ({@link #expired}).
 */
public final class LadybugItems {
	public static Item LUCKY_APPLE;
	public static Item LUCKY_TNT;
	public static Item LUCKY_PARACHUTE;
	public static Item LUCKY_SWORD;
	public static Item LUCKY_BOW;

	private LadybugItems() {
	}

	/** Every charm item. */
	public interface Charm {
	}

	public static void initialize() {
		Item.Properties base = new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant();
		// v0.16.7 (user): fills hunger AND saturation to the top (20 / 20), Regeneration II for a minute, Absorption V
		FoodProperties apple = new FoodProperties.Builder().nutrition(20).saturationModifier(0.5f).alwaysEdible()
				.effect(new MobEffectInstance(MobEffects.HEAL, 1, 1), 1f)
				.effect(new MobEffectInstance(MobEffects.REGENERATION, LadybugConfig.CHARM_APPLE_REGEN_TICKS, 1), 1f)
				.effect(new MobEffectInstance(MobEffects.ABSORPTION, 2400, LadybugConfig.CHARM_APPLE_ABSORPTION_AMPLIFIER), 1f)
				.effect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 0), 1f).build();
		LUCKY_APPLE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ladybug_lucky_apple"),
				new AppleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant().food(apple)));
		LUCKY_TNT = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ladybug_lucky_tnt"), new TntItem(base));
		LUCKY_PARACHUTE = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ladybug_lucky_parachute"), new ChuteItem(base));
		ItemAttributeModifiers sword = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, LadybugConfig.CHARM_SWORD_DAMAGE - 1.0,
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, -2.4, AttributeModifier.Operation.ADD_VALUE),
						EquipmentSlotGroup.MAINHAND)
				.build();
		LUCKY_SWORD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ladybug_lucky_sword"),
				new LuckySwordItem(new Item.Properties().rarity(Rarity.EPIC).fireResistant().attributes(sword)
						.component(DataComponents.UNBREAKABLE, new Unbreakable(false))));
		LUCKY_BOW = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("ladybug_lucky_bow"),
				new LuckyBowItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()
						.component(DataComponents.UNBREAKABLE, new Unbreakable(false))));
	}

	public static boolean isCharm(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof Charm;
	}

	/** A charm stack bound to {@code owner}'s current transformation. */
	public static ItemStack bound(Item item, ServerPlayer owner) {
		ItemStack stack = new ItemStack(item);
		CompoundTag tag = new CompoundTag();
		tag.putUUID("lb_owner", owner.getUUID());
		tag.putLong("lb_form", Miraculous.state(owner).formChangeAt);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	/** Whether a charm stack held by {@code holder} should be gone. */
	public static boolean expired(ItemStack stack, Entity holder) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || !(holder instanceof ServerPlayer p)) {
			return true;
		}
		CompoundTag tag = data.copyTag();
		if (!tag.hasUUID("lb_owner")) {
			return true;
		}
		UUID owner = tag.getUUID("lb_owner");
		if (!owner.equals(p.getUUID()) || !Miraculous.transformedAs(p, Kwami.TIKKI)) {
			return true;
		}
		MiraculousState s = Miraculous.state(p);
		return s.formChangeAt != tag.getLong("lb_form") || s.val(LadybugKeys.V_CHARM) == 0L;
	}

	static void vanishTick(ItemStack stack, Level level, Entity entity) {
		if (!level.isClientSide() && expired(stack, entity)) {
			stack.setCount(0);
		}
	}

	// ---------------------------------------------------------------- the charms

	/** A spotted golden apple: a big heal, Regeneration II, Absorption II, Resistance. */
	public static final class AppleItem extends Item implements Charm {
		public AppleItem(Properties p) {
			super(p);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			vanishTick(stack, level, entity);
		}
	}

	/** Spotted TNT: thrown, bursts on impact ({@link LuckyTntEntity}). */
	public static final class TntItem extends Item implements Charm {
		public TntItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TNT_PRIMED, SoundSource.PLAYERS, 0.8f, 1.2f);
			if (!level.isClientSide()) {
				LuckyTntEntity tnt = new LuckyTntEntity(level, player);
				tnt.setItem(stack.copyWithCount(1));
				tnt.shootFromRotation(player, player.getXRot(), player.getYRot(), -10f, 1.3f, 0.5f);
				level.addFreshEntity(tnt);
			}
			player.awardStat(Stats.ITEM_USED.get(this));
			stack.consume(1, player);
			return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			vanishTick(stack, level, entity);
		}
	}

	/** A spotted parachute: held while falling, the canopy opens (the kit's tick: slow fall). */
	public static final class ChuteItem extends Item implements Charm {
		public ChuteItem(Properties p) {
			super(p);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			vanishTick(stack, level, entity);
		}
	}

	/** A spotted sword: {@link LadybugConfig#CHARM_SWORD_DAMAGE} (a Sharpness V netherite sword), unbreakable. */
	public static final class LuckySwordItem extends SwordItem implements Charm {
		public LuckySwordItem(Properties p) {
			super(Tiers.NETHERITE, p);
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			vanishTick(stack, level, entity);
		}
	}

	/** A spotted bow: needs no arrows, its arrows hit like Power V, unbreakable. */
	public static final class LuckyBowItem extends BowItem implements Charm {
		public LuckyBowItem(Properties p) {
			super(p);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResultHolder.consume(player.getItemInHand(hand));
		}

		@Override
		public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
			if (!(user instanceof Player player)) {
				return;
			}
			float power = getPowerForTime(getUseDuration(stack, user) - timeLeft);
			if (power < 0.1f) {
				return;
			}
			if (!level.isClientSide()) {
				Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), stack);
				arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, power * 3.0f, 1.0f);
				arrow.setBaseDamage(LadybugConfig.CHARM_ARROW_DAMAGE); // v0.16.7 (user): Power V
				arrow.setCritArrow(power >= 1.0f);
				arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
				level.addFreshEntity(arrow);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f,
					1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + power * 0.5f);
			player.awardStat(Stats.ITEM_USED.get(this));
		}

		@Override
		public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
			vanishTick(stack, level, entity);
		}
	}
}
