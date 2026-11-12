package com.projecthero.mod.shazam;

import com.projecthero.mod.shazam.data.ShazamState;
import com.projecthero.mod.shazam.entity.ShazamEntities;
import com.projecthero.mod.shazam.entity.WizardShazamEntity;
import com.projecthero.mod.worthiness.Worthiness;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.3 Shazam: getting the power. During a thunderstorm a very rare <b>Wizard Shazam</b> appears near a village ({@link #trySpawn},
 * one 30-second check per player in 60). Right-click him: he judges whether you are <b>pure of heart</b> ({@link #pure}, on top of the
 * existing {@link Worthiness} -- the worthy and a Hero of the Village always are): no villager or iron golem killed in the last three
 * days, and no villager nearby who dislikes you. Worthy: he says your name and grants the power (it replaces your Primary). Unworthy:
 * he fades in a lightning flash and won't come back to you for 5 days. Random serums never give it.
 */
public final class ShazamWizards {
	private ShazamWizards() {
	}

	static void clearSessionState() {
		// no static state: the bans and kills live in each player's ShazamState
	}

	/** A villager or iron golem died at {@code killer}'s hand. */
	static void rememberInnocentKill(ServerPlayer killer) {
		ShazamState n = Shazam.state(killer).copy();
		n.innocentKillAt = Math.max(1L, killer.level().getGameTime());
		Shazam.save(killer, n);
	}

	/** Pure of heart, as the Wizard sees it. */
	public static boolean pure(ServerPlayer player) {
		if (Worthiness.isWorthy(player) || Worthiness.hasAscensionEffect(player)) {
			return true;
		}
		ShazamState s = Shazam.state(player);
		long now = player.level().getGameTime();
		if (s.innocentKillAt > 0L && now - s.innocentKillAt < ShazamConfig.INNOCENT_KILL_MEMORY && now >= s.innocentKillAt) {
			return false;
		}
		double r = ShazamConfig.REPUTATION_RANGE;
		for (Villager v : player.level().getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(r))) {
			if (v.getPlayerReputation(player) < 0) {
				return false;
			}
		}
		return true;
	}

	public static boolean banned(ServerPlayer player) {
		ShazamState s = Shazam.peek(player);
		return s != null && s.wizardBanUntil > player.level().getGameTime();
	}

	private static void line(ServerPlayer player, String key, ChatFormatting colour, Object... args) {
		player.sendSystemMessage(Component.translatable("message.projecthero.shazam.wizard.name").withStyle(ChatFormatting.GOLD)
				.append(Component.translatable(key, args).withStyle(colour)));
	}

	/** Right-click: the judgement. True if the power was granted. */
	public static boolean judge(WizardShazamEntity wizard, ServerPlayer player) {
		if (Shazam.hasPower(player)) {
			line(player, "message.projecthero.shazam.wizard.already", ChatFormatting.WHITE);
			return false;
		}
		if (banned(player)) {
			line(player, "message.projecthero.shazam.wizard.banned", ChatFormatting.GRAY);
			return false;
		}
		if (!pure(player)) {
			line(player, "message.projecthero.shazam.wizard.unworthy", ChatFormatting.RED);
			ShazamState n = Shazam.state(player).copy();
			n.wizardBanUntil = player.level().getGameTime() + ShazamConfig.WIZARD_BAN_TICKS;
			Shazam.save(player, n);
			fade(wizard);
			return false;
		}
		line(player, "message.projecthero.shazam.wizard.worthy", ChatFormatting.WHITE, player.getDisplayName());
		if (!Shazam.grant(player)) {
			return false; // the unique-powers rule said why
		}
		line(player, "message.projecthero.shazam.wizard.say_it", ChatFormatting.YELLOW);
		fade(wizard);
		return true;
	}

	/** He fades in a flash of lightning. */
	public static void fade(WizardShazamEntity wizard) {
		if (!(wizard.level() instanceof ServerLevel level) || wizard.isRemoved()) {
			return;
		}
		Vec3 c = wizard.position();
		Shazam.bolt(level, c);
		level.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 1, 0, 0, 0, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y + 1, c.z, 30, 0.4, 0.9, 0.4, 0.15);
		wizard.discard();
	}

	/** Spawns him near {@code player} (the command, and {@link #trySpawn}). */
	public static WizardShazamEntity spawnNear(ServerPlayer player, double minDist, double maxDist, boolean summoned) {
		ServerLevel level = player.serverLevel();
		for (int tries = 0; tries < 12; tries++) {
			double a = level.random.nextDouble() * Math.PI * 2;
			double d = minDist + level.random.nextDouble() * (maxDist - minDist);
			int x = (int) Math.floor(player.getX() + Math.cos(a) * d);
			int z = (int) Math.floor(player.getZ() + Math.sin(a) * d);
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos pos = new BlockPos(x, y, z);
			if (Math.abs(y - player.getY()) > 12 || !level.getBlockState(pos.below()).isSolid() || !level.getFluidState(pos).isEmpty()) {
				continue;
			}
			WizardShazamEntity w = ShazamEntities.WIZARD.create(level);
			if (w == null) {
				return null;
			}
			w.moveTo(x + 0.5, y, z + 0.5, level.random.nextFloat() * 360f, 0f);
			w.setSummoned(summoned);
			level.addFreshEntity(w);
			Shazam.bolt(level, w.position());
			w.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, player.getEyePosition());
			return w;
		}
		return null;
	}

	/** Once every 30 s per overworld in a thunderstorm: maybe a visit to a player near a village. */
	public static void tickLevel(ServerLevel level) {
		if (level.dimension() != Level.OVERWORLD || !level.isThundering() || level.getGameTime() % ShazamConfig.WIZARD_CHECK_EVERY != 0) {
			return;
		}
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator() || Shazam.hasPower(p) || banned(p) || !level.isCloseToVillage(p.blockPosition(), ShazamConfig.VILLAGE_SECTIONS)) {
				continue;
			}
			trySpawn(p);
		}
	}

	/** One roll for {@code player}: one in 60, and never with another Wizard within 128 blocks. */
	static void trySpawn(ServerPlayer player) {
		ServerLevel level = player.serverLevel();
		if (level.random.nextInt(ShazamConfig.WIZARD_CHANCE) != 0) {
			return;
		}
		if (!level.getEntities(ShazamEntities.WIZARD, player.getBoundingBox().inflate(128), e -> e.isAlive()).isEmpty()) {
			return;
		}
		if (spawnNear(player, 8, 14, false) != null) {
			player.displayClientMessage(Component.translatable("message.projecthero.shazam.wizard.appears").withStyle(ChatFormatting.GOLD), true);
		}
	}
}
