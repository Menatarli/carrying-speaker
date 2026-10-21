package com.menatarli.ncmplayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** {@link PlayRequest} 的纯逻辑测试。 */
class PlayRequestTest {

	@Test
	void randomLoopDefaults() {
		PlayRequest request = PlayRequest.randomLoop();
		assertEquals("", request.soundFile());
		assertEquals(0.0, request.startOffsetSeconds(), 1e-9);
		assertTrue(request.loop());
	}

	@Test
	void offsetAt44100() {
		// 44.1kHz 每帧约 26.122ms；60 秒 → floor(60000 / 26.122) = 2296
		assertEquals(2296, PlayRequest.offsetToFrames(60.0, 26.122F));
	}

	@Test
	void offsetAt48000() {
		// 48kHz 每帧 24ms；60 秒 → 2500
		assertEquals(2500, PlayRequest.offsetToFrames(60.0, 24.0F));
	}

	@Test
	void zeroOffset() {
		assertEquals(0, PlayRequest.offsetToFrames(0.0, 26.122F));
	}

	@Test
	void negativeOffset() {
		assertEquals(0, PlayRequest.offsetToFrames(-5.0, 26.122F));
	}

	@Test
	void nanOffset() {
		assertEquals(0, PlayRequest.offsetToFrames(Double.NaN, 26.122F));
	}

	@Test
	void badFrameLength() {
		assertEquals(0, PlayRequest.offsetToFrames(10.0, 0.0F));
		assertEquals(0, PlayRequest.offsetToFrames(10.0, -1.0F));
		assertEquals(0, PlayRequest.offsetToFrames(10.0, Float.NaN));
	}

	@Test
	void nonLoopRequest() {
		PlayRequest request = new PlayRequest("a.mp3", 12.5, false);
		assertEquals("a.mp3", request.soundFile());
		assertEquals(12.5, request.startOffsetSeconds(), 1e-9);
		assertFalse(request.loop());
	}
}
