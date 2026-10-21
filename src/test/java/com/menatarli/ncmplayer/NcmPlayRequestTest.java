package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link NcmPlayRequest} 的纯逻辑测试，不依赖 Minecraft。 */
class NcmPlayRequestTest {

	@Test
	void acceptsSongAndPlaylist() {
		assertTrue(NcmPlayRequest.isValidType(NcmPlayer.TYPE_SONG));
		assertTrue(NcmPlayRequest.isValidType(NcmPlayer.TYPE_PLAYLIST));
	}

	@Test
	void rejectsOtherTypes() {
		assertFalse(NcmPlayRequest.isValidType("album"));
		assertFalse(NcmPlayRequest.isValidType(""));
		assertFalse(NcmPlayRequest.isValidType(null));
	}

	@Test
	void keepsNormalName() {
		assertEquals("Menatarli", NcmPlayRequest.sanitizeSender("Menatarli"));
		assertEquals("player_1", NcmPlayRequest.sanitizeSender("player_1"));
	}

	@Test
	void rejectsFormattingCode() {
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender("§cHacker"));
	}

	@Test
	void rejectsTooLongName() {
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender("abcdefghijklmnopq"));
	}

	@Test
	void rejectsEmptyAndNull() {
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender(""));
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender(null));
	}

	@Test
	void rejectsChineseAndSpaces() {
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender("梦娜"));
		assertEquals(NcmPlayRequest.FALLBACK_SENDER, NcmPlayRequest.sanitizeSender("a b"));
	}
}
