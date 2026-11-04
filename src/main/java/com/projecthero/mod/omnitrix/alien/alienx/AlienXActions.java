package com.projecthero.mod.omnitrix.alien.alienx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.projecthero.mod.hero.HeroConfig;
import com.projecthero.mod.hero.power.TempBlocks;
import com.projecthero.mod.omnitrix.OmnitrixDamage;
import com.projecthero.mod.omnitrix.OmnitrixSfx;
import com.projecthero.mod.omnitrix.alien.AlienStatus;
import com.projecthero.mod.quirk.QuirkThreat;
import com.projecthero.mod.quirk.fx.QuirkCamera;
import com.projecthero.mod.quirk.fx.QuirkFx;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.2 (user: "make a good debating system where the player can do many many many things"; "Alien X can one shot anything
 * but the new system wont allow him to just go and hit things"): everything the Celestial Council can be talked into, done at
 * Alien X's body in the world. Each action has a category, the targets it takes ({@code K_} bits), how inclined each head is to
 * it before Ben says a word ({@link Action#bellicus} / {@link Action#serena}, 0..100), the argument tones that contradict it
 * ({@link Action#contra}: arguing mercy while asking to kill backfires) and its cooldown once carried.
 *
 * <p>Everything is validated again here (target alive, in range of the body, same world). Terrain changes are temporary
 * (TempBlocks, or the erased-block queue put back on a timer) and need the abilityTerrainDamage rule.
 */
public final class AlienXActions {
	/** Target kinds: a creature (not a player), a player, a point (where his body looks, or a creature's spot), the area round the body, the body. */
	public static final int K_LIVING = 1, K_PLAYER = 2, K_POINT = 4, K_AREA = 8, K_BODY = 16;
	/** Special target ids (entity ids are >= 0). */
	public static final int T_AREA = -1, T_POINT = -2, T_BODY = -3;
	/** How far from the body a target may be. */
	public static final double RANGE = 48;

	public static final List<String> CATEGORIES = List.of("strike", "time", "protect", "heal", "reshape", "create", "banish", "weather", "gravity",
			"travel", "summon", "leave");

	/**
	 * @param bellicus / serena the heads' leaning before any argument (the meters start here)
	 * @param contra   a mask of the argument tones ({@link AlienXCouncil#TONES}) that contradict this action
	 */
	public record Action(String id, String category, int kinds, int bellicus, int serena, int contra, int cooldown) {
		public boolean takes(int kind) {
			return (kinds & kind) != 0;
		}
	}

	private static final int FORCE = 1, GLORY = 2, MERCY = 1 << 4, HUMILITY = 1 << 7;
	private static final Map<String, Action> ACTIONS = new LinkedHashMap<>();

	static {
		// strike -- "Alien X can one shot anything"
		add("unmake", "strike", K_LIVING | K_PLAYER, 50, 0, MERCY | HUMILITY, 600);
		add("starfall", "strike", K_POINT | K_LIVING | K_PLAYER, 45, 10, MERCY, 300);
		add("disarm", "strike", K_LIVING | K_PLAYER, 30, 40, 0, 200);
		// time
		add("stop_time", "time", K_AREA, 40, 40, 0, 600);
		add("slow_time", "time", K_AREA, 40, 45, 0, 400);
		// protect
		add("shield", "protect", K_PLAYER | K_BODY, 25, 55, FORCE, 400);
		add("barrier", "protect", K_AREA, 30, 55, 0, 400);
		add("calm", "protect", K_AREA, 10, 60, FORCE | GLORY, 400);
		// heal
		add("restore", "heal", K_PLAYER, 20, 65, FORCE, 300);
		add("mend_all", "heal", K_AREA, 15, 60, FORCE, 600);
		// reshape the world
		add("raise_wall", "reshape", K_POINT | K_LIVING | K_PLAYER, 40, 35, 0, 300);
		add("open_ground", "reshape", K_POINT | K_LIVING | K_PLAYER, 50, 15, MERCY, 300);
		// create
		add("cage", "create", K_LIVING | K_PLAYER, 40, 40, 0, 300);
		// banish
		add("banish", "banish", K_LIVING | K_PLAYER, 40, 20, MERCY, 600);
		add("exile", "banish", K_AREA, 35, 30, 0, 600);
		// weather
		add("clear_skies", "weather", K_AREA, 35, 45, 0, 600);
		add("storm", "weather", K_AREA, 55, 20, 0, 600);
		add("bring_day", "weather", K_AREA, 35, 45, 0, 1200);
		add("bring_night", "weather", K_AREA, 45, 30, 0, 1200);
		// gravity
		add("lift", "gravity", K_LIVING | K_PLAYER | K_AREA, 45, 25, 0, 300);
		add("crush", "gravity", K_AREA, 55, 5, MERCY | HUMILITY, 400);
		// travel
		add("move_body", "travel", K_POINT | K_LIVING | K_PLAYER, 40, 40, 0, 200);
		add("summon_ally", "travel", K_PLAYER, 30, 50, 0, 400);
		// summon
		add("guardian", "summon", K_AREA, 45, 40, 0, 1200);
		// leave -- v0.17.2 (user: "when detransforming as alien x make it so that the player has to convince the heads to let them
		// detransform aswell please"): turning back is a motion too; carried, he returns to his body as Ben
		add("release", "leave", K_BODY, 30, 35, 0, 0);
	}

	private static void add(String id, String category, int kinds, int b, int s, int contra, int cooldown) {
		ACTIONS.put(id, new Action(id, category, kinds, b, s, contra, cooldown));
	}

	// the numbers
	public static final float STARFALL_DAMAGE = 60f, CRUSH_DAMAGE = 50f;
	public static final double STARFALL_RADIUS = 6, STOP_RADIUS = 16, SLOW_RADIUS = 24, CALM_RADIUS = 24, MEND_RADIUS = 16, EXILE_RADIUS = 16,
			CRUSH_RADIUS = 10, LIFT_RADIUS = 12, BARRIER_RADIUS = 5;
	public static final int STOP_TICKS = 200, SLOW_TICKS = 400, SHIELD_TICKS = 600, BARRIER_TICKS = 400, WALL_TICKS = 400, PIT_TICKS = 200,
			CAGE_TICKS = 300, LIFT_TICKS = 60, GUARDIAN_TICKS = 1200, MAX_ERASED = 200;

	private AlienXActions() {
	}

	public static Map<String, Action> all() {
		return ACTIONS;
	}

	@Nullable
	public static Action get(String id) {
		return ACTIONS.get(id);
	}

	public static List<Action> inCategory(String category) {
		List<Action> out = new ArrayList<>();
		for (Action a : ACTIONS.values()) {
			if (a.category().equals(category)) {
				out.add(a);
			}
		}
		return out;
	}

	/** A target's kind for {@code e} (or the special ids). */
	public static int kindOf(int targetId, @Nullable Entity e) {
		return switch (targetId) {
			case T_AREA -> K_AREA;
			case T_POINT -> K_POINT;
			case T_BODY -> K_BODY;
			default -> e instanceof Player ? K_PLAYER : e instanceof LivingEntity ? K_LIVING : 0;
		};
	}

	// ---------------------------------------------------------------- doing it

	/** What happened: a transcript line (lang key + args; an arg starting '#' is itself a lang key). */
	public record Result(boolean done, String key, String... args) {
	}

	/**
	 * Carries out {@code a} at {@code origin} (the body; Ben himself if there is none) on target {@code targetId}. Never throws;
	 * a target that has gone (died, left range) since the motion was moved gives a not-done result.
	 */
	public static Result execute(ServerPlayer p, LivingEntity origin, Action a, int targetId) {
		ServerLevel l = (ServerLevel) origin.level();
		Entity te = targetId >= 0 ? l.getEntity(targetId) : null;
		LivingEntity t = te instanceof LivingEntity le && le.isAlive() && le.distanceTo(origin) <= RANGE + 8 ? le : null;
		if (targetId >= 0 && t == null) {
			return new Result(false, "council.result.gone");
		}
		Vec3 at = targetId == T_POINT ? (origin instanceof AlienXBodyEntity b ? b.lookEnd(RANGE) : lookEnd(origin))
				: t != null ? t.position() : origin.position();
		if (origin instanceof AlienXBodyEntity b) {
			b.act();
		}
		OmnitrixSfx.play(l, origin.position(), AlienXKit.CARRIED, 1.0f, 1.0f);
		String name = t != null ? t.getDisplayName().getString() : "";
		switch (a.id()) {
			case "unmake" -> {
				// v0.17.2 (user: "Alien X can one shot anything"): unmade -- whatever its health, armour or boss status
				QuirkFx.ribbon(l, origin.getEyePosition(), t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0xFFFFFF, 0x9FC4FF).size(0.5f).life(12).spawn();
				QuirkFx.blast(l, t.position().add(0, t.getBbHeight() * 0.5, 0)).colours(0xFFFFFF, 0x8A4DFF).size(Math.max(1.5f, t.getBbHeight())).life(16).spawn();
				OmnitrixSfx.play(l, t.position(), AlienXKit.BANG_SOUND, 1.2f, 1.3f);
				t.invulnerableTime = 0;
				t.hurt(OmnitrixDamage.alienStrike(origin), Math.max(1000f, t.getMaxHealth() * 10f));
				if (t.isAlive() && !(t instanceof Player pl && (pl.isCreative() || pl.isSpectator()))) {
					t.kill();
				}
				QuirkThreat.add(t, p, 10f);
				QuirkCamera.shakeAround(l, t.position(), 24, 0.5f, 10);
				return new Result(true, "council.result.unmake", name);
			}
			case "starfall" -> {
				QuirkFx.ribbon(l, at.add(0, 30, 0), at).colours(0xFFFFFF, 0x9FC4FF).size(1.2f).life(10).spawn();
				QuirkFx.blast(l, at).colours(0xFFFFFF, 0x6A5AFF).size((float) STARFALL_RADIUS).life(20).spawn();
				QuirkFx.ring(l, at).colours(0xBFD8FF, 0x3A2A9A).size((float) STARFALL_RADIUS + 2).ground().life(18).spawn();
				OmnitrixSfx.play(l, at, AlienXKit.BANG_SOUND, 1.4f, 1.1f);
				int n = 0;
				for (LivingEntity e : around(p, origin, at, STARFALL_RADIUS)) {
					e.invulnerableTime = 0;
					e.hurt(OmnitrixDamage.alienStrike(origin), STARFALL_DAMAGE);
					n++;
				}
				QuirkCamera.shakeAround(l, at, 32, 0.6f, 12);
				return new Result(true, "council.result.starfall", String.valueOf(n));
			}
			case "disarm" -> {
				if (t instanceof Mob m) {
					for (EquipmentSlot s : new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND }) {
						ItemStack st = m.getItemBySlot(s);
						if (!st.isEmpty()) {
							m.spawnAtLocation(st.copy());
							m.setItemSlot(s, ItemStack.EMPTY);
						}
					}
				}
				t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 2), origin);
				QuirkFx.ring(l, t.position().add(0, t.getBbHeight() * 0.6, 0)).colours(0xFFFFFF, 0x9FC4FF).size(1.6f).life(12).spawn();
				return new Result(true, "council.result.disarm", name);
			}
			case "stop_time" -> {
				for (LivingEntity e : around(p, origin, origin.position(), STOP_RADIUS)) {
					AlienStatus.stun(p, e, STOP_TICKS);
					e.setDeltaMovement(Vec3.ZERO);
					e.hurtMarked = true;
				}
				STOPS.add(new Stop(l, origin.position(), l.getGameTime() + STOP_TICKS));
				QuirkFx.ring(l, origin.position()).colours(0xFFE6A0, 0x3A2A9A).size((float) STOP_RADIUS).ground().life(STOP_TICKS).spawn();
				OmnitrixSfx.play(l, origin.position(), AlienXKit.STOP_SOUND, 1.4f, 1.0f);
				return new Result(true, "council.result.stop_time");
			}
			case "slow_time" -> {
				int n = 0;
				for (LivingEntity e : around(p, origin, origin.position(), SLOW_RADIUS)) {
					if (e instanceof Enemy) {
						e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 2), origin);
						e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, SLOW_TICKS, 2), origin);
						n++;
					}
				}
				QuirkFx.ring(l, origin.position()).colours(0xFFE6A0, 0x6A5AFF).size((float) SLOW_RADIUS).ground().life(30).spawn();
				OmnitrixSfx.play(l, origin.position(), AlienXKit.STOP_SOUND, 1.0f, 0.7f);
				return new Result(true, "council.result.slow_time", String.valueOf(n));
			}
			case "shield" -> {
				LivingEntity who = t != null ? t : origin;
				who.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, SHIELD_TICKS, 3), origin);
				who.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SHIELD_TICKS, 3), origin);
				QuirkFx.ring(l, who.position().add(0, who.getBbHeight() * 0.5, 0)).colours(0x9FE8FF, 0x3A7BFF).size(1.4f).life(20).spawn();
				return new Result(true, "council.result.shield", t != null ? name : "#projecthero.omnitrix.alien_x.council.target.body");
			}
			case "barrier" -> {
				int placed = dome(l, BlockPos.containing(origin.position()), BARRIER_RADIUS, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), BARRIER_TICKS);
				QuirkFx.ring(l, origin.position()).colours(0x9FE8FF, 0x3A7BFF).size((float) BARRIER_RADIUS).ground().life(20).spawn();
				return new Result(true, placed > 0 ? "council.result.barrier" : "council.result.barrier_none");
			}
			case "calm" -> {
				int n = 0;
				for (LivingEntity e : around(p, origin, origin.position(), CALM_RADIUS)) {
					if (e instanceof Mob m && !AlienStatus.isBoss(e)) {
						m.setTarget(null);
						m.getNavigation().stop();
						m.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0), origin);
						m.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), origin);
						n++;
					}
				}
				QuirkFx.ring(l, origin.position()).colours(0x9FE8FF, 0x7CFF6A).size((float) CALM_RADIUS).ground().life(30).spawn();
				return new Result(true, "council.result.calm", String.valueOf(n));
			}
			case "restore" -> {
				mend(t, t.getMaxHealth());
				if (t instanceof Player pl) {
					pl.getFoodData().eat(20, 20f);
				}
				QuirkFx.ring(l, t.position()).colours(0x7CFF6A, 0x2A8A3A).size(1.5f).life(20).spawn();
				return new Result(true, "council.result.restore", name);
			}
			case "mend_all" -> {
				int n = 0;
				for (Player o : l.getEntitiesOfClass(Player.class, new AABB(origin.position(), origin.position()).inflate(MEND_RADIUS))) {
					if (o.isAlive() && o != p) {
						mend(o, 20f);
						n++;
					}
				}
				QuirkFx.ring(l, origin.position()).colours(0x7CFF6A, 0x2A8A3A).size((float) MEND_RADIUS).ground().life(24).spawn();
				return new Result(true, "council.result.mend_all", String.valueOf(n));
			}
			case "raise_wall" -> {
				Vec3 dir = at.subtract(origin.position());
				Vec3 h = new Vec3(dir.x, 0, dir.z);
				h = h.lengthSqr() < 1.0e-4 ? new Vec3(0, 0, 1) : h.normalize();
				Vec3 side = new Vec3(-h.z, 0, h.x);
				Vec3 base = targetId >= 0 ? at.subtract(h.scale(2.0)) : at;
				int placed = 0;
				for (int s = -3; s <= 3; s++) {
					for (int y = 0; y < 4; y++) {
						if (TempBlocks.place(l, BlockPos.containing(base.add(side.scale(s)).add(0, y + 0.1, 0)), Blocks.STONE.defaultBlockState(), WALL_TICKS)) {
							placed++;
						}
					}
				}
				QuirkFx.shards(l, base).block(Blocks.STONE.defaultBlockState()).size(2f).count(16).spawn();
				return new Result(true, placed > 0 ? "council.result.raise_wall" : "council.result.terrain_off");
			}
			case "open_ground" -> {
				int n = pit(l, at, PIT_TICKS);
				QuirkFx.ring(l, at).colours(0x8A4DFF, 0x10142A).size(2.5f).ground().life(20).spawn();
				OmnitrixSfx.play(l, at, AlienXKit.ERASE_SOUND, 1.2f, 1.0f);
				return new Result(true, n > 0 ? "council.result.open_ground" : "council.result.terrain_off");
			}
			case "cage" -> {
				int placed = cage(l, t, Blocks.BLACK_STAINED_GLASS.defaultBlockState(), CAGE_TICKS);
				AlienStatus.stun(p, t, CAGE_TICKS);
				OmnitrixSfx.play(l, t.position(), AlienXKit.CREATE_SOUND, 1.2f, 1.0f);
				return new Result(true, placed > 0 ? "council.result.cage" : "council.result.cage_held", name);
			}
			case "banish" -> {
				QuirkFx.blast(l, t.position().add(0, 1, 0)).colours(0xFFFFFF, 0x8A4DFF).size(2f).life(14).spawn();
				Vec3 to = farAway(l, t.position(), 600, 1000);
				t.teleportTo(to.x, to.y, to.z);
				t.resetFallDistance();
				OmnitrixSfx.play(l, origin.position(), AlienXKit.BLINK_SOUND, 1.2f, 0.7f);
				return new Result(true, "council.result.banish", name, String.valueOf((int) to.distanceTo(origin.position())));
			}
			case "exile" -> {
				int n = 0;
				for (LivingEntity e : around(p, origin, origin.position(), EXILE_RADIUS)) {
					if (e instanceof Enemy && !AlienStatus.isBoss(e)) {
						Vec3 to = farAway(l, e.position(), 48, 80);
						e.teleportTo(to.x, to.y, to.z);
						e.resetFallDistance();
						n++;
					}
				}
				QuirkFx.ring(l, origin.position()).colours(0xFFFFFF, 0x8A4DFF).size((float) EXILE_RADIUS).ground().life(20).spawn();
				return new Result(true, "council.result.exile", String.valueOf(n));
			}
			case "clear_skies", "storm" -> {
				if (!l.dimensionType().hasSkyLight()) {
					return new Result(false, "council.result.no_sky");
				}
				boolean storm = a.id().equals("storm");
				l.setWeatherParameters(storm ? 0 : 12000, storm ? 6000 : 0, storm, storm);
				return new Result(true, "council.result." + a.id());
			}
			case "bring_day", "bring_night" -> {
				long day = l.getDayTime();
				long base = day - Math.floorMod(day, 24000L);
				long want = a.id().equals("bring_day") ? 1000L : 13000L;
				l.setDayTime(base + want + (Math.floorMod(day, 24000L) > want ? 24000L : 0L));
				return new Result(true, "council.result." + a.id());
			}
			case "lift" -> {
				int n = 0;
				List<LivingEntity> who = t != null ? List.of(t) : around(p, origin, origin.position(), LIFT_RADIUS).stream().filter(e -> e instanceof Enemy).toList();
				for (LivingEntity e : who) {
					e.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LIFT_TICKS, 3), origin);
					n++;
				}
				QuirkFx.ring(l, t != null ? t.position() : origin.position()).colours(0x9FC4FF, 0x6A5AFF).size(t != null ? 1.5f : (float) LIFT_RADIUS).ground().life(20).spawn();
				return new Result(true, "council.result.lift", String.valueOf(n));
			}
			case "crush" -> {
				int n = 0;
				for (LivingEntity e : around(p, origin, origin.position(), CRUSH_RADIUS)) {
					e.invulnerableTime = 0;
					e.hurt(OmnitrixDamage.alienStrike(origin), CRUSH_DAMAGE);
					e.setDeltaMovement(e.getDeltaMovement().x * 0.2, -1.4, e.getDeltaMovement().z * 0.2);
					e.hurtMarked = true;
					e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3), origin);
					n++;
				}
				QuirkFx.ring(l, origin.position()).colours(0x8A4DFF, 0x10142A).size((float) CRUSH_RADIUS).ground().life(16).spawn();
				QuirkCamera.shakeAround(l, origin.position(), 24, 0.6f, 12);
				OmnitrixSfx.play(l, origin.position(), AlienXKit.CRUSH_SOUND, 1.4f, 1.0f);
				return new Result(true, "council.result.crush", String.valueOf(n));
			}
			case "move_body" -> {
				Vec3 to = t != null ? freeSpotNear(l, origin, t.position()) : freeSpotNear(l, origin, at);
				if (to == null) {
					return new Result(false, "council.result.no_room");
				}
				QuirkFx.blast(l, origin.position().add(0, 1.2, 0)).colours(0xFFFFFF, 0x8A4DFF).size(1.5f).life(10).spawn();
				origin.teleportTo(to.x, to.y, to.z);
				OmnitrixSfx.play(l, to, AlienXKit.BLINK_SOUND, 1.0f, 1.0f);
				return new Result(true, "council.result.move_body", String.valueOf((int) to.distanceTo(at)));
			}
			case "summon_ally" -> {
				Vec3 to = freeSpotNear(l, t, origin.position().add(origin.getLookAngle().multiply(1, 0, 1).normalize().scale(2)));
				if (to == null) {
					return new Result(false, "council.result.no_room");
				}
				t.teleportTo(to.x, to.y, to.z);
				t.resetFallDistance();
				OmnitrixSfx.play(l, to, AlienXKit.BLINK_SOUND, 1.0f, 1.2f);
				return new Result(true, "council.result.summon_ally", name);
			}
			case "release" -> {
				// the council lets him go: AlienXCouncil turns him back a moment after the banner (Session.releaseAt)
				QuirkFx.ring(l, origin.position().add(0, 0.1, 0)).colours(0xFFFFFF, 0x9FC4FF).size(3f).ground().life(14).spawn();
				return new Result(true, "council.result.release");
			}
			case "guardian" -> {
				IronGolem g = EntityType.IRON_GOLEM.create(l);
				if (g == null) {
					return new Result(false, "council.result.no_room");
				}
				Vec3 to = freeSpotNear(l, g, origin.position().add(origin.getLookAngle().multiply(1, 0, 1).normalize().scale(3)));
				if (to == null) {
					return new Result(false, "council.result.no_room");
				}
				g.moveTo(to.x, to.y, to.z, origin.getYRot(), 0);
				g.setPlayerCreated(true);
				g.finalizeSpawn(l, l.getCurrentDifficultyAt(BlockPos.containing(to)), MobSpawnType.MOB_SUMMONED, null);
				l.addFreshEntity(g);
				GUARDIANS.add(new Guardian(l, g.getUUID(), l.getGameTime() + GUARDIAN_TICKS));
				QuirkFx.blast(l, to.add(0, 1.4, 0)).colours(0xFFFFFF, 0x9FC4FF).size(2f).life(14).spawn();
				return new Result(true, "council.result.guardian");
			}
			default -> {
				return new Result(false, "council.result.gone");
			}
		}
	}

	private static Vec3 lookEnd(LivingEntity e) {
		Vec3 eye = e.getEyePosition();
		Vec3 end = eye.add(e.getLookAngle().scale(RANGE));
		var hit = e.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
				net.minecraft.world.level.ClipContext.Fluid.NONE, e));
		return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
	}

	/** Living things within {@code r} of {@code at}: never Ben, his body, or his squad allies. */
	static List<LivingEntity> around(ServerPlayer p, LivingEntity origin, Vec3 at, double r) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : origin.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r))) {
			if (e == p || e == origin || e instanceof AlienXBodyEntity || !e.isAlive() || e.isSpectator()
					|| e instanceof net.minecraft.world.entity.decoration.ArmorStand || e.distanceToSqr(at) > r * r * 1.4) {
				continue;
			}
			if (com.projecthero.mod.squad.Squads.areAllies(p, e) && !com.projecthero.mod.squad.Squads.friendlyFireOn(p)) {
				continue;
			}
			out.add(e);
		}
		return out;
	}

	private static void mend(LivingEntity e, float amount) {
		e.heal(amount);
		e.clearFire();
		e.getActiveEffects().stream().filter(x -> x.getEffect().value().getCategory() == MobEffectCategory.HARMFUL).map(MobEffectInstance::getEffect).toList()
				.forEach(e::removeEffect);
	}

	/** A spot on the surface {@code min}..{@code max} blocks from {@code from} in a random direction. */
	private static Vec3 farAway(ServerLevel l, Vec3 from, int min, int max) {
		var r = l.getRandom();
		for (int i = 0; i < 8; i++) {
			double ang = r.nextDouble() * Math.PI * 2;
			double d = min + r.nextDouble() * (max - min);
			int x = Mth.floor(from.x + Math.cos(ang) * d), z = Mth.floor(from.z + Math.sin(ang) * d);
			if (!l.getWorldBorder().isWithinBounds(x, z)) {
				continue;
			}
			l.getChunk(x >> 4, z >> 4); // load it: the heightmap must be real
			int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (y > l.getMinBuildHeight() + 1 && y < l.getMaxBuildHeight() - 2) {
				return new Vec3(x + 0.5, y, z + 0.5);
			}
		}
		return from.add(0, 0, 0);
	}

	/** A free standing spot for {@code e} near {@code at}, or null. */
	@Nullable
	private static Vec3 freeSpotNear(ServerLevel l, Entity e, Vec3 at) {
		for (int ring = 0; ring <= 3; ring++) {
			for (int dx = -ring; dx <= ring; dx++) {
				for (int dz = -ring; dz <= ring; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
						continue;
					}
					for (int dy = -2; dy <= 3; dy++) {
						Vec3 feet = new Vec3(Mth.floor(at.x) + dx + 0.5, Mth.floor(at.y) + dy, Mth.floor(at.z) + dz + 0.5);
						AABB box = e.getType().getDimensions().makeBoundingBox(feet);
						if (l.noCollision(e, box) && !l.noCollision(e, box.move(0, -0.5, 0))) {
							return feet;
						}
					}
				}
			}
		}
		return null;
	}

	/** A hollow half-dome of {@code state} (TempBlocks, into air only) round {@code c}. */
	private static int dome(ServerLevel l, BlockPos c, double r, BlockState state, int ttl) {
		int n = 0;
		int ri = (int) Math.ceil(r);
		for (BlockPos bp : BlockPos.betweenClosed(c.offset(-ri, 0, -ri), c.offset(ri, ri, ri))) {
			double d = Math.sqrt(bp.distSqr(c));
			if (d <= r && d > r - 1.0 && TempBlocks.place(l, bp.immutable(), state, ttl)) {
				n++;
			}
		}
		return n;
	}

	/** A cage of {@code state} round {@code t}'s box (walls and roof). */
	private static int cage(ServerLevel l, LivingEntity t, BlockState state, int ttl) {
		AABB b = t.getBoundingBox();
		int x0 = Mth.floor(b.minX) - 1, x1 = Mth.floor(b.maxX - 1.0e-3) + 1, z0 = Mth.floor(b.minZ) - 1, z1 = Mth.floor(b.maxZ - 1.0e-3) + 1;
		int y0 = Mth.floor(b.minY), y1 = Mth.floor(b.maxY - 1.0e-3) + 1;
		int n = 0;
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					if ((x == x0 || x == x1 || z == z0 || z == z1 || y == y1) && TempBlocks.place(l, new BlockPos(x, y, z), state, ttl)) {
						n++;
					}
				}
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- the opened ground (erased blocks, put back on a timer)

	private record Erased(ServerLevel level, BlockPos pos, BlockState state, long until) {
	}

	private static final ArrayDeque<Erased> ERASED = new ArrayDeque<>();

	/** Unmakes a 3x3 pit 5 deep under {@code at}; never blocks with contents, unbreakable ones or fluids. */
	private static int pit(ServerLevel l, Vec3 at, int ttl) {
		if (!HeroConfig.get().abilityTerrainDamage) {
			return 0;
		}
		BlockPos c = BlockPos.containing(at.x, at.y - 0.5, at.z);
		long until = l.getGameTime() + ttl;
		int n = 0;
		synchronized (ERASED) {
			for (BlockPos bp : BlockPos.betweenClosed(c.offset(-1, -4, -1), c.offset(1, 0, 1))) {
				BlockState st = l.getBlockState(bp);
				if (n >= MAX_ERASED || st.isAir() || !st.getFluidState().isEmpty() || l.getBlockEntity(bp) != null || st.getDestroySpeed(l, bp) < 0f) {
					continue;
				}
				ERASED.addLast(new Erased(l, bp.immutable(), st, until));
				l.setBlock(bp, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
				n++;
			}
		}
		return n;
	}

	/** How many unmade blocks wait to come back (tests). */
	public static int erasedCount() {
		synchronized (ERASED) {
			return ERASED.size();
		}
	}

	// ---------------------------------------------------------------- lasting effects

	private record Stop(ServerLevel level, Vec3 at, long until) {
	}

	private record Guardian(ServerLevel level, java.util.UUID id, long until) {
	}

	private static final List<Stop> STOPS = new ArrayList<>();
	private static final List<Guardian> GUARDIANS = new ArrayList<>();

	/** Every server tick: the stopped moments hold their projectiles, unmade blocks come back, guardians go home. */
	static void tick(MinecraftServer server, boolean all) {
		synchronized (ERASED) {
			Iterator<Erased> it = ERASED.iterator();
			int budget = all ? Integer.MAX_VALUE : 256;
			while (it.hasNext() && budget-- > 0) {
				Erased e = it.next();
				if (!all && e.level().getGameTime() < e.until()) {
					continue;
				}
				it.remove();
				if (e.level().getBlockState(e.pos()).isAir()) { // never over something put there since
					e.level().setBlock(e.pos(), e.state(), Block.UPDATE_CLIENTS);
				}
			}
		}
		STOPS.removeIf(s -> all || s.level().getGameTime() >= s.until());
		for (Stop s : STOPS) {
			for (Projectile pr : s.level().getEntitiesOfClass(Projectile.class, new AABB(s.at(), s.at()).inflate(STOP_RADIUS))) {
				if (pr.getDeltaMovement().lengthSqr() > 1.0e-6) {
					pr.setDeltaMovement(Vec3.ZERO);
					pr.hurtMarked = true;
				}
			}
		}
		GUARDIANS.removeIf(g -> {
			if (!all && g.level().getGameTime() < g.until()) {
				return false;
			}
			Entity e = g.level().getEntity(g.id());
			if (e != null) {
				e.discard();
			}
			return true;
		});
	}

	static void clearSessionState() {
		synchronized (ERASED) {
			ERASED.clear();
		}
		STOPS.clear();
		GUARDIANS.clear();
	}
}
