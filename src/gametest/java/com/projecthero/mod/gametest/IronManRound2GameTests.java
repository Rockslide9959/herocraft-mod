package com.projecthero.mod.gametest;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.projecthero.mod.hero.AbilitySlot;
import com.projecthero.mod.ironman.IronManBlade;
import com.projecthero.mod.ironman.IronManBladeLook;
import com.projecthero.mod.ironman.IronManEnergy;
import com.projecthero.mod.ironman.TonyStark;
import com.projecthero.mod.ironman.ability.IronManAbilities;
import com.projecthero.mod.ironman.ability.IronManAbilityManager;
import com.projecthero.mod.ironman.data.TonyStarkState;
import com.projecthero.mod.ironman.entity.IronManMissileEntity;
import com.projecthero.mod.ironman.item.IronManItems;
import com.projecthero.mod.ironman.suit.IronManSuitUpManager;
import com.projecthero.mod.ironman.suit.IronManSuits;
import com.projecthero.mod.ironman.ui.IronManUiLayout;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * v0.14.21 Iron Man "round two": Homing Missiles on the Mark VII weapon wheel (targeting rules, the volley, the wheel
 * layout), the Mark V blade timing, the nested {@link TonyStarkState} save codec reading old flat saves, and the new
 * assets (glowmasks, blade bones, Repulsor Boots / palm-glow textures). Rendering itself is not testable here.
 */
public class IronManRound2GameTests implements FabricGameTest {
	private static final String[] MARKS = { "mark_1", "mark_2", "mark_iii", "mark_4", "mark_v", "mark_6", "mark_vii" };
	private static final int[][] SCREENS = { { 320, 240 }, { 426, 240 }, { 480, 270 }, { 960, 540 } };
	private static JsonObject lang;

	private static String t(String key) {
		if (lang == null) {
			try (InputStream in = res("/assets/projecthero/lang/en_us.json")) {
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				throw new IllegalStateException("cannot read en_us.json", e);
			}
		}
		if (!lang.has(key)) {
			throw new IllegalStateException("missing lang key " + key);
		}
		return lang.get(key).getAsString();
	}

	private static InputStream res(String path) {
		InputStream in = IronManRound2GameTests.class.getResourceAsStream(path);
		if (in == null) {
			throw new IllegalStateException("missing resource " + path);
		}
		return in;
	}

	private static ServerPlayer suited(GameTestHelper h, String suitId) {
		ServerPlayer p = h.makeMockServerPlayerInLevel();
		p.setGameMode(GameType.SURVIVAL);
		TonyStark.grant(p);
		for (ArmorItem.Type type : ArmorItem.Type.values()) {
			var item = IronManItems.armor(suitId, type);
			if (item != null) {
				p.setItemSlot(IronManSuitUpManager.slotFor(type), new ItemStack(item));
			}
		}
		IronManEnergy.setEnergy(p, suitId, 1_000_000f);
		BlockPos at = h.absolutePos(new BlockPos(1, 1, 1));
		p.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		p.setYRot(0f); // facing +Z
		p.setXRot(0f);
		p.setYHeadRot(0f);
		return p;
	}

	private static <T extends net.minecraft.world.entity.Mob> T mob(GameTestHelper h, EntityType<T> type, ServerPlayer p,
			double dx, double dz) {
		T m = type.create(h.getLevel());
		m.moveTo(p.getX() + dx, p.getY(), p.getZ() + dz, 0f, 0f);
		m.setNoAi(true);
		h.getLevel().addFreshEntity(m);
		return m;
	}

	// ------------------------------------------------------------------ Homing Missiles

