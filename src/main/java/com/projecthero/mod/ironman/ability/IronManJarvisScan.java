package com.projecthero.mod.ironman.ability;

import java.util.ArrayList;
import java.util.List;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.suit.IronManSuit;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.27: the Mark III's Sneak+X JARVIS scan -- 20 energy, 3 s cooldown. JARVIS reads back a short, chat-wrapped
 * report of the ~48 blocks around the suit: hostiles (count + the most dangerous one), passive / neutral creatures,
 * other players (hero identity + health), bosses, the structure you are standing in, valuables in the rock nearby,
 * biome / time / weather and the suit's own condition. Chat wraps every line to the chat width, so nothing sprawls.
 */
public final class IronManJarvisScan {
	public static final double RADIUS = 48.0;
	public static final int ORE_RADIUS = 8;
	public static final float ENERGY = 20f;
	public static final int COOLDOWN = 3 * 20;
	/** Anything with at least this much max health (or a vanilla boss) is reported as a boss. */
	public static final float BOSS_HEALTH = 150f;

	private static final String[] COMPASS = { "S", "SW", "W", "NW", "N", "NE", "E", "SE" };

	/** What a scan found -- returned for tests and reused to build the readout. */
	public record Report(int hostiles, int passive, int neutral, LivingEntity topThreat, List<ServerPlayer> players,
			List<LivingEntity> bosses, int diamonds, int emeralds, int debris, List<String> structures) {
	}

	private IronManJarvisScan() {
	}

	/** Sneak+X: pay, cool down, scan, read it out. Returns the report, or null if it did not run. */
	public static Report run(ServerPlayer player, IronManSuit suit) {
		if (!IronManMark3.requireHelmet(player) || !IronManMark3.cooldownReady(player, IronManMark3.JARVIS_SCAN)
				|| !IronManMark3.pay(player, suit, ENERGY)) {
			return null;
		}
		TonyStark.triggerCooldown(player, IronManMark3.SUIT_ID, IronManMark3.JARVIS_SCAN, COOLDOWN);
		Report r = scan(player);
		readOut(player, suit, r);
		AbilityHelpers.sound(player, SoundEvents.BEACON_POWER_SELECT, 0.6f, 1.9f);
		((ServerLevel) player.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
				player.getX(), player.getEyeY(), player.getZ(), 16, 1.5, 0.6, 1.5, 0.02);
		return r;
	}

