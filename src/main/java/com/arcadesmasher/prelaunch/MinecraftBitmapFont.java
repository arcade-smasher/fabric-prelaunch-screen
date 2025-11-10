package com.arcadesmasher.prelaunch;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

public class MinecraftBitmapFont {

	private final int textureId;
	private final BufferedImage image;
	private final boolean mono;

	private static final int asciiStart = 32;
	private static final int asciiEnd = 127;
	public static final int charWidth = 8;
	public static final int charHeight = 8;
	public static final float lineGap = 1.5f;
	public static final int descent = -1;
	public static final int baseline = 7;
	private final int columns = 16;

	private final Character[] charCache = new Character[97];

	private MinecraftBitmapFont(int textureId, BufferedImage image, boolean mono) {
		this.textureId = textureId;
		this.image = image;
		this.mono = mono;
		for (char c = asciiStart; c <= asciiEnd + 1; c++) { // + 1 for extra fallback character
			this.charCache[c - asciiStart] = new Character(c);
			// System.out.println(c);
			// System.out.println(this.charCache[c - asciiStart].realWidth);
			// System.out.println(this.charCache[c - asciiStart].width);
			// System.out.println(Math.floor((this.charCache[c - asciiStart].width * 2 - this.charCache[c - asciiStart].realWidth * 2) / 2));
			// System.out.println();
		}
	}

	public static MinecraftBitmapFont load(InputStream file) throws IOException {
		return load(file, false);
	}

