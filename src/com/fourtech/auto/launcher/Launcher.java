package com.fourtech.auto.launcher;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import com.fourtech.automonitor.MonitorService;

public class Launcher extends Activity {

	private static String SERIALNO = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SystemProperties.set("ctl.start", "pctool_recovery:altimeter");
		// ((ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE)).setAirplaneMode(false);
		setLocationEnabled(true);

		Intent service = new Intent();
		service.setClass(this, MonitorService.class);
		startService(service);
		Log.i("AutoLauncher", "start MonitorService");
	}

	private boolean setLocationEnabled(boolean enabled) {
		final ContentResolver cr = getContentResolver();
		int mode = enabled
				? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
				: Settings.Secure.LOCATION_MODE_OFF;

		sendBroadcast(
				new Intent("com.android.settings.location.MODE_CHANGING").putExtra("NEW_MODE", mode),
				android.Manifest.permission.WRITE_SECURE_SETTINGS);

		return Settings.Secure.putInt(cr, Settings.Secure.LOCATION_MODE, mode);
	}

	public static String getSerialNo() {
		return (SERIALNO != null) ? SERIALNO : (SERIALNO = SystemProperties.get("ro.serialno", "venus001"));
	}

}