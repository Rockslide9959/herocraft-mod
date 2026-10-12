package com.projecthero.mod.frostgiant.item;

import java.util.List;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.frostgiant.FrostCombat;
import com.projecthero.mod.frostgiant.FrostGiantConfig;
import com.projecthero.mod.item.ModItems;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.minecraft.world.item.armortrim.TrimPatterns;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

/**
 * v0.16.1: everything the <b>Rime Shard</b> and the <b>Casket of Ancient Winters</b> do to gear.
 * <ul>
 *   <li><b>Rimed netherite</b>: at a smithing table, a netherite armour piece (base) + a Rime Shard (addition), no
 *       template -> +1 toughness on that piece and a blue frost trim ({@link #RIMED}). The full Rimed set makes you immune
 *       to freezing and lets you walk on powder snow.</li>
 *   <li><b>Rime-touched Mjolnir / Stormbreaker</b>: the same recipe with a Thor weapon as the base -- its thrown hits add
 *       freeze ({@link #RIME_TOUCHED}), and it carries a frost glint.</li>
 *   <li><b>The Casket in the off hand</b>: immune to freezing and powder snow, walk on powder snow.</li>
 *   <li><b>Jotunheim Storm</b>: a worthy Thor's lightning near a Casket holder becomes frost-lightning (adds freeze).</li>
 * </ul>
 */
