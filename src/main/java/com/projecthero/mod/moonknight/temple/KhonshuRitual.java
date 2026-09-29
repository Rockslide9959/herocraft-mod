package com.projecthero.mod.moonknight.temple;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import com.projecthero.mod.grave.GraveboundCurse;
import com.projecthero.mod.moonknight.MoonKnight;
import com.projecthero.mod.moonknight.MoonKnightLunar;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * The pact ritual on the {@link KhonshuAltarBlock Altar of Khonshu}. Server-authoritative from start to finish; the
 * only thing a client does is draw the scarab on the altar and the white fade ({@link MoonKnightRitualFadePayload}).
 *
 * <ol>
 *   <li><b>Lay the scarab</b> ({@link #placeScarab}): night ({@link MoonKnightLunar#isMoonNight}), open sky above the
 *       altar, an altar that is neither busy nor spent, a player without the pact. The scarab is consumed into the
 *       altar.</li>
 *   <li><b>Kneel</b> ({@link #tick}): the player has {@value #WAIT_TICKS} ticks to climb onto the altar and sneak.
 *       Then {@value #KNEEL_TICKS} ticks of unbroken kneeling, with a moonbeam, a rising tone and Khonshu speaking.
 *       Standing up, stepping off, the dawn, the sky being covered, dying or logging out cancels and the scarab pops
 *       back out.</li>
 *   <li><b>Death and rebirth</b>: a white flash, the screen fades to white, and for {@value #REBIRTH_TICKS} ticks the
 *       player is held invisible, motionless and unhurtable on the altar -- no real death, no drops, no death screen.
 *       Then they rise with the pact ({@link MoonKnight#grant(ServerPlayer, boolean)}, full moon = 100 Vengeance), the
 *       {@code moon_knight/pact} advancement, and the altar cracks: {@link KhonshuAltarBlockEntity.AltarState#SPENT}
 *       forever.</li>
 * </ol>
 */
public final class KhonshuRitual {
	public static final int KNEEL_TICKS = 200;
	public static final int WAIT_TICKS = 600;
	public static final int REBIRTH_TICKS = 40;
	/** The white fade: in over ~8 ticks, held through the rebirth, out over ~24. */
	public static final int FADE_TICKS = REBIRTH_TICKS + 30;
	private static final double WANDER_LIMIT_SQ = 24.0 * 24.0;
	private static final int BAR_SEGMENTS = 20;
	/** When Khonshu speaks (kneel ticks) -> line 1..5. */
	private static final int[] LINE_AT = { 10, 50, 90, 130, 170 };

	private static final DustParticleOptions MOON_DUST = new DustParticleOptions(new Vector3f(0.92f, 0.95f, 1.0f), 1.6f);

	/** Players currently between the flash and the rebirth: they take no damage (see {@code KhonshuTemple}). */
	private static final Set<UUID> REBORN = ConcurrentHashMap.newKeySet();

	private KhonshuRitual() {
	}

	// ---------------------------------------------------------------- queries

	public static boolean isBeingReborn(ServerPlayer player) {
		return REBORN.contains(player.getUUID());
	}

	static boolean night(ServerLevel level, KhonshuAltarBlockEntity be) {
		return be.forcedNight != null ? be.forcedNight : MoonKnightLunar.isMoonNight(level);
	}

	static boolean sky(ServerLevel level, BlockPos altar, KhonshuAltarBlockEntity be) {
		return be.forcedSky != null ? be.forcedSky : MoonKnightLunar.hasSky(level, altar.above());
	}

	/** Feet on the altar's top face (a little slack at the edges, where a sneaking player can balance). */
	public static boolean isOnAltar(ServerPlayer player, BlockPos altar) {
		double dx = player.getX() - (altar.getX() + 0.5);
		double dz = player.getZ() - (altar.getZ() + 0.5);
		double dy = player.getY() - (altar.getY() + 1.0);
		return Math.abs(dx) <= 0.8 && Math.abs(dz) <= 0.8 && dy >= -0.1 && dy <= 0.6;
	}

	// ---------------------------------------------------------------- step 1: the scarab

	/**
	 * The player right-clicked the altar with the scarab. Returns true if the scarab was laid (and consumed) and the
	 * ritual begun; otherwise tells the player why not and leaves the stack alone.
	 */
	public static boolean placeScarab(ServerPlayer player, ServerLevel level, BlockPos pos, ItemStack stack) {
		if (!(level.getBlockEntity(pos) instanceof KhonshuAltarBlockEntity be) || !stack.is(KhonshuTemple.SCARAB_OF_KHONSHU)) {
			return false;
		}
		String refusal = null;
		if (be.state == KhonshuAltarBlockEntity.AltarState.SPENT) {
			refusal = "spent";
		} else if (be.state == KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB) {
			refusal = "busy";
		} else if (MoonKnight.hasPower(player)) {
			refusal = "already";
		} else if (!night(level, be)) {
			refusal = "day";
		} else if (!sky(level, pos, be)) {
			refusal = "no_sky";
		}
		if (refusal != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.khonshu.refuse." + refusal)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
			return false;
		}
		stack.shrink(1); // consumed into the altar, creative included -- the altar holds it now
		be.state = KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB;
		be.ritualPlayer = player.getUUID();
		be.progress = 0;
		be.waitTicks = 0;
		be.rebirthTicks = -1;
		be.restored = false;
		be.sync();
		Vec3 top = top(pos);
		level.playSound(null, pos, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, 1.0f, 0.7f);
		level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.6f, 0.6f);
		level.sendParticles(ParticleTypes.END_ROD, top.x, top.y + 0.3, top.z, 20, 0.2, 0.3, 0.2, 0.02);
		player.displayClientMessage(Component.translatable("message.projecthero.khonshu.ritual.placed")
				.withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC), false);
		return true;
	}

	// ---------------------------------------------------------------- step 2 + 3: the tick

	/** One server tick of a ritual in progress (from the block entity's ticker, or a GameTest directly). */
	public static void tick(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be) {
		if (be.state != KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB) {
			return;
		}
		ServerPlayer player = findPlayer(level, be.ritualPlayer);
		if (be.restored) {
			cancel(level, pos, be, player, "interrupted");
			return;
		}
		if (player == null || !player.isAlive() || player.isSpectator() || player.level() != level) {
			cancel(level, pos, be, player, player == null ? null : "left");
			return;
		}
		if (be.rebirthTicks >= 0) {
			tickRebirth(level, pos, be, player);
			return;
		}
		if (!night(level, be)) {
			cancel(level, pos, be, player, "dawn");
			return;
		}
		if (!sky(level, pos, be)) {
			cancel(level, pos, be, player, "no_sky");
			return;
		}
		boolean kneeling = isOnAltar(player, pos) && player.isShiftKeyDown();
		if (be.progress == 0) {
			if (!kneeling) {
				be.waitTicks++;
				if (be.waitTicks > WAIT_TICKS || player.distanceToSqr(top(pos)) > WANDER_LIMIT_SQ) {
					cancel(level, pos, be, player, "left");
				} else if (be.waitTicks % 20 == 1) {
					player.displayClientMessage(Component.translatable("message.projecthero.khonshu.ritual.wait")
							.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
				}
				moonbeam(level, pos, 0.25f);
				return;
			}
		} else if (!kneeling) {
			cancel(level, pos, be, player, "broke");
			return;
		}

		be.progress++;
		float f = be.progress / (float) KNEEL_TICKS;
		moonbeam(level, pos, 0.4f + 0.6f * f);
		if (be.progress % 20 == 1) {
			level.playSound(null, pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.8f + f, 0.5f + 1.5f * f);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 0.6f, 0.5f + 1.2f * f);
		}
		for (int i = 0; i < LINE_AT.length; i++) {
			if (be.progress == LINE_AT[i]) {
				speak(player, "message.projecthero.khonshu.ritual.line" + (i + 1));
				level.playSound(null, pos, SoundEvents.SOUL_ESCAPE.value(), SoundSource.BLOCKS, 1.5f, 0.5f);
			}
		}
		if (be.progress % 4 == 1 || be.progress >= KNEEL_TICKS) {
			player.displayClientMessage(Component.translatable("message.projecthero.khonshu.ritual.kneel", bar(f))
					.withStyle(ChatFormatting.WHITE), true);
		}
		if (be.progress % 40 == 1) {
			be.sync(); // the client renderer brightens the scarab with the progress
		}
		if (be.progress >= KNEEL_TICKS) {
			beginRebirth(level, pos, be, player);
		}
	}

	/** Ten seconds of kneeling done: the flash, the "death", the fade to white. */
	private static void beginRebirth(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be, ServerPlayer player) {
		be.rebirthTicks = 0;
		be.sync();
		REBORN.add(player.getUUID());
		Vec3 top = top(pos);
		level.sendParticles(ParticleTypes.FLASH, top.x, top.y + 1.0, top.z, 3, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, top.x, top.y + 1.0, top.z, 160, 0.6, 1.2, 0.6, 0.35);
		level.sendParticles(MOON_DUST, top.x, top.y + 1.0, top.z, 80, 1.5, 1.5, 1.5, 0.0);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5f, 0.5f);
		level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.6f, 1.6f);
		level.playSound(null, pos, SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 0.8f);
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, REBIRTH_TICKS + 10, 0, false, false, false));
		player.stopUsingItem();
		player.setShiftKeyDown(false);
		if (ServerPlayNetworking.canSend(player, MoonKnightRitualFadePayload.TYPE)) {
			ServerPlayNetworking.send(player, new MoonKnightRitualFadePayload(FADE_TICKS));
		}
		speak(player, "message.projecthero.khonshu.ritual.death");
	}

	private static void tickRebirth(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be, ServerPlayer player) {
		be.rebirthTicks++;
		// held in place, "lifeless", while the screen is white
		Vec3 top = top(pos);
		if (player.position().distanceToSqr(top) > 0.04) {
			player.teleportTo(top.x, top.y, top.z);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.hurtMarked = true;
		moonbeam(level, pos, 1.0f);
		if (be.rebirthTicks >= REBIRTH_TICKS) {
			rise(level, pos, be, player);
		}
	}

	/** The rebirth: the player stands again on the altar as the Fist of Khonshu, and the altar is spent. */
	private static void rise(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be, ServerPlayer player) {
		REBORN.remove(player.getUUID());
		player.removeEffect(MobEffects.INVISIBILITY);
		Vec3 top = top(pos);
		player.teleportTo(top.x, top.y, top.z);
		player.setHealth(player.getMaxHealth());
		player.clearFire();
		level.sendParticles(ParticleTypes.END_ROD, top.x, top.y + 1.0, top.z, 120, 0.5, 1.0, 0.5, 0.25);
		level.sendParticles(MOON_DUST, top.x, top.y + 1.0, top.z, 60, 1.2, 1.2, 1.2, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, top.x, top.y + 0.2, top.z, 40, 1.0, 0.1, 1.0, 0.05);
		level.playSound(null, pos, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 0.8f);
		boolean fullMoon = night(level, be) && level.getMoonPhase() == 0;
		spend(level, pos, be);
		speak(player, "message.projecthero.khonshu.ritual.rise");
		MoonKnight.grant(player, fullMoon);
		GraveboundCurse.award(player, "moon_knight/pact");
	}

	/** Crack the altar for good (public for the GameTests). */
	public static void spend(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be) {
		be.state = KhonshuAltarBlockEntity.AltarState.SPENT;
		be.ritualPlayer = null;
		be.progress = 0;
		be.waitTicks = 0;
		be.rebirthTicks = -1;
		be.restored = false;
		if (level.getBlockState(pos).is(KhonshuTemple.KHONSHU_ALTAR)) {
			level.setBlock(pos, level.getBlockState(pos).setValue(KhonshuAltarBlock.SPENT, true), Block.UPDATE_ALL);
		}
		be.sync();
		level.playSound(null, pos, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.0f, 0.6f);
		level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 20, 0.4, 0.1, 0.4, 0.02);
	}

	/**
	 * Abort: the scarab pops back out on top of the altar, which returns to waiting. {@code reason} names the message
	 * the player gets (null = no message, e.g. they are offline).
	 */
	public static void cancel(ServerLevel level, BlockPos pos, KhonshuAltarBlockEntity be, @Nullable ServerPlayer player,
			@Nullable String reason) {
		if (be.state != KhonshuAltarBlockEntity.AltarState.HOLDING_SCARAB) {
			return;
		}
		if (be.ritualPlayer != null) {
			REBORN.remove(be.ritualPlayer);
		}
		if (player != null && be.rebirthTicks >= 0) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
		be.state = KhonshuAltarBlockEntity.AltarState.DORMANT_READY;
		be.ritualPlayer = null;
		be.progress = 0;
		be.waitTicks = 0;
		be.rebirthTicks = -1;
		be.restored = false;
		be.sync();
		Vec3 top = top(pos);
		ItemEntity scarab = new ItemEntity(level, top.x, top.y + 0.25, top.z, new ItemStack(KhonshuTemple.SCARAB_OF_KHONSHU));
		scarab.setDeltaMovement(0.0, 0.2, 0.0);
		scarab.setDefaultPickUpDelay();
		level.addFreshEntity(scarab);
		level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.8f, 1.2f);
		level.sendParticles(ParticleTypes.SMOKE, top.x, top.y + 0.2, top.z, 12, 0.2, 0.1, 0.2, 0.01);
		if (player != null && reason != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.khonshu.cancel." + reason)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
		}
	}

	// ---------------------------------------------------------------- lifecycle

	/** Disconnect: drop the rebirth bookkeeping (the altar itself notices the player is gone on its next tick). */
	public static void onDisconnect(ServerPlayer player) {
		if (REBORN.remove(player.getUUID())) {
			player.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** {@code ServerStateReset}: nothing here may outlive a server. */
	public static void clearSessionState() {
		REBORN.clear();
	}

	// ---------------------------------------------------------------- helpers

	@Nullable
	private static ServerPlayer findPlayer(ServerLevel level, @Nullable UUID id) {
		return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
	}

	private static Vec3 top(BlockPos altar) {
		return new Vec3(altar.getX() + 0.5, altar.getY() + 1.0, altar.getZ() + 0.5);
	}

	private static void speak(ServerPlayer player, String key) {
		player.displayClientMessage(Component.translatable("message.projecthero.khonshu.speaks",
				Component.translatable(key).withStyle(ChatFormatting.WHITE, ChatFormatting.ITALIC))
				.withStyle(ChatFormatting.GRAY), false);
	}

	/** "▮▮▮▯▯▯" -- filled up to {@code f}. */
	static String bar(float f) {
		int filled = Math.max(0, Math.min(BAR_SEGMENTS, Math.round(f * BAR_SEGMENTS)));
		return "▮".repeat(filled) + "▯".repeat(BAR_SEGMENTS - filled);
	}

	/** A column of moonlight falling from the sky onto the altar; {@code strength} 0..1 thickens it. */
	private static void moonbeam(ServerLevel level, BlockPos pos, float strength) {
		if (level.getGameTime() % 2L != 0L) {
			return;
		}
		double x = pos.getX() + 0.5;
		double z = pos.getZ() + 0.5;
		double base = pos.getY() + 1.0;
		int count = Math.max(1, Math.round(2 * strength));
		for (int i = 0; i < 16; i++) {
			double y = base + i * 2.0 + level.random.nextDouble() * 2.0;
			level.sendParticles(ParticleTypes.END_ROD, x, y, z, count, 0.12, 0.6, 0.12, 0.0);
			if (i % 3 == 0) {
				level.sendParticles(MOON_DUST, x, y, z, count, 0.2, 0.8, 0.2, 0.0);
			}
		}
	}
}
