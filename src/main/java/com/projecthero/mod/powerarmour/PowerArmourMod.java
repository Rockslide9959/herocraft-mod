package com.projecthero.mod.powerarmour;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;
import com.projecthero.mod.loot.HeroRarity;
import com.projecthero.mod.powerarmour.block.CoreChargerBlock;
import com.projecthero.mod.powerarmour.block.CoreChargerBlockEntity;
import com.projecthero.mod.powerarmour.entity.PowerArmourFrameEntity;
import com.projecthero.mod.powerarmour.item.ArmourModuleItem;
import com.projecthero.mod.powerarmour.item.PowerArmourFrameItem;
import com.projecthero.mod.powerarmour.item.PowerCoreItem;
import com.projecthero.mod.powerarmour.menu.CoreChargerMenu;
import com.projecthero.mod.powerarmour.menu.PowerArmourEditorMenu;
import com.projecthero.mod.powerarmour.network.PowerArmourEditPayload;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * v0.17.5 Power Armour: registration -- the frame entity, the seven frame items and their platings, the Redstone Circuit Board,
 * the three power cores, the 23 upgrade modules, the Core Charger, the two menus, the edit payload and the event hooks (player
 * tick, damage, death, join). Design: {@code docs/POWER_ARMOUR_DESIGN.md}; numbers: {@code docs/POWER_ARMOUR_REFERENCE.md}.
 */
