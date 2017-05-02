package com.fourtech.automonitor;

import static android.os.SystemProperties.getBoolean;

import java.util.HashMap;
import java.util.Map;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

public class MonitorService extends Service {
	private static final boolean DEBUG = true;
	private static final String TAG = "MonitorService";
	public static final String MONITOR_SERVICE = "auto-monitor";
	private static final boolean ENABLE_VOICE_MONITOR = getBoolean("persist.sys.voicemonitor", true);
	private static final int TRIGGER_TIME = 1500;

	private Looper mLooper;
	private Handler mHandler;

	private LocationMonitor mLocationMonitor;
	private AltimeterMonitor mAltimeterMonitor;
	private VoiceMonitor mVoiceMonitor;
	private AutoMonitorCloud mAutoMonitorCloud;
	private Map<Byte, Long> mActionTimes = new HashMap<>();

	@Override
	public void onCreate() {
		super.onCreate();
		HandlerThread t = new HandlerThread(TAG);
		t.start();
		mLooper = t.getLooper();
		mHandler = new Handler(mLooper);

		mAutoMonitorCloud = AutoMonitorCloud.get();
		mLocationMonitor = new LocationMonitor(this);
		mAltimeterMonitor = new AltimeterMonitor(this);
		mAltimeterMonitor.setOnTriggerListener(mAltListener);
		mLocationMonitor.start();
		mAltimeterMonitor.start();
		if (!ENABLE_VOICE_MONITOR) {
			mAltimeterMonitor.setTrigger(12.0f);
		} else {
			mVoiceMonitor = new VoiceMonitor(this);
			mVoiceMonitor.setOnTriggerListener(mVoiListener);
			mVoiceMonitor.start();
		}

		mActionTimes.put(Action.TRIGGER, 0l);
		mActionTimes.put(Action.DOOR_OPENED, 0l);
		mActionTimes.put(Action.DOOR_CLOSED, 0l);

		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				String deviceId = mAutoMonitorCloud.getDeviceId();
				Log.i(TAG, "onCreate() deviceId=" + deviceId);
				if (TextUtils.isEmpty(deviceId)) {
					mHandler.postDelayed(this, 5000);
				} else {
					register();
				}
			}
		}, 5000);

		if (DEBUG) Log.i(TAG, "MonitorService() start");
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	private void register() {
		mHandler.postDelayed(new Runnable() {
			@Override
			public void run() {
				if (mAutoMonitorCloud.reg()) {
					mHandler.postDelayed(this, 60000);
				} else {
					mHandler.postDelayed(this, 5000);
				}
			}
		}, 60000);
	}

	private OnTriggerListener mAltListener = new OnTriggerListener() {
		@Override
		public void onTrigger(byte action) {
			if (!mLocationMonitor.isMoving()) {
				long now = System.currentTimeMillis();
				long tt = mActionTimes.get(Action.TRIGGER);
				if (now - tt < TRIGGER_TIME || !ENABLE_VOICE_MONITOR) {
					mAutoMonitorCloud.postAction(action);
					mActionTimes.put(Action.TRIGGER, 0l);
				} else {
					mActionTimes.put(action, now);
				}
			}
		}
	};

	private OnTriggerListener mVoiListener = new OnTriggerListener() {
		@Override
		public void onTrigger(byte action) {
			if (!mLocationMonitor.isMoving()) {
				long now = System.currentTimeMillis();
				long dot = mActionTimes.get(Action.DOOR_OPENED);
				long dct = mActionTimes.get(Action.DOOR_CLOSED);
				if (dot > dct) {
					if (now - dot < TRIGGER_TIME) {
						mAutoMonitorCloud.postAction(Action.DOOR_OPENED);
						mActionTimes.put(Action.TRIGGER, 0l);
						mActionTimes.put(Action.DOOR_OPENED, 0l);
					} else {
						mActionTimes.put(action, now);
					}
				} else {
					if (now - dct < TRIGGER_TIME) {
						mAutoMonitorCloud.postAction(Action.DOOR_CLOSED);
						mActionTimes.put(Action.TRIGGER, 0l);
						mActionTimes.put(Action.DOOR_CLOSED, 0l);
					} else {
						mActionTimes.put(action, now);
					}
				}
			}
		}
	};

}