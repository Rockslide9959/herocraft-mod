package com.projecthero.mod.grave;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.event.EventConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;

/**
 * The single Gravebound Curse implementation. <b>Both</b> ways of catching it -- activating a
 * Graveyard's Cursed Grave and being hit by a rare Cursed Zombie -- come through
 * {@link #apply(ServerPlayer, CurseSource)}, and there is deliberately no second curse, second timer
 * or second scheduled raid anywhere in the mod (spec sections 1, 6, 7 and 14).
 *
 * <h2>One curse, never reset</h2>
 * {@link #apply} is a no-op when the player is already cursed. That single guard is what satisfies
 * every "must not" in section 7 at once: a second Cursed Zombie hit cannot reset, extend or stack the
 * timer, cannot schedule a second raid and cannot duplicate saved data -- and neither can walking
 * back into the Graveyard, because it takes the same path.
 *
 * <h2>What can and cannot remove it</h2>
 * Only {@link #clear} removes a curse, and only two things call it: eating an Enchanted Golden Apple
 * ({@link com.projecthero.mod.grave.GraveboundEvents}) and the operator debug command. Because the
 * state is a persistent, copy-on-death attachment rather than a status effect, logging out, dying,
 * changing dimension, restarting the server and drinking milk all leave it completely untouched --
 * not by special-casing each of them, but because none of them can reach attachment data at all.
 *
 * <h2>Cost</h2>
 * {@link #tick} runs once per online player per tick and does nothing but an integer decrement in the
 * overwhelmingly common "not cursed" case. Ambience is scheduled by a countdown rather than sampled
 * every tick, and the rare "a zombie shambles out of the dark" beat spawns one mob at most and only
 * in the final stage.
 */
public final class GraveboundCurse {
	private GraveboundCurse() {
	}

