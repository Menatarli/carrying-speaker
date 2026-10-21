package com.menatarli.ncmplayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code <游戏目录>/config/carrying-speaker/config.json} 的读写。
 *
 * <p>文件不存在就写一份默认值；解析失败、取值越界都用默认值兜底并打 warn，绝不抛给游戏。
 */
public final class NcmConfig {
	/** 配置文件名（相对 config 目录）。 */
	public static final String FILE_NAME = "config.json";

	public static final boolean DEFAULT_LOW_HEALTH_ENABLED = true;
	public static final float DEFAULT_ENTER_RATIO = 0.3F;
	public static final float DEFAULT_EXIT_RATIO = 0.5F;
	public static final String DEFAULT_MUSIC_DIR = "music";
	/** 默认在后台播放（拉起网易云后最小化它、把焦点还给 MC）。 */
	public static final boolean DEFAULT_NCM_BACKGROUND = true;
	/** 低血量触发的本地音乐默认最短播放时长（秒）。 */
	public static final int DEFAULT_MIN_PLAY_SECONDS = 10;
	/** minPlaySeconds 的合法上限（秒）。 */
	public static final int MAX_MIN_PLAY_SECONDS = 600;
	/** 冷启动网易云时，等它起来后再补发一次播放指令的默认延迟（秒）。 */
	public static final int DEFAULT_COLD_START_DELAY_SECONDS = 10;
	/** coldStartDelaySeconds 的合法上限（秒）。 */
	public static final int MAX_COLD_START_DELAY_SECONDS = 60;

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private boolean lowHealthEnabled = DEFAULT_LOW_HEALTH_ENABLED;
	private float enterRatio = DEFAULT_ENTER_RATIO;
	private float exitRatio = DEFAULT_EXIT_RATIO;
	private String musicDir = DEFAULT_MUSIC_DIR;
	private boolean ncmBackground = DEFAULT_NCM_BACKGROUND;
	private int minPlaySeconds = DEFAULT_MIN_PLAY_SECONDS;
	private int coldStartDelaySeconds = DEFAULT_COLD_START_DELAY_SECONDS;

	/**
	 * 读取配置。目录不存在就建；文件不存在就写默认值；解析失败或取值不合法就用默认值并 warn。
	 * 顺便确保音乐目录存在。
	 *
	 * @param configDir Fabric 的 config 目录
	 */
	public static NcmConfig load(Path configDir) {
		NcmConfig config = new NcmConfig();
		Path file = configDir.resolve(FILE_NAME);
		try {
			Files.createDirectories(configDir);
			if (Files.notExists(file)) {
				Files.writeString(file, config.toJson(), StandardCharsets.UTF_8);
				LOGGER.info("配置不存在，已写入默认值：{}", file.toAbsolutePath());
			} else {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				NcmConfig parsed = GSON.fromJson(text, NcmConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			}
		} catch (Exception e) {
			LOGGER.warn("读取配置 {} 失败，改用默认值", file.toAbsolutePath(), e);
			config = new NcmConfig();
		}
		config.validate(file);
		config.ensureMusicDirectory(configDir);
		return config;
	}

	/** 当前配置的 JSON 文本（字段顺序固定，带缩进）。 */
	public String toJson() {
		JsonObject root = new JsonObject();
		root.addProperty("lowHealthEnabled", lowHealthEnabled);
		root.addProperty("enterRatio", enterRatio);
		root.addProperty("exitRatio", exitRatio);
		root.addProperty("musicDir", musicDir);
		root.addProperty("ncmBackground", ncmBackground);
		root.addProperty("minPlaySeconds", minPlaySeconds);
		root.addProperty("coldStartDelaySeconds", coldStartDelaySeconds);
		return GSON.toJson(root) + System.lineSeparator();
	}

	/** 音乐目录的绝对路径（相对 config 目录；纯字符串拼接，不做 IO）。 */
	public Path resolveMusicDirectory(Path configDir) {
		return configDir.resolve(musicDir);
	}

	/** 确保音乐目录存在，失败只 warn。 */
	public void ensureMusicDirectory(Path configDir) {
		Path dir = resolveMusicDirectory(configDir);
		try {
			Files.createDirectories(dir);
		} catch (IOException e) {
			LOGGER.warn("创建音乐目录 {} 失败", dir.toAbsolutePath(), e);
		}
	}

	private void validate(Path file) {
		boolean ratioOk = isUsableRatio(enterRatio)
				&& isUsableRatio(exitRatio)
				&& enterRatio < exitRatio
				&& exitRatio <= 1.0F;
		if (!ratioOk) {
			LOGGER.warn("配置 {} 里的 enterRatio={} / exitRatio={} 不合法（要求 0 < enter < exit <= 1），回退默认值 {} / {}",
					file.toAbsolutePath(), enterRatio, exitRatio, DEFAULT_ENTER_RATIO, DEFAULT_EXIT_RATIO);
			enterRatio = DEFAULT_ENTER_RATIO;
			exitRatio = DEFAULT_EXIT_RATIO;
		}
		if (musicDir == null || musicDir.isBlank()) {
			LOGGER.warn("配置 {} 里的 musicDir 为空，回退默认值 {}", file.toAbsolutePath(), DEFAULT_MUSIC_DIR);
			musicDir = DEFAULT_MUSIC_DIR;
		}
		if (minPlaySeconds < 0 || minPlaySeconds > MAX_MIN_PLAY_SECONDS) {
			LOGGER.warn("配置 {} 里的 minPlaySeconds={} 不合法（要求 0-{}），回退默认值 {}",
					file.toAbsolutePath(), minPlaySeconds, MAX_MIN_PLAY_SECONDS, DEFAULT_MIN_PLAY_SECONDS);
			minPlaySeconds = DEFAULT_MIN_PLAY_SECONDS;
		}
		if (coldStartDelaySeconds < 0 || coldStartDelaySeconds > MAX_COLD_START_DELAY_SECONDS) {
			LOGGER.warn("配置 {} 里的 coldStartDelaySeconds={} 不合法（要求 0-{}），回退默认值 {}",
					file.toAbsolutePath(), coldStartDelaySeconds, MAX_COLD_START_DELAY_SECONDS,
					DEFAULT_COLD_START_DELAY_SECONDS);
			coldStartDelaySeconds = DEFAULT_COLD_START_DELAY_SECONDS;
		}
	}

	private static boolean isUsableRatio(float ratio) {
		return !Float.isNaN(ratio) && ratio > 0.0F;
	}

	public boolean isLowHealthEnabled() {
		return lowHealthEnabled;
	}

	public float getEnterRatio() {
		return enterRatio;
	}

	public float getExitRatio() {
		return exitRatio;
	}

	public String getMusicDir() {
		return musicDir;
	}

	/** 拉起网易云后是否最小化它并把焦点还给 MC。旧配置里没有这个字段时按默认值 true 处理。 */
	public boolean isNcmBackground() {
		return ncmBackground;
	}

	/** 低血量触发时的最短播放时长（秒）。0 表示不限制；旧配置里没有这个字段时按默认值 10 处理。 */
	public int getMinPlaySeconds() {
		return minPlaySeconds;
	}

	/** 冷启动网易云时，等它起来后再补发一次播放指令的延迟（秒）。旧配置里没有这个字段时按默认值 10 处理。 */
	public int getColdStartDelaySeconds() {
		return coldStartDelaySeconds;
	}
}
