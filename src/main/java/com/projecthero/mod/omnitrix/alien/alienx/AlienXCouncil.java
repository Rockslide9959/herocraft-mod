package com.projecthero.mod.omnitrix.alien.alienx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.omnitrix.Omnitrix;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.data.OmnitrixState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "when the player transforms into alien x i want his body to remain in place ... but the player gets transported
 * to that dimension where he talks to the two heads, make a good debating system where the player can do many many many things,
 * make it work well and be intuitive please"; "Alien X can one shot anything but the new system wont allow him to just go and
 * hit things"; "it should be difficult to use him").
 *
 * <h2>The Celestial Council</h2>
 * v0.17.2 round 4 (user: "change the alien x council thing to be the mind palace thing that the hulk has, i also want a screen where
 * the player can see a third person view of themselves and even turn and look around if they want to please"). Transforming leaves
 * Alien X's body ({@link AlienXBodyEntity}, 2.5 blocks tall, impervious, frozen) standing in the world, and the player STAYS in the
 * world too: a client can only draw the level its player stands in, and the hall's giant screen shows that level, live. He is held
 * exactly at his body ({@link #hold}), hidden from everyone ({@link AlienXKit#inHall}: drawn by nobody, no hitbox, no pushing, mobs
 * lose him, he picks nothing up, and nothing he clicks reaches the world), and the hall itself is an illusion drawn over his screen
 * by his own client ({@code AlienXHall}). Everything the old dimension gave for free is therefore done here: his own presence keeps
 * the chunks loaded, a body that goes missing is put back ({@link #spawnBody}), and a session is memory only (a logout ends it and
 * turns him back where he stands; a crash leaves him Alien X, and the kit simply opens a new session).
 *
 * <h3>The old dimension</h3>
 * Until v0.17.2 round 4 he was really sent to {@code projecthero:celestial_council}, with where he came from saved on him
 * ({@link #RETURN}). Nothing sends anyone there now, but a player may still be SAVED in it, so its data files, {@link #DIMENSION}, the
 * saved return and the half-second sweep ({@link #check}) stay: he is sent back to that spot, or to spawn.
 *
 * <h2>The debate</h2>
 * The council screen ({@code AlienXCouncilScreen}, reached by walking up to the heads in the hall): pick a <b>category</b>, an <b>action</b> ({@link AlienXActions})
 * and a <b>target</b> from what is around his body (creatures and players with health and distance, "where my body looks", the
 * area round the body, the body itself), then <b>propose</b> it. Each head has a persuasion meter (starting where the action
 * leans them) and both must reach {@link #NEED} within {@link #TURNS} turns. Each turn Ben picks one of {@link #OFFERS}
 * offered <b>arguments</b>, each in a tone ({@link #TONES}): Bellicus likes force, glory and resolve; Serena mercy, balance,
 * protection, wisdom and humility ({@link #BELLICUS_PREF} / {@link #SERENA_PREF}). Arguing a tone the action contradicts
 * (mercy for a killing) backfires on both; contradicting himself (force after mercy, glory after humility) costs both; a
 * repeated tone is worth half each time; each head has a hidden <b>concern</b> tone worth double, which <b>Listen</b> (a turn)
 * reveals. Carried: the action happens at his body, and the result is read out. Refused: that category rests
 * {@link #REFUSED_COOLDOWN}. Everything the client sends is validated here.
 */
public final class AlienXCouncil {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("celestial_council"));

	// ---------------------------------------------------------------- the debate's rules
	public static final List<String> TONES = List.of("force", "glory", "resolve", "protection", "mercy", "balance", "wisdom", "humility");
	public static final int[] BELLICUS_PREF = { 35, 30, 25, 15, -20, -5, 5, -15 };
	public static final int[] SERENA_PREF = { -20, -15, -5, 25, 35, 30, 20, 20 };
	private static final int[] BELLICUS_LIKES = { 0, 1, 2, 3 }, SERENA_LIKES = { 3, 4, 5, 6, 7 };
	/** Self-contradicting pairs: force / mercy, glory / humility. */
	private static final int[][] OPPOSED = { { 0, 4 }, { 1, 7 } };
	// v0.17.2 (user: "right now its too difficult to get both heads to 100, maybe make it like 15% easier please"): NEED was 100
	public static final int TURNS = 7, OFFERS = 5, NEED = 85, METER_FLOOR = -50, CONTRA_PENALTY = 15, FLIPFLOP_PENALTY = 10;
	public static final float REPEAT_FACTOR = 0.5f, CONCERN_FACTOR = 2f;
	public static final int REFUSED_COOLDOWN = 400;
	public static final int PHASE_PROPOSE = 0, PHASE_DEBATE = 1, PHASE_RESULT = 2;
	public static final int WHO_BEN = 0, WHO_SERENA = 1, WHO_BELLICUS = 2, WHO_COUNCIL = 3;
	/** How many lines each reaction has in the lang file (projecthero.omnitrix.alien_x.council.<head>.<kind>.N). */
	private static final Map<String, Integer> LINES = Map.ofEntries(Map.entry("pleased", 4), Map.entry("warm", 3), Map.entry("cold", 4),
			Map.entry("repeat", 3), Map.entry("contra", 3), Map.entry("flipflop", 2), Map.entry("concern", 3), Map.entry("carried", 3),
			Map.entry("refused", 3), Map.entry("keen", 2), Map.entry("wary", 2), Map.entry("against", 2), Map.entry("welcome", 2),
			Map.entry("listen", 2));
	public static final int BEN_LINES = 3, TRANSCRIPT = 14;

	// ---------------------------------------------------------------- the saved return point (the old dimension only)
	public record Return(String dimension, double x, double y, double z, float yRot, float xRot) {
		public static final Codec<Return> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dimension").forGetter(Return::dimension),
				Codec.DOUBLE.fieldOf("x").forGetter(Return::x),
				Codec.DOUBLE.fieldOf("y").forGetter(Return::y),
				Codec.DOUBLE.fieldOf("z").forGetter(Return::z),
				Codec.FLOAT.fieldOf("yRot").forGetter(Return::yRot),
				Codec.FLOAT.fieldOf("xRot").forGetter(Return::xRot)).apply(i, Return::new));
	}

	public static final AttachmentType<Return> RETURN = AttachmentRegistry.create(ProjectHeroMod.id("alien_x_council_return"),
			b -> b.persistent(Return.CODEC));

	// ---------------------------------------------------------------- sessions

	record Line(int who, String key, String[] args) {
	}

	static final class Debate {
		final AlienXActions.Action action;
		final int target;
		final String targetName;
		float bellicus, serena;
		int turn;
		int[] offered = new int[0];
		final int concernB, concernS;
		boolean listened;
		final List<Integer> used = new ArrayList<>();

		Debate(AlienXActions.Action action, int target, String targetName, int concernB, int concernS) {
			this.action = action;
			this.target = target;
			this.targetName = targetName;
			this.bellicus = action.bellicus();
			this.serena = action.serena();
			this.concernB = concernB;
			this.concernS = concernS;
		}
	}

	static final class Session {
		final UUID player;
		UUID body;
		ResourceKey<Level> bodyDimension;
		int phase = PHASE_PROPOSE;
		@Nullable
		Debate debate;
		boolean lastCarried;
		/** v0.17.2: the "Let Me Go" motion was carried -- he turns back at this tick (0 = not leaving). */
		long releaseAt;
		boolean watching;
		final Map<String, Long> readyAt = new HashMap<>();
		final List<Line> transcript = new ArrayList<>();

		Session(UUID player) {
			this.player = player;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** Tests: players whose arguments need not be among the offered ones, and forced concerns. */
	private static final Set<UUID> FREE_TONES = ConcurrentHashMap.newKeySet();
	private static final Map<UUID, int[]> FORCED_CONCERNS = new ConcurrentHashMap<>();
	private static boolean initialized;

	private AlienXCouncil() {
	}

	/** From {@link AlienXKit#register} (mod init). */
	static void initialize() {
		if (initialized) {
			return;
		}
		initialized = true;
		AlienXBodyEntity.initialize();
		PayloadTypeRegistry.playC2S().register(AlienXCouncilPayload.TYPE, AlienXCouncilPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(AlienXCouncilStatePayload.TYPE, AlienXCouncilStatePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(AlienXCouncilPayload.TYPE, (payload, context) -> handle(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(AlienXCouncil::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> AlienXActions.tick(server, true));
		// a logout ends the session (the body goes), and he is saved where he stands: at his body. (Registered before OmnitrixMod's
		// own logout hook, so the revert that follows finds no session and moves nothing mid-disconnect.)
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			if (handler.getPlayer() != null) {
				onDisconnect(handler.getPlayer());
			}
		});
		// he is in the hall, not in the world: nothing he clicks reaches it, whatever a client sends
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> inSession(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> inSession(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> inSession(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> inSession(player) ? InteractionResultHolder.fail(player.getItemInHand(hand))
				: InteractionResultHolder.pass(player.getItemInHand(hand)));
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !inSession(player));
	}

	public static void clearSessionState() {
		SESSIONS.clear();
		FREE_TONES.clear();
		FORCED_CONCERNS.clear();
		AlienXActions.clearSessionState();
	}

	// ---------------------------------------------------------------- queries

	public static boolean inSession(Player p) {
		return SESSIONS.containsKey(p.getUUID());
	}

	/**
	 * Game tests only: the game-test server has no datapack dimensions, so the rescue from the old dimension is tested through a
	 * stand-in level (the Nether). Null in play.
	 */
	@Nullable
	private static ResourceKey<Level> testCouncil;

	public static void useTestCouncil(@Nullable ResourceKey<Level> stand) {
		testCouncil = stand;
	}

	/** Whether {@code level} is the council (or the tests' stand-in for it). */
	public static boolean isCouncil(Level level) {
		return level.dimension() == DIMENSION || testCouncil != null && level.dimension() == testCouncil;
	}

	public static boolean bodyLive(@Nullable UUID owner, UUID body) {
		Session s = owner == null ? null : SESSIONS.get(owner);
		return s != null && body.equals(s.body);
	}

	@Nullable
	public static AlienXBodyEntity body(MinecraftServer server, Player p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.body == null || s.bodyDimension == null) {
			return null;
		}
		ServerLevel level = server.getLevel(s.bodyDimension);
		return level != null && level.getEntity(s.body) instanceof AlienXBodyEntity b ? b : null;
	}

	/** Where the motions happen: his body, or Ben himself in the moment before a lost body is put back. */
	static LivingEntity origin(ServerPlayer p) {
		AlienXBodyEntity b = p.getServer() == null ? null : body(p.getServer(), p);
		return b != null && b.isAlive() ? b : p;
	}

	public static int phase(Player p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null ? -1 : s.phase;
	}

	/** {bellicus, serena, turn} of the running debate, or null (tests). */
	@Nullable
	public static float[] meters(Player p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.debate == null ? null : new float[] { s.debate.bellicus, s.debate.serena, s.debate.turn };
	}

	public static boolean lastCarried(Player p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.lastCarried;
	}

	/** Ticks until {@code key} ("cat:<category>" or an action id) is ready for {@code p}, 0 if it is. */
	public static int cooldownLeft(ServerPlayer p, String key) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return 0;
		}
		long now = now(p);
		return (int) Math.max(0L, s.readyAt.getOrDefault(key, 0L) - now);
	}

	private static long now(ServerPlayer p) {
		return p.getServer() != null ? p.getServer().overworld().getGameTime() : p.level().getGameTime();
	}

	/** Tests: {@code p}'s arguments may be any tone. */
	public static void freeTones(ServerPlayer p) {
		FREE_TONES.add(p.getUUID());
	}

	/** Tests: the next debate's concerns (tone indices). */
	public static void forceConcerns(ServerPlayer p, int bellicus, int serena) {
		FORCED_CONCERNS.put(p.getUUID(), new int[] { bellicus, serena });
	}

	// ---------------------------------------------------------------- going in

	/**
	 * He just became Alien X: the body stays standing here and he goes into the hall. He never leaves the level: {@link #tick} holds
	 * him at the body, and his client draws the hall as soon as the synced flag ({@link AlienXKit#inHall}) is up.
	 */
	public static void enter(ServerPlayer p) {
		if (SESSIONS.containsKey(p.getUUID()) || p.getServer() == null) {
			return;
		}
		Session s = new Session(p.getUUID());
		SESSIONS.put(p.getUUID(), s);
		say(s, WHO_SERENA, "council.serena.welcome." + (1 + p.getRandom().nextInt(LINES.get("welcome"))));
		say(s, WHO_BELLICUS, "council.bellicus.welcome." + (1 + p.getRandom().nextInt(LINES.get("welcome"))));
		p.stopRiding();
		p.ejectPassengers();
		spawnBody(p, s);
		p.setDeltaMovement(Vec3.ZERO);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(AlienXKit.ID + "." + AlienXKit.T_HALL, 1L);
		Omnitrix.save(p, n);
		p.displayClientMessage(Component.translatable("message.projecthero.omnitrix.alien_x.council.enter").withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC),
				false);
	}

	/** A body for this session, standing where he stands, looking where he looks (the first one, or one to replace a lost one). */
	private static void spawnBody(ServerPlayer p, Session s) {
		ServerLevel home = p.serverLevel();
		AlienXBodyEntity body = AlienXBodyEntity.TYPE.create(home);
		if (body == null) {
			return;
		}
		body.setOwner(p.getUUID());
		body.moveTo(p.getX(), p.getY(), p.getZ(), p.getYRot(), Mth.clamp(p.getXRot(), -30f, 30f));
		body.yBodyRot = p.getYRot();
		body.yBodyRotO = p.getYRot();
		body.yHeadRot = p.getYRot();
		body.yHeadRotO = p.getYRot();
		s.body = body.getUUID();
		s.bodyDimension = home.dimension();
		home.addFreshEntity(body);
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(AlienXKit.ID + "." + AlienXKit.T_BODY, (long) body.getId());
		Omnitrix.save(p, n);
	}

	/**
	 * Every tick of a session: he stays exactly where his body is (it may settle onto the ground, or step through reality when "Move
	 * My Body" is carried; his chunks and his view go with it), wherever anything else tries to send him. A body that has gone
	 * (nothing in the game should do that, but it is what the whole hall looks at) is put back where he stands.
	 */
	private static void hold(MinecraftServer server, ServerPlayer p, Session s) {
		AlienXBodyEntity body = body(server, p);
		if (body == null || !body.isAlive()) {
			if (!isCouncil(p.level())) {
				spawnBody(p, s);
			}
			return;
		}
		p.setPortalCooldown();
		if (p.level() != body.level()) {
			p.teleportTo((ServerLevel) body.level(), body.getX(), body.getY(), body.getZ(), p.getYRot(), p.getXRot());
		} else if (p.distanceToSqr(body) > HOLD_SLACK * HOLD_SLACK) {
			p.teleportTo(body.getX(), body.getY(), body.getZ());
			p.setDeltaMovement(Vec3.ZERO);
			p.resetFallDistance();
		}
	}

	// ---------------------------------------------------------------- coming back

	/**
	 * He is turning back (N, "Leave the Council", a switch, death...): the hall goes, and so does the body, leaving him standing
	 * exactly where it stood. Dead -- nothing but the clean-up (he respawns as usual).
	 */
	public static void leave(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		MinecraftServer server = p.getServer();
		if (s == null || server == null) {
			if (s != null) {
				SESSIONS.remove(p.getUUID());
			}
			clearHall(p);
			return;
		}
		AlienXBodyEntity body = body(server, p);
		ServerLevel level = body != null && body.isAlive() ? (ServerLevel) body.level() : null;
		Vec3 at = level != null ? body.position() : null;
		float yRot = level != null ? body.getYRot() : p.getYRot();
		end(server, s);
		clearHall(p);
		if (at != null && !p.isDeadOrDying() && !p.isRemoved() && (p.level() != level || p.position().distanceToSqr(at) > 1.0e-3)) {
			p.teleportTo(level, at.x, at.y, at.z, yRot, p.getXRot());
			p.resetFallDistance();
			p.setDeltaMovement(Vec3.ZERO);
		}
	}

	private static void clearHall(ServerPlayer p) {
		if (Omnitrix.state(p).readyAt(AlienXKit.ID + "." + AlienXKit.T_HALL) != 0L) {
			OmnitrixState n = Omnitrix.state(p).copy();
			n.timers.remove(AlienXKit.ID + "." + AlienXKit.T_HALL);
			n.timers.remove(AlienXKit.ID + "." + AlienXKit.T_BODY);
			Omnitrix.save(p, n);
		}
	}

	private static ServerLevel savedLevel(MinecraftServer server, @Nullable Return saved) {
		ServerLevel level = null;
		if (saved != null) {
			ResourceLocation id = ResourceLocation.tryParse(saved.dimension());
			level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		return level == null || isCouncil(level) ? server.overworld() : level;
	}

	private static Vec3 savedPos(ServerLevel level, @Nullable Return saved) {
		if (saved != null && level.dimension().location().toString().equals(saved.dimension())) {
			return new Vec3(saved.x(), saved.y(), saved.z());
		}
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/** Ends a session without moving anyone: the body goes. */
	private static void end(MinecraftServer server, Session s) {
		SESSIONS.remove(s.player);
		if (s.bodyDimension != null && s.body != null) {
			ServerLevel home = server.getLevel(s.bodyDimension);
			Entity body = home == null ? null : home.getEntity(s.body);
			if (body != null) {
				body.discard();
			}
		}
	}

	/** Logging out mid-session: the session (and the body) ends, and he is saved where he stands, at his body. */
	public static void onDisconnect(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && p.getServer() != null) {
			end(p.getServer(), s);
			clearHall(p);
		}
	}

	/** The arguments on offer this turn (tests), or an empty array. */
	public static int[] offered(Player p) {
		Session s = SESSIONS.get(p.getUUID());
		return s == null || s.debate == null ? new int[0] : s.debate.offered.clone();
	}

	/**
	 * The old dimension (nothing sends anyone there since v0.17.2 round 4): a player found in it -- saved there by an older version,
	 * or dropped there by a command -- goes back to the saved spot, or to spawn if nothing was saved. Anywhere else a leftover
	 * return is just dropped. Before any session, so a save that was mid-transformation gets out too ({@code AlienXKit.tick} waits
	 * for this before it opens one).
	 */
	public static void check(ServerPlayer p) {
		if (p.getServer() == null) {
			return;
		}
		Return saved = p.getAttachedOrElse(RETURN, null);
		if (isCouncil(p.level())) {
			ServerLevel level = savedLevel(p.getServer(), saved);
			Vec3 at = savedPos(level, saved);
			p.removeAttached(RETURN);
			p.teleportTo(level, at.x, at.y, at.z, saved == null ? p.getYRot() : saved.yRot(), saved == null ? 0f : saved.xRot());
			p.resetFallDistance();
			p.setDeltaMovement(Vec3.ZERO);
		} else if (saved != null) {
			p.removeAttached(RETURN);
		}
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		AlienXActions.tick(server, false);
		long now = server.overworld().getGameTime();
		for (Session s : SESSIONS.values()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player);
			if (p == null) {
				end(server, s);
				continue;
			}
			if (!AlienXKit.ID.equals(Omnitrix.state(p).active) || !Omnitrix.state(p).transformed()) {
				leave(p); // no longer Alien X (and the kit did not say): home
				continue;
			}
			if (s.releaseAt > 0 && now >= s.releaseAt) {
				s.releaseAt = 0;
				Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V); // the council let him go
				continue;
			}
			hold(server, p, s);
			if (s.watching && s.phase == PHASE_PROPOSE && now % 20L == 0L) {
				send(p, s, false); // the live list of what is round his body
			}
		}
		if (now % 10L == 0L) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				check(p);
			}
		}
	}

	// ---------------------------------------------------------------- the screen's requests

	static void handle(ServerPlayer p, AlienXCouncilPayload msg) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return;
		}
		switch (msg.op()) {
			case AlienXCouncilPayload.OP_OPEN -> open(p);
			case AlienXCouncilPayload.OP_CLOSE -> s.watching = false;
			case AlienXCouncilPayload.OP_PROPOSE -> propose(p, msg.action(), msg.value());
			case AlienXCouncilPayload.OP_ARGUE -> argue(p, msg.value());
			case AlienXCouncilPayload.OP_LISTEN -> listen(p);
			case AlienXCouncilPayload.OP_WITHDRAW -> withdraw(p);
			case AlienXCouncilPayload.OP_BACK -> {
				if (s.phase == PHASE_RESULT) {
					s.phase = PHASE_PROPOSE;
					send(p, s, false);
				}
			}
			case AlienXCouncilPayload.OP_LEAVE -> requestRelease(p);
			default -> {
			}
		}
	}

	/** How far from his body he may drift before he is put back (blocks): his own client walks him nowhere. */
	private static final double HOLD_SLACK = 0.6;

	/** Ticks between the "Let Me Go" motion being carried and him turning back (the banner shows first). */
	public static final int RELEASE_DELAY = 30;

	/**
	 * v0.17.2 (user: "when detransforming as alien x make it so that the player has to convince the heads to let them detransform
	 * aswell please"): N, V, the alien wheel and "Leave the Council" no longer turn him back -- they move the "Let Me Go" motion
	 * (target: his body) and open the screen; both heads must agree like any other motion. Mid-debate on something else, the
	 * screen just opens (withdraw first). With no council session or no body to aim it at he turns back at once, so he can never
	 * be stuck; death, logout, the watch coming off and /kill still end it outright.
	 */
	public static void requestRelease(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V);
			return;
		}
		if (s.releaseAt > 0) {
			return; // already let go
		}
		if (s.phase == PHASE_DEBATE) {
			if (s.debate == null || !s.debate.action.id().equals("release")) {
				say(s, WHO_COUNCIL, "council.release_busy");
			}
			open(p);
			return;
		}
		if (!(origin(p) instanceof AlienXBodyEntity)) {
			Omnitrix.revert(p, Omnitrix.Reason.SHIFT_V); // no body to return through: never trap him
			return;
		}
		propose(p, "release", AlienXActions.T_BODY);
		open(p);
	}

	/** v0.17.2: he is leaving (the "Let Me Go" motion carried, waiting out the banner). */
	public static boolean releasing(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.releaseAt > 0;
	}

	/** At the heads (or the screen asking): the screen opens with the current state. */
	public static void open(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.watching = true;
			send(p, s, true);
		}
	}

	/** Ben moves a motion: {@code actionId} on {@code target}. False (with a reason line) if it is not allowed. */
	public static boolean propose(ServerPlayer p, String actionId, int target) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.phase == PHASE_DEBATE) {
			return false;
		}
		AlienXActions.Action a = AlienXActions.get(actionId);
		if (a == null) {
			return false;
		}
		s.phase = PHASE_PROPOSE;
		if (cooldownLeft(p, "cat:" + a.category()) > 0 || cooldownLeft(p, a.id()) > 0) {
			say(s, WHO_COUNCIL, "council.resting");
			send(p, s, false);
			return false;
		}
		LivingEntity origin = origin(p);
		Entity te = target >= 0 ? origin.level().getEntity(target) : null;
		int kind = AlienXActions.kindOf(target, te);
		boolean ok = kind != 0 && a.takes(kind);
		if (ok && target >= 0) {
			ok = te instanceof LivingEntity le && le.isAlive() && le != p && le != origin && !AlienXKit.inHall(le) && le.level() == origin.level()
					&& le.distanceTo(origin) <= AlienXActions.RANGE;
		}
		if (ok && target == AlienXActions.T_BODY) {
			ok = origin instanceof AlienXBodyEntity;
		}
		if (!ok) {
			say(s, WHO_COUNCIL, "council.bad_target");
			send(p, s, false);
			return false;
		}
		RandomSource r = p.getRandom();
		int[] forced = FORCED_CONCERNS.remove(p.getUUID());
		int cb = forced != null ? forced[0] : pick(r, BELLICUS_LIKES, a.contra());
		int cs = forced != null ? forced[1] : pick(r, SERENA_LIKES, a.contra());
		String tname = target >= 0 ? te.getDisplayName().getString() : "#projecthero.omnitrix.alien_x.council.target." + specialName(target);
		Debate d = new Debate(a, target, tname, cb, cs);
		s.debate = d;
		s.phase = PHASE_DEBATE;
		s.transcript.clear();
		say(s, WHO_BEN, "council.ben.propose", "#" + actionKey(a.id()), tname);
		say(s, WHO_SERENA, "council.serena." + leaning(d.serena) + "." + (1 + r.nextInt(LINES.get(leaning(d.serena)))));
		say(s, WHO_BELLICUS, "council.bellicus." + leaning(d.bellicus) + "." + (1 + r.nextInt(LINES.get(leaning(d.bellicus)))));
		offer(p, d);
		speak(p, d.bellicus < d.serena ? WHO_BELLICUS : WHO_SERENA);
		send(p, s, false);
		return true;
	}

	private static String specialName(int target) {
		return switch (target) {
			case AlienXActions.T_AREA -> "area";
			case AlienXActions.T_POINT -> "point";
			default -> "body";
		};
	}

	private static String leaning(float v) {
		return v >= 40 ? "keen" : v >= 20 ? "wary" : "against";
	}

	private static int pick(RandomSource r, int[] from, int contra) {
		List<Integer> ok = new ArrayList<>();
		for (int t : from) {
			if ((contra & (1 << t)) == 0) {
				ok.add(t);
			}
		}
		return ok.isEmpty() ? from[0] : ok.get(r.nextInt(ok.size()));
	}

	/** {@link #OFFERS} different tones for this turn. */
	private static void offer(ServerPlayer p, Debate d) {
		List<Integer> all = new ArrayList<>();
		for (int i = 0; i < TONES.size(); i++) {
			all.add(i);
		}
		java.util.Collections.shuffle(all, new java.util.Random(p.getRandom().nextLong()));
		d.offered = all.subList(0, OFFERS).stream().mapToInt(Integer::intValue).sorted().toArray();
	}

	/** Ben argues in tone {@code tone} (an index into {@link #TONES}; it must be on offer). */
	public static boolean argue(ServerPlayer p, int tone) {
		Session s = SESSIONS.get(p.getUUID());
		Debate d = s == null ? null : s.debate;
		if (d == null || s.phase != PHASE_DEBATE || tone < 0 || tone >= TONES.size()) {
			return false;
		}
		boolean offered = FREE_TONES.contains(p.getUUID());
		for (int t : d.offered) {
			offered |= t == tone;
		}
		if (!offered) {
			return false;
		}
		RandomSource r = p.getRandom();
		say(s, WHO_BEN, "council.ben." + TONES.get(tone) + "." + benLine(d.turn), "#" + actionKey(d.action.id()),
				d.targetName);
		boolean contra = (d.action.contra() & (1 << tone)) != 0;
		boolean flipflop = false;
		for (int[] pair : OPPOSED) {
			int other = pair[0] == tone ? pair[1] : pair[1] == tone ? pair[0] : -1;
			flipflop |= other >= 0 && d.used.contains(other);
		}
		int repeats = (int) d.used.stream().filter(x -> x == tone).count();
		float db, ds;
		if (contra) {
			db = -CONTRA_PENALTY;
			ds = -CONTRA_PENALTY;
		} else {
			db = gain(BELLICUS_PREF[tone], repeats, tone == d.concernB);
			ds = gain(SERENA_PREF[tone], repeats, tone == d.concernS);
			if (flipflop) {
				db -= FLIPFLOP_PENALTY;
				ds -= FLIPFLOP_PENALTY;
			}
		}
		d.used.add(tone);
		d.bellicus = Mth.clamp(d.bellicus + db, METER_FLOOR, NEED);
		d.serena = Mth.clamp(d.serena + ds, METER_FLOOR, NEED);
		d.turn++;
		// the one who cares more answers last (and his head animates)
		boolean bellicusLast = Math.abs(db) >= Math.abs(ds);
		react(s, r, bellicusLast ? WHO_SERENA : WHO_BELLICUS, bellicusLast ? ds : db, contra, flipflop, repeats,
				tone == (bellicusLast ? d.concernS : d.concernB));
		react(s, r, bellicusLast ? WHO_BELLICUS : WHO_SERENA, bellicusLast ? db : ds, contra, flipflop, repeats,
				tone == (bellicusLast ? d.concernB : d.concernS));
		speak(p, bellicusLast ? WHO_BELLICUS : WHO_SERENA);
		conclude(p, s, d);
		return true;
	}

	/** Which of Ben's lines a tone uses on {@code turn} (the screen previews the same one). */
	public static int benLine(int turn) {
		return 1 + Math.floorMod(turn, BEN_LINES);
	}

	private static float gain(int pref, int repeats, boolean concern) {
		if (pref <= 0) {
			return pref;
		}
		float g = pref * (float) Math.pow(REPEAT_FACTOR, repeats);
		return concern ? g * CONCERN_FACTOR : g;
	}

	private static void react(Session s, RandomSource r, int who, float delta, boolean contra, boolean flipflop, int repeats, boolean concern) {
		String kind = contra ? "contra" : flipflop ? "flipflop" : delta > 0 && concern ? "concern" : repeats > 0 && delta > 0 ? "repeat"
				: delta >= 20 ? "pleased" : delta > 0 ? "warm" : "cold";
		String head = who == WHO_SERENA ? "serena" : "bellicus";
		say(s, who, "council." + head + "." + kind + "." + (1 + r.nextInt(LINES.get(kind))));
	}

	/** Listen: a turn spent hearing them out -- both reveal their concern. */
	public static boolean listen(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		Debate d = s == null ? null : s.debate;
		if (d == null || s.phase != PHASE_DEBATE) {
			return false;
		}
		RandomSource r = p.getRandom();
		say(s, WHO_BEN, "council.ben.listen");
		d.listened = true;
		d.turn++;
		say(s, WHO_SERENA, "council.serena.listen." + (1 + r.nextInt(LINES.get("listen"))), "#" + toneKey(d.concernS));
		say(s, WHO_BELLICUS, "council.bellicus.listen." + (1 + r.nextInt(LINES.get("listen"))), "#" + toneKey(d.concernB));
		speak(p, WHO_SERENA);
		conclude(p, s, d);
		return true;
	}

	/** Ben drops the motion: nothing happens, nothing rests. */
	public static void withdraw(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.phase == PHASE_DEBATE) {
			s.debate = null;
			s.phase = PHASE_PROPOSE;
			say(s, WHO_BEN, "council.ben.withdraw");
			send(p, s, false);
		}
	}

	/** After a turn: carried (both convinced), refused (out of turns), or the next offers. */
	private static void conclude(ServerPlayer p, Session s, Debate d) {
		RandomSource r = p.getRandom();
		long now = now(p);
		if (d.bellicus >= NEED && d.serena >= NEED) {
			LivingEntity origin = origin(p);
			AlienXActions.Result res = AlienXActions.execute(p, origin, d.action, d.target);
			say(s, WHO_SERENA, "council.serena.carried." + (1 + r.nextInt(LINES.get("carried"))));
			say(s, WHO_BELLICUS, "council.bellicus.carried." + (1 + r.nextInt(LINES.get("carried"))));
			say(s, WHO_COUNCIL, res.key(), res.args());
			s.lastCarried = res.done();
			if (res.done()) {
				s.readyAt.put(d.action.id(), now + d.action.cooldown());
				if (d.action.id().equals("release")) {
					s.releaseAt = now + RELEASE_DELAY; // the banner shows, then he goes
				}
			}
			OmnitrixSfx.notify(p, AlienXKit.CARRIED, 1.0f, 1.0f);
			s.debate = null;
			s.phase = PHASE_RESULT;
		} else if (d.turn >= TURNS) {
			say(s, d.serena < d.bellicus ? WHO_SERENA : WHO_BELLICUS, "council." + (d.serena < d.bellicus ? "serena" : "bellicus") + ".refused."
					+ (1 + r.nextInt(LINES.get("refused"))));
			say(s, WHO_COUNCIL, "council.result.refused", "#projecthero.omnitrix.alien_x.council.category." + d.action.category());
			s.readyAt.put("cat:" + d.action.category(), now + REFUSED_COOLDOWN);
			s.lastCarried = false;
			OmnitrixSfx.notify(p, AlienXKit.DENIED, 1.0f, 1.0f);
			s.debate = null;
			s.phase = PHASE_RESULT;
		} else {
			offer(p, d);
		}
		send(p, s, false);
	}

	private static void say(Session s, int who, String key, String... args) {
		s.transcript.add(new Line(who, key, args));
		while (s.transcript.size() > TRANSCRIPT) {
			s.transcript.remove(0);
		}
	}

	/** Which head is talking (synced: the client animates it). */
	private static void speak(ServerPlayer p, int who) {
		OmnitrixState n = Omnitrix.state(p).copy();
		n.timers.put(AlienXKit.ID + "." + AlienXKit.T_SPEAK_WHO, (long) who);
		n.timers.put(AlienXKit.ID + "." + AlienXKit.T_SPEAK_AT, p.level().getGameTime());
		Omnitrix.save(p, n);
	}

	public static String actionKey(String id) {
		return "projecthero.omnitrix.alien_x.council.action." + id;
	}

	public static String toneKey(int tone) {
		return "projecthero.omnitrix.alien_x.council.tone." + TONES.get(tone);
	}

	// ---------------------------------------------------------------- the state for the screen

	private static void send(ServerPlayer p, Session s, boolean open) {
		try {
			if (p.connection != null && ServerPlayNetworking.canSend(p, AlienXCouncilStatePayload.TYPE)) {
				ServerPlayNetworking.send(p, new AlienXCouncilStatePayload(open, stateTag(p, s)));
			}
		} catch (RuntimeException e) {
			// a mock player (game tests) has no real connection: nothing to show
		}
	}

	static CompoundTag stateTag(ServerPlayer p, Session s) {
		CompoundTag t = new CompoundTag();
		long now = now(p);
		t.putInt("phase", s.phase);
		t.putBoolean("carried", s.lastCarried);
		LivingEntity origin = origin(p);
		t.putBoolean("body", origin instanceof AlienXBodyEntity);
		// what is round his body
		ListTag targets = new ListTag();
		targets.add(target(AlienXActions.T_AREA, AlienXActions.K_AREA, "#projecthero.omnitrix.alien_x.council.target.area", 0, 0, 0));
		targets.add(target(AlienXActions.T_POINT, AlienXActions.K_POINT, "#projecthero.omnitrix.alien_x.council.target.point", 0, 0,
				(float) (origin instanceof AlienXBodyEntity b ? b.lookEnd(AlienXActions.RANGE) : origin.position()).distanceTo(origin.getEyePosition())));
		if (origin instanceof AlienXBodyEntity) {
			targets.add(target(AlienXActions.T_BODY, AlienXActions.K_BODY, "#projecthero.omnitrix.alien_x.council.target.body", 0, 0, 0));
		}
		List<LivingEntity> near = origin.level().getEntitiesOfClass(LivingEntity.class, origin.getBoundingBox().inflate(AlienXActions.RANGE),
				e -> e != origin && e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && !(e instanceof AlienXBodyEntity)
						&& !AlienXKit.inHall(e) && e.distanceTo(origin) <= AlienXActions.RANGE);
		near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(origin)));
		for (LivingEntity e : near.subList(0, Math.min(16, near.size()))) {
			targets.add(target(e.getId(), e instanceof Player ? AlienXActions.K_PLAYER : AlienXActions.K_LIVING, e.getDisplayName().getString(), e.getHealth(),
					e.getMaxHealth(), e.distanceTo(origin)));
		}
		t.put("targets", targets);
		CompoundTag cds = new CompoundTag();
		for (var e : s.readyAt.entrySet()) {
			long left = e.getValue() - now;
			if (left > 0) {
				cds.putInt(e.getKey(), (int) left);
			}
		}
		t.put("cooldowns", cds);
		if (s.debate != null) {
			Debate d = s.debate;
			CompoundTag dt = new CompoundTag();
			dt.putString("action", d.action.id());
			dt.putString("target", d.targetName);
			dt.putFloat("bellicus", d.bellicus);
			dt.putFloat("serena", d.serena);
			dt.putInt("turn", d.turn);
			dt.putInt("turns", TURNS);
			dt.put("offered", new IntArrayTag(d.offered));
			dt.putInt("concernB", d.listened ? d.concernB : -1);
			dt.putInt("concernS", d.listened ? d.concernS : -1);
			dt.put("used", new IntArrayTag(d.used.stream().mapToInt(Integer::intValue).toArray()));
			t.put("debate", dt);
		}
		ListTag lines = new ListTag();
		for (Line l : s.transcript) {
			CompoundTag lt = new CompoundTag();
			lt.putInt("who", l.who());
			lt.putString("key", l.key());
			ListTag args = new ListTag();
			for (String a : l.args()) {
				args.add(StringTag.valueOf(a));
			}
			lt.put("args", args);
			lines.add(lt);
		}
		t.put("lines", lines);
		return t;
	}

	private static CompoundTag target(int id, int kind, String name, float hp, float max, float dist) {
		CompoundTag c = new CompoundTag();
		c.putInt("id", id);
		c.putInt("kind", kind);
		c.putString("name", name);
		c.putFloat("hp", hp);
		c.putFloat("max", max);
		c.putFloat("dist", dist);
		return c;
	}

}
