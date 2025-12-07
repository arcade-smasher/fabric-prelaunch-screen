package com.arcadesmasher.prelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.arcadesmasher.prelaunch.BackgroundWaiter;
import com.arcadesmasher.prelaunch.PreLaunch;

import net.minecraft.Bootstrap;

@Mixin(Bootstrap.class)
public class BootstrapMixin {
	private static boolean isBackgroundInit = false;

	private static void inject(CallbackInfo ci) {
		PreLaunch.showDetailedStatus = true;
		if (!PreLaunch.resourcesInitialized) return;
		System.out.println("BOOTSTRAP");
		PreLaunch.currentStatus.update("Loading bootstrap resources");
	}

	@Inject(method = "initialize", at = @At("HEAD"), cancellable = true)
	private static void onInitialize(CallbackInfo ci) {
		if (isBackgroundInit) {
			// inject method is any code you'd normally call during bootstrap initialization if there had not been thread separation.
			// essentially, replacing the whole onInitialize method with the inject method would virtually do the same thing
			inject(ci);
			return;
		}

		ci.cancel();

		BackgroundWaiter.runAndTick(() -> { // this thread separation stuff could potentially cause issues with mods that touch the bootstrapper
			isBackgroundInit = true;
			Bootstrap.initialize();
			isBackgroundInit = false;
		}, PreLaunch::periodicTick);
	}

	@Inject(method = "initialize", at = @At("RETURN"))
	private static void onInitialized(CallbackInfo ci) {
		PreLaunch.showDetailedStatus = false;
		if (!PreLaunch.resourcesInitialized) return;
		PreLaunch.currentStatus.update("Bootstrap resources loaded");
	}
}