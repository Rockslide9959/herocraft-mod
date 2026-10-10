package com.projecthero.mod.ironman.gear;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.item.IronManArmorItem;
import com.projecthero.mod.ironman.suit.IronManSuit;
import com.projecthero.mod.ironman.suit.IronManSuits;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;

/**
 * v0.15.1: <b>Stark Gear</b> -- Tony Stark's tinted Stark Glasses, worn in their own slot (not the helmet slot, the suit
 * needs that). Sneak + N opens the Stark Gear screen ({@link StarkGearMenu}) to put them on or take them off.
 *
 * <p>While the glasses are on:
 * <ul>
 *   <li>the wearer has Night Vision (the same hidden ambient effect an Iron Man helmet gives, so the two never fight;
 *       it clears the tick the glasses come off -- see {@code IronManSuitTicker.clearHelmetNightVision});</li>
 *   <li>suits can be <em>called</em> -- off a Suit Platform, by the quick call, the Mark 7 orbital drop, Remote Pilot off a
 *       platform, {@code /ironman suit|part}. Without them every call is refused with {@link #refuseCall}. Putting on a
 *       suit you are carrying (plain C), the Mark 5 suitcase and sending a suit home are not calls and always work;</li>
 *   <li>Protocol Phoenix is armed -- and it only ever brings in a Mark {@value #PHOENIX_MIN_MARK} or later suit.</li>
 * </ul>
 *
 * <p>v0.15.4: the slot can hold the <b>Colantotte Bracelets</b> instead ({@link ColantotteBracelets}) -- they allow suit
 * calling (and arm Protocol Phoenix) just like the glasses, but give no Night Vision; and they make a called Mark 7 come
 * twice as fast and go on with the quick bracelet wrap-on. v0.15.6: they call <em>only</em> the Mark 7
 * ({@link #canCall(Player, String)}) and are used up by that call.
 *
 * <p>The slot is the {@link ModAttachments#STARK_GEAR} attachment (persistent, synced to everyone so they see the
 * glasses). Death without keepInventory drops the glasses where you died, like the rest of the inventory.
 */
public final class StarkGear {
	/** Protocol Phoenix only recalls a suit of this mark or later. */
	public static final int PHOENIX_MIN_MARK = 7;
	/** v0.15.7: the Stark Glasses only call a suit of this mark or later (the Mark 7 is the bracelets' job). */
	public static final int GLASSES_MIN_MARK = 8;
	/** The glasses' Night Vision: same signature as the Iron Man helmet optic (ambient, hidden, no icon, <= 400 ticks). */
	public static final int NIGHT_VISION_TICKS = 400;
	/** Re-applied once it drops below this, so it never reaches the flickering last 10 s. */
	private static final int NIGHT_VISION_REFRESH_BELOW = 300;

	public static final MenuType<StarkGearMenu> MENU = Registry.register(BuiltInRegistries.MENU,
			ProjectHeroMod.id("stark_gear"), new MenuType<>(StarkGearMenu::new, FeatureFlags.VANILLA_SET));

	private StarkGear() {
	}

