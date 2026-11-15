package com.projecthero.mod.targetdummy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.projecthero.mod.hero.AbilitySlot;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * v0.17.5: names and colours a hit on a {@link TargetDummyEntity} -- "Diamond Sword" (a melee swing, crit or not),
 * "Arrow" (the projectile's own name), "Shift+R ability" (a player-caused hit right after an ability key), or the damage
 * type ("Burning", "Explosion", "Poison").
 *
 * <p>Most powers hurt with the plain {@code playerAttack} / {@code mobAttack} / {@code magic} sources, so the source
 * alone cannot say which move it was. The ability-key presses ({@link #notePress}, from the universal ability packet)
 * fill that gap: a player-caused, non-melee, non-projectile hit within {@link #PRESS_WINDOW_TICKS} of a press is
 * labelled with that key. Powers with their own packets (Iron Man, Spider-Man...) still show the damage type.
 *
 * <p>The one static map ({@link #PRESSES}) holds UUIDs and tick stamps only; it is still cleared in
 * {@code ServerStateReset} so nothing carries across worlds.
 */
public final class TargetDummyLabels {
	/** A player-caused hit this long (ticks) after an ability key press is credited to that key (beams keep going). */
	public static final int PRESS_WINDOW_TICKS = 60;

	/** Player -> {slot number, shift (0/1), server tick of the press}. */
	private static final Map<UUID, int[]> PRESSES = new HashMap<>();

	private TargetDummyLabels() {
	}

	/** One label for a hit. */
	public record Label(Component name, DummyDamageKind kind, boolean crit, boolean dot) {
	}

	/** The universal ability packet: remember the key (presses only). */
	public static void notePress(ServerPlayer player, int slot, boolean pressed, boolean shift) {
		if (!pressed || slot < 1 || slot > 8 || player.getServer() == null) {
			return;
		}
		PRESSES.put(player.getUUID(), new int[] { slot, shift ? 1 : 0, player.getServer().getTickCount() });
	}

	public static void clearSessionState() {
		PRESSES.clear();
	}

	/** The key a player pressed within the window, as "R" / "Shift+R", or null. */
	static String recentKey(Player player) {
		int[] p = PRESSES.get(player.getUUID());
		if (p == null || player.getServer() == null || player.getServer().getTickCount() - p[2] > PRESS_WINDOW_TICKS) {
			return null;
		}
		AbilitySlot slot = AbilitySlot.byNumber(p[0]);
		return (p[1] == 1 ? "Shift+" : "") + slot.defaultKey();
	}

	/** Names a hit. {@code melee} is true when a player's own swing produced it (see {@link TargetDummyEntity#markMelee}). */
	public static Label describe(TargetDummyEntity dummy, DamageSource source, boolean melee, boolean crit) {
		Entity attacker = source.getEntity();
		Entity direct = source.getDirectEntity();
		DummyDamageKind kind = kindOf(source);
		boolean dot = attacker == null && !source.is(DamageTypeTags.IS_EXPLOSION);
		if (melee && attacker instanceof LivingEntity living) {
			ItemStack held = living.getMainHandItem();
			Component name = held.isEmpty() ? Component.translatable("projecthero.target_dummy.source.fist") : held.getHoverName();
			return new Label(name, kind, crit, false);
		}
		if (direct != null && direct != attacker) {
			return new Label(direct.getName(), kind == DummyDamageKind.PHYSICAL ? DummyDamageKind.PROJECTILE : kind, false, false);
		}
		if (attacker instanceof Player player && powerSourced(source)) {
			String key = recentKey(player);
			DummyDamageKind k = kind == DummyDamageKind.PHYSICAL ? DummyDamageKind.POWER : kind;
			if (key != null) {
				return new Label(Component.translatable("projecthero.target_dummy.source.ability", key), k, false, false);
			}
			if (source.is(DamageTypes.MOB_ATTACK) || isModType(source)) {
				// a player "mob attack" is always a power (vanilla melee is player_attack)
				return new Label(typeName(dummy, source), DummyDamageKind.POWER, false, false);
			}
		}
		return new Label(typeName(dummy, source), kind, false, dot);
	}

	/** Sources a power deals its damage with (vanilla melee goes through {@code melee} first). */
	private static boolean powerSourced(DamageSource source) {
		return source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK) || source.is(DamageTypes.MAGIC)
				|| source.is(DamageTypes.INDIRECT_MAGIC) || source.is(DamageTypes.GENERIC) || source.is(DamageTypes.LIGHTNING_BOLT)
				|| source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.FREEZE) || source.is(DamageTypes.EXPLOSION)
				|| source.is(DamageTypes.PLAYER_EXPLOSION) || source.is(DamageTypes.SONIC_BOOM) || isModType(source);
	}

	private static boolean isModType(DamageSource source) {
		return source.typeHolder().unwrapKey().map(k -> k.location().getNamespace().equals("projecthero")).orElse(false);
	}

	/** Colour group from the damage type's tags. */
	public static DummyDamageKind kindOf(DamageSource source) {
		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			return DummyDamageKind.EXPLOSION;
		}
		if (source.is(DamageTypeTags.IS_FIRE)) {
			return DummyDamageKind.FIRE;
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return DummyDamageKind.LIGHTNING;
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return DummyDamageKind.FREEZE;
		}
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			return DummyDamageKind.PROJECTILE;
		}
		if (source.is(DamageTypeTags.WITCH_RESISTANT_TO) || source.is(DamageTypes.WITHER) || source.is(DamageTypes.DRAGON_BREATH)) {
			return DummyDamageKind.MAGIC;
		}
		if (isModType(source)) {
			return DummyDamageKind.POWER;
		}
		return DummyDamageKind.PHYSICAL;
	}

	/** "Burning", "Explosion", "Poison"... -- {@code projecthero.target_dummy.source.<msgId>}, else the msgId tidied up. */
	static Component typeName(TargetDummyEntity dummy, DamageSource source) {
		String id = source.getMsgId();
		if (id.equals("magic") && dummy.hasEffect(MobEffects.POISON)) {
			id = "poison";
		}
		return Component.translatableWithFallback("projecthero.target_dummy.source." + id, pretty(id));
	}

	/** "onFire" / "projecthero.alien_fire" / "explosion.player" -> "On fire" / "Alien fire" / "Explosion". */
	static String pretty(String msgId) {
		String s = msgId;
		int dot = s.lastIndexOf('.');
		if (s.startsWith("explosion")) {
			s = "explosion";
		} else if (dot >= 0 && dot < s.length() - 1) {
			s = s.substring(dot + 1);
		}
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '_') {
				b.append(' ');
			} else if (Character.isUpperCase(c) && i > 0) {
				b.append(' ').append(Character.toLowerCase(c));
			} else {
				b.append(c);
			}
		}
		if (b.length() > 0) {
			b.setCharAt(0, Character.toUpperCase(b.charAt(0)));
		}
		return b.toString();
	}
}
