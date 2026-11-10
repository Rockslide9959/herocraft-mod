package com.projecthero.mod.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.projecthero.mod.saiyan.SaiyanForm;
import com.projecthero.mod.saiyan.moves.SaiyanMoves;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/** TEMP-HARNESS (not shipped): films the Saiyan moves. Enabled by run/saiyanmoves.flag (its text picks scenes). */
public final class SaiyanMovesHarness {
	private static boolean createdWorld;
	private static int waitTicks;
	private static int inWorldTicks;
	private static int t0 = -1;
	private static final Map<Integer, Runnable> AT = new HashMap<>();
	private static String recording;
	private static int recFrame;
	private static int recUntil;
	private static Vec3 origin;
	private static Vec3 base;
	private static final List<Mob> dummies = new ArrayList<>();
	private static String only;

	public static volatile Vec3 camPos;
	public static volatile Vec3 camLook;

	private SaiyanMovesHarness() {
	}

	public static void init() {
		if (!Files.exists(Path.of("saiyanmoves.flag"))) {
			return;
		}
		try {
			only = Files.readString(Path.of("saiyanmoves.flag")).trim();
		} catch (Exception e) {
			only = "";
		}
		ClientTickEvents.END_CLIENT_TICK.register(SaiyanMovesHarness::tick);
		Thread wd = new Thread(() -> {
			try {
				Thread.sleep(900_000L);
			} catch (InterruptedException e) {
				return;
			}
			System.out.println("[HARNESS] WATCHDOG timeout");
			Runtime.getRuntime().halt(3);
		}, "saiyan-harness-watchdog");
		wd.setDaemon(true);
		wd.start();
	}

	private static void server(Minecraft mc, java.util.function.Consumer<ServerPlayer> fn) {
		MinecraftServer srv = mc.getSingleplayerServer();
		if (srv != null) {
			srv.execute(() -> {
				if (!srv.getPlayerList().getPlayers().isEmpty()) {
					try {
						fn.accept(srv.getPlayerList().getPlayers().get(0));
					} catch (Throwable e) {
						System.out.println("[HARNESS] error: " + e);
						e.printStackTrace();
					}
				}
			});
		}
	}

	private static void at(int t, Runnable r) {
		AT.merge(t, r, (a, b) -> () -> {
			a.run();
			b.run();
		});
	}

