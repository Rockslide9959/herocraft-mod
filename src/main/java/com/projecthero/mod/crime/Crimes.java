package com.projecthero.mod.crime;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.projecthero.mod.event.EventInstance;
import com.projecthero.mod.event.EventManager;
import com.projecthero.mod.event.EventSavedData;
import com.projecthero.mod.event.EventTypes;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

/**
 * v0.16.2: street crime -- the one entry point ({@link #initialize}, called from {@code ProjectHeroMod}): the four
 * event types, the hooks that credit helpers / end a mugging's beating / clean up crooks, the restart clean-up, and
 * {@code /projecthero crime ...}. See {@link CrimeEvent}, {@link CrimeScheduler} and {@code docs/CRIME_REFERENCE.md}.
 */
public final class Crimes {
	/** Crimes never start within this many blocks of another running world event (a raid, a bust, a crime...). */
	static final double EVENT_CLEARANCE = 160.0;

	private Crimes() {
	}

	/** v0.16.2 City part B: told when a crime begins (its crooks have just arrived). City Cops use it to answer the call. */
	@FunctionalInterface
	public interface CrimeListener {
		void onBegin(ServerLevel level, CrimeEvent crime);
	}

	private static final List<CrimeListener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

	/** Adds a {@link CrimeListener} (e.g. the City Cops). */
	public static void addListener(CrimeListener listener) {
		LISTENERS.add(listener);
	}

	static void fireBegin(ServerLevel level, CrimeEvent crime) {
		for (CrimeListener l : LISTENERS) {
			l.onBegin(level, crime);
		}
	}

