package com.projecthero.mod.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.projecthero.mod.ProjectHeroMod;

import net.fabricmc.loader.api.FabricLoader;

/**
 * v0.14.21: the one loader every {@code config/projecthero*.json} file goes through, so a balance default changed in a
 * release actually reaches the people who already have the file. Background: the "unbeatable" Behemoth / Oathbreaker
 * reports were a config written by an old release that kept its old health forever, because every config loader
 * deserialised straight onto whatever was on disk. See {@code docs/CONFIGS.md}.
 *
 * <p>Each config declares, once, in a static {@link #builder}:
 * <ul>
 *   <li>its file name and a factory for today's defaults;</li>
 *   <li>its <b>balance-owned keys</b> ({@link Builder#balance}) -- dotted paths ({@code "stats.health"}) or whole sections
 *       ({@code "abilities"}) holding numbers the mod tunes. Everything else (toggles, lists, server preferences) is the
 *       server's;</li>
 *   <li>one <b>step</b> per version ({@link Builder#resetBalance}, {@link Builder#reset}, {@link Builder#custom},
 *       {@link Builder#introduce}). The highest step is the current version.</li>
 * </ul>
 *
 * <p>{@link #load(Path)}:
 * <ol>
 *   <li>no file: today's defaults, stamped with the current version, written out;</li>
 *   <li>a file that is not a JSON object (truncated, hand-edit typo, ...): copied aside to
 *       {@code <name>.corrupt-<time>.bak} and regenerated from defaults -- never a crash;</li>
 *   <li>otherwise every step newer than the file's {@code configVersion} runs in order (a file with no
 *       {@code configVersion} is version 0, so all of them run): the keys a step names are reset to today's defaults,
 *       every other key keeps what the file says;</li>
 *   <li>missing keys (new in this release, or deleted by hand) get their defaults; a key whose value has the wrong JSON
 *       type ({@code "health": "lots"}) gets its default; unknown keys are dropped;</li>
 *   <li>the result is stamped with the current version and written back.</li>
 * </ol>
 * A file from a newer release (a version above ours, e.g. after a downgrade) runs no steps and keeps its values.
 *
 * <p>The config class needs a {@code public Integer configVersion;} field (boxed and uninitialised so Gson leaves it
 * null for a file that predates it) and a no-arg constructor that builds today's defaults.
 */
public final class VersionedConfig<T> {
	public static final String VERSION_KEY = "configVersion";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Class<T> type;
	private final String fileName;
	private final Supplier<T> defaults;
	private final List<String> balanceKeys;
	private final List<Step> steps;
	private final int currentVersion;

	/** One version bump: the keys it resets to today's defaults, and/or a hand-written change. */
	private record Step(int version, String note, List<String> resetKeys, BiConsumer<JsonObject, JsonObject> custom) {
	}

