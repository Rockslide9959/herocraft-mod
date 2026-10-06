package com.projecthero.mod.ironman.suit;

import com.projecthero.mod.attachment.ModAttachments;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;

/**
 * v0.14.21: everything a viewer needs to animate an Iron Man suit going on or coming off -- synced to <b>every</b>
 * client tracking the wearer (not just the wearer), never persisted. Written only on the server, by
 * {@link IronManSuitUpManager} (and through it the couriers, the delivery pod and the Suit Platform); read on the client
 * by the armour renderer (the per-piece plate build-on / break-off reveal and the GeckoLib lock-on / release clips), the
 * suit-up body pose and the Mark V suitcase layer.
 *
 * <ul>
 *   <li>{@code headStart .. feetStart}: the game tick a piece in that slot began locking on (its bit in
 *       {@link #assembleMask} set) or releasing (bit clear); 0 = no clock. A piece locks on over {@link #LOCK_TICKS}
 *       and releases over {@link #RELEASE_TICKS}; outside that window its clock is ignored, so a stale clock can never
 *       hide a piece that was equipped some other way later.</li>
 *   <li>{@code style}: how the plates sweep on ({@link #STYLE_PLATES} up each piece from its own edge, or
 *       {@link #STYLE_CASE} outward from the suitcase in the right hand).</li>
 *   <li>{@code poseStart / poseTicks / poseKind}: the body-pose timeline of the whole sequence (arms out while pieces
 *       lock on, the faceplate-close beat at the end).</li>
 *   <li>{@code faceplateAt}: when the faceplate last opened or closed (H, or the suit-up's closing beat), so the
 *       visor swing animates; the open/closed state itself stays in {@code IRON_MAN_FACEPLATE_OPEN}.</li>
 *   <li>{@code poseVariant} (v0.14.27): which of the {@link #POSE_VARIANTS} suit-up body poses this sequence plays,
 *       picked at random by the server so every viewer sees the same one.</li>
 * </ul>
 *
 * <p>v0.14.27: a piece put on with {@link #STYLE_PLATES} now <b>builds</b> itself on over {@link #BUILD_TICKS} (3 s):
 * the base layer closes in two halves, then the outer shell fills in one texel at a time. The Mark V case build
 * ({@link #STYLE_CASE}) keeps its quick {@link #LOCK_TICKS} fly-out-of-the-case lock-on.
 */
