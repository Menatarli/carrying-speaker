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

/** {@link SoundEventTable} 的读写与容错测试。 */
class SoundEventTableTest {
	@TempDir
	Path tempDir;

	private Path eventsFile() {
		return tempDir.resolve(SoundEventTable.FILE_NAME);
	}

	@Test
	void createsTemplateWhenMissing() throws IOException {
		Path file = eventsFile();
		SoundEventTable table = SoundEventTable.load(file);

		assertTrue(Files.exists(file), "文件不存在时应当生成模板");
		String text = Files.readString(file, StandardCharsets.UTF_8);
		assertFalse(text.contains("STYX"), "内置曲已移除，模板里不该再出现 STYX");

		SoundEventTable reloaded = SoundEventTable.parse(text);
		SoundEventConfig low = reloaded.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow();
		assertEquals(10, low.minPlaySeconds());
		assertEquals(0.3, low.minHealth(), 1e-9);
		assertEquals(0.5, low.exitHealth(), 1e-9);
		assertTrue(low.loop());
		assertTrue(low.soundFile().isEmpty());

		SoundEventConfig death = reloaded.get(SoundEventConfig.EventType.DEATH).orElseThrow();
		assertEquals(120, death.minPlaySeconds());
		assertFalse(death.loop());
		assertEquals("", death.soundFile(), "DEATH 默认改成从音乐目录随机选");

		assertEquals(10, table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow().minPlaySeconds());
	}

	@Test
	void badJsonDoesNotOverwriteUserFile() throws IOException {
		Path file = eventsFile();
		Files.writeString(file, "{ this is not json", StandardCharsets.UTF_8);
		byte[] before = Files.readAllBytes(file);

		SoundEventTable table = SoundEventTable.load(file);

		byte[] after = Files.readAllBytes(file);
		assertArrayEquals(before, after, "解析失败时绝不能覆盖用户文件");
		assertEquals(10, table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow().minPlaySeconds());
	}

	@Test
	void invalidFieldFallsBackOnlyThatEvent() {
		String json = "{\"version\":1,\"events\":["
				+ "{\"eventType\":\"LOW_HEALTH\",\"startOffsetSeconds\":-1},"
				+ "{\"eventType\":\"DEATH\",\"minPlaySeconds\":150}"
				+ "]}";
		SoundEventTable table = SoundEventTable.parse(json);

		SoundEventConfig low = table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow();
		assertEquals(SoundEventConfig.DEFAULT_MIN_PLAY_SECONDS, low.minPlaySeconds(), "非法事件回退默认");
		assertEquals(0.0, low.startOffsetSeconds(), 1e-9);

		SoundEventConfig death = table.get(SoundEventConfig.EventType.DEATH).orElseThrow();
		assertEquals(150, death.minPlaySeconds(), "合法事件不受影响");
	}

	@Test
	void invalidHealthRatioFallsBack() {
		String json = "{\"events\":[{\"eventType\":\"LOW_HEALTH\",\"minHealth\":0.8,\"exitHealth\":0.5}]}";
		SoundEventTable table = SoundEventTable.parse(json);
		SoundEventConfig low = table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow();
		assertEquals(SoundEventConfig.DEFAULT_MIN_HEALTH, low.minHealth(), 1e-9);
		assertEquals(SoundEventConfig.DEFAULT_EXIT_HEALTH, low.exitHealth(), 1e-9);
	}

	@Test
	void pathTraversalSoundFileIsRejected() {
		String withSlash = "{\"events\":[{\"eventType\":\"LOW_HEALTH\",\"soundFile\":\"../x.mp3\"}]}";
		assertEquals(SoundEventConfig.DEFAULT_MIN_PLAY_SECONDS,
				SoundEventTable.parse(withSlash).get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow()
						.minPlaySeconds());

		String absolute = "{\"events\":[{\"eventType\":\"LOW_HEALTH\",\"soundFile\":\"C:\\\\a.mp3\"}]}";
		assertEquals("",
				SoundEventTable.parse(absolute).get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow()
						.soundFile(), "绝对路径应当被判非法并回退到空串");
	}

	@Test
	void duplicateTypeKeepsFirst() {
		String json = "{\"events\":["
				+ "{\"eventType\":\"LOW_HEALTH\",\"minPlaySeconds\":11},"
				+ "{\"eventType\":\"LOW_HEALTH\",\"minPlaySeconds\":99}"
				+ "]}";
		SoundEventTable table = SoundEventTable.parse(json);
		assertEquals(11, table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow().minPlaySeconds());
	}

	@Test
	void unknownTypeIsSkipped() {
		String json = "{\"events\":["
				+ "{\"eventType\":\"EXPLOSION\",\"minPlaySeconds\":5},"
				+ "{\"eventType\":\"DEATH\",\"minPlaySeconds\":130}"
				+ "]}";
		SoundEventTable table = SoundEventTable.parse(json);
		assertEquals(130, table.get(SoundEventConfig.EventType.DEATH).orElseThrow().minPlaySeconds());
		assertEquals(SoundEventConfig.DEFAULT_MIN_PLAY_SECONDS,
				table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow().minPlaySeconds());
	}

	@Test
	void missingFieldsUseDefaults() {
		String json = "{\"events\":[{\"eventType\":\"LOW_HEALTH\"}]}";
		SoundEventTable table = SoundEventTable.parse(json);
		SoundEventConfig low = table.get(SoundEventConfig.EventType.LOW_HEALTH).orElseThrow();
		assertEquals(SoundEventConfig.DEFAULT_START_OFFSET_SECONDS, low.startOffsetSeconds(), 1e-9);
		assertEquals(SoundEventConfig.DEFAULT_MIN_PLAY_SECONDS, low.minPlaySeconds());
		assertEquals("", low.soundFile());
		assertTrue(low.loop());
	}
}
