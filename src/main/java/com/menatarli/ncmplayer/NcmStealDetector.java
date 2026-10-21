package com.menatarli.ncmplayer;

/**
 * 判断前台是不是被网易云从 MC 手里抢走的。纯逻辑，不引用任何 Minecraft / JNA 类。
 *
 * <p>规则：只有「上一次前台是 MC、这一次是网易云」才算网易云主动抢（{@link Action#ACT}）。
 * 其他任何进入网易云的方式都算用户自己切过去的（{@link Action#STOP}），我们不接管窗口。
 */
public final class NcmStealDetector {
	/** 前台窗口的分类。 */
	public enum Fg {
		/** Minecraft。 */
		MC,
		/** 网易云。 */
		CLOUDMUSIC,
		/** 其他任何窗口。 */
		OTHER
	}

	/** 这一轮该做什么。 */
	public enum Action {
		/** 网易云从 MC 手里抢走了前台：可以接管窗口。 */
		ACT,
		/** 用户自己切过去的：本轮不再接管。 */
		STOP,
		/** 没变化：继续轮询。 */
		NONE
	}

	private Fg last;

	public NcmStealDetector(Fg initial) {
		this.last = initial == null ? Fg.OTHER : initial;
	}

	/** 喂一次当前前台状态，返回这一轮的动作。 */
	public Action feed(Fg now) {
		if (now == null) {
			now = Fg.OTHER;
		}
		Action action = Action.NONE;
		if (now == Fg.CLOUDMUSIC) {
			action = last == Fg.MC ? Action.ACT : Action.STOP;
		}
		last = now;
		return action;
	}
}