	/** Load a PNG font from disk and create a MinecraftBitmapFont instance */
	public static MinecraftBitmapFont load(InputStream file, boolean mono) throws IOException {
		BufferedImage image = ImageIO.read(file);
		int width = image.getWidth();
		int height = image.getHeight();

		// Convert BufferedImage to ByteBuffer
		ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int pixel = image.getRGB(x, y);
				buffer.put((byte)((pixel >> 16) & 0xFF));	// R
				buffer.put((byte)((pixel >> 8) & 0xFF));	// G
				buffer.put((byte)(pixel & 0xFF));			// B
				buffer.put((byte)((pixel >> 24) & 0xFF));	// A
			}
		}
		buffer.flip();

		int textureId = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);

		MinecraftBitmapFont font = new MinecraftBitmapFont(textureId, image, true);
		font.image.flush();
		return font;
	}

	public void dispose() {
		if (GL11.glIsTexture(textureId)) {
			GL11.glDeleteTextures(textureId);
		}

		if (image != null) {
			image.flush();
		}
	}

	public class Text {
		private String string;
		private int width;
		private int height = charHeight;
		private Character[] cachedCharacters;
		private String[] words;
		private Text[] wordTexts;

		public Text() {
			this.string = "";
			this.width = 0;
			wordTexts = new Text[0];
			cachedCharacters = new Character[0];
		}

		public Text(String string) {
			this.update(string);
		}

		public int width() {
			return width;
		}

		public int height() {
			return height;
		}

		public String string() {
			return string;
		}

		public void update(String text) {
			if (string == text) return;
			string = text;
			int w = 0;
			int fallbackIndex = asciiEnd + 1;
			cachedCharacters = new Character[text.length()];

			for (int i = 0, len = text.length(); i < len; i++) {
				char c = text.charAt(i);
				Character bCharacter = (c >= asciiStart && c < asciiEnd + 1) ? charCache[c - asciiStart] : charCache[c - fallbackIndex];
				cachedCharacters[i] = bCharacter;
				w += bCharacter.width + 1;
			}
			words = text.split(" ");
			wordTexts = new Text[words.length];
			for (int i = 0; i < words.length; i++) {
				wordTexts[i] = new Text();
			}
			width = w;
		}

		public void render(float x, float y, float scale) {
			float currentX = x;

			for (Character bCharacter : cachedCharacters) {
				bCharacter.render(currentX, y, scale);
				currentX += bCharacter.width * scale + scale;
			}
		}

		public void render(float x, float y, float maxWidth, float scale) {
			float currentX = x;
			float currentY = y;
			height = charHeight;

			for (Text wordText : wordTexts) {
				float wordWidth = wordText.width() * scale;

				if (currentX + wordWidth > maxWidth) {
					currentX = x;
					height += charHeight + lineGap * scale;
					currentY = y + height;
				}

				wordText.render(currentX, currentY, scale);
				currentX += wordWidth + charCache[0].width * scale;
			}
		}
	}

	private class Character {
		public final int width;
		public final int height = charHeight;
		public final int realWidth;
		public final boolean visible;
		public final float uMin;
		public final float vMin;
		public final float uMax;
		public final float vMax;

		public Character(char c) {
			if (c < asciiStart || c > asciiEnd) {
				width = 5;
				realWidth = 5;
				visible = false;
				uMin = 0 * charWidth / 128f;
				vMin = 0 * charHeight / 48f;
				uMax = uMin + charWidth / 128f;
				vMax = vMin + charHeight / 48f;
				return;
			}
			int index = c - asciiStart;
			int col = index % columns;
			int row = index / columns;

			uMin = col * charWidth / 128f;
			vMin = row * charHeight / 48f;
			uMax = uMin + charWidth / 128f;
			vMax = vMin + charHeight / 48f;

			// Measure width by checking rightmost non-transparent pixel
			int pixelWidth = 0;
			for (int px = charWidth - 1; px >= 0; px--) {
				for (int py = 0; py < charHeight; py++) {
					int alpha = (image.getRGB(col * charWidth + px, row * charHeight + py) >> 24) & 0xFF;
					if (alpha != 0) {
						pixelWidth = px + 1;
						break;
					}
				}
				if (pixelWidth > 0) break;
			}
			if (pixelWidth == 0) {
				visible = false;
				pixelWidth = 5; // 5 is default width for space character
			} else {
				visible = true;
			}
			realWidth = pixelWidth;
			width = mono ? 5 : pixelWidth;
		}

		public void render(float x, float y, float scale) {
			if (!visible) return;
			if (mono) x = x + (float) Math.floor((width * scale - realWidth * scale) / 2);

			GL11.glPushAttrib(GL11.GL_TEXTURE_BIT | GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT);
			GL11.glEnable(GL11.GL_BLEND);
			GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			GL11.glColor4f(1f, 1f, 1f, 1f);

			GL11.glBegin(GL11.GL_QUADS);
			GL11.glTexCoord2f(uMin, vMin); GL11.glVertex2f(x, y);
			GL11.glTexCoord2f(uMax, vMin); GL11.glVertex2f(x + charWidth*scale, y);
			GL11.glTexCoord2f(uMax, vMax); GL11.glVertex2f(x + charWidth*scale, y + charHeight*scale);
			GL11.glTexCoord2f(uMin, vMax); GL11.glVertex2f(x, y + charHeight*scale);
			GL11.glEnd();
			GL11.glPopAttrib();
		}
	}

	public void drawString(String text, float x, float y, float scale) {
		float currentX = x;
		int fallbackIndex = asciiEnd + 1;

		for (int i = 0, len = text.length(); i < len; i++) {
			char c = text.charAt(i);
			Character bCharacter = (c >= asciiStart && c < asciiEnd + 1) ? charCache[c - asciiStart] : charCache[fallbackIndex];
			bCharacter.render(currentX, y, scale);
			currentX += bCharacter.width * scale + scale;
		}
	}

	public void drawString(String text, float x, float y, float maxWidth, float scale) {
		float currentX = x;
		float currentY = y;

		for (String word : text.split(" ")) {
			Text wordText = new Text(word);
			float wordWidth = wordText.width() * scale;
			if (currentX + wordWidth > maxWidth) {
				currentX = x;
				currentY += charHeight + lineGap * scale;
			}
			charCache[0].render(currentX + wordWidth, currentY, scale);
			wordText.render(currentX, currentY, scale);
			currentX += wordWidth + charCache[0].width * scale;
		}
	}
}