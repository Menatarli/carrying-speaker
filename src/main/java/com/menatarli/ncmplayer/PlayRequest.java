package com.menatarli.ncmplayer;

/**
 * 一次播放请求。纯逻辑，不引用任何 Minecraft / 音频类。
 *
 * @param soundFile          空串表示从音乐目录随机选；非空表示只播这一首
 * @param startOffsetSeconds 从第几秒起播（只作用于本次请求的第一首）
 * @param loop               是否循环续播；false 表示只播一遍
 */
public record PlayRequest(String soundFile, double startOffsetSeconds, boolean loop) {
	/** 现在默认的行为：随机选曲、从头播、循环。 */
	public static PlayRequest randomLoop() {
		return new PlayRequest("", 0.0, true);
	}

	/**
	 * 把秒数换算成 mp3 帧数：{@code floor(offset * 1000 / msPerFrame)}。
	 *
	 * <p>帧长非正、offset 是 NaN / 无穷 / 负数时都返回 0（从头播）。
	 */
	public static int offsetToFrames(double offsetSeconds, float msPerFrame) {
		if (!(msPerFrame > 0.0F)) {
			return 0;
		}
		if (!Double.isFinite(offsetSeconds) || offsetSeconds < 0.0) {
			return 0;
		}
		return (int) Math.floor(offsetSeconds * 1000.0 / msPerFrame);
	}
}
