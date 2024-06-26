/*
 * Spartan Time Lapse Recorder - Minimalistic android time lapse recording app
 * Copyright (C) 2014  Andreas Rohner
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.andreasrohner.spartantimelapserec;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.widget.Toast;

import at.andreasrohner.spartantimelapserec.data.RecMode;
import at.andreasrohner.spartantimelapserec.data.RecSettings;
import at.andreasrohner.spartantimelapserec.preference.DateTimePreference;
import at.andreasrohner.spartantimelapserec.preference.IntervalPickerPreference;
import at.andreasrohner.spartantimelapserec.preference.IpInformation;
import at.andreasrohner.spartantimelapserec.preference.SeekBarPreference;
import at.andreasrohner.spartantimelapserec.rest.RestService;
import at.andreasrohner.spartantimelapserec.sensor.CameraSettings;

public class SettingsCommon implements OnSharedPreferenceChangeListener, SeekBarPreference.OnFormatOutputValueListener {

	private Context context;

	private CameraSettings cameraSettings;

	private ListPreference prefFrameSize;

	private ListPreference prefFrameRate;

	private ListPreference prefCamera;

	private ListPreference prefRecMode;

	private SeekBarPreference prefInitialDelay;

	private IntervalPickerPreference prefCaptureRate;

	private SeekBarPreference prefJpegQuality;

	private DateTimePreference prefScheduleRec;

	private SeekBarPreference prefStopRecAfter;

	private SeekBarPreference prefExposureComp;

	private SeekBarPreference prefZoom;

	private SeekBarPreference prefCameraInitDelay;

	private SeekBarPreference prefCameraTriggerDelay;

	private EditTextPreference prefVideoEncodingBitRate;

	private SwitchPreference prefFlash;

	private IpInformation prefIpInformation;

	private int calcGcd(int a, int b) {
		if (b == 0)
			return a;
		return calcGcd(b, a % b);
	}

	private void setFrameSizes(final SharedPreferences prefs) {
		int camId = RecSettings.getInteger(prefs, "pref_camera", 0);
		String defSize = prefs.getString("pref_frame_size", "1920x1080");

		final List<int[]> sizes;
		switch (RecSettings.getRecMode(prefs, "pref_rec_mode", RecMode.VIDEO_TIME_LAPSE)) {
			case IMAGE_TIME_LAPSE:
				sizes = cameraSettings.getPictureSizes(prefs, camId);
				break;
			case VIDEO:
				sizes = cameraSettings.getFrameSizes(prefs, camId, false);
				break;
			default:
				sizes = cameraSettings.getFrameSizes(prefs, camId, true);
				break;
		}

		final ArrayList<String> sizesList = new ArrayList<String>();
		final ArrayList<String> sizesListVal = new ArrayList<String>();
		int defInd = sizes.size() - 1;

		for (int[] size : sizes) {
			int gcd = calcGcd(size[0], size[1]);
			String value = size[0] + "x" + size[1];
			if (defSize.equals(value))
				defInd = sizesListVal.size();

			sizesList.add(value + " (" + (size[0] / gcd) + ":" + (size[1] / gcd) + ")");
			sizesListVal.add(value);

		}

		final int index = defInd;
		Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			@Override
			public void run() {
				prefFrameSize.setEntries(sizesList.toArray(new String[sizesList.size()]));
				prefFrameSize.setEntryValues(sizesListVal.toArray(new String[sizesListVal.size()]));

				if (index >= 0 && sizesList.size() > 0) {
					prefFrameSize.setValueIndex(index);
					prefFrameSize.setSummary(sizesList.get(index));
				}

				updatePrefStatus(prefs);
			}
		});
	}

	private void setFrameRates(final SharedPreferences prefs) {
		int camId = RecSettings.getInteger(prefs, "pref_camera", 0);
		String defFps = prefs.getString("pref_frame_rate", "30");

		final List<Integer> fpsIntList = cameraSettings.getFrameRates(prefs, camId);
		final ArrayList<String> fpsList = new ArrayList<String>();
		final ArrayList<String> fpsListVal = new ArrayList<String>();
		int defInd = fpsIntList.size() - 1;

		for (Integer fpsInt : fpsIntList) {
			String fps = fpsInt.toString();
			if (defFps.equals(fps))
				defInd = fpsList.size();

			fpsListVal.add(fps);
			fpsList.add(fps + " " + context.getString(R.string.format_fps));
		}

		final int index = defInd;
		Handler handler = new Handler(Looper.getMainLooper());
		handler.post(new Runnable() {
			@Override
			public void run() {
				prefFrameRate.setEntries(fpsList.toArray(new String[fpsList.size()]));
				prefFrameRate.setEntryValues(fpsListVal.toArray(new String[fpsListVal.size()]));

				if (index >= 0 && fpsList.size() > 0) {
					prefFrameRate.setValueIndex(index);
					prefFrameRate.setSummary(fpsList.get(index));
				}
				updatePrefStatus(prefs);
			}
		});
	}

	private void delayedInit(final SharedPreferences prefs) {
		prefFrameRate.setEnabled(false);
		prefFrameSize.setEnabled(false);

		startStopRestApiServer();

		new Thread(new Runnable() {
			@Override
			public void run() {
				setFrameRates(prefs);
				setFrameSizes(prefs);
			}
		}).start();
	}

	private void setCameras(SharedPreferences prefs) {
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		int cameraCount = Camera.getNumberOfCameras();

		String[] camList = new String[cameraCount];
		String[] camListInd = new String[cameraCount];

		for (int i = 0; i < cameraCount; ++i) {
			Camera.getCameraInfo(i, cameraInfo);
			String item = context.getString(R.string.pref_camera_camera) + " " + i + " (";

			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
				item += context.getString(R.string.pref_camera_front);
			else
				item += context.getString(R.string.pref_camera_back);
			item += ")";

			camList[i] = item;
			camListInd[i] = String.valueOf(i);
		}

		prefCamera.setEntries(camList);
		prefCamera.setEntryValues(camListInd);

		int camId = RecSettings.getInteger(prefs, "pref_camera", 0);
		prefCamera.setSummary(camList[camId]);
	}

	private void updatePrefStatus(SharedPreferences prefs) {
		switch (RecSettings.getRecMode(prefs, "pref_rec_mode", RecMode.VIDEO_TIME_LAPSE)) {
			case IMAGE_TIME_LAPSE:
				prefFrameRate.setEnabled(false);
				prefVideoEncodingBitRate.setEnabled(false);
				prefCaptureRate.setEnabled(true);
				prefJpegQuality.setEnabled(true);
				prefFlash.setEnabled(true);
				break;
			case VIDEO:
				prefFrameRate.setEnabled(true);
				prefVideoEncodingBitRate.setEnabled(true);
				prefCaptureRate.setEnabled(false);
				prefJpegQuality.setEnabled(false);
				prefFlash.setEnabled(false);
				break;
			default:
				prefFrameRate.setEnabled(true);
				prefVideoEncodingBitRate.setEnabled(true);
				prefCaptureRate.setEnabled(true);
				prefJpegQuality.setEnabled(false);
				prefFlash.setEnabled(false);
				break;
		}

		// disable the empty lists
		if (prefFrameRate.getEntries() == null || prefFrameRate.getEntries().length == 0) {
			prefFrameRate.setSummary(null);
			prefFrameRate.setEnabled(false);
		}

		if (prefFrameSize.getEntries() == null || prefFrameSize.getEntries().length == 0) {
			prefFrameSize.setSummary(null);
			prefFrameSize.setEnabled(false);
		} else {
			prefFrameSize.setEnabled(true);
		}

		if (prefCamera.getEntries() == null || prefCamera.getEntries().length == 0) {
			prefCamera.setSummary(null);
			prefCamera.setEnabled(false);
		}
	}

	private void setRecMode(SharedPreferences prefs) {

		CharSequence entry = prefRecMode.getEntry();
		if (entry != null) {
			prefRecMode.setSummary(entry);
		}
	}

	private String formatTime(int millis) {
		if (millis < 1000)
			return millis + " " + context.getString(R.string.time_format_msec);

		double secs = ((double) (millis % 60000)) / 1000;
		String formatSec = " " + context.getString(R.string.time_format_sec);
		String formatSecs = " " + context.getString(R.string.time_format_secs);
		DecimalFormat df = new DecimalFormat("#.##");

		if (millis >= 1000 && millis < 60000)
			return df.format(secs) + ((secs == 1) ? formatSec : formatSecs);

		int intSecs = millis % 60000 / 1000;
		int mins = (millis % 3600000) / 1000 / 60;
		int hours = (millis / 1000 / 60 / 60);

		String formatMin = " " + context.getString(R.string.time_format_min);
		String formatMins = " " + context.getString(R.string.time_format_mins);
		String formatHour = " " + context.getString(R.string.time_format_hour);
		String formatHours = " " + context.getString(R.string.time_format_hours);
		String res = "";
		if (hours == 1)
			res += hours + formatHour;
		else if (hours > 0)
			res += hours + formatHours;

		if (mins == 1)
			res += " " + mins + formatMin;
		else if (mins > 0)
			res += " " + mins + formatMins;

		if (intSecs == 1)
			res += " " + intSecs + formatSec;
		else if (intSecs > 0)
			res += " " + intSecs + formatSecs;

		return res;
	}

	@Override
	public String onFormatOutputValue(int value, String suffix) {
		if ("ms".equals(suffix))
			return formatTime(value);
		else if ("min".equals(suffix)) {
			if (value >= 47 * 60)
				return context.getString(R.string.pref_infinite);
			return formatTime(value * 1000 * 60);
		}
		return null;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
		if (key.equals("pref_camera")) {
			CharSequence entry = prefCamera.getEntry();
			if (entry != null) {
				prefCamera.setSummary(entry);
			}
			setFrameRates(prefs);
			setFrameSizes(prefs);
			setExposureCompRange(prefs);
			setZoomRange(prefs);
			prefs.edit().putInt("pref_exposurecomp", 0).apply();
		} else if (key.equals("pref_mute_shutter")) {
			boolean mute = prefs.getBoolean(key, false);
			//ToDo Show dialog and explain that this is needed to allow muting the phone
			NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			if (!notificationManager.isNotificationPolicyAccessGranted() && mute) {
				Intent intent = new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(intent);
			}
		} else if (key.equals("pref_rec_mode")) {
			setRecMode(prefs);
			setFrameSizes(prefs);
		} else if (key.equals("pref_capture_rate")) {
			prefCaptureRate.setSummary(formatTime(prefCaptureRate.getmValue()));
		} else if (key.equals("pref_initial_delay")) {
			prefInitialDelay.setSummary(formatTime(prefInitialDelay.getmValue()));
		} else if (key.equals("pref_jpeg_quality")) {
			prefJpegQuality.setSummary(prefJpegQuality.getmValue() + " %");
		} else if (key.equals("pref_frame_size")) {
			prefFrameSize.setSummary(prefFrameSize.getEntry());
		} else if (key.equals("pref_frame_rate")) {
			prefFrameRate.setSummary(prefFrameRate.getEntry());
		} else if (key.equals("pref_schedule_recording")) {
			prefScheduleRec.setSummary(prefScheduleRec.formatDateTime());
			RecSettings settings = new RecSettings();
			settings.load(context, PreferenceManager.getDefaultSharedPreferences(context));
			if (settings.isSchedRecEnabled() && settings.getSchedRecTime() > System.currentTimeMillis()) {
				Intent intent = new Intent(context, ForegroundService.class);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					context.startForegroundService(intent);
				} else {
					context.startService(intent);
				}
			} else {
				Intent intent = new Intent(context, ForegroundService.class);
				intent.setAction(ForegroundService.ACTION_STOP_SERVICE);
				context.startService(intent);
			}
		} else if (key.equals("pref_stop_recording_after")) {
			prefStopRecAfter.setSummary(onFormatOutputValue(prefStopRecAfter.getmValue(), "min"));
		} else if (key.equals("pref_exposurecomp")) {
			prefExposureComp.setSummary(Integer.toString(prefExposureComp.getmValue()));
		} else if (key.equals("pref_zoom")) {
			prefZoom.setSummary(Integer.toString(prefZoom.getmValue()));
		} else if (key.equals("pref_camera_init_delay")) {
			prefCameraInitDelay.setSummary(formatTime(prefCameraInitDelay.getmValue()));
		} else if (key.equals("pref_camera_trigger_delay")) {
			prefCameraTriggerDelay.setSummary(formatTime(prefCameraTriggerDelay.getmValue()));
		} else if (key.equals("pref_video_encoding_br")) {
			if (RecSettings.getInteger(prefs, "pref_video_encoding_br", 0) == 0) {  //reset to undefined, so hint is shown again
				SharedPreferences.Editor editor = prefs.edit();
				editor.remove("pref_video_encoding_br");
				editor.commit();
				prefVideoEncodingBitRate.setText("");  //show hint again
			}
			prefVideoEncodingBitRate.setSummary(RecSettings.getInteger(prefs, "pref_video_encoding_br", 0) == 0 ? context.getString(R.string.encode_best) : context.getString(R.string.format_bps, prefs.getString("pref_video_encoding_br", "0")));
		} else if (key.equals("pref_restapi_enabled")) {
			startStopRestApiServer();
		} else if (key.equals("pref_restapi_port")) {
			prefIpInformation.updateData();
		}

		updatePrefStatus(prefs);
	}

	/**
	 * Start or stop REST API Server, as set by the preference 'pref_restapi_enabled'.
	 * If already in the correct state, it does nothing.
	 */
	private void startStopRestApiServer() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		boolean restApiEnabled = prefs.getBoolean("pref_restapi_enabled", false);

		if (RestService.isRunning() == restApiEnabled) {
			// The service and settings are in the same state - nothing to do
			return;
		}

		if (restApiEnabled) {
			Intent intent = new Intent(context, RestService.class);
			if (!RestService.isRunning()) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
					context.startForegroundService(intent);
				} else {
					context.startService(intent);
				}
			}

			Toast.makeText(context, context.getText(R.string.info_restapi_started), Toast.LENGTH_SHORT).show();
		} else {
			Intent intent = new Intent(context, RestService.class);
			context.stopService(intent);

			Toast.makeText(context, context.getText(R.string.info_restapi_stopped), Toast.LENGTH_SHORT).show();
		}
	}

	public void onCreate(Context context, PreferenceScreen screen) {
		this.context = context;
		SharedPreferences prefs = screen.getSharedPreferences();
		cameraSettings = new CameraSettings();
		cameraSettings.prefetch(prefs);
		prefVideoEncodingBitRate = (EditTextPreference) screen.findPreference("pref_video_encoding_br");
		prefFrameSize = (ListPreference) screen.findPreference("pref_frame_size");
		prefFrameRate = (ListPreference) screen.findPreference("pref_frame_rate");
		prefCamera = (ListPreference) screen.findPreference("pref_camera");
		prefRecMode = (ListPreference) screen.findPreference("pref_rec_mode");
		prefCaptureRate = (IntervalPickerPreference) screen.findPreference("pref_capture_rate");
		prefJpegQuality = (SeekBarPreference) screen.findPreference("pref_jpeg_quality");
		prefInitialDelay = (SeekBarPreference) screen.findPreference("pref_initial_delay");
		prefScheduleRec = (DateTimePreference) screen.findPreference("pref_schedule_recording");
		prefStopRecAfter = (SeekBarPreference) screen.findPreference("pref_stop_recording_after");
		prefExposureComp = (SeekBarPreference) screen.findPreference("pref_exposurecomp");
		prefZoom = (SeekBarPreference) screen.findPreference("pref_zoom");
		prefCameraInitDelay = (SeekBarPreference) screen.findPreference("pref_camera_init_delay");
		prefCameraTriggerDelay = (SeekBarPreference) screen.findPreference("pref_camera_trigger_delay");
		prefFlash = (SwitchPreference) screen.findPreference("pref_flash");
		prefIpInformation = (IpInformation) screen.findPreference("pref_restapi_information");

		setZoomRange(prefs);
		setExposureCompRange(prefs);
		setCameras(prefs);
		setRecMode(prefs);
		// Opening the camera object to
		// fetch the params can take some time
		delayedInit(prefs);

		int value = prefs.getInt("pref_capture_rate", -1);
		if (value != -1)
			prefCaptureRate.setSummary(formatTime(value));

		prefInitialDelay.setOnFormatOutputValueListener(this);
		value = prefs.getInt("pref_initial_delay", -1);
		if (value != -1)
			prefInitialDelay.setSummary(formatTime(value));

		value = prefs.getInt("pref_jpeg_quality", -1);
		if (value != -1)
			prefJpegQuality.setSummary(value + " %");

		prefScheduleRec.setSummary(prefScheduleRec.formatDateTime());

		prefStopRecAfter.setOnFormatOutputValueListener(this);
		value = prefs.getInt("pref_stop_recording_after", -1);
		if (value != -1)
			prefStopRecAfter.setSummary(onFormatOutputValue(value, "min"));
		prefVideoEncodingBitRate.setSummary(RecSettings.getInteger(prefs, "pref_video_encoding_br", 0) == 0 ? context.getString(R.string.encode_best) : context.getString(R.string.format_bps, prefs.getString("pref_video_encoding_br", "0")));
		prefExposureComp.setSummary(Integer.toString(prefExposureComp.getmValue()));
		prefZoom.setSummary(Integer.toString(prefZoom.getmValue()));
		prefCameraInitDelay.setSummary(formatTime(prefCameraInitDelay.getmValue()));
		prefCameraTriggerDelay.setSummary(formatTime(prefCameraTriggerDelay.getmValue()));

		updatePrefStatus(prefs);
	}

	private void setZoomRange(SharedPreferences prefs) {
		int camId = RecSettings.getInteger(prefs, "pref_camera", 0);
		prefZoom.setMinValue(0);
		prefZoom.setMaxValue(cameraSettings.getMaxZoom(camId));
	}

	private void setExposureCompRange(SharedPreferences prefs) {
		int camId = RecSettings.getInteger(prefs, "pref_camera", 0);
		prefExposureComp.setMinValue(cameraSettings.getMinExposureCompensation(camId));
		prefExposureComp.setMaxValue(cameraSettings.getMaxExposureCompensation(camId));
	}

	public void onResume(PreferenceScreen screen) {
		screen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
		prefIpInformation.updateData();
	}

	public void onPause(PreferenceScreen screen) {
		screen.getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
	}

	static public void setDefaultValues(Context context, SharedPreferences prefs) {
		PreferenceManager.setDefaultValues(context, R.xml.preferences, false);

	}
}
