package com.menatarli.ncmplayer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内置音乐的首次释放。纯逻辑，不引用任何 Minecraft 类。
 *
 * <p>classpath 没法直接列目录，所以靠 {@code builtin_music/index.txt} 这份清单。
 * 释放记录写在 {@code .builtin_released} 里：只要记过某个文件名，就不再重复释放——这样用户删掉的内置歌
 * 不会被重新塞回去。
 *
 * <p>所有 IO 失败都 catch 并打 warn，不影响其他文件。
 */
public final class BuiltinMusic {
	/** 清单资源路径。 */
	public static final String INDEX_RESOURCE = "/assets/carryingspeaker/builtin_music/index.txt";
	/** 资源前缀。 */
	public static final String RESOURCE_PREFIX = "/assets/carryingspeaker/builtin_music/";
	/** 释放记录文件名。 */
	public static final String MARKER_FILE = ".builtin_released";
	/** 释放线程名。 */
	public static final String THREAD_NAME = "carryingspeaker-builtin";

	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");

	private BuiltinMusic() {
	}

	/** 在后台 daemon 线程里释放内置音乐，不阻塞游戏启动。 */
	public static void releaseAsync(Path musicDir, Path markerDir) {
		Thread thread = new Thread(() -> {
			try {
				release(musicDir, markerDir, BuiltinMusic::openResource);
			} catch (Throwable t) {
				LOGGER.warn("释放内置音乐失败", t);
			}
		}, THREAD_NAME);
		thread.setDaemon(true);
		thread.start();
	}

	/** 同步释放，清单从 classpath 读。 */
	static void release(Path musicDir, Path markerDir, Function<String, InputStream> opener) {
		release(musicDir, markerDir, readIndex(), opener);
	}

	/** 同步释放，清单可注入（测试用）。 */
	static void release(Path musicDir, Path markerDir, List<String> names,
			Function<String, InputStream> opener) {
		if (musicDir == null || names == null || names.isEmpty() || opener == null) {
			return;
		}
		try {
			Files.createDirectories(musicDir);
		} catch (IOException e) {
			LOGGER.warn("创建音乐目录 {} 失败", musicDir, e);
			return;
		}
		if (markerDir != null) {
			try {
				Files.createDirectories(markerDir);
			} catch (IOException e) {
				LOGGER.warn("创建标记目录 {} 失败", markerDir, e);
			}
		}
		final Path marker = markerDir == null ? null : markerDir.resolve(MARKER_FILE);
		final List<String> released = readMarker(marker);

		for (String name : names) {
			if (name == null || name.isBlank()) {
				continue;
			}
			if (!SoundEventConfig.isValidSoundFile(name)) {
				LOGGER.warn("内置音乐清单里有不合法的文件名「{}」，已跳过", name);
				continue;
			}
			if (released.contains(name)) {
				continue;
			}
			Path target = musicDir.resolve(name);
			if (Files.exists(target)) {
				// 文件已经在（用户自己放的或之前放过的），不覆盖，只补一条释放记录。
				recordRelease(marker, released, name);
				continue;
			}
			InputStream input = null;
			try {
				input = opener.apply(name);
				if (input == null) {
					LOGGER.warn("内置音乐资源缺失：{}", name);
					continue;
				}
				Path temp = musicDir.resolve(name + ".tmp");
				Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING);
				try {
					Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
				} catch (Exception moveError) {
					Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
				}
				recordRelease(marker, released, name);
				LOGGER.info("已释放内置音乐：{}", name);
			} catch (Exception e) {
				LOGGER.warn("释放内置音乐 {} 失败", name, e);
			} finally {
				closeQuietly(input);
			}
		}
	}

	private static List<String> readIndex() {
		List<String> names = new ArrayList<>();
		try (InputStream input = BuiltinMusic.class.getResourceAsStream(INDEX_RESOURCE)) {
			if (input == null) {
				LOGGER.warn("内置音乐清单 {} 不存在", INDEX_RESOURCE);
				return names;
			}
			String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
			for (String line : text.split("\\R")) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					names.add(trimmed);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("读取内置音乐清单失败", e);
		}
		return names;
	}

	private static InputStream openResource(String name) {
		return BuiltinMusic.class.getResourceAsStream(RESOURCE_PREFIX + name);
	}

	private static List<String> readMarker(Path marker) {
		List<String> names = new ArrayList<>();
		if (marker == null || Files.notExists(marker)) {
			return names;
		}
		try {
			for (String line : Files.readAllLines(marker, StandardCharsets.UTF_8)) {
				String trimmed = line.trim();
				if (!trimmed.isEmpty()) {
					names.add(trimmed);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("读取内置音乐释放记录失败", e);
		}
		return names;
	}

	private static void recordRelease(Path marker, List<String> released, String name) {
		if (marker == null || released.contains(name)) {
			return;
		}
		try {
			Files.writeString(marker, name + System.lineSeparator(), StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			released.add(name);
		} catch (Exception e) {
			LOGGER.warn("记录内置音乐释放标记失败", e);
		}
	}

	private static void closeQuietly(InputStream input) {
		if (input == null) {
			return;
		}
		try {
			input.close();
		} catch (Exception ignored) {
			// 关不掉不影响已经写完的文件
		}
	}
}
