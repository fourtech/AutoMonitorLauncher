package com.fourtech.automonitor;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

public class LocationMonitor {
	private static final String TAG = "LocationMonitor";
	private static final boolean DEBUG = true;
	private static final int CHECK_INTERVAL = 1000 * 30;

	private Context mContext;
	private Location mLocation;
	private LocationManager mLm;
	private boolean mIsMoving = false;
	private AutoMonitorCloud mAutoMonitorCloud;
	private Handler mHandler = new Handler();

	private static LocationMonitor sMe;
	public static LocationMonitor get() { return sMe; }

	public LocationMonitor(Context context) {
		super();
		sMe = this;
		mContext = context;
		mAutoMonitorCloud = AutoMonitorCloud.get();
		mLm = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
	}

	public void start() {
		mLocation = mLm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		mAutoMonitorCloud.postLocation(mLocation);
		if (DEBUG) Log.i(TAG, "LastKnownLocation=" + mLocation);
		mLm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, mGpsLocationListener);
	}

	public void stop() {
		mLm.removeUpdates(mGpsLocationListener);
	}

	public boolean isMoving() {
		return mIsMoving;
	}

	public Location getLocation() {
		return mLocation;
	}

	public void postLastLocation() {
		mAutoMonitorCloud.postLocationImmediately(mLocation);
	}

	private LocationListener mGpsLocationListener = new LocationListener() {
		@Override
		public void onLocationChanged(Location location) {
			if (location != null) {
				float distance = 0.0f;
				Location oldLocation = mLocation;
				if (oldLocation == null
						|| (distance = oldLocation.distanceTo(location)) >= 30) {
					mLocation = new Location(location);
					mAutoMonitorCloud.postLocation(mLocation);
					if (distance > 0) {
						mIsMoving = true;
						mHandler.removeCallbacks(mCancelMovingJob);
						mHandler.postDelayed(mCancelMovingJob, 5000);
					}
				}
				if (DEBUG) Log.i(TAG, "onLocationChanged() distance=" + distance + ", location=" + mLocation);
			}
		}

		@Override
		public void onProviderDisabled(String provider) {
		}

		@Override
		public void onProviderEnabled(String provider) {
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}
	};

	private Runnable mCancelMovingJob = new Runnable() {
		@Override
		public void run() {
			mIsMoving = false;
		}
	};

	protected boolean isBetterLocation(Location location,
			Location currentBestLocation) {
		if (currentBestLocation == null) {
			// A new location is always better than no location
			return true;
		}

		if (location == null) {
			return false;
		}

		// Check whether the new location fix is newer or older
		long timeDelta = location.getTime() - currentBestLocation.getTime();
		boolean isSignificantlyNewer = timeDelta > CHECK_INTERVAL;
		boolean isSignificantlyOlder = timeDelta < -CHECK_INTERVAL;
		boolean isNewer = timeDelta > 0;

		// If it's been more than two minutes since the current location,
		// use the new location because the user has likely moved
		if (isSignificantlyNewer) {
			return true;
			// If the new location is more than two minutes older, it must be worse
		} else if (isSignificantlyOlder) {
			return false;
		}

		// Check whether the new location fix is more or less accurate
		int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
		boolean isLessAccurate = accuracyDelta > 0;
		boolean isMoreAccurate = accuracyDelta < 0;
		boolean isSignificantlyLessAccurate = accuracyDelta > 200;

		// Check if the old and new location are from the same provider
		boolean isFromSameProvider = isSameProvider(location.getProvider(), currentBestLocation.getProvider());

		// Determine location quality using a combination of timeliness and accuracy
		if (isMoreAccurate) {
			return true;
		}

		if (isNewer && !isLessAccurate) {
			return true;
		}

		if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
			return true;
		}

		return false;
	}

	/** Checks whether two providers are the same */
	private boolean isSameProvider(String provider1, String provider2) {
		if (provider1 == null) return (provider2 == null);
		return provider1.equals(provider2);
	}
}