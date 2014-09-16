/**	 Cycle Altanta, Copyright 2012 Georgia Institute of Technology
 *                                    Atlanta, GA. USA
 *
 *   @author Christopher Le Dantec <ledantec@gatech.edu>
 *   @author Anhong Guo <guoanhong15@gmail.com>
 *
 *   Updated/Modified for Atlanta's app deployment. Based on the
 *   CycleTracks codebase for SFCTA.
 *
 *   CycleTracks, Copyright 2009,2010 San Francisco County Transportation Authority
 *                                    San Francisco, CA, USA
 *
 * 	 @author Billy Charlton <billy.charlton@sfcta.org>
 *
 *   This file is part of CycleTracks.
 *
 *   CycleTracks is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   CycleTracks is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with CycleTracks.  If not, see <http://www.gnu.org/licenses/>.
 */

package edu.pdx.cecs.orcycle;

import java.util.Timer;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

public class RecordingService extends Service implements IRecordService, LocationListener {

	public final static String MODULE_TAG = "RecordingService";

	IRecordServiceListener recordServiceListener;
	LocationManager lm = null;

	// Bike bell variables
	static int BELL_FIRST_INTERVAL = 20;
	static int BELL_NEXT_INTERVAL = 5;
	Timer timer;
	SoundPool soundpool;
	int bikebell;
	final Handler mHandler = new Handler();
	final Runnable mRemindUser = new Runnable() {
		public void run() {
			remindUser();
		}
	};

	// Aspects of the currently recording trip
	Location lastLocation;
	float distanceMeters;   // The distance travelled in meters
	private TripData trip = null;

	private final static long MIN_TIME_BETWEEN_READINGS_MILLISECONDS = 1000;
	private final static float MIN_DISTANCE_BETWEEN_READINGS_METERS = 0.0f;
	private final static int MIN_DESIRED_ACCURACY = 19;
	private final static float MAX_BELIEVABLE_BIKE_SPEED_MPS = 26.8224f; // In meters per second = 60 Miles per hour

	public final static int STATE_IDLE = 0;
	public final static int STATE_RECORDING = 1;
	public final static int STATE_PAUSED = 2;
	public final static int STATE_FULL = 3;

	int state = STATE_IDLE;


	private final MyServiceBinder myServiceBinder = new MyServiceBinder();

	// *********************************************************************************
	// *                            Service Implementation
	// *********************************************************************************

	@Override
	public IBinder onBind(Intent arg0) {
		return myServiceBinder;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		soundpool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
		bikebell = soundpool.load(this.getBaseContext(), R.raw.bikebell, 1);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
	}

	public class MyServiceBinder extends Binder {

        /**
         * This function returns a reference to the bound service
         *
         * @return Reference to the bound service
         */
        public RecordingService getService() {
            return RecordingService.this;
        }

		public int getState() {
			return RecordingService.this.getState();
		}

		public void startRecording(TripData trip) {
			RecordingService.this.startRecording(trip);
		}

		public void cancelRecording() {
			RecordingService.this.cancelRecording();
		}

		public long finishRecording() {
			return RecordingService.this.finishRecording();
		}

		public long getCurrentTripID() {
			return RecordingService.this.getCurrentTripID();
		}

		public void pauseRecording() {
			RecordingService.this.pauseRecording();
		}

		public void resumeRecording() {
			RecordingService.this.resumeRecording();
		}

		public void reset() {
			RecordingService.this.reset();
		}

		public void setListener(IRecordServiceListener mia) {
			RecordingService.this.setListener(mia);
		}
	}

	// *********************************************************************************
	// *                       RecordingService Implementation
	// *********************************************************************************

	public int getState() {
		return state;
	}

	public long getCurrentTripID() {
		if (RecordingService.this.trip != null) {
			return RecordingService.this.trip.tripid;
		}
		return -1;
	}

	public TripData getCurrentTripData() {
		return trip;
	}

	public void setListener(IRecordServiceListener listener) {
		RecordingService.this.recordServiceListener = listener;
		//notifyListeners();
	}

	public void reset() {
		RecordingService.this.state = STATE_IDLE;
	}

	/**
	 * Start the recording process:
	 *  - reset trip variables
	 *  - enable location manager updates
	 *  - enable bike bell timer
	 */
	public void startRecording(TripData trip) {
		this.state = STATE_RECORDING;
		this.trip = trip;

		distanceMeters = 0.0f;
		lastLocation = null;

		// Add the notify bar and blinking light
		setNotification();

		// Start listening for GPS updates!
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,
				MIN_TIME_BETWEEN_READINGS_MILLISECONDS,
				MIN_DISTANCE_BETWEEN_READINGS_METERS, this);

