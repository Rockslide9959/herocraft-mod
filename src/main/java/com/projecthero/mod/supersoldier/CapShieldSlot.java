package com.projecthero.mod.supersoldier;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.supersoldier.entity.SoldierShieldEntity;
import com.projecthero.mod.supersoldier.item.CapBattleSuitItem;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.5 (user: "when players with super soldier wear this they can press N and put the shield into a shield slot. the shield will
 * always count as "held" when this happens"): the <b>shield slot</b> of the Captain America Battle Suit's top.
 *
 * <h2>Who has it</h2>
 * A Super Soldier wearing the Battle Suit top ({@link #hasSlot}). Anyone else wearing it gets no slot: a shield left in it pops out into
 * their inventory (else drops) the next tick.
 *
 * <h2>N ({@link #toggle})</h2>
 * <ul>
 *   <li>Slot empty, a Captain America shield in hand (main hand first; Adamantium, Vibranium or the display-only Soldier's Shield):
 *   it goes into the slot and the hand is free.</li>
 *   <li>Slot full, the main hand empty: it comes back out into the main hand (else the empty off hand; both busy: a hint).</li>
 *   <li>Slot full and another Captain America shield in hand: they swap.</li>
 *   <li>The slotted shield is in the air (thrown): nothing (it is coming back to the slot).</li>
 * </ul>
 *
 * <h2>"Always counts as held"</h2>
 * <ul>
 *   <li><b>C</b> throws it straight from the slot ({@link SuperSoldierAbilities#shieldThrow}); it flies home <b>into the slot</b>
 *   ({@link SoldierShieldEntity}: the hand it left otherwise).</li>
 *   <li><b>Block</b>: hold right-click with the main hand empty (the off hand empty or holding something with no use, e.g. a totem) to
 *   raise it ({@link #GUARD}, client -> {@link GuardPayload}). Once it has been up {@link #GUARD_DELAY} ticks (vanilla's shield delay)
 *   it stops everything a raised vanilla shield stops from the front half -- same arc, same {@code bypasses_shield} rule, arrows bounce
 *   off -- and knocks a melee attacker back. No attacking while it is up (the client drops the clicks, as for a raised shield).</li>
 *   <li>The HUD's C key lights up as if a shield were in hand.</li>
 * </ul>
 * The display-only Soldier's Shield can be slotted for looks but, like in the hand, cannot block or be thrown.
 *
 * <h2>Where it shows</h2>
 * On the back (the {@code CapShieldBackLayer} sling look) while idle; strapped to the off-hand forearm while raised (and while it lowers);
 * in flight when thrown. Every viewer: the slot is a component of the worn top (synced with the equipment), the guard a synced flag.
 *
 * <h2>Persistence</h2>
 * The slot is the {@link #SLOT} component ({@link ItemContainerContents}, one item -- it compares by contents, so equipment syncing
 * doesn't resend it every tick) on the top: it survives death / relog and drops with the top. A top carrying a shield anywhere but the
 * chest slot of a Super Soldier hands the shield to its holder's inventory (else drops it) on the next tick ({@link #tick}).
 * No static state.
 */
public final class CapShieldSlot {
	/** The shield in a Battle Suit top's slot (absent = empty). */
	public static final DataComponentType<ItemContainerContents> SLOT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
			ProjectHeroMod.id("cap_shield_slot"),
			DataComponentType.<ItemContainerContents>builder().persistent(ItemContainerContents.CODEC)
					.networkSynchronized(ItemContainerContents.STREAM_CODEC).cacheEncoding().build());

	/** The game tick the slotted shield was raised (absent = lowered). Synced to every viewer (the pose), not saved. */
	public static final AttachmentType<Long> GUARD = AttachmentRegistry.create(ProjectHeroMod.id("cap_shield_guard"),
			builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** Ticks the shield must be up before it blocks (vanilla's raised-shield delay). */
	public static final int GUARD_DELAY = 5;

	private CapShieldSlot() {
	}

	public static void initialize() {
		// SLOT / GUARD register in the static initialiser; touching the class here makes sure it runs during mod init
		PayloadTypeRegistry.playC2S().register(GuardPayload.TYPE, GuardPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(GuardPayload.TYPE, (payload, context) -> {
			if (payload.up()) {
				guardStart(context.player());
			} else {
				guardStop(context.player());
			}
		});
	}

	// ------------------------------------------------------------------------------------------------ queries (client-safe)

	/** A Super Soldier wearing the Battle Suit top: he has the slot. */
	/**
	 * A top that carries the shield slot: the Captain America Battle Suit top, or (v0.17.5, user: "make sure the captain america suit works
	 * properly with the N shield") the Super Soldier suit's own chestplate -- both Captain America suits clamp the shield on.
	 */
	public static boolean isSlotTop(ItemStack s) {
		return s != null && (s.getItem() instanceof CapBattleSuitItem
				|| s.getItem() instanceof com.projecthero.mod.supersoldier.item.SuperSoldierArmorItem a && a.getEquipmentSlot() == EquipmentSlot.CHEST);
	}

	public static boolean hasSlot(Player p) {
		return p != null && SuperSoldier.hasPower(p) && isSlotTop(p.getItemBySlot(EquipmentSlot.CHEST)) && !p.isSpectator();
	}

	/** The shield in this top's slot (EMPTY: none). */
	public static ItemStack get(ItemStack top) {
		ItemContainerContents c = top.get(SLOT);
		return c == null ? ItemStack.EMPTY : c.copyOne();
	}

	private static void set(ItemStack top, ItemStack shield) {
		if (shield.isEmpty()) {
			top.remove(SLOT);
		} else {
			top.set(SLOT, ItemContainerContents.fromItems(List.of(shield)));
		}
	}

	/** The shield in his worn top's slot, whether or not he may use it (EMPTY: none). Client-safe. */
	public static ItemStack slotted(LivingEntity e) {
		ItemStack top = e.getItemBySlot(EquipmentSlot.CHEST);
		return isSlotTop(top) ? get(top) : ItemStack.EMPTY;
	}

	/** The slotted shield he can use as if held (EMPTY: none, or no slot). */
	public static ItemStack usable(Player p) {
		return hasSlot(p) ? slotted(p) : ItemStack.EMPTY;
	}

	/** A slotted shield that blocks and throws (a real {@link ShieldItem}; the display-only Soldier's Shield doesn't). */
	public static boolean hasThrowable(Player p) {
		return usable(p).getItem() instanceof ShieldItem;
	}

	/** True while the slotted shield is raised (synced: safe on the client for the pose). */
	public static boolean isGuarding(Player p) {
		return p.getAttached(GUARD) != null && canGuard(p);
	}

	/**
	 * May he raise the slotted shield now: a throwable shield in the slot, the main hand empty, and the off hand empty or holding
	 * something right-click wouldn't use anyway (a totem, a map...; not food, a block, a bow or another shield).
	 */
	public static boolean canGuard(Player p) {
		if (!hasThrowable(p) || !p.getMainHandItem().isEmpty()) {
			return false;
		}
		ItemStack off = p.getOffhandItem();
		return off.isEmpty() || (off.getUseAnimation() == UseAnim.NONE && !(off.getItem() instanceof BlockItem) && !(off.getItem() instanceof ShieldItem));
	}

	// ------------------------------------------------------------------------------------------------ N

	/** N for a Super Soldier in the Battle Suit top: store / retrieve / swap (see the class notes). Returns whether anything moved. */
	public static boolean toggle(ServerPlayer p) {
		if (!hasSlot(p)) {
			return false;
		}
		ItemStack top = p.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack inSlot = get(top);
		InteractionHand shieldHand = CapShieldStow.isCapShield(p.getMainHandItem()) ? InteractionHand.MAIN_HAND
				: CapShieldStow.isCapShield(p.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
		if (inSlot.isEmpty()) {
			if (shieldHand == null) {
				if (inFlight(p)) {
					SuperSoldier.say(p, "message.projecthero.super_soldier.slot_in_flight", ChatFormatting.GRAY);
				} else {
					SuperSoldier.say(p, "message.projecthero.super_soldier.slot_nothing", ChatFormatting.GRAY);
				}
				return false;
			}
			ItemStack shield = p.getItemInHand(shieldHand);
			if (p.isUsingItem() && p.getUsedItemHand() == shieldHand) {
				p.stopUsingItem();
			}
			p.setItemInHand(shieldHand, ItemStack.EMPTY);
			set(top, shield);
			sound(p, true);
			SuperSoldier.say(p, "message.projecthero.super_soldier.slot_stored", ChatFormatting.AQUA, shield.getHoverName());
		} else if (shieldHand != null) {
			ItemStack shield = p.getItemInHand(shieldHand);
			if (p.isUsingItem() && p.getUsedItemHand() == shieldHand) {
				p.stopUsingItem();
			}
			p.setItemInHand(shieldHand, inSlot);
			set(top, shield);
			sound(p, true);
			SuperSoldier.say(p, "message.projecthero.super_soldier.slot_swapped", ChatFormatting.AQUA, shield.getHoverName());
		} else {
			InteractionHand free = p.getMainHandItem().isEmpty() ? InteractionHand.MAIN_HAND
					: p.getOffhandItem().isEmpty() ? InteractionHand.OFF_HAND : null;
			if (free == null) {
				SuperSoldier.say(p, "message.projecthero.super_soldier.slot_hands_full", ChatFormatting.GRAY);
				return false;
			}
			guardStop(p);
			set(top, ItemStack.EMPTY);
			p.setItemInHand(free, inSlot);
			sound(p, false);
		}
		CapShieldStow.update(p);
		return true;
	}

	private static void sound(ServerPlayer p, boolean in) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), in ? SoundEvents.ARMOR_EQUIP_NETHERITE.value() : SoundEvents.ARMOR_EQUIP_IRON.value(),
				SoundSource.PLAYERS, 0.9f, in ? 1.1f : 1.3f);
	}

	/** A shield he threw from the slot is still flying. */
	private static boolean inFlight(ServerPlayer p) {
		return !p.serverLevel().getEntitiesOfClass(SoldierShieldEntity.class, p.getBoundingBox().inflate(64),
				s -> p.getUUID().equals(s.ownerId()) && s.returnsToSlot()).isEmpty();
	}

	// ------------------------------------------------------------------------------------------------ C (the throw)

	/** Takes the slotted shield out for a throw (EMPTY: nothing throwable there). The thrown entity brings it back to the slot. */
	public static ItemStack takeForThrow(ServerPlayer p) {
		if (!hasThrowable(p)) {
			return ItemStack.EMPTY;
		}
		guardStop(p);
		ItemStack top = p.getItemBySlot(EquipmentSlot.CHEST);
		ItemStack shield = get(top);
		set(top, ItemStack.EMPTY);
		return shield;
	}

	/** A shield thrown from the slot comes home: back into the slot if he still has an empty one. Returns false if it can't. */
	public static boolean putBack(ServerPlayer p, ItemStack shield) {
		if (shield.isEmpty() || !hasSlot(p) || !slotted(p).isEmpty()) {
			return false;
		}
		set(p.getItemBySlot(EquipmentSlot.CHEST), shield);
		CapShieldStow.update(p);
		return true;
	}

	// ------------------------------------------------------------------------------------------------ the guard (right-click)

	public static void guardStart(ServerPlayer p) {
		if (!canGuard(p) || p.getAttached(GUARD) != null) {
			return;
		}
		p.setAttached(GUARD, p.level().getGameTime());
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 0.8f, 0.8f);
	}

	public static void guardStop(ServerPlayer p) {
		if (p.getAttached(GUARD) != null) {
			p.removeAttached(GUARD);
		}
	}

	/**
	 * Does the raised slotted shield stop this hit? The rule of a raised vanilla shield ({@code LivingEntity.isDamageSourceBlocked}): up
	 * for {@link #GUARD_DELAY} ticks, not {@code bypasses_shield}, not a piercing arrow, and coming from the front half.
	 */
	public static boolean blocks(ServerPlayer p, DamageSource source) {
		Long since = p.getAttached(GUARD);
		if (since == null || !canGuard(p) || p.level().getGameTime() - since < GUARD_DELAY || source.is(DamageTypeTags.BYPASSES_SHIELD)) {
			return false;
		}
		if (source.getDirectEntity() instanceof AbstractArrow arrow && arrow.getPierceLevel() > 0) {
			return false;
		}
		Vec3 from = source.getSourcePosition();
		if (from == null) {
			return false;
		}
		Vec3 view = p.calculateViewVector(0f, p.getYHeadRot());
		Vec3 to = from.vectorTo(p.position());
		to = new Vec3(to.x, 0, to.z).normalize();
		return to.dot(view) < 0;
	}

	/** A hit stopped by the slotted shield: the clang, sparks, and a melee attacker shoved back (vanilla's blockedByShield). */
	public static void onBlocked(ServerPlayer p, DamageSource source) {
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 1.0f,
				0.8f + p.getRandom().nextFloat() * 0.4f);
		Vec3 look = p.getLookAngle();
		((ServerLevel) p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, p.getX() + look.x * 0.6, p.getEyeY() - 0.4,
				p.getZ() + look.z * 0.6, 6, 0.2, 0.2, 0.2, 0.2);
		if (source.getDirectEntity() instanceof LivingEntity attacker && attacker != p) {
			double dx = p.getX() - attacker.getX(), dz = p.getZ() - attacker.getZ();
			attacker.knockback(0.5, dx, dz);
		}
	}

	// ------------------------------------------------------------------------------------------------ upkeep

	/**
	 * Every server tick for every player (cheap without a Battle Suit top): a guard that stopped being legal comes down; a slotted
	 * shield on a top worn by someone without the slot, or on a top that isn't worn (in the inventory / cursor after taking it off),
	 * goes into that player's inventory (else drops at his feet).
	 */
	public static void tick(ServerPlayer p) {
		if (p.getAttached(GUARD) != null && !canGuard(p)) {
			guardStop(p);
		}
		ItemStack worn = p.getItemBySlot(EquipmentSlot.CHEST);
		if (isSlotTop(worn) && !hasSlot(p)) {
			eject(p, worn);
		}
		if (p.level().getGameTime() % 5 != 0) {
			return;
		}
		var inv = p.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			eject(p, inv.items.get(i));
		}
		eject(p, inv.offhand.get(0));
		eject(p, p.containerMenu.getCarried());
	}

	private static void eject(ServerPlayer p, @Nullable ItemStack top) {
		if (top == null || !isSlotTop(top)) {
			return;
		}
		ItemStack shield = get(top);
		if (shield.isEmpty()) {
			return;
		}
		set(top, ItemStack.EMPTY);
		if (!p.getInventory().add(shield) || !shield.isEmpty()) {
			p.drop(shield, false);
		}
	}

	// ------------------------------------------------------------------------------------------------ the guard packet

	/** Client -> server: right-click went down / up with the slotted shield usable (re-validated on the server). */
	public record GuardPayload(boolean up) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<GuardPayload> TYPE = new CustomPacketPayload.Type<>(ProjectHeroMod.id("cap_shield_guard"));
		public static final StreamCodec<RegistryFriendlyByteBuf, GuardPayload> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, GuardPayload::up,
				GuardPayload::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
