package com.projecthero.mod.event.raid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.event.EventConfig;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.entity.PillagerSpy;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * v0.14.21: the Supervillain's Mark -- the Supervillain Raid's answer to vanilla Bad Omen / Raid Omen.
 *
 * <ol>
 *   <li><b>Supervillain's Mark</b> ({@link #MARK}): a Pillager Spy's hit, or killing a Pillager Spy (the way a raid
 *       captain's banner used to hand out Bad Omen), marks the <em>player</em>. 100 minutes, harmful, cleared by milk.
 *       Anywhere -- the player no longer has to be standing in a village.</li>
 *   <li><b>Supervillain Omen</b> ({@link #OMEN}): when a marked player is inside a village (the same test the Spy
 *       uses, {@link PillagerSpy#insideVillage}) on a non-Peaceful world and no Supervillain Raid is already running
 *       there, the mark converts into a 30-second omen -- like vanilla's Raid Omen -- with a warning title and sound.
 *       </li>
 *   <li>When the omen runs out, the existing Supervillain Raid starts at the spot where it converted, through
 *       {@link SupervillainRaidStarter#startFromOmen} (so the raid's own Marked-for-Attack preparation timer, limits
 *       and config all apply unchanged). The mark is only <em>consumed</em> once a raid actually starts: if the raid
 *       cannot start (one is already running there, or the world went Peaceful during the omen) the player gets the
 *       mark back.</li>
 * </ol>
 *
 * <p>Both effects are pure markers ({@code shouldApplyEffectTickThisTick} is always false): the conversion is driven
 * from the server tick ({@link #tick}) rather than {@code applyEffectTick}, because that runs while vanilla is
 * iterating the entity's effect map and adding / removing effects there is a {@code ConcurrentModificationException}
 * waiting to happen. Where the omen converted and when it fires live in the {@link #OMEN_STATE} attachment (vanilla
 * keeps the same thing in {@code ServerPlayer#raidOmenPosition}); it is not copied on death, and is dropped as soon as
 * the omen effect is gone (milk, death), so a cleared omen never starts a raid.
 */
public final class SupervillainMark {
	/** How often (ticks) marked players are checked for standing in a village -- the village test is a POI search. */
	private static final int VILLAGE_CHECK_INTERVAL = 10;
	/** The omen effect outlives its deadline by this much, so it is still showing when the raid fires. */
	private static final int OMEN_EFFECT_SLACK = 10;

	public static final Holder<MobEffect> MARK = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ProjectHeroMod.id("supervillain_mark"), new MarkerEffect(0x4A1A6B));
	public static final Holder<MobEffect> OMEN = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			ProjectHeroMod.id("supervillain_omen"), new MarkerEffect(0x8A1238));

	/** Where the omen converted (the raid's spot) and the game time it fires at. */
	public record OmenState(long pos, long fireAt) {
		public static final Codec<OmenState> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.LONG.fieldOf("pos").forGetter(OmenState::pos),
				Codec.LONG.fieldOf("fire_at").forGetter(OmenState::fireAt)).apply(i, OmenState::new));
	}

	public static final AttachmentType<OmenState> OMEN_STATE = AttachmentRegistry.create(
			ProjectHeroMod.id("supervillain_omen_state"), builder -> builder.persistent(OmenState.CODEC));

	private SupervillainMark() {
	}

	/** Class-load hook (registers the two effects and the attachment). */
	public static void initialize() {
	}

	private static final class MarkerEffect extends MobEffect {
		MarkerEffect(int color) {
			super(MobEffectCategory.HARMFUL, color);
		}

		@Override
		public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
			return false;
		}
	}

	// ---------------- queries ----------------

	public static boolean isMarked(ServerPlayer player) {
		return player.hasEffect(MARK);
	}

	public static boolean hasOmen(ServerPlayer player) {
		return player.hasEffect(OMEN);
	}

	/** Marked or already counting down -- either way a Spy has nothing left to do to this player. */
	public static boolean isMarkedOrOmened(ServerPlayer player) {
		return isMarked(player) || hasOmen(player);
	}

	// ---------------- giving the mark ----------------

	/**
	 * Mark {@code player} (a Spy's hit, a Spy's death, or the debug command). Refreshes the full duration if already
	 * marked; does nothing to a player whose omen is already counting down (the raid is already on its way).
	 *
	 * @return true if the player now carries a fresh mark
	 */
	public static boolean mark(ServerPlayer player) {
		if (player.isSpectator() || hasOmen(player)) {
			return false;
		}
		boolean fresh = !isMarked(player);
		int ticks = Math.max(1, EventConfig.supervillain().markDurationMinutes) * 60 * 20;
		player.addEffect(new MobEffectInstance(MARK, ticks, 0, false, false, true));
		if (fresh) {
			player.displayClientMessage(Component.translatable("event.projecthero.supervillain_raid.spy_marked")
					.withStyle(ChatFormatting.DARK_PURPLE), true);
			player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.APPLY_EFFECT_BAD_OMEN,
					SoundSource.PLAYERS, 1.0f, 0.7f);
		}
		return true;
	}

	// ---------------- per-tick conversion ----------------

	/** Server tick (from {@link SupervillainRaidEvents}). */
	public static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			tickPlayer(player, server.getTickCount() % VILLAGE_CHECK_INTERVAL == 0);
		}
	}

	/**
	 * One player's mark / omen step. {@code checkVillage} gates the (POI-searching) village test for a marked player;
	 * the omen is checked every tick so its countdown cues land on whole seconds.
	 */
	public static void tickPlayer(ServerPlayer player, boolean checkVillage) {
		OmenState omen = player.getAttached(OMEN_STATE);
		if (omen != null) {
			if (!hasOmen(player)) {
				player.removeAttached(OMEN_STATE); // milk / death took the omen: no raid
			} else {
				tickOmen(player, omen);
			}
			return;
		}
		if (checkVillage && isMarked(player)) {
			tryConvert(player);
		}
	}

	/**
	 * The Bad Omen -> Raid Omen step: a marked player standing in a village, on a non-Peaceful world, with no
	 * Supervillain Raid already running there, swaps the mark for the omen countdown.
	 *
	 * @return true if the omen started
	 */
	public static boolean tryConvert(ServerPlayer player) {
		if (!isMarked(player) || hasOmen(player) || player.isSpectator()) {
			return false;
		}
		ServerLevel level = player.serverLevel();
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return false;
		}
		BlockPos pos = player.blockPosition();
		if (!PillagerSpy.insideVillage(level, pos)) {
			return false;
		}
		if (raidBlocked(level, pos)) {
			return false; // keep the mark until this village's raid is over
		}
		int omenTicks = Math.max(1, EventConfig.supervillain().markOmenSeconds) * 20;
		player.removeEffect(MARK);
		player.addEffect(new MobEffectInstance(OMEN, omenTicks + OMEN_EFFECT_SLACK, 0, false, false, true));
		player.setAttached(OMEN_STATE, new OmenState(pos.asLong(), level.getGameTime() + omenTicks));

		player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 20));
		player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
				"event.projecthero.supervillain_raid.omen").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD)));
		player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
				"event.projecthero.supervillain_raid.omen.sub", omenTicks / 20).withStyle(ChatFormatting.GRAY)));
		level.playSound(null, pos, SoundEvents.APPLY_EFFECT_RAID_OMEN, SoundSource.HOSTILE, 1.0f, 0.8f);
		level.playSound(null, pos, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6f, 0.5f);
		ProjectHeroMod.LOGGER.info("[SupervillainRaid] {}'s Supervillain's Mark turned into the omen at {}",
				player.getGameProfile().getName(), pos);
		return true;
	}

	private static void tickOmen(ServerPlayer player, OmenState omen) {
		ServerLevel level = player.serverLevel();
		long left = omen.fireAt() - level.getGameTime();
		if (left > 0) {
			if (left % 20 == 0) {
				long seconds = left / 20;
				if (seconds == 10) {
					player.displayClientMessage(Component.translatable("event.projecthero.supervillain_raid.omen.warn",
							seconds).withStyle(ChatFormatting.RED), true);
				} else if (seconds <= 5) {
					player.displayClientMessage(Component.literal(String.valueOf(seconds))
							.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
					player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.HOSTILE, 1.0f, 0.6f);
				}
			}
			return;
		}
		fireOmen(player, omen);
	}

	/** The omen ran out: start the raid where it converted, or hand the mark back if it cannot start. */
	private static void fireOmen(ServerPlayer player, OmenState omen) {
		ServerLevel level = player.serverLevel();
		player.removeAttached(OMEN_STATE);
		player.removeEffect(OMEN);
		BlockPos pos = BlockPos.of(omen.pos());
		boolean started = level.getDifficulty() != Difficulty.PEACEFUL
				&& SupervillainRaidStarter.startFromOmen(level, pos);
		if (started) {
			ProjectHeroMod.LOGGER.info("[SupervillainRaid] {}'s omen started a Supervillain Raid at {}",
					player.getGameProfile().getName(), pos);
			return;
		}
		// Peaceful, or a raid got there first: the mark is kept until a raid can start.
		mark(player);
	}

	/** True if a Supervillain Raid is already running close enough to {@code pos} to stop a new one starting. */
	public static boolean raidBlocked(ServerLevel level, BlockPos pos) {
		return EventManager.anyActiveNear(level, pos, SupervillainRaid.TYPE_ID,
				EventConfig.framework().minDistanceBetweenEvents);
	}

	// ---------------- test / debug hooks ----------------

	/** Make a running omen fire on the next tick (debug command, gametests). */
	public static void expireOmenNow(ServerPlayer player) {
		OmenState omen = player.getAttached(OMEN_STATE);
		if (omen != null) {
			player.setAttached(OMEN_STATE, new OmenState(omen.pos(), player.serverLevel().getGameTime()));
		}
	}

	/** Remove the mark, the omen and its record (debug command, gametests). */
	public static void clear(ServerPlayer player) {
		player.removeEffect(MARK);
		player.removeEffect(OMEN);
		player.removeAttached(OMEN_STATE);
	}
}
