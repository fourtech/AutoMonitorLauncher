package com.fourtech.automonitor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.fourtech.voice.analyser.Fft;

public class VoiceMonitor {
	private static final boolean DEBUG = false;
	private static final String TAG = "VoiceMonitor";

	// 48000 44100 8000
	private static final int SAMPLE_RATE_IN_HZ = 48000;
	// The size of mFDbBuffer
	private static final int HISTORY_SIZE = 200;

	private Looper mLooper;
	private Handler mHandler;
	private Context mContext;

	private Fft mFft = new Fft();
	private AudioRecord mRecorder;
	private boolean isRecording = false;

	private int N = 0;
	private float mTrigger = 6.0f;

	private short[] mPcmBuffer; // PCM buffer
	private float[] mFDbBuffer; // Frequency to DB buffer
	private float[][] mFDbHistory; // Frequency to DB buffer history
	private float[] mFDbBufferT; // Sorted buffer to get mFDbBufferC
	private OnTriggerListener mListener;

	private boolean mIsAddModels = false;
	private List<float[]> mFDBModels = new ArrayList<>();

	public VoiceMonitor(Context context) {
		super();
		mContext = context;

		HandlerThread t = new HandlerThread(TAG);
		t.start();
		mLooper = t.getLooper();
		mHandler = new Handler(mLooper);
		if (DEBUG) Log.i(TAG, "VoiceMonitor create" + mContext);
	}

	public void start() {
		stop();
		if (mRecorder == null) {
			int minBufferSize = AudioRecord
					.getMinBufferSize(SAMPLE_RATE_IN_HZ,
							AudioFormat.CHANNEL_IN_MONO,
							AudioFormat.ENCODING_PCM_16BIT);

			if (DEBUG) Log.i(TAG, "start() 1 minBufferSize=" + minBufferSize);

			switch (SAMPLE_RATE_IN_HZ) {
			case 44100: minBufferSize = 4096; break;
			case 48000: minBufferSize = 4480; break;
			}

			if (DEBUG) Log.i(TAG, "start() 2 minBufferSize=" + minBufferSize);

			mPcmBuffer = new short[minBufferSize / 2];
			mFDbBuffer = new float[mPcmBuffer.length / 2 + 1];

			// mFft.init(mPcmBuffer.length, Fft.FLAG_FILTER | (20 << 8));
			// mFft.init(mPcmBuffer.length, Fft.FLAG_HAMM);
			// mFft.init(mPcmBuffer.length, Fft.FLAG_HANN);
			mFft.init(mPcmBuffer.length, 0);

			mFDbHistory = new float[mFDbBuffer.length / 3][HISTORY_SIZE];
			mFDbBufferT = new float[HISTORY_SIZE];

			mRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
					SAMPLE_RATE_IN_HZ, AudioFormat.CHANNEL_IN_MONO,
					AudioFormat.ENCODING_PCM_16BIT, minBufferSize);
		}
		mRecorder.startRecording();
		mHandler.post(new AudioRecordJob());
	}

	public void stop() {
		isRecording = false;
		if (mRecorder != null) {
			mRecorder.stop();
			mRecorder = null;
		}
		mHandler.removeCallbacksAndMessages(null);
	}

	public void setOnTriggerListener(OnTriggerListener l) {
		mListener = l;
	}

	private class AudioRecordJob implements Runnable {
		public void run() {
			long start;
			isRecording = true;
			while (isRecording) {
				if (DEBUG) start = System.currentTimeMillis();

				int read = mRecorder.read(mPcmBuffer, 0, mPcmBuffer.length);
				if (DEBUG) {
					Log.i(TAG, "mRecorder.read() mPcmBuffer=["
							+ mPcmBuffer[100] + ", " + mPcmBuffer[101] + ", "
							+ mPcmBuffer[102] + ", " + mPcmBuffer[103] + ", "
							+ mPcmBuffer[104] + ", " + mPcmBuffer[105] + ", "
							+ mPcmBuffer[106] + ", " + mPcmBuffer[107] + ", "
							+ mPcmBuffer[108] + ", " + mPcmBuffer[109] + ", "
							+ mPcmBuffer[110] + ", " + mPcmBuffer[111] + "]");
				}
				int result = mFft.fft(mPcmBuffer, mFDbBuffer);
				if (DEBUG) {
					Log.i(TAG, "mRecorder.read() used "
									+ (System.currentTimeMillis() - start)
									+ "millis read=" + read
									+ ", mPcmBuffer.length" + mPcmBuffer.length
									+ ", result=" + result);
				}

				if (DEBUG) start = System.currentTimeMillis();

				if (mIsAddModels) {
					addModels(Arrays.copyOf(mFDbBuffer, mFDbBuffer.length));
				} else {
					compareModels(Arrays.copyOf(mFDbBuffer, mFDbBuffer.length));
				}

				if (DEBUG) Log.i(TAG, "refreshUIAsync() used " + (System.currentTimeMillis() - start) + "millis");
			}
		}
	}

	private void addModels(float[] buffer) {

	}

	private void compareModels(float[] buffer) {
		if (N < HISTORY_SIZE) {
			for (int i = 0; i < mFDbHistory.length; i++) {
				pushInArray(mFDbHistory[i], buffer[i]);
			}
			N++;
			return;
		}

		float a = 0;
		// int aIndex = 3*mFDbBufferT.length/5;
		float newFDb = 0, comFDb = 0, fDbChanged = 0.0f;
		for (int i = 0; i < mFDbHistory.length; i++) {
			pushInArray(mFDbHistory[i], buffer[i]);
			System.arraycopy(mFDbHistory[i], 0, mFDbBufferT, 0, HISTORY_SIZE);
			Arrays.sort(mFDbBufferT);
			for (int j = 50, l = mFDbBufferT.length - 50; j < l; j++) {
				a += mFDbBufferT[j];
			}
			a /= (mFDbBufferT.length - 100);
			// a = mFDbBufferT[aIndex];

			newFDb += buffer[i];
			comFDb += a;
		}

		fDbChanged = (newFDb - comFDb) / mFDbHistory.length;
		if (DEBUG) Log.i(TAG, "compareModels() fDbChanged=" + fDbChanged);

		if (fDbChanged >= mTrigger && mListener != null) {
			mListener.onTrigger(Action.TRIGGER);
		}
	}

	// 把newValue插入数组最前面并依次后移
	private void pushInArray(float[] array, float newValue) {
		for (int i = array.length - 1; i > 0; i--) {
			array[i] = array[i - 1] > 0 ? array[i - 1] : newValue;
		}
		array[0] = newValue;
	}
}