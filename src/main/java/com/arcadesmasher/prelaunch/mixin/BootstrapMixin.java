package com.arcadesmasher.prelaunch.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.arcadesmasher.prelaunch.PreLaunch;

import net.minecraft.Bootstrap;

@Mixin(Bootstrap.class)
public class BootstrapMixin {
	@Inject(method = "initialize", at = @At("HEAD"))
	private static void onInitialize(CallbackInfo ci) {
		if (!PreLaunch.running) return;
		System.out.println("BOOTSTRAP");
		PreLaunch.currentStatus.update("Loading bootstrap resources");
		PreLaunch.showDetailedStatus = true;
	}

	@Inject(method = "initialize", at = @At("RETURN"))
	private static void onInitialized(CallbackInfo ci) {
		if (!PreLaunch.running) return;
		PreLaunch.currentStatus.update("Bootstrap resources loaded");
		PreLaunch.showDetailedStatus = false;
	}
}