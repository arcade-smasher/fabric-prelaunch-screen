package com.arcadesmasher.prelaunch;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Semaphore;

// should probably separate rendering and window management into another class at some point
public class PreLaunch implements PreLaunchEntrypoint {

    private static final Logger LOGGER = new Logger("loading-window");

    private static final AtomicReference<Long> windowRef = new AtomicReference<>(null);
    private static ScheduledExecutorService renderScheduler;
    private static ScheduledFuture<?> renderTick;
    private static final Semaphore renderLock = new Semaphore(1);

    public static volatile boolean running = false;
    public static volatile boolean resourcesInitialized = false;
    public static volatile boolean showDetailedStatus = false;

    private static MinecraftBitmapFont font;
    public static MinecraftBitmapFont.Text currentStatus;
    public static MinecraftBitmapFont.Text detailedStatus;

    private static final int WINDOW_WIDTH = 854;
    private static final int WINDOW_HEIGHT = 480;
    private static final int BAR_WIDTH = 400;
    private static final int BAR_HEIGHT = 20;
    private static final int SCALE = 2;
    private static final int BAR_SPACING = (int) ((BAR_HEIGHT + MinecraftBitmapFont.charHeight + MinecraftBitmapFont.lineGap) * SCALE);

    private static final Color BACKGROUND_COLOR = new Color(239, 50, 61, 1f);
    private static final Color FOREGROUND_COLOR = new Color(255, 255, 255, 1f);

    private static int frame = 0;
    private static Runtime runtime;
    private static long maxMemory;
    private static MinecraftBitmapFont.Text memoryText;
    private static float[] memoryUsed = new float[1];
    private static MinecraftBitmapFont.Text versionInfoText;
    private static MinecraftBitmapFont.Text fabricVersionInfoText;
    private static int detailedStatusFrame = -1;

    @Override
    public void onPreLaunch() {
        LOGGER.info("PRELOAD");
        try {
            startWindow();
        } catch (Exception e) {
            LOGGER.error("Failed to create early loading window", e);
        }
    }

    private void startWindow() {
        // initialize GLFW on the main thread !!! important
        GLFWErrorCallback.createPrint(System.err).set();
        if (!GLFW.glfwInit()) {
            LOGGER.error("GLFW failed to initialize!");
            return;
        }

        try {
            // create window on MAIN THREAD!!
            long windowID = createWindow();
            if (windowID == MemoryUtil.NULL) return;

            windowRef.set(windowID);
            running = true;

            // start render thread
            renderScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                final Thread thread = Executors.defaultThreadFactory().newThread(r); // it might potentially be possible for this to return something other than a Thread.
                thread.setDaemon(true);
                thread.setName("prelaunch-render-thread");
                return thread;
            });

            // init rendering on render thread
            renderScheduler.submit(() -> initGL(windowID));

            // periodic rendering, ~20 FPS
            renderTick = renderScheduler.scheduleAtFixedRate(() -> renderThreadFunc(windowID), 50, 50, TimeUnit.MILLISECONDS);

