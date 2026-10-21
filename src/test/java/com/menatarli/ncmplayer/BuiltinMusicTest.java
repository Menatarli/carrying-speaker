package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** {@link BuiltinMusic} 的释放逻辑测试，opener 可注入。 */
class BuiltinMusicTest {
	@TempDir
	Path tempDir;

	private Path musicDir() {
		return tempDir.resolve("music");
	}

	private Path markerDir() {
		return tempDir.resolve("cfg");
	}

	private static Function<String, InputStream> opener(byte[] data) {
		return name -> new ByteArrayInputStream(data);
	}

	@Test
	void releasesOnFirstRun() throws IOException {
		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3"),
				opener("hello".getBytes(StandardCharsets.UTF_8)));

		assertEquals("hello", Files.readString(musicDir().resolve("a.mp3")));
		assertTrue(Files.exists(markerDir().resolve(BuiltinMusic.MARKER_FILE)));
	}

	@Test
	void emptyManifestDoesNothing() {
		BuiltinMusic.release(musicDir(), markerDir(), List.of(),
				opener("x".getBytes(StandardCharsets.UTF_8)));

		assertFalse(Files.exists(musicDir()), "空清单不该创建音乐目录");
		assertFalse(Files.exists(markerDir().resolve(BuiltinMusic.MARKER_FILE)), "空清单不该创建标记文件");
	}

	@Test
	void doesNotOverwriteExistingFile() throws IOException {
		Files.createDirectories(musicDir());
		Files.writeString(musicDir().resolve("a.mp3"), "original", StandardCharsets.UTF_8);

		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3"),
				opener("new".getBytes(StandardCharsets.UTF_8)));

		assertEquals("original", Files.readString(musicDir().resolve("a.mp3")));
	}

	@Test
	void markerPreventsReReleaseAfterDelete() throws IOException {
		byte[] data = "x".getBytes(StandardCharsets.UTF_8);
		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3"), opener(data));
		Files.delete(musicDir().resolve("a.mp3"));

		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3"), opener(data));

		assertFalse(Files.exists(musicDir().resolve("a.mp3")), "标记存在时不该重新释放");
	}

	@Test
	void skipsInvalidName() {
		BuiltinMusic.release(musicDir(), markerDir(), List.of("../x.mp3"),
				opener("x".getBytes(StandardCharsets.UTF_8)));

		assertFalse(Files.exists(tempDir.resolve("x.mp3")));
		assertFalse(Files.exists(musicDir().resolve("../x.mp3")));
	}

	@Test
	void openerReturningNullDoesNotBreakOthers() throws IOException {
		Function<String, InputStream> opener = name -> name.equals("a.mp3")
				? null
				: new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8));

		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3", "b.mp3"), opener);

		assertFalse(Files.exists(musicDir().resolve("a.mp3")));
		assertTrue(Files.exists(musicDir().resolve("b.mp3")));
	}

	@Test
	void openerThrowingDoesNotBreakOthers() throws IOException {
		Function<String, InputStream> opener = name -> {
			if (name.equals("a.mp3")) {
				throw new RuntimeException("boom");
			}
			return new ByteArrayInputStream("ok".getBytes(StandardCharsets.UTF_8));
		};

		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3", "b.mp3"), opener);

		assertTrue(Files.exists(musicDir().resolve("b.mp3")), "一个文件失败不影响后面的");
	}

	@Test
	void contentBytesMatch() throws IOException {
		byte[] data = {1, 2, 3, 4, 5};
		BuiltinMusic.release(musicDir(), markerDir(), List.of("a.mp3"), opener(data));

		assertArrayEquals(data, Files.readAllBytes(musicDir().resolve("a.mp3")));
	}
}