	public static GraveboundState state(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GRAVEBOUND_STATE);
	}

	public static void save(ServerPlayer player, GraveboundState state) {
		player.setAttached(ModAttachments.GRAVEBOUND_STATE, state);
	}

	public static boolean isCursed(ServerPlayer player) {
		return state(player).cursed();
	}

	public static int remainingTicks(ServerPlayer player) {
		return state(player).curseTicksLeft;
	}

	/**
	 * Apply the Gravebound Curse. Returns false -- changing nothing at all -- if this player is
	 * already cursed, whatever the original source was.
	 */
	public static boolean apply(ServerPlayer player, CurseSource source) {
		GraveboundState existing = state(player);
		if (existing.cursed()) {
			return false;
		}
		GraveboundState next = existing.copy();
		next.curseTicksLeft = Math.max(20, EventConfig.raid().curseDurationTicks);
		next.curseSource = source.name();
		next.curseAnnounced = false;
		next.nextAmbientTicks = 20 * 20;
		save(player, next);
		announce(player, source);
		return true;
	}

	/** Remove the curse and everything scheduled off it. Safe to call when not cursed. */
	public static boolean clear(ServerPlayer player, boolean announce) {
		GraveboundState existing = state(player);
		if (!existing.cursed()) {
			return false;
		}
		GraveboundState next = existing.copy();
		next.curseTicksLeft = 0;
		next.curseAnnounced = false;
		next.nextAmbientTicks = 0;
		save(player, next);
		if (announce) {
			player.sendSystemMessage(Component.translatable("message.projecthero.curse.broken")
					.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			if (player.level() instanceof ServerLevel level) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(),
						SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8f, 1.2f);
				level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(),
						30, 0.4, 0.7, 0.4, 0.05);
			}
			award(player, "gravebound/curse_broken");
		}
		return true;
	}

	/** Debug helper: force the curse to expire on the next tick, starting the raid immediately. */
	public static void expireNow(ServerPlayer player) {
		GraveboundState existing = state(player);
		if (!existing.cursed()) {
			return;
		}
		GraveboundState next = existing.copy();
		next.curseTicksLeft = 1;
		save(player, next);
	}

	// ---------------- per-player tick ----------------

	/** How often the ticking curse is written back to the attachment. See {@link #tick}. */
	private static final int SYNC_INTERVAL_TICKS = 20;

	/**
	 * One player, one tick. Called from the mod's existing per-player server tick loop. The whole
	 * method is a single field read plus an early return unless the player is actually cursed, which
	 * is why it can afford to run every tick.
	 *
	 * <h4>Why the countdown mutates in place</h4>
	 * The obvious implementation -- copy the state, decrement, {@link #save} -- would call
	 * {@code setAttached} on <em>every tick of every cursed player</em>, and this attachment is synced
	 * to its owner. That is one network packet per player per tick for twenty solid minutes: about
	 * 24,000 packets per curse, to move a number the HUD only ever renders to the nearest second.
	 *
	 * <p>So the counter is decremented on the live attachment object and written back (which is what
	 * triggers the sync) once a second, plus immediately on the two moments that actually matter -- an
	 * ambience beat and the curse expiring. The saved value is therefore never more than a second
	 * stale, which is invisible on a HUD that shows {@code m:ss}, and the packet count drops by
	 * twentyfold.
	 */
	public static void tick(ServerPlayer player) {
		GraveboundState state = state(player);
		if (!state.cursed()) {
			return;
		}
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}

		state.curseTicksLeft--;
		state.curseAnnounced = true;

		if (state.curseTicksLeft <= 0) {
			state.curseTicksLeft = 0;
			save(player, state);
			com.projecthero.mod.event.raid.ZombieRaidStarter.startForCursedPlayer(player);
			return;
		}

		if (--state.nextAmbientTicks <= 0) {
			state.nextAmbientTicks = ambience(level, player, state.curseTicksLeft);
			save(player, state);
			return;
		}
		if (state.curseTicksLeft % SYNC_INTERVAL_TICKS == 0) {
			save(player, state);
		}
	}

	/**
	 * One atmosphere beat, escalating as the timer runs down. Returns how many ticks to wait before
	 * the next one -- longer early, shorter at the end -- which is how "do not spam" is enforced
	 * structurally rather than by hoping the random rolls behave.
	 */
	private static int ambience(ServerLevel level, ServerPlayer player, int ticksLeft) {
		RandomSource random = player.getRandom();
		int finalStage = EventConfig.raid().curseFinalStageTicks;
		int total = Math.max(1, EventConfig.raid().curseDurationTicks);
		boolean isFinal = ticksLeft <= finalStage;
		boolean isMiddle = !isFinal && ticksLeft <= total / 2;

		if (isFinal) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					random.nextBoolean() ? SoundEvents.WARDEN_HEARTBEAT : SoundEvents.SOUL_ESCAPE.value(),
					SoundSource.AMBIENT, 0.7f, 0.6f + random.nextFloat() * 0.2f);
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 0.4, player.getZ(),
					6, 0.6, 0.4, 0.6, 0.01);
			if (random.nextFloat() < 0.5f) {
				player.displayClientMessage(Component.translatable(FINAL_LINES[random.nextInt(FINAL_LINES.length)])
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
			}
			if (random.nextFloat() < 0.35f) {
				spawnWatcher(level, player, random);
			}
			return 240 + random.nextInt(180); // 12-21s
		}

		if (isMiddle) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					random.nextBoolean() ? SoundEvents.ZOMBIE_AMBIENT : SoundEvents.AMBIENT_CAVE.value(),
					SoundSource.AMBIENT, 0.5f, 0.7f); // both unwrapped to SoundEvent for the shared call
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.2, player.getZ(),
					4, 0.5, 0.2, 0.5, 0.0);
			return 500 + random.nextInt(400); // 25-45s
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.4f, 0.8f);
		level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 0.5, player.getZ(),
				5, 0.6, 0.4, 0.6, 0.0);
		return 900 + random.nextInt(600); // 45-75s
	}

	/**
	 * The final-stage "something is following you" beat: one zombie, a little way off, on legal ground,
	 * staring at the player. Deliberately capped at one -- it is a warning, not a fight, and it must
	 * not turn the last two minutes into an early raid.
	 *
	 * <p>"changes 22": it is now the raid's own {@link com.projecthero.mod.event.entity.RaidZombie}
	 * (BASIC variant, near-identical to a vanilla zombie: 22 HP / 3.5 damage) rather than a literal
	 * {@code EntityType.ZOMBIE}. The curse ticks down on a wall clock, so this beat lands in broad
	 * daylight about as often as not -- and a vanilla zombie simply caught fire and died before the
	 * player ever noticed it, which is the one thing the beat exists to avoid. Every raid-family mob
	 * is sun-immune, does not convert in water and never calls reinforcements
	 * ({@link com.projecthero.mod.event.entity.RaidUndead}), so this one behaves the way the moment is
	 * written. It is not registered with any event instance, so it is not part of a raid's wave count.
	 */
	private static void spawnWatcher(ServerLevel level, ServerPlayer player, RandomSource random) {
		double angle = random.nextDouble() * Math.PI * 2.0;
		double dist = 10.0 + random.nextDouble() * 8.0;
		BlockPos pos = BlockPos.containing(
				player.getX() + Math.cos(angle) * dist,
				player.getY(),
				player.getZ() + Math.sin(angle) * dist);
		if (!level.isLoaded(pos)) {
			return;
		}
		BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
		if (Math.abs(ground.getY() - player.getBlockY()) > 8) {
			return;
		}
		com.projecthero.mod.event.entity.RaidZombie zombie =
				com.projecthero.mod.event.entity.RaidEntityTypes.RAID_ZOMBIE.create(level);
		if (zombie == null) {
			return;
		}
		zombie.setVariant(com.projecthero.mod.event.entity.RaidZombie.Variant.BASIC);
		zombie.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5,
				(float) (Math.toDegrees(angle) + 180.0), 0.0f);
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);
		zombie.setTarget(player);
		level.addFreshEntity(zombie);
		level.sendParticles(ParticleTypes.SOUL, ground.getX() + 0.5, ground.getY() + 1.0, ground.getZ() + 0.5,
				8, 0.3, 0.5, 0.3, 0.01);
	}

	private static final String[] FINAL_LINES = {
			"message.projecthero.curse.ambient.following",
			"message.projecthero.curse.ambient.restless",
			"message.projecthero.curse.ambient.shifting",
	};

	// ---------------- feedback ----------------

	private static void announce(ServerPlayer player, CurseSource source) {
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 50, 20));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
				Component.translatable("message.projecthero.curse.applied.sub").withStyle(ChatFormatting.GRAY)));
		player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
				Component.translatable("message.projecthero.curse.applied.title")
						.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
		player.sendSystemMessage(Component.translatable("message.projecthero.curse.applied.hint")
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));

		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.6f, 1.4f);
			level.playSound(null, player.getX(), player.getY(), player.getZ(),
					SoundEvents.SOUL_ESCAPE, SoundSource.HOSTILE, 1.0f, 0.5f);
			level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1.0, player.getZ(),
					40, 0.5, 0.9, 0.5, 0.03);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.6, player.getZ(),
					25, 0.6, 0.5, 0.6, 0.01);
		}
		ProjectHeroMod.LOGGER.debug("[ProjectHero] Gravebound Curse applied to {} from {}",
				player.getGameProfile().getName(), source);
		award(player, "gravebound/cursed");
	}

	// ---------------- advancements / research ----------------

	/** Award one of this mod's code-triggered advancements, exactly like {@code MutationManager} does. */
	public static void award(ServerPlayer player, String path) {
		if (player.getServer() == null) {
			return;
		}
		AdvancementHolder holder = player.getServer().getAdvancements()
				.get(ResourceLocation.fromNamespaceAndPath(ProjectHeroMod.MOD_ID, path));
		if (holder != null) {
			player.getAdvancements().award(holder, "code_trigger");
		}
	}
}
