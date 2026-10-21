package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** {@link ConfigMigration} 的迁移逻辑测试。 */
class ConfigMigrationTest {
	@TempDir
	Path configRoot;

	private Path oldConfig() {
		return configRoot.resolve(ConfigMigration.OLD_CONFIG_FILE);
	}

	private Path newDir() {
		return configRoot.resolve(ConfigMigration.NEW_DIR_NAME);
	}

	private Path newConfig() {
		return newDir().resolve(ConfigMigration.NEW_CONFIG_FILE);
	}

	@Test
	void migratesAndRewritesDefaultMusicDir() throws IOException {
		Files.writeString(oldConfig(),
				"{\"lowHealthEnabled\":true,\"musicDir\":\"" + ConfigMigration.OLD_MUSIC_DIR + "\"}",
				StandardCharsets.UTF_8);

		ConfigMigration.migrate(configRoot);

		assertTrue(Files.exists(newConfig()));
		JsonObject root = JsonParser.parseString(Files.readString(newConfig(), StandardCharsets.UTF_8))
				.getAsJsonObject();
		assertEquals(ConfigMigration.NEW_MUSIC_DIR, root.get("musicDir").getAsString());
		assertTrue(Files.exists(newDir().resolve(ConfigMigration.MARKER_FILE)));
	}

	@Test
	void keepsCustomMusicDir() throws IOException {
		Files.writeString(oldConfig(), "{\"musicDir\":\"my-songs\"}", StandardCharsets.UTF_8);
		ConfigMigration.migrate(configRoot);
		JsonObject root = JsonParser.parseString(Files.readString(newConfig(), StandardCharsets.UTF_8))
				.getAsJsonObject();
		assertEquals("my-songs", root.get("musicDir").getAsString());
	}

	@Test
	void badJsonIsCopiedVerbatim() throws IOException {
		String broken = "{ not valid";
		Files.writeString(oldConfig(), broken, StandardCharsets.UTF_8);
		ConfigMigration.migrate(configRoot);
		assertEquals(broken, Files.readString(newConfig(), StandardCharsets.UTF_8));
	}

	@Test
	void copiesOnlyMp3AndWavFromTopLevel() throws IOException {
		Path oldMusic = configRoot.resolve(ConfigMigration.OLD_MUSIC_DIR);
		Files.createDirectories(oldMusic.resolve("sub"));
		Files.writeString(oldMusic.resolve("a.mp3"), "x");
		Files.writeString(oldMusic.resolve("b.wav"), "x");
		Files.writeString(oldMusic.resolve("c.txt"), "x");
		Files.writeString(oldMusic.resolve("sub/d.mp3"), "x");

		ConfigMigration.migrate(configRoot);

		Path newMusic = newDir().resolve(ConfigMigration.NEW_MUSIC_DIR);
		assertTrue(Files.exists(newMusic.resolve("a.mp3")));
		assertTrue(Files.exists(newMusic.resolve("b.wav")));
		assertFalse(Files.exists(newMusic.resolve("c.txt")), "txt 不该被复制");
		assertFalse(Files.exists(newMusic.resolve("sub")), "不递归");
	}

	@Test
	void doesNotOverwriteExistingNewConfig() throws IOException {
		Files.createDirectories(newDir());
		Files.writeString(newConfig(), "{\"musicDir\":\"keep\"}", StandardCharsets.UTF_8);
		Files.writeString(oldConfig(), "{\"musicDir\":\"other\"}", StandardCharsets.UTF_8);

		ConfigMigration.migrate(configRoot);

		assertEquals("{\"musicDir\":\"keep\"}", Files.readString(newConfig(), StandardCharsets.UTF_8));
	}

	@Test
	void markerSkipsEverything() throws IOException {
		Files.createDirectories(newDir());
		Files.writeString(newDir().resolve(ConfigMigration.MARKER_FILE), "done", StandardCharsets.UTF_8);
		Files.writeString(oldConfig(), "{\"musicDir\":\"x\"}", StandardCharsets.UTF_8);

		ConfigMigration.migrate(configRoot);

		assertFalse(Files.exists(newConfig()), "有标记时不应迁移配置");
	}

	@Test
	void oldFilesAreUntouched() throws IOException {
		byte[] oldBytes = "{\"musicDir\":\"ncmplayer-music\"}".getBytes(StandardCharsets.UTF_8);
		Files.write(oldConfig(), oldBytes);

		ConfigMigration.migrate(configRoot);

		assertArrayEquals(oldBytes, Files.readAllBytes(oldConfig()), "旧文件必须字节不变");
	}

	@Test
	void nothingToMigrateCreatesDirOnly() {
		ConfigMigration.migrate(configRoot);
		assertTrue(Files.isDirectory(newDir()));
		assertFalse(Files.exists(newConfig()));
	}
}