public final class FrostGear {
	public static final DataComponentType<Boolean> RIMED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ProjectHeroMod.id("rimed"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
	public static final DataComponentType<Boolean> RIME_TOUCHED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("rime_touched"),
			DataComponentType.<Boolean>builder().persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL).build());
	/** The Casket's two cooldowns (game time each is ready again). */
	public static final DataComponentType<Long> BREATH_READY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("casket_breath_ready"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());
	public static final DataComponentType<Long> PRISON_READY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("casket_prison_ready"),
			DataComponentType.<Long>builder().persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG).build());

	private static final Set<Item> NETHERITE_ARMOUR = Set.of(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS,
			Items.NETHERITE_BOOTS);

	public static RecipeSerializer<RimeForging> SERIALIZER;

	private FrostGear() {
	}

	public static void initialize() {
		SERIALIZER = Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, ProjectHeroMod.id("rime_forging"), new Serializer());
	}

	// ---------------------------------------------------------------- queries

	public static boolean isRimed(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(RIMED));
	}

	public static boolean isRimeTouched(ItemStack stack) {
		return Boolean.TRUE.equals(stack.get(RIME_TOUCHED));
	}

	public static boolean holdsCasket(LivingEntity e) {
		return FrostGiantItems.CASKET != null && e.getOffhandItem().is(FrostGiantItems.CASKET);
	}

	/** All four armour slots Rimed netherite. */
	public static boolean fullRimed(LivingEntity e) {
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			if (!isRimed(e.getItemBySlot(slot))) {
				return false;
			}
		}
		return true;
	}

	/** Immune to freezing (the blizzard's cold, powder snow, freeze from hits). */
	public static boolean coldImmune(Entity e) {
		return e instanceof LivingEntity l && (holdsCasket(l) || fullRimed(l));
	}

	/** Walks on top of powder snow. */
	public static boolean walksOnPowderSnow(Entity e) {
		return coldImmune(e);
	}

	public static boolean canRime(ItemStack stack) {
		return NETHERITE_ARMOUR.contains(stack.getItem()) && !isRimed(stack);
	}

	public static boolean canRimeTouch(ItemStack stack) {
		return (stack.is(ModItems.MJOLNIR) || stack.is(ModItems.STORMBREAKER)) && !isRimeTouched(stack);
	}

	// ---------------------------------------------------------------- forging

	/** A Rimed copy of a netherite armour piece: +toughness for its slot, the frost trim, the component. */
	public static ItemStack rime(ItemStack base, HolderLookup.Provider registries) {
		ItemStack out = base.copyWithCount(1);
		if (!(out.getItem() instanceof ArmorItem armor)) {
			return out;
		}
		ItemAttributeModifiers mods = out.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		if (mods.modifiers().isEmpty()) {
			mods = armor.getDefaultAttributeModifiers();
		}
		String slot = armor.getType().getName();
		ResourceLocation id = ProjectHeroMod.id("rimed_toughness_" + slot);
		mods = mods.withModifierAdded(Attributes.ARMOR_TOUGHNESS,
				new AttributeModifier(id, FrostGiantConfig.gear().rimedToughness, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.bySlot(armor.getEquipmentSlot()));
		out.set(DataComponents.ATTRIBUTE_MODIFIERS, mods);
		out.set(RIMED, true);
		try {
			var material = registries.lookupOrThrow(Registries.TRIM_MATERIAL).getOrThrow(TrimMaterials.DIAMOND);
			var pattern = registries.lookupOrThrow(Registries.TRIM_PATTERN).getOrThrow(TrimPatterns.COAST);
			out.set(DataComponents.TRIM, new ArmorTrim(material, pattern));
		} catch (RuntimeException e) {
			// no trim registry (a stripped datapack): the piece is still Rimed, just plain
		}
		return out;
	}

	/** A Rime-touched copy of Mjolnir / Stormbreaker. */
	public static ItemStack rimeTouch(ItemStack base) {
		ItemStack out = base.copyWithCount(1);
		out.set(RIME_TOUCHED, true);
		out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return out;
	}

	/** Tooltip line for a Rimed / Rime-touched stack. */
	public static void appendTooltip(ItemStack stack, List<Component> tooltip) {
		if (isRimed(stack)) {
			tooltip.add(Component.translatable("item.projecthero.rimed").withStyle(ChatFormatting.AQUA));
		}
		if (isRimeTouched(stack)) {
			tooltip.add(Component.translatable("item.projecthero.rime_touched").withStyle(ChatFormatting.AQUA));
		}
	}

	// ---------------------------------------------------------------- the Thor tie-ins

	/**
	 * After any hit lands: a Rime-touched hammer's thrown hit adds freeze; a worthy Thor's lightning near a Casket holder
	 * (Jotunheim Storm) adds freeze too.
	 */
	public static void afterDamage(LivingEntity victim, DamageSource source) {
		if (!(victim.level() instanceof ServerLevel level) || FrostCombat.isFrost(victim)) {
			return;
		}
		if (source.getDirectEntity() instanceof ThrowableItemProjectile thrown && isRimeTouched(thrown.getItem())) {
			FrostCombat.freeze(victim, FrostGiantConfig.gear().rimeTouchedFreezeTicks);
			level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 12, 0.3, 0.4, 0.3,
					0.05);
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING) && source.getEntity() instanceof Player thor && Worthiness.isWorthy(thor)) {
			boolean casketNear = holdsCasket(thor);
			if (!casketNear) {
				for (Player p : level.players()) {
					if (holdsCasket(p) && p.distanceToSqr(victim) <= 16 * 16) {
						casketNear = true;
						break;
					}
				}
			}
			if (casketNear) {
				FrostCombat.freeze(victim, FrostGiantConfig.casket().jotunheimStormFreezeTicks);
				level.sendParticles(ParticleTypes.SNOWFLAKE, victim.getX(), victim.getY() + victim.getBbHeight() * 0.6, victim.getZ(), 16, 0.4, 0.5,
						0.4, 0.08);
			}
		}
	}

	// ---------------------------------------------------------------- the smithing recipe

	/** Base: a netherite armour piece or Mjolnir / Stormbreaker; addition: a Rime Shard; template: none. */
	public static final class RimeForging implements SmithingRecipe {
		@Override
		public boolean isTemplateIngredient(ItemStack stack) {
			return false;
		}

		@Override
		public boolean isBaseIngredient(ItemStack stack) {
			return canRime(stack) || canRimeTouch(stack);
		}

		@Override
		public boolean isAdditionIngredient(ItemStack stack) {
			return FrostGiantItems.RIME_SHARD != null && stack.is(FrostGiantItems.RIME_SHARD);
		}

		@Override
		public boolean matches(SmithingRecipeInput input, Level level) {
			return input.template().isEmpty() && isBaseIngredient(input.base()) && isAdditionIngredient(input.addition());
		}

		@Override
		public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
			return canRime(input.base()) ? rime(input.base(), registries) : rimeTouch(input.base());
		}

		@Override
		public ItemStack getResultItem(HolderLookup.Provider registries) {
			return ItemStack.EMPTY;
		}

		@Override
		public boolean isSpecial() {
			return true;
		}

		@Override
		public RecipeSerializer<?> getSerializer() {
			return SERIALIZER;
		}
	}

	static final class Serializer implements RecipeSerializer<RimeForging> {
		private static final RimeForging INSTANCE = new RimeForging();
		private static final MapCodec<RimeForging> CODEC = MapCodec.unit(INSTANCE);
		private static final StreamCodec<RegistryFriendlyByteBuf, RimeForging> STREAM = StreamCodec.unit(INSTANCE);

		@Override
		public MapCodec<RimeForging> codec() {
			return CODEC;
		}

		@Override
		public StreamCodec<RegistryFriendlyByteBuf, RimeForging> streamCodec() {
			return STREAM;
		}
	}
}
