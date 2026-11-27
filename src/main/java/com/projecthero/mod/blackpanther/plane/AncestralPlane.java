package com.projecthero.mod.blackpanther.plane;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3f;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.blackpanther.BlackPanther;
import com.projecthero.mod.network.TitanShakePayload;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Black Panther: <b>the Ancestral Plane</b> -- how the power is earned now. The user: "to get this power you need to
 * craft the heart shaped herb soup / drinking this gives you Bast's Blessing for 1 minute / then player needs to sleep, upon
 * falling asleep player is transported to the ancestral plain ... a savana ... a warm orange sunset, player will spawn a
 * distance away from their ancenstors, they will need to walk up to their ancestors and they will then take you to a panther
 * shrine, in there player must right click the panther shrine and then the player will be tested, player will have to beat a
 * minigame and do a 100 block parkour course and once they complete that they will get the powers ... incorporate cutscenes,
 * this must all take place in the players mind palace thing similar to hulk and doctor fate".
 *
 * <p>Built on the Shazam journey's framework ({@code shazam.journey}): a private slot per dreamer in a datapack dimension
 * ({@link PlaneRealm}), a saved return point, server-authoritative stages with the client shooting the cutscenes from shared
 * beats ({@link PlaneScene}), the shared {@code CutsceneHands}, the black "Loading terrain" cover, and the same safety rules.
 * The stages, in order:
 * <ol>
 * <li>{@link Stage#ARRIVAL} -- asleep in bed with {@link BastBlessing} (any time the bed allows, and by day too while blessed):
 * the blessing is spent and the dreamer wakes on the savanna at sunset, 70 blocks from the ancestors (cutscene).</li>
 * <li>{@link Stage#WANDER} -- free; a trail of gold and violet motes leads to the robed ancestors under the great acacia.</li>
 * <li>{@link Stage#GREETING} -- walking up to them (or right-clicking one): the eldest greets the dreamer (cutscene).</li>
 * <li>{@link Stage#PROCESSION} -- they walk the dreamer to the Panther Shrine (cutscene).</li>
 * <li>{@link Stage#SHRINE} -- free; right-click the statue, its pedestal or the altar.</li>
 * <li>{@link Stage#TRIAL_INTRO} -- the statue's eyes wake, Bast speaks (cutscene); then {@link Stage#TRIAL}, the Trial of the
 * Shades ({@link ShadeTrial}): three rounds of ghost panthers, retry a lost round on the spot.</li>
 * <li>{@link Stage#PATH} -- the spirit path forms behind the shrine (cutscene); then {@link Stage#PARKOUR}: 113 blocks of
 * floating stones ({@link ParkourCourse}), checkpoints every ~20 blocks, a fall puts you back on the last one.</li>
 * <li>{@link Stage#FINALE} -- Bast's spirit and the ancestors' farewell (cutscene); then the dreamer wakes in bed and the power
 * is granted ({@link BlackPanther#grant}: the one Primary, the title and chat).</li>
 * </ol>
 *
 * <p><b>Safety:</b> nothing in the plane can hurt the dreamer, nothing can be built or broken; straying too far draws you back;
 * holding Sneak for 5 s in a free stage wakes you early with no power (a new soup is needed). Logging out, a crash or a server
 * stop ends the dream; the saved return point ({@link #RETURN}) sends them back to bed at the next login (no power). Death ends
 * it too. The live dreams are a static map of player UUIDs (no worlds or entities), cleared by {@code ServerStateReset}.
 */
public final class AncestralPlane {
	// ---------------------------------------------------------------- registration

	public static final AttachmentType<PlanePayloads.Return> RETURN = AttachmentRegistry.create(ProjectHeroMod.id("ancestral_plane_return"),
			builder -> builder.persistent(PlanePayloads.Return.CODEC));

	/** Which plane cutscenes this player has watched to the end (bits of {@link PlaneScene#bit}): those can be skipped. */
	public static final AttachmentType<Integer> SEEN = AttachmentRegistry.create(ProjectHeroMod.id("ancestral_plane_seen"),
			builder -> builder.persistent(Codec.INT).copyOnDeath().initializer(() -> 0));

	public static final EntityType<AncestorEntity> ANCESTOR = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("ancestor")),
			EntityType.Builder.<AncestorEntity>of(AncestorEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).noSave().noSummon()
					.clientTrackingRange(10).updateInterval(1).build("ancestor"));

	public static final EntityType<PantherShadeEntity> SHADE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("panther_shade")),
			EntityType.Builder.<PantherShadeEntity>of(PantherShadeEntity::new, MobCategory.MISC).sized(1.0f, 1.0f).noSave().noSummon()
					.clientTrackingRange(10).updateInterval(1).fireImmune().build("panther_shade"));

	public enum Stage {
		ARRIVAL, WANDER, GREETING, PROCESSION, SHRINE, TRIAL_INTRO, TRIAL, PATH, PARKOUR, FINALE
	}

	/** Asleep this long (vanilla's screen is nearly dark at 100) with the blessing: into the plane. */
	public static final int SLEEP_TICKS = 80;
	/** Hold Sneak this long in a free stage to wake early. */
	public static final int WAKE_HOLD = 100;
	/** Lying in bed this long after waking, then up. */
	public static final int WAKE_LIE = 40;

	private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.32f), 1.1f);
	private static final DustParticleOptions VIOLET = new DustParticleOptions(new Vector3f(0.66f, 0.38f, 1.0f), 1.2f);
	private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.18f, 0.08f, 0.3f), 1.6f);

	private static final class Session {
		final UUID player;
		final int slot;
		Stage stage = Stage.ARRIVAL;
		int serial;
		long stageStart;
		long lastT = -1;
		Vec3 hold;
		float holdYaw, holdPitch;
		final UUID[] ancestors = new UUID[PlaneGeometry.ANCESTOR_COUNT];
		final Vec3[] walkFrom = new Vec3[PlaneGeometry.ANCESTOR_COUNT];
		final List<UUID> shades = new ArrayList<>();
		UUID spirit;
		ShadeTrial trial;
		long trialPauseUntil;
		long nextSpawn;
		int checkpoint;
		int sneakTicks;
		int hudEvent;
		PlanePayloads.Hud lastHud;
		long lastHudAt;

		Session(UUID player, int slot) {
			this.player = player;
			this.slot = slot;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
	/** Players put back in bed: wake them at this game time. UUIDs only; cleared by {@code ServerStateReset}. */
	private static final Map<UUID, Long> PENDING_WAKE = new ConcurrentHashMap<>();

	private AncestralPlane() {
	}

	public static void initialize() {
		BastBlessing.initialize();
		FabricDefaultAttributeRegistry.register(ANCESTOR, AncestorEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(SHADE, PantherShadeEntity.createAttributes());
		PayloadTypeRegistry.playS2C().register(PlanePayloads.Scene.TYPE, PlanePayloads.Scene.CODEC);
		PayloadTypeRegistry.playS2C().register(PlanePayloads.Hud.TYPE, PlanePayloads.Hud.CODEC);
		PayloadTypeRegistry.playC2S().register(PlanePayloads.Skip.TYPE, PlanePayloads.Skip.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(PlanePayloads.Skip.TYPE, (payload, context) -> skip(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(AncestralPlane::tick);
		// v0.17.7 (user: sleeping while blessed starts it): blessed, a bed lets you sleep by day as well (and the waking dreamer
		// lies a moment longer)
		EntitySleepEvents.ALLOW_SLEEP_TIME.register((player, pos, vanilla) -> !vanilla
				&& (BastBlessing.has(player) || PENDING_WAKE.containsKey(player.getUUID())) ? InteractionResult.SUCCESS : InteractionResult.PASS);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.getPlayer()));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && SESSIONS.containsKey(p.getUUID())) {
				end(p.server, SESSIONS.get(p.getUUID()));
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				newPlayer.removeAttached(RETURN);
			}
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && guarded(p))
				|| source.is(DamageTypeTags.BYPASSES_INVULNERABILITY));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !guarded(sp)) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND && shrineClicked(sp, hit.getBlockPos())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.FAIL;
		});
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> player instanceof ServerPlayer sp && guarded(sp)
				? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !(player instanceof ServerPlayer sp && guarded(sp)));
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof ServerPlayer sp && guarded(sp)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
	}

	public static void clearSessionState() {
		SESSIONS.clear();
		PENDING_WAKE.clear();
		PlaneRealm.clearSessionState();
	}

	// ---------------------------------------------------------------- queries

	public static boolean inPlane(ServerPlayer player) {
		return SESSIONS.containsKey(player.getUUID());
	}

	public static Stage stage(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : s.stage;
	}

	public static int slot(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? -1 : s.slot;
	}

	public static BlockPos origin(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : PlaneRealm.origin(PlaneRealm.level(player.server), s.slot);
	}

	public static int checkpoint(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? -1 : s.checkpoint;
	}

	public static ShadeTrial trial(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : s.trial;
	}

	public static List<PantherShadeEntity> shades(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		List<PantherShadeEntity> out = new ArrayList<>();
		if (s != null) {
			for (UUID id : s.shades) {
				if (PlaneRealm.level(player.server).getEntity(id) instanceof PantherShadeEntity e) {
					out.add(e);
				}
			}
		}
		return out;
	}

	public static AncestorEntity eldest(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : ancestor(player.server, s, 0);
	}

	/** In a dream, or anywhere in the plane's dimension: no damage, no building. */
	static boolean guarded(ServerPlayer p) {
		return SESSIONS.containsKey(p.getUUID()) || p.level().dimension() == PlaneRealm.DIMENSION;
	}

	private static AncestorEntity ancestor(MinecraftServer server, Session s, int i) {
		ServerLevel l = PlaneRealm.level(server);
		return s.ancestors[i] != null && l.getEntity(s.ancestors[i]) instanceof AncestorEntity a ? a : null;
	}

	private static PantherShadeEntity spirit(MinecraftServer server, Session s) {
		ServerLevel l = PlaneRealm.level(server);
		return s.spirit != null && l.getEntity(s.spirit) instanceof PantherShadeEntity e ? e : null;
	}

	private static int freeSlot() {
		for (int i = 0;; i++) {
			boolean used = false;
			for (Session s : SESSIONS.values()) {
				used |= s.slot == i;
			}
			if (!used) {
				return i;
			}
		}
	}

	static PlanePayloads.Return returnOf(ServerPlayer p, BlockPos bed) {
		return new PlanePayloads.Return(p.level().dimension().location().toString(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(),
				Optional.ofNullable(bed));
	}

	// ---------------------------------------------------------------- falling asleep

	/** Asleep in bed long enough with the blessing: into the plane. True if it began. */
	public static boolean fellAsleep(ServerPlayer p) {
		if (SESSIONS.containsKey(p.getUUID()) || !BastBlessing.has(p) || !p.isAlive()) {
			return false;
		}
		BlockPos bed = p.getSleepingPos().orElse(null);
		PlanePayloads.Return back = returnOf(p, bed);
		p.removeEffect(BastBlessing.EFFECT); // the blessing is spent
		if (p.isSleeping()) {
			p.stopSleepInBed(false, false);
		}
		return begin(p, back);
	}

	/** Starts a dream for {@code p}, coming back to {@code back} afterwards. False if already dreaming. */
	public static boolean begin(ServerPlayer p, PlanePayloads.Return back) {
		if (SESSIONS.containsKey(p.getUUID()) || !p.isAlive()) {
			return false;
		}
		ServerLevel realm = PlaneRealm.level(p.server);
		Session s = new Session(p.getUUID(), freeSlot());
		BlockPos o = PlaneRealm.origin(realm, s.slot);
		p.setAttached(RETURN, back);
		SESSIONS.put(p.getUUID(), s);
		if (PlaneRealm.isBuilt(realm, s.slot)) {
			PlaneRealm.resetForVisit(realm, o);
		}
		PlaneRealm.queue(realm, s.slot);
		PlaneRealm.hold(realm, s.slot);
		p.stopRiding();
		p.clearFire();
		Vec3 at = PlaneGeometry.spawn(o);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
		p.fallDistance = 0f;
		p.setDeltaMovement(Vec3.ZERO);
		setStage(p, s, Stage.ARRIVAL);
		hold(s, at, PlaneGeometry.FACE_EAST, 0f);
		return true;
	}

	/** Test / command hook: a dream from where {@code p} stands (no bed to wake in). */
	public static boolean beginHere(ServerPlayer p) {
		return begin(p, returnOf(p, null));
	}

	private static void setStage(ServerPlayer p, Session s, Stage stage) {
		s.stage = stage;
		s.serial++;
		s.stageStart = p.level().getGameTime();
		s.lastT = -1;
		s.hold = null;
		s.sneakTicks = 0;
		PlaneScene scene = sceneOf(stage);
		if (scene != null) {
			sendScene(p, s, scene, 0);
		}
	}

	private static PlaneScene sceneOf(Stage stage) {
		return switch (stage) {
			case ARRIVAL -> PlaneScene.ARRIVAL;
			case GREETING -> PlaneScene.GREETING;
			case PROCESSION -> PlaneScene.PROCESSION;
			case TRIAL_INTRO -> PlaneScene.TRIAL_INTRO;
			case PATH -> PlaneScene.PATH;
			case FINALE -> PlaneScene.FINALE;
			default -> null;
		};
	}

	private static void sendScene(ServerPlayer p, Session s, PlaneScene scene, int elapsed) {
		if (p.connection == null || !ServerPlayNetworking.canSend(p, PlanePayloads.Scene.TYPE)) {
			return;
		}
		BlockPos o = PlaneRealm.origin(PlaneRealm.level(p.server), s.slot);
		Entity focus = scene == PlaneScene.FINALE ? spirit(p.server, s) : ancestor(p.server, s, 0);
		boolean skippable = scene.skippable && (p.getAttachedOrElse(SEEN, 0) & scene.bit()) != 0;
		ServerPlayNetworking.send(p, new PlanePayloads.Scene(scene.id, elapsed, o.getX(), o.getY(), o.getZ(), focus == null ? -1 : focus.getId(), skippable));
	}

	private static void hold(Session s, Vec3 at, float yaw, float pitch) {
		s.hold = at;
		s.holdYaw = yaw;
		s.holdPitch = pitch;
	}

	private static void markSeen(ServerPlayer p, Stage stage) {
		PlaneScene scene = sceneOf(stage);
		if (scene != null) {
			p.setAttached(SEEN, p.getAttachedOrElse(SEEN, 0) | scene.bit());
		}
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		PlaneRealm.tick(server);
		long now = server.overworld().getGameTime();
		for (Session s : SESSIONS.values()) {
			ServerPlayer p = server.getPlayerList().getPlayer(s.player);
			if (p == null) {
				end(server, s);
				continue;
			}
			try {
				step(p, s);
			} catch (RuntimeException e) {
				ProjectHeroMod.LOGGER.error("[AncestralPlane] step failed; waking {}", p.getGameProfile().getName(), e);
				end(server, s);
				returnHome(p, false);
			}
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!SESSIONS.containsKey(p.getUUID()) && p.isSleeping() && p.getSleepTimer() >= SLEEP_TICKS && BastBlessing.has(p)) {
				fellAsleep(p);
			}
			Long wake = PENDING_WAKE.get(p.getUUID());
			if (wake != null && (p.level().getGameTime() >= wake || !p.isSleeping())) {
				PENDING_WAKE.remove(p.getUUID());
				if (p.isSleeping()) {
					p.stopSleepInBed(false, true);
				}
			}
		}
		if (now % 10L == 0L) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				check(p);
			}
		}
		if (!PENDING_WAKE.isEmpty() && now % 200L == 0L) {
			PENDING_WAKE.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		}
	}

	private static boolean beat(Session s, long t, int at) {
		return s.lastT < at && t >= at;
	}

	private static void step(ServerPlayer p, Session s) {
		ServerLevel realm = PlaneRealm.level(p.server);
		BlockPos o = PlaneRealm.origin(realm, s.slot);
		long now = realm.getGameTime();
		long t = now - s.stageStart;
		int serial = s.serial;
		if (p.level() != realm || !PlaneGeometry.inBox(o, p.position()) && PlaneRealm.isFallback(realm) && p.position().distanceTo(PlaneGeometry.spawn(o)) > 600) {
			// taken out some other way (a command, a portal): the dream is over
			end(p.server, s);
			p.removeAttached(RETURN);
			return;
		}
		if (now % 20L == 0L) {
			PlaneRealm.hold(realm, s.slot);
		}
		boolean free = s.stage == Stage.WANDER || s.stage == Stage.SHRINE || s.stage == Stage.TRIAL || s.stage == Stage.PARKOUR;
		if (free && !PlaneGeometry.inBox(o, p.position())) {
			drawBack(p, s, realm, o);
		}
		switch (s.stage) {
			case ARRIVAL -> arrival(p, s, t, realm, o);
			case WANDER -> wander(p, s, t, realm, o);
			case GREETING -> greeting(p, s, t, realm, o);
			case PROCESSION -> procession(p, s, t, realm, o);
			case SHRINE -> shrine(p, s, t, realm, o);
			case TRIAL_INTRO -> trialIntro(p, s, t, realm, o);
			case TRIAL -> trial(p, s, t, realm, o, now);
			case PATH -> path(p, s, t, realm, o);
			case PARKOUR -> parkour(p, s, t, realm, o);
			case FINALE -> finale(p, s, t, realm, o);
		}
		Session still = SESSIONS.get(p.getUUID());
		if (still != s) {
			return;
		}
		if (s.serial == serial) {
			s.lastT = t;
		}
		// hold Sneak to wake early (free stages only)
		boolean freeNow = s.stage == Stage.WANDER || s.stage == Stage.SHRINE || s.stage == Stage.TRIAL || s.stage == Stage.PARKOUR;
		if (freeNow && p.isShiftKeyDown()) {
			s.sneakTicks++;
			if (s.sneakTicks >= WAKE_HOLD) {
				leave(p);
				return;
			}
		} else {
			s.sneakTicks = 0;
		}
		sendHud(p, s, realm, o, now);
		// the scene's hold
		if (s.hold != null) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			p.fallDistance = 0f;
			if (p.position().distanceToSqr(s.hold) > 0.09) {
				p.connection.teleport(s.hold.x, s.hold.y, s.hold.z, s.holdYaw, s.holdPitch);
			}
		}
	}

	/** Strayed out of the plane's box: back to where the current stage expects you. */
	private static void drawBack(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		Vec3 at = switch (s.stage) {
			case SHRINE -> PlaneGeometry.shrineEntry(o);
			case TRIAL -> PlaneGeometry.arena(o);
			case PARKOUR -> checkpointSpot(o, s.checkpoint);
			default -> PlaneGeometry.spawn(o);
		};
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
		p.fallDistance = 0f;
		p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.drawn_back").withStyle(ChatFormatting.GOLD), true);
	}

	// ---------------------------------------------------------------- ancestors

	/** Makes sure the five ancestors stand (spawning any missing once their chunk is live); {@code shrine}: at the shrine. */
	private static void ensureAncestors(ServerPlayer p, Session s, ServerLevel realm, BlockPos o, boolean shrine) {
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			if (ancestor(p.server, s, i) != null) {
				continue;
			}
			Vec3 at = shrine ? PlaneGeometry.ancestorAtShrine(o, i) : PlaneGeometry.ancestor(o, i);
			if (!PlaneRealm.live(realm, at)) {
				continue;
			}
			AncestorEntity a = ANCESTOR.create(realm);
			if (a == null) {
				continue;
			}
			a.setVariant(i);
			a.moveTo(at.x, at.y, at.z, PlaneGeometry.FACE_WEST, 0f);
			a.place(at, shrine ? PlaneGeometry.lookYaw(at, PlaneGeometry.shrineEntry(o)) : PlaneGeometry.FACE_WEST, null);
			realm.addFreshEntity(a);
			s.ancestors[i] = a.getUUID();
		}
	}

	/** Every ancestor faces (head and, when close, body) toward {@code p}. */
	private static void ancestorsWatch(ServerPlayer p, Session s, boolean turnBodies) {
		Vec3 eye = p.getEyePosition();
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a == null) {
				continue;
			}
			float yaw = a.getYRot();
			if (turnBodies) {
				float want = PlaneGeometry.lookYaw(a.position(), p.position());
				yaw = Mth.approachDegrees(yaw, want, 4f);
			}
			a.place(a.position(), yaw, a.distanceTo(p) < 40 ? eye : null);
		}
	}

	private static void ancestorGlow(ServerPlayer p, Session s, ServerLevel realm, long t) {
		if (t % 6 != 0) {
			return;
		}
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a != null) {
				realm.sendParticles(p, i % 2 == 0 ? GOLD : VIOLET, true, a.getX(), a.getY() + 0.3 + realm.random.nextDouble() * 1.6, a.getZ(), 1, 0.35, 0.2,
						0.35, 0.0);
			}
		}
	}

	// ---------------------------------------------------------------- the stages

	private static void arrival(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		ensureAncestors(p, s, realm, o, false);
		if (beat(s, t, PlaneScene.ARRIVAL_DARK)) {
			notify(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8f, 0.5f);
			notify(p, SoundEvents.BEACON_AMBIENT, 0.6f, 0.6f);
		}
		if (beat(s, t, 50)) {
			notify(p, SoundEvents.ELYTRA_FLYING, 0.18f, 0.45f);
		}
		if (beat(s, t, 104)) {
			notify(p, SoundEvents.CAT_PURREOW, 0.5f, 0.45f);
		}
		if (t >= PlaneScene.ARRIVAL.length) {
			markSeen(p, s.stage);
			setStage(p, s, Stage.WANDER);
			p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.wander").withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void wander(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		ensureAncestors(p, s, realm, o, false);
		ancestorsWatch(p, s, false);
		ancestorGlow(p, s, realm, t);
		Vec3 eldest = PlaneGeometry.ancestor(o, 0);
		double d = PlaneGeometry.flat(p.position(), eldest);
		// the guiding motes: a drifting trail of gold and violet from a few blocks ahead toward the ancestors
		if (t % 5 == 0 && d > PlaneGeometry.MEET_RANGE + 2) {
			Vec3 dir = new Vec3(eldest.x - p.getX(), 0, eldest.z - p.getZ()).normalize();
			double phase = (t % 40) / 40.0;
			for (int k = 0; k < 6; k++) {
				double along = 3 + (k + phase) * 3.2;
				if (along > d - 2) {
					break;
				}
				Vec3 at = p.position().add(dir.scale(along)).add(0, 0.9 + 0.25 * Math.sin((t + k * 7) * 0.2), 0);
				realm.sendParticles(p, k % 2 == 0 ? GOLD : VIOLET, true, at.x, at.y, at.z, 1, 0.12, 0.08, 0.12, 0.0);
			}
		}
		if (t > 0 && t % 160 == 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.wander").withStyle(ChatFormatting.GOLD), true);
		}
		if (d <= PlaneGeometry.MEET_RANGE && ancestor(p.server, s, 0) != null) {
			startGreeting(p, s, realm, o);
		}
	}

	/** Right-clicked an ancestor: walking up to them, the greeting. */
	static void ancestorClicked(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.stage == Stage.WANDER) {
			ServerLevel realm = PlaneRealm.level(p.server);
			startGreeting(p, s, realm, PlaneRealm.origin(realm, s.slot));
		} else if (s != null && s.stage == Stage.SHRINE) {
			p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.shrine").withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void startGreeting(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		Vec3 at = PlaneGeometry.meetSpot(o);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
		setStage(p, s, Stage.GREETING);
		hold(s, at, PlaneGeometry.FACE_EAST, 0f);
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a != null) {
				Vec3 home = PlaneGeometry.ancestor(o, i);
				a.place(home, PlaneGeometry.lookYaw(home, at), at.add(0, 1.6, 0));
			}
		}
	}

	private static void greeting(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		Vec3 me = PlaneGeometry.meetSpot(o);
		hold(s, me, PlaneGeometry.FACE_EAST, 0f);
		ensureAncestors(p, s, realm, o, false);
		ancestorGlow(p, s, realm, t);
		// the eldest steps forward to meet the dreamer as she begins
		AncestorEntity eldest = ancestor(p.server, s, 0);
		if (eldest != null) {
			Vec3 home = PlaneGeometry.ancestor(o, 0);
			Vec3 near = home.add(-2.2, 0, 0);
			double k = Mth.clamp((t - 18) / 26.0, 0.0, 1.0);
			Vec3 pos = home.lerp(near, k);
			eldest.place(pos, PlaneGeometry.lookYaw(pos, me), p.getEyePosition());
		}
		for (int i = 1; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a != null) {
				a.place(a.position(), PlaneGeometry.lookYaw(a.position(), me), p.getEyePosition());
			}
		}
		for (int line : new int[] { PlaneScene.GREET_LINE1, PlaneScene.GREET_LINE2, PlaneScene.GREET_LINE3, PlaneScene.GREET_LINE4 }) {
			if (beat(s, t, line)) {
				voice(realm, eldest == null ? PlaneGeometry.ancestor(o, 0) : eldest.position());
			}
		}
		if (t >= PlaneScene.GREETING.length) {
			markSeen(p, s.stage);
			startProcession(p, s, realm, o);
		}
	}

	private static void startProcession(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		Vec3 at = PlaneGeometry.shrineEntry(o);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
		setStage(p, s, Stage.PROCESSION);
		hold(s, at, PlaneGeometry.FACE_EAST, 0f);
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			s.walkFrom[i] = a != null ? a.position() : PlaneGeometry.ancestor(o, i);
		}
	}

	private static void procession(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		Vec3 me = PlaneGeometry.shrineEntry(o);
		hold(s, me, PlaneGeometry.FACE_EAST, 0f);
		if (!PlaneRealm.shrineReady(realm, s.slot)) {
			s.stageStart = realm.getGameTime(); // the scene waits for the shrine (only on a brand-new slot)
			return;
		}
		ensureAncestors(p, s, realm, o, false);
		ancestorGlow(p, s, realm, t);
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a == null) {
				continue;
			}
			Vec3 from = s.walkFrom[i] != null ? s.walkFrom[i] : PlaneGeometry.ancestor(o, i);
			Vec3 to = PlaneGeometry.ancestorAtShrine(o, i);
			int start = PlaneScene.PROC_WALK + i * 6;
			int dur = PlaneScene.PROC_ARRIVE - PlaneScene.PROC_WALK - 24;
			double k = Mth.clamp((t - start) / (double) dur, 0.0, 1.0);
			// walking pace with a soft start and stop
			double e = k < 0.08 ? k * k / 0.16 : k > 0.92 ? 1 - (1 - k) * (1 - k) / 0.16 : (k - 0.04) / 0.92;
			Vec3 pos = from.lerp(to, Mth.clamp(e, 0.0, 1.0));
			float yaw = k >= 1.0 ? PlaneGeometry.lookYaw(to, me) : PlaneGeometry.lookYaw(from, to);
			a.place(pos, Mth.approachDegrees(a.getYRot(), yaw, 9f), null);
		}
		AncestorEntity eldest = ancestor(p.server, s, 0);
		for (int line : new int[] { PlaneScene.PROC_LINE1, PlaneScene.PROC_LINE2 }) {
			if (beat(s, t, line)) {
				voice(realm, eldest == null ? me : eldest.position());
			}
		}
		if (t >= PlaneScene.PROCESSION.length) {
			markSeen(p, s.stage);
			enterShrineStage(p, s, realm, o);
		}
	}

	/** The ancestors stand at the shrine's entrance; the dreamer is free to go in. */
	private static void enterShrineStage(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		ensureAncestors(p, s, realm, o, true);
		Vec3 me = PlaneGeometry.shrineEntry(o);
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a != null) {
				Vec3 at = PlaneGeometry.ancestorAtShrine(o, i);
				a.place(at, PlaneGeometry.lookYaw(at, me), null);
			}
		}
		setStage(p, s, Stage.SHRINE);
		p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.shrine").withStyle(ChatFormatting.GOLD), true);
	}

	private static void shrine(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		ensureAncestors(p, s, realm, o, true);
		ancestorsWatch(p, s, true);
		ancestorGlow(p, s, realm, t);
		if (t % 10 == 0) {
			// a faint shimmer at the altar, inviting a touch
			Vec3 altar = PlaneGeometry.at(o, PlaneGeometry.SHRINE_X + PlaneGeometry.ALTAR_DX + 0.5, PlaneGeometry.SHRINE_FLOOR + 1.3, 0.5);
			realm.sendParticles(p, VIOLET, true, altar.x, altar.y, altar.z, 2, 0.5, 0.3, 0.9, 0.0);
		}
		if (t > 0 && t % 160 == 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.shrine").withStyle(ChatFormatting.GOLD), true);
		}
	}

	/** A right-click on a block in the plane: the statue, its pedestal or the altar starts the test. */
	static boolean shrineClicked(ServerPlayer p, BlockPos pos) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.stage != Stage.SHRINE) {
			return false;
		}
		ServerLevel realm = PlaneRealm.level(p.server);
		BlockPos o = PlaneRealm.origin(realm, s.slot);
		if (!PlaneGeometry.isShrineHeart(pos.getX() - o.getX(), pos.getY() - o.getY(), pos.getZ() - o.getZ())) {
			return false;
		}
		startTrialIntro(p, s, realm, o);
		return true;
	}

	private static void startTrialIntro(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		ensureAncestors(p, s, realm, o, true);
		Vec3 at = PlaneGeometry.arena(o);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, -12f);
		setStage(p, s, Stage.TRIAL_INTRO);
		hold(s, at, PlaneGeometry.FACE_EAST, -12f);
		notify(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 0.6f);
	}

	private static void trialIntro(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		hold(s, PlaneGeometry.arena(o), PlaneGeometry.FACE_EAST, -12f);
		Vec3 head = PlaneGeometry.statueHead(o);
		if (beat(s, t, PlaneScene.INTRO_EYES)) {
			PlaneRealm.eyes(realm, o, true);
			play(realm, head, SoundEvents.BEACON_ACTIVATE, 1.4f, 0.55f);
			play(realm, head, SoundEvents.ENDER_DRAGON_GROWL, 0.35f, 1.7f);
			realm.sendParticles(p, VIOLET, true, head.x - 1.2, head.y, head.z, 40, 0.4, 0.3, 1.0, 0.02);
		}
		for (int line : new int[] { PlaneScene.INTRO_LINE1, PlaneScene.INTRO_LINE2, PlaneScene.INTRO_LINE3 }) {
			if (beat(s, t, line)) {
				bastVoice(realm, head);
			}
		}
		if (t >= PlaneScene.INTRO_LINE2 && t % 3 == 0) {
			// the shades gather: shadow wisps rising round the rim
			Vec3 c = PlaneGeometry.arena(o);
			double a = realm.random.nextDouble() * Math.PI * 2;
			realm.sendParticles(p, SHADOW, true, c.x + Math.cos(a) * PlaneGeometry.SHADE_RING, c.y + 0.3, c.z + Math.sin(a) * PlaneGeometry.SHADE_RING, 6, 0.3,
					0.4, 0.3, 0.01);
		}
		if (t >= PlaneScene.TRIAL_INTRO.length) {
			markSeen(p, s.stage);
			startTrial(p, s, realm, o);
		}
	}

	private static void startTrial(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		PlaneRealm.eyes(realm, o, true);
		if (s.trial == null) {
			s.trial = new ShadeTrial();
		}
		setStage(p, s, Stage.TRIAL);
		s.trialPauseUntil = realm.getGameTime() + 30;
		s.nextSpawn = s.trialPauseUntil;
		s.hudEvent = 4;
		p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.trial").withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	// ---------------------------------------------------------------- the trial of the shades

	private static void trial(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o, long now) {
		Vec3 centre = PlaneGeometry.shrineCentre(o);
		Vec3 arena = PlaneGeometry.arena(o);
		// kept on the shrine floor
		if (PlaneGeometry.flat(p.position(), centre) > PlaneGeometry.ARENA_KEEP || p.getY() < centre.y - 1.5) {
			p.teleportTo(realm, arena.x, arena.y, arena.z, p.getYRot(), p.getXRot());
			p.fallDistance = 0f;
		}
		ShadeTrial tr = s.trial;
		if (tr == null) {
			tr = s.trial = new ShadeTrial();
		}
		List<PantherShadeEntity> alive = new ArrayList<>();
		s.shades.removeIf(id -> !(realm.getEntity(id) instanceof PantherShadeEntity e) || e.isRemoved());
		for (UUID id : s.shades) {
			if (realm.getEntity(id) instanceof PantherShadeEntity e) {
				alive.add(e);
			}
		}
		if (tr.won()) {
			if (now >= s.trialPauseUntil) {
				startPath(p, s, realm, o);
			}
			return;
		}
		if (now < s.trialPauseUntil) {
			return;
		}
		if (now >= s.nextSpawn && tr.canSpawn(alive.size())) {
			PantherShadeEntity e = spawnShade(p, s, realm, arena, alive);
			if (e != null) {
				tr.noteSpawned();
				alive.add(e);
				s.nextSpawn = now + ShadeTrial.spawnGap(tr.round());
			}
		}
		int round = tr.round();
		for (PantherShadeEntity e : alive) {
			stepShade(p, s, realm, arena, e, (int) (now & 0x3FFFFFFF), round);
			if (SESSIONS.get(p.getUUID()) != s || s.stage != Stage.TRIAL || s.trial.round() != round || s.shades.isEmpty()) {
				break; // a round ended mid-loop
			}
		}
	}

	private static PantherShadeEntity spawnShade(ServerPlayer p, Session s, ServerLevel realm, Vec3 arena, List<PantherShadeEntity> alive) {
		// on the rim, never on the statue's side; a second shade comes from across the floor
		double a;
		if (!alive.isEmpty()) {
			Vec3 other = alive.get(0).position();
			a = Math.atan2(other.z - arena.z, other.x - arena.x) + Math.PI + (realm.random.nextDouble() - 0.5) * 0.6;
		} else {
			a = Math.toRadians(70 + realm.random.nextDouble() * 220);
		}
		double deg = Mth.wrapDegrees(Math.toDegrees(a));
		if (Math.abs(deg) < 60) {
			a = Math.toRadians(deg < 0 ? -70 : 70);
		}
		Vec3 at = new Vec3(arena.x + Math.cos(a) * PlaneGeometry.SHADE_RING, arena.y, arena.z + Math.sin(a) * PlaneGeometry.SHADE_RING);
		if (!PlaneRealm.live(realm, at)) {
			return null;
		}
		PantherShadeEntity e = SHADE.create(realm);
		if (e == null) {
			return null;
		}
		e.moveTo(at.x, at.y, at.z, 0f, 0f);
		e.face(p.position());
		e.setPhase(PantherShadeEntity.APPEAR, (int) (realm.getGameTime() & 0x3FFFFFFF));
		e.orbit = realm.random.nextBoolean() ? 1 : -1;
		realm.addFreshEntity(e);
		s.shades.add(e.getUUID());
		realm.sendParticles(p, SHADOW, true, at.x, at.y + 0.5, at.z, 24, 0.4, 0.4, 0.4, 0.02);
		play(realm, at, SoundEvents.CAT_HISS, 0.9f, 0.55f);
		return e;
	}

	private static void stepShade(ServerPlayer p, Session s, ServerLevel realm, Vec3 arena, PantherShadeEntity e, int now, int round) {
		int age = now - e.phaseStart;
		Vec3 me = p.position();
		switch (e.phase()) {
			case PantherShadeEntity.APPEAR -> {
				e.face(me);
				if (age % 3 == 0) {
					realm.sendParticles(p, SHADOW, true, e.getX(), e.getY() + 0.4, e.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
				}
				if (age >= ShadeTrial.APPEAR_TICKS) {
					e.setPhase(PantherShadeEntity.STALK, now);
				}
			}
			case PantherShadeEntity.STALK -> {
				Vec3 to = new Vec3(me.x - e.getX(), 0, me.z - e.getZ());
				double d = to.length();
				if (d > ShadeTrial.STALK_STOP && d > 1.0e-3) {
					Vec3 dir = to.scale(1.0 / d);
					Vec3 side = new Vec3(-dir.z, 0, dir.x).scale(e.orbit * 0.05);
					double stepLen = Math.min(ShadeTrial.stalkSpeed(round), d - ShadeTrial.STALK_STOP);
					Vec3 next = e.position().add(dir.scale(stepLen)).add(side);
					next = keepOnFloor(next, arena);
					e.setPos(next.x, arena.y, next.z);
				}
				e.face(me);
				if ((d <= ShadeTrial.STALK_STOP + 0.25 && age >= 16) || age >= ShadeTrial.stalkMax(round)) {
					e.setPhase(PantherShadeEntity.WINDUP, now);
					play(realm, e.position(), SoundEvents.CAT_HISS, 1.0f, 0.5f);
					notify(p, SoundEvents.WARDEN_HEARTBEAT, 0.7f, 1.4f);
				}
			}
			case PantherShadeEntity.WINDUP -> {
				e.face(me);
				if (age % 4 == 0) {
					realm.sendParticles(p, VIOLET, true, e.getX(), e.getY() + 0.8, e.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
				}
				if (age >= ShadeTrial.windup(round)) {
					Vec3 from = e.position();
					Vec3 dir = new Vec3(me.x - from.x, 0, me.z - from.z);
					double len = dir.length();
					Vec3 to = len < 1.0e-3 ? me : new Vec3(me.x, arena.y, me.z).add(dir.scale(0.8 / len));
					e.from = from;
					e.to = keepOnFloor(to, arena);
					e.setPhase(PantherShadeEntity.POUNCE, now);
					play(realm, from, SoundEvents.PHANTOM_SWOOP, 0.9f, 1.3f);
					play(realm, from, SoundEvents.CAT_HISS, 0.8f, 0.7f);
				}
			}
			case PantherShadeEntity.POUNCE -> {
				double k = Math.min(1.0, (age + 1) / (double) ShadeTrial.POUNCE_TICKS);
				Vec3 pos = e.from.lerp(e.to, k).add(0, 1.3 * Math.sin(Math.PI * k), 0);
				e.setPos(pos.x, pos.y, pos.z);
				if (k >= 1.0) {
					boolean hit = ShadeTrial.pounceHits(PlaneGeometry.flat(e.position(), me)) && Math.abs(me.y - arena.y) < 2.5;
					ShadeTrial.Outcome out = ShadeTrial.Outcome.NONE;
					if (hit) {
						out = s.trial.wound();
						s.hudEvent = out == ShadeTrial.Outcome.ROUND_LOST ? 2 : 3;
						notify(p, SoundEvents.PLAYER_HURT, 0.8f, 0.9f);
						Vec3 push = new Vec3(me.x - e.from.x, 0, me.z - e.from.z).normalize().scale(0.5);
						p.setDeltaMovement(push.x, 0.25, push.z);
						p.hurtMarked = true;
						shake(p, 1.5f, 8);
					}
					if (out == ShadeTrial.Outcome.ROUND_LOST) {
						roundLost(p, s, realm);
						return;
					}
					double a = Math.atan2(e.getZ() - arena.z, e.getX() - arena.x);
					e.from = e.position();
					e.to = keepOnFloor(new Vec3(arena.x + Math.cos(a) * PlaneGeometry.SHADE_RING, arena.y, arena.z + Math.sin(a) * PlaneGeometry.SHADE_RING), arena);
					if (hit) {
						e.setPhase(PantherShadeEntity.RETREAT, now);
					} else {
						e.orbit = -e.orbit;
						e.setPhase(PantherShadeEntity.STALK, now);
					}
				}
			}
			case PantherShadeEntity.RETREAT -> {
				double k = Math.min(1.0, (age + 1) / (double) ShadeTrial.RETREAT_TICKS);
				Vec3 pos = e.from.lerp(e.to, k).add(0, 0.9 * Math.sin(Math.PI * k), 0);
				e.setPos(pos.x, pos.y, pos.z);
				e.face(me);
				if (k >= 1.0) {
					e.setPhase(PantherShadeEntity.STALK, now);
				}
			}
			default -> {
			}
		}
	}

	/** Keeps a shade on the open floor west of the pedestal. */
	private static Vec3 keepOnFloor(Vec3 v, Vec3 arena) {
		double cx = arena.x + 3; // the shrine centre
		double dx = v.x - cx, dz = v.z - arena.z;
		double r = Math.hypot(dx, dz);
		double max = PlaneGeometry.SHRINE_R - 1.0;
		if (r > max) {
			dx *= max / r;
			dz *= max / r;
		}
		double x = Math.min(cx + dx, cx + PlaneGeometry.PEDESTAL_DX - 5.5);
		return new Vec3(x, v.y, arena.z + dz);
	}

	/** A shade was struck by its dreamer. True if the blow counts (the shade dissolves). */
	static boolean shadeStruck(ServerPlayer p, PantherShadeEntity e) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.stage != Stage.TRIAL || s.trial == null || !s.shades.contains(e.getUUID())) {
			return false;
		}
		ServerLevel realm = PlaneRealm.level(p.server);
		dissolve(p, realm, e);
		s.shades.remove(e.getUUID());
		ShadeTrial.Outcome out = s.trial.kill();
		notify(p, SoundEvents.AMETHYST_BLOCK_BREAK, 0.9f, 1.4f);
		if (out == ShadeTrial.Outcome.ROUND_WON || out == ShadeTrial.Outcome.TRIAL_WON) {
			clearShades(p, s, realm);
			s.trialPauseUntil = realm.getGameTime() + (out == ShadeTrial.Outcome.TRIAL_WON ? 50 : ShadeTrial.BREATHER_TICKS);
			s.nextSpawn = s.trialPauseUntil;
			s.hudEvent = 1;
			notify(p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, out == ShadeTrial.Outcome.TRIAL_WON ? 1.2f : 0.9f);
			notify(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 1.0f);
		}
		return true;
	}

	private static void roundLost(ServerPlayer p, Session s, ServerLevel realm) {
		clearShades(p, s, realm);
		s.trialPauseUntil = realm.getGameTime() + ShadeTrial.BREATHER_TICKS;
		s.nextSpawn = s.trialPauseUntil;
		notify(p, SoundEvents.WARDEN_HEARTBEAT, 1.0f, 0.8f);
		p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.round_lost").withStyle(ChatFormatting.LIGHT_PURPLE), true);
	}

	private static void dissolve(ServerPlayer p, ServerLevel realm, PantherShadeEntity e) {
		realm.sendParticles(p, SHADOW, true, e.getX(), e.getY() + 0.5, e.getZ(), 30, 0.45, 0.35, 0.45, 0.04);
		realm.sendParticles(p, VIOLET, true, e.getX(), e.getY() + 0.6, e.getZ(), 14, 0.35, 0.3, 0.35, 0.03);
		play(realm, e.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.8f);
		e.discard();
	}

	private static void clearShades(ServerPlayer p, Session s, ServerLevel realm) {
		for (UUID id : new ArrayList<>(s.shades)) {
			if (realm.getEntity(id) instanceof PantherShadeEntity e) {
				dissolve(p, realm, e);
			}
		}
		s.shades.clear();
	}

	// ---------------------------------------------------------------- the spirit path

	private static void startPath(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		clearShades(p, s, realm);
		Vec3 at = PlaneGeometry.arena(o);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
		setStage(p, s, Stage.PATH);
		hold(s, at, PlaneGeometry.FACE_EAST, 0f);
	}

	private static void path(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		hold(s, PlaneGeometry.arena(o), PlaneGeometry.FACE_EAST, 0f);
		if (beat(s, t, PlaneScene.PATH_LINE)) {
			bastVoice(realm, PlaneGeometry.statueHead(o));
		}
		if (t >= PlaneScene.PATH_BUILD_FROM) {
			double k = Mth.clamp((t - PlaneScene.PATH_BUILD_FROM) / (double) (PlaneScene.PATH_BUILD_TO - PlaneScene.PATH_BUILD_FROM), 0.0, 1.0);
			double upTo = k * ParkourCourse.finish().x0();
			int before = builtCount(realm, o);
			PlaneRealm.buildPath(realm, o, upTo);
			int after = builtCount(realm, o);
			if (after > before) {
				ParkourCourse.Platform last = ParkourCourse.platforms().get(after - 1);
				Vec3 c = PlaneGeometry.at(o, PlaneGeometry.PATH_X + last.centreX(), PlaneGeometry.PATH_BASE + last.y() + 1, last.centreZ());
				realm.sendParticles(p, VIOLET, true, c.x, c.y, c.z, 16, last.sx() * 0.3, 0.3, last.sz() * 0.3, 0.02);
				play(realm, c, SoundEvents.AMETHYST_BLOCK_PLACE, 1.6f, 0.8f + 0.04f * after);
			}
		}
		if (t >= PlaneScene.PATH.length) {
			markSeen(p, s.stage);
			startParkour(p, s, realm, o);
		}
	}

	private static int builtCount(ServerLevel realm, BlockPos o) {
		int n = 0;
		for (ParkourCourse.Platform pl : ParkourCourse.platforms()) {
			if (realm.getBlockState(o.offset(PlaneGeometry.PATH_X + pl.x0(), PlaneGeometry.PATH_BASE + pl.y(), pl.z0())).isAir()) {
				break;
			}
			n++;
		}
		return n;
	}

	static Vec3 checkpointSpot(BlockPos o, int cp) {
		double[] r = ParkourCourse.respawn(cp);
		return PlaneGeometry.at(o, PlaneGeometry.PATH_X + r[0], PlaneGeometry.PATH_BASE + r[1], r[2]);
	}

	private static void startParkour(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		PlaneRealm.buildPath(realm, o, Double.MAX_VALUE);
		clearShades(p, s, realm);
		s.checkpoint = 0;
		Vec3 at = checkpointSpot(o, 0);
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 8f);
		p.fallDistance = 0f;
		p.setDeltaMovement(Vec3.ZERO);
		setStage(p, s, Stage.PARKOUR);
		p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.parkour").withStyle(ChatFormatting.GOLD), true);
	}

	private static void parkour(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		double rx = p.getX() - (o.getX() + PlaneGeometry.PATH_X);
		double ry = p.getY() - (o.getY() + PlaneGeometry.PATH_BASE);
		double rz = p.getZ() - o.getZ();
		p.fallDistance = 0f;
		int reached = ParkourCourse.reachedCheckpoint(rx, ry, rz);
		if (reached > s.checkpoint) {
			s.checkpoint = ParkourCourse.advance(s.checkpoint, reached);
			s.hudEvent = 1;
			notify(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
			notify(p, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f);
			realm.sendParticles(p, GOLD, true, p.getX(), p.getY() + 0.3, p.getZ(), 30, 1.2, 0.2, 1.2, 0.0);
		}
		if (ParkourCourse.onFinish(rx, ry, rz)) {
			startFinale(p, s, realm, o);
			return;
		}
		if (ParkourCourse.fell(ry, -PlaneGeometry.PATH_BASE, s.checkpoint)) {
			Vec3 at = checkpointSpot(o, s.checkpoint);
			p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 8f);
			p.fallDistance = 0f;
			p.setDeltaMovement(Vec3.ZERO);
			s.hudEvent = 2;
			notify(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7f, 1.2f);
			realm.sendParticles(p, VIOLET, true, at.x, at.y + 0.6, at.z, 20, 0.4, 0.6, 0.4, 0.02);
		}
	}

	// ---------------------------------------------------------------- the finale

	private static void startFinale(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		ParkourCourse.Platform f = ParkourCourse.finish();
		Vec3 at = PlaneGeometry.at(o, PlaneGeometry.PATH_X + f.centreX() - 1.0, PlaneGeometry.PATH_BASE + f.y() + 1, f.centreZ());
		p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, -8f);
		setStage(p, s, Stage.FINALE);
		hold(s, at, PlaneGeometry.FACE_EAST, -8f);
		// the ancestors are waiting at the end of the path, either side
		for (int i = 0; i < PlaneGeometry.ANCESTOR_COUNT; i++) {
			AncestorEntity a = ancestor(p.server, s, i);
			if (a == null) {
				continue;
			}
			int side = i % 2 == 0 ? -1 : 1;
			double row = i == 0 ? 0 : (i + 1) / 2;
			Vec3 spot = i == 0 ? at.add(1.5, 0, -2.2) : at.add(-0.8 - row * 1.3 + 2.0, 0, side * (1.6 + row * 0.9));
			a.teleportTo(spot.x, spot.y, spot.z);
			a.place(spot, PlaneGeometry.lookYaw(spot, at), at.add(0, 1.6, 0));
		}
		notify(p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f);
	}

	private static void finale(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		Vec3 me = p.position();
		if (s.hold != null) {
			me = s.hold;
		}
		Vec3 sp = PlaneGeometry.spirit(o);
		if (t >= PlaneScene.FINALE_SPIRIT && spirit(p.server, s) == null && PlaneRealm.live(realm, sp)) {
			PantherShadeEntity e = SHADE.create(realm);
			if (e != null) {
				e.makeSpirit(9.0f);
				e.moveTo(sp.x, sp.y, sp.z, 0f, 0f);
				e.face(me);
				e.setPhase(PantherShadeEntity.APPEAR, (int) (realm.getGameTime() & 0x3FFFFFFF));
				realm.addFreshEntity(e);
				s.spirit = e.getUUID();
				sendScene(p, s, PlaneScene.FINALE, (int) t); // with the spirit's id now
				play(realm, me, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.7f);
				play(realm, me, SoundEvents.ENDER_DRAGON_GROWL, 0.3f, 1.4f);
			}
		}
		PantherShadeEntity spirit = spirit(p.server, s);
		if (spirit != null) {
			double bob = Math.sin(t * 0.05) * 0.4;
			spirit.setPos(sp.x, sp.y + bob, sp.z);
			spirit.face(me);
			if (t % 3 == 0) {
				realm.sendParticles(p, GOLD, true, sp.x, sp.y + 3, sp.z, 6, 3.0, 2.0, 3.0, 0.0);
			}
		}
		ancestorGlow(p, s, realm, t);
		if (beat(s, t, PlaneScene.FINALE_LINE1) || beat(s, t, PlaneScene.FINALE_LINE2)) {
			bastVoice(realm, sp);
		}
		if (beat(s, t, PlaneScene.FINALE_LINE3)) {
			AncestorEntity eldest = ancestor(p.server, s, 0);
			voice(realm, eldest == null ? me : eldest.position());
		}
		if (beat(s, t, PlaneScene.FINALE_GOLD)) {
			notify(p, SoundEvents.TOTEM_USE, 0.6f, 1.1f);
			notify(p, SoundEvents.BEACON_POWER_SELECT, 1.0f, 0.8f);
		}
		if (t >= PlaneScene.FINALE_HOME) {
			markSeen(p, s.stage);
			end(p.server, s);
			returnHome(p, true);
		}
	}

	// ---------------------------------------------------------------- skipping, leaving, ending

	/** Space during a scene seen before: straight to its end. */
	public static boolean skip(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return false;
		}
		PlaneScene scene = sceneOf(s.stage);
		if (scene == null || !scene.skippable || (p.getAttachedOrElse(SEEN, 0) & scene.bit()) == 0) {
			return false;
		}
		int end = s.stage == Stage.FINALE ? PlaneScene.FINALE_HOME : scene.length;
		long now = p.level().getGameTime();
		s.stageStart = now - end;
		s.lastT = end - 1;
		if (s.stage == Stage.TRIAL_INTRO) {
			ServerLevel realm = PlaneRealm.level(p.server);
			PlaneRealm.eyes(realm, PlaneRealm.origin(realm, s.slot), true);
		}
		return true;
	}

	/** Wakes the dreamer early: back in bed, no power. */
	public static boolean leave(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null) {
			return false;
		}
		end(p.server, s);
		returnHome(p, false);
		return true;
	}

	/** Ends a dream without moving anyone: its ancestors, shades and spirit go. The return point is left alone. */
	private static void end(MinecraftServer server, Session s) {
		SESSIONS.remove(s.player);
		ServerLevel l = PlaneRealm.level(server);
		for (UUID id : s.ancestors) {
			if (id != null && l.getEntity(id) instanceof Entity e) {
				e.discard();
			}
		}
		for (UUID id : s.shades) {
			if (l.getEntity(id) instanceof Entity e) {
				e.discard();
			}
		}
		if (s.spirit != null && l.getEntity(s.spirit) instanceof Entity e) {
			e.discard();
		}
		ServerPlayer p = server.getPlayerList().getPlayer(s.player);
		if (p != null && p.connection != null && ServerPlayNetworking.canSend(p, PlanePayloads.Hud.TYPE)) {
			ServerPlayNetworking.send(p, PlanePayloads.Hud.none());
		}
	}

	/** Back to bed (or where they fell asleep): lying down a moment, the dark lifting. {@code completed}: the power. */
	public static void returnHome(ServerPlayer p, boolean completed) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		PlanePayloads.Return r = p.getAttachedOrElse(RETURN, null);
		ServerLevel level = null;
		if (r != null) {
			ResourceLocation id = ResourceLocation.tryParse(r.dimension());
			level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		Vec3 at;
		float yaw = p.getYRot(), pitch = 0f;
		BlockPos bed = null;
		if (level == null || level.dimension() == PlaneRealm.DIMENSION) {
			level = server.overworld();
			at = spawn(level);
		} else {
			at = new Vec3(r.x(), r.y(), r.z());
			yaw = r.yRot();
			pitch = r.xRot();
			bed = r.bed().orElse(null);
		}
		p.removeAttached(RETURN);
		p.stopRiding();
		p.teleportTo(level, at.x, at.y, at.z, yaw, pitch);
		p.fallDistance = 0f;
		p.setDeltaMovement(Vec3.ZERO);
		if (bed != null && level.getBlockState(bed).getBlock() instanceof BedBlock && !p.isSleeping()) {
			p.startSleeping(bed);
			PENDING_WAKE.put(p.getUUID(), level.getGameTime() + WAKE_LIE);
		}
		if (p.connection != null && ServerPlayNetworking.canSend(p, PlanePayloads.Scene.TYPE)) {
			ServerPlayNetworking.send(p, new PlanePayloads.Scene(PlaneScene.WAKE.id, 0, at.x, at.y, at.z, -1, false));
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 0.8f);
		if (completed) {
			if (!BlackPanther.grant(p)) {
				p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.wake_no_power").withStyle(ChatFormatting.GRAY), false);
			}
		} else {
			p.displayClientMessage(Component.translatable("message.projecthero.ancestral_plane.wake_early").withStyle(ChatFormatting.GRAY), false);
		}
	}

	private static Vec3 spawn(ServerLevel level) {
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/** Logging out mid-dream: it ends (ancestors, shades); the saved return point puts them back in bed next login. */
	public static void onDisconnect(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && p.getServer() != null) {
			end(p.getServer(), s);
		}
		PENDING_WAKE.remove(p.getUUID());
	}

	/** A player with no live dream but a saved return point goes back; one in the plane with nothing saved goes to spawn. */
	public static void check(ServerPlayer p) {
		if (SESSIONS.containsKey(p.getUUID())) {
			return;
		}
		if (p.getAttachedOrElse(RETURN, null) != null) {
			returnHome(p, false);
		} else if (p.level().dimension() == PlaneRealm.DIMENSION && p.getServer() != null) {
			ServerLevel overworld = p.getServer().overworld();
			Vec3 at = spawn(overworld);
			p.teleportTo(overworld, at.x, at.y, at.z, p.getYRot(), p.getXRot());
		}
	}

	// ---------------------------------------------------------------- the HUD

	private static void sendHud(ServerPlayer p, Session s, ServerLevel realm, BlockPos o, long now) {
		if (p.connection == null || !ServerPlayNetworking.canSend(p, PlanePayloads.Hud.TYPE)) {
			return;
		}
		int wake = s.sneakTicks >= 20 ? Math.min(100, s.sneakTicks * 100 / WAKE_HOLD) : 0;
		PlanePayloads.Hud h;
		switch (s.stage) {
			case WANDER -> h = new PlanePayloads.Hud(1, 0, 0, 0, 0, 0, wake);
			case SHRINE -> h = new PlanePayloads.Hud(2, 0, 0, 0, 0, 0, wake);
			case TRIAL -> {
				ShadeTrial tr = s.trial;
				h = tr == null ? PlanePayloads.Hud.none()
						: new PlanePayloads.Hud(3, Math.min(tr.round(), ShadeTrial.ROUNDS - 1), tr.won() ? ShadeTrial.shadesIn(2) : tr.killed(),
								tr.won() ? ShadeTrial.shadesIn(2) : tr.total(), tr.wounds(), s.hudEvent, wake);
			}
			case PARKOUR -> {
				double rx = p.getX() - (o.getX() + PlaneGeometry.PATH_X);
				h = new PlanePayloads.Hud(4, s.checkpoint, ParkourCourse.checkpointCount(), (int) Math.round(ParkourCourse.progress(rx) * 1000), 0, s.hudEvent, wake);
			}
			default -> h = PlanePayloads.Hud.none();
		}
		boolean changed = !h.equals(s.lastHud);
		if (changed || now - s.lastHudAt > 40) {
			ServerPlayNetworking.send(p, h);
			s.lastHud = h;
			s.lastHudAt = now;
			s.hudEvent = 0;
		}
	}

	// ---------------------------------------------------------------- sounds

	/** An ancestor speaks: a soft resonant chime under the subtitle. */
	private static void voice(ServerLevel level, Vec3 at) {
		play(level, at.add(0, 1.6, 0), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9f, 0.7f);
		play(level, at.add(0, 1.6, 0), SoundEvents.BEACON_AMBIENT, 0.6f, 1.2f);
	}

	/** Bast speaks: a low purr and the beacon's hum. */
	private static void bastVoice(ServerLevel level, Vec3 at) {
		play(level, at, SoundEvents.CAT_PURREOW, 1.6f, 0.45f);
		play(level, at, SoundEvents.BEACON_AMBIENT, 1.2f, 0.7f);
	}

	private static void notify(ServerPlayer p, SoundEvent sound, float volume, float pitch) {
		p.playNotifySound(sound, SoundSource.AMBIENT, volume, pitch);
	}

	private static void play(ServerLevel level, Vec3 at, SoundEvent sound, float volume, float pitch) {
		level.playSound(null, at.x, at.y, at.z, sound, SoundSource.AMBIENT, volume, pitch);
	}

	private static void shake(ServerPlayer p, float intensity, int ticks) {
		if (p.connection != null && ServerPlayNetworking.canSend(p, TitanShakePayload.TYPE)) {
			ServerPlayNetworking.send(p, new TitanShakePayload(intensity, ticks));
		}
	}

	// ---------------------------------------------------------------- test / command hooks

	/** Jumps a dream (starting one where {@code p} stands if needed) to {@code stage}: SHRINE, TRIAL_INTRO, TRIAL, PARKOUR or FINALE. */
	public static boolean jumpTo(ServerPlayer p, Stage stage) {
		if (!SESSIONS.containsKey(p.getUUID()) && !beginHere(p)) {
			return false;
		}
		Session s = SESSIONS.get(p.getUUID());
		ServerLevel realm = PlaneRealm.level(p.server);
		BlockPos o = PlaneRealm.origin(realm, s.slot);
		PlaneRealm.buildNow(realm, s.slot);
		clearShades(p, s, realm);
		switch (stage) {
			case SHRINE -> {
				Vec3 at = PlaneGeometry.shrineEntry(o);
				p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
				enterShrineStage(p, s, realm, o);
			}
			case TRIAL_INTRO -> startTrialIntro(p, s, realm, o);
			case TRIAL -> {
				s.trial = null;
				startTrial(p, s, realm, o);
				Vec3 at = PlaneGeometry.arena(o);
				p.teleportTo(realm, at.x, at.y, at.z, PlaneGeometry.FACE_EAST, 0f);
			}
			case PATH -> startPath(p, s, realm, o);
			case PARKOUR -> startParkour(p, s, realm, o);
			case FINALE -> {
				PlaneRealm.buildPath(realm, o, Double.MAX_VALUE);
				startFinale(p, s, realm, o);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Test hook: the current stage {@code ticks} further on (beats in between still fire). */
	public static void fastForward(ServerPlayer p, int ticks) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.stageStart -= ticks;
			s.trialPauseUntil -= ticks;
			s.nextSpawn -= ticks;
		}
	}

	/** Test hook: the right-click on the shrine's heart (the altar). */
	public static boolean touchShrineForTest(ServerPlayer p) {
		BlockPos o = origin(p);
		return o != null && shrineClicked(p, o.offset(PlaneGeometry.SHRINE_X + PlaneGeometry.ALTAR_DX, PlaneGeometry.SHRINE_FLOOR, 0));
	}

	/** Test hook: the dreamer strikes this shade. */
	public static boolean strikeForTest(ServerPlayer p, PantherShadeEntity e) {
		return shadeStruck(p, e);
	}

	/** Test hook: the trial jumps to round {@code r} (3 = won). */
	public static void setTrialRound(ServerPlayer p, int r) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.trial != null) {
			s.trial.setRound(r);
			ServerLevel realm = PlaneRealm.level(p.server);
			clearShades(p, s, realm);
			s.trialPauseUntil = realm.getGameTime();
		}
	}
}
