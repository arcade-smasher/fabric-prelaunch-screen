package com.arcadesmasher.prelaunch;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BackgroundWaiter {
	private static ExecutorService runner = Executors.newSingleThreadExecutor();

	public static void runAndTick(Runnable toRun, Runnable toTick) {
		final Future<?> work = runner.submit(toRun);
		do {
			toTick.run();
			try {
				Thread.sleep(50);
			} catch (InterruptedException ignored) {}
		} while (!work.isDone());
		try {
			runner.shutdown();
			work.get();
		} catch (ExecutionException e) {
			throw new RuntimeException(e.getCause());
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}
}