	/** The scan itself (no cost, no output). */
	public static Report scan(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		int hostiles = 0;
		int passive = 0;
		int neutral = 0;
		LivingEntity top = null;
		double topScore = -1;
		List<ServerPlayer> players = new ArrayList<>();
		List<LivingEntity> bosses = new ArrayList<>();
		double r2 = RADIUS * RADIUS;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS),
				e -> e != player && e.isAlive() && !(e instanceof ArmorStand))) {
			if (e.distanceToSqr(player) > r2) {
				continue;
			}
			if (e instanceof Player p) {
				if (p instanceof ServerPlayer sp && !sp.isSpectator()) {
					players.add(sp);
				}
				continue;
			}
			if (e instanceof WitherBoss || e instanceof EnderDragon || e.getMaxHealth() >= BOSS_HEALTH) {
				bosses.add(e);
			}
			if (e instanceof Enemy) {
				hostiles++;
				double score = threat(e);
				if (score > topScore) {
					topScore = score;
					top = e;
				}
			} else if (e instanceof NeutralMob) {
				neutral++;
			} else {
				passive++;
			}
		}

		int diamonds = 0;
		int emeralds = 0;
		int debris = 0;
		BlockPos c = player.blockPosition();
		for (BlockPos p : BlockPos.betweenClosed(c.offset(-ORE_RADIUS, -ORE_RADIUS, -ORE_RADIUS),
				c.offset(ORE_RADIUS, ORE_RADIUS, ORE_RADIUS))) {
			BlockState st = level.getBlockState(p);
			if (st.is(BlockTags.DIAMOND_ORES)) {
				diamonds++;
			} else if (st.is(BlockTags.EMERALD_ORES)) {
				emeralds++;
			} else if (st.is(Blocks.ANCIENT_DEBRIS)) {
				debris++;
			}
		}

		List<String> structures = new ArrayList<>();
		try {
			var reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
			for (var s : level.structureManager().getAllStructuresAt(c).keySet()) {
				var key = reg.getKey(s);
				if (key != null) {
					structures.add(key.getPath().replace('_', ' '));
				}
			}
		} catch (RuntimeException ignored) {
			// structure lookup is a nicety -- never let it break the scan
		}
		return new Report(hostiles, passive, neutral, top, players, bosses, diamonds, emeralds, debris, structures);
	}

	private static double threat(LivingEntity e) {
		double attack = e.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE) ? e.getAttributeValue(Attributes.ATTACK_DAMAGE) : 0;
		return e.getHealth() + attack * 4.0;
	}

	private static void readOut(ServerPlayer player, IronManSuit suit, Report r) {
		List<Component> lines = new ArrayList<>();
		lines.add(line("header", (int) RADIUS).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		lines.add(line("hostiles", r.hostiles()).withStyle(r.hostiles() > 0 ? ChatFormatting.RED : ChatFormatting.GREEN));
		if (r.topThreat() != null) {
			LivingEntity t = r.topThreat();
			lines.add(line("threat", t.getDisplayName(), hp(t), dist(player, t), dir(player, t)).withStyle(ChatFormatting.RED));
		}
		lines.add(line("creatures", r.passive(), r.neutral()).withStyle(ChatFormatting.GRAY));
		for (LivingEntity b : r.bosses()) {
			lines.add(line("boss", b.getDisplayName(), hp(b), dist(player, b), dir(player, b)).withStyle(ChatFormatting.DARK_RED));
		}
		if (r.players().isEmpty()) {
			lines.add(line("no_players").withStyle(ChatFormatting.GRAY));
		}
		for (ServerPlayer p : r.players()) {
			lines.add(line("player", p.getDisplayName(),
					com.projecthero.mod.squad.HeroIdentity.render(com.projecthero.mod.squad.HeroIdentity.describe(p)),
					hp(p), dist(player, p), dir(player, p)).withStyle(ChatFormatting.YELLOW));
		}
		for (String s : r.structures()) {
			lines.add(line("structure", s).withStyle(ChatFormatting.GOLD));
		}
		if (r.diamonds() + r.emeralds() + r.debris() > 0) {
			lines.add(line("ores", ORE_RADIUS, r.diamonds(), r.emeralds(), r.debris()).withStyle(ChatFormatting.AQUA));
		} else {
			lines.add(line("no_ores", ORE_RADIUS).withStyle(ChatFormatting.GRAY));
		}
		ServerLevel level = (ServerLevel) player.level();
		var biome = level.getBiome(player.blockPosition()).unwrapKey();
		Component biomeName = biome.isPresent()
				? Component.translatable("biome." + biome.get().location().getNamespace() + "." + biome.get().location().getPath())
				: Component.literal("?");
		long tod = ((level.getDayTime() + 6000L) % 24000L + 24000L) % 24000L;
		String clock = String.format(java.util.Locale.ROOT, "%02d:%02d", tod / 1000L, (tod % 1000L) * 60L / 1000L);
		String weather = level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear";
		lines.add(line("env", biomeName, clock, Component.translatable("message.projecthero.ironman.jarvis.weather." + weather))
				.withStyle(ChatFormatting.GRAY));
		String id = suit.id();
		int energyPct = Math.round(100f * IronManEnergy.energy(player, id) / Math.max(1f, IronManEnergy.capacity(id)));
		int integrityPct = Math.round(100f * IronManEnergy.integrity(player, id) / Math.max(1f, IronManEnergy.maxIntegrity(id)));
		lines.add(line("suit", energyPct + "%", integrityPct + "%").withStyle(ChatFormatting.DARK_AQUA));

		MutableComponent tag = Component.literal("[JARVIS] ").withStyle(ChatFormatting.DARK_AQUA);
		for (Component l : lines) {
			player.sendSystemMessage(tag.copy().append(l));
		}
		player.displayClientMessage(line("summary", r.hostiles(), r.players().size(), (int) RADIUS)
				.withStyle(ChatFormatting.AQUA), true);
	}

	private static MutableComponent line(String key, Object... args) {
		return Component.translatable("message.projecthero.ironman.jarvis." + key, args);
	}

	private static String hp(LivingEntity e) {
		return Math.round(e.getHealth()) + "/" + Math.round(e.getMaxHealth());
	}

	private static int dist(ServerPlayer from, LivingEntity e) {
		return (int) Math.round(from.distanceTo(e));
	}

	private static String dir(ServerPlayer from, LivingEntity e) {
		double dx = e.getX() - from.getX();
		double dz = e.getZ() - from.getZ();
		double yaw = Math.toDegrees(Math.atan2(-dx, dz)); // Minecraft yaw: 0 = south, 90 = west
		int i = Math.floorMod((int) Math.round(yaw / 45.0), 8);
		return COMPASS[i];
	}
}
