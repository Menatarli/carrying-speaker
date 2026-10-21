package com.menatarli.ncmplayer;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用入口：客户端和专用服务器都会加载。
 *
 * <p>目前只打一行日志；后续的网络 payload 注册和服务端命令（/ncmop）会加在这里。
 * 这个类所在的 main 源集不能引用任何客户端类（Minecraft、GLFW、JNA 等），
 * 否则专用服务器启动时会 NoClassDefFoundError。
 */
public class NcmCommonMod implements ModInitializer {
	private static final Logger LOGGER = LoggerFactory.getLogger("carryingspeaker");

	@Override
	public void onInitialize() {
		NcmNetworking.register();
		NcmServerCommands.register();
		LOGGER.info("carryingspeaker 通用部分已加载");
	}
}
