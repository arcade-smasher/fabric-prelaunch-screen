package com.arcadesmasher.prelaunch;

import net.fabricmc.api.ModInitializer;

public class Initializer implements ModInitializer {
	public static final Logger LOGGER = new Logger("loading-window");

	@Override
	public void onInitialize() {
		WindowOpenListener.getListeners().add(() -> {
			PreLaunch.close();
			LOGGER.info("Closed early GLFW loading window.");
		});
	}
}