            // mem display updates, can and should be less frequent than rendering to not tax the JVM too much
            renderScheduler.scheduleAtFixedRate(PreLaunch::updateMemory, 500, 500, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            LOGGER.error("Failed during window setup", e);
            cleanupWindow();
        }
    }

    private long createWindow() {
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);

        long windowID = GLFW.glfwCreateWindow(WINDOW_WIDTH, WINDOW_HEIGHT, "Launching Minecraft", MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowID == MemoryUtil.NULL) {
            LOGGER.error("Could not create GLFW window!");
            return MemoryUtil.NULL;
        }

        GLFW.glfwSetWindowCloseCallback(windowID, w -> GLFW.glfwSetWindowShouldClose(w, false));

        GLFWVidMode vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
        if (vidMode != null) {
            GLFW.glfwSetWindowPos(windowID,
                    (vidMode.width() - WINDOW_WIDTH) / 2,
                    (vidMode.height() - WINDOW_HEIGHT) / 2
            );
        }

        GLFW.glfwShowWindow(windowID);
        return windowID;
    }

    private void initGL(long windowID) {
        // get context on render thread
        GLFW.glfwMakeContextCurrent(windowID);
        GL.createCapabilities();
        GLFW.glfwSwapInterval(1);

        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, WINDOW_WIDTH, WINDOW_HEIGHT, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();

        loadFont();
        initTextObjects();

        GLFW.glfwMakeContextCurrent(0); // release context back to MC
    }

    private void loadFont() {
        try (InputStream stream = PreLaunch.class.getResourceAsStream("/assets/prelaunch/ascii-mono.png")) {
            if (stream != null) font = MinecraftBitmapFont.load(stream, true);
        } catch (IOException e) {
            LOGGER.warn("Failed to load font for prelaunch window", e);
        }
    }

    private void initTextObjects() {
        runtime = Runtime.getRuntime();
        maxMemory = runtime.maxMemory();
        memoryText = font.new Text();

        currentStatus = font.new Text("Launching minecraft");
        detailedStatus = font.new Text("Loading");

        final String minecraftVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown");
        final String fabricVersion = FabricLoader.getInstance()
                .getModContainer("fabricloader")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("Unknown");

        versionInfoText = font.new Text(minecraftVersion + "-" + fabricVersion);
        fabricVersionInfoText = font.new Text("Fabric loading " + fabricVersion);

        resourcesInitialized = true;
    }

    private static void updateMemory() {
        if (memoryText == null) return;
        long totalMemory = runtime.totalMemory();
        long usedMemory = totalMemory - runtime.freeMemory();
        memoryUsed[0] = (float) usedMemory / maxMemory;
        memoryText.update(String.format(
                "Memory: %d/%dMB (%.1f%%)",
                usedMemory / 1048576L,
                maxMemory / 1048576L,
                memoryUsed[0] * 100f
        ));
    }

    private static final long MINFRAMETIME = TimeUnit.MILLISECONDS.toNanos(10);
    private static long nextFrameTime = 0;

    private void renderThreadFunc(long windowID) {
        // limit frame rate. i'm considering the tradeoff between high fps and cpu usage, not sure if high fps would change much
        long nt = System.nanoTime();
        if (nt < nextFrameTime) {
            return;
        }
        nextFrameTime = nt + MINFRAMETIME;

        if (!renderLock.tryAcquire()) {
            return;
        }

        try {
            GLFW.glfwMakeContextCurrent(windowID); // get context

            GL11.glViewport(0, 0, WINDOW_WIDTH, WINDOW_HEIGHT);

            background(BACKGROUND_COLOR); // draws over the frame, not behind it intuitively. essentially clears the frame.
            renderFrame();

            GLFW.glfwSwapBuffers(windowID);

            GLFW.glfwMakeContextCurrent(0);

            frame++;
        } catch (Throwable t) {
            LOGGER.error("Error during rendering", t);
        } finally {
            renderLock.release();
        }
    }

    private void renderFrame() {
        if (font == null || currentStatus == null) return;

        int x0 = (WINDOW_WIDTH - BAR_WIDTH) / 2;
        int y0 = 250 + 10;
        int gap = (int) ((currentStatus.height() + MinecraftBitmapFont.lineGap) * SCALE);
        int y1 = y0 + gap;
        int y2 = y1 + BAR_SPACING;
        int y3 = y2 + gap;

        // mem bar
        float[] color = computeMemoryBarColor(memoryUsed[0]);
        memoryText.render(WINDOW_WIDTH / 2 - memoryText.width() * SCALE / 2, 10 * SCALE + 18, SCALE);
        drawProgressBar(x0, 10, BAR_WIDTH, BAR_HEIGHT, memoryUsed[0], color);

        // status bar
        currentStatus.render(x0, y0, SCALE);
        drawProgressBar(x0, y1, BAR_WIDTH, BAR_HEIGHT, frame);

        // version info
        fabricVersionInfoText.render(10, WINDOW_HEIGHT - 8 * SCALE - 13, SCALE);
        versionInfoText.render(WINDOW_WIDTH - versionInfoText.width() * SCALE - 10, WINDOW_HEIGHT - 8 * SCALE - 13, SCALE);

        // detailed status if enabled
        if (showDetailedStatus) {
            if (detailedStatusFrame == -1) detailedStatusFrame = frame;
            detailedStatus.render(x0, y2, SCALE);
            drawProgressBar(x0, y3, BAR_WIDTH, BAR_HEIGHT, frame - detailedStatusFrame);
        } else {
            detailedStatusFrame = -1;
        }
    }

    public static void periodicTick() {
        if (running) {
            GLFW.glfwPollEvents();
        }
    }

    private float[] computeMemoryBarColor(float progress) {
        float h6 = (1.0f - (float) Math.pow(progress, 1.5f)) / 3f * 6f;
        int i = (int) Math.floor(h6);
        float f = h6 - i;

        float p = 0f;
        float q = 0.5f * (1 - f);
        float t = 0.5f * f;
        float r = 0, g = 0, b = 0;

        switch (i % 6) {
            case 0: r = 0.5f; g = t; b = p; break;
            case 1: r = q; g = 0.5f; b = p; break;
            case 2: r = p; g = 0.5f; b = t; break;
            case 3: r = p; g = q; b = 0.5f; break;
            case 4: r = t; g = p; b = 0.5f; break;
            case 5: r = 0.5f; g = p; b = q; break;
        }

        return new float[]{r, g, b, 1f};
    }

    private void drawProgressBar(int x, int y, int width, int height, float progress, Color color) {
        int inset = SCALE;
        width = width + 4 * inset;
        int filledWidth = (int)(progress * (width - 2 * inset));

        // Outer border (foreground)
        color(FOREGROUND_COLOR);
        drawBox(x, y, width, height);

        // Inner background
        color(BACKGROUND_COLOR);
        drawBox(x + inset, y + inset, width - 2 * inset, height - 2 * inset);

        // Progress fill with dynamic color
        color(color);
        drawBox(x + inset * 2, y + inset * 2, filledWidth, height - inset * 4);
    }

    private void drawProgressBar(int x, int y, int width, int height, float progress, float[] color) {
        int inset = SCALE;
        width = width + 4 * inset;
        int filledWidth = (int)(progress * (width - 2 * inset));

        // Outer border (foreground)
        color(FOREGROUND_COLOR);
        drawBox(x, y, width, height);

        // Inner background
        color(BACKGROUND_COLOR);
        drawBox(x + inset, y + inset, width - 2 * inset, height - 2 * inset);

        // Progress fill with dynamic color
        color(color);
        drawBox(x + inset * 2, y + inset * 2, filledWidth, height - inset * 4);
    }

    private void drawProgressBar(int x, int y, int width, int height, float progress) {
        drawProgressBar(x, y, width, height, progress, FOREGROUND_COLOR);
    }

    private void drawProgressBar(int x, int y, int width, int height, int frame, Color color) {
        int inset = SCALE;
        width = width + 4 * inset;
        int maxFilledWidth = width - 2 * inset;
        float clampedProgress = (frame * 4) % maxFilledWidth;

        // Outer border (foreground)
        color(FOREGROUND_COLOR);
        drawBox(x, y, width, height);

        // Inner background
        color(BACKGROUND_COLOR);
        drawBox(x + inset, y + inset, width - 2 * inset, height - 2 * inset);

        // Progress fill
        color(color);
        if (clampedProgress + 16 > maxFilledWidth - inset * 2) {
            drawBox(x + inset * 2, y + inset * 2, 16 - ((maxFilledWidth - inset * 2) - clampedProgress), height - inset * 4);
            drawBox(x + inset * 2 + clampedProgress, y + inset * 2, (maxFilledWidth - inset * 2) - clampedProgress, height - inset * 4);
        } else {
            drawBox(x + inset * 2 + clampedProgress, y + inset * 2, 16, height - inset * 4);
        }
    }
    private void drawProgressBar(int x, int y, int width, int height, int frame, float[] color) {
        int inset = SCALE;
        width = width + 4 * inset;
        int maxFilledWidth = width - 2 * inset;
        float clampedProgress = (frame * 4) % maxFilledWidth;

        // Outer border (foreground)
        color(FOREGROUND_COLOR);
        drawBox(x, y, width, height);

        // Inner background
        color(BACKGROUND_COLOR);
        drawBox(x + inset, y + inset, width - 2 * inset, height - 2 * inset);

        // Progress fill
        color(color);
        if (clampedProgress + 16 > maxFilledWidth - inset * 2) {
            drawBox(x + inset * 2, y + inset * 2, 16 - ((maxFilledWidth - inset * 2) - clampedProgress), height - inset * 4);
            drawBox(x + inset * 2 + clampedProgress, y + inset * 2, (maxFilledWidth - inset * 2) - clampedProgress, height - inset * 4);
        } else {
            drawBox(x + inset * 2 + clampedProgress, y + inset * 2, 16, height - inset * 4);
        }
    }

    private void drawProgressBar(int x, int y, int width, int height, int frame) {
        drawProgressBar(x, y, width, height, frame, FOREGROUND_COLOR);
    }

    private static void drawBox(float x, float y, float width, float height) {
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + width, y);
        GL11.glVertex2f(x + width, y + height);
        GL11.glVertex2f(x, y + height);
        GL11.glEnd();
    }

    private static void color(Color color) {
        GL11.glColor4f(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha);
    }

    private static void color(float[] color) {
        GL11.glColor4f(color[0], color[1], color[2], color[3]);
    }

    private static void background(Color color) {
        GL11.glClearColor(color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
    }

    public static void close() {
        running = false;

        if (renderScheduler != null) {
            // submit disposal task to render thread BEFORE canceling ticker
            renderScheduler.submit(() -> {
                try {
                    renderLock.acquire();
                    Long windowID = windowRef.get();
                    if (windowID != null) {
                        GLFW.glfwMakeContextCurrent(windowID);
                        disposeResources();
                        GLFW.glfwMakeContextCurrent(0);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Error disposing loading screen resources", e);
                } finally {
                    renderLock.release();
                }
            });

            if (renderTick != null) {
                renderTick.cancel(false);
            }

            renderScheduler.shutdown();
            try {
                renderScheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                renderScheduler.shutdownNow();
            }
        }

        // just destroy the window, don't try to use its context
        Long windowID = windowRef.get();
        if (windowID != null) {
            GLFW.glfwDestroyWindow(windowID);
            windowRef.set(null);
        }
    }

    private static void disposeResources() {
        disposeFont();
        font = null;
        currentStatus = null;
        detailedStatus = null;
        memoryText = null;
        versionInfoText = null;
        fabricVersionInfoText = null;
    }

    private static void disposeFont() {
        if (font != null) {
            try {
                font.dispose();
            } catch (Exception e) {
                LOGGER.warn("Failed to dispose font", e);
            }
        }
    }

    private static void cleanupWindow() {
        Long windowID = windowRef.get();
        if (windowID == null) return;

        disposeResources();
        GLFW.glfwDestroyWindow(windowID);
        GLFW.glfwTerminate();
        GLFW.glfwSetErrorCallback(null).free();
        windowRef.set(null);
    }
}
