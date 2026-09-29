package com.projecthero.mod.moonknight.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.item.MoonKnightItems;
import com.projecthero.mod.moonknight.item.MoonKnightTruncheonItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Z -- the Truncheon / Staff (Moon Knight Phase 4).
 * <ul>
 *   <li><b>TAP</b>: summon the Truncheon into the main hand (whatever was there moves into a free inventory slot first,
 *       and comes back to the hand when it is stowed; a full inventory refuses), or stow it. It only exists while
 *       transformed and out ({@code FLAG_TRUNCHEON}): dropped, stored, stowed or un-suited, it is gone. Every
 *       {@link MoonKnightConfig#TRUNCHEON_COMBO_HITS}rd consecutive hit within {@link MoonKnightConfig#TRUNCHEON_COMBO_WINDOW}
 *       is a slam (+{@link MoonKnightConfig#TRUNCHEON_SLAM_BONUS} x power and knockback); at night each hit on a mob
 *       heals {@link MoonKnightConfig#TRUNCHEON_NIGHT_HEAL} x power.</li>
 *   <li><b>HOLD</b>: extend it into the staff ({@code FLAG_STAFF}) and spin: {@link MoonKnightConfig#STAFF_SPIN_DAMAGE}
 *       x power to everything within {@link MoonKnightConfig#STAFF_SPIN_RADIUS} x power. Summons the truncheon first
 *       if it isn't out. Cooldown {@link MoonKnightConfig#STAFF_SPIN_COOLDOWN}.</li>
 *   <li><b>SNEAK+Z</b>: on the ground, a shockwave ({@link MoonKnightConfig#GROUND_SLAM_RADIUS} x power) that launches
 *       mobs up; in the air, dive straight down and slam where you land -- damage grows with the height dived
 *       ({@link MoonKnightConfig#DIVE_SLAM_DAMAGE_PER_BLOCK} per block, max {@link MoonKnightConfig#DIVE_SLAM_MAX_DAMAGE},
 *       x power), with no fall damage. Cooldown {@link MoonKnightConfig#SLAM_COOLDOWN}.</li>
 * </ul>
 */
public final class MoonKnightTruncheon implements MoonKnightMove {
	public static final MoonKnightTruncheon INSTANCE = new MoonKnightTruncheon();

	private record Displaced(int slot, ItemStack stack) {
	}

	private record Dive(double startY, long start) {
	}

	/** What the summon moved out of the hand, so the stow can hand it back. */
	private static final Map<UUID, Displaced> DISPLACED = new ConcurrentHashMap<>();
	/** Combo: {hits so far, game time of the last hit}. */
	private static final Map<UUID, long[]> COMBO = new ConcurrentHashMap<>();
	/** Game time the staff folds back into the truncheon. */
	private static final Map<UUID, Long> STAFF_UNTIL = new ConcurrentHashMap<>();
	private static final Map<UUID, Dive> DIVES = new ConcurrentHashMap<>();
	/** Game time until which a finished dive keeps falls harmless. */
	private static final Map<UUID, Long> FALL_GRACE = new ConcurrentHashMap<>();

	private MoonKnightTruncheon() {
	}

	// ---------------------------------------------------------------- TAP: summon / stow

	@Override
	public void tap(ServerPlayer player) {
		if (isOut(player)) {
			stow(player, true);
		} else {
			summon(player);
		}
	}

	public static boolean isOut(ServerPlayer player) {
		return MoonKnightAnim.flag(player, MoonKnightAction.FLAG_TRUNCHEON);
	}

	public static boolean isTruncheon(ItemStack stack) {
		return stack.getItem() instanceof MoonKnightTruncheonItem;
	}

	/** Put the truncheon in the main hand. Never deletes anything: a full inventory refuses. True if it is out. */
	public static boolean summon(ServerPlayer player) {
		if (isOut(player) && isTruncheon(player.getMainHandItem())) {
			return true;
		}
		Inventory inv = player.getInventory();
		removeAll(player); // never two
		ItemStack held = inv.getSelected();
		if (!held.isEmpty()) {
			int free = inv.getFreeSlot();
			if (free < 0) {
				player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.truncheon_full")
						.withStyle(ChatFormatting.RED), true);
				return false;
			}
			inv.items.set(free, held);
			DISPLACED.put(player.getUUID(), new Displaced(free, held));
		} else {
			DISPLACED.remove(player.getUUID());
		}
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRUNCHEON, true);
		inv.items.set(inv.selected, new ItemStack(MoonKnightItems.TRUNCHEON));
		inv.setChanged();
		MoonKnightAnim.play(player, MoonKnightAnim.TRUNCHEON_DRAW);
		Vec3 hand = AbilityHelpers.handPosition(player);
		player.serverLevel().sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 8, 0.1, 0.2, 0.1, 0.03);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.8f, 1.5f);
		AbilityHelpers.sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7f, 1.2f);
		return true;
	}

	/** Put it away (and hand back whatever the summon displaced, if it is still where we left it). */
	public static void stow(ServerPlayer player, boolean feedback) {
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_TRUNCHEON, false);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_STAFF, false);
		STAFF_UNTIL.remove(player.getUUID());
		COMBO.remove(player.getUUID());
		removeAll(player);
		Displaced d = DISPLACED.remove(player.getUUID());
		Inventory inv = player.getInventory();
		if (d != null && inv.getSelected().isEmpty() && d.slot() != inv.selected
				&& d.slot() < inv.items.size() && inv.items.get(d.slot()) == d.stack()) {
			inv.items.set(inv.selected, d.stack());
			inv.items.set(d.slot(), ItemStack.EMPTY);
			inv.setChanged();
		}
		if (feedback) {
			AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_NETHERITE, 0.6f, 1.8f);
		}
	}

	/** Delete every truncheon the player has anywhere (inventory, offhand, cursor, an open container). */
	static int removeAll(ServerPlayer player) {
		int removed = 0;
		Inventory inv = player.getInventory();
		for (int i = 0; i < inv.items.size(); i++) {
			if (isTruncheon(inv.items.get(i))) {
				inv.items.set(i, ItemStack.EMPTY);
				removed++;
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isTruncheon(inv.offhand.get(i))) {
				inv.offhand.set(i, ItemStack.EMPTY);
				removed++;
			}
		}
		if (isTruncheon(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
			removed++;
		}
		removed += sweepContainer(player);
		if (removed > 0) {
			inv.setChanged();
		}
		return removed;
	}

	/** A truncheon can't be stored: clear it out of any open container's slots. */
	private static int sweepContainer(ServerPlayer player) {
		if (player.containerMenu == player.inventoryMenu) {
			return 0;
		}
		int removed = 0;
		for (Slot slot : player.containerMenu.slots) {
			if (slot.container != player.getInventory() && isTruncheon(slot.getItem())) {
				slot.set(ItemStack.EMPTY);
				removed++;
			}
		}
		return removed;
	}

	/** There is only ever one truncheon: keep the one in the hand (or the first found) and delete the rest. */
	private static void keepOne(ServerPlayer player) {
		Inventory inv = player.getInventory();
		boolean kept = isTruncheon(inv.getSelected());
		for (int i = 0; i < inv.items.size(); i++) {
			if (i != inv.selected || !kept) {
				if (isTruncheon(inv.items.get(i))) {
					if (kept) {
						inv.items.set(i, ItemStack.EMPTY);
					}
					kept = true;
				}
			}
		}
		for (int i = 0; i < inv.offhand.size(); i++) {
			if (isTruncheon(inv.offhand.get(i))) {
				if (kept) {
					inv.offhand.set(i, ItemStack.EMPTY);
				}
				kept = true;
			}
		}
		if (kept && isTruncheon(player.containerMenu.getCarried())) {
			player.containerMenu.setCarried(ItemStack.EMPTY);
		}
		inv.setChanged();
	}

	private static int count(ServerPlayer player) {
		int n = 0;
		Inventory inv = player.getInventory();
		for (ItemStack s : inv.items) {
			n += isTruncheon(s) ? 1 : 0;
		}
		for (ItemStack s : inv.offhand) {
			n += isTruncheon(s) ? 1 : 0;
		}
		return n + (isTruncheon(player.containerMenu.getCarried()) ? 1 : 0);
	}

	// ---------------------------------------------------------------- the combo (every melee hit)

	/** A melee hit landed by the Moon Knight (combo slams, night healing). */
	public static void onMeleeHit(ServerPlayer attacker, LivingEntity target, float amount) {
		if (MoonKnightCombat.isAbilityHit() || !isTruncheon(attacker.getMainHandItem()) || !isOut(attacker)) {
			return;
		}
		float power = MoonKnightAbilities.power(attacker);
		long now = attacker.level().getGameTime();
		long[] c = COMBO.computeIfAbsent(attacker.getUUID(), k -> new long[]{0L, Long.MIN_VALUE / 2});
		c[0] = now - c[1] <= MoonKnightConfig.TRUNCHEON_COMBO_WINDOW ? c[0] + 1 : 1;
		c[1] = now;
		ServerLevel level = attacker.serverLevel();
		if (target instanceof Mob && MoonKnightAbilities.night(attacker)) {
			attacker.heal(MoonKnightConfig.TRUNCHEON_NIGHT_HEAL * power);
			level.sendParticles(MoonKnightCombat.PALE_BLUE, attacker.getX(), attacker.getY() + 1.2, attacker.getZ(),
					3, 0.3, 0.3, 0.3, 0.0);
		}
		if (c[0] < MoonKnightConfig.TRUNCHEON_COMBO_HITS) {
			return;
		}
		c[0] = 0;
		MoonKnightCombat.hit(attacker, target, MoonKnightConfig.TRUNCHEON_SLAM_BONUS * power);
		MoonKnightCombat.knock(target, attacker.position(), MoonKnightConfig.TRUNCHEON_SLAM_KNOCKBACK * power, 0.25);
		MoonKnightAnim.play(attacker, MoonKnightAnim.TRUNCHEON_SLAM);
		Vec3 at = target.position().add(0, target.getBbHeight() * 0.5, 0);
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.3);
		level.sendParticles(MoonKnightCombat.MOON, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0f, 0.7f);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 0.7f, 1.3f);
	}

	// ---------------------------------------------------------------- HOLD: staff spin

	@Override
	public void holdStart(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "truncheon_hold")) {
			return;
		}
		if (!isOut(player) && !summon(player)) {
			return;
		}
		staffSpin(player);
	}

	/** The 360° spin; returns how many it hit. Public for the gametests. */
	public static int staffSpin(ServerPlayer player) {
		float power = MoonKnightAbilities.power(player);
		double radius = MoonKnightConfig.STAFF_SPIN_RADIUS * power;
		Vec3 c = player.position().add(0, player.getBbHeight() * 0.5, 0);
		int hits = 0;
		for (LivingEntity e : MoonKnightCombat.enemies(player, c, radius)) {
			if (MoonKnightCombat.hit(player, e, MoonKnightConfig.STAFF_SPIN_DAMAGE * power)) {
				hits++;
			}
			MoonKnightCombat.knock(e, player.position(), MoonKnightConfig.STAFF_SPIN_KNOCKBACK * power, 0.15);
		}
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_STAFF, true);
		STAFF_UNTIL.put(player.getUUID(), player.level().getGameTime() + MoonKnightConfig.STAFF_SPIN_TICKS);
		MoonKnightAnim.play(player, MoonKnightAnim.STAFF_SPIN);
		MoonKnightAbilities.cooldown(player, "truncheon_hold", MoonKnightConfig.STAFF_SPIN_COOLDOWN);
		ServerLevel level = player.serverLevel();
		for (int i = 0; i < 12; i++) {
			double a = Math.PI * 2 * i / 12.0;
			double r = Math.min(radius, 2.5);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
			level.sendParticles(MoonKnightCombat.MOON, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius,
					2, 0.05, 0.1, 0.05, 0.0);
		}
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
		AbilityHelpers.sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
		AbilityHelpers.sound(player, SoundEvents.ARMOR_EQUIP_CHAIN, 0.8f, 1.4f);
		return hits;
	}

	// ---------------------------------------------------------------- SNEAK: ground slam / dive slam

	@Override
	public void sneak(ServerPlayer player) {
		if (!MoonKnightAbilities.ready(player, "truncheon_sneak")) {
			return;
		}
		if (player.onGround() || player.isInWater()) {
			groundSlam(player);
		} else {
			startDive(player);
		}
	}

	/** The on-the-ground shockwave. Returns how many it hit. */
	public static int groundSlam(ServerPlayer player) {
		float power = MoonKnightAbilities.power(player);
		int hits = slam(player, MoonKnightConfig.GROUND_SLAM_DAMAGE * power, MoonKnightConfig.GROUND_SLAM_RADIUS * power);
		MoonKnightAnim.play(player, MoonKnightAnim.GROUND_SLAM);
		MoonKnightAbilities.cooldown(player, "truncheon_sneak", MoonKnightConfig.SLAM_COOLDOWN);
		return hits;
	}

	private static int slam(ServerPlayer player, float damage, double radius) {
		ServerLevel level = player.serverLevel();
		Vec3 feet = player.position();
		int hits = 0;
		for (LivingEntity e : MoonKnightCombat.enemies(player, feet.add(0, 0.5, 0), radius)) {
			if (MoonKnightCombat.hit(player, e, damage)) {
				hits++;
			}
			if (!com.projecthero.mod.titanshifter.TitanCombat.isBoss(e)) {
				AbilityHelpers.knockbackFrom(e, feet, MoonKnightConfig.GROUND_SLAM_KNOCKBACK);
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(v.x, Math.max(v.y, 0.0) + MoonKnightConfig.GROUND_SLAM_LAUNCH, v.z);
				e.hurtMarked = true;
				e.hasImpulse = true;
			}
		}
		BlockState floor = level.getBlockState(BlockPos.containing(feet).below());
		for (int i = 0; i < 20; i++) {
			double a = Math.PI * 2 * i / 20.0;
			double x = feet.x + Math.cos(a) * radius * 0.8;
			double z = feet.z + Math.sin(a) * radius * 0.8;
			level.sendParticles(MoonKnightCombat.MOON, x, feet.y + 0.2, z, 2, 0.1, 0.05, 0.1, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, x, feet.y + 0.1, z, 1, 0.1, 0.02, 0.1, 0.02);
			if (!floor.isAir()) {
				level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, floor), x, feet.y + 0.1, z, 3, 0.2, 0.05, 0.2, 0.1);
			}
		}
		level.sendParticles(ParticleTypes.END_ROD, feet.x, feet.y + 0.3, feet.z, 20, radius * 0.3, 0.1, radius * 0.3, 0.08);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.0f, 0.9f);
		level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.6f);
		return hits;
	}

	private static void startDive(ServerPlayer player) {
		MoonKnightCape.stopGlide(player);
		DIVES.put(player.getUUID(), new Dive(player.getY(), player.level().getGameTime()));
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_DIVING, true);
		MoonKnightAnim.play(player, MoonKnightAnim.DIVE_SLAM);
		AbilityHelpers.launchSelf(player, new Vec3(0.0, -MoonKnightConfig.DIVE_SLAM_SPEED, 0.0));
		AbilityHelpers.sound(player, SoundEvents.PHANTOM_FLAP, 0.8f, 0.7f);
	}

	private static void tickDive(ServerPlayer player) {
		Dive d = DIVES.get(player.getUUID());
		if (d == null) {
			return;
		}
		long age = player.level().getGameTime() - d.start();
		if (player.onGround() || player.isInWater() || player.isInLava()) {
			endDive(player);
			float power = MoonKnightAbilities.power(player);
			double height = Math.max(0.0, d.startY() - player.getY());
			float damage = (float) Math.min(MoonKnightConfig.DIVE_SLAM_MAX_DAMAGE,
					MoonKnightConfig.GROUND_SLAM_DAMAGE * 0.5f + height * MoonKnightConfig.DIVE_SLAM_DAMAGE_PER_BLOCK) * power;
			slam(player, damage, MoonKnightConfig.GROUND_SLAM_RADIUS * power);
			MoonKnightAnim.play(player, MoonKnightAnim.GROUND_SLAM);
			MoonKnightAbilities.cooldown(player, "truncheon_sneak", MoonKnightConfig.SLAM_COOLDOWN);
			return;
		}
		if (age > MoonKnightConfig.DIVE_SLAM_MAX_TICKS) {
			endDive(player);
			MoonKnightAbilities.cooldown(player, "truncheon_sneak", MoonKnightConfig.SLAM_COOLDOWN);
			return;
		}
		player.resetFallDistance();
		AbilityHelpers.launchSelf(player, new Vec3(0.0, -MoonKnightConfig.DIVE_SLAM_SPEED, 0.0));
		if (age % 2 == 0) {
			player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.6, player.getZ(),
					2, 0.2, 0.3, 0.2, 0.0);
		}
	}

	private static void endDive(ServerPlayer player) {
		if (DIVES.remove(player.getUUID()) == null) {
			return;
		}
		FALL_GRACE.put(player.getUUID(), player.level().getGameTime() + 20L);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_DIVING, false);
		MoonKnightAnim.stop(player, MoonKnightAnim.DIVE_SLAM);
		player.resetFallDistance();
	}

	/** A dive in progress (or just landed) never takes fall damage. */
	public static boolean protectsFromFall(ServerPlayer player) {
		if (DIVES.containsKey(player.getUUID())) {
			return true;
		}
		Long until = FALL_GRACE.get(player.getUUID());
		return until != null && player.level().getGameTime() <= until;
	}

	// ---------------------------------------------------------------- upkeep

	@Override
	public void tick(ServerPlayer player) {
		tickDive(player);
		Long staffUntil = STAFF_UNTIL.get(player.getUUID());
		if (staffUntil != null && player.level().getGameTime() >= staffUntil) {
			STAFF_UNTIL.remove(player.getUUID());
			MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_STAFF, false);
		}
		if (isOut(player)) {
			sweepContainer(player); // it can't be stored
			int n = count(player);
			if (n == 0) {
				stow(player, false); // dropped or otherwise lost: it's simply gone
			} else if (n > 1) {
				keepOne(player);
			}
		}
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		endDive(player);
		stow(player, false);
		DISPLACED.remove(player.getUUID());
	}

	public static void clearSessionState() {
		DISPLACED.clear();
		COMBO.clear();
		STAFF_UNTIL.clear();
		DIVES.clear();
		FALL_GRACE.clear();
	}
}
