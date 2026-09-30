package com.projecthero.mod.moonknight.ability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.hero.power.PowerToggles;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightAlter;
import com.projecthero.mod.moonknight.MoonKnightAnim;
import com.projecthero.mod.moonknight.MoonKnightConfig;
import com.projecthero.mod.moonknight.MoonKnightLunar;
import com.projecthero.mod.moonknight.data.MoonKnightAction;
import com.projecthero.mod.moonknight.data.MoonKnightState;
import com.projecthero.mod.network.MoonKnightScholarSightPayload;
import com.projecthero.mod.titanshifter.TitanCombat;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * V -- the Alters (Moon Knight Phase 5; on C before v0.13.21): Marc Spector, Steven Grant and Jake Lockley share the
 * body, each with his own passives, his own suit and his own SNEAK+V special.
 * <ul>
 *   <li><b>TAP</b> -- cycle Marc -> Steven -> Jake -> Marc (1.4 s cooldown {@code alter}); while suited the new alter's suit
 *       rematerialises over the old one pixel by pixel ({@code MoonKnightAnim.markSwap}); refused during a Fracture.</li>
 *   <li><b>HOLD</b> -- the client opens the radial picker ({@code MoonKnightAlterPicker}) and sends
 *       {@code SELECT_ALTER} on release, which lands in {@link #select}; the server's hold hooks only keep
 *       {@code FLAG_ALTER_PICKER} in step so other clients could show it.</li>
 *   <li><b>SNEAK+V</b> ({@code alter_sneak}, 20 s): Marc "Fist of Khonshu" (Strength II + knockback resistance, 15
 *       Vengeance), Steven "Scholar's Sight" (chests / ores / spawners outlined through walls, this player only),
 *       Jake "Vanish" (Invisibility, every mob hunting him loses the scent).</li>
 * </ul>
 * Passives (only while transformed and in that alter): Marc +4 armour, +20% melee, knockback resistance; Steven -15%
 * melee taken, an extra loot roll on half his kills, cheaper villager trades; Jake faster sneaking, mobs notice him at
 * half the range, +50% melee from behind. Durations scale with the lunar power, cooldowns divide by it.
 */
public final class MoonKnightAlters implements MoonKnightMove {
	public static final MoonKnightAlters INSTANCE = new MoonKnightAlters();

	private static final String TAP = "alter";
	private static final String SNEAK = "alter_sneak";

	private static final ResourceLocation MARC_ARMOR = PowerToggles.id("moon_knight_marc_armor");
	private static final ResourceLocation MARC_KNOCKBACK = PowerToggles.id("moon_knight_marc_knockback");
	private static final ResourceLocation FIST_KNOCKBACK = PowerToggles.id("moon_knight_fist_knockback");
	private static final ResourceLocation JAKE_SNEAK = PowerToggles.id("moon_knight_jake_sneak");
	/** v0.13.21: the suit's own +7 melee, whoever is in control. */
	public static final ResourceLocation SUIT_STRENGTH = PowerToggles.id("moon_knight_suit_strength");
	private static final ResourceLocation SUIT_SPEED = PowerToggles.id("moon_knight_suit_speed");
	private static final ResourceLocation SUIT_JUMP = PowerToggles.id("moon_knight_suit_jump");
	private static final ResourceLocation SUIT_SAFE_FALL = PowerToggles.id("moon_knight_suit_safe_fall");
	/** v0.14.4: 1-block step assist while suited (fixed id, transient: re-adding is free and it never saves). */
	public static final ResourceLocation SUIT_STEP = PowerToggles.id("moon_knight_suit_step");

	private static final DustParticleOptions MOONDUST = new DustParticleOptions(new org.joml.Vector3f(0.93f, 0.95f, 1.0f), 1.0f);
	private static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.82f, 0.35f), 0.9f);
	private static final DustParticleOptions SHADOW = new DustParticleOptions(new org.joml.Vector3f(0.08f, 0.08f, 0.1f), 1.4f);

	/**
	 * Per player (UUID): game time Fist of Khonshu ends. Server-only; cleared on untransform + server stop. (Vanish needs
	 * no map since v0.14.4: it is a toggle, and its state is the infinite Invisibility effect itself -- see {@link #vanish}.)
	 */
	private static final Map<UUID, Long> FIST_UNTIL = new ConcurrentHashMap<>();

	private MoonKnightAlters() {
	}

	/** Registration (from {@code ProjectHeroMod}): Steven's loot hook, the Scholar's Sight payload, disconnect cleanup. */
	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(MoonKnightScholarSightPayload.TYPE, MoonKnightScholarSightPayload.CODEC);
		ServerLivingEntityEvents.AFTER_DEATH.register(MoonKnightAlters::onEntityKilled);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.getPlayer().getUUID()));
	}

	public static void clearSessionState() {
		FIST_UNTIL.clear();
	}

	private static void forget(UUID id) {
		FIST_UNTIL.remove(id);
	}

	// ================================================================ TAP / HOLD: switching

	@Override
	public void tap(ServerPlayer player) {
		if (blockedByFracture(player) || !MoonKnightAbilities.ready(player, TAP)) {
			return;
		}
		switchTo(player, MoonKnight.alter(player).next());
	}

	@Override
	public void holdStart(ServerPlayer player) {
		// the client draws the picker itself; this flag only tells everyone else it is open
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_ALTER_PICKER, true);
	}

	@Override
	public void holdRelease(ServerPlayer player, int ticksHeld) {
		// the choice arrives separately as MoonKnightActionPayload(SELECT_ALTER) -> select()
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_ALTER_PICKER, false);
	}

	@Override
	public void cancelHold(ServerPlayer player) {
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_ALTER_PICKER, false);
	}

	/** The radial picker chose an alter (client payload; transformed is already checked by the receiver). */
	public static void select(ServerPlayer player, int alterOrdinal) {
		if (!MoonKnight.isTransformed(player) || alterOrdinal < 0 || alterOrdinal >= MoonKnightAlter.values().length) {
			return;
		}
		MoonKnightAlter target = MoonKnightAlter.byOrdinal(alterOrdinal);
		if (target == MoonKnight.alter(player)) {
			return; // picking who is already in control changes nothing and costs nothing
		}
		if (blockedByFracture(player) || !MoonKnightAbilities.ready(player, TAP)) {
			return;
		}
		switchTo(player, target);
	}

	private static boolean blockedByFracture(ServerPlayer player) {
		if (MoonKnight.fractured(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.alter_fractured")
					.withStyle(ChatFormatting.RED, ChatFormatting.ITALIC), true);
			return true;
		}
		return false;
	}

	private static void switchTo(ServerPlayer player, MoonKnightAlter target) {
		MoonKnightState c = MoonKnight.state(player).copy();
		int from = c.alter;
		c.alter = target.ordinal();
		MoonKnight.saveState(player, c);
		MoonKnightAnim.markSwap(player, from); // v0.13.21: the new suit rematerialises over the old one
		MoonKnightAbilities.cooldown(player, TAP, MoonKnightConfig.ALTER_SWITCH_COOLDOWN);
		reconcile(player);
		MoonKnightAnim.play(player, MoonKnightAnim.ALTER_SWAP);
		// a swirl of moonlight (and the new alter's colour) spiralling up the body
		ServerLevel level = player.serverLevel();
		DustParticleOptions tint = switch (target) {
			case STEVEN -> GOLD_DUST;
			case JAKE -> SHADOW;
			default -> MOONDUST;
		};
		for (int i = 0; i < 36; i++) {
			double t = i / 36.0;
			double ang = t * Math.PI * 6.0;
			double r = 0.75 - t * 0.25;
			level.sendParticles(i % 2 == 0 ? MOONDUST : tint, player.getX() + Math.cos(ang) * r, player.getY() + t * 2.1,
					player.getZ() + Math.sin(ang) * r, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 8, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
				SoundSource.PLAYERS, 0.7f, 1.2f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
				SoundSource.PLAYERS, 0.9f, 0.7f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.alter_switch",
				Component.translatable(target.nameKey()).withStyle(target.colour(), ChatFormatting.BOLD)), true);
	}

	// ================================================================ SNEAK+V: the alter's special

	@Override
	public void sneak(ServerPlayer player) {
		// v0.14.4: Vanish is a toggle -- Sneak+V again steps back out (always allowed), and the cooldown starts then
		if (MoonKnight.alter(player) == MoonKnightAlter.JAKE && inVanish(player)) {
			endVanish(player, true);
			MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.ALTER_SPECIAL_COOLDOWN);
			return;
		}
		if (!MoonKnightAbilities.ready(player, SNEAK)) {
			return;
		}
		boolean used = switch (MoonKnight.alter(player)) {
			case MARC -> fistOfKhonshu(player);
			case STEVEN -> scholarsSight(player);
			case JAKE -> vanish(player);
		};
		if (used && MoonKnight.alter(player) != MoonKnightAlter.JAKE) {
			MoonKnightAbilities.cooldown(player, SNEAK, MoonKnightConfig.ALTER_SPECIAL_COOLDOWN);
		}
	}

	/** Marc: 10 s (x lunar power) of Strength II and knockback resistance, for 15 Vengeance. */
	public static boolean fistOfKhonshu(ServerPlayer player) {
		if (!MoonKnightAbilities.spendVengeance(player, MoonKnightConfig.FIST_OF_KHONSHU_COST)) {
			return false;
		}
		int ticks = Math.round(MoonKnightLunar.scale(MoonKnightConfig.FIST_OF_KHONSHU_TICKS, MoonKnightAbilities.power(player)));
		player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, MoonKnightConfig.FIST_STRENGTH_AMPLIFIER,
				false, true, true));
		FIST_UNTIL.put(player.getUUID(), player.level().getGameTime() + ticks);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_FIST, true);
		reconcile(player);
		MoonKnightAnim.play(player, MoonKnightAnim.FIST_OF_KHONSHU);
		ServerLevel level = player.serverLevel();
		Vec3 c = player.position().add(0.0, 1.1, 0.0);
		for (int i = 0; i < 24; i++) {
			double ang = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(ParticleTypes.END_ROD, c.x + Math.cos(ang) * 1.2, player.getY() + 0.1, c.z + Math.sin(ang) * 1.2,
					1, 0.0, 0.05, 0.0, 0.01);
		}
		level.sendParticles(MOONDUST, c.x, c.y, c.z, 40, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 0.6f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 0.8f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.fist")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD), true);
		return true;
	}

	/** Steven: 8 s (x lunar power) of chests, barrels, ores and spawners outlined through walls within 16 blocks. */
	public static boolean scholarsSight(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		List<long[]> found = scan(level, player.blockPosition(), MoonKnightConfig.SCHOLARS_SIGHT_RADIUS);
		found.sort(Comparator.comparingLong(e -> e[2]));
		int n = Math.min(found.size(), MoonKnightConfig.SCHOLARS_SIGHT_MAX_BLOCKS);
		long[] positions = new long[n];
		int[] colours = new int[n];
		for (int i = 0; i < n; i++) {
			positions[i] = found.get(i)[0];
			colours[i] = (int) found.get(i)[1];
		}
		int ticks = Math.round(MoonKnightLunar.scale(MoonKnightConfig.SCHOLARS_SIGHT_TICKS, MoonKnightAbilities.power(player)));
		MoonKnightKhonshu.send(player, new MoonKnightScholarSightPayload(positions, colours, ticks));
		MoonKnightAnim.play(player, MoonKnightAnim.SCHOLARS_SIGHT);
		Vec3 eye = player.getEyePosition();
		level.sendParticles(ParticleTypes.ENCHANT, eye.x, eye.y + 0.4, eye.z, 60, 0.6, 0.5, 0.6, 0.8);
		level.sendParticles(GOLD_DUST, eye.x, eye.y, eye.z, 16, 0.35, 0.25, 0.35, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0f, 0.9f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.8f, 1.3f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.scholars_sight", n)
				.withStyle(ChatFormatting.GOLD), true);
		return true;
	}

	/** Every block of interest in a cube around {@code centre}: {packedPos, rgb, distanceSq}. */
	public static List<long[]> scan(ServerLevel level, BlockPos centre, int radius) {
		List<long[]> out = new ArrayList<>();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		int minY = Math.max(level.getMinBuildHeight(), centre.getY() - radius);
		int maxY = Math.min(level.getMaxBuildHeight() - 1, centre.getY() + radius);
		for (int x = centre.getX() - radius; x <= centre.getX() + radius; x++) {
			for (int z = centre.getZ() - radius; z <= centre.getZ() + radius; z++) {
				for (int y = minY; y <= maxY; y++) {
					m.set(x, y, z);
					if (!level.isLoaded(m)) {
						continue;
					}
					int rgb = interestColour(level.getBlockState(m));
					if (rgb >= 0) {
						out.add(new long[] { m.asLong(), rgb, (long) centre.distSqr(m) });
					}
				}
			}
		}
		return out;
	}

	/** Outline colour for a block of interest, or -1 if it isn't one. */
	public static int interestColour(BlockState state) {
		if (state.isAir()) {
			return -1;
		}
		if (state.getBlock() instanceof ChestBlock || state.getBlock() instanceof BarrelBlock) {
			return 0xFFC24A; // treasure: gold
		}
		if (state.is(Blocks.SPAWNER) || state.is(Blocks.TRIAL_SPAWNER)) {
			return 0xFF4040; // danger: red
		}
		if (state.is(BlockTags.DIAMOND_ORES)) {
			return 0x5CF5F0;
		}
		if (state.is(BlockTags.EMERALD_ORES)) {
			return 0x3CE070;
		}
		if (state.is(BlockTags.GOLD_ORES)) {
			return 0xFFE040;
		}
		if (state.is(BlockTags.REDSTONE_ORES)) {
			return 0xE02828;
		}
		if (state.is(BlockTags.LAPIS_ORES)) {
			return 0x3050E0;
		}
		if (state.is(BlockTags.IRON_ORES)) {
			return 0xE0B090;
		}
		if (state.is(BlockTags.COPPER_ORES)) {
			return 0xE07840;
		}
		if (state.is(BlockTags.COAL_ORES)) {
			return 0x707070;
		}
		if (state.is(Blocks.ANCIENT_DEBRIS)) {
			return 0xA06850;
		}
		if (state.is(ConventionalBlockTags.ORES)) {
			return 0xE8E8F0; // any other (modded / nether quartz) ore
		}
		return -1;
	}

	/**
	 * Jake: Invisibility until he ends it (v0.14.4: a toggle, no time limit -- Sneak+V again, an alter switch, the suit
	 * coming off or death), and every mob hunting him loses the scent. While it lasts no mob can target him at all
	 * ({@link #isVanished}). The infinite effect IS the state, so it survives a relog.
	 */
	public static boolean vanish(ServerPlayer player) {
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
		int lost = dropAggro(player, MoonKnightConfig.VANISH_AGGRO_RADIUS);
		MoonKnightAnim.play(player, MoonKnightAnim.VANISH);
		ServerLevel level = player.serverLevel();
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.02);
		level.sendParticles(SHADOW, player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.6, 0.3, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 0.7f);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.8f, 0.6f);
		player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.vanish", lost)
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
		return true;
	}

	/** True while Jake's own Vanish is on (the infinite Invisibility it applies). */
	public static boolean inVanish(Player player) {
		MobEffectInstance inv = player.getEffect(MobEffects.INVISIBILITY);
		return inv != null && inv.isInfiniteDuration();
	}

	/** Ends Vanish (Sneak+V again, an alter switch, suit off). {@code announce}: the step-out effect and message. */
	public static void endVanish(ServerPlayer player, boolean announce) {
		if (!inVanish(player)) {
			return;
		}
		player.removeEffect(MobEffects.INVISIBILITY);
		if (announce) {
			ServerLevel level = player.serverLevel();
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.3, 0.7, 0.3, 0.02);
			level.sendParticles(SHADOW, player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.4, 0.8, 0.4, 0.0);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE,
					SoundSource.PLAYERS, 0.7f, 0.6f);
			player.displayClientMessage(Component.translatable("message.projecthero.moon_knight.vanish_end")
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC), true);
		}
	}

	/** Every mob within {@code radius} that is targeting {@code player} forgets him. Returns how many did. */
	public static int dropAggro(ServerPlayer player, double radius) {
		AABB box = player.getBoundingBox().inflate(radius);
		int count = 0;
		for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, box, m -> m.isAlive())) {
			// hasMemoryValue first: getMemory throws for a memory the mob's brain never registered
			boolean hunting = mob.getTarget() == player
					|| (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
							&& mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == player);
			if (!hunting) {
				continue;
			}
			mob.setTarget(null);
			if (mob.getLastHurtByMob() == player) {
				mob.setLastHurtByMob(null);
			}
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
			if (mob instanceof NeutralMob neutral) {
				neutral.stopBeingAngry();
			}
			mob.getNavigation().stop();
			count++;
		}
		return count;
	}

	// ================================================================ upkeep

	@Override
	public void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Long fist = FIST_UNTIL.get(player.getUUID());
		if (fist != null) {
			if (now >= fist) {
				FIST_UNTIL.remove(player.getUUID());
				MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_FIST, false);
				reconcile(player);
			} else if (now % 5L == 0L) {
				// the fists glow with moonlight while it lasts
				Vec3 hand = AbilityHelpers.handPosition(player);
				player.serverLevel().sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 1, 0.08, 0.08, 0.08, 0.005);
			}
		}
		// v0.14.4: while Jake is invisible nothing may hunt him. Mob#setTarget refuses him (MoonKnightVanishTargetMixin);
		// this sweep also clears brain-driven mobs (piglins, hoglins, ...) that keep their target in a memory instead.
		if (now % 5L == 0L && isVanished(player)) {
			dropAggro(player, MoonKnightConfig.VANISH_AGGRO_RADIUS);
		}
	}

	/**
	 * v0.14.4: Jake Lockley, suited and invisible (his Vanish, or any Invisibility effect) -- mobs cannot target him at
	 * all, even when he strikes them. Checked by {@code MoonKnightVanishTargetMixin} on every {@code Mob#setTarget}.
	 */
	public static boolean isVanished(Player player) {
		return player.hasEffect(MobEffects.INVISIBILITY) && MoonKnight.isTransformed(player)
				&& MoonKnight.alter(player) == MoonKnightAlter.JAKE;
	}

	@Override
	public void onUntransform(ServerPlayer player) {
		if (FIST_UNTIL.remove(player.getUUID()) != null) {
			player.removeEffect(MobEffects.DAMAGE_BOOST);
		}
		endVanish(player, false);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_FIST, false);
		MoonKnightAnim.setFlag(player, MoonKnightAction.FLAG_ALTER_PICKER, false);
		PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, FIST_KNOCKBACK);
	}

	/**
	 * Keep the suit's and the alter's attribute passives in step (once a second while transformed, on every switch,
	 * and on suit up / down). Fixed-id transient modifiers, so re-applying is free and nothing survives a relog.
	 */
	public static void reconcile(ServerPlayer player) {
		boolean on = MoonKnight.isTransformed(player);
		MoonKnightAlter alter = MoonKnight.alter(player);
		// v0.14.4: Vanish is Jake's -- switching alter or taking the suit off ends it
		if ((!on || alter != MoonKnightAlter.JAKE) && inVanish(player)) {
			endVanish(player, on);
		}
		// v0.13.21: the suit itself hits harder (+7 melee), for as long as it is on
		if (on) {
			PowerToggles.modifier(player, Attributes.ATTACK_DAMAGE, SUIT_STRENGTH, MoonKnightConfig.SUIT_MELEE_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
			// v0.14.3: faster, and a jump that clears two blocks
			PowerToggles.modifier(player, Attributes.MOVEMENT_SPEED, SUIT_SPEED, MoonKnightConfig.SUIT_SPEED_BONUS,
					AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			PowerToggles.modifier(player, Attributes.JUMP_STRENGTH, SUIT_JUMP, MoonKnightConfig.SUIT_JUMP_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.SAFE_FALL_DISTANCE, SUIT_SAFE_FALL, MoonKnightConfig.SUIT_SAFE_FALL_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
			// v0.14.4: walks straight up full blocks (step height 0.6 -> 1.0)
			PowerToggles.modifier(player, Attributes.STEP_HEIGHT, SUIT_STEP, MoonKnightConfig.SUIT_STEP_HEIGHT_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ATTACK_DAMAGE, SUIT_STRENGTH);
			PowerToggles.clearModifier(player, Attributes.MOVEMENT_SPEED, SUIT_SPEED);
			PowerToggles.clearModifier(player, Attributes.JUMP_STRENGTH, SUIT_JUMP);
			PowerToggles.clearModifier(player, Attributes.SAFE_FALL_DISTANCE, SUIT_SAFE_FALL);
			PowerToggles.clearModifier(player, Attributes.STEP_HEIGHT, SUIT_STEP);
		}
		boolean marc = on && alter == MoonKnightAlter.MARC;
		boolean jake = on && alter == MoonKnightAlter.JAKE;
		Long fist = FIST_UNTIL.get(player.getUUID());
		boolean fistOn = on && fist != null && fist > player.level().getGameTime();
		if (marc) {
			PowerToggles.modifier(player, Attributes.ARMOR, MARC_ARMOR, MoonKnightConfig.MARC_ARMOR, AttributeModifier.Operation.ADD_VALUE);
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, MARC_KNOCKBACK,
					MoonKnightConfig.MARC_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.ARMOR, MARC_ARMOR);
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, MARC_KNOCKBACK);
		}
		if (fistOn) {
			PowerToggles.modifier(player, Attributes.KNOCKBACK_RESISTANCE, FIST_KNOCKBACK,
					MoonKnightConfig.FIST_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.KNOCKBACK_RESISTANCE, FIST_KNOCKBACK);
		}
		if (jake) {
			PowerToggles.modifier(player, Attributes.SNEAKING_SPEED, JAKE_SNEAK, MoonKnightConfig.JAKE_SNEAK_SPEED_BONUS,
					AttributeModifier.Operation.ADD_VALUE);
		} else {
			PowerToggles.clearModifier(player, Attributes.SNEAKING_SPEED, JAKE_SNEAK);
		}
	}

	// ================================================================ damage passives (from MoonKnightDamage)

	/** A plain hand-to-hand hit: a living attacker striking directly, not a projectile / explosion / magic. */
	private static boolean isMelee(DamageSource source) {
		Entity direct = source.getDirectEntity();
		return direct instanceof LivingEntity && direct == source.getEntity()
				&& !source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION)
				&& !source.is(DamageTypeTags.WITCH_RESISTANT_TO);
	}

	/** Incoming-damage multiplier from the current alter (Steven: -15% melee). */
	public static float incomingFactor(ServerPlayer player, DamageSource source) {
		if (MoonKnight.alter(player) == MoonKnightAlter.STEVEN && isMelee(source)) {
			return MoonKnightConfig.STEVEN_MELEE_TAKEN;
		}
		return 1.0f;
	}

	/** Outgoing-damage multiplier from the current alter (Marc +20% melee, Jake +50% melee from behind a mob). */
	public static float outgoingFactor(ServerPlayer attacker, LivingEntity target, DamageSource source) {
		if (!source.is(DamageTypes.PLAYER_ATTACK) || source.getDirectEntity() != attacker) {
			return 1.0f;
		}
		return switch (MoonKnight.alter(attacker)) {
			case MARC -> 1.0f + (float) MoonKnightConfig.MARC_MELEE_BONUS;
			case JAKE -> target instanceof Mob && fromBehind(attacker, target) ? 1.0f + MoonKnightConfig.JAKE_BACKSTAB_BONUS : 1.0f;
			default -> 1.0f;
		};
	}

	/** Is {@code attacker} within the backstab arc behind {@code target}'s body? */
	public static boolean fromBehind(Entity attacker, LivingEntity target) {
		double yaw = Math.toRadians(target.yBodyRot);
		double fx = -Math.sin(yaw);
		double fz = Math.cos(yaw);
		double dx = attacker.getX() - target.getX();
		double dz = attacker.getZ() - target.getZ();
		double len = Math.sqrt(dx * dx + dz * dz);
		if (len < 1.0e-4) {
			return false;
		}
		double dot = (fx * dx + fz * dz) / len; // 1 = in front of its face, -1 = straight behind
		return dot <= -Math.cos(Math.toRadians(MoonKnightConfig.JAKE_BACKSTAB_ARC_DEGREES));
	}

	/**
	 * Jake: how much of the normal detection range a mob uses against this player (from the {@code getVisibilityPercent}
	 * mixin). 1 for anyone else.
	 */
	public static double detectionFactor(Player player) {
		if (!MoonKnight.isTransformed(player) || MoonKnight.alter(player) != MoonKnightAlter.JAKE) {
			return 1.0;
		}
		if (player.hasEffect(MobEffects.INVISIBILITY)) {
			return 0.0; // v0.14.4: invisible Jake cannot be noticed at all (was VANISH_DETECTION_FACTOR)
		}
		return MoonKnightConfig.JAKE_DETECTION_FACTOR;
	}

	// ================================================================ Steven: loot + trades

	/** AFTER_DEATH: a mob slain by a transformed Steven has a chance of one extra roll of its loot table. */
	private static void onEntityKilled(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !(victim instanceof Mob mob)
				|| !MoonKnight.isTransformed(killer) || MoonKnight.alter(killer) != MoonKnightAlter.STEVEN) {
			return;
		}
		if (TitanCombat.isBoss(mob) || killer.getRandom().nextFloat() >= MoonKnightConfig.STEVEN_EXTRA_LOOT_CHANCE) {
			return;
		}
		extraLootRoll(mob, source, killer);
	}

	/** One more roll of {@code mob}'s own loot table, exactly as vanilla builds it on death (killed by a player). */
	public static void extraLootRoll(Mob mob, DamageSource source, ServerPlayer killer) {
		if (!(mob.level() instanceof ServerLevel level) || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBLOOT)
				|| level.getServer() == null) {
			return;
		}
		LootTable table = level.getServer().reloadableRegistries().getLootTable(mob.getLootTable());
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.THIS_ENTITY, mob)
				.withParameter(LootContextParams.ORIGIN, mob.position())
				.withParameter(LootContextParams.DAMAGE_SOURCE, source)
				.withOptionalParameter(LootContextParams.ATTACKING_ENTITY, source.getEntity())
				.withOptionalParameter(LootContextParams.DIRECT_ATTACKING_ENTITY, source.getDirectEntity())
				.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
				.withLuck(killer.getLuck())
				.create(LootContextParamSets.ENTITY);
		table.getRandomItems(params, 0L, mob::spawnAtLocation);
		level.sendParticles(GOLD_DUST, mob.getX(), mob.getY() + 0.5, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.0);
	}

	/**
	 * Villager trading ({@code Villager#updateSpecialPrices} mixin): a transformed Steven haggles every price down by
	 * {@link MoonKnightConfig#STEVEN_TRADE_DISCOUNT} of its base cost (at least 1), the Hero-of-the-Village math.
	 * Vanilla resets the special prices when the trade screen closes, so it never sticks.
	 */
	public static void applyTradeDiscount(Villager villager, Player player) {
		if (!MoonKnight.isTransformed(player) || MoonKnight.alter(player) != MoonKnightAlter.STEVEN) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int cut = (int) Math.floor(MoonKnightConfig.STEVEN_TRADE_DISCOUNT * offer.getBaseCostA().getCount());
			offer.addToSpecialPriceDiff(-Math.max(cut, 1));
		}
	}

	/** v0.14.4: the Fortune level Steven's mining counts as having (0 for anyone else). Public for the gametests. */
	public static int stevenFortuneLevel(Entity miner) {
		return miner instanceof Player p && MoonKnight.isTransformed(p) && MoonKnight.alter(p) == MoonKnightAlter.STEVEN
				? MoonKnightConfig.STEVEN_FORTUNE_LEVEL : 0;
	}

	/**
	 * v0.14.4 (from {@code mixin/MoonKnightStevenFortuneMixin}, on {@code Block#getDrops} with a miner and a tool):
	 * blocks a transformed Steven breaks drop as if his tool had Fortune {@link MoonKnightConfig#STEVEN_FORTUNE_LEVEL}.
	 * The loot tables read Fortune off the TOOL loot parameter, so this hands them a copy of his tool with Fortune
	 * raised to at least that level -- a real Fortune tool keeps the higher of the two (never stacked), and a bare hand
	 * becomes a plain stick carrying it (a stick changes nothing else a block's loot looks at; whether the block drops
	 * at all was already decided from the real tool). Anyone else's tool comes back untouched.
	 */
	public static ItemStack fortuneTool(Entity miner, ItemStack tool, ServerLevel level) {
		int fortune = stevenFortuneLevel(miner);
		if (fortune <= 0 || level == null) {
			return tool;
		}
		var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(Enchantments.FORTUNE);
		if (holder.isEmpty()) {
			return tool;
		}
		ItemStack copy = tool == null || tool.isEmpty() ? new ItemStack(Items.STICK) : tool.copy();
		if (EnchantmentHelper.getItemEnchantmentLevel(holder.get(), copy) >= fortune) {
			return tool;
		}
		copy.enchant(holder.get(), fortune); // upgrade: the higher level wins
		return copy;
	}
}