		// Set up timer for bike bell
		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
		timer = new Timer();
		timer.schedule(new TimerTask() {
			@Override
			public void run() {
				mHandler.post(mRemindUser);
			}
		}, BELL_FIRST_INTERVAL * 60000, BELL_NEXT_INTERVAL * 60000);
	}

	/**
	 * Pause the recording process:
	 *  - disable location manager updates
	 *  - start recording paused time
	 */
	public void pauseRecording() {
		this.state = STATE_PAUSED;
		trip.startPause();
	}

	/**
	 * Resume recording process:
	 *  - enable location manager updates
	 *  - calculate time paused and save in trip data
	 */
	public void resumeRecording() {
		this.state = STATE_RECORDING;
		trip.finishPause();
	}

	/**
	 * End the recording process:
	 *  - disable location manager updates
	 *  - clear notifications
	 *  - if trip has any points, finalize data collection and push to
	 *    database, otherwise cancel trip and don't save any data
	 */
	public long finishRecording() {
		this.state = STATE_FULL;

		// Disable location manager updates
		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.removeUpdates(this);

		// Clear notifications
		clearNotifications();

		//
		if (trip.numpoints > 0) {
			trip.finish(); // makes some final calculations and pushed trip to the database
		}
		else {
			cancelRecording(); // TODO: isn't the tripid invalid at this point? Verify.
		}

		return trip.tripid;
	}

	public void cancelRecording() {
		if (trip != null) {
			trip.dropTrip();
		}

		lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		lm.removeUpdates(this);

		clearNotifications();
		this.state = STATE_IDLE;
	}

	// *********************************************************************************
	// *                     LocationListener Implementation
	// *********************************************************************************

	@Override
	public void onLocationChanged(Location location) {

		try {
			if (location != null) {

				// Stats should only be updated if accuracy is decent
				if (location.getAccuracy() <= MIN_DESIRED_ACCURACY) {

					if (lastLocation != null) {
						distanceMeters += lastLocation.distanceTo(location);
					}

					trip.addPointNow(location, System.currentTimeMillis(), distanceMeters);

					// Update the status page every time, if we can.
					// notifyListeners();
					lastLocation = location;
				}
			}
		}
		catch(Exception ex) {
			Log.e(MODULE_TAG, ex.getMessage());
		}
	}

	@Override
	public void onProviderDisabled(String arg0) {
	}

	@Override
	public void onProviderEnabled(String arg0) {
	}

	@Override
	public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
	}

	private void updateTripStats(Location newLocation) {

	}

	void notifyListeners() {
		if (null != recordServiceListener) {
			if (null == trip) {
				recordServiceListener.updateStatus(0.0f, 0.0f);
			}
			else {
				double duration = trip.getDuration() / 1000.0f;
				float avgSpeedMps = (float) ((duration > 1.0f) ? (distanceMeters / duration): 0);

				recordServiceListener.updateStatus(distanceMeters, avgSpeedMps);
			}
		}
	}

	// *********************************************************************************
	// *                     Notification Implementation
	// *********************************************************************************

	public void remindUser() {
		soundpool.play(bikebell, 1.0f, 1.0f, 1, 0, 1.0f);

		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.icon48;
		long when = System.currentTimeMillis();
		int minutes = (int) (when - trip.getStartTime()) / 60000;
		CharSequence tickerText = String.format("Still recording (%d min)", minutes);

		Notification notification = new Notification(icon, tickerText, when);
		notification.flags |= Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_SHOW_LIGHTS;
		notification.ledARGB = 0xffff00ff;
		notification.ledOnMS = 300;
		notification.ledOffMS = 3000;

		Context context = this;
		CharSequence contentTitle = "ORcycle recording";
		CharSequence contentText = "Tap to see your ongoing trip";
		Intent notificationIntent = new Intent(context, FragmentMainInput.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);
		final int RECORDING_ID = 1;
		mNotificationManager.notify(RECORDING_ID, notification);
	}

	private void setNotification() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		int icon = R.drawable.icon25;
		CharSequence tickerText = "Recording...";
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);

		notification.ledARGB = 0xffff00ff;
		notification.ledOnMS = 300;
		notification.ledOffMS = 3000;
		notification.flags = notification.flags
				| Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_INSISTENT
				| Notification.FLAG_NO_CLEAR;

		Context context = this;
		CharSequence contentTitle = "ORcycle recording";
		CharSequence contentText = "Tap to see your ongoing trip";
		Intent notificationIntent = new Intent(context, FragmentMainInput.class);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);
		notification.setLatestEventInfo(context, contentTitle, contentText,
				contentIntent);

		final int RECORDING_ID = 1;
		mNotificationManager.notify(RECORDING_ID, notification);
	}

	private void clearNotifications() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancelAll();

		if (timer != null) {
			timer.cancel();
			timer.purge();
		}
	}
}