	private VersionedConfig(Builder<T> b) {
		this.type = b.type;
		this.fileName = b.fileName;
		this.defaults = b.defaults;
		this.balanceKeys = List.copyOf(b.balance);
		List<Step> sorted = new ArrayList<>(b.steps);
		sorted.sort((a, c) -> Integer.compare(a.version, c.version));
		this.steps = List.copyOf(sorted);
		this.currentVersion = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1).version;
	}

	public static <T> Builder<T> builder(Class<T> type, String fileName, Supplier<T> defaults) {
		return new Builder<>(type, fileName, defaults);
	}

	public int currentVersion() {
		return currentVersion;
	}

	public String fileName() {
		return fileName;
	}

	/** The balance-owned keys, as declared. */
	public List<String> balanceKeys() {
		return balanceKeys;
	}

	/** Where the real file lives: {@code <game>/config/<fileName>}. */
	public Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve(fileName);
	}

	/** Loads (migrating, repairing and rewriting) the real file. */
	public T load() {
		return load(path());
	}

	/**
	 * Loads {@code path}: migrates it, fills in what is missing, repairs bad values, backs up and regenerates a corrupt
	 * file, and writes the result back. Never throws; an I/O failure falls back to today's defaults.
	 */
	public T load(Path path) {
		T result;
		try {
			if (!Files.exists(path)) {
				result = fresh();
			} else {
				String text = Files.readString(path);
				JsonObject file = parseObject(text);
				if (file == null) {
					backUp(path, "is not valid JSON");
					result = fresh();
				} else {
					try {
						result = GSON.fromJson(migrate(file), type);
					} catch (JsonParseException | IllegalStateException | NumberFormatException | ClassCastException e) {
						// a value the per-key repair could not catch (a list holding the wrong things, ...)
						backUp(path, "could not be read (" + e.getMessage() + ")");
						result = fresh();
					}
				}
			}
			Files.createDirectories(path.toAbsolutePath().getParent());
			Files.writeString(path, GSON.toJson(result));
		} catch (IOException e) {
			ProjectHeroMod.LOGGER.warn("[ProjectHero] could not load config {}, using defaults", fileName, e);
			result = fresh();
		}
		return result;
	}

	/** Runs the migration on a JSON string and returns the config (nothing is read or written). For tests. */
	public T fromJson(String json) {
		JsonObject file = parseObject(json);
		return file == null ? fresh() : GSON.fromJson(migrate(file), type);
	}

	/** Today's defaults, stamped with the current version. */
	public T fresh() {
		JsonObject d = defaultsTree();
		d.addProperty(VERSION_KEY, currentVersion);
		return GSON.fromJson(d, type);
	}

	/**
	 * The migration proper, on the JSON tree (mutated and returned): run the steps newer than the file, merge in the
	 * defaults, stamp the version.
	 */
	JsonObject migrate(JsonObject file) {
		JsonObject defaults = defaultsTree();
		int from = versionOf(file);
		if (from > currentVersion) {
			ProjectHeroMod.LOGGER.info("[ProjectHero] {} is from a newer release (v{} > v{}) -- left as it is", fileName, from,
					currentVersion);
		}
		for (Step step : steps) {
			if (step.version <= from) {
				continue;
			}
			if (!step.resetKeys.isEmpty() || step.custom != null) {
				ProjectHeroMod.LOGGER.info("[ProjectHero] {} v{} -> v{}: {}", fileName, from, step.version, step.note);
			}
			for (String key : step.resetKeys) {
				resetKey(file, defaults, key);
			}
			if (step.custom != null) {
				step.custom.accept(file, defaults);
			}
		}
		mergeDefaults(file, defaults, fileName);
		file.addProperty(VERSION_KEY, Math.max(from, currentVersion));
		return file;
	}

	private JsonObject defaultsTree() {
		JsonObject d = GSON.toJsonTree(defaults.get()).getAsJsonObject();
		d.remove(VERSION_KEY);
		return d;
	}

	private static int versionOf(JsonObject file) {
		JsonElement v = file.get(VERSION_KEY);
		if (v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) {
			return v.getAsInt();
		}
		return 0;
	}

	private static JsonObject parseObject(String text) {
		try {
			JsonElement e = JsonParser.parseString(text);
			return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (JsonParseException | IllegalStateException e) {
			return null;
		}
	}

	private void backUp(Path path, String why) throws IOException {
		Path backup = path.resolveSibling(path.getFileName() + ".corrupt-" + LocalDateTime.now().format(STAMP) + ".bak");
		Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
		ProjectHeroMod.LOGGER.warn("[ProjectHero] config {} {} -- kept a copy at {} and wrote fresh defaults", fileName, why,
				backup.getFileName());
	}

	/**
	 * Copies the default at {@code key} ({@code "a.b.c"}, a whole section {@code "a"}, or {@code "*"} for everything) over
	 * the file's value. Public so a {@link Builder#custom} step can reuse it.
	 */
	public static void resetKey(JsonObject file, JsonObject defaults, String key) {
		if ("*".equals(key)) {
			for (String k : new ArrayList<>(file.keySet())) {
				file.remove(k);
			}
			for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
				file.add(e.getKey(), e.getValue().deepCopy());
			}
			return;
		}
		String[] parts = key.split("\\.");
		JsonObject f = file;
		JsonObject d = defaults;
		for (int i = 0; i < parts.length - 1; i++) {
			JsonElement dn = d.get(parts[i]);
			if (dn == null || !dn.isJsonObject()) {
				throw new IllegalArgumentException("config key " + key + " has no section " + parts[i]);
			}
			d = dn.getAsJsonObject();
			JsonElement fn = f.get(parts[i]);
			if (fn == null || !fn.isJsonObject()) {
				fn = new JsonObject();
				f.add(parts[i], fn);
			}
			f = fn.getAsJsonObject();
		}
		String leaf = parts[parts.length - 1];
		JsonElement dv = d.get(leaf);
		if (dv == null) {
			throw new IllegalArgumentException("config key " + key + " has no default");
		}
		f.add(leaf, dv.deepCopy());
	}

	/** The default at {@code key} (dotted path), or null. For {@link Builder#custom} steps. */
	public static JsonElement at(JsonObject tree, String key) {
		JsonElement e = tree;
		for (String part : key.split("\\.")) {
			if (e == null || !e.isJsonObject()) {
				return null;
			}
			e = e.getAsJsonObject().get(part);
		}
		return e;
	}

	/**
	 * Fills in every key the defaults have and the file lacks (or holds as null), replaces values of the wrong JSON
	 * type, and drops keys the defaults do not know. Recurses into sections.
	 */
	private static void mergeDefaults(JsonObject file, JsonObject defaults, String where) {
		for (String k : new ArrayList<>(file.keySet())) {
			if (!defaults.has(k) && !VERSION_KEY.equals(k)) {
				file.remove(k);
			}
		}
		for (Map.Entry<String, JsonElement> e : defaults.entrySet()) {
			String k = e.getKey();
			JsonElement dv = e.getValue();
			JsonElement fv = file.get(k);
			if (fv == null || fv.isJsonNull()) {
				file.add(k, dv.deepCopy());
			} else if (dv.isJsonObject()) {
				if (fv.isJsonObject()) {
					mergeDefaults(fv.getAsJsonObject(), dv.getAsJsonObject(), where + "." + k);
				} else {
					warnType(where, k);
					file.add(k, dv.deepCopy());
				}
			} else if (dv.isJsonArray()) {
				if (!fv.isJsonArray()) {
					warnType(where, k);
					file.add(k, dv.deepCopy());
				}
			} else if (dv.isJsonPrimitive() && !sameKind(dv.getAsJsonPrimitive(), fv)) {
				warnType(where, k);
				file.add(k, dv.deepCopy());
			}
		}
	}

	private static boolean sameKind(JsonPrimitive d, JsonElement f) {
		if (!f.isJsonPrimitive()) {
			return false;
		}
		JsonPrimitive p = f.getAsJsonPrimitive();
		if (d.isNumber()) {
			double value;
			if (p.isNumber()) {
				value = p.getAsDouble();
			} else if (p.isString()) { // Gson reads "12" as 12 -- accept it if it really is a number
				try {
					value = Double.parseDouble(p.getAsString());
				} catch (NumberFormatException e) {
					return false;
				}
			} else {
				return false;
			}
			Number n = d.getAsNumber();
			boolean integral = n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
			// an int field cannot take 12.5 (Gson would refuse the whole file)
			return !integral || (value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE);
		}
		if (d.isBoolean()) {
			return p.isBoolean();
		}
		return p.isString();
	}

	private static void warnType(String where, String key) {
		ProjectHeroMod.LOGGER.warn("[ProjectHero] config {}: '{}' had the wrong kind of value -- reset to the default", where, key);
	}

	public static final class Builder<T> {
		private final Class<T> type;
		private final String fileName;
		private final Supplier<T> defaults;
		private final Set<String> balance = new LinkedHashSet<>();
		private final List<Step> steps = new ArrayList<>();

		private Builder(Class<T> type, String fileName, Supplier<T> defaults) {
			this.type = type;
			this.fileName = fileName;
			this.defaults = defaults;
		}

		/** Declares balance-owned keys (dotted paths or whole sections): the ones {@link #resetBalance} resets. */
		public Builder<T> balance(String... keys) {
			balance.addAll(Arrays.asList(keys));
			return this;
		}

		/** A version that only stamps the file (the version the file format started at, or a no-balance change). */
		public Builder<T> introduce(int version, String note) {
			return add(new Step(version, note, Collections.emptyList(), null));
		}

		/** A version bump that resets every balance-owned key. The default for a balance pass. */
		public Builder<T> resetBalance(int version, String note) {
			return add(new Step(version, note, null, null));
		}

		/** A version bump that resets exactly {@code keys} (the ones this balance pass changed); the rest is kept. */
		public Builder<T> reset(int version, String note, String... keys) {
			return add(new Step(version, note, List.of(keys), null));
		}

		/**
		 * A version bump with a hand-written change on the JSON tree ({@code (file, defaults) -> ...}), e.g. "move the
		 * old default up, but keep a value the server chose". Runs after {@code keys} are reset.
		 */
		public Builder<T> custom(int version, String note, BiConsumer<JsonObject, JsonObject> change, String... keys) {
			return add(new Step(version, note, List.of(keys), change));
		}

		private Builder<T> add(Step step) {
			for (Step s : steps) {
				if (s.version == step.version) {
					throw new IllegalArgumentException(fileName + ": two steps for version " + step.version);
				}
			}
			steps.add(step);
			return this;
		}

		public VersionedConfig<T> build() {
			// resetBalance steps take the balance keys declared (in any order) on this builder
			List<Step> resolved = new ArrayList<>();
			for (Step s : steps) {
				resolved.add(s.resetKeys == null ? new Step(s.version, s.note, List.copyOf(balance), s.custom) : s);
			}
			steps.clear();
			steps.addAll(resolved);
			VersionedConfig<T> config = new VersionedConfig<>(this);
			// fail fast at start-up (and in every gametest run) on a key that names nothing
			JsonObject probe = config.defaultsTree();
			JsonObject scratch = probe.deepCopy();
			for (String key : balance) {
				resetKey(scratch, probe, key);
			}
			for (Step s : config.steps) {
				for (String key : s.resetKeys) {
					resetKey(scratch, probe, key);
				}
			}
			return config;
		}
	}
}
