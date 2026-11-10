package com.projecthero.mod.undead;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.undead.data.UndeadState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 (user: "if player shift+right clicks with a sword the stab it into their shoulder, whenever player attacks unarmed they
 * attack with the sword, makes regeneration 10% slower and increase regen delay by 10% please"): the shoulder sword. Undead
 * Shift + right-clicks with any sword in hand and drives it into his off shoulder; it stays there (drawn on him, its
 * enchantments and durability kept: the stack itself is stored in {@link #ATTACHMENT}). While it is in, a bare-handed hit is the
 * sword's hit -- its attack damage and speed ({@link #attributes}) and, through {@code UndeadShoulderSwordMixin} (the player's
 * weapon item), its enchantments (sharpness, fire aspect, knockback...) and its wear; and his regeneration is slower
 * ({@link UndeadRegenMods}). Shift + right click with an empty hand pulls it out into the hand. On death it drops with his things
 * (kept with keepInventory); losing the power puts it back in his inventory. It is never lost or doubled: exactly one place holds
 * the stack at any time.
 */
public final class UndeadShoulderSword {
	/** The sword in his shoulder (absent: none). Saved, copied over on respawn (dropped on death without keepInventory first). */
	public static final AttachmentType<ItemStack> ATTACHMENT = AttachmentRegistry.create(ProjectHeroMod.id("undead_shoulder_sword"),
			builder -> builder.persistent(ItemStack.OPTIONAL_CODEC).copyOnDeath().syncWith(ItemStack.OPTIONAL_STREAM_CODEC, AttachmentSyncPredicate.all()));

	private static final ResourceLocation DAMAGE_ID = PowerToggles.id("undead_shoulder_sword_damage");
	private static final ResourceLocation SPEED_ID = PowerToggles.id("undead_shoulder_sword_speed");

	private UndeadShoulderSword() {
	}

	static void initialize() {
		// class-loads the attachment on both sides (the client needs it registered to receive it)
		ATTACHMENT.identifier();
	}

	// ---------------------------------------------------------------- state

	/** The sword in his shoulder, or {@link ItemStack#EMPTY} (client-safe; the stored stack itself, not a copy). */
	public static ItemStack stored(Player p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		return s == null ? ItemStack.EMPTY : s;
	}

	public static boolean has(Player p) {
		return !stored(p).isEmpty();
	}

	/** Any sword (the vanilla tag, or a modded {@link SwordItem}). */
	public static boolean isSword(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem);
	}

	/**
	 * The weapon a bare-handed hit uses: the shoulder sword while one is in, the main hand is empty and the Bone Sword is not out;
	 * else null (vanilla's). From {@code UndeadShoulderSwordMixin} ({@code Player.getWeaponItem}).
	 */
	public static ItemStack weapon(Player p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		if (s == null || s.isEmpty() || !p.getMainHandItem().isEmpty() || !Undead.hasPower(p) || UndeadBoneSword.held(p)) {
			return null;
		}
		return s;
	}

	/** The stored stack has just broken (worn to nothing by a hit): it must not be mistaken for another hand's item. */
	public static boolean justBroke(Player p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		return s != null && s.isEmpty();
	}

	// ---------------------------------------------------------------- in and out

	/**
	 * Shift + right click (from {@link UndeadKitNet.ShoulderSword}): a sword in the main hand goes into his shoulder; an empty main
	 * hand pulls the one there out. True if anything moved.
	 */
	public static boolean interact(ServerPlayer p) {
		if (!Undead.hasPower(p) || !p.isAlive() || p.isSpectator() || !p.isShiftKeyDown() || UndeadBoneSword.held(p)) {
			return false;
		}
		ItemStack hand = p.getMainHandItem();
		if (isSword(hand)) {
			return stab(p);
		}
		if (hand.isEmpty() && has(p)) {
			return pull(p);
		}
		return false;
	}

	/** The main hand's sword into his shoulder. False (with a line) if one is already in or the hand can't. */
	public static boolean stab(ServerPlayer p) {
		ItemStack hand = p.getMainHandItem();
		if (!isSword(hand) || UndeadMod.handGone(p, InteractionHand.MAIN_HAND)) {
			return false;
		}
		if (has(p)) {
			Undead.say(p, "message.projecthero.undead.shoulder_full", ChatFormatting.GRAY);
			return false;
		}
		if (Undead.missing(p, UndeadState.BODY)) {
			return false;
		}
		// one place at a time: the hand is emptied in the same step the shoulder takes it
		ItemStack sword = hand.copy();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setAttached(ATTACHMENT, sword);
		anim(p, UndeadKitConfig.ANIM_SHOULDER_IN);
		attributes(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = shoulder(p);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_HIT, SoundSource.PLAYERS, 0.7f, 0.7f);
		UndeadSfx.splat(level, at);
		level.sendParticles(Undead.BLOOD, at.x, at.y, at.z, 6, 0.08, 0.08, 0.08, 0.0);
		return true;
	}

	/** Pulls it out into the empty main hand. */
	public static boolean pull(ServerPlayer p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		if (s == null || s.isEmpty() || !p.getMainHandItem().isEmpty() || UndeadMod.handGone(p, InteractionHand.MAIN_HAND)) {
			return false;
		}
		p.removeAttached(ATTACHMENT);
		p.setItemInHand(InteractionHand.MAIN_HAND, s);
		anim(p, UndeadKitConfig.ANIM_SHOULDER_OUT);
		attributes(p);
		ServerLevel level = (ServerLevel) p.level();
		Vec3 at = shoulder(p);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.6f, 1.2f);
		UndeadSfx.bloodBurst(level, at, 0.4f);
		level.sendParticles(Undead.BLOOD, at.x, at.y, at.z, 8, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** Puts it back with his things (the power is gone, or it can't stay): inventory, else dropped at his feet. */
	public static void giveBack(ServerPlayer p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		p.removeAttached(ATTACHMENT);
		attributes(p);
		if (s != null && !s.isEmpty() && !p.getInventory().add(s)) {
			p.drop(s, false);
		}
	}

	/** On death (after vanilla dropped his things): it drops with them, unless keepInventory keeps it (it is copied over). */
	static void onDeath(ServerPlayer p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		if (s == null) {
			return;
		}
		if (s.isEmpty()) {
			p.removeAttached(ATTACHMENT);
			return;
		}
		if (p.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY) || p.isSpectator()) {
			return;
		}
		p.removeAttached(ATTACHMENT);
		p.drop(s, true, false);
	}

	private static void anim(ServerPlayer p, int id) {
		UndeadState n = Undead.state(p).copy();
		n.animId = id;
		n.animStart = p.level().getGameTime();
		Undead.save(p, n);
	}

	/** About where his off shoulder is. */
	public static Vec3 shoulder(Player p) {
		int off = Undead.otherArm(Undead.mainArm(p));
		return UndeadParts.centre(p, UndeadState.BODY).add(UndeadParts.right(p).scale(off == UndeadState.RIGHT_ARM ? 0.28 : -0.28)).add(0, 0.3, 0);
	}

	// ---------------------------------------------------------------- the hit, the wear

	/**
	 * The sword's attack damage and speed on his bare hand while it is in (the hand empty, the katana not out): its own main-hand
	 * modifiers, exactly what holding it would add.
	 */
	static void attributes(ServerPlayer p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		boolean on = s != null && !s.isEmpty() && p.getMainHandItem().isEmpty() && Undead.hasPower(p) && !UndeadBoneSword.held(p);
		if (!on) {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SPEED_ID);
			return;
		}
		double[] add = new double[2];
		s.forEachModifier(EquipmentSlot.MAINHAND, (Holder<Attribute> attr, AttributeModifier mod) -> {
			if (mod.operation() != AttributeModifier.Operation.ADD_VALUE) {
				return;
			}
			if (attr.is(Attributes.ATTACK_DAMAGE)) {
				add[0] += mod.amount();
			} else if (attr.is(Attributes.ATTACK_SPEED)) {
				add[1] += mod.amount();
			}
		});
		double dmg = add[0]; // as if it were held (his Bloody Arts fists add theirs on top, as they do to a held sword)
		if (dmg > 0.0) {
			PowerToggles.modifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID, dmg, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		}
		if (add[1] != 0.0) {
			PowerToggles.modifier(p, Attributes.ATTACK_SPEED, SPEED_ID, add[1], AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(p, Attributes.ATTACK_SPEED, SPEED_ID);
		}
	}

	/** Every tick (any player with one in, from {@link UndeadKit}): the modifiers follow the hand; a sword worn out by a hit is gone. */
	static void tick(ServerPlayer p) {
		ItemStack s = p.getAttachedOrElse(ATTACHMENT, null);
		if (s == null) {
			return;
		}
		if (s.isEmpty()) {
			// broke on the last hit
			p.removeAttached(ATTACHMENT);
			attributes(p);
			Vec3 at = shoulder(p);
			p.level().playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.8f, 0.8f);
			return;
		}
		if (!Undead.hasPower(p)) {
			giveBack(p);
			return;
		}
		attributes(p);
	}
}