	@GameTest(template = EMPTY_STRUCTURE)
	public void homingMissilesIsAWheelOption(GameTestHelper helper) {
		helper.assertTrue(List.of(IronManAbilities.WEAPON_WHEEL_OPTIONS).contains(IronManAbilities.HOMING_MISSILES),
				"Homing Missiles can be bound to X");
		helper.assertTrue(IronManAbilities.WEAPON_WHEEL_SECTORS.length == 7, "the wheel has 7 wedges");
		helper.assertTrue(List.of(IronManAbilities.WEAPON_WHEEL_OPTIONS).contains(IronManAbilities.MICRO_MISSILES),
				"Micro-Missiles is still there (added, not replaced)");
		int n = IronManAbilities.WEAPON_WHEEL_SECTORS.length;
		for (int[] s : SCREENS) {
			int[] radii = IronManUiLayout.wheelRadii(s[0], s[1]);
			double mid = (radii[0] + radii[1]) / 2.0;
			double arc = 2 * Math.PI * mid / n - 2 * 0.035 * mid; // minus the drawn gap on both sides
			for (String label : new String[] { "screen.projecthero.weapon_wheel.bound", "screen.projecthero.weapon_wheel.on",
					"screen.projecthero.weapon_wheel.off" }) {
				helper.assertTrue(IronManUiLayout.approxWidth(t(label)) + 4 <= arc, "wedge label fits a 1/7 wedge at "
						+ s[0] + "x" + s[1] + ": " + label + " (arc " + (int) arc + ")");
			}
			helper.assertTrue(16 + 4 <= arc, "the icon fits a 1/7 wedge");
			int tw = IronManUiLayout.wheelTextWidth(radii[0]);
			helper.assertTrue(IronManUiLayout.approxWidth(t("hud.projecthero.ironman.ability.homing_missiles")) <= tw,
					"Homing Missiles name fits the centre disc");
			helper.assertTrue(IronManUiLayout.wrapLines(t("screen.projecthero.weapon_wheel.desc.homing_missiles"), tw,
					IronManUiLayout::approxWidth) <= 2, "its description wraps to two lines at most");
		}
		t("message.projecthero.ironman.homing_locked");
		t("message.projecthero.ironman.homing_no_lock");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void homingMissilesLockOnAndFireFour(GameTestHelper helper) {
		ServerPlayer p = suited(helper, "mark_vii");
		Zombie zombie = mob(helper, EntityType.ZOMBIE, p, 0.0, 8.0);
		helper.assertTrue(IronManAbilities.homingTarget(p) == zombie, "the zombie under the crosshair is the lock");
		TonyStark.setWeaponWheelChoice(p, IronManAbilities.HOMING_MISSILES);
		float before = IronManEnergy.energy(p, "mark_vii");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_3, true);
		TonyStarkState s = TonyStark.state(p);
		helper.assertTrue(s.pendingMissiles == IronManAbilities.HOMING_MISSILE_COUNT && s.pendingMissileHoming,
				"X queues a 4-missile homing volley (got " + s.pendingMissiles + ")");
		helper.assertTrue(s.pendingMissileTargetId == zombie.getId(), "locked on to the zombie");
		float spent = before - IronManEnergy.energy(p, "mark_vii");
		float expected = IronManSuits.byId("mark_vii").missileEnergyCost() * IronManSuits.byId("mark_vii").energyCostMultiplier();
		helper.assertTrue(spent > 0f && Math.abs(spent - expected) <= Math.max(1f, expected * 0.05f),
				"costs the same as Micro-Missiles (" + spent + " vs " + expected + ")");
		helper.assertTrue(s.abilityReadyAt.containsKey("mark_vii/" + IronManAbilities.HOMING_MISSILES),
				"its own cooldown (the HUD strip reads mark_vii/homing_missiles)");
		IronManAbilities.tickMicroMissiles(p, IronManSuits.byId("mark_vii"));
		AABB box = p.getBoundingBox().inflate(6.0);
		List<IronManMissileEntity> mine = helper.getLevel().getEntitiesOfClass(IronManMissileEntity.class, box,
				m -> m.getOwner() == p);
		helper.assertTrue(mine.size() == 1, "the first missile launches (got " + mine.size() + ")");
		helper.assertTrue(mine.get(0).isHoming() && mine.get(0).lockedTarget() == zombie, "it homes on the locked zombie");
		helper.assertTrue(TonyStark.state(p).pendingMissiles == IronManAbilities.HOMING_MISSILE_COUNT - 1, "three to go");
		mine.forEach(m -> m.discard());
		zombie.discard();
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void homingTargetFollowsHeroTargets(GameTestHelper helper) {
		ServerPlayer p = suited(helper, "mark_vii");
		// a passive cow inside the cone but off the crosshair is not a threat (isHostile) -> never auto-picked
		Cow cow = mob(helper, EntityType.COW, p, 2.5, 8.0);
		helper.assertTrue(IronManAbilities.homingTarget(p) != cow, "an off-crosshair passive mob is not auto-targeted");
		// ... but a cow right under the crosshair is an aimed shot (canHarm) -> it may be locked
		Cow aimed = mob(helper, EntityType.COW, p, 0.0, 6.0);
		helper.assertTrue(IronManAbilities.homingTarget(p) == aimed, "aimed: the crosshair target is locked (canHarm)");
		aimed.discard();
		// a zombie beyond 30 blocks is out of range
		Zombie far = mob(helper, EntityType.ZOMBIE, p, 0.0, IronManAbilities.HOMING_RANGE + 6.0);
		helper.assertTrue(IronManAbilities.homingTarget(p) != far, "beyond the homing range is ignored");
		// a zombie off to the side but inside the cone is picked over the cow
		Zombie side = mob(helper, EntityType.ZOMBIE, p, -2.5, 9.0);
		helper.assertTrue(IronManAbilities.homingTarget(p) == side, "a hostile in the cone is auto-targeted (isHostile)");
		// behind the player: never
		side.moveTo(p.getX(), p.getY(), p.getZ() - 6.0, 0f, 0f);
		helper.assertTrue(IronManAbilities.homingTarget(p) != side, "nothing behind the crosshair cone");
		cow.discard();
		far.discard();
		side.discard();
		helper.succeed();
	}

	// ------------------------------------------------------------------ Mark V blades

	@GameTest(template = EMPTY_STRUCTURE)
	public void bladeExtendAndRetractTiming(GameTestHelper helper) {
		float x = 0f;
		int ticks = 0;
		while (x < 1f && ticks < 50) {
			x = IronManBladeLook.step(x, true);
			ticks++;
		}
		helper.assertTrue(ticks == IronManBladeLook.EXTEND_TICKS && IronManBladeLook.EXTEND_TICKS == 6,
				"fully out in 6 ticks (took " + ticks + ")");
		ticks = 0;
		while (x > 0f && ticks < 50) {
			x = IronManBladeLook.step(x, false);
			ticks++;
		}
		helper.assertTrue(ticks == 6, "fully stowed in 6 ticks");
		helper.assertTrue(IronManBladeLook.eased(0f) == 0f && IronManBladeLook.eased(1f) == 1f, "eased endpoints");
		float prev = -1f;
		for (int i = 0; i <= 20; i++) {
			float e = IronManBladeLook.eased(i / 20f);
			helper.assertTrue(e >= prev, "easing is monotonic");
			prev = e;
		}
		helper.assertFalse(IronManBladeLook.visible(0f), "stowed = hidden");
		helper.assertTrue(IronManBladeLook.visible(IronManBladeLook.eased(1f / 6f)), "visible from the first tick out");
		helper.assertTrue(IronManBladeLook.boneScale(0f) > 0f && IronManBladeLook.boneScale(1f) == 1f, "bone scale never 0");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void bladeStateIsSyncedTheClientDrivesItFrom(GameTestHelper helper) {
		ServerPlayer p = suited(helper, "mark_v");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true); // v0.14.29: Blades moved to V
		helper.assertTrue(p.getAttachedOrElse(com.projecthero.mod.attachment.ModAttachments.IRON_MAN_BLADES, false),
				"V sets the synced IRON_MAN_BLADES flag the blade geometry reads");
		IronManBlade.tick(p);
		helper.assertTrue(IronManBlade.active(p), "blades stay out while the Mark V runs");
		IronManAbilityManager.handle(p, AbilitySlot.SLOT_5, true);
		helper.assertFalse(IronManBlade.active(p), "V again retracts them");
		helper.succeed();
	}

	// ------------------------------------------------------------------ TonyStarkState save codec

	private static JsonObject oldFlatSave() {
		JsonObject o = new JsonObject();
		o.addProperty("has_power", true);
		o.addProperty("tech_level", 2);
		JsonArray built = new JsonArray();
		built.add("mark_1");
		built.add("mark_v");
		built.add("mark_v/helmet");
		o.add("built_suits", built);
		o.addProperty("active_suit", "mark_v");
		JsonObject energy = new JsonObject();
		energy.addProperty("mark_v", 7667.5f);
		o.add("suit_energy", energy);
		JsonObject integrity = new JsonObject();
		integrity.addProperty("mark_v", 88.0f);
		o.add("suit_integrity", integrity);
		JsonObject ready = new JsonObject();
		ready.addProperty("mark_v/blade", 123456L);
		o.add("ability_ready_at", ready);
		o.addProperty("mob_highlight_on", true);
		o.addProperty("flamethrower_heat", 12.5f);
		o.addProperty("timed_flight_until", 111L);
		o.addProperty("wrist_laser_until", 222L);
		o.addProperty("overload_until", 333L);
		JsonArray spent = new JsonArray();
		spent.add("mark_4");
		o.add("wrist_laser_spent", spent);
		o.addProperty("weapon_wheel_choice", "rocket");
		o.addProperty("phoenix_ready_at", 444L);
		o.addProperty("suit_air", 0.25f);
		return o;
	}

	private static void assertOldSave(GameTestHelper helper, TonyStarkState s, String where) {
		helper.assertTrue(s.hasPower && s.techLevel == 2 && "mark_v".equals(s.activeSuit), where + ": core");
		helper.assertTrue(s.builtSuits.equals(Set.of("mark_1", "mark_v", "mark_v/helmet")), where + ": built suits");
		helper.assertTrue(s.suitEnergy.get("mark_v") == 7667.5f && s.suitIntegrity.get("mark_v") == 88.0f, where + ": energy / integrity");
		helper.assertTrue(s.abilityReadyAt.get("mark_v/blade") == 123456L, where + ": cooldowns");
		helper.assertTrue(s.mobHighlightOn && s.flamethrowerHeat == 12.5f, where + ": highlight / heat");
		helper.assertTrue(s.timedFlightUntil == 111L && s.wristLaserUntil == 222L && s.overloadUntil == 333L
				&& s.phoenixReadyAt == 444L, where + ": timers");
		helper.assertTrue(s.wristLaserSpent.equals(Set.of("mark_4")), where + ": wrist laser spent");
		helper.assertTrue("rocket".equals(s.weaponWheelChoice) && s.suitAir == 0.25f, where + ": wheel / air");
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void oldFlatSaveLoadsAndResavesNested(GameTestHelper helper) {
		JsonObject old = oldFlatSave();
		TonyStarkState s = TonyStarkState.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
		assertOldSave(helper, s, "old flat JSON");

		JsonElement saved = TonyStarkState.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow();
		helper.assertTrue(saved.isJsonObject() && saved.getAsJsonObject().has("core") && saved.getAsJsonObject().has("suit")
				&& saved.getAsJsonObject().has("cooldowns") && saved.getAsJsonObject().has("misc"),
				"re-saved in the nested shape: " + saved);
		helper.assertFalse(saved.getAsJsonObject().has("has_power"), "no flat fields at the top any more");
		assertOldSave(helper, TonyStarkState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow(), "nested JSON round trip");

		// NBT (the real attachment storage): an old flat compound, as an old world has it on disk
		Tag oldNbt = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, old);
		helper.assertTrue(oldNbt instanceof CompoundTag c && c.contains("has_power"), "old NBT blob is flat");
		TonyStarkState fromNbt = TonyStarkState.CODEC.parse(NbtOps.INSTANCE, oldNbt).getOrThrow();
		assertOldSave(helper, fromNbt, "old flat NBT");
		Tag newNbt = TonyStarkState.CODEC.encodeStart(NbtOps.INSTANCE, fromNbt).getOrThrow();
		helper.assertTrue(newNbt instanceof CompoundTag c2 && c2.contains("core") && !c2.contains("has_power"), "NBT re-saved nested");
		assertOldSave(helper, TonyStarkState.CODEC.parse(NbtOps.INSTANCE, newNbt).getOrThrow(), "nested NBT round trip");

		// the legacy codec is exactly the pre-restructure writer: what it writes, the new codec reads
		Tag legacy = TonyStarkState.LEGACY_FLAT_CODEC.encodeStart(NbtOps.INSTANCE, fromNbt).getOrThrow();
		assertOldSave(helper, TonyStarkState.CODEC.parse(NbtOps.INSTANCE, legacy).getOrThrow(), "legacy writer -> new reader");

		// an empty compound (fresh / corrupted) still loads with defaults
		TonyStarkState empty = TonyStarkState.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
		helper.assertTrue(!empty.hasPower && empty.suitAir == 1.0f
				&& IronManAbilities.MICRO_MISSILES.equals(empty.weaponWheelChoice), "empty save = defaults");
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void syncCodecStillRoundTrips(GameTestHelper helper) {
		TonyStarkState s = TonyStarkState.CODEC.parse(JsonOps.INSTANCE, oldFlatSave()).getOrThrow();
		s.supersonicUntil = 9999L;
		io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
		TonyStarkState.SYNC_CODEC.encode(buf, s);
		TonyStarkState back = TonyStarkState.SYNC_CODEC.decode(buf);
		assertOldSave(helper, back, "sync codec");
		helper.assertTrue(back.supersonicUntil == 9999L, "supersonicUntil still synced");
		helper.succeed();
	}

	// ------------------------------------------------------------------ assets

	@GameTest(template = EMPTY_STRUCTURE)
	public void glowmasksExistForEveryMark(GameTestHelper helper) throws Exception {
		for (String m : MARKS) {
			BufferedImage img;
			try (InputStream in = res("/assets/projecthero/textures/armor/" + m + "_glowmask.png")) {
				img = ImageIO.read(in);
			}
			helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, m + " glowmask is 64x64 like its texture");
			int lit = 0;
			boolean cleanTransparent = true;
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int argb = img.getRGB(x, y);
					if ((argb >>> 24) != 0) {
						lit++;
					} else if ((argb & 0xFFFFFF) != 0) {
						cleanTransparent = false;
					}
				}
			}
			helper.assertTrue(lit > 0, m + " glowmask has lit texels");
			helper.assertTrue(cleanTransparent, m + " glowmask's transparent texels are black (additive pass)");
			if (!"mark_1".equals(m)) {
				helper.assertTrue((img.getRGB(49, 17) >>> 24) != 0 && (img.getRGB(41, 49) >>> 24) != 0,
						m + " palm repulsors glow");
			}
		}
		for (String path : new String[] { "/assets/projecthero/textures/armor/repulsor_boots.png",
				"/assets/projecthero/textures/misc/repulsor_palm_glow.png" }) {
			try (InputStream in = res(path)) {
				helper.assertTrue(ImageIO.read(in) != null, path + " loads");
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void markVGeoHasBladeBones(GameTestHelper helper) throws Exception {
		JsonObject geo;
		try (InputStream in = res("/assets/projecthero/geo/mark_v.geo.json")) {
			geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Map<String, JsonObject> bones = new HashMap<>();
		for (JsonElement b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
			bones.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
		}
		for (String[] pair : new String[][] { { "right_blade", "armorRightArm" }, { "left_blade", "armorLeftArm" } }) {
			JsonObject b = bones.get(pair[0]);
			helper.assertTrue(b != null, pair[0] + " bone exists");
			helper.assertTrue(pair[1].equals(b.get("parent").getAsString()), pair[0] + " hangs off " + pair[1]);
			helper.assertTrue(b.getAsJsonArray("cubes").size() == 3, pair[0] + ": housing, blade, tip");
			float pivotY = b.getAsJsonArray("pivot").get(1).getAsFloat();
			float lowest = Float.MAX_VALUE;
			for (JsonElement c : b.getAsJsonArray("cubes")) {
				lowest = Math.min(lowest, c.getAsJsonObject().getAsJsonArray("origin").get(1).getAsFloat());
			}
			helper.assertTrue(pivotY > 16f && lowest < 12f, pair[0] + " extends from the gauntlet past the fist (y 12)");
		}
		// the rest of the marks have no blades
		try (InputStream in = res("/assets/projecthero/geo/mark_vii.geo.json")) {
			helper.assertFalse(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("right_blade"), "Mark VII has no blades");
		}
		helper.succeed();
	}
	// ------------------------------------------------------------------ v0.15.1: the user's skin models, nothing added

	private static Map<String, JsonObject> geoBones(String mark) throws Exception {
		JsonObject geo;
		try (InputStream in = res("/assets/projecthero/geo/" + mark + ".geo.json")) {
			geo = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		}
		Map<String, JsonObject> bones = new HashMap<>();
		for (JsonElement b : geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones")) {
			bones.put(b.getAsJsonObject().get("name").getAsString(), b.getAsJsonObject());
		}
		return bones;
	}

	/** Every bone the assembly / build-on / suitcase / faceplate / blade code looks up by name. */
	private static Set<String> requiredBones(String mark) {
		Set<String> need = new java.util.TreeSet<>(List.of("armorHead", "armorBody", "armorRightArm", "armorLeftArm",
				"armorRightLeg", "armorLeftLeg", "armorRightBoot", "armorLeftBoot", "helmet", "faceplate", "helmet_brow"));
		for (int bit = 0; bit < 4; bit++) {
			for (boolean fromCase : new boolean[] { false, true }) {
				for (List<String> group : com.projecthero.mod.ironman.suit.IronManAssemblyPlan.groups(bit, fromCase)) {
					need.addAll(group);
				}
			}
		}
		if ("mark_v".equals(mark)) {
			need.add("right_blade");
			need.add("left_blade");
		}
		return need;
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void everyIronManGeoHasTheBonesTheCodeDrives(GameTestHelper helper) throws Exception {
		for (String m : MARKS) {
			Map<String, JsonObject> bones = geoBones(m);
			for (String need : requiredBones(m)) {
				helper.assertTrue(bones.containsKey(need), m + " geo has bone " + need);
			}
		}
		helper.succeed();
	}

	@GameTest(template = EMPTY_STRUCTURE)
	public void ironManGeoIsJustTheSkinModel(GameTestHelper helper) throws Exception {
		Set<String> skin = Set.of("helmet", "faceplate", "chest", "right_upper_arm", "left_upper_arm", "right_thigh", "left_thigh",
				"right_boot", "left_boot");
		for (String m : MARKS) {
			Map<String, JsonObject> bones = geoBones(m);
			for (Map.Entry<String, JsonObject> e : bones.entrySet()) {
				String name = e.getKey();
				int cubes = e.getValue().has("cubes") ? e.getValue().getAsJsonArray("cubes").size() : 0;
				if (name.endsWith("_blade")) {
					helper.assertTrue("mark_v".equals(m) && cubes == 3, m + ": only the Mark V has (3-cube) blades");
				} else if (skin.contains(name)) {
					helper.assertTrue(cubes == 2, m + " " + name + ": base + layer, got " + cubes);
				} else {
					helper.assertTrue(cubes == 0, m + " " + name + " adds no geometry beyond the skin model, got " + cubes);
				}
			}
			// the helmet's front faces are the faceplate (H lifts it)
			for (JsonElement c : bones.get("helmet").getAsJsonArray("cubes")) {
				helper.assertFalse(c.getAsJsonObject().getAsJsonObject("uv").has("north"), m + " helmet has no front faces");
			}
			// leggings + boots are exactly the model's legs: each layer's thigh starts where its boot ends
			for (String side : new String[] { "right", "left" }) {
				JsonArray thigh = bones.get(side + "_thigh").getAsJsonArray("cubes");
				JsonArray boot = bones.get(side + "_boot").getAsJsonArray("cubes");
				for (int i = 0; i < 2; i++) {
					JsonObject t = thigh.get(i).getAsJsonObject();
					JsonObject b = boot.get(i).getAsJsonObject();
					float bootTop = b.getAsJsonArray("origin").get(1).getAsFloat() + b.getAsJsonArray("size").get(1).getAsFloat();
					float thighBottom = t.getAsJsonArray("origin").get(1).getAsFloat();
					helper.assertTrue(Math.abs(bootTop - thighBottom) < 1e-3f, m + " " + side + " leg layer " + i + " seamless, no overlap");
					helper.assertTrue(t.getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv_size").get(1).getAsFloat()
							+ b.getAsJsonObject("uv").getAsJsonObject("north").getAsJsonArray("uv_size").get(1).getAsFloat() == 12f,
							m + " " + side + " leg: 12 texel rows between thigh and boot");
				}
			}
			// the skin's unused top-left corner stays empty -- except the Mark V's blade swatch
			BufferedImage img;
			try (InputStream in = res("/assets/projecthero/textures/armor/" + m + ".png")) {
				img = ImageIO.read(in);
			}
			helper.assertTrue(img.getWidth() == 64 && img.getHeight() == 64, m + " texture is a 64x64 skin");
			helper.assertTrue(((img.getRGB(2, 2) >>> 24) != 0) == "mark_v".equals(m), m + ": blade swatch only on the Mark V");
			// no enchanted shimmer on any piece
			for (ArmorItem.Type type : ArmorItem.Type.values()) {
				if (type == ArmorItem.Type.BODY) {
					continue;
				}
				var item = IronManItems.armor(m, type);
				helper.assertTrue(item != null && Boolean.FALSE.equals(new ItemStack(item)
						.get(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE)), m + " " + type + " has no glint");
			}
		}
		helper.succeed();
	}
}
