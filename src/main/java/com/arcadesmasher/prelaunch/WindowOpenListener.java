package com.arcadesmasher.prelaunch;

import java.util.LinkedList;
import java.util.List;

public final class WindowOpenListener {
	private static final List<Runnable> listeners = new LinkedList<>();
	
	public static List<Runnable> getListeners() {
		return listeners;
	}

	public static void trigger() {
		for (Runnable l : listeners) {
			l.run();
		}
	}
}