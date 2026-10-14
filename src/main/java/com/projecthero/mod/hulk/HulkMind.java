package com.projecthero.mod.hulk;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.attachment.ModAttachments;
import com.projecthero.mod.hulk.data.HulkMindReturn;
import com.projecthero.mod.hulk.entity.HulkEntities;
import com.projecthero.mod.hulk.entity.HulkMindBodyEntity;
import com.projecthero.mod.hulk.entity.MirrorHulkEntity;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.joml.Vector3f;

/**
 * v0.16.4 (user): "after the gamma reactor blows up the player gets transported to another dimension where its just a dark
 * room and the player can see the hulk in a mirror, the hulk will mirror the direction that the player is looking in ...
 * the player then has to right click the mirror and the hulk will smash through it and grab the player through the mirror
 * and then the player gets transported back to their original position and then transform, back in the normal world the
 * players body must still be there for other players to see, this place the player transports to is basically their mind".
 *
 * <p>The mind is its own dimension, {@code projecthero:hulk_mind} (datapack JSON: a void, sky-less, mob-less, always-night
 * level). Each visit builds a small dark room on demand at a slot of its own ({@link #SLOT_SPACING} blocks apart along X,
 * so two players never share one): a 9 x 5 x 9 black room with a full-length mirror (3 wide, 4 tall, tinted glass in a
 * polished frame) set in its north wall, and behind the glass the same room again, reflected -- where the
 * {@link MirrorHulkEntity} stands as his reflection. Faint hidden lights keep both just visible.
 *
 * <p>Pressing H in the mind (v0.16.4 user change; it was a right-click on the mirror) starts the smash ({@link MirrorHulkEntity#SMASH_APPROACH_END} ...): the Hulk
 * walks up to the glass, rears back, punches through it (the glass shatters), lunges and grabs him -- his client pulls his
 * view and body into the Hulk's hands and the screen burns white-green -- and {@link MirrorHulkEntity#SMASH_RETURN_AT}
 * ticks in he is sent back to where his body knelt, given a blast shield and changes slowly into the Hulk
 * ({@link GammaOverload#awaken}).
 *
 * <p>While he is away his body ({@link HulkMindBodyEntity}) kneels where he stood, clutching his head, for everyone else
 * to see; its chunk is kept loaded. Where he came from is saved on him ({@link ModAttachments#HULK_MIND_RETURN}), so a
 * crash or relog still brings him home: a player found in the mind with no live session (or anywhere with that saved
 * return and no session) is sent back and the change finishes. Anyone else who ends up in the mind goes to world spawn.
 * Inside it nothing can be built, broken, used or hurt. The live sessions are a static world-object cache, cleared by
 * {@code ServerStateReset} through {@link Hulk#clearSessionState}.
 */
