package com.arcadesmasher.prelaunch;

public class Color {

	public final int red;
	public final int green;
	public final int blue;
	public final float alpha;

	public Color(Number r, Number g, Number b, Number a) {
		this.red   = normalizeTo255(r);
		this.green = normalizeTo255(g);
		this.blue  = normalizeTo255(b);
		this.alpha = normalizeTo1(a);
	}

	private static int normalizeTo255(Number n) {
		if (n instanceof Float || n instanceof Double) {
			return Math.min(255, Math.max(0, (int) (n.floatValue() * 255)));
		} else {
			return Math.min(255, Math.max(0, n.intValue()));
		}
	}

	private static float normalizeTo1(Number n) {
		if (n instanceof Float || n instanceof Double) {
			return Math.min(1f, Math.max(0f, n.floatValue()));
		} else {
			return Math.min(1f, Math.max(0f, n.intValue() / 255f));
		}
	}

	@Override
	public String toString() {
		return String.format("Color(r=%d, g=%d, b=%d, a=%.2f)", red, green, blue, alpha);
	}
}