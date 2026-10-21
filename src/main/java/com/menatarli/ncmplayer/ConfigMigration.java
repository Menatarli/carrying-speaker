package com.menatarli.ncmplayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把旧版配置从 {@code config/} 根目录迁移到 {@code config/carrying-speaker/}。
 *
 * <p>只在首次启动时做一次：迁完写 {@code .migrated} 标记，之后整体跳过。
 * <b>只复制，绝不删除或改写旧文件</b>。任何 IO 异常都 catch 并打 warn，不往外抛。
 *
 * <p>旧路径常量只允许出现在这个类里。
 */
public final class ConfigMigration {
	/** 旧配置文件（在 config 根目录）。 */
	public static final String OLD_CONFIG_FILE = "ncmplayer.json";
	/** 旧音乐目录名。 */
	public static final String OLD_MUSIC_DIR = "ncmplayer-music";

	/** 新目录名。 */
	public static final String NEW_DIR_NAME = "carrying-speaker";
	/** 新配置文件名。 */
	public static final String NEW_CONFIG_FILE = "config.json";
	/** 新音乐目录名。 */
	public static final String NEW_MUSIC_DIR = "music";
	/** 迁移完成标记。 */
	public static final String MARKER_FILE = ".migrated";

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private ConfigMigration() {
	}

	/**
	 * 执行迁移。已经迁过（标记存在）就什么都不做。
	 *
	 * @param configRoot FabricLoader 的 config 目录
	 */
	public static void migrate(Path configRoot) {
		if (configRoot == null) {
			return;
		}
		try {
			Path newDir = configRoot.resolve(NEW_DIR_NAME);
			Files.createDirectories(newDir);
			Path marker = newDir.resolve(MARKER_FILE);
			if (Files.exists(marker)) {
				return;
			}

			Path oldConfig = configRoot.resolve(OLD_CONFIG_FILE);
			Path newConfig = newDir.resolve(NEW_CONFIG_FILE);
			if (Files.notExists(newConfig) && Files.exists(oldConfig)) {
				migrateConfig(oldConfig, newConfig);
			}

			Path oldMusic = configRoot.resolve(OLD_MUSIC_DIR);
			Path newMusic = newDir.resolve(NEW_MUSIC_DIR);
			if (Files.notExists(newMusic) && Files.isDirectory(oldMusic)) {
				migrateMusic(oldMusic, newMusic);
			}

			Files.writeString(marker, Instant.now().toString(), StandardCharsets.UTF_8);
			LOGGER.info("配置迁移完成：{}", newDir.toAbsolutePath());
		} catch (Exception e) {
			LOGGER.warn("配置迁移失败，本次跳过", e);
		}
	}

	/** 复制配置文件；musicDir 还是旧默认值时改成新默认值。JSON 坏了就原样复制。 */
	private static void migrateConfig(Path oldFile, Path newFile) {
		try {
			String text = Files.readString(oldFile, StandardCharsets.UTF_8);
			String output = text;
			try {
				JsonObject root = JsonParser.parseString(text).getAsJsonObject();
				if (root.has("musicDir") && OLD_MUSIC_DIR.equals(root.get("musicDir").getAsString())) {
					root.addProperty("musicDir", NEW_MUSIC_DIR);
					output = GSON.toJson(root) + System.lineSeparator();
				}
			} catch (Exception parseError) {
				LOGGER.warn("旧配置 {} 的 JSON 无法解析，原样复制", oldFile, parseError);
				output = text;
			}
			Files.writeString(newFile, output, StandardCharsets.UTF_8);
		} catch (Exception e) {
			LOGGER.warn("迁移配置文件 {} 失败", oldFile, e);
		}
	}

	/** 只复制第一层的 mp3 / wav，不递归、不覆盖。 */
	private static void migrateMusic(Path oldDir, Path newDir) {
		try {
			Files.createDirectories(newDir);
			try (Stream<Path> stream = Files.list(oldDir)) {
				List<Path> entries = stream.toList();
				for (Path entry : entries) {
					if (!Files.isRegularFile(entry)) {
						continue;
					}
					String name = entry.getFileName().toString().toLowerCase(Locale.ROOT);
					if (!name.endsWith(".mp3") && !name.endsWith(".wav")) {
						continue;
					}
					Path target = newDir.resolve(entry.getFileName());
					if (Files.exists(target)) {
						continue;
					}
					Files.copy(entry, target, StandardCopyOption.COPY_ATTRIBUTES);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("迁移音乐目录 {} 失败", oldDir, e);
		}
	}
}
