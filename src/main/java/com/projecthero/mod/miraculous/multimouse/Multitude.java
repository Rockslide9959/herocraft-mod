package com.projecthero.mod.miraculous.multimouse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.projecthero.mod.hero.power.AbilityHelpers;
import com.projecthero.mod.miraculous.Miraculous;
import com.projecthero.mod.miraculous.data.MiraculousState;
import com.projecthero.mod.miraculous.multimouse.entity.MiniMouseEntity;
import com.projecthero.mod.miraculous.multimouse.entity.MultimouseEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v0.17.1: MULTITUDE (Shift+Z, the special). She cries "Multitude!" and splits: she shrinks to a quarter of her size
 * (generic.scale, see {@link MultimouseMoves#applySize}) and {@link MultimouseConfig#MULTITUDE_COPIES} mini copies of her
 * appear ({@link MiniMouseEntity#MULTITUDE}). For {@link MultimouseConfig#MULTITUDE_TICKS} they fight beside her; her own
 * moves do half damage. Shift+Z again (never on the cooldown) or the timer running out recombines: the survivors run
 * back for up to {@link MultimouseConfig#RECOMBINE_TICKS}, then she snaps back to full size in a shockwave (knockback
 * within {@link MultimouseConfig#RECOMBINE_RADIUS}, {@link MultimouseConfig#RECOMBINE_DAMAGE_PER_COPY} for each copy that
 * made it back). Using it starts the Miraculous countdown. Detransforming, death or logout drop the copies at once.
 */
public final class Multitude {
	/** player -> the copies' entity ids. */
	private static final Map<UUID, List<Integer>> COPIES = new ConcurrentHashMap<>();

	private Multitude() {
	}

	static void clearSessionState() {
		COPIES.clear();
	}

	static void forget(UUID id) {
		COPIES.remove(id);
	}

	public static boolean live(ServerPlayer p) {
		return Miraculous.state(p).val(Multimouse.V_MULTI) != 0L;
	}

	/** The copies still alive (gametests, the HUD count). */
	public static List<MiniMouseEntity> copies(ServerPlayer p) {
		List<MiniMouseEntity> out = new ArrayList<>();
		List<Integer> ids = COPIES.get(p.getUUID());
		if (ids == null) {
			return out;
		}
		for (int id : ids) {
			if (p.serverLevel().getEntity(id) instanceof MiniMouseEntity m && m.isAlive() && !m.isRemoved()) {
				out.add(m);
			}
		}
		return out;
	}

	/** Split. Returns the copies made. */
	public static List<MiniMouseEntity> start(ServerPlayer p) {
		ServerLevel level = p.serverLevel();
		long now = level.getGameTime();
		List<Integer> ids = new ArrayList<>();
		List<MiniMouseEntity> out = new ArrayList<>();
		int n = MultimouseConfig.MULTITUDE_COPIES;
		for (int i = 0; i < n; i++) {
			MiniMouseEntity m = MultimouseEntityTypes.MINI.create(level);
			if (m == null) {
				continue;
			}
			double a = Math.PI * 2 * i / n;
			double r = 0.9 + 0.35 * (i % 2);
			m.moveTo(p.getX() + Math.cos(a) * r, p.getY(), p.getZ() + Math.sin(a) * r, p.getYRot(), 0f);
			m.setup(p, MiniMouseEntity.MULTITUDE, Integer.MAX_VALUE, MultimouseConfig.MINI_BITE, null);
			level.addFreshEntity(m);
			ids.add(m.getId());
			out.add(m);
			MultimouseMoves.sparkle(level, m.position().add(0, 0.2, 0), 6);
		}
		COPIES.put(p.getUUID(), ids);
		Miraculous.edit(p, s -> {
			s.vals.put(Multimouse.V_MULTI, now + MultimouseConfig.MULTITUDE_TICKS);
			s.vals.put(Multimouse.V_MULTI_LEFT, (long) out.size());
			s.vals.put(Multimouse.V_MULTI_TOTAL, (long) out.size());
			s.vals.remove(Multimouse.V_RECOMBINE);
			s.vals.remove(Multimouse.V_SCURRY);
		});
		Miraculous.setAnim(p, Multimouse.ANIM_MULTITUDE);
		Miraculous.setCooldown(p, MultimouseConfig.MULTITUDE, MultimouseConfig.MULTITUDE_COOLDOWN);
		Miraculous.startCountdown(p);
		MultimouseMoves.applySize(p);
		// a ring of pink light round her feet (not a cloud in her own face)
		MultimouseMoves.ring(level, MultimouseMoves.PINK_BIG, p.position().add(0, 0.15, 0), 1.3, 20);
		MultimouseMoves.sparkle(level, p.position().add(0, 0.3, 0), 8);
		MultimouseMoves.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.8f);
		MultimouseMoves.sound(p, SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0f, 1.6f);
		MultimouseMoves.sound(p, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.8f, 1.6f);
		Miraculous.say(p, "message.projecthero.multimouse.multitude", ChatFormatting.LIGHT_PURPLE);
		return out;
	}

	/** Shift+Z again / the timer: the survivors run back; the shockwave lands at the end of {@link MultimouseConfig#RECOMBINE_TICKS}. */
	public static void recombine(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		if (st.val(Multimouse.V_MULTI) == 0L || st.val(Multimouse.V_RECOMBINE) != 0L) {
			return;
		}
		long at = p.level().getGameTime() + MultimouseConfig.RECOMBINE_TICKS;
		Miraculous.setVal(p, Multimouse.V_RECOMBINE, at);
		Miraculous.setAnim(p, Multimouse.ANIM_RECOMBINE);
		for (MiniMouseEntity m : copies(p)) {
			m.runBack();
		}
		MultimouseMoves.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
	}

	static void tick(ServerPlayer p) {
		MiraculousState st = Miraculous.state(p);
		if (st.val(Multimouse.V_MULTI) == 0L) {
			return;
		}
		long now = p.level().getGameTime();
		List<MiniMouseEntity> alive = copies(p);
		if (alive.size() != (int) st.val(Multimouse.V_MULTI_LEFT)) {
			int left = alive.size();
			Miraculous.edit(p, s -> s.vals.put(Multimouse.V_MULTI_LEFT, (long) left));
		}
		long recombine = st.val(Multimouse.V_RECOMBINE);
		if (recombine == 0L) {
			if (now >= st.val(Multimouse.V_MULTI)) {
				recombine(p);
			}
			return;
		}
		boolean home = true;
		for (MiniMouseEntity m : alive) {
			if (m.distanceToSqr(p) > 1.5 * 1.5) {
				home = false;
				break;
			}
		}
		// back together once every survivor is home or the time is up -- and there is room for her full size (no
		// growing back inside a 1-block gap), else as soon as there is, at the latest 5 s later
		if ((home || now >= recombine) && (MultimouseMoves.roomToGrow(p) || now >= recombine + 100)) {
			finish(p, alive);
		}
	}

	/** The shockwave: knockback round her, damage for each copy that made it back; full size again. Returns how many were hit. */
	static int finish(ServerPlayer p, List<MiniMouseEntity> survivors) {
		int back = survivors.size();
		ServerLevel level = p.serverLevel();
		for (MiniMouseEntity m : survivors) {
			MultimouseMoves.sparkle(level, m.position().add(0, 0.2, 0), 6);
			m.discard();
		}
		COPIES.remove(p.getUUID());
		Miraculous.edit(p, s -> {
			s.vals.remove(Multimouse.V_MULTI);
			s.vals.remove(Multimouse.V_MULTI_LEFT);
			s.vals.remove(Multimouse.V_MULTI_TOTAL);
			s.vals.remove(Multimouse.V_RECOMBINE);
		});
		MultimouseMoves.applySize(p);
		float dmg = back * MultimouseConfig.RECOMBINE_DAMAGE_PER_COPY;
		double r = MultimouseConfig.RECOMBINE_RADIUS;
		Vec3 c = p.position();
		for (int k = 1; k <= 4; k++) {
			MultimouseMoves.ring(level, k % 2 == 0 ? MultimouseMoves.PINK_BIG : MultimouseMoves.SILVER, c.add(0, 0.2 + 0.2 * k, 0), r * k / 4.0, 10 + 6 * k);
		}
		MultimouseMoves.sparkle(level, c.add(0, 0.3, 0), 12);
		MultimouseMoves.sound(p, SoundEvents.GENERIC_EXPLODE, 0.5f, 1.6f);
		MultimouseMoves.sound(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2f, 1.2f);
		int n = 0;
		for (LivingEntity e : AbilityHelpers.enemiesAround(p, c.add(0, 0.9, 0), r)) {
			if (e instanceof MiniMouseEntity || e.distanceToSqr(p) > r * r) {
				continue;
			}
			// (full size again here, so the shockwave is never halved)
			boolean landed = dmg > 0f ? MultimouseMoves.hit(p, e, c, dmg, MultimouseConfig.RECOMBINE_KNOCKBACK, 0.3) : false;
			if (!landed && dmg <= 0f && !MultimouseMoves.isBoss(e) && com.projecthero.mod.combat.HeroTargets.canHarm(p, e)) {
				AbilityHelpers.knockbackFrom(e, c, MultimouseConfig.RECOMBINE_KNOCKBACK);
				landed = true;
			}
			if (landed) {
				n++;
			}
		}
		return n;
	}

	/** Detransforming / death / logout: the copies are gone at once (no shockwave). */
	static void drop(ServerPlayer p) {
		for (MiniMouseEntity m : copies(p)) {
			m.pop();
		}
		COPIES.remove(p.getUUID());
	}
}
