package com.fourtech.automonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import android.location.Location;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.fourtech.auto.launcher.Launcher;

public class AutoMonitorCloud {
	private static final String TAG = "AutoMonitorCloud";
	private static final boolean DEBUG = true;
	private static final String URL_REG = "http://auto.fourtech.me/automonitor/reg.php";
	private static final String URL_SIGN = "http://auto.fourtech.me/automonitor/sign.php";
	private static final String URL_PUSH = "http://auto.fourtech.me/automonitor/push.php";
	private static final String URL_POST_ACTION = "http://auto.fourtech.me/automonitor/postAction.php";
	private static final String URL_POST_LOCATIONS = "http://auto.fourtech.me/automonitor/postLocations.php";
	private static final long POST_LOCATIONS_INTERVAL = 30 * 1000;
	private static final long SEND_DOOR_MESSAGE_INTERVAL = 2 * 1000;

	private static String sDeviceId = null;

	private Looper mLooper;
	private Handler mHandler;
	private StringBuffer mLatlngs = new StringBuffer();
	private long mLastSendLocTime = System.currentTimeMillis();
	private Map<Byte, Long> mLastSendTimes = new HashMap<>();

	private static AutoMonitorCloud sMe;
	public static AutoMonitorCloud get() {
		return sMe != null ? sMe : (sMe = new AutoMonitorCloud());
	}

	private AutoMonitorCloud() {
		super();
		HandlerThread t = new HandlerThread(TAG);
		t.start();
		mLooper = t.getLooper();
		mHandler = new Handler(mLooper);

		mLastSendTimes.put(Action.TRIGGER, 0l);
		mLastSendTimes.put(Action.DOOR_OPENED, 0l);
		mLastSendTimes.put(Action.DOOR_CLOSED, 0l);
	}

	public boolean reg() {
		String deviceId = getDeviceId();
		if (!TextUtils.isEmpty(deviceId)) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("device_id", deviceId));
			params.add(new BasicNameValuePair("serialno", Launcher.getSerialNo()));
			String result = httpPost(URL_REG, params);
			if (DEBUG) Log.i(TAG, "reg() result=" + result);
			return ("OK".equals(result));
		}
		return false;
	}

	public String getDeviceId() {
		if (sDeviceId != null)
			return sDeviceId;

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair("serialno", Launcher.getSerialNo()));
		String result = httpPost(URL_SIGN, params);
		if (DEBUG) Log.i(TAG, "getDeviceId() result=" + result);
		if (!TextUtils.isEmpty(result)) {
			try {
				JSONObject obj = new JSONObject(result);
				return (sDeviceId = obj.getString("id"));
			} catch (Throwable t) {
				Log.w(TAG, "getDeviceId() failed", t);
			}
		}

		return null;
	}

	public void postLocation(Location location) {
		if (location != null) {
			if (mLatlngs.length() > 0) mLatlngs.append(',');
			mLatlngs.append(location.getLatitude()).append(',')
					.append(location.getLongitude()).append(',')
					.append(location.getTime());
			mHandler.post(mPostLocationsJob);
		}
	}

	public void postLocationImmediately(Location location) {
		mLastSendLocTime = 0;
		postLocation(location);
	}

	public void postAction(byte action) {
		mHandler.post(new PostActionJob(action));
	}

	public void sendTextMessage(final String msg) {
		mHandler.post(new Runnable() {
			@Override
			public void run() {
				pushTextMessageLocal(msg);
			}
		});
	}

	private boolean postLocationsLocal(String latlngs) {
		if (latlngs == null || latlngs.length() <= 0)
			return false;

		String deviceId = getDeviceId();
		if (!TextUtils.isEmpty(deviceId)) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("device_id", deviceId));
			params.add(new BasicNameValuePair("latlngs", latlngs));
			String result = httpPost(URL_POST_LOCATIONS, params);
			if (DEBUG) Log.i(TAG, "postLocationsLocal() result=" + result);
			return ("OK".equals(result));
		}

		return false;
	}

	private boolean postActionLocal(byte action) {
		String deviceId = getDeviceId();
		if (!TextUtils.isEmpty(deviceId)) {
			long now = System.currentTimeMillis();
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("device_id", deviceId));
			params.add(new BasicNameValuePair("action", String.valueOf(action)));
			params.add(new BasicNameValuePair("time", String.valueOf(now)));
			String result = httpPost(URL_POST_ACTION, params);
			if (DEBUG) Log.i(TAG, "postActionLocal() result=" + result);
			return ("OK".equals(result));
		}
		return false;
	}

	private boolean pushTextMessageLocal(String text) {
		if (text == null || text.length() <= 0)
			return false;

		String deviceId = getDeviceId();
		if (!TextUtils.isEmpty(deviceId)) {
			List<NameValuePair> params = new ArrayList<NameValuePair>();
			params.add(new BasicNameValuePair("device_id", deviceId));
			params.add(new BasicNameValuePair("content", text));
			String result = httpPost(URL_PUSH, params);
			if (DEBUG) Log.i(TAG, "pushTextMessage() result=" + result);
			return ("OK".equals(result));
		}

		return false;
	}

	private String httpPost(String uri, List<NameValuePair> params) {
		if (uri == null || uri.length() <= 0 || params == null
				|| params.size() <= 0)
			return null;

		HttpResponse httpResponse = null;
		HttpPost httpPost = new HttpPost(uri);
		try {
			httpPost.setEntity(new UrlEncodedFormEntity(params, HTTP.UTF_8));
			httpResponse = new DefaultHttpClient().execute(httpPost);
			HttpEntity entity = httpResponse.getEntity();

			if (httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				if (DEBUG) Log.i(TAG, "httpPost() OK entity=" + (entity != null));
				if (entity != null) {
					byte[] buffer = new byte[128];
					int len = entity.getContent().read(buffer);
					if (len > 0) return new String(buffer, 0, len);
				}
			} else {
				Log.w(TAG, "httpPost() NOT OK msg="
								+ EntityUtils.toString(entity, "UTF-8")
								+ httpResponse.getStatusLine().getStatusCode()
								+ "ERROR");
			}
		} catch (Throwable t) {
			Log.w(TAG, "httpPost() error", t);
		}

		return null;
	}

	private Runnable mPostLocationsJob = new Runnable() {
		@Override
		public void run() {
			mHandler.removeCallbacks(this);
			long now = System.currentTimeMillis();
			if ((now - mLastSendLocTime) >= POST_LOCATIONS_INTERVAL) {
				String latlngs = mLatlngs.toString();
				if (postLocationsLocal(latlngs)) {
					mLatlngs = new StringBuffer();
					mLastSendLocTime = now;
				}
			} else {
				mHandler.postDelayed(this, (now - mLastSendLocTime));
			}
		}
	};

	private class PostActionJob implements Runnable {
		private byte mAction;

		public PostActionJob(byte action) {
			mAction = action;
		}

		@Override
		public void run() {
			long now = System.currentTimeMillis();
			long last = mLastSendTimes.get(mAction);
			if ((now - last) >= SEND_DOOR_MESSAGE_INTERVAL) {
				mLastSendTimes.put(mAction, now);
				if (postActionLocal(mAction)) {
					// mLastSendTimes.put(mAction, now);
				} else {
					mHandler.postDelayed(this, 5000);
				}
			}
		}
	};

}