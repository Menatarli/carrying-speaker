package com.menatarli.ncmplayer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NcmPlayer 的纯逻辑测试：不加载 Minecraft，也不真的拉起任何进程。 */
class NcmPlayerTest {
	/** 任务书里给出的、实测能拉起网易云播《海阔天空》的那串 URL。 */
	private static final String VERIFIED_SONG_URL =
			"orpheus://eyJ0eXBlIjoic29uZyIsImlkIjoiMzQ3MjMwIiwiY21kIjoicGxheSJ9";

	@Test
	void buildUrlSongMatchesVerifiedPayload() {
		assertEquals(VERIFIED_SONG_URL, NcmPlayer.buildUrl("song", "347230"));
	}

	@Test
	void buildUrlPlaylistDecodesBackToExpectedJson() {
		String url = NcmPlayer.buildUrl("playlist", "3778678");

		assertTrue(url.startsWith("orpheus://"), "缺 orpheus:// 前缀：" + url);
		String json = new String(Base64.getDecoder().decode(url.substring("orpheus://".length())),
				StandardCharsets.UTF_8);
		assertEquals("{\"type\":\"playlist\",\"id\":\"3778678\",\"cmd\":\"play\"}", json);
	}

	@Test
	void buildUrlKeyOrderAndSpacingAreStable() {
		String url = NcmPlayer.buildUrl("song", "1");
		String json = new String(Base64.getDecoder().decode(url.substring("orpheus://".length())),
				StandardCharsets.UTF_8);
		// 字段顺序固定为 type, id, cmd，没有空格
		assertEquals("{\"type\":\"song\",\"id\":\"1\",\"cmd\":\"play\"}", json);
	}

	@Test
	void buildUrlRejectsUnknownType() {
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("album", "1"));
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("Song", "1"));
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("", "1"));
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("song ", "1"));
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl(null, "1"));
	}

	@Test
	void buildUrlRejectsIllegalIds() {
		String[] badIds = { "", " ", "abc", "12a", "1 2", " 12", "12 ", "1\"2", "12&34", "-1", "+1", "1.0", "中文" };
		for (String id : badIds) {
			assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("song", id), "id 应被拒绝：" + id);
		}
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("song", null));
	}

	@Test
	void isValidIdAcceptsOneToTwentyDigits() {
		assertTrue(NcmPlayer.isValidId("1"));
		assertTrue(NcmPlayer.isValidId("347230"));
		assertTrue(NcmPlayer.isValidId("12345678901234567890")); // 20 位
	}

	@Test
	void isValidIdRejectsEverythingElse() {
		String[] badIds = {
				null, "", " ", "abc", "12a", "1 2", " 12", "12 ", "1\"2", "12&34", "-1", "+1", "1.0",
				"123456789012345678901" // 21 位
		};
		for (String id : badIds) {
			assertFalse(NcmPlayer.isValidId(id), "id 应被拒绝：" + id);
		}
	}

	@Test
	void buildUrlRejectsTwentyOneDigitId() {
		assertThrows(IllegalArgumentException.class, () -> NcmPlayer.buildUrl("song", "123456789012345678901"));
	}
}
