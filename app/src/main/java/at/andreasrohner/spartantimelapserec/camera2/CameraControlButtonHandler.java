package at.andreasrohner.spartantimelapserec.camera2;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;
import at.andreasrohner.spartantimelapserec.R;
import at.andreasrohner.spartantimelapserec.camera2.pupcfg.PopupDialogMenu;
import at.andreasrohner.spartantimelapserec.camera2.wrapper.Camera2Wrapper;

/**
 * Handle buttons and button actions of the camera preview
 */
public class CameraControlButtonHandler implements PopupDialogBase.DialogResult {

	/**
	 * Activity
	 */
	private final AppCompatActivity activity;

	/**
	 * Preferences
	 */
	private final SharedPreferences prefs;

	/**
	 * Camera
	 */
	private Camera2Wrapper camera;

	/**
	 * AF/MF, Focus Control Button
	 */
	private ImageButton afmfButton;

	/**
	 * ISO Button
	 */
	private ImageButton isoButton;

	/**
	 * Brightness button
	 */
	private ImageButton exposureButton;

	/**
	 * Menu Button
	 */
	private ImageButton menuButton;

	/**
	 * Camera Configuration Listener
	 */
	private ConfigChangeListener configChangeListener;

	/**
	 * Autofocus config Popup Dialog
	 */
	private PopupDialogAfMf afMfDialog;

	/**
	 * Constructor
	 *
	 * @param activity Activity
	 */
	public CameraControlButtonHandler(AppCompatActivity activity) {
		this.activity = activity;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(activity);
	}

	/**
	 * @param configChangeListener Camera Configuration Listener
	 */
	public void setConfigChangeListener(ConfigChangeListener configChangeListener) {
		this.configChangeListener = configChangeListener;
	}

	/**
	 * Camera opened
	 *
	 * @param camera Camera
	 */
	public void cameraOpened(Camera2Wrapper camera) {
		this.camera = camera;

		this.afmfButton = (ImageButton) activity.findViewById(R.id.bt_afmf);
		this.afMfDialog = new PopupDialogAfMf(activity, camera);
		afMfDialog.setDialogResultListener(this);
		this.afmfButton.setOnClickListener(afMfDialog);

		this.isoButton = (ImageButton) activity.findViewById(R.id.bt_iso);
		PopupDialogIso isoDialog = new PopupDialogIso(activity, camera);
		isoDialog.setDialogResultListener(this);
		this.isoButton.setOnClickListener(isoDialog);

		this.exposureButton = (ImageButton) activity.findViewById(R.id.bt_brightness);
		PopupDialogExposureTime exposureDialog = new PopupDialogExposureTime(activity, camera);
		exposureDialog.setDialogResultListener(this);
		this.exposureButton.setOnClickListener(exposureDialog);

		this.menuButton = (ImageButton) activity.findViewById(R.id.bt_menu);
		PopupDialogMenu menuDialog = new PopupDialogMenu(activity, camera);
		menuDialog.setDialogResultListener(this);
		this.menuButton.setOnClickListener(menuDialog);

		updateButtonImage();
	}

	/**
	 * Update button according to the config
	 */
	private void updateButtonImage() {
		int iso = prefs.getInt("pref_camera_iso", -1);
		if (iso == -1) {
			this.isoButton.setImageResource(R.drawable.ic_cam_bt_iso);
		} else {
			this.isoButton.setImageResource(R.drawable.ic_cam_bt_iso_enabled);
		}

		long exposure = prefs.getLong("pref_camera_exposure", -1);
		int relExposure = prefs.getInt("pref_camera_exposure_rel", 0);
		if (exposure == -1 && relExposure == 0) {
			this.exposureButton.setImageResource(R.drawable.ic_cam_bt_brightness);
		} else {
			this.exposureButton.setImageResource(R.drawable.ic_cam_bt_brightness_enabled);
		}

		String afMode = prefs.getString("pref_camera_af_mode", "auto");
		if (!camera.isAfSupported()) {
			this.afmfButton.setImageResource(R.drawable.ic_cam_bt_afmf_disabled);
			this.afmfButton.setOnClickListener(v -> showNoAfConfigDialog());
		} else {
			this.afmfButton.setOnClickListener(afMfDialog);
			if ("auto".equals(afMode)) {
				this.afmfButton.setImageResource(R.drawable.ic_cam_bt_afmf);
			} else {
				this.afmfButton.setImageResource(R.drawable.ic_cam_bt_afmf_enabled);
			}
		}

		boolean menuButtonEnabled = false;
		String wb = prefs.getString("pref_camera_wb", "auto");
		if (!"auto".equals(wb)) {
			menuButtonEnabled = true;
		}

		String currentFlashMode = prefs.getString("pref_camera_flash", "off");
		if (!"off".equals(currentFlashMode)) {
			menuButtonEnabled = true;
		}

		if (menuButtonEnabled) {
			this.menuButton.setImageResource(R.drawable.ic_cam_bt_menu_enabled);
		} else {
			this.menuButton.setImageResource(R.drawable.ic_cam_bt_menu);
		}
	}

	/**
	 * Show information that the camera does not support AF Configuration
	 */
	private void showNoAfConfigDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(activity);
		builder.setTitle(R.string.no_af_title);
		builder.setMessage(R.string.no_af_description);

		builder.setPositiveButton(R.string.dialog_OK_button, null);
		builder.show();
	}

	@Override
	public void dialogFinished(boolean accepted, int flags) {
		updateButtonImage();
		if (configChangeListener != null) {
			configChangeListener.cameraConfigChanged(flags);
		}
	}

	/**
	 * Configuration has been changed
	 */
	public interface ConfigChangeListener {

		/**
		 * Camera Configuration has been changed
		 *
		 * @param flags Flags to trigger actions
		 */
		void cameraConfigChanged(int flags);
	}
}