public final class PowerArmourMod {
	public static final EntityType<PowerArmourFrameEntity> FRAME = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ProjectHeroMod.id("power_armour_frame"), EntityType.Builder.<PowerArmourFrameEntity>of(PowerArmourFrameEntity::new, MobCategory.MISC)
					.sized(0.8f, 2.05f).eyeHeight(1.7f).clientTrackingRange(10).fireImmune().build("power_armour_frame"));
	public static final EntityType<com.projecthero.mod.powerarmour.entity.ArmourMissileEntity> MISSILE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("pa_missile"),
			EntityType.Builder.<com.projecthero.mod.powerarmour.entity.ArmourMissileEntity>of(com.projecthero.mod.powerarmour.entity.ArmourMissileEntity::new,
					MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).build("pa_missile"));
	public static final EntityType<com.projecthero.mod.powerarmour.entity.SentryTurretEntity> TURRET = Registry.register(
			BuiltInRegistries.ENTITY_TYPE, ProjectHeroMod.id("pa_sentry_turret"),
			EntityType.Builder.<com.projecthero.mod.powerarmour.entity.SentryTurretEntity>of(com.projecthero.mod.powerarmour.entity.SentryTurretEntity::new,
					MobCategory.MISC).sized(0.7f, 1.0f).clientTrackingRange(8).fireImmune().build("pa_sentry_turret"));

	private static final Map<ArmourTier, PowerArmourFrameItem> FRAMES = new EnumMap<>(ArmourTier.class);
	private static final Map<ArmourTier, Item> PLATINGS = new EnumMap<>(ArmourTier.class);
	private static final Map<CoreType, PowerCoreItem> CORES = new EnumMap<>(CoreType.class);
	private static final Map<ModuleType, ArmourModuleItem> MODULES = new EnumMap<>(ModuleType.class);

	public static final Item CIRCUIT_BOARD = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("redstone_circuit_board"),
			new Item(new Item.Properties()));

	public static final Block CHARGER = Registry.register(BuiltInRegistries.BLOCK, ProjectHeroMod.id("core_charger"),
			new CoreChargerBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f).requiresCorrectToolForDrops()
					.sound(SoundType.METAL).lightLevel(state -> state.getValue(CoreChargerBlock.LIT) ? 10 : 0)));
	public static final Item CHARGER_ITEM = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("core_charger"),
			new BlockItem(CHARGER, new Item.Properties()));
	public static final BlockEntityType<CoreChargerBlockEntity> CHARGER_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			ProjectHeroMod.id("core_charger"), BlockEntityType.Builder.of(CoreChargerBlockEntity::new, CHARGER).build(null));

	public static final MenuType<CoreChargerMenu> CHARGER_MENU = Registry.register(BuiltInRegistries.MENU, ProjectHeroMod.id("core_charger"),
			new MenuType<>(CoreChargerMenu::new, FeatureFlags.VANILLA_SET));
	public static final ExtendedScreenHandlerType<PowerArmourEditorMenu, Integer> EDITOR_MENU = Registry.register(BuiltInRegistries.MENU,
			ProjectHeroMod.id("power_armour_editor"), new ExtendedScreenHandlerType<>(PowerArmourEditorMenu::new, ByteBufCodecs.VAR_INT.cast()));

	static {
		for (ArmourTier tier : ArmourTier.values()) {
			Item.Properties props = HeroRarity.props(rarityOf(tier)).stacksTo(1);
			if (tier.fireproof()) {
				props = props.fireResistant();
			}
			FRAMES.put(tier, Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("power_armour_frame_" + tier.id()),
					new PowerArmourFrameItem(tier, props)));
			Item.Properties plate = HeroRarity.props(rarityOf(tier));
			if (tier.fireproof()) {
				plate = plate.fireResistant();
			}
			PLATINGS.put(tier, Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("power_armour_plating_" + tier.id()),
					new Item(plate)));
		}
		for (CoreType c : CoreType.values()) {
			HeroRarity rarity = c == CoreType.FUSION_CELL ? HeroRarity.UNCOMMON : c == CoreType.ARC_CORE ? HeroRarity.RARE : HeroRarity.EPIC;
			CORES.put(c, Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(c.id()), new PowerCoreItem(c, HeroRarity.props(rarity))));
		}
		for (ModuleType m : ModuleType.values()) {
			MODULES.put(m, Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id(m.itemId()),
					new ArmourModuleItem(m, HeroRarity.props(m.output >= 4 ? HeroRarity.RARE : HeroRarity.UNCOMMON).stacksTo(16))));
		}
	}

	private PowerArmourMod() {
	}

	private static HeroRarity rarityOf(ArmourTier tier) {
		return switch (tier) {
			case IRON, GOLD -> HeroRarity.COMMON;
			case HERO_ALLOY -> HeroRarity.UNCOMMON;
			case DIAMOND, NETHERITE -> HeroRarity.RARE;
			case VIBRANIUM -> HeroRarity.EPIC;
			case CELESTIAL -> HeroRarity.LEGENDARY;
		};
	}

	public static PowerArmourFrameItem frame(ArmourTier tier) {
		return FRAMES.get(tier);
	}

	public static Item plating(ArmourTier tier) {
		return PLATINGS.get(tier);
	}

	public static PowerCoreItem core(CoreType type) {
		return CORES.get(type);
	}

	public static ArmourModuleItem module(ModuleType type) {
		return MODULES.get(type);
	}

	/** Every Power Armour item, in creative-tab order. */
	public static List<Item> allItems() {
		List<Item> out = new ArrayList<>();
		out.addAll(FRAMES.values());
		out.add(CIRCUIT_BOARD);
		out.addAll(PLATINGS.values());
		out.addAll(CORES.values());
		out.add(CHARGER_ITEM);
		out.addAll(MODULES.values());
		return out;
	}

	public static void initialize() {
		PowerArmourAttachments.initialize();
		PayloadTypeRegistry.playC2S().register(PowerArmourEditPayload.TYPE, PowerArmourEditPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(com.projecthero.mod.powerarmour.network.PowerArmourFxPayload.TYPE,
				com.projecthero.mod.powerarmour.network.PowerArmourFxPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PowerArmourEditPayload.TYPE, (payload, context) -> onEdit(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				PowerArmour.tick(player);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(PowerArmour::allowDamage);
		ServerLivingEntityEvents.ALLOW_DEATH.register(PowerArmour::allowDeath);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PowerArmour.onJoin(handler.player));
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> allItems().forEach(entries::accept));
	}

	/** Static scratch state, dropped when the server stops ({@code ServerStateReset}). */
	public static void clearSessionState() {
		com.projecthero.mod.powerarmour.kit.PowerArmourAbilities.clearSessionState();
	}

	private static void onEdit(ServerPlayer player, PowerArmourEditPayload p) {
		if (!(player.containerMenu instanceof PowerArmourEditorMenu menu) || menu.frame() == null || menu.frame().getId() != p.frameId()
				|| !menu.stillValid(player)) {
			return;
		}
		PowerArmourFrameEntity frame = menu.frame();
		SuitData suit = frame.suit();
		switch (p.action()) {
			case PowerArmourEditPayload.PAINT_PRIMARY -> suit.primary = paint(p.value());
			case PowerArmourEditPayload.PAINT_SECONDARY -> suit.secondary = paint(p.value());
			case PowerArmourEditPayload.PAINT_GLOW -> suit.glow = paint(p.value());
			case PowerArmourEditPayload.PICK_UP -> {
				net.minecraft.world.item.ItemStack stack = PowerArmourFrameItem.stackOf(suit);
				if (frame.hasCustomName()) {
					stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, frame.getCustomName());
				}
				player.closeContainer();
				frame.discard();
				PowerArmour.giveOrDrop(player, stack);
				player.serverLevel().playSound(null, frame.getX(), frame.getY() + 1.0, frame.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
						SoundSource.PLAYERS, 0.8f, 1.1f);
				player.displayClientMessage(Component.translatable("message.projecthero.power_armour.picked_up").withStyle(ChatFormatting.GRAY), true);
				return;
			}
			default -> {
				return;
			}
		}
		frame.suitChanged();
		player.serverLevel().playSound(null, frame.getX(), frame.getY() + 1.0, frame.getZ(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 0.6f, 1.2f);
	}

	private static int paint(int value) {
		return value < 0 ? SuitData.DEFAULT_PAINT : value & 0xFFFFFF;
	}
}
