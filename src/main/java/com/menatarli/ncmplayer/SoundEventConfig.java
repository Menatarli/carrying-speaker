package com.menatarli.ncmplayer;

import java.util.Locale;
import java.util.Optional;

import com.google.gson.JsonObject;

/**
 * 一条音效事件的配置。纯逻辑，不引用任何 Minecraft 类。
 *
 * <p>字段范围由 {@link #parse(EventType, JsonObject)} 校验，任何一项不合法都返回空，
 * 由 {@link SoundEventTable} 决定回退到默认值。
 */
public final class SoundEventConfig {
	/** 目前支持的事件类型。 */
	public enum EventType {
		/** 低血量。 */
		LOW_HEALTH,
		/** 死亡。 */
		DEATH
	}

	public static final boolean DEFAULT_ENABLED = true;
	public static final double DEFAULT_START_OFFSET_SECONDS = 0.0;
	public static final double DEFAULT_MIN_HEALTH = 0.3;
	public static final double DEFAULT_EXIT_HEALTH = 0.5;
	public static final int DEFAULT_MIN_PLAY_SECONDS = 10;
	public static final boolean DEFAULT_LOOP = true;

	/** 死亡事件默认曲目：空串表示从音乐目录随机选。 */
	public static final String DEFAULT_DEATH_SOUND = "";
	/** 死亡事件默认最短播放时长。 */
	public static final int DEFAULT_DEATH_MIN_PLAY_SECONDS = 120;

	private static final double MAX_START_OFFSET_SECONDS = 3600.0;
	private static final int MAX_MIN_PLAY_SECONDS = 600;

	private final EventType eventType;
	private final boolean enabled;
	private final String soundFile;
	private final double startOffsetSeconds;
	private final double minHealth;
	private final double exitHealth;
	private final int minPlaySeconds;
	private final boolean loop;

	private SoundEventConfig(EventType eventType, boolean enabled, String soundFile, double startOffsetSeconds,
			double minHealth, double exitHealth, int minPlaySeconds, boolean loop) {
		this.eventType = eventType;
		this.enabled = enabled;
		this.soundFile = soundFile;
		this.startOffsetSeconds = startOffsetSeconds;
		this.minHealth = minHealth;
		this.exitHealth = exitHealth;
		this.minPlaySeconds = minPlaySeconds;
		this.loop = loop;
	}

	/** 某个类型的默认配置。 */
	public static SoundEventConfig defaults(EventType type) {
		if (type == EventType.DEATH) {
			return new SoundEventConfig(type, DEFAULT_ENABLED, DEFAULT_DEATH_SOUND,
					DEFAULT_START_OFFSET_SECONDS, DEFAULT_MIN_HEALTH, DEFAULT_EXIT_HEALTH,
					DEFAULT_DEATH_MIN_PLAY_SECONDS, false);
		}
		return new SoundEventConfig(type, DEFAULT_ENABLED, "",
				DEFAULT_START_OFFSET_SECONDS, DEFAULT_MIN_HEALTH, DEFAULT_EXIT_HEALTH,
				DEFAULT_MIN_PLAY_SECONDS, DEFAULT_LOOP);
	}

	/**
	 * 从 JSON 解析一条事件。缺字段用默认值；任何字段越界或不合法都返回空。
	 *
	 * @param type 已经确定的类型（未知类型在外面就被跳过）
	 * @param json 该事件的 JSON 对象
	 */
	public static Optional<SoundEventConfig> parse(EventType type, JsonObject json) {
		try {
			boolean enabled = json.has("enabled") ? json.get("enabled").getAsBoolean() : DEFAULT_ENABLED;

			String soundFile = json.has("soundFile") ? json.get("soundFile").getAsString() : "";
			if (!isValidSoundFile(soundFile)) {
				return Optional.empty();
			}

			double startOffset = json.has("startOffsetSeconds")
					? json.get("startOffsetSeconds").getAsDouble()
					: DEFAULT_START_OFFSET_SECONDS;
			if (startOffset < 0.0 || startOffset > MAX_START_OFFSET_SECONDS) {
				return Optional.empty();
			}

			double minHealth = json.has("minHealth") ? json.get("minHealth").getAsDouble() : DEFAULT_MIN_HEALTH;
			double exitHealth = json.has("exitHealth") ? json.get("exitHealth").getAsDouble() : DEFAULT_EXIT_HEALTH;
			if (type == EventType.LOW_HEALTH) {
				if (!(minHealth > 0.0 && minHealth < 1.0)) {
					return Optional.empty();
				}
				if (!(exitHealth > minHealth && exitHealth <= 1.0)) {
					return Optional.empty();
				}
			}

			int minPlay = json.has("minPlaySeconds")
					? json.get("minPlaySeconds").getAsInt()
					: DEFAULT_MIN_PLAY_SECONDS;
			if (minPlay < 0 || minPlay > MAX_MIN_PLAY_SECONDS) {
				return Optional.empty();
			}

			boolean loop = json.has("loop") ? json.get("loop").getAsBoolean() : DEFAULT_LOOP;
			return Optional.of(new SoundEventConfig(type, enabled, soundFile, startOffset,
					minHealth, exitHealth, minPlay, loop));
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	/**
	 * 校验音乐文件名：空串合法（表示从音乐目录随机选）；非空时只能是纯文件名，
	 * 带路径分隔符、{@code ..} 或盘符的一律不合法，防止路径穿越。
	 */
	public static boolean isValidSoundFile(String name) {
		if (name == null) {
			return false;
		}
		if (name.isEmpty()) {
			return true;
		}
		if (name.contains("/") || name.contains("\\") || name.contains("..")) {
			return false;
		}
		if (name.matches("^[A-Za-z]:.*")) {
			return false;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		return lower.endsWith(".mp3") || lower.endsWith(".wav");
	}

	/** 转成 JSON。LOW_HEALTH 才写两个血量阈值。 */
	public JsonObject toJson() {
		JsonObject json = new JsonObject();
		json.addProperty("eventType", eventType.name());
		json.addProperty("enabled", enabled);
		json.addProperty("soundFile", soundFile);
		json.addProperty("startOffsetSeconds", startOffsetSeconds);
		if (eventType == EventType.LOW_HEALTH) {
			json.addProperty("minHealth", minHealth);
			json.addProperty("exitHealth", exitHealth);
		}
		json.addProperty("minPlaySeconds", minPlaySeconds);
		json.addProperty("loop", loop);
		return json;
	}

	public EventType eventType() {
		return eventType;
	}

	public boolean enabled() {
		return enabled;
	}

	public String soundFile() {
		return soundFile;
	}

	public double startOffsetSeconds() {
		return startOffsetSeconds;
	}

	public double minHealth() {
		return minHealth;
	}

	public double exitHealth() {
		return exitHealth;
	}

	public int minPlaySeconds() {
		return minPlaySeconds;
	}

	public boolean loop() {
		return loop;
	}

	/** 类型名小写，用于日志。 */
	public String typeName() {
		return eventType.name().toLowerCase(Locale.ROOT);
	}
}