public final class HulkMind {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, ProjectHeroMod.id("hulk_mind"));
	/** Rooms are this many blocks apart along X (slot n at x = n * spacing). */
	public static final int SLOT_SPACING = 2000;
	public static final int FLOOR_Y = 100;
	/** Half the room's inner width (9 wide), its inner depth and height, and the mirror (glass) half width and height. */
	private static final int HALF_W = 4;
	private static final int DEPTH = 9;
	private static final int HEIGHT = 5;
	private static final int GLASS_HALF = 1;
	private static final int GLASS_H = 4;
	/** The mirror plane: the middle of the glass, which sits in the wall at z = origin z. */
	public static final double MIRROR_OFFSET = 0.5;
	/** Where he arrives: 6 blocks in front of the mirror, facing it. */
	private static final double ARRIVE_Z = 6.5;
	/** The mind's chunk ticket on his body, re-issued every second (60-tick lifespan). */
	private static final TicketType<ChunkPos> BODY_TICKET = TicketType.create("projecthero_hulk_mind_body",
			Comparator.comparingLong(ChunkPos::toLong), 60);
	private static final DustParticleOptions GAMMA = new DustParticleOptions(new Vector3f(0.35f, 1.0f, 0.25f), 1.4f);

	private static final class Session {
		final UUID player;
		final int slot;
		final long enteredAt;
		UUID body;
		ResourceKey<Level> bodyDimension;
		UUID hulk;
		/** Game time he pressed H (the Hulk came for him); 0 until then. */
		long smashAt;
		Vec3 hulkFrom = Vec3.ZERO;

		Session(UUID player, int slot, long enteredAt) {
			this.player = player;
			this.slot = slot;
			this.enteredAt = enteredAt;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private HulkMind() {
	}

	public static void clearSessionState() {
		SESSIONS.clear();
	}

	static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(HulkMind::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
		// (no JOIN hook: Hulk.onPlayerJoin runs after it and would undo the change -- the half-second sweep in tick finds a
		// player who logged back in mid-way instead)
		// nothing in the mind can be built, broken or used. v0.16.4 (user): the mirror is not right-clicked any more -- pressing
		// H in the mind brings the Hulk through it (HulkActionPayload MIND_MIRROR)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> level.dimension() == DIMENSION ? InteractionResult.FAIL
				: InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> entity instanceof MirrorHulkEntity ? InteractionResult.FAIL
				: InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> level.dimension() == DIMENSION
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> level.dimension() == DIMENSION
				? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> level.dimension() != DIMENSION);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> entity.level().dimension() != DIMENSION
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
	}

	// ---------------------------------------------------------------- queries

	public static boolean inSession(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	/** True once he has right-clicked the mirror (tests). */
	public static boolean smashing(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s != null && s.smashAt != 0L;
	}

	/** Is {@code body} the body of {@code owner}'s live session? (The body removes itself once it is not.) */
	public static boolean bodyLive(UUID owner, UUID body) {
		Session s = owner == null ? null : SESSIONS.get(owner);
		return s != null && body.equals(s.body);
	}

	/** Is {@code hulk} the mirror Hulk of {@code owner}'s live session? */
	public static boolean hulkLive(UUID owner, UUID hulk) {
		Session s = owner == null ? null : SESSIONS.get(owner);
		return s != null && hulk.equals(s.hulk);
	}

	/** The live session's body entity (tests), or null. */
	public static HulkMindBodyEntity body(MinecraftServer server, ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null || s.body == null) {
			return null;
		}
		ServerLevel level = server.getLevel(s.bodyDimension);
		return level != null && level.getEntity(s.body) instanceof HulkMindBodyEntity b ? b : null;
	}

	public static BlockPos origin(int slot) {
		return new BlockPos(slot * SLOT_SPACING, FLOOR_Y, 0);
	}

	/** The mirror plane (z) of the room at {@code origin}. */
	public static double mirrorZ(BlockPos origin) {
		return origin.getZ() + MIRROR_OFFSET;
	}

	/** One of the mirror's glass blocks? */
	public static boolean isMirrorGlass(BlockPos origin, BlockPos pos) {
		return pos.getZ() == origin.getZ() && Math.abs(pos.getX() - origin.getX()) <= GLASS_HALF
				&& pos.getY() > origin.getY() && pos.getY() <= origin.getY() + GLASS_H;
	}

	private static int freeSlot() {
		for (int i = 0;; i++) {
			boolean used = false;
			for (Session s : SESSIONS.values()) {
				if (s.slot == i) {
					used = true;
					break;
				}
			}
			if (!used) {
				return i;
			}
		}
	}

	// ---------------------------------------------------------------- going in

	/**
	 * The reactor just blew: pull {@code player} into his mind. Saves where he is, leaves his body kneeling there, builds
	 * his room and sends him in, facing the mirror. False (nothing done) if the mind dimension is missing or he is
	 * already in a session.
	 */
	public static boolean enter(ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID()) || player.level().dimension() == DIMENSION) {
			return false;
		}
		MinecraftServer server = player.getServer();
		ServerLevel mind = server == null ? null : server.getLevel(DIMENSION);
		if (mind == null) {
			return false;
		}
		ServerLevel home = player.serverLevel();
		long now = home.getGameTime();
		player.stopRiding();
		player.ejectPassengers();
		// the dose is spent now: the radiation is in him
		GammaOverload.setDosed(player, false);
		player.setAttached(ModAttachments.HULK_MIND_RETURN, new HulkMindReturn(home.dimension().location().toString(),
				player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()));
		Session s = new Session(player.getUUID(), freeSlot(), now);
		SESSIONS.put(player.getUUID(), s);

		// his body stays behind, on its knees
		HulkMindBodyEntity body = HulkEntities.MIND_BODY.create(home);
		if (body != null) {
			body.setOwner(player.getUUID());
			body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0f);
			body.yBodyRot = player.yBodyRot;
			body.yBodyRotO = player.yBodyRot;
			body.yHeadRot = player.yBodyRot;
			body.yHeadRotO = player.yBodyRot;
			s.body = body.getUUID();
			s.bodyDimension = home.dimension();
			home.addFreshEntity(body);
			holdChunk(home, body.blockPosition());
		}

		// his room, the mirror, and the Hulk behind it
		BlockPos o = origin(s.slot);
		buildRoom(mind, o);
		Vec3 arrive = new Vec3(o.getX() + 0.5, o.getY() + 1, o.getZ() + ARRIVE_Z);
		MirrorHulkEntity hulk = HulkEntities.MIRROR_HULK.create(mind);
		if (hulk != null) {
			hulk.setup(player.getUUID(), mirrorZ(o));
			Vec3 at = MirrorHulkEntity.reflect(arrive, mirrorZ(o));
			hulk.moveTo(at.x, at.y, at.z, MirrorHulkEntity.reflectYaw(180.0f), 0.0f);
			hulk.yBodyRot = hulk.getYRot();
			hulk.yHeadRot = hulk.getYRot();
			s.hulk = hulk.getUUID();
			mind.addFreshEntity(hulk);
		}
		player.teleportTo(mind, arrive.x, arrive.y, arrive.z, 180.0f, 0.0f);
		player.fallDistance = 0.0f;
		player.setDeltaMovement(Vec3.ZERO);
		mind.playSound(null, arrive.x, arrive.y, arrive.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.5f, 0.5f);
		mind.playSound(null, arrive.x, arrive.y, arrive.z, SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 1.0f, 0.5f);
		player.displayClientMessage(Component.translatable("message.projecthero.hulk_mind.enter")
				.withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC), false);
		return true;
	}

	/** The dark room and its reflection, rebuilt fresh for every visit. */
	static void buildRoom(ServerLevel level, BlockPos o) {
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockState wall = Blocks.BLACK_CONCRETE.defaultBlockState();
		BlockState floor = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
		BlockState frame = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState glass = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
		int flags = Block.UPDATE_CLIENTS;
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int x = -HALF_W - 1; x <= HALF_W + 1; x++) {
			for (int y = 0; y <= HEIGHT + 1; y++) {
				for (int z = -DEPTH - 1; z <= DEPTH + 1; z++) {
					boolean shell = Math.abs(x) == HALF_W + 1 || y == 0 || y == HEIGHT + 1 || Math.abs(z) == DEPTH + 1 || z == 0;
					BlockState state = !shell ? air : y == 0 ? floor : wall;
					if (z == 0 && y > 0 && y <= HEIGHT) {
						if (Math.abs(x) <= GLASS_HALF && y <= GLASS_H) {
							state = glass;
						} else if (Math.abs(x) <= GLASS_HALF + 1) {
							state = frame; // the frame round the mirror (both rooms see it)
						}
					}
					level.setBlock(p.set(o.getX() + x, o.getY() + y, o.getZ() + z), state, flags);
				}
			}
		}
		// hidden lights: a dim room on his side; v0.16.4 (screenshot check) the reflection side is lit brighter so the Hulk in
		// the mirror reads clearly through the dark glass (z mirrored: z' = 1 - z)
		int[][] lights = { { -3, 3, 6 }, { 3, 3, 6 }, { -3, 7, 5 }, { 3, 7, 5 }, { 0, 2, 9 } };
		for (int[] l : lights) {
			level.setBlock(p.set(o.getX() + l[0], o.getY() + HEIGHT, o.getZ() + l[1]),
					Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, l[2]), flags);
			level.setBlock(p.set(o.getX() + l[0], o.getY() + HEIGHT, o.getZ() + 1 - l[1]),
					Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, Math.min(15, l[2] + 4)), flags);
		}
	}

	private static void holdChunk(ServerLevel level, BlockPos at) {
		ChunkPos c = new ChunkPos(at);
		level.getChunkSource().addRegionTicket(BODY_TICKET, c, 1, c);
	}

	// ---------------------------------------------------------------- the mirror

	/** He pressed H in his mind (v0.16.4; it was a right-click on the mirror): the Hulk comes for him. */
	public static void onMirrorUsed(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s == null || s.smashAt != 0L || player.level().dimension() != DIMENSION) {
			return;
		}
		ServerLevel mind = player.serverLevel();
		MirrorHulkEntity hulk = hulk(mind, s);
		if (hulk == null) {
			return;
		}
		long now = mind.getGameTime();
		s.smashAt = now;
		hulk.mirror(player);
		s.hulkFrom = hulk.position();
		hulk.startSmash(now);
		GammaOverload.lockMovement(player, true);
		mind.playSound(null, hulk.getX(), hulk.getY(), hulk.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.0f, 0.5f);
		mind.playSound(null, hulk.getX(), hulk.getY(), hulk.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.HOSTILE, 1.5f, 0.5f);
	}

	private static MirrorHulkEntity hulk(ServerLevel mind, Session s) {
		return s.hulk != null && mind.getEntity(s.hulk) instanceof MirrorHulkEntity h ? h : null;
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		ServerLevel mind = server.getLevel(DIMENSION);
		long now = server.overworld().getGameTime();
		for (Session s : SESSIONS.values()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player);
			if (p == null) {
				end(server, s); // logged out mid-way: the saved return finishes it on his next login
				continue;
			}
			if (mind == null || p.level() != mind) {
				// left by some other way (death, a command): the change happens wherever he is
				end(server, s);
				p.removeAttached(ModAttachments.HULK_MIND_RETURN);
				GammaOverload.lockMovement(p, false);
				GammaOverload.shield(p);
				GammaOverload.awaken(p);
				continue;
			}
			if (now % 20L == 0L && s.bodyDimension != null) {
				ServerLevel home = server.getLevel(s.bodyDimension);
				Entity body = home == null ? null : home.getEntity(s.body);
				if (body != null) {
					holdChunk(home, body.blockPosition());
				}
			}
			MirrorHulkEntity hulk = hulk(mind, s);
			if (hulk == null) {
				hulk = respawnHulk(mind, s, p);
				if (hulk == null) {
					continue;
				}
			}
			if (s.smashAt == 0L) {
				hulk.mirror(p);
				ambient(mind, p, hulk, now - s.enteredAt);
			} else {
				tickSmash(server, mind, p, hulk, s, now - s.smashAt);
			}
		}
		if (now % 10L == 0L) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				check(p);
			}
		}
	}

	/** The mirror Hulk went missing (should not happen): put a new one at his reflection. */
	private static MirrorHulkEntity respawnHulk(ServerLevel mind, Session s, ServerPlayer p) {
		if (s.smashAt != 0L) {
			returnHome(p, s);
			return null;
		}
		MirrorHulkEntity hulk = HulkEntities.MIRROR_HULK.create(mind);
		if (hulk == null) {
			return null;
		}
		hulk.setup(p.getUUID(), mirrorZ(origin(s.slot)));
		hulk.mirror(p);
		s.hulk = hulk.getUUID();
		mind.addFreshEntity(hulk);
		return hulk;
	}

	/** The heartbeat in his head, the thing in the mirror breathing, and the hint. */
	private static void ambient(ServerLevel mind, ServerPlayer p, MirrorHulkEntity hulk, long t) {
		if (t % 30L == 0L) {
			mind.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.55f);
		}
		if (t % 90L == 45L) {
			mind.playSound(null, hulk.getX(), hulk.getY() + 2.0, hulk.getZ(), SoundEvents.RAVAGER_AMBIENT, SoundSource.HOSTILE, 0.6f, 0.45f);
		}
		if (t % 4L == 0L) {
			mind.sendParticles(GAMMA, hulk.getX(), hulk.getY() + 1.6, hulk.getZ(), 1, 0.5, 0.9, 0.5, 0.0);
		}
		if (t == 60L || (t > 60L && t % 160L == 0L)) {
			p.displayClientMessage(Component.translatable("message.projecthero.hulk_mind.hint_h").withStyle(ChatFormatting.GREEN), true);
		}
	}

	/** The Hulk walks up to the glass, rears back, smashes through, lunges and grabs him. */
	private static void tickSmash(MinecraftServer server, ServerLevel mind, ServerPlayer p, MirrorHulkEntity hulk, Session s, long t) {
		GammaOverload.lockMovement(p, true);
		BlockPos o = origin(s.slot);
		double mz = mirrorZ(o);
		// just behind the glass, in the middle of the hole
		Vec3 hole = new Vec3(o.getX() + 0.5, o.getY() + 1, mz - 1.3);
		Vec3 target = p.position();
		if (t <= MirrorHulkEntity.SMASH_APPROACH_END) {
			float f = ease(t / (float) MirrorHulkEntity.SMASH_APPROACH_END);
			place(hulk, s.hulkFrom.lerp(hole, f), target);
		} else if (t < MirrorHulkEntity.SMASH_SHATTER_AT) {
			place(hulk, hole, target);
			if (t == MirrorHulkEntity.SMASH_APPROACH_END + 1) {
				mind.playSound(null, hole.x, hole.y, hole.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.4f, 0.6f);
			}
			if (t == MirrorHulkEntity.SMASH_PUNCH_AT) {
				mind.playSound(null, hole.x, hole.y, hole.z, SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 1.6f, 0.5f);
			}
		} else if (t == MirrorHulkEntity.SMASH_SHATTER_AT) {
			place(hulk, hole, target);
			shatter(mind, o, target);
		} else if (t <= MirrorHulkEntity.SMASH_GRAB_AT) {
			// the lunge: through the hole, to arm's length in front of him
			Vec3 flat = new Vec3(hole.x - target.x, 0.0, hole.z - target.z);
			Vec3 grab = flat.lengthSqr() < 1.0e-4 ? target : target.add(flat.normalize().scale(1.3));
			float f = ease((t - MirrorHulkEntity.SMASH_SHATTER_AT) / (float) (MirrorHulkEntity.SMASH_GRAB_AT - MirrorHulkEntity.SMASH_SHATTER_AT));
			place(hulk, hole.lerp(grab, f), target);
			if (t == MirrorHulkEntity.SMASH_GRAB_AT) {
				mind.playSound(null, target.x, target.y, target.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.HOSTILE, 1.5f, 0.5f);
				mind.playSound(null, target.x, target.y, target.z, SoundEvents.POLAR_BEAR_WARNING, SoundSource.HOSTILE, 1.8f, 0.45f);
				HulkCombat.shake(mind, target, 0.6f, 20, 8.0);
			}
		} else if (t < MirrorHulkEntity.SMASH_RETURN_AT) {
			place(hulk, hulk.position(), target);
			mind.sendParticles(GAMMA, target.x, target.y + 1.0, target.z, 4, 0.4, 0.6, 0.4, 0.02);
			if (t == MirrorHulkEntity.SMASH_RETURN_AT - 10) {
				mind.playSound(null, target.x, target.y, target.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.6f);
			}
		} else {
			returnHome(p, s);
		}
	}

	private static float ease(float f) {
		f = Mth.clamp(f, 0.0f, 1.0f);
		return f * f * (3.0f - 2.0f * f);
	}

	/** Puts the Hulk at {@code at}, turned to face {@code look} (his eyes). */
	private static void place(MirrorHulkEntity hulk, Vec3 at, Vec3 look) {
		hulk.setPos(at.x, at.y, at.z);
		double dx = look.x - at.x;
		double dz = look.z - at.z;
		if (dx * dx + dz * dz > 1.0e-4) {
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
			double dy = (look.y + 1.5) - (at.y + hulk.getEyeHeight());
			hulk.setYRot(yaw);
			hulk.yBodyRot = yaw;
			hulk.yHeadRot = yaw;
			hulk.setXRot((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG));
		}
	}

	/** The mirror bursts: every glass block breaks (particles and the break sound) with a few louder crashes on top. */
	private static void shatter(ServerLevel mind, BlockPos o, Vec3 toward) {
		BlockState glass = Blocks.GRAY_STAINED_GLASS.defaultBlockState();
		for (int x = -GLASS_HALF; x <= GLASS_HALF; x++) {
			for (int y = 1; y <= GLASS_H; y++) {
				BlockPos pos = o.offset(x, y, 0);
				if (mind.getBlockState(pos).is(Blocks.GRAY_STAINED_GLASS)) {
					mind.levelEvent(2001, pos, Block.getId(glass)); // break particles + sound
					mind.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
				Vec3 c = Vec3.atCenterOf(pos);
				Vec3 d = toward.add(0, 1.0, 0).subtract(c).normalize();
				mind.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, glass), c.x, c.y, c.z, 0, d.x, d.y + 0.2, d.z, 0.6);
				mind.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), c.x, c.y, c.z, 12, 0.4,
						0.4, 0.4, 0.3);
			}
		}
		Vec3 c = Vec3.atCenterOf(o.offset(0, 2, 0));
		mind.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.5f, 0.6f);
		mind.playSound(null, c.x, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 2.5f, 0.8f);
		mind.playSound(null, c.x, c.y, c.z, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0f, 0.55f);
		mind.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 0.6f, 1.6f);
		HulkCombat.shake(mind, c, 0.8f, 16, 12.0);
	}

	// ---------------------------------------------------------------- coming back

	/** The Hulk has him: back to where his body knelt, and the change begins. */
	static void returnHome(ServerPlayer player, Session s) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		ServerLevel level = null;
		Vec3 at = null;
		HulkMindReturn saved = player.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null);
		float yRot = saved == null ? player.getYRot() : saved.yRot();
		float xRot = saved == null ? 0.0f : saved.xRot();
		// where his body is now (it may have dropped into the crater)...
		if (s.bodyDimension != null && s.body != null) {
			ServerLevel home = server.getLevel(s.bodyDimension);
			Entity body = home == null ? null : home.getEntity(s.body);
			if (body != null && body.isAlive()) {
				level = home;
				at = body.position();
			}
		}
		end(server, s);
		// ...or where he stood
		if (at == null) {
			level = savedLevel(server, saved);
			at = savedPos(level, saved);
		}
		finish(player, level, at, yRot, xRot);
	}

	/** No live session but a saved return (relog / crash): send him back and finish the change. */
	private static void finishFromSave(ServerPlayer player) {
		MinecraftServer server = player.getServer();
		if (server == null) {
			return;
		}
		HulkMindReturn saved = player.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null);
		ServerLevel level = savedLevel(server, saved);
		finish(player, level, savedPos(level, saved), saved == null ? player.getYRot() : saved.yRot(), saved == null ? 0.0f : saved.xRot());
	}

	private static void finish(ServerPlayer player, ServerLevel level, Vec3 at, float yRot, float xRot) {
		player.removeAttached(ModAttachments.HULK_MIND_RETURN);
		GammaOverload.lockMovement(player, false);
		player.teleportTo(level, at.x, at.y, at.z, yRot, xRot);
		player.fallDistance = 0.0f;
		player.setDeltaMovement(Vec3.ZERO);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0f, 0.5f);
		level.sendParticles(GAMMA, at.x, at.y + 1.0, at.z, 40, 0.5, 0.9, 0.5, 0.05);
		GammaOverload.shield(player);
		GammaOverload.awaken(player);
	}

	private static ServerLevel savedLevel(MinecraftServer server, HulkMindReturn saved) {
		ServerLevel level = null;
		if (saved != null) {
			ResourceLocation id = ResourceLocation.tryParse(saved.dimension());
			level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		return level == null || level.dimension() == DIMENSION ? server.overworld() : level;
	}

	private static Vec3 savedPos(ServerLevel level, HulkMindReturn saved) {
		if (saved != null && level.dimension().location().toString().equals(saved.dimension())) {
			return new Vec3(saved.x(), saved.y(), saved.z());
		}
		return spawn(level);
	}

	private static Vec3 spawn(ServerLevel level) {
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/** Ends a session without finishing anything: the body and the mirror Hulk go. */
	private static void end(MinecraftServer server, Session s) {
		SESSIONS.remove(s.player);
		if (s.bodyDimension != null && s.body != null) {
			ServerLevel home = server.getLevel(s.bodyDimension);
			Entity body = home == null ? null : home.getEntity(s.body);
			if (body != null) {
				body.discard();
			}
		}
		ServerLevel mind = server.getLevel(DIMENSION);
		Entity hulk = mind == null || s.hulk == null ? null : mind.getEntity(s.hulk);
		if (hulk != null) {
			hulk.discard();
		}
	}

	/** Test hook: the Hulk has him now -- straight back home and into the change. */
	public static void returnNow(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s != null) {
			returnHome(player, s);
		}
	}

	/** Logging out mid-way: the session (body, mirror Hulk) ends; the saved return brings him back on his next login. */
	public static void onDisconnect(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		if (s != null && player.getServer() != null) {
			end(player.getServer(), s);
		}
	}

	/**
	 * A player with no live session: in the mind with a saved return (a relog or crash mid-way), or anywhere with one left
	 * over -- he is sent back and the change finishes. In the mind with nothing saved -- he has no business there: spawn.
	 */
	public static void check(ServerPlayer player) {
		if (SESSIONS.containsKey(player.getUUID())) {
			return;
		}
		if (player.getAttachedOrElse(ModAttachments.HULK_MIND_RETURN, null) != null) {
			finishFromSave(player);
		} else if (player.level().dimension() == DIMENSION && player.getServer() != null) {
			ServerLevel overworld = player.getServer().overworld();
			Vec3 at = spawn(overworld);
			player.teleportTo(overworld, at.x, at.y, at.z, player.getYRot(), player.getXRot());
		}
	}
}