	/** Client -> server: Sneak + N as Tony Stark -- open the Stark Gear screen. Re-validated here. */
	public record OpenPayload() implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<OpenPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("stark_gear_open"));
		public static final StreamCodec<RegistryFriendlyByteBuf, OpenPayload> CODEC = StreamCodec.unit(new OpenPayload());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.playC2S().register(OpenPayload.TYPE, OpenPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(OpenPayload.TYPE, (payload, context) -> openMenu(context.player()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p) {
				onDeath(p);
			}
		});
	}

	// ---------------------------------------------------------------- state

	/** What is in the Stark Gear slot (empty = nothing). Never mutate the returned stack. */
	public static ItemStack glasses(Player player) {
		return player.getAttachedOrElse(ModAttachments.STARK_GEAR, ItemStack.EMPTY);
	}

	/** True while Stark Glasses are in the Stark Gear slot. Works on both sides (the slot is synced). */
	public static boolean hasGlasses(Player player) {
		return glasses(player).getItem() instanceof StarkGlassesItem;
	}

	/** v0.15.4: true while the Colantotte Bracelets are in the Stark Gear slot. Works on both sides. */
	public static boolean hasBracelets(Player player) {
		return glasses(player).getItem() instanceof ColantotteBraceletsItem;
	}

	/** v0.15.4: can {@code stack} go in the Stark Gear slot (the Stark Glasses or the Colantotte Bracelets)? */
	public static boolean isGear(ItemStack stack) {
		return stack.getItem() instanceof StarkGlassesItem || stack.getItem() instanceof ColantotteBraceletsItem;
	}

	/** Replace the slot's contents (a copy is stored; empty clears it). Server-side -- syncs to every client. */
	public static void setGlasses(Player player, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			player.removeAttached(ModAttachments.STARK_GEAR);
		} else {
			player.setAttached(ModAttachments.STARK_GEAR, stack.copyWithCount(1));
		}
	}

	/**
	 * Put {@code stack} (one item of it) on. Returns what came out of the slot (empty if it was empty), or
	 * {@code stack} itself untouched if it isn't Stark Gear (v0.15.4: the glasses or the bracelets). The caller shrinks
	 * {@code stack} by one on success.
	 */
	public static ItemStack equip(ServerPlayer player, ItemStack stack) {
		if (!isGear(stack)) {
			return stack;
		}
		ItemStack old = glasses(player).copy();
		setGlasses(player, stack);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.ARMOR_EQUIP_GOLD.value(), SoundSource.PLAYERS, 0.8f, 1.3f);
		return old;
	}

	/** Take the glasses off; returns them (empty if nothing was worn). The caller must put the result somewhere. */
	public static ItemStack unequip(ServerPlayer player) {
		ItemStack old = glasses(player).copy();
		setGlasses(player, ItemStack.EMPTY);
		return old;
	}

	// ---------------------------------------------------------------- suit calling

	/** Whether this player may call a suit right now (only with the Stark Glasses -- v0.15.4: or the bracelets -- on). */
	public static boolean canCall(Player player) {
		return hasGlasses(player) || hasBracelets(player);
	}

	/**
	 * v0.15.6: may this player call {@code suitId} right now? The Colantotte Bracelets call only the Mark 7
	 * ({@code IronManSuitUpManager.BRACELET_SUIT}); v0.15.7: the Stark Glasses call only a Mark
	 * {@value #GLASSES_MIN_MARK} or later. Works on both sides (the slot is synced).
	 */
	public static boolean canCall(Player player, String suitId) {
		if (hasGlasses(player)) {
			IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
			return suit != null && (suit.markNumber() >= GLASSES_MIN_MARK || anyMarkForTests(glasses(player)));
		}
		return hasBracelets(player) && com.projecthero.mod.ironman.suit.IronManSuitUpManager.BRACELET_SUIT.equals(suitId);
	}

	/** GameTests only: glasses tagged {@value #TEST_ANY_MARK} still call every mark (the pre-0.15.7 call tests). */
	public static final String TEST_ANY_MARK = "projecthero_test_any_mark";

	private static boolean anyMarkForTests(ItemStack glasses) {
		net.minecraft.world.item.component.CustomData data = glasses.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().getBoolean(TEST_ANY_MARK);
	}

	/** v0.15.7: Protocol Phoenix is armed only by the Stark Glasses (never by the bracelets). Works on both sides. */
	public static boolean phoenixArmed(Player player) {
		return hasGlasses(player);
	}

	/** v0.15.7: a refused call with no gear on says nothing (it used to nag about the glasses). */
	public static void refuseCall(ServerPlayer player) {
	}

	/**
	 * v0.15.6: tell the player why calling {@code suitId} was refused -- only the bracelets (Mark 7 only) say anything;
	 * v0.15.7: no gear, or the glasses with no Mark {@value #GLASSES_MIN_MARK}+, stays silent.
	 */
	public static void refuseCall(ServerPlayer player, String suitId) {
		if (hasBracelets(player) && !hasGlasses(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.ironman.bracelets_mk7_only")
					.withStyle(ChatFormatting.RED), true);
		}
	}

	/** Whether Protocol Phoenix may bring in this suit (Mark {@value #PHOENIX_MIN_MARK} or later). */
	public static boolean phoenixEligible(String suitId) {
		IronManSuit suit = suitId == null ? null : IronManSuits.byId(suitId);
		return suit != null && suit.markNumber() >= PHOENIX_MIN_MARK;
	}

	// ---------------------------------------------------------------- night vision

	/** True if an Iron Man helmet with its own Night Vision optic is worn (then the glasses aren't drawn either). */
	public static boolean ironManHelmetOn(Player player) {
		return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof IronManArmorItem;
	}

	/** The glasses' / helmet optic's Night Vision signature: ambient, no particles, no icon, at most 400 ticks. */
	public static boolean isOptic(MobEffectInstance eff) {
		return eff != null && eff.isAmbient() && !eff.isVisible() && !eff.showIcon() && eff.getDuration() <= NIGHT_VISION_TICKS;
	}

	/**
	 * Per tick (from {@code IronManSuitTicker}, for every player): glasses on -> keep the hidden Night Vision topped up.
	 * Never touches a Night Vision from a potion or a beacon. Removal is {@code IronManSuitTicker.clearHelmetNightVision},
	 * which runs for every player without an optic and now spares a wearer of the glasses.
	 */
	public static void tick(ServerPlayer player) {
		if (hasBracelets(player) && player.level().getGameTime() % 20 == 0
				&& ColantotteBracelets.retired(glasses(player), player.getServer())) {
			// v0.15.4: a pair replaced by a newer one crumbles, worn or not
			setGlasses(player, ItemStack.EMPTY);
			ColantotteBracelets.crumbled(player);
		}
		if (!hasGlasses(player)) {
			return;
		}
		MobEffectInstance eff = player.getEffect(MobEffects.NIGHT_VISION);
		if (!com.projecthero.mod.armor.ArmorNightVision.wanted(player, isOptic(eff))) {
			return; // v0.15.21: lit -- the optic is off (IronManSuitTicker.clearHelmetNightVision takes it away)
		}
		if (eff == null || (isOptic(eff) && eff.getDuration() < NIGHT_VISION_REFRESH_BELOW)) {
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
		}
	}

	// ---------------------------------------------------------------- screen

	/** Open the Stark Gear screen (Tony Stark, or anyone still wearing glasses so they can always take them off). */
	public static void openMenu(ServerPlayer player) {
		if (!TonyStark.hasPower(player) && glasses(player).isEmpty()) {
			return;
		}
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> new StarkGearMenu(id, inv),
				Component.translatable("screen.projecthero.stark_gear.title")));
	}

	// ---------------------------------------------------------------- death

	/** Without keepInventory the glasses drop where the player died, like the rest of the inventory. */
	public static void onDeath(ServerPlayer player) {
		ItemStack worn = glasses(player);
		if (worn.isEmpty() || player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
			return; // keepInventory: the attachment is copied onto the respawned player (copyOnDeath)
		}
		ItemStack drop = worn.copy();
		player.removeAttached(ModAttachments.STARK_GEAR);
		ItemEntity item = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), drop);
		item.setDefaultPickUpDelay();
		player.level().addFreshEntity(item);
	}
}