	public static void initialize() {
		for (CrimeType type : CrimeType.values()) {
			EventTypes.register(type.typeId(), type::create);
		}
		// helpers: damage dealt to a crook counts toward the reward
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0 && entity.getTags().contains(CrimeAI.CROOK_TAG) && source.getEntity() instanceof ServerPlayer p
					&& EventManager.owning(entity) instanceof CrimeEvent crime) {
				crime.addContribution(p.getUUID(), taken);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Mob mob && entity.getTags().contains(CrimeAI.CROOK_TAG) && entity.level() instanceof ServerLevel level
					&& EventManager.owning(entity) instanceof CrimeEvent crime && crime.state().active()) {
				crime.crookDown(level, mob, source);
			}
		});
		// v0.16.2: crimes are for players to stop -- the village's iron golems leave crime crooks alone (in a village
		// with a golem it otherwise flattened every crook before anyone could get there)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.getEntity() instanceof IronGolem golem && entity.getTags().contains(CrimeAI.CROOK_TAG)) {
				golem.setTarget(null);
				return false;
			}
			return true;
		});
		// a mugging victim's beating is a hit counter, never real damage from the crooks
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof Villager v && v.getTags().contains(CrimeAI.VICTIM_TAG) && v.level() instanceof ServerLevel level) {
				MuggingCrime crime = muggingOf(level, v.getUUID());
				if (crime != null) {
					return crime.onVictimHurt(level, v, source);
				}
			}
			return true;
		});
		// a crook whose chunk reloads gets his role back -- or, if his crime is over, is removed (no orphans)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Mob mob && entity.getTags().contains(CrimeAI.CROOK_TAG)) {
				if (EventManager.owning(entity) instanceof CrimeEvent crime && crime.state().active()) {
					crime.reapply(mob);
				} else {
					entity.discard();
				}
			} else if (entity instanceof Villager v && v.getTags().contains(CrimeAI.VICTIM_TAG) && muggingOf(level, v.getUUID()) == null) {
				v.removeTag(CrimeAI.VICTIM_TAG);
			}
		});
		// a crime is a few minutes long: one that was running when the server stopped is simply over
		ServerLifecycleEvents.SERVER_STARTED.register(Crimes::endAll);
	}

	private static MuggingCrime muggingOf(ServerLevel level, UUID victim) {
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e instanceof MuggingCrime m && m.state().active() && victim.equals(m.victimId())) {
				return m;
			}
		}
		return null;
	}

	/** All running crimes. */
	public static List<CrimeEvent> active(MinecraftServer server) {
		List<CrimeEvent> out = new ArrayList<>();
		for (EventInstance e : EventManager.active(server)) {
			if (e instanceof CrimeEvent c) {
				out.add(c);
			}
		}
		return out;
	}

	/** Ends every running crime (crooks vanish, fires go out, victims are released). Returns how many. */
	public static int endAll(MinecraftServer server) {
		int n = 0;
		for (CrimeEvent c : active(server)) {
			ServerLevel level = c.dimension() == null ? null : server.getLevel(c.dimension());
			if (level == null) {
				continue;
			}
			c.abort(level);
			EventSavedData.get(level).remove(c.id());
			n++;
		}
		return n;
	}

	/** True if any running world event (of any kind) is within {@link #EVENT_CLEARANCE} of {@code pos}. */
	static boolean otherEventNear(ServerLevel level, BlockPos pos) {
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e.state().active() && e.isAt(level, pos, EVENT_CLEARANCE)) {
				return true;
			}
		}
		return false;
	}

	static boolean crimeNear(ServerLevel level, BlockPos pos, double within) {
		for (EventInstance e : EventSavedData.get(level).events()) {
			if (e instanceof CrimeEvent && e.state().active() && e.isAt(level, pos, within)) {
				return true;
			}
		}
		return false;
	}

	private static List<Villager> villagersNear(ServerLevel level, BlockPos pos) {
		return level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(32.0), v -> v.isAlive() && !v.isBaby() && !v.isSleeping());
	}

	/** A weighted pick of the kinds that can happen at {@code bell} (a mugging needs a villager about). */
	static CrimeType pickType(ServerLevel level, BlockPos bell) {
		boolean villagers = !villagersNear(level, bell).isEmpty();
		int total = 0;
		for (CrimeType t : CrimeType.values()) {
			if (t != CrimeType.MUGGING || villagers) {
				total += t.weight();
			}
		}
		if (total <= 0) {
			return null;
		}
		int roll = level.random.nextInt(total);
		for (CrimeType t : CrimeType.values()) {
			if (t == CrimeType.MUGGING && !villagers) {
				continue;
			}
			roll -= t.weight();
			if (roll < 0) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Starts a crime of {@code type} in the village around {@code centre}. A mugging picks a grown villager within 32
	 * blocks (or fails). {@code checkSpacing} keeps it {@code trigger.minDistanceBetweenCrimes} from any other crime.
	 * The crooks arrive on the crime's first step (the next event tick with a player around).
	 */
	public static CrimeEvent start(ServerLevel level, CrimeType type, BlockPos centre, boolean hasBell, boolean checkSpacing) {
		return start(level, type, centre, hasBell, checkSpacing, null);
	}

	public static CrimeEvent start(ServerLevel level, CrimeType type, BlockPos centre, boolean hasBell, boolean checkSpacing, Villager victim) {
		if (checkSpacing && crimeNear(level, centre, CrimeConfig.trigger().minDistanceBetweenCrimes)) {
			return null;
		}
		CrimeEvent crime = type.create(UUID.randomUUID());
		crime.setVillage(centre, hasBell);
		BlockPos scene = centre;
		if (crime instanceof MuggingCrime mugging) {
			if (victim == null) {
				List<Villager> near = villagersNear(level, centre);
				victim = near.isEmpty() ? null : near.get(level.random.nextInt(near.size()));
			}
			if (victim == null) {
				return null;
			}
			mugging.setVictim(victim);
			scene = victim.blockPosition();
		}
		crime.placeAt(level, scene);
		EventSavedData.get(level).add(crime);
		return crime;
	}

	// ---------------------------------------------------------------- /projecthero crime

	public static LiteralArgumentBuilder<CommandSourceStack> buildCommand() {
		List<String> ids = new ArrayList<>();
		for (CrimeType t : CrimeType.values()) {
			ids.add(t.id());
		}
		return Commands.literal("crime")
				.requires(s -> s.hasPermission(2))
				.then(Commands.literal("start")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((c, b) -> SharedSuggestionProvider.suggest(ids, b))
								.executes(Crimes::commandStart)))
				.then(Commands.literal("end").executes(c -> {
					int n = endAll(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.literal("Ended " + n + " crime(s)."), true);
					return n;
				}))
				.then(Commands.literal("trigger").executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					CrimeEvent crime = CrimeScheduler.tryStartNear(player.serverLevel(), player, true);
					if (crime == null) {
						c.getSource().sendFailure(Component.literal("No crime could start (needs a village bell within "
								+ CrimeConfig.trigger().villageSearchRadius + " blocks, not Peaceful, no other event nearby)."));
						return 0;
					}
					c.getSource().sendSuccess(() -> Component.literal("Started a natural " + crime.crimeType().id() + " in "
							+ crime.villageName() + "."), true);
					return 1;
				}))
				.then(Commands.literal("status").executes(c -> {
					MinecraftServer server = c.getSource().getServer();
					List<CrimeEvent> list = active(server);
					c.getSource().sendSuccess(() -> Component.literal(list.size() + " crime(s) running."), false);
					for (CrimeEvent e : list) {
						BlockPos p = e.center();
						c.getSource().sendSuccess(() -> Component.literal(" - " + e.crimeType().id() + " at " + e.villageName() + " ["
								+ p.getX() + ", " + p.getY() + ", " + p.getZ() + "] " + e.state() + ", " + e.runTicks() / 20 + " s"), false);
					}
					return list.size();
				}));
	}

	private static int commandStart(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		ServerLevel level = player.serverLevel();
		CrimeType type = CrimeType.byId(StringArgumentType.getString(c, "type"));
		if (type == null) {
			c.getSource().sendFailure(Component.literal("Unknown crime (mugging, break_in, getaway, arson)."));
			return 0;
		}
		Optional<BlockPos> bell = CrimeVillages.findBell(level, player.blockPosition(), CrimeConfig.trigger().villageSearchRadius);
		BlockPos centre = bell.orElse(player.blockPosition().relative(player.getDirection(), 10));
		Villager victim = null;
		if (type == CrimeType.MUGGING) {
			List<Villager> near = villagersNear(level, centre);
			if (near.isEmpty()) {
				// no villager about: bring one, a few blocks in front of you
				BlockPos at = CrimeVillages.spotNear(level, player.blockPosition().relative(player.getDirection(), 8), 0, 3, level.random);
				victim = EntityType.VILLAGER.create(level);
				if (victim != null) {
					BlockPos p = at == null ? player.blockPosition().relative(player.getDirection(), 4) : at;
					victim.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
					victim.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.COMMAND, null);
					level.addFreshEntity(victim);
				}
			}
		}
		CrimeEvent crime = start(level, type, centre, bell.isPresent(), false, victim);
		if (crime == null) {
			c.getSource().sendFailure(Component.literal("Could not start that crime here."));
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.literal("Started a " + type.id() + " in " + crime.villageName()
				+ (bell.isPresent() ? "." : " (no village bell nearby -- using the spot in front of you).")), true);
		return 1;
	}
}