public record IronManSuitFx(long headStart, long chestStart, long legsStart, long feetStart, int assembleMask, int style,
		long poseStart, int poseTicks, int poseKind, long faceplateAt, int poseVariant) {
	/** v0.14.27: a piece builds itself on the body over exactly this many ticks (3 s) -- base halves, then the pixel shell. */
	public static final int BUILD_TICKS = 60;
	/** v0.14.27: how many different suit-up body poses there are (picked at random per suit-up). */
	public static final int POSE_VARIANTS = 4;
	/** The Mark V case build: a piece's bones fly out of the case and lock on over this many ticks. */
	public static final int LOCK_TICKS = 18;
	/** A piece's bones unlock and fly off over this many ticks before it leaves the body (v0.14.21 self-assembly: 10 -> 14). */
	public static final int RELEASE_TICKS = 14;
	/** A client clock up to this far behind a piece's start tick still counts as "the lock-on has just begun". */
	public static final int CLOCK_SKEW_TICKS = 20;
	/** After a release finishes, the piece stays fully gone this long (the item leaving the slot syncs meanwhile). */
	public static final int RELEASE_HOLD_TICKS = 20;
	/** The visor swing (helmet_open / helmet_close clips are 0.5 s / 0.4 s). */
	public static final int FACEPLATE_TICKS = 10;

	public static final int STYLE_PLATES = 0;
	public static final int STYLE_CASE = 1;
	/**
	 * v0.14.28: an ordinary C suit-down -- each piece <b>un-builds</b> over {@link #BUILD_TICKS} (the outer shell
	 * vanishes texel by texel, then the base layer opens in halves), helmet -> chestplate -> leggings -> boots: the exact
	 * reverse of the suit-up. (The Suit Platform's retrieve keeps the quick {@link #RELEASE_TICKS} break-away.)
	 */
	public static final int STYLE_UNBUILD = 2;
	/**
	 * v0.14.29: the Mark 5 suitcase build / fold -- each piece builds on (or comes apart) like {@link #STYLE_PLATES} /
	 * {@link #STYLE_UNBUILD}, but over its own {@link IronManMk5Suitcase} window, chest -> arms -> legs -> head ->
	 * faceplate (and the reverse). A piece that has come apart stays hidden until the fold finishes.
	 */
	public static final int STYLE_MK5 = 35;

	public static final int POSE_NONE = 0;
	/** Standing suit-up: arms out and slightly raised while the pieces lock on, then the faceplate beat. */
	public static final int POSE_SUIT_UP = 1;
	/** Suit-down: the reverse -- arms ease out as the plates break away, then drop. */
	public static final int POSE_SUIT_DOWN = 2;
	/** Mark V: the suitcase unfolds from the right hand and the suit climbs the arm. */
	public static final int POSE_CASE_UP = 3;
	/** Mark V: the suit folds back into the case, which snaps shut in the right hand. */
	public static final int POSE_CASE_DOWN = 4;
	/** Waiting for couriers / the pod: arms held out to receive the pieces. */
	public static final int POSE_RECEIVE = 5;
	/**
	 * v0.15.1 (v0.15.4: the Stark Gantry): standing on the lift while the robotic arms fit the suit -- arms out, head following the work
	 * ({@code gantry.GantryTimeline#pose}); {@code poseVariant} carries the piece count - 1.
	 */
	public static final int POSE_PLATFORM = 6;
	/** v0.15.3 (v0.15.4: the Stark Gantry): the same, played backwards, while the arms take the suit off. */
	public static final int POSE_PLATFORM_OFF = 7;
	/** v0.14.29: Mark 5 suit-up -- the case held out in both hands, onto the chest, arms out while it builds. */
	public static final int POSE_MK5_UP = 35;
	/** v0.14.29: Mark 5 suit-down -- arms out while it comes apart, then the case ends up held out in both hands. */
	public static final int POSE_MK5_DOWN = 36;

	public static final IronManSuitFx EMPTY = new IronManSuitFx(0L, 0L, 0L, 0L, 0, STYLE_PLATES, 0L, 0, POSE_NONE, 0L, 0);

	public static final StreamCodec<ByteBuf, IronManSuitFx> STREAM_CODEC = new StreamCodec<>() {
		@Override
		public IronManSuitFx decode(ByteBuf buf) {
			return new IronManSuitFx(ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_INT.decode(buf));
		}

		@Override
		public void encode(ByteBuf buf, IronManSuitFx fx) {
			ByteBufCodecs.VAR_LONG.encode(buf, fx.headStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.chestStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.legsStart());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.feetStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.assembleMask());
			ByteBufCodecs.VAR_INT.encode(buf, fx.style());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.poseStart());
			ByteBufCodecs.VAR_INT.encode(buf, fx.poseTicks());
			ByteBufCodecs.VAR_INT.encode(buf, fx.poseKind());
			ByteBufCodecs.VAR_LONG.encode(buf, fx.faceplateAt());
			ByteBufCodecs.VAR_INT.encode(buf, fx.poseVariant());
		}
	};

	// ---------------- reading (both sides) ----------------

	public static IronManSuitFx of(Player player) {
		return player.getAttachedOrElse(ModAttachments.IRON_MAN_SUIT_FX, EMPTY);
	}

	/** 0 HEAD, 1 CHEST, 2 LEGS, 3 FEET, -1 for a hand slot. Same order as {@code IronManSuitUpManager}'s mask bits. */
	public static int bit(EquipmentSlot slot) {
		return switch (slot) {
			case HEAD -> 0;
			case CHEST -> 1;
			case LEGS -> 2;
			case FEET -> 3;
			default -> -1;
		};
	}

	public long start(int bit) {
		return switch (bit) {
			case 0 -> headStart;
			case 1 -> chestStart;
			case 2 -> legsStart;
			case 3 -> feetStart;
			default -> 0L;
		};
	}

	public boolean assembling(int bit) {
		return bit >= 0 && (assembleMask & (1 << bit)) != 0;
	}

	/** Ticks (with partial) since the piece in {@code slot} started its lock-on / release, or -1 if it has no live clock. */
	public float pieceAge(EquipmentSlot slot, long gameTime, float partial) {
		int b = bit(slot);
		long s = start(b);
		if (b < 0 || s <= 0L) {
			return -1f;
		}
		float age = gameTime - s + partial;
		boolean up = assembling(b);
		int window = up ? lockTicks(b) : releaseTicks(b);
		// v0.14.21 smoothness: a client clock a little behind the server's start tick reads as "just started" (not as
		// "no clock", which drew the whole piece for a frame before it vanished and assembled), and a finished release
		// keeps the piece gone for a second, until the slot-empty sync lands, instead of popping it back for a frame
		if (age < 0f) {
			return age > -CLOCK_SKEW_TICKS ? 0f : -1f;
		}
		// v0.14.29: a Mark 5 piece that has come apart stays gone until the fold ends and it leaves the slot
		int hold = up ? 2 : style == STYLE_MK5 ? IronManMk5Suitcase.DOWN_TICKS + RELEASE_HOLD_TICKS : RELEASE_HOLD_TICKS;
		return age > window + hold ? -1f : age;
	}

	/** How much of the piece in {@code slot} is built on, 0..1 -- 1 whenever no lock-on / release is running. */
	public float pieceProgress(EquipmentSlot slot, long gameTime, float partial) {
		float age = pieceAge(slot, gameTime, partial);
		if (age < 0f) {
			return 1f;
		}
		int b = bit(slot);
		return assembling(b) ? clamp(age / lockTicks(b)) : 1f - clamp(age / releaseTicks(b));
	}

	/** v0.14.29: how long piece {@code bit} takes to go on -- the Mark 5 has a window per piece, everything else {@link #lockTicks()}. */
	public int lockTicks(int bit) {
		return style == STYLE_MK5 ? IronManMk5Suitcase.upWindow(bit) : lockTicks();
	}

	/** v0.14.29: how long piece {@code bit} takes to come off (per piece for the Mark 5). */
	public int releaseTicks(int bit) {
		return style == STYLE_MK5 ? IronManMk5Suitcase.downWindow(bit) : releaseTicks();
	}

	/** v0.14.29: is this the Mark 5 suitcase build / fold? */
	public boolean mk5() {
		return style == STYLE_MK5;
	}

	/** How long a piece takes to go on in this style: {@link #BUILD_TICKS} (3 s build) or the Mark V case's {@link #LOCK_TICKS}. */
	public int lockTicks() {
		return style == STYLE_CASE ? LOCK_TICKS : BUILD_TICKS;
	}

	/** v0.14.28: how long a piece takes to come off in this style: the reverse build ({@link #STYLE_UNBUILD}) or the quick break-away. */
	public int releaseTicks() {
		return style == STYLE_UNBUILD ? BUILD_TICKS : RELEASE_TICKS;
	}

	/** v0.14.28: is a piece coming off as the reverse build (shell texels out, then the base halves open)? */
	public boolean unbuilding() {
		return style == STYLE_UNBUILD || style == STYLE_MK5;
	}

	/** Is piece {@code bit} still building itself on at {@code gameTime}? (Server: holds the suit-up open until it is done.) */
	public boolean building(int bit, long gameTime) {
		long s = start(bit);
		if (!assembling(bit) || s <= 0L) {
			return false;
		}
		long age = gameTime - s;
		return age >= 0L && age < lockTicks(bit);
	}

	/** Any piece still building at {@code gameTime}? */
	public boolean anyBuilding(long gameTime) {
		for (int bit = 0; bit < 4; bit++) {
			if (building(bit, gameTime)) {
				return true;
			}
		}
		return false;
	}

	public static final int PHASE_NONE = 0;
	public static final int PHASE_LOCK_ON = 1;
	public static final int PHASE_RELEASE = 2;

	/** Which GeckoLib piece clip the piece in {@code slot} should be playing right now. */
	public int piecePhase(EquipmentSlot slot, long gameTime) {
		float age = pieceAge(slot, gameTime, 0f);
		if (age < 0f) {
			return PHASE_NONE;
		}
		return assembling(bit(slot)) ? PHASE_LOCK_ON : PHASE_RELEASE;
	}

	/** Ticks since the faceplate last moved, or -1 once the swing is over. */
	public float faceplateAge(long gameTime, float partial) {
		if (faceplateAt <= 0L) {
			return -1f;
		}
		float age = gameTime - faceplateAt + partial;
		return age < 0f || age > FACEPLATE_TICKS ? -1f : age;
	}

	/** Ticks into the body-pose timeline, or -1 when no pose is running. */
	public float poseAge(long gameTime, float partial) {
		if (poseKind == POSE_NONE || poseStart <= 0L || poseTicks <= 0) {
			return -1f;
		}
		float age = gameTime - poseStart + partial;
		return age < 0f || age > poseTicks ? -1f : age;
	}

	static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	// ---------------- writing (server) ----------------

	public IronManSuitFx withPiece(int bit, long start, boolean assembling) {
		long h = bit == 0 ? start : headStart;
		long c = bit == 1 ? start : chestStart;
		long l = bit == 2 ? start : legsStart;
		long f = bit == 3 ? start : feetStart;
		int mask = assembling ? assembleMask | (1 << bit) : assembleMask & ~(1 << bit);
		return new IronManSuitFx(h, c, l, f, mask, style, poseStart, poseTicks, poseKind, faceplateAt, poseVariant);
	}

	public IronManSuitFx withPose(int kind, long start, int ticks, int newStyle) {
		return withPose(kind, start, ticks, newStyle, poseVariant);
	}

	public IronManSuitFx withPose(int kind, long start, int ticks, int newStyle, int variant) {
		return new IronManSuitFx(headStart, chestStart, legsStart, feetStart, assembleMask, newStyle, start, ticks, kind,
				faceplateAt, Math.floorMod(variant, POSE_VARIANTS));
	}

	public IronManSuitFx withFaceplate(long at) {
		return new IronManSuitFx(headStart, chestStart, legsStart, feetStart, assembleMask, style, poseStart, poseTicks,
				poseKind, at, poseVariant);
	}

	/** A piece reached the body ({@code assembling}) or began breaking away from it. Synced to every viewer. */
	public static void markPiece(ServerPlayer player, EquipmentSlot slot, boolean assembling) {
		int b = bit(slot);
		if (b < 0) {
			return;
		}
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX,
				of(player).withPiece(b, player.level().getGameTime(), assembling));
	}

	public static void startPose(ServerPlayer player, int kind, int ticks, int style) {
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX,
				of(player).withPose(kind, player.level().getGameTime(), Math.max(1, ticks), style));
	}

	/** v0.14.27: as {@link #startPose(ServerPlayer, int, int, int)}, with the suit-up pose variant (0..3) every viewer plays. */
	public static void startPose(ServerPlayer player, int kind, int ticks, int style, int variant) {
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX,
				of(player).withPose(kind, player.level().getGameTime(), Math.max(1, ticks), style, variant));
	}

	public static void endPose(ServerPlayer player) {
		IronManSuitFx fx = of(player);
		if (fx.poseKind() != POSE_NONE) {
			player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, fx.withPose(POSE_NONE, 0L, 0, fx.style()));
		}
	}

	public static void faceplateMoved(ServerPlayer player) {
		player.setAttached(ModAttachments.IRON_MAN_SUIT_FX, of(player).withFaceplate(player.level().getGameTime()));
	}
}
