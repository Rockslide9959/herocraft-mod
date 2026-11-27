package com.projecthero.mod.blackpanther;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.blackpanther.data.BlackPantherState;
import com.projecthero.mod.blackpanther.item.BlackPantherItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.hero.power.PowerToggles;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * The single server-side API for Black Panther (T'Challa), the v0.17.3 Hero-Tier power. Nothing else touches
 * {@link BlackPantherState}; every change re-saves through {@link ServerPlayer#setAttached}. Moves in
 * {@link BlackPantherAbilities} (suited) and {@link BlackPantherUnsuited}, damage rules in {@link BlackPantherDamage}, numbers in
 * {@link BlackPantherConfig}.
 *
 * <h2>How it works</h2>
 * Heart-Shaped Herbs grow in jungles; three in a bowl make the Heart-Shaped Herb Soup. Drinking it runs ten seconds of the
 * Ancestral Plane ({@link #drink}), then the power and the silver necklace ({@link #grant}).
 *
 * <h2>The suit (v0.17.7)</h2>
 * The user: "Make the suit just a necklace, player crafts the necklace, equips it and presses H to make it transform into the full
 * suit". The suit is the crafted Black Panther Necklace ({@link BlackPantherItems#NECKLACE}), worn in the chestplate slot exactly
 * like the Flash Ring holding the Flash Suit. With it on, H ({@link #toggleSuit}) runs the suit-up (the texel build out of the
 * necklace, the head last) and the keys switch to the suited kit; H again runs it back. Taking the necklace off while suited draws
 * the suit back as well ({@link #syncSuit}, every tick). While suited the suit gives 20 armour / 8 toughness as attribute
 * modifiers ({@link #reconcile}). The claws are always out in the suit.
 *
 * <h2>Kinetic Energy</h2>
 * 0..100. The suit soaks 40% of every hit and stores it (1 damage = 4 Kinetic); the suited kit's plain moves add a little when
 * they land, the Shift moves spend it. v0.17.7: it never drains while suited; with the suit in the necklace it slowly leaks (1 every
 * 2 s); spending and a death lower it too. The bar lights
 * the suit's trim purple as it fills.
 */
public final class BlackPanther {
	public static final String KEY = "black_panther";

	/** MELEE_ID: legacy (the claw toggle), only ever cleared now. ARMOR_ID / TOUGHNESS_ID: v0.17.7, the suit's armour while suited. */
	private static final ResourceLocation MELEE_ID = PowerToggles.id("black_panther_melee");
	private static final ResourceLocation ARMOR_ID = PowerToggles.id("black_panther_armor");
	private static final ResourceLocation TOUGHNESS_ID = PowerToggles.id("black_panther_toughness");
	private static final ResourceLocation ABSORB_ID = PowerToggles.id("black_panther_absorb");
	/** The herb's (and the suit's) melee: 5 a bare-handed hit out of the suit, 10 in it. */
	private static final ResourceLocation HERB_ID = com.projecthero.mod.combat.MeleeBonus.register(PowerToggles.id("black_panther_herb"));
	/** v0.17.7: the herb's jump (2 blocks out of the suit, 3 in it), a jump-strength modifier in place of the old Jump Boost. */
	private static final ResourceLocation JUMP_ID = PowerToggles.id("black_panther_jump");

	public static final DustParticleOptions PURPLE = new DustParticleOptions(new Vector3f(0.62f, 0.25f, 1.0f), 1.2f);
	public static final DustParticleOptions PURPLE_BIG = new DustParticleOptions(new Vector3f(0.72f, 0.4f, 1.0f), 2.2f);
	public static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.65f, 0.05f, 0.08f), 0.9f);

	private static final Map<UUID, Long> LAST_MESSAGE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_TICK = new ConcurrentHashMap<>();
	/** v0.17.4 Night Eyes: who has the darkness night vision on right now (it switches with a gap, so it never flickers). */
	private static final Map<UUID, Boolean> NIGHT_EYES = new ConcurrentHashMap<>();

	private BlackPanther() {
	}

	public static void clearSessionState() {
		LAST_MESSAGE.clear();
		LAST_TICK.clear();
		NIGHT_EYES.clear();
		BlackPantherAbilities.clearSessionState();
		BlackPantherUnsuited.clearSessionState();
		BlackPantherAbilityManager.clearSessionState();
		BlackPantherDamage.clearSessionState();
	}

	// ---------------------------------------------------------------- state

	public static BlackPantherState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.BLACK_PANTHER_STATE);
	}

	static void save(ServerPlayer player, BlackPantherState state) {
		player.setAttached(ModAttachments.BLACK_PANTHER_STATE, state);
	}

	public static BlackPantherState peek(Player player) {
		return player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
	}

	public static boolean hasPower(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower;
	}

	public static boolean suited(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	public static boolean stalking(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.suited && s.stalking;
	}

	public static boolean absorbing(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.suited && s.absorbing;
	}

	/** v0.17.7 (user: "claws should always be active"): the vibranium claws are out whenever the suit is on. */
	public static boolean clawsOut(Player player) {
		return suited(player);
	}

	/** Shift+N: the mask is off. */
	public static boolean unmasked(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited && s.unmasked;
	}

	/** v0.17.4 Night Eyes is on (the server's view; the client reads the night vision effect). */
	public static boolean nightEyes(Player player) {
		return Boolean.TRUE.equals(NIGHT_EYES.get(player.getUUID()));
	}

	public static float kinetic(Player player) {
		BlackPantherState s = peek(player);
		return s == null ? 0f : s.kinetic;
	}

	/** The Ancestral Plane is showing. Client-safe. */
	public static boolean inVision(Player player) {
		BlackPantherState s = peek(player);
		return s != null && !s.hasPower && s.visionUntil > 0L;
	}

	public static int cooldownRemaining(Player player, String abilityId) {
		BlackPantherState s = peek(player);
		if (s == null) {
			return 0;
		}
		Long ready = s.abilityReadyAt.get(abilityId);
		return ready == null ? 0 : (int) Math.max(0L, ready - player.level().getGameTime());
	}

	static void say(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		long now = player.level().getGameTime();
		Long last = LAST_MESSAGE.get(player.getUUID());
		if (last != null && now - last < 10 && now >= last) {
			return;
		}
		LAST_MESSAGE.put(player.getUUID(), now);
		player.displayClientMessage(Component.translatable(key, args).withStyle(colour), true);
	}

	// ---------------------------------------------------------------- Kinetic Energy

	public static void setKinetic(ServerPlayer player, float value) {
		BlackPantherState n = state(player).copy();
		n.kinetic = Math.max(0f, Math.min(BlackPantherConfig.KINETIC_MAX, value));
		save(player, n);
	}

	/** Stores Kinetic from a hit; {@code absorbed} also counts it against the current stance. */
	static void store(ServerPlayer player, float amount, boolean absorbed) {
		BlackPantherState n = state(player).copy();
		n.kinetic = Math.max(0f, Math.min(BlackPantherConfig.KINETIC_MAX, n.kinetic + amount));
		n.lastFightAt = player.level().getGameTime();
		if (absorbed) {
			n.absorbed += amount;
		}
		save(player, n);
	}

	/** v0.17.7: a plain suited move (the Vibranium Dagger here) landed on a living target -- Kinetic into the bar. */
	public static void gainKinetic(ServerPlayer player, float amount) {
		if (amount > 0f && suited(player)) {
			store(player, amount, false);
		}
	}

	/** A hit landed (the last fight time; the bar no longer drains since v0.17.7). */
	static void fought(ServerPlayer player) {
		BlackPantherState n = state(player).copy();
		n.lastFightAt = player.level().getGameTime();
		save(player, n);
	}

	/** v0.17.7: game time {@code now} is one of the unsuited Kinetic leak's steps (one every {@code KINETIC_UNSUITED_DRAIN_EVERY} ticks). */
	public static boolean kineticDrains(long now) {
		return now % BlackPantherConfig.KINETIC_UNSUITED_DRAIN_EVERY == 0L;
	}

	static boolean spend(ServerPlayer player, float cost) {
		BlackPantherState s = state(player);
		if (s.kinetic + 1.0e-3f < cost) {
			return false;
		}
		setKinetic(player, s.kinetic - cost);
		return true;
	}

	// ---------------------------------------------------------------- the soup and the power

	/** May this player drink the soup right now? Says why not (nothing is consumed on a refusal). */
	public static boolean canDrink(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (s.hasPower) {
			say(player, "message.projecthero.black_panther.already", ChatFormatting.GRAY);
			return false;
		}
		if (s.visionUntil > 0L) {
			return false;
		}
		return !com.projecthero.mod.hero.UniquePowers.refuse(player, KEY);
	}

	/** The soup is drunk: the Ancestral Plane opens. The power follows when it closes ({@link #tick}), even after a death. */
	public static void drink(ServerPlayer player) {
		BlackPantherState n = state(player).copy();
		n.visionUntil = player.level().getGameTime() + BlackPantherConfig.VISION_TICKS;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.vision").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC), false);
	}

	/** The Ancestral Plane's end, or the command. False if the player already holds the power. */
	public static boolean grant(ServerPlayer player) {
		BlackPantherState old = state(player);
		if (old.hasPower) {
			return false;
		}
		if (com.projecthero.mod.hero.UniquePowers.refuse(player, KEY)) {
			if (old.visionUntil > 0L) {
				BlackPantherState n = old.copy();
				n.visionUntil = 0L;
				save(player, n);
			}
			return false;
		}
		HeroTiers.claimPrimary(player, KEY);
		BlackPantherState s = new BlackPantherState();
		s.hasPower = true;
		s.v174 = true;
		s.v177 = true; // v0.17.7: a new Black Panther crafts the necklace
		save(player, s);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		Vec3 c = player.position().add(0, 1.2, 0);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.5f, 1.5f);
		level.sendParticles(PURPLE_BIG, c.x, c.y, c.z, 30, 0.5, 0.8, 0.5, 0.0);
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.black_panther.title").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.black_panther.subtitle").withStyle(ChatFormatting.LIGHT_PURPLE)));
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.acquired")
				.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), false);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.acquired_hint")
				.withStyle(ChatFormatting.GRAY), false);
		return true;
	}

	public static void revoke(ServerPlayer player) {
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
		save(player, new BlackPantherState());
		reconcile(player);
		PowerToggles.clearEffect(player, MobEffects.MOVEMENT_SPEED);
		PowerToggles.clearEffect(player, MobEffects.JUMP);
		PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
		PowerToggles.clearEffect(player, MobEffects.REGENERATION);
		PowerToggles.clearEffect(player, MobEffects.DAMAGE_RESISTANCE);
		ejectNecklace(player); // v0.17.7: only the Black Panther wears the necklace
		forget(player.getUUID());
	}

	private static void forget(UUID id) {
		LAST_MESSAGE.remove(id);
		LAST_TICK.remove(id);
		NIGHT_EYES.remove(id);
		BlackPantherUnsuited.forget(id);
		BlackPantherAbilityManager.forget(id);
		BlackPantherDamage.forget(id);
	}

	/** Death, a new world, leaving: the running moves end. The power and the bar stay. */
	public static void clearTransient(ServerPlayer player) {
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
	}

	/**
	 * A death: the running moves end and the stored Kinetic is lost (v0.17.7: the bar never drains on its own, so a death is the one
	 * reset besides spending it; the state is copied to the respawned player with the empty bar).
	 */
	public static void onDeath(ServerPlayer player) {
		clearTransient(player);
		if (state(player).kinetic > 0f) {
			setKinetic(player, 0f);
		}
	}

	public static void onPlayerJoin(ServerPlayer player) {
		migrate(player);
		convertLegacyItems(player);
		syncSuit(player);
		reconcile(player);
	}

	public static void onPlayerRespawn(ServerPlayer player) {
		syncSuit(player);
		reconcile(player);
	}

	/**
	 * v0.17.7: the suit is a crafted necklace now. A Black Panther who already had the suit before v0.17.7 (the v0.17.4-0.17.6
	 * Panther Habit in the necklace, or a 0.17.3 power, which came with it) is handed the necklace once -- worn straight away (and
	 * still suited) if he had the suit on and the chestplate slot is free, otherwise into the inventory (dropped at his feet if it is
	 * full). The old necklace and claw toggles are cleared. The four armour pieces of the first v0.17.7 build and leftover Panther
	 * Habits are turned into necklaces separately ({@link #convertLegacyItems}).
	 */
	static void migrate(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || s.v177) {
			return;
		}
		boolean hadSuit = s.habit || !s.v174;
		boolean wasSuited = s.suited;
		BlackPantherState n = s.copy();
		n.v174 = true;
		n.v177 = true;
		n.habit = false;
		n.necklaceOff = false;
		n.clawsOut = false;
		save(player, n);
		if (hadSuit) {
			giveNecklace(player, wasSuited);
		}
	}

	/** The necklace into the chestplate slot ({@code wear}, if free) or the inventory (at his feet if it is full). */
	static void giveNecklace(ServerPlayer player, boolean wear) {
		ItemStack stack = new ItemStack(BlackPantherItems.NECKLACE);
		if (wear && player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
			player.setItemSlot(EquipmentSlot.CHEST, stack);
		} else if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/**
	 * v0.17.7: the legacy items in this player's inventory and armour slots become necklaces -- the four armour pieces of the first
	 * v0.17.7 build (one necklace per full set of four, at least one for any pieces, so a set never becomes several) and each old
	 * Panther Habit (one each). Anyone, power or not. A worn legacy chestplate is replaced by a worn necklace, so a Black Panther who
	 * was suited in the pieces stays suited. Called by the legacy items' own inventory tick, on join and when a habit is used.
	 */
	public static void convertLegacyItems(ServerPlayer player) {
		var inv = player.getInventory();
		boolean chestWorn = BlackPantherItems.isLegacyPiece(player.getItemBySlot(EquipmentSlot.CHEST));
		int pieces = 0;
		int habits = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (BlackPantherItems.isLegacyPiece(st)) {
				pieces += st.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			} else if (st.is(BlackPantherItems.HABIT)) {
				habits += st.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		int necklaces = (pieces > 0 ? Math.max(1, pieces / 4) : 0) + habits;
		if (necklaces == 0) {
			return;
		}
		for (int i = 0; i < necklaces; i++) {
			giveNecklace(player, i == 0 && chestWorn);
		}
		player.level().playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
				SoundSource.PLAYERS, 0.8f, 1.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.legacy_converted")
				.withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	// ---------------------------------------------------------------- the suit (the necklace)

	/** The Black Panther Necklace is worn (in the chestplate slot, the Flash Ring's slot). Client-safe. */
	public static boolean wearsNecklace(Player player) {
		return player.getItemBySlot(EquipmentSlot.CHEST).is(BlackPantherItems.NECKLACE);
	}

	/**
	 * v0.17.7 (user, later the same day: "make the black panther necklace model visible when not transformed, should be visible when
	 * the necklace is equipped"): the necklace model is drawn whenever the necklace is worn in the chest slot -- suited, unsuited and
	 * all through the suit-up build / retract -- and never when it is not worn. Client-safe (the chest slot is synced to every viewer).
	 */
	public static boolean showsNecklace(Player player) {
		return wearsNecklace(player) && !player.isSpectator();
	}

	/** The suit is running out or draining back right now (one change at a time). */
	public static boolean transitioning(Player player) {
		BlackPantherState s = peek(player);
		if (s == null || !s.hasPower || s.suitChangeAt <= 0L) {
			return false;
		}
		long age = player.level().getGameTime() - s.suitChangeAt;
		return age >= 0 && age < (s.suited ? BlackPantherConfig.SUIT_UP_TICKS : BlackPantherConfig.SUIT_DOWN_TICKS);
	}

	/**
	 * v0.17.7 H (the Flash Ring's toggle, {@code FlashRing.toggle}): suited, the suit drains back into the necklace; otherwise, with
	 * the necklace worn, the suit runs out of it. A necklace carried in the inventory goes onto the chest first (whatever was in the
	 * chestplate slot takes its place in the inventory), as the Flash Ring does from the inventory. Without one: a hint, nothing else.
	 */
	public static void toggleSuit(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (transitioning(player)) {
			return; // one change at a time
		}
		if (s.suited) {
			suitDown(player);
			return;
		}
		if (!wearsNecklace(player) && !equipFromInventory(player)) {
			say(player, "message.projecthero.black_panther.no_necklace", ChatFormatting.GRAY);
			return;
		}
		suitUp(player);
	}

	/** A necklace from the inventory into the chestplate slot (swapping with what was there). */
	private static boolean equipFromInventory(ServerPlayer player) {
		var inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack st = inv.getItem(i);
			if (st.is(BlackPantherItems.NECKLACE) && st != player.getItemBySlot(EquipmentSlot.CHEST)) {
				ItemStack old = player.getItemBySlot(EquipmentSlot.CHEST).copy();
				player.setItemSlot(EquipmentSlot.CHEST, st.copyWithCount(1));
				st.shrink(1);
				if (!old.isEmpty()) {
					if (inv.getItem(i).isEmpty()) {
						inv.setItem(i, old);
					} else if (!inv.add(old)) {
						player.drop(old, false);
					}
				}
				return true;
			}
		}
		return false;
	}

	/** v0.17.7: the suit lasts only while the necklace is worn -- taking it off (or turning spectator) draws the suit back. Every tick. */
	public static void syncSuit(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower) {
			return;
		}
		if (s.suited && (!wearsNecklace(player) || player.isSpectator())) {
			suitDown(player);
		}
	}

	/** The suit is still running out over him. */
	public static boolean changing(Player player) {
		BlackPantherState s = peek(player);
		if (s == null || !s.hasPower || !s.suited) {
			return false;
		}
		long age = player.level().getGameTime() - s.suitChangeAt;
		return age >= 0 && age < BlackPantherConfig.SUIT_UP_TICKS;
	}

	/** The suited kit takes over: the suit runs out of the necklace (H with the necklace on, {@link #toggleSuit}). */
	public static void suitUp(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || s.suited) {
			return;
		}
		// the unsuited kit's running moves and toggles end -- the suited kit takes the keys
		BlackPantherUnsuited.clear(player);
		long now = player.level().getGameTime();
		BlackPantherState n = state(player).copy();
		n.suited = true;
		n.senses = false;
		n.stalking = false;
		n.absorbing = false;
		n.suitChangeAt = now;
		// v0.17.7 (user: "when transforming into the suit remove the animation for it"): no body clip -- he keeps his own pose and
		// full control while the suit builds over him texel by texel out of the necklace (any running clip is cut)
		n.animId = BlackPantherState.ANIM_NONE;
		n.animStart = now;
		n.unmasked = false;
		save(player, n);
		reconcile(player);
		ServerLevel level = (ServerLevel) player.level();
		BlackPantherSounds.step(player, 0, true); // the nanotech build starts (the rest from tick)
		Vec3 neck = necklacePos(player);
		level.sendParticles(PURPLE, neck.x, neck.y, neck.z, 14, 0.22, 0.12, 0.22, 0.0);
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.WITCH, neck.x, neck.y, neck.z, 6, 0.2, 0.1, 0.2, 0.0);
	}

	/** Where the necklace hangs: the collar, a little in front of the chest. */
	static Vec3 necklacePos(ServerPlayer player) {
		Vec3 f = Vec3.directionFromRotation(0f, player.yBodyRot);
		return player.position().add(f.x * 0.2, player.getBbHeight() * 0.78, f.z * 0.2);
	}

	/** Back to the unsuited kit: the suit drains into the necklace (H again, or the necklace taken off, {@link #syncSuit}). */
	public static void suitDown(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.suited) {
			return;
		}
		BlackPantherAbilities.clear(player);
		BlackPantherUnsuited.clear(player);
		long now = player.level().getGameTime();
		BlackPantherState n = state(player).copy();
		n.suited = false;
		n.suitChangeAt = now;
		n.stalking = false;
		n.absorbing = false;
		n.senses = false;
		n.unmasked = false;
		n.animId = BlackPantherState.ANIM_NONE; // v0.17.7: no body clip, the suit just collapses back into the necklace
		n.animStart = now;
		save(player, n);
		reconcile(player);
		nightEyes(player, n); // v0.17.5: in the dark the herb's Night Eyes carries straight on, no blink
		BlackPantherSounds.step(player, 0, false); // the softer nanotech retract starts (the rest from tick)
	}

	/** Shift+N: the mask off or back on. Suited only. */
	public static void toggleMask(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return;
		}
		if (!s.suited) {
			say(player, "message.projecthero.black_panther.not_suited", ChatFormatting.GRAY);
			return;
		}
		BlackPantherState n = s.copy();
		n.unmasked = !s.unmasked;
		save(player, n);
		ServerLevel level = (ServerLevel) player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.7f,
				n.unmasked ? 0.9f : 1.5f);
	}

	// ---------------------------------------------------------------- stats

	public static void reconcile(ServerPlayer player) {
		// legacy: the claw toggle's +5 now lives in the herb modifier
		PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, MELEE_ID);
		BlackPantherState st = peek(player);
		boolean power = st != null && st.hasPower;
		boolean suited = power && st.suited;
		// v0.17.7: the necklace suit's Vibranium Weave -- 20 armour / 8 toughness (full diamond), only while the suit is on
		if (suited) {
			PowerToggles.modifier(player, Attributes.ARMOR, ARMOR_ID, BlackPantherConfig.SUIT_ARMOR, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID, BlackPantherConfig.SUIT_TOUGHNESS, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, ARMOR_ID);
			PowerToggles.clearModifier(player, Attributes.ARMOR_TOUGHNESS, TOUGHNESS_ID);
		}
		if (power) {
			// v0.17.7 (user): unsuited melee 8, suited 10 (the bare fist's 1 is already there)
			// v0.17.7 (user: "+5 on top of tools and weapons ... do this for black panthers"): the shared melee rule -- a weapon or tool
			// hits for its own damage + 8 / + 10 (MeleeBonus)
			com.projecthero.mod.combat.MeleeBonus.fist(player, HERB_ID, suited ? BlackPantherConfig.SUITED_MELEE : BlackPantherConfig.UNSUITED_MELEE);
			// v0.17.7 (user): Regeneration I without the suit, II with it (our own endless II is taken off first: vanilla never
			// lowers an effect's level by adding the weaker one; a timed buff such as Wakanda Forever is left alone)
			int regen = suited ? BlackPantherConfig.REGEN_SUITED : BlackPantherConfig.REGEN_UNSUITED;
			net.minecraft.world.effect.MobEffectInstance cur = player.getEffect(MobEffects.REGENERATION);
			if (cur != null && cur.isInfiniteDuration() && cur.getAmplifier() > regen) {
				player.removeEffect(MobEffects.REGENERATION);
				// v0.17.7: Wakanda Forever cast in the suit (or the suit put on during it) had its timed Regeneration II merged into the
				// suit's endless II; taking that off must not cut the buff short, so the rest of it goes back on
				int left = BlackPantherUnsuited.wakandaRemaining(player);
				if (left > 0) {
					player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.REGENERATION, left, BlackPantherConfig.RESOLVE_REGEN,
							false, true, true));
				}
			}
			PowerToggles.effect(player, MobEffects.REGENERATION, regen, false);
			// v0.17.7 (user: "give player passive resistance one"): Resistance I, suit on or off
			PowerToggles.effect(player, MobEffects.DAMAGE_RESISTANCE, BlackPantherConfig.RESISTANCE_LEVEL, false);
			// (a stronger timed effect -- Wakanda Forever's Regeneration / Resistance II -- is never stripped by these: vanilla keeps
			// the stronger one and holds the endless I behind it, which comes back when it runs out)
			// v0.17.7 (user: "player can jump 2 blocks without the suit / player can jump 3 blocks with the suit"): jump strength,
			// not Jump Boost (the old endless Jump Boost I is taken off)
			PowerToggles.clearEffect(player, MobEffects.JUMP);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, suited ? BlackPantherConfig.JUMP_SUITED : BlackPantherConfig.JUMP_UNSUITED,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			com.projecthero.mod.combat.MeleeBonus.clear(player, HERB_ID);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, JUMP_ID);
		}
		if (power && st.absorbing) { // Absorb Stance in the suit, Block out of it: he moves slowly while it is up
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID, suited ? BlackPantherConfig.ABSORB_SLOW : BlackPantherConfig.BLOCK_SLOW,
					AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		} else {
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, ABSORB_ID);
		}
	}

	static boolean canAct(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!s.suited) {
			say(player, "message.projecthero.black_panther.not_suited", ChatFormatting.GRAY);
			return false;
		}
		if (changing(player)) {
			say(player, "message.projecthero.black_panther.changing", ChatFormatting.GRAY);
			return false;
		}
		return true;
	}

	/** v0.17.4: may the unsuited kit (T'Challa without the suit) be used right now? */
	static boolean canActBare(ServerPlayer player) {
		BlackPantherState s = state(player);
		if (!s.hasPower || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		return !s.suited;
	}

	/** v0.17.4: the moveset the keys run right now -- the suited kit once the suit is on (or forming), the unsuited one otherwise. */
	public static boolean suitedKit(Player player) {
		BlackPantherState s = peek(player);
		return s != null && s.hasPower && s.suited;
	}

	// ---------------------------------------------------------------- tick

	/** The stack is the Black Panther Necklace. */
	public static boolean isNecklace(ItemStack stack) {
		return BlackPantherItems.NECKLACE != null && stack.is(BlackPantherItems.NECKLACE);
	}

	/**
	 * v0.17.7 (user: "black panther necklace can only be worn by players with black panther ability"): a necklace worn by anyone
	 * without the power (put there by a command, another mod, or worn when the power was lost) pops off into the inventory, or drops
	 * at his feet if it is full. Every tick for every player, and straight away from {@link #revoke}. True if one came off.
	 */
	public static boolean ejectNecklace(ServerPlayer player) {
		if (!wearsNecklace(player) || hasPower(player)) {
			return false;
		}
		ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
		player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
		if (!player.getInventory().add(worn)) {
			player.drop(worn, false);
		}
		player.displayClientMessage(Component.translatable("message.projecthero.black_panther.necklace_refuse").withStyle(ChatFormatting.GRAY), true);
		return true;
	}

	public static void tick(ServerPlayer player) {
		ejectNecklace(player);
		BlackPantherState s = player.getAttachedOrElse(ModAttachments.BLACK_PANTHER_STATE, null);
		if (s == null) {
			return;
		}
		long now = player.level().getGameTime();
		if (!s.hasPower) {
			if (s.visionUntil > 0L && s.visionUntil <= now && player.isAlive()) {
				grant(player); // the Ancestral Plane closes
			}
			return;
		}
		Long prev = LAST_TICK.put(player.getUUID(), now);
		if (prev != null && prev == now) {
			return;
		}
		if (!s.v177) {
			migrate(player);
		}
		syncSuit(player);
		s = state(player);
		if (player.tickCount % 20 == 0) {
			reconcile(player);
		}
		if (player.tickCount % 40 == 0) {
			// the herb: Speed I, suit on or off (v0.17.7: the jump is a jump-strength modifier now, see reconcile)
			PowerToggles.effect(player, MobEffects.MOVEMENT_SPEED, 0, false);
		}
		if (player.tickCount % 10 == 0) {
			nightEyes(player, s);
		}
		// v0.17.7: the nanotech build / retract sound, off the synced suit clock (step 0 played by suitUp / suitDown)
		if (s.suitChangeAt > 0L && now > s.suitChangeAt) {
			BlackPantherSounds.step(player, (int) Math.min(Integer.MAX_VALUE, now - s.suitChangeAt), s.suited);
		}
		// v0.17.7 (user: "make it so that the kinetic energy bar doesnt dissipate"): it never drains while suited. Later the same day
		// (user: "make kinetic energy dissipate slowly when suit is in necklace mode please"): with the suit away (in the necklace, or
		// no suit at all) it slowly leaks -- KINETIC_UNSUITED_DRAIN every KINETIC_UNSUITED_DRAIN_EVERY ticks, only while he is online
		// (a relog keeps the bar). A death still empties it (onDeath).
		if (!s.suited && s.kinetic > 0f && kineticDrains(now)) {
			setKinetic(player, s.kinetic - BlackPantherConfig.KINETIC_UNSUITED_DRAIN);
		}
		BlackPantherAbilities.tick(player);
		BlackPantherUnsuited.tick(player);
	}

	/**
	 * v0.17.4 Night Eyes: the suit's lenses give night vision all the time; out of it, the herb gives it whenever it is dark
	 * where he stands (on at light 6 or less, off again over 9, so it does not flicker at a torch's edge).
	 */
	private static void nightEyes(ServerPlayer player, BlackPantherState s) {
		boolean on;
		if (s.suited) {
			on = true;
		} else {
			int light = player.level().getMaxLocalRawBrightness(BlockPos.containing(player.getEyePosition()));
			boolean was = nightEyes(player);
			on = was ? light <= BlackPantherConfig.NIGHT_EYES_BRIGHT : light <= BlackPantherConfig.NIGHT_EYES_DARK;
		}
		if (on) {
			PowerToggles.effect(player, MobEffects.NIGHT_VISION, 0, false);
			NIGHT_EYES.put(player.getUUID(), true);
		} else if (nightEyes(player) || player.tickCount % 40 == 0) {
			PowerToggles.clearEffect(player, MobEffects.NIGHT_VISION);
			NIGHT_EYES.remove(player.getUUID());
		}
	}
}