	private static void tick(Minecraft mc) {
		mc.options.pauseOnLostFocus = false;
		if (mc.level == null || mc.player == null) {
			waitTicks++;
			if (waitTicks % 100 == 0 && !createdWorld && mc.getOverlay() == null && !(mc.screen instanceof TitleScreen)) {
				mc.options.onboardAccessibility = false;
				mc.setScreen(new TitleScreen());
			}
			if (!createdWorld && mc.getOverlay() == null && mc.screen instanceof TitleScreen && waitTicks > 40) {
				createdWorld = true;
				mc.execute(SaiyanMovesHarness::createWorld);
			}
			return;
		}
		inWorldTicks++;
		if (inWorldTicks == 100) {
			mc.options.hideGui = true;
			mc.options.fov().set(70);
			mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
			mc.options.bobView().set(false);
			server(mc, p -> {
				ServerLevel level = p.serverLevel();
				level.setDayTime(6000L);
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
				level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, p.server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, p.server);
				p.server.setDifficulty(Difficulty.NORMAL, true);
				origin = new Vec3(Math.floor(p.getX()) + 0.5, p.getY(), Math.floor(p.getZ()) + 0.5);
			});
			t0 = inWorldTicks;
			script(mc);
		}
		if (t0 >= 0) {
			Runnable r = AT.remove(inWorldTicks - t0);
			if (r != null) {
				r.run();
			}
			if (recording != null) {
				String name = String.format("sm_%s_%03d.png", recording, recFrame++);
				Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> {
				});
				if (inWorldTicks - t0 >= recUntil) {
					System.out.println("[HARNESS] recorded " + recording + " frames=" + recFrame);
					recording = null;
				}
			}
		}
	}

	private static void createWorld() {
		Minecraft mc = Minecraft.getInstance();
		LevelSettings settings = new LevelSettings("SM_" + System.currentTimeMillis() % 100000, GameType.SURVIVAL, false,
				Difficulty.NORMAL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
		mc.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(424242L, false, false),
				reg -> reg.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
						.getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
				mc.screen);
	}

	private static void clearDummies() {
		for (Mob m : dummies) {
			m.discard();
		}
		dummies.clear();
	}

	private static Mob dummy(ServerPlayer p, Vec3 at, float yaw) {
		Mob m = EntityType.ZOMBIE.create(p.serverLevel());
		m.setNoAi(true);
		m.setPersistenceRequired();
		m.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
		m.setHealth(1000);
		m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100000, 0, false, false));
		m.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
		m.moveTo(at.x, at.y, at.z, yaw, 0f);
		m.setYHeadRot(yaw);
		p.serverLevel().addFreshEntity(m);
		dummies.add(m);
		return m;
	}

	private static void record(int start, String name, int ticks) {
		at(start, () -> {
			recording = name;
			recFrame = 0;
			recUntil = start + ticks;
		});
	}

	private static void reset(ServerPlayer p, SaiyanForm form, float pitch) {
		p.clearFire();
		p.removeAllEffects();
		p.setDeltaMovement(Vec3.ZERO);
		p.teleportTo(p.serverLevel(), base.x, base.y, base.z, 0f, pitch);
		p.setYHeadRot(0f);
		p.yBodyRot = 0f;
		p.setHealth(p.getMaxHealth());
		SaiyanMoves.resetCooldowns(p);
		SaiyanMoves.overrideForTest(p, form, 1000f);
	}

	private static void scene(Minecraft mc, int t, int index, CameraType type, Vec3 cam, Vec3 look, SaiyanForm form, float pitch,
			java.util.function.Consumer<ServerPlayer> setup) {
		at(t, () -> {
			base = origin.add(index * 60, 0, 0);
			mc.options.setCameraType(type);
			mc.gui.clear();
			mc.options.hideGui = true;
			camPos = cam == null ? null : base.add(cam);
			camLook = look == null ? null : base.add(look);
			server(mc, p -> {
				clearDummies();
				reset(p, form, pitch);
				setup.accept(p);
			});
		});
	}

	private static void press(Minecraft mc, int t, String id, boolean down) {
		at(t, () -> server(mc, p -> SaiyanMoves.press(p, id, down)));
	}

	private static boolean want(String name) {
		return only == null || only.isEmpty() || only.contains(name);
	}

	private static void script(Minecraft mc) {
		int t = 10;
		at(t, () -> server(mc, p -> {
			base = origin;
			p.setGameMode(GameType.SURVIVAL);
		}));
		t += 30;
		int idx = 1;
		Vec3 side = new Vec3(5.5, 1.6, 2.2);
		Vec3 sideLook = new Vec3(0, 1.1, 2.2);
		if (want("combo")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, side, sideLook, SaiyanForm.BASE, 0f, p -> dummy(p, base.add(0, 0, 2.2), 180f));
			record(t + 20, "combo", 60);
			for (int k = 0; k < 4; k++) {
				press(mc, t + 25 + k * 7, SaiyanMoves.COMBO, true);
			}
			t += 90;
		}
		if (want("feint")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, side, sideLook, SaiyanForm.SSJ, 0f, p -> dummy(p, base.add(0, 0, 2.2), 180f));
			record(t + 20, "feint", 40);
			press(mc, t + 25, SaiyanMoves.FEINT, true);
			t += 70;
		}
		if (want("beam")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(7, 2.5, 5), new Vec3(0, 1.2, 6), SaiyanForm.BASE, 0f, p -> {
				dummy(p, base.add(0, 0, 12), 180f);
				dummy(p, base.add(0.8, 0, 13), 180f);
			});
			record(t + 20, "beam", 90);
			press(mc, t + 25, SaiyanMoves.KI_BEAM, true);
			press(mc, t + 55, SaiyanMoves.KI_BEAM, false);
			t += 130;
			// first person
			scene(mc, t, idx++, CameraType.FIRST_PERSON, null, null, SaiyanForm.BLUE, 0f, p -> dummy(p, base.add(0, 0, 10), 180f));
			record(t + 20, "beam_fp", 80);
			press(mc, t + 25, SaiyanMoves.KI_BEAM, true);
			press(mc, t + 45, SaiyanMoves.KI_BEAM, false);
			t += 120;
		}
		if (want("orb")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(12, 6, -6), new Vec3(0, 4, 8), SaiyanForm.BASE, 18f, p -> {
				dummy(p, base.add(0, 0, 16), 180f);
				dummy(p, base.add(2, 0, 17), 180f);
				dummy(p, base.add(-2, 0, 17), 180f);
			});
			record(t + 20, "orb", 150);
			press(mc, t + 25, SaiyanMoves.SPIRIT_ORB, true);
			press(mc, t + 110, SaiyanMoves.SPIRIT_ORB, false);
			t += 190;
		}
		if (want("barrage")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, side, sideLook, SaiyanForm.SSJ2, 0f, p -> dummy(p, base.add(0, 0, 2.0), 180f));
			record(t + 20, "barrage", 55);
			press(mc, t + 24, SaiyanMoves.BARRAGE, true);
			t += 85;
		}
		if (want("sky")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(9, 5, 2.5), new Vec3(0, 5, 2.5), SaiyanForm.SSJ, 0f,
					p -> {
						// a mob that moves (a no-AI mob can not be knocked up): slowed so it stays put until kicked
						Mob m = dummy(p, base.add(0, 0, 2.5), 180f);
						m.setNoAi(false);
						m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100000, 9, false, false));
					});
			record(t + 20, "sky", 80);
			press(mc, t + 24, SaiyanMoves.SKY_COMBO, true);
			t += 110;
		}
		if (want("rush")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(9, 2.2, 8), new Vec3(0, 1.2, 8), SaiyanForm.SSJ3, 0f,
					p -> dummy(p, base.add(0, 0, 14), 180f));
			record(t + 20, "rush", 50);
			press(mc, t + 24, SaiyanMoves.DRAGON_RUSH, true);
			t += 80;
		}
		if (want("step")) {
			scene(mc, t, idx++, CameraType.THIRD_PERSON_BACK, new Vec3(8, 2.2, 5), new Vec3(0, 1.2, 5), SaiyanForm.INSTINCT, 0f,
					p -> dummy(p, base.add(0, 0, 9), 180f));
			record(t + 20, "step", 40);
			press(mc, t + 26, SaiyanMoves.INSTANT_STEP, true);
			t += 70;
		}
		at(t, () -> {
			System.out.println("[HARNESS] done");
			Runtime.getRuntime().halt(0);
		});
	}
}
