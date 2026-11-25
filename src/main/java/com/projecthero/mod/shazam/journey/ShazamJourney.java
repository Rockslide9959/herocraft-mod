package com.projecthero.mod.shazam.journey;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.Codec;
import com.projecthero.mod.ProjectHeroMod;
import com.projecthero.mod.item.ModCreativeTab;
import com.projecthero.mod.network.TitanShakePayload;
import com.projecthero.mod.shazam.Shazam;
import com.projecthero.mod.shazam.ShazamFx;
import com.projecthero.mod.shazam.ShazamWord;
import com.projecthero.mod.shazam.entity.ShazamEntities;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.7 Shazam acquisition (user: "once a player becomes worthy they can randomly see illusions of the train stairway,
 * approaching it transports them to the subway and then they can hop onto the train, the train then takes them to the cave
 * with the wizard shazam and then approaching him he will tell you to say his name and then the player transforms for the
 * first time, i want there to be cutscenes for this stuff too"). The story, one stage after another, server-authoritative:
 *
 * <ol>
 * <li>{@link Stage#DESCENT} -- the player reached the illusion ({@link ShazamIllusions}): held still while the screen darkens
 * and the camera looks down the stairs; a gust, a horn in the distance. Their realm slot is built meanwhile
 * ({@link EternityRealm#queue}).</li>
 * <li>{@link Stage#TRAIN} -- they stand at the foot of the stairs on the platform of the old station; the train's lights grow
 * in the tunnel, its horn, the wind, the screech of the brakes; it stops and the doors open.</li>
 * <li>{@link Stage#WAITING} -- free to walk; stepping into a doorway (or right-clicking the train) boards it.</li>
 * <li>{@link Stage#RIDE} -- seated; the doors close, the lamps streak past, glyphs, a lightning flash, raw rock, the cave.</li>
 * <li>{@link Stage#CAVE} -- they step off into the Rock of Eternity; walking up to the Wizard on his throne (or right-clicking
 * him) starts...</li>
 * <li>{@link Stage#MEETING} -- he rises and speaks;</li>
 * <li>{@link Stage#PROMPT} -- "Say my name": the prompt's button, or "Shazam" typed in chat;</li>
 * <li>{@link Stage#TRANSFORM} -- SHAZAM! The colossal bolt, the power ({@link Shazam#grant}: the one Primary) and the first
 * transformation ({@link Shazam#speak}), his last words, then a white flash and they are back where they first saw the
 * illusion -- turned back into themselves ({@link Shazam#forceUntransform}), ready to say it on their own.</li>
 * </ol>
 *
 * <p><b>Safety:</b> nobody in a journey takes damage; nothing in the realm can be broken, placed or used; falling out of the
 * build puts you back; the cutscenes hold you in place server-side and every client camera lock times out by itself. Where
 * they started is saved on the player ({@link #RETURN}): logging out, a crash or a restart mid-way ends the journey and their
 * next login sends them home (a player found in the realm with nothing saved goes to world spawn). The live journeys are a
 * static map of player UUIDs (no worlds or entities), cleared by {@code ServerStateReset}.
 */
public final class ShazamJourney {
	// ---------------------------------------------------------------- registration

	/** Where the player first saw the illusion; set for the whole journey, cleared once they're home. */
	public static final AttachmentType<JourneyPayloads.Return> RETURN = AttachmentRegistry.create(ProjectHeroMod.id("shazam_journey_return"),
			builder -> builder.persistent(JourneyPayloads.Return.CODEC));

	/** Overworld game time before which no illusion can appear (the minute between rolls, the cooldown after one). */
	public static final AttachmentType<Long> ILLUSION_READY = AttachmentRegistry.create(ProjectHeroMod.id("shazam_illusion_ready"),
			builder -> builder.persistent(Codec.LONG).copyOnDeath().initializer(() -> 0L));

	public static final EntityType<SubwayTrainEntity> TRAIN = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("subway_train")),
			EntityType.Builder.<SubwayTrainEntity>of(SubwayTrainEntity::new, MobCategory.MISC).sized(2.9f, 4.0f).noSave().noSummon()
					.clientTrackingRange(16).updateInterval(20).fireImmune().build("subway_train"));

	public static final EntityType<ThroneWizardEntity> THRONE_WIZARD = Registry.register(BuiltInRegistries.ENTITY_TYPE,
			ResourceKey.create(Registries.ENTITY_TYPE, ProjectHeroMod.id("wizard_shazam_throne")),
			EntityType.Builder.<ThroneWizardEntity>of(ThroneWizardEntity::new, MobCategory.MISC).sized(0.6f, 1.95f).eyeHeight(1.62f).noSave()
					.clientTrackingRange(10).build("wizard_shazam_throne"));

	/** Creative only: a Wizard Shazam (the old visitor; right-click him and he judges you -- the admin way to the power). */
	public static final Item WIZARD_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, ProjectHeroMod.id("wizard_shazam_spawn_egg"),
			new SpawnEggItem(ShazamEntities.WIZARD, 0x8E2A1E, 0xE8D9B0, new Item.Properties()));

	public enum Stage {
		DESCENT, TRAIN, WAITING, RIDE, CAVE, MEETING, PROMPT, TRANSFORM
	}

	private static final class Session {
		final UUID player;
		final int slot;
		final ResourceKey<Level> realm;
		Stage stage = Stage.DESCENT;
		int serial;
		long stageStart;
		long lastT = -1;
		/** Where they are held for the current scene (null: free to move). */
		Vec3 hold;
		float holdYaw, holdPitch;
		Vec3 illusion;
		float illusionYaw;
		UUID train;
		UUID wizard;
		boolean granted;
		long nextArc;

		Session(UUID player, int slot, ResourceKey<Level> realm) {
			this.player = player;
			this.slot = slot;
			this.realm = realm;
		}
	}

	private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

	private ShazamJourney() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(THRONE_WIZARD, ThroneWizardEntity.createAttributes());
		PayloadTypeRegistry.playS2C().register(JourneyPayloads.Scene.TYPE, JourneyPayloads.Scene.CODEC);
		PayloadTypeRegistry.playS2C().register(JourneyPayloads.Illusion.TYPE, JourneyPayloads.Illusion.CODEC);
		PayloadTypeRegistry.playC2S().register(JourneyPayloads.Answer.TYPE, JourneyPayloads.Answer.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(JourneyPayloads.Answer.TYPE, (payload, context) -> answer(context.player()));
		ServerTickEvents.END_SERVER_TICK.register(ShazamJourney::tick);
		// "Shazam" typed in chat answers the prompt (and is never posted)
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			if (!wouldAnswer(sender, message.signedContent())) {
				return true;
			}
			sender.server.execute(() -> answer(sender));
			return false;
		});
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
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> player instanceof ServerPlayer sp && guarded(sp) ? InteractionResult.FAIL
				: InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> player instanceof ServerPlayer sp && guarded(sp)
				? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !(player instanceof ServerPlayer sp && guarded(sp)));
		UseItemCallback.EVENT.register((player, level, hand) -> player instanceof ServerPlayer sp && guarded(sp)
				? InteractionResultHolder.fail(player.getItemInHand(hand)) : InteractionResultHolder.pass(player.getItemInHand(hand)));
		ItemGroupEvents.modifyEntriesEvent(ModCreativeTab.SUPERHEROES_KEY).register(entries -> entries.accept(WIZARD_SPAWN_EGG));
	}

	public static void clearSessionState() {
		SESSIONS.clear();
		ShazamIllusions.clearSessionState();
		EternityRealm.clearSessionState();
	}

	// ---------------------------------------------------------------- queries

	public static boolean inJourney(ServerPlayer player) {
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

	/** In a journey, or anywhere in the realm dimension: no damage, no building. */
	static boolean guarded(ServerPlayer p) {
		return SESSIONS.containsKey(p.getUUID()) || p.level().dimension() == EternityRealm.DIMENSION;
	}

	public static SubwayTrainEntity train(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : train(player.server, s);
	}

	public static ThroneWizardEntity wizard(ServerPlayer player) {
		Session s = SESSIONS.get(player.getUUID());
		return s == null ? null : wizard(player.server, s);
	}

	private static SubwayTrainEntity train(MinecraftServer server, Session s) {
		ServerLevel l = server.getLevel(s.realm);
		return l != null && s.train != null && l.getEntity(s.train) instanceof SubwayTrainEntity t ? t : null;
	}

	private static ThroneWizardEntity wizard(MinecraftServer server, Session s) {
		ServerLevel l = server.getLevel(s.realm);
		return l != null && s.wizard != null && l.getEntity(s.wizard) instanceof ThroneWizardEntity w ? w : null;
	}

	/** Where {@code player} stands now, as a return point. */
	static JourneyPayloads.Return returnOf(ServerPlayer player) {
		return new JourneyPayloads.Return(player.level().dimension().location().toString(), player.getX(), player.getY(), player.getZ(),
				player.getYRot(), player.getXRot());
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

	// ---------------------------------------------------------------- starting

	/** The player walked up to their illusion: the descent begins. False if they can't go (already in one). */
	public static boolean begin(ServerPlayer player, ShazamIllusions.Live illusion) {
		if (SESSIONS.containsKey(player.getUUID()) || !player.isAlive()) {
			return false;
		}
		ServerLevel realm = EternityRealm.level(player.server);
		Session s = new Session(player.getUUID(), freeSlot(), realm.dimension());
		s.illusion = illusion.pos();
		s.illusionYaw = illusion.yaw();
		player.setAttached(RETURN, illusion.seenFrom() != null ? illusion.seenFrom() : returnOf(player));
		SESSIONS.put(player.getUUID(), s);
		EternityRealm.queue(realm, s.slot);
		EternityRealm.hold(realm, s.slot);
		player.stopRiding();
		setStage(player, s, Stage.DESCENT);
		hold(s, player.position(), lookYaw(player.getEyePosition(), illusion.pos()), 30f);
		return true;
	}

	/** Test hook: the journey from the illusion at {@code pos}, without waiting for a roll or a walk. */
	public static boolean beginAt(ServerPlayer player, Vec3 pos, float yaw) {
		return begin(player, ShazamIllusions.showAt(player, pos, yaw)) && removeIllusion(player);
	}

	private static boolean removeIllusion(ServerPlayer player) {
		ShazamIllusions.dismiss(player);
		return true;
	}

	private static void setStage(ServerPlayer p, Session s, Stage stage) {
		s.stage = stage;
		s.serial++;
		s.stageStart = p.level().getGameTime();
		s.lastT = -1;
		s.hold = null;
		JourneyScene scene = switch (stage) {
			case DESCENT -> JourneyScene.DESCENT;
			case TRAIN -> JourneyScene.TRAIN;
			case RIDE -> JourneyScene.RIDE;
			case MEETING -> JourneyScene.MEETING;
			case PROMPT -> JourneyScene.PROMPT;
			case TRANSFORM -> JourneyScene.TRANSFORM;
			default -> null;
		};
		if (scene != null) {
			sendScene(p, s, scene, 0, scene == JourneyScene.PROMPT);
		}
	}

	/** {@code open}: for the prompt, (re)open its screen (a refresh only keeps it alive). */
	private static void sendScene(ServerPlayer p, Session s, JourneyScene scene, int elapsed, boolean open) {
		if (p.connection == null || !ServerPlayNetworking.canSend(p, JourneyPayloads.Scene.TYPE)) {
			return;
		}
		Vec3 a;
		float yaw = 0f;
		int entity = -1;
		if (scene == JourneyScene.DESCENT) {
			a = s.illusion;
			yaw = s.illusionYaw;
		} else {
			BlockPos o = EternityRealm.origin(EternityRealm.level(p.server), s.slot);
			a = new Vec3(o.getX(), o.getY(), o.getZ());
			Entity e = scene == JourneyScene.TRAIN || scene == JourneyScene.RIDE ? train(p.server, s) : wizard(p.server, s);
			entity = e == null ? -1 : e.getId();
			yaw = open ? 1f : 0f;
		}
		ServerPlayNetworking.send(p, new JourneyPayloads.Scene(scene.id, elapsed, a.x, a.y, a.z, yaw, entity));
	}

	private static void hold(Session s, Vec3 at, float yaw, float pitch) {
		s.hold = at;
		s.holdYaw = yaw;
		s.holdPitch = pitch;
	}

	static float lookYaw(Vec3 from, Vec3 to) {
		return (float) (Math.atan2(-(to.x - from.x), to.z - from.z) * 180.0 / Math.PI);
	}

	// ---------------------------------------------------------------- the tick

	private static void tick(MinecraftServer server) {
		EternityRealm.tick(server);
		ShazamIllusions.tick(server);
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
				ProjectHeroMod.LOGGER.error("[ShazamJourney] step failed; sending {} home", p.getGameProfile().getName(), e);
				end(server, s);
				returnHome(p);
			}
		}
		if (now % 10L == 0L) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				check(p);
			}
		}
	}

	/** True if the tick just crossed beat {@code at}. */
	private static boolean beat(Session s, long t, int at) {
		return s.lastT < at && t >= at;
	}

	private static void step(ServerPlayer p, Session s) {
		long now = p.level().getGameTime();
		long t = now - s.stageStart;
		int serial = s.serial;
		ServerLevel realm = EternityRealm.level(p.server);
		BlockPos o = EternityRealm.origin(realm, s.slot);
		boolean inRealm = p.level() == realm && (!EternityRealm.isFallback(realm) || EternityRealm.inBox(realm, s.slot, p.position())
				|| s.stage == Stage.DESCENT);
		if (s.stage != Stage.DESCENT && !inRealm) {
			// taken out some other way (a command, a portal): the journey is over, nothing more to do
			end(p.server, s);
			p.removeAttached(RETURN);
			return;
		}
		if (now % 20L == 0L) {
			EternityRealm.hold(realm, s.slot);
		}
		if (s.stage == Stage.TRAIN || s.stage == Stage.WAITING) {
			EternityRealm.flicker(realm, s.slot);
		}
		// fell out of the build (or got stuck below it): back onto the floor
		if (s.stage != Stage.DESCENT && s.stage != Stage.RIDE && p.getY() < o.getY() - 6) {
			Vec3 safe = s.stage.ordinal() >= Stage.CAVE.ordinal() ? JourneyGeometry.caveStepOff(o) : JourneyGeometry.platformArrival(o);
			p.teleportTo(realm, safe.x, safe.y, safe.z, p.getYRot(), p.getXRot());
			p.fallDistance = 0f;
		}
		switch (s.stage) {
			case DESCENT -> descent(p, s, t, realm, o);
			case TRAIN -> trainArrives(p, s, t, realm, o);
			case WAITING -> waiting(p, s, t, realm, o);
			case RIDE -> ride(p, s, t, realm, o);
			case CAVE -> cave(p, s, t, realm, o);
			case MEETING -> meeting(p, s, t, realm, o);
			case PROMPT -> prompt(p, s, t, realm, o);
			case TRANSFORM -> transform(p, s, t, realm, o);
		}
		if (s.stage.ordinal() >= Stage.CAVE.ordinal()) {
			ceilingLightning(realm, o, s, now);
		}
		Session still = SESSIONS.get(p.getUUID());
		if (still == s && s.serial == serial) {
			s.lastT = t;
		}
		// the scene's hold
		if (still == s && s.hold != null && p.level() == (s.stage == Stage.DESCENT ? p.level() : realm)) {
			p.setDeltaMovement(Vec3.ZERO);
			p.hurtMarked = true;
			p.fallDistance = 0f;
			if (p.position().distanceToSqr(s.hold) > 0.09) {
				p.connection.teleport(s.hold.x, s.hold.y, s.hold.z, s.holdYaw, s.holdPitch);
			}
		}
	}

	// ---------------------------------------------------------------- the stages

	private static void descent(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		if (beat(s, t, 0)) {
			notify(p, SoundEvents.SOUL_ESCAPE.value(), 0.9f, 0.6f);
			notify(p, SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), 0.5f, 0.7f);
		}
		if (beat(s, t, 8)) {
			notify(p, SoundEvents.ELYTRA_FLYING, 0.45f, 0.7f);
		}
		if (beat(s, t, JourneyScene.DESCENT_HORN)) {
			notify(p, horn(), 0.35f, 0.6f);
		}
		if (beat(s, t, 42)) {
			notify(p, horn(), 0.6f, 0.58f);
			notify(p, SoundEvents.MINECART_RIDING, 0.4f, 0.5f);
		}
		if (t >= JourneyScene.DESCENT_GO && EternityRealm.stationReady(realm, s.slot)) {
			Vec3 at = JourneyGeometry.platformArrival(o);
			p.stopRiding();
			p.teleportTo(realm, at.x, at.y, at.z, 90f, 0f);
			p.fallDistance = 0f;
			p.setDeltaMovement(Vec3.ZERO);
			setStage(p, s, Stage.TRAIN);
			hold(s, at, 90f, 0f);
			trySpawnTrain(p, s, realm, o);
			return;
		}
		if (t > 20 * 60) {
			// the realm never got built (should not happen): give up quietly
			end(p.server, s);
			returnHome(p);
		}
	}

	/**
	 * Puts the train deep in the west tunnel for its arrival -- once that chunk is entity-ticking (a fresh realm may still be
	 * loading it); the arrival starts {@link JourneyScene#TRAIN_ARRIVE} into the scene, or at once if that's already past.
	 */
	private static boolean trySpawnTrain(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		if (train(p.server, s) != null) {
			return true;
		}
		if (!EternityRealm.live(realm, s.slot, JourneyGeometry.ARRIVE_FROM) || !EternityRealm.live(realm, s.slot, 0)) {
			return false;
		}
		spawnTrain(p, s, realm, o);
		SubwayTrainEntity train = train(p.server, s);
		if (train != null) {
			train.setMode(SubwayTrainEntity.ARRIVING, Math.max(realm.getGameTime(), s.stageStart + JourneyScene.TRAIN_ARRIVE));
			// the scene payload went out before the train existed: send it again with the train's id
			sendScene(p, s, JourneyScene.TRAIN, (int) (realm.getGameTime() - s.stageStart), false);
		}
		return train != null;
	}

	private static void spawnTrain(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		SubwayTrainEntity old = train(p.server, s);
		if (old != null) {
			old.discard();
		}
		SubwayTrainEntity train = TRAIN.create(realm);
		if (train == null) {
			return;
		}
		Vec3 at = JourneyGeometry.track(o, JourneyGeometry.ARRIVE_FROM);
		train.moveTo(at.x, at.y, at.z, 0f, 0f);
		train.setBase(o);
		train.setMode(SubwayTrainEntity.ARRIVING, realm.getGameTime() + JourneyScene.TRAIN_ARRIVE);
		realm.addFreshEntity(train);
		s.train = train.getUUID();
	}

	private static void trainArrives(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		if (!trySpawnTrain(p, s, realm, o)) {
			s.stageStart = realm.getGameTime() - Math.min(t, JourneyScene.TRAIN_ARRIVE - 1); // the scene waits for its train
		}
		SubwayTrainEntity train = train(p.server, s);
		Vec3 front = train == null ? JourneyGeometry.track(o, -30) : train.position().add(JourneyGeometry.TRAIN_HALF, 2, 0);
		Vec3 mouth = JourneyGeometry.track(o, -JourneyGeometry.STATION_HALF - 2).add(0, 2, 0);
		if (beat(s, t, JourneyScene.TRAIN_ARRIVE)) {
			play(realm, mouth, horn(), 2.2f, 0.6f);
		}
		if (beat(s, t, 62)) {
			play(realm, front, horn(), 3.0f, 0.62f);
		}
		if (beat(s, t, 76)) {
			notify(p, SoundEvents.ELYTRA_FLYING, 0.9f, 0.85f);
			play(realm, front, SoundEvents.MINECART_RIDING, 2.0f, 0.55f);
		}
		for (int b = 98; b <= 134; b += 9) {
			if (beat(s, t, b)) {
				play(realm, front.subtract(10, 1, 0), SoundEvents.GRINDSTONE_USE, 1.3f, 1.7f + (b - 98) * 0.006f);
			}
		}
		if (beat(s, t, 142)) {
			play(realm, front.subtract(17, 0, 0), SoundEvents.FIRE_EXTINGUISH, 0.8f, 0.5f); // the air brakes sigh
		}
		if (beat(s, t, JourneyScene.TRAIN_ARRIVE + JourneyGeometry.ARRIVE_TICKS + 4)) {
			play(realm, JourneyGeometry.track(o, 0).add(0, 2, 1.5), SoundEvents.PISTON_CONTRACT, 0.7f, 1.5f);
		}
		if (t >= JourneyScene.TRAIN.length) {
			if (train != null) {
				train.setMode(SubwayTrainEntity.STATION, realm.getGameTime());
			}
			setStage(p, s, Stage.WAITING);
			p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.board").withStyle(ChatFormatting.GOLD), true);
		}
	}

	private static void waiting(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		SubwayTrainEntity train = train(p.server, s);
		if (train == null) {
			if (trySpawnTrain(p, s, realm, o)) {
				train = train(p.server, s);
				train.setMode(SubwayTrainEntity.STATION, realm.getGameTime());
			}
			return;
		}
		if (t > 0 && t % 100 == 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.board").withStyle(ChatFormatting.GOLD), true);
		}
		// stepped into the carriage?
		Vec3 c = train.position();
		Vec3 q = p.position();
		if (q.z - c.z < JourneyGeometry.CAR_HALF_W + 0.1 && q.z - c.z > -JourneyGeometry.CAR_HALF_W && q.y > o.getY() - 0.2 && q.y < o.getY() + 3
				&& atDoor(q.x - c.x)) {
			board(p, s, train);
		}
	}

	/** Is x offset {@code dx} (from the train's centre) in one of the six doorways? */
	static boolean atDoor(double dx) {
		for (double car : new double[] { -JourneyGeometry.CAR_OFFSET, JourneyGeometry.CAR_OFFSET }) {
			for (double d : JourneyGeometry.DOORS) {
				if (Math.abs(dx - car - d) <= JourneyGeometry.DOOR_HALF + 0.2) {
					return true;
				}
			}
		}
		return false;
	}

	/** Right-clicked the train. */
	static void boardRequest(ServerPlayer p, SubwayTrainEntity train) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && s.stage == Stage.WAITING && train.getUUID().equals(s.train)) {
			board(p, s, train);
		}
	}

	private static void board(ServerPlayer p, Session s, SubwayTrainEntity train) {
		ServerLevel realm = (ServerLevel) train.level();
		p.stopRiding();
		p.startRiding(train, true);
		p.setYRot(0f);
		p.setYHeadRot(0f);
		train.setMode(SubwayTrainEntity.RIDE, realm.getGameTime());
		setStage(p, s, Stage.RIDE);
	}

	private static void ride(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		SubwayTrainEntity train = train(p.server, s);
		if (train == null) {
			// lost the train (should not happen): straight to the cave
			arriveInCave(p, s, realm, o);
			return;
		}
		if (!p.isPassenger() && t < JourneyGeometry.RIDE_TICKS - 1) {
			p.startRiding(train, true); // no getting off between stations
		}
		Vec3 me = p.position();
		Vec3 front = train.position().add(JourneyGeometry.TRAIN_HALF, 2, 0);
		if (beat(s, t, 1)) {
			play(realm, me, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 1.26f);
		}
		if (beat(s, t, 4)) {
			play(realm, me.add(0, 0, 2), SoundEvents.PISTON_EXTEND, 0.6f, 1.4f);
		}
		if (beat(s, t, 8)) {
			play(realm, me, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.0f, 0.94f);
		}
		if (beat(s, t, JourneyGeometry.RIDE_DEPART)) {
			play(realm, front, horn(), 2.0f, 0.66f);
		}
		if (beat(s, t, JourneyGeometry.RIDE_GLYPHS)) {
			notify(p, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 0.55f);
			notify(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0f, 0.5f);
			notify(p, SoundEvents.SOUL_ESCAPE.value(), 1.0f, 0.5f);
		}
		if (t >= JourneyGeometry.RIDE_GLYPHS && t < JourneyGeometry.RIDE_FLASH + 30 && t % 3 == 0) {
			Vec3 at = me.add(4 + p.getRandom().nextDouble() * 6, 1.2, 0.9);
			realm.sendParticles(ParticleTypes.ENCHANT, at.x, at.y, at.z, 14, 1.2, 0.7, 0.4, 0.6);
		}
		if (beat(s, t, JourneyGeometry.RIDE_FLASH)) {
			notify(p, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f, 0.85f);
			notify(p, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9f, 0.7f);
			shake(p, 0.8f, 18);
		}
		if (t >= JourneyGeometry.RIDE_FLASH && t < JourneyGeometry.RIDE_FLASH + 24 && t % 2 == 0) {
			// the tiles crumble away from the walls ahead
			Vec3 at = front.add(3, 0.5, 0);
			realm.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), at.x, at.y, o.getZ() + 3.6, 18, 2.5, 1.5,
					0.2, 0.1);
			realm.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.QUARTZ_BRICKS.defaultBlockState()), at.x, at.y, o.getZ() - 2.6, 18, 2.5, 1.5,
					0.2, 0.1);
		}
		if (beat(s, t, 170)) {
			placeWizard(p, s, realm, o);
		}
		if (beat(s, t, JourneyGeometry.RIDE_BRAKE + 4)) {
			play(realm, me, SoundEvents.GRINDSTONE_USE, 1.0f, 1.8f);
		}
		if (beat(s, t, JourneyGeometry.RIDE_BRAKE + 30)) {
			play(realm, me, SoundEvents.GRINDSTONE_USE, 1.0f, 1.9f);
		}
		if (beat(s, t, JourneyGeometry.RIDE_STOP)) {
			play(realm, me, SoundEvents.FIRE_EXTINGUISH, 0.7f, 0.5f);
		}
		if (beat(s, t, JourneyGeometry.RIDE_STOP + 2)) {
			play(realm, me.add(0, 0, 2), SoundEvents.PISTON_CONTRACT, 0.7f, 1.5f);
		}
		if (t >= JourneyGeometry.RIDE_TICKS) {
			train.setMode(SubwayTrainEntity.CAVE, realm.getGameTime());
			arriveInCave(p, s, realm, o);
		}
	}

	private static void arriveInCave(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		p.stopRiding();
		Vec3 at = JourneyGeometry.caveStepOff(o);
		p.teleportTo(realm, at.x, at.y, at.z, -90f, 0f);
		p.fallDistance = 0f;
		p.setDeltaMovement(Vec3.ZERO);
		placeWizard(p, s, realm, o);
		setStage(p, s, Stage.CAVE);
		p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.approach").withStyle(ChatFormatting.GOLD), true);
		play(realm, at, SoundEvents.AMBIENT_CAVE.value(), 0.6f, 0.6f);
	}

	private static void placeWizard(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		Vec3 seat = JourneyGeometry.throneSeat(o);
		// never into a chunk that isn't entity-ticking yet (it would be invisible, and stuck there)
		if (wizard(p.server, s) != null || !realm.isPositionEntityTicking(BlockPos.containing(seat))) {
			return;
		}
		ThroneWizardEntity w = THRONE_WIZARD.create(realm);
		if (w == null) {
			return;
		}
		w.moveTo(seat.x, seat.y, seat.z, ThroneWizardEntity.FACING, 0f);
		w.setYHeadRot(ThroneWizardEntity.FACING);
		w.setYBodyRot(ThroneWizardEntity.FACING);
		w.setSummoned(true);
		realm.addFreshEntity(w);
		s.wizard = w.getUUID();
	}

	private static void cave(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		ThroneWizardEntity w = wizard(p.server, s);
		if (w == null) {
			placeWizard(p, s, realm, o);
			return;
		}
		if (t > 0 && t % 120 == 0) {
			p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.approach").withStyle(ChatFormatting.GOLD), true);
		}
		if (ShazamIllusions.flatDistance(p.position(), w.position()) <= JourneyGeometry.MEET_RANGE) {
			startMeeting(p, s, realm, o);
		}
	}

	/** Right-clicked the Wizard: in the cave, the meeting; at the prompt, the prompt again. */
	static void wizardClicked(ServerPlayer p, ThroneWizardEntity w) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || !w.getUUID().equals(s.wizard)) {
			p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.wizard_idle").withStyle(ChatFormatting.GOLD), true);
			return;
		}
		ServerLevel realm = EternityRealm.level(p.server);
		if (s.stage == Stage.CAVE) {
			startMeeting(p, s, realm, EternityRealm.origin(realm, s.slot));
		} else if (s.stage == Stage.PROMPT) {
			sendScene(p, s, JourneyScene.PROMPT, (int) (p.level().getGameTime() - s.stageStart), true);
		}
	}

	private static void startMeeting(ServerPlayer p, Session s, ServerLevel realm, BlockPos o) {
		Vec3 at = JourneyGeometry.meetSpot(o);
		p.teleportTo(realm, at.x, at.y, at.z, -90f, -10f);
		setStage(p, s, Stage.MEETING);
		hold(s, at, -90f, -10f);
		ThroneWizardEntity w = wizard(p.server, s);
		if (w != null) {
			w.rise(realm.getGameTime() + JourneyScene.MEET_RISE);
		}
	}

	private static void meeting(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		hold(s, JourneyGeometry.meetSpot(o), -90f, -10f);
		ThroneWizardEntity w = wizard(p.server, s);
		if (w != null) {
			w.follow(JourneyGeometry.throneSeat(o), JourneyGeometry.throneStand(o));
		}
		Vec3 wz = w == null ? JourneyGeometry.throneStand(o) : w.position();
		if (beat(s, t, JourneyScene.MEET_RISE)) {
			play(realm, wz, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0f, 0.5f);
			play(realm, wz, SoundEvents.BEACON_AMBIENT, 1.0f, 0.6f);
		}
		for (int line : new int[] { JourneyScene.MEET_LINE1, JourneyScene.MEET_LINE2, JourneyScene.MEET_LINE3 }) {
			if (beat(s, t, line)) {
				play(realm, wz.add(0, 1.6, 0), SoundEvents.EVOKER_AMBIENT, 0.7f, 0.7f);
			}
		}
		if (t >= JourneyScene.MEETING.length) {
			setStage(p, s, Stage.PROMPT);
			hold(s, JourneyGeometry.meetSpot(o), -90f, -10f);
		}
	}

	private static void prompt(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		hold(s, JourneyGeometry.meetSpot(o), -90f, -10f);
		ThroneWizardEntity w = wizard(p.server, s);
		if (w != null) {
			w.follow(JourneyGeometry.throneSeat(o), JourneyGeometry.throneStand(o));
		}
		if (t > 0 && t % 100 == 0) {
			sendScene(p, s, JourneyScene.PROMPT, (int) t, false); // keeps the client's prompt alive
			p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.say_it").withStyle(ChatFormatting.GOLD), true);
		}
	}

	/** Is {@code text} from {@code p} the answer to the prompt (so it is kept out of chat)? */
	public static boolean wouldAnswer(ServerPlayer p, String text) {
		Session s = SESSIONS.get(p.getUUID());
		return s != null && s.stage == Stage.PROMPT && ShazamWord.isWord(text);
	}

	/** The chat path without the chat (tests): true if {@code text} answered the prompt. */
	public static boolean chatAnswer(ServerPlayer p, String text) {
		return wouldAnswer(p, text) && answer(p);
	}

	/** "SHAZAM!" -- the answer (the prompt's button, or the word in chat). True if it was the moment for it. */
	public static boolean answer(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s == null || s.stage != Stage.PROMPT) {
			return false;
		}
		ServerLevel realm = EternityRealm.level(p.server);
		BlockPos o = EternityRealm.origin(realm, s.slot);
		setStage(p, s, Stage.TRANSFORM);
		hold(s, JourneyGeometry.meetSpot(o), -90f, -10f);
		return true;
	}

	private static void transform(ServerPlayer p, Session s, long t, ServerLevel realm, BlockPos o) {
		hold(s, JourneyGeometry.meetSpot(o), -90f, -10f);
		Vec3 me = p.position();
		if (beat(s, t, JourneyScene.TRANSFORM_SHOUT)) {
			play(realm, me, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.3f);
		}
		if (beat(s, t, JourneyScene.TRANSFORM_STRIKE)) {
			s.granted = Shazam.grant(p);
			if (s.granted) {
				Shazam.speak(p); // the first transformation: the colossal bolt, the suit, "SHAZAM!"
			}
			shake(p, 1.2f, 24);
			play(realm, me, SoundEvents.TRIDENT_THUNDER.value(), 1.0f, 0.8f);
			realm.sendParticles(ParticleTypes.FLASH, me.x, me.y + 1.2, me.z, 3, 0.3, 0.3, 0.3, 0);
		}
		if (beat(s, t, JourneyScene.TRANSFORM_STRIKE + 4)) {
			ShazamFx.sky(realm, me.add(2.6, 0, 1.8), 1.4f);
		}
		if (beat(s, t, JourneyScene.TRANSFORM_STRIKE + 8)) {
			ShazamFx.sky(realm, me.add(1.5, 0, -2.4), 1.4f);
			ShazamFx.burst(realm, me, 1.6f);
		}
		if (beat(s, t, JourneyScene.TRANSFORM_LINE1) || beat(s, t, JourneyScene.TRANSFORM_LINE2)) {
			ThroneWizardEntity w = wizard(p.server, s);
			if (w != null) {
				play(realm, w.position().add(0, 1.6, 0), SoundEvents.EVOKER_AMBIENT, 0.7f, 0.7f);
			}
		}
		if (beat(s, t, JourneyScene.TRANSFORM_WHITE)) {
			play(realm, me, SoundEvents.BEACON_ACTIVATE, 1.0f, 0.8f);
		}
		if (t >= JourneyScene.TRANSFORM_HOME) {
			boolean granted = s.granted;
			end(p.server, s);
			returnHome(p);
			if (p.connection != null && ServerPlayNetworking.canSend(p, JourneyPayloads.Scene.TYPE)) {
				ServerPlayNetworking.send(p, new JourneyPayloads.Scene(JourneyScene.HOME.id, 0, p.getX(), p.getY(), p.getZ(), p.getYRot(), -1));
			}
			if (granted) {
				p.displayClientMessage(Component.translatable("message.projecthero.shazam_journey.home").withStyle(ChatFormatting.GOLD), false);
			}
		}
	}

	/** Now and then, lightning flickers high under the cave's dome. */
	private static void ceilingLightning(ServerLevel realm, BlockPos o, Session s, long now) {
		if (now < s.nextArc) {
			return;
		}
		var r = realm.getRandom();
		s.nextArc = now + 30 + r.nextInt(60);
		double a = r.nextDouble() * Math.PI * 2;
		double rr = 6 + r.nextDouble() * 10;
		Vec3 c = new Vec3(o.getX() + JourneyGeometry.CAVE_X + 4, o.getY() + JourneyGeometry.CAVE_H - 5, o.getZ() + 0.5);
		Vec3 p1 = c.add(Math.cos(a) * rr * 1.6, r.nextDouble() * 2, Math.sin(a) * rr);
		Vec3 p2 = p1.add((r.nextDouble() - 0.5) * 14, -2 - r.nextDouble() * 4, (r.nextDouble() - 0.5) * 10);
		ShazamFx.arc(realm, p1, p2, 1.3f);
		realm.playSound(null, p1.x, p1.y, p1.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.35f, 1.5f + r.nextFloat() * 0.3f);
	}

	// ---------------------------------------------------------------- ending

	/** Ends a journey without moving anyone: its train and Wizard go. The saved return point is left alone. */
	private static void end(MinecraftServer server, Session s) {
		SESSIONS.remove(s.player);
		SubwayTrainEntity train = train(server, s);
		if (train != null) {
			train.ejectPassengers();
			train.discard();
		}
		ThroneWizardEntity w = wizard(server, s);
		if (w != null) {
			w.discard();
		}
	}

	/** Back to where the illusion first appeared to them, in a flash; turned back into themselves. */
	public static void returnHome(ServerPlayer p) {
		MinecraftServer server = p.getServer();
		if (server == null) {
			return;
		}
		JourneyPayloads.Return r = p.getAttachedOrElse(RETURN, null);
		ServerLevel level = null;
		if (r != null) {
			ResourceLocation id = ResourceLocation.tryParse(r.dimension());
			level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
		}
		Vec3 at;
		float yaw = p.getYRot(), pitch = p.getXRot();
		if (level == null || level.dimension() == EternityRealm.DIMENSION) {
			level = server.overworld();
			at = spawn(level);
		} else {
			at = new Vec3(r.x(), r.y(), r.z());
			yaw = r.yRot();
			pitch = r.xRot();
		}
		p.removeAttached(RETURN);
		p.stopRiding();
		if (Shazam.transformed(p)) {
			Shazam.forceUntransform(p);
		}
		p.teleportTo(level, at.x, at.y, at.z, yaw, pitch);
		p.fallDistance = 0f;
		p.setDeltaMovement(Vec3.ZERO);
		level.sendParticles(ParticleTypes.FLASH, at.x, at.y + 1.0, at.z, 2, 0.1, 0.1, 0.1, 0);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1.0, at.z, 40, 0.5, 0.9, 0.5, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.1f);
	}

	private static Vec3 spawn(ServerLevel level) {
		BlockPos sp = level.getSharedSpawnPos();
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.getX(), sp.getZ());
		return new Vec3(sp.getX() + 0.5, Math.max(y, level.getMinBuildHeight() + 1), sp.getZ() + 0.5);
	}

	/** Logging out mid-way: the journey ends (train, Wizard); the saved return point brings them home next login. */
	public static void onDisconnect(ServerPlayer p) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null && p.getServer() != null) {
			end(p.getServer(), s);
		}
		ShazamIllusions.dismiss(p);
	}

	/**
	 * A player with no live journey but a saved return point (a relog, crash or restart mid-way) is sent home; one in the realm
	 * with nothing saved goes to world spawn.
	 */
	public static void check(ServerPlayer p) {
		if (SESSIONS.containsKey(p.getUUID())) {
			return;
		}
		if (p.getAttachedOrElse(RETURN, null) != null) {
			returnHome(p);
		} else if (p.level().dimension() == EternityRealm.DIMENSION && p.getServer() != null) {
			ServerLevel overworld = p.getServer().overworld();
			Vec3 at = spawn(overworld);
			p.teleportTo(overworld, at.x, at.y, at.z, p.getYRot(), p.getXRot());
		}
	}

	// ---------------------------------------------------------------- little helpers

	private static SoundEvent horn() {
		return SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(5).value(); // "call": a deep two-note horn, pitched down
	}

	/** A sound only {@code p} hears. */
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

	// ---------------------------------------------------------------- test hooks

	/** Test hook: the right-click on the train. */
	public static void boardRequestForTest(ServerPlayer p, SubwayTrainEntity train) {
		boardRequest(p, train);
	}

	/** Test hook: the right-click on the Wizard. */
	public static void wizardClickedForTest(ServerPlayer p) {
		ThroneWizardEntity w = wizard(p);
		if (w != null) {
			wizardClicked(p, w);
		}
	}

	/** Test hook: jumps the current stage {@code ticks} ahead (the beats in between still fire). */
	public static void fastForward(ServerPlayer p, int ticks) {
		Session s = SESSIONS.get(p.getUUID());
		if (s != null) {
			s.stageStart -= ticks;
			SubwayTrainEntity train = train(p.server, s);
			if (train != null && (s.stage == Stage.TRAIN || s.stage == Stage.RIDE)) {
				train.setMode(train.mode(), train.start() - ticks);
			}
			ThroneWizardEntity w = wizard(p.server, s);
			if (w != null && w.riseAt() != 0L && s.stage == Stage.MEETING) {
				w.rise(w.riseAt() - ticks);
			}
		}
	}
}
