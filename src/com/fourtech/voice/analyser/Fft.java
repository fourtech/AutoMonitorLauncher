package com.fourtech.voice.analyser;

public class Fft {
	static {
		System.loadLibrary("fftw_android");
	}

	public static final int FLAG_HAMM = 0x0001;
	public static final int FLAG_HANN = 0x0002;
	public static final int FLAG_FILTER = 0x0004;
	public static final int FLAG_FILTER_VALUE = 0xFF00;

	public native int init(int length, int useHamm);
	public native int fft(short[] inArray, float[] outArray);
	public native int release();
}