package com.projecthero.mod.greenlantern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.greenlantern.block.FallenLanternPedestalBlock;
import com.projecthero.mod.greenlantern.item.GreenLanternItems;
import com.projecthero.mod.hero.HeroTiers;
import com.projecthero.mod.network.GreenLanternTrialPromptPayload;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * The Will Trial: right-clicking an unclaimed Fallen Lantern Site pedestal begins a 3-wave fight
 * within a 32-block radius. First-pass enemies are vanilla mobs, boosted, marked Glowing and put on a
 * green-coloured scoreboard team for a readable green (not white) outline, and given a plain helmet so
 * the undead ones never catch fire in daylight -- see the build brief's explicit allowance not to block
 * the whole feature on a bespoke trial mob.
 *
 * <p>v0.11.11 rework, explicit user request: starting a trial now physically seals the site --
 * {@link #sealArea} shoves every other living thing outside the radius and raises a hollow green
 * hard-light dome over the whole area, and every tick ({@link #tick}) re-expels anyone but the
 * attempting player and the trial's own mobs who wanders back in, so nobody can interfere with or steal
 * someone else's attempt. Clearing wave 3 no longer bonds the ring immediately: it opens an "Are you
 * afraid?" confirmation on the winner's screen ({@link #promptAfraid}/{@link #handleAnswer}). Answering
 * "no" declines outright (no ring, no cooldown -- the site is immediately open to anyone, including the
 * same player again) and answering "yes" breaks the pedestal for good and hands over the Power Ring and
 * Lantern Core -- but {@link GreenLantern#bond} itself is now deferred to the moment the player actually
 * right-clicks the ring item ({@link #initialize}'s {@code UseItemCallback}), which is also the moment
 * {@code PowerRingLayer} starts rendering it on their body, since both key off the same
 * {@link GreenLantern#hasPower} flag.
 *
 * <p>Failure (leaving the radius for 8s, dying, or logging out) still drops a 10-minute per-player
 * cooldown for that pedestal and restores the dome/terrain either way.
 */
public final class GreenLanternTrial {
	/** Scoreboard team every trial mob joins purely so its Glowing outline renders green, not white. */
	private static final String TRIAL_TEAM_NAME = "projecthero_gl_trial";
	/** How often (ticks) the active-trial area is re-swept for anyone who wandered back in. */
	private static final int SEAL_ENFORCE_INTERVAL_TICKS = 10;

	private static final class Trial {
		final BlockPos pedestal;
		final Vec3 center;
		final ServerLevel level;
		final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
		int wave = 1;
		final List<Integer> liveMobIds = new ArrayList<>();
		long outOfRadiusSince = -1L;
		/** True once wave 3 is cleared and the "Are you afraid?" answer is pending. */
		boolean awaitingAnswer = false;
		final List<BlockPos> domeCells = new ArrayList<>();
		final List<BlockState> domePrevious = new ArrayList<>();

		Trial(BlockPos pedestal, Vec3 center, ServerLevel level) {
			this.pedestal = pedestal;
			this.center = center;
			this.level = level;
			this.dimension = level.dimension();
		}
	}

	private static final Map<UUID, Trial> ACTIVE = new ConcurrentHashMap<>();
	/** (player, pedestal) -> game-time the 10-minute failure cooldown ends. */
	private static final Map<String, Long> COOLDOWNS = new ConcurrentHashMap<>();
	/** Pedestals with a trial currently running -- refuses a second player racing the same site. */
	private static final java.util.Set<Long> PEDESTALS_IN_PROGRESS = java.util.concurrent.ConcurrentHashMap.newKeySet();

	private GreenLanternTrial() {
	}

	public static void clearSessionState() {
		ACTIVE.clear();
		COOLDOWNS.clear();
		PEDESTALS_IN_PROGRESS.clear();
	}

	/** The ring itself is inert until right-clicked -- that's the moment {@link GreenLantern#bond} runs
	 *  and the cosmetic ring model starts showing on the wearer's body. */
	public static void initialize() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !stack.is(GreenLanternItems.POWER_RING)
					|| GreenLantern.hasPower(sp)) {
				return InteractionResultHolder.pass(stack);
			}
			GreenLantern.bond(sp);
			return InteractionResultHolder.success(stack);
		});
	}

	public static void attemptStart(ServerPlayer player, BlockPos pedestal, boolean claimed) {
		if (claimed) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.site_claimed"), true);
			return;
		}
		if (ACTIVE.containsKey(player.getUUID())) {
			return;
		}
		if (GreenLantern.hasPower(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.already_bonded"), true);
			return;
		}
		if (HeroTiers.hasHeroTier(player) || HeroTiers.hasExperimental(player)) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_ineligible"), true);
			return;
		}
		// v0.11.12, explicit user request: the ring demands proof of experience before it will even
		// test your will -- experience LEVELS (the enchant-table number), not XP points, and purely a
		// gate, not a cost -- nothing is spent here.
		if (player.experienceLevel < GreenLanternConfig.TRIAL_LEVEL_REQUIREMENT) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_level_required",
					GreenLanternConfig.TRIAL_LEVEL_REQUIREMENT), true);
			return;
		}
		Long cooldownUntil = COOLDOWNS.get(cooldownKey(player, pedestal));
		if (cooldownUntil != null && player.level().getGameTime() < cooldownUntil) {
			int secs = (int) ((cooldownUntil - player.level().getGameTime()) / 20);
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_cooldown",
					secs), true);
			return;
		}
		// The last gate before committing: refuses a second player racing the same unclaimed pedestal.
		// Every earlier `return` above must NOT have reserved this, or a rejected attempt would leave
		// the pedestal permanently (falsely) marked in-progress.
		if (!PEDESTALS_IN_PROGRESS.add(pedestal.asLong())) {
			player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_in_progress"), true);
			return;
		}

		Trial trial = new Trial(pedestal, Vec3.atCenterOf(pedestal), player.serverLevel());
		ACTIVE.put(player.getUUID(), trial);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_begin")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
		// v0.11.12 fix: expel outsiders and spawn the wave BEFORE the dome goes up, not after. Building
		// the dome first (the original v0.11.11 order) was the actual cause of "spawns the dome but
		// nothing else happens" -- groundedPointNear() below snaps a spawn point onto the terrain via
		// the MOTION_BLOCKING_NO_LEAVES heightmap, and the dome's own glass shell (which sits ~30 blocks
		// above the crater floor near the centre) immediately became the tallest blocking block in every
		// column under it, so every mob in wave 1 silently spawned up at the dome's own ceiling instead
		// of on the ground -- invisible, inaudible, and almost certainly dead of suffocation within a
		// couple of ticks, which from the player's point of view really did look like nothing happened.
		expelOutsiders(trial, player.getUUID());
		spawnWave(player, trial);
		buildDome(trial);
	}

	private static String cooldownKey(ServerPlayer player, BlockPos pedestal) {
		return player.getUUID() + "@" + pedestal.asLong();
	}

	// ---------------- sealing the trial area ----------------

	/**
	 * v0.11.11, explicit user request ("push all other entities out of the range of the trial ... only
	 * the player who started the trial and the mobs involved should be allowed inside"). Sweeps every
	 * living thing in the radius except the attempting player and the trial's own tracked mobs, and
	 * shoves each one straight out past the boundary. Called once when the trial starts and again every
	 * {@link #SEAL_ENFORCE_INTERVAL_TICKS} while it runs, so nobody can walk, fly or teleport back in
	 * mid-attempt either.
	 */
	private static void expelOutsiders(Trial trial, UUID exempt) {
		double r = GreenLanternConfig.TRIAL_RADIUS;
		AABB box = new AABB(trial.center.x - r, trial.center.y - r, trial.center.z - r,
				trial.center.x + r, trial.center.y + r, trial.center.z + r);
		double r2 = r * r;
		for (LivingEntity e : trial.level.getEntitiesOfClass(LivingEntity.class, box,
				e2 -> !e2.getUUID().equals(exempt) && !trial.liveMobIds.contains(e2.getId())
						&& e2.position().distanceToSqr(trial.center) < r2)) {
			expel(e, trial.center, r);
		}
	}

	private static void expel(LivingEntity e, Vec3 center, double radius) {
		Vec3 away = e.position().subtract(center);
		Vec3 flat = away.x * away.x + away.z * away.z < 1.0e-4
				? new Vec3(1, 0, 0) : new Vec3(away.x, 0, away.z).normalize();
		Vec3 dest = center.add(flat.scale(radius + 3.0));
		e.teleportTo(dest.x, e.getY(), dest.z);
		e.setDeltaMovement(flat.scale(0.5).add(0, 0.1, 0));
		if (e instanceof ServerPlayer sp) {
			sp.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_area_sealed"), true);
		}
	}

	/**
	 * A hollow hard-light dome over the whole trial radius -- a spherical shell (only the upper half is
	 * ever visited) centred on the pedestal, thick enough to have no gaps. Every displaced block is
	 * remembered in {@link Trial#domeCells}/{@link Trial#domePrevious} so {@link #restoreDome} can put
	 * the site back exactly as it was, regardless of which of the trial's several end states triggers it.
	 */
	private static void buildDome(Trial trial) {
		ServerLevel level = trial.level;
		Vec3 c = trial.center;
		double r = GreenLanternConfig.TRIAL_RADIUS;
		int ir = (int) Math.ceil(r);
		BlockState domeState = Blocks.GREEN_STAINED_GLASS.defaultBlockState();
		for (int dx = -ir; dx <= ir; dx++) {
			for (int dy = 0; dy <= ir; dy++) {
				for (int dz = -ir; dz <= ir; dz++) {
					double dist = Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz);
					if (dist < r - 1.0 || dist > r) {
						continue;
					}
					BlockPos pos = BlockPos.containing(c.x + dx, c.y + dy, c.z + dz);
					BlockState current = level.getBlockState(pos);
					if (!canSealCell(level, pos, current)) {
						continue;
					}
					trial.domeCells.add(pos.immutable());
					trial.domePrevious.add(current);
				}
			}
		}
		// v0.11.12: UPDATE_CLIENTS, not UPDATE_ALL -- a dome shell this size is several thousand blocks,
		// and UPDATE_ALL's per-block neighbour-notify cascade (irrelevant for plain glass, which has no
		// neighbour-dependent behaviour) turned what should be an instant effect into a multi-second
		// stall on the server thread. Sync-only placement is what every other bulk-placement site in the
		// mod already uses for exactly this reason (see IronManSuitPlatformBlockEntity/LabDeviceBlock).
		for (int i = 0; i < trial.domeCells.size(); i++) {
			level.setBlock(trial.domeCells.get(i), domeState, Block.UPDATE_CLIENTS);
		}
	}

	/** Never overwrites bedrock/portals/containers/unbreakable blocks -- same convention as every other
	 *  hard-light construct in this power ({@code GreenLanternConstructs#add}). */
	private static boolean canSealCell(ServerLevel level, BlockPos pos, BlockState current) {
		if (!current.canBeReplaced() && !current.isAir()) {
			return false;
		}
		if (current.getDestroySpeed(level, pos) < 0) {
			return false;
		}
		return level.getBlockEntity(pos) == null;
	}

	/** Restores every dome cell the trial displaced, whichever way the trial ended. */
	private static void restoreDome(Trial trial) {
		for (int i = 0; i < trial.domeCells.size(); i++) {
			BlockPos pos = trial.domeCells.get(i);
			if (trial.level.hasChunkAt(pos) && trial.level.getBlockState(pos).is(Blocks.GREEN_STAINED_GLASS)) {
				trial.level.setBlock(pos, trial.domePrevious.get(i), Block.UPDATE_CLIENTS);
			}
		}
	}

	// ---------------- waves ----------------

	private static void spawnWave(ServerPlayer player, Trial trial) {
		ServerLevel level = trial.level;
		int ordinary = switch (trial.wave) {
			case 1 -> GreenLanternConfig.TRIAL_WAVE_1_COUNT;
			case 2 -> GreenLanternConfig.TRIAL_WAVE_2_COUNT;
			default -> GreenLanternConfig.TRIAL_WAVE_3_ORDINARY_COUNT;
		};
		int ranged = trial.wave == 2 ? 2 : 0;
		for (int i = 0; i < ordinary; i++) {
			MobFactory factory = i < ranged
					? lvl -> new Skeleton(net.minecraft.world.entity.EntityType.SKELETON, lvl)
					: lvl -> new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, lvl);
			Mob mob = spawnBoosted(level, trial.center, factory);
			if (mob != null) {
				trial.liveMobIds.add(mob.getId());
			}
		}
		if (trial.wave == 3) {
			Mob fearEcho = spawnBoosted(level, trial.center, lvl -> new Vex(net.minecraft.world.entity.EntityType.VEX, lvl));
			if (fearEcho != null) {
				fearEcho.setCustomName(Component.literal("Fear Echo").withStyle(ChatFormatting.DARK_GREEN));
				fearEcho.setCustomNameVisible(true);
				fearEcho.getAttribute(Attributes.MAX_HEALTH).setBaseValue(60.0);
				fearEcho.setHealth(60.0f);
				fearEcho.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6.0);
				trial.liveMobIds.add(fearEcho.getId());
			}
		}
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_wave", trial.wave), true);
		level.playSound(null, trial.center.x, trial.center.y, trial.center.z, SoundEvents.RAVAGER_ROAR,
				SoundSource.HOSTILE, 1.0f, 1.0f);
	}

	private static Mob spawnBoosted(ServerLevel level, Vec3 center, MobFactory factory) {
		Vec3 pos = groundedPointNear(level, center, 6.0, 14.0);
		Mob mob = factory.create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
				net.minecraft.world.entity.MobSpawnType.EVENT, null);
		mob.getAttribute(Attributes.MAX_HEALTH).addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
				com.projecthero.mod.ProjectHeroMod.id("green_lantern_trial_hp"), 0.5, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		mob.setHealth(mob.getMaxHealth());
		mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20000, 0, false, false, false));
		// A plain helmet is the same trick vanilla itself uses to keep an undead mob from ever catching
		// fire in daylight (checked before ignition, not a workaround after the fact) -- explicit user
		// request ("make them unable to burn in daylight"). setDropChance keeps it from littering the
		// ground as a stray drop once the mob dies.
		mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		mob.setDropChance(EquipmentSlot.HEAD, 0.0f);
		level.addFreshEntity(mob);
		joinGreenGlowTeam(level, mob);
		return mob;
	}

	/**
	 * Puts {@code mob} on a dedicated scoreboard team coloured green, purely so its vanilla Glowing
	 * outline (already applied above) renders green instead of the default white -- explicit user
	 * request. The team itself is created once, lazily, and simply reused for every trial mob after.
	 */
	private static void joinGreenGlowTeam(ServerLevel level, Mob mob) {
		Scoreboard scoreboard = level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayerTeam(TRIAL_TEAM_NAME);
		if (team == null) {
			team = scoreboard.addPlayerTeam(TRIAL_TEAM_NAME);
			team.setColor(ChatFormatting.GREEN);
		}
		scoreboard.addPlayerToTeam(mob.getScoreboardName(), team);
	}

	private interface MobFactory {
		Mob create(ServerLevel level);
	}

	/**
	 * A random point in the ring [min, max] around {@code center}, snapped onto the real surface
	 * (the crater's walls mean a fixed {@code center.y} often lands a spawn inside solid ground). Tries
	 * a few times to avoid a column with no open headroom before falling back to whatever the last roll
	 * found -- never blocks spawning outright.
	 */
	private static Vec3 groundedPointNear(ServerLevel level, Vec3 center, double min, double max) {
		Vec3 best = null;
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = level.random.nextDouble() * Math.PI * 2;
			double dist = min + level.random.nextDouble() * (max - min);
			double x = center.x + Math.cos(angle) * dist;
			double z = center.z + Math.sin(angle) * dist;
			int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
					(int) Math.floor(x), (int) Math.floor(z));
			Vec3 candidate = new Vec3(x, y, z);
			BlockPos feet = BlockPos.containing(candidate);
			if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
					&& level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
				return candidate;
			}
			best = candidate;
		}
		return best;
	}

	// ---------------- per-server tick ----------------

	public static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) {
			return;
		}
		long now = server.overworld().getGameTime();
		for (var it = ACTIVE.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Trial> entry = it.next();
			Trial trial = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				fail(entry.getKey(), trial, "message.projecthero.green_lantern.trial_failed_logout");
				it.remove();
				continue;
			}
			if (!player.isAlive()) {
				fail(entry.getKey(), trial, "message.projecthero.green_lantern.trial_failed_death");
				it.remove();
				continue;
			}
			if (player.level().dimension() != trial.dimension) {
				// Left the dimension entirely (portal, /execute in, command teleport) -- treat exactly
				// like leaving the 32-block radius rather than letting the trial silently follow them.
				fail(entry.getKey(), trial, "message.projecthero.green_lantern.trial_failed_left");
				it.remove();
				continue;
			}
			if (player.position().distanceTo(trial.center) > GreenLanternConfig.TRIAL_RADIUS) {
				if (trial.outOfRadiusSince < 0) {
					trial.outOfRadiusSince = now;
				} else if (now - trial.outOfRadiusSince > GreenLanternConfig.TRIAL_LEAVE_FAIL_TICKS) {
					fail(entry.getKey(), trial, "message.projecthero.green_lantern.trial_failed_left");
					it.remove();
					continue;
				}
			} else {
				trial.outOfRadiusSince = -1L;
			}

			if (now % SEAL_ENFORCE_INTERVAL_TICKS == 0) {
				expelOutsiders(trial, entry.getKey());
			}

			trial.liveMobIds.removeIf(id -> !(trial.level.getEntity(id) instanceof Mob m) || !m.isAlive());
			// Belt-and-braces on top of the helmet trick above -- guarantees a trial mob never actually
			// stays lit even if something else ever manages to ignite it.
			for (int id : trial.liveMobIds) {
				if (trial.level.getEntity(id) instanceof Mob m) {
					m.clearFire();
				}
			}

			if (!trial.awaitingAnswer && trial.liveMobIds.isEmpty()) {
				if (trial.wave < 3) {
					trial.wave++;
					spawnWave(player, trial);
				} else {
					promptAfraid(player, trial);
				}
			}
		}
	}

	private static void fail(UUID playerId, Trial trial, String messageKey) {
		PEDESTALS_IN_PROGRESS.remove(trial.pedestal.asLong());
		for (int id : trial.liveMobIds) {
			if (trial.level.getEntity(id) instanceof Mob m) {
				m.discard();
			}
		}
		restoreDome(trial);
		COOLDOWNS.put(playerId + "@" + trial.pedestal.asLong(),
				trial.level.getGameTime() + GreenLanternConfig.TRIAL_FAIL_COOLDOWN_TICKS);
		ServerPlayer player = trial.level.getServer() != null ? trial.level.getServer().getPlayerList().getPlayer(playerId) : null;
		if (player != null) {
			player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.RED), false);
		}
	}

	// ---------------- "Are you afraid?" ----------------

	/** Wave 3 cleared -- open the confirmation on the winner's screen instead of bonding immediately. */
	private static void promptAfraid(ServerPlayer player, Trial trial) {
		trial.awaitingAnswer = true;
		ServerPlayNetworking.send(player, GreenLanternTrialPromptPayload.INSTANCE);
	}

	/**
	 * The server-side landing spot for {@code GreenLanternTrialAnswerPayload}. Re-validates that the
	 * sender actually has a trial of their own awaiting an answer -- a modified client sending this
	 * unprompted (or a second time) finds nothing here to act on.
	 */
	public static void handleAnswer(ServerPlayer player, boolean yes) {
		Trial trial = ACTIVE.get(player.getUUID());
		if (trial == null || !trial.awaitingAnswer) {
			return;
		}
		ACTIVE.remove(player.getUUID());
		PEDESTALS_IN_PROGRESS.remove(trial.pedestal.asLong());
		restoreDome(trial);
		if (yes) {
			grantRing(player, trial);
		} else {
			decline(player, trial);
		}
	}

	/**
	 * "Yes": breaks the pedestal for good (so nobody can farm the site again) and hands over the ring +
	 * core -- but does NOT bond the power yet. {@link GreenLantern#bond} only runs once the player
	 * actually right-clicks the ring ({@link #initialize}), per explicit user request.
	 */
	private static void grantRing(ServerPlayer player, Trial trial) {
		trial.level.destroyBlock(trial.pedestal, false);
		ItemStack core = new ItemStack(GreenLanternItems.LANTERN_CORE);
		if (!player.getInventory().add(core)) {
			player.drop(core, false);
		}
		ItemStack ring = new ItemStack(GreenLanternItems.POWER_RING);
		if (!player.getInventory().add(ring)) {
			player.drop(ring, false);
		}
		trial.level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getY() + 1, player.getZ(),
				60, 0.5, 1.0, 0.5, 0.2);
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_ring_granted")
				.withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD), false);
	}

	/** "No": nothing granted, no cooldown either -- the pedestal stays unclaimed, open to anyone at once. */
	private static void decline(ServerPlayer player, Trial trial) {
		player.displayClientMessage(Component.translatable("message.projecthero.green_lantern.trial_declined"), false);
		trial.level.playSound(null, trial.center.x, trial.center.y, trial.center.z,
				SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6f, 0.8f);
	}
}
