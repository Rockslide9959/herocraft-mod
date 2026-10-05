package com.projecthero.mod.carnage;

import java.util.List;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.symbiote.Symbiote;
import com.projecthero.mod.symbiote.SymbioteVitalsManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** v0.14.25: Carnage's drop and the spawn eggs. */
public final class CarnageItems {
	public static Item CRIMSON_BIOMASS;
	public static Item CARNAGE_SPAWN_EGG;
	public static Item CRIMSON_SPAWN_SPAWN_EGG;

	private CarnageItems() {
	}

	static void initialize() {
		CRIMSON_BIOMASS = item("crimson_biomass", new CrimsonBiomassItem(new Item.Properties().stacksTo(16).rarity(Rarity.EPIC)));
		CARNAGE_SPAWN_EGG = item("carnage_spawn_egg", new SpawnEggItem(CarnageEntityTypes.CARNAGE, 0xB0101A, 0x120004, new Item.Properties()));
		CRIMSON_SPAWN_SPAWN_EGG = item("crimson_spawn_spawn_egg", new SpawnEggItem(CarnageEntityTypes.CRIMSON_SPAWN, 0xE02030, 0x3A0006, new Item.Properties()));
	}

	public static void addToCreativeTab(CreativeModeTab.Output output) {
		output.accept(CRIMSON_BIOMASS);
		output.accept(CARNAGE_SPAWN_EGG);
		output.accept(CRIMSON_SPAWN_SPAWN_EGG);
	}

	private static Item item(String path, Item item) {
		return Registry.register(BuiltInRegistries.ITEM, ResourceKey.create(Registries.ITEM, ProjectHeroMod.id(path)), item);
	}

	/**
	 * A living chunk of Carnage. A Symbiote host absorbs it: Biomass refills to full and the host goes into a 60-second
	 * crimson frenzy (Strength II, Speed I, Regeneration I). Anyone else can't hold onto it -- it slithers off.
	 */
	public static final class CrimsonBiomassItem extends Item {
		public CrimsonBiomassItem(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) {
				return InteractionResultHolder.success(stack);
			}
			if (!Symbiote.hasSymbiote(sp)) {
				sp.displayClientMessage(Component.translatable("item.projecthero.crimson_biomass.not_host").withStyle(ChatFormatting.RED), true);
				return InteractionResultHolder.fail(stack);
			}
			SymbioteVitalsManager.restoreBiomass(sp);
			sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1));
			sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 0));
			sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 1200, 0));
			server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2f, 0.6f);
			server.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 1.4f);
			server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.8f, 0.05f, 0.08f), 1.2f),
					sp.getX(), sp.getY() + 1, sp.getZ(), 40, 0.4, 0.6, 0.4, 0.0);
			sp.displayClientMessage(Component.translatable("item.projecthero.crimson_biomass.absorbed").withStyle(ChatFormatting.DARK_RED), true);
			sp.getCooldowns().addCooldown(this, 600);
			if (!sp.getAbilities().instabuild) {
				stack.shrink(1);
			}
			return InteractionResultHolder.consume(stack);
		}

		@Override
		public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tooltip, TooltipFlag flag) {
			tooltip.add(Component.translatable("item.projecthero.crimson_biomass.tooltip").withStyle(ChatFormatting.GRAY));
		}
	}
}
