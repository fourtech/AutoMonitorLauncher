package com.fourtech.automonitor;

import java.util.Arrays;
import java.util.Locale;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.fourtech.hardware.Variometer;

public class AltimeterMonitor {
	private static final boolean DEBUG = false;
	private static final String TAG = "AltimeterMonitor";

	private Looper mLooper;
	private Handler mHandler;

	private static final int P_SIZE = 50;

	private int mN = 0; // 计算第N次重新开始
	private float mTrigger = 9.0f; // 触发开门警告的阀值
	private float[] mPs = new float[P_SIZE]; // 压力值
	private float[] mSortedPs = new float[P_SIZE]; // 排序压力值

	private Variometer mVariometer;
	private int[] mOutValues = { 0, 0 };
	private boolean mIsRunning = false;
	private OnTriggerListener mListener;

	public AltimeterMonitor(Context context) {
		super();
		mVariometer = new Variometer();
		mVariometer.open();

		HandlerThread t = new HandlerThread(TAG);
		t.start();
		mLooper = t.getLooper();
		mHandler = new Handler(mLooper);
	}

	public void start() {
		mIsRunning = true;
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				if (mIsRunning) refreshData();
				if (mIsRunning) mHandler.postDelayed(this, 20);
			}
		});
	}

	public void stop() {
		mIsRunning = false;
		mHandler.removeCallbacksAndMessages(null);
	}

	public float getTrigger() {
		return mTrigger;
	}

	public void setTrigger(float trigger) {
		mTrigger = trigger;
	}

	public void setOnTriggerListener(OnTriggerListener l) {
		mListener = l;
	}

	// 更新UI，需要异步调用
	private void refreshData() {
		if (DEBUG) Log.i(TAG, "refreshData() +++");
		mVariometer.getValues(mOutValues); // 读取气压和温度值
		if (DEBUG) Log.i(TAG, "refreshData() vs=" + Arrays.toString(mOutValues));

		try {
			float pressure = mOutValues[0];
			float temperature = mOutValues[1];

			// 把新加入的气压值插入队列
			pushInArray(mPs, pressure);

			// 排序气压值，以方便过滤掉最大值和最小值求平均数
			System.arraycopy(mPs, 0, mSortedPs, 0, mPs.length);
			Arrays.sort(mSortedPs);

			// 求平均值
			float a = 0;
			// 过滤掉10个最大值和10个最小值
			for (int j = 10; j < mPs.length - 10; j++) {
				a += mSortedPs[j];
			}
			a /= mPs.length - 20;

			// 如果压力超过阀值则触发开门警告
			if (mN >= P_SIZE) {
				float delta = pressure - a;
				/* if (DEBUG) */Log.i(TAG, "refreshData() a=" + a + ",\tp=" + pressure + "(\t" + delta + "\t),\tt=" + temperature);
				if (delta > mTrigger) { // 气压升高为车门关闭
					Log.i(TAG, String.format(Locale.ENGLISH, "Door closed ( %.2f mBar )", delta / 100.0f));
					if (mListener != null) mListener.onTrigger(Action.DOOR_CLOSED);
				} else if (delta <= -mTrigger) { // 气压降低为车门打开
					Log.i(TAG, String.format(Locale.ENGLISH, "Door opened ( %.2f mBar )", delta / 100.0f));
					if (mListener != null) mListener.onTrigger(Action.DOOR_OPENED);
				}
			} else {
				mN++;
			}

		} catch (Throwable tt) {
			Log.w(TAG, "refreshUIAsync() error", tt);
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