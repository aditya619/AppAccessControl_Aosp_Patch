package com.acl.appaccesscontrol;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.ServiceManager;
import android.os.IAppAccessService;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class AppDetails extends Activity {

	public static final String ANDROID_PERMISSION = "android.permission.";
	private String packageName = null;
    private int uid = -1;
	private List<String> blockedPermissions = null;
	private List<String> currentBlockedPermissions = null;
	private IAppAccessService mAppAccessService = null;
    private static final String TAG = "AppAccessControl";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_app_details);
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String packageName = extras.getString("packageName");
            int uid = Integer.parseInt(extras.getString("uid"));
            Log.i("UID-Packagename", packageName+"-"+uid);
			this.packageName = packageName;
            this.uid = uid;
			final PackageManager pm = getPackageManager();
            mAppAccessService = IAppAccessService.Stub.asInterface(ServiceManager.getService("AppAccess"));
			try {
				ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
				Drawable icon = pm.getApplicationIcon(app);
				String name = (String) pm.getApplicationLabel(app);

				ImageView imageview = (ImageView) findViewById(R.id.imageView1);
				imageview.setImageDrawable(icon);

				TextView appName = (TextView) findViewById(R.id.textView1);
				appName.setText(name);

				PackageInfo packageInfo = pm.getPackageInfo(packageName,
						PackageManager.GET_PERMISSIONS);

				int[] gids = null;
				try {
					gids = pm.getPackageGids(packageName);
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				Toast.makeText(getApplicationContext(), Arrays.toString(gids),
						Toast.LENGTH_LONG).show();

				currentBlockedPermissions = mAppAccessService
						.getBlockedPermissions(this.packageName);
				Log.i(TAG,
						Arrays.toString(currentBlockedPermissions.toArray()));
				String[] requestedPermissions = packageInfo.requestedPermissions;
                Log.i(TAG, Arrays.toString(requestedPermissions));
				if (requestedPermissions != null) {
					for (String permission : requestedPermissions) {
						if (permission != null && permission.startsWith(ANDROID_PERMISSION)) {
							String permissionText = permission.replace(
									ANDROID_PERMISSION, "");
							addPermissionSwitch(permissionText);
						}
					}
				} else {
					Toast.makeText(getApplicationContext(),
							"App has no requested permissions!",
							Toast.LENGTH_LONG).show();
				}
			} catch (NameNotFoundException e) {
				toastError();
				e.printStackTrace();
			} catch (RemoteException e) {  
                toastError();
                Log.v(TAG, "Remote Exception calling service AppAccessService");  
                e.printStackTrace();
            }  

		} else {
			toastError();
		}
	}

	public void addPermissionSwitch(String permission) {
		Switch permissionSwitch = new Switch(this);
		permissionSwitch.setText(permission);
		// Set this by reading the current xml file
		if (currentBlockedPermissions.contains(permission)) {
			permissionSwitch.setChecked(false);
		} else {
			permissionSwitch.setChecked(true);
		}

		permissionSwitch
				.setOnCheckedChangeListener(new OnCheckedChangeListener() {

					@Override
					public void onCheckedChanged(CompoundButton buttonView,
							boolean isChecked) {
						String permission = (String) buttonView.getText();
						if (!isChecked) {
							if (blockedPermissions == null) {
								blockedPermissions = new ArrayList<String>();
							}
							blockedPermissions.add(permission);
							Log.i(TAG, "Adding " + permission
									+ " to blocked permissions");
						} else {
							if (blockedPermissions != null) {
								if (blockedPermissions.contains(permission)) {
									blockedPermissions.remove(permission);
								}
								if (currentBlockedPermissions.contains(permission)) {
									currentBlockedPermissions.remove(permission);
								}
							}
						}
					}
				});
		LinearLayout ll = (LinearLayout) findViewById(R.id.permissionLayout);
		LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
				LayoutParams.WRAP_CONTENT);
		ll.addView(permissionSwitch, lp);
	}

	public void toastError() {
		Toast t = Toast
				.makeText(
						getApplicationContext(),
						"Something is not right! Please go back to home screen and Try Again!!",
						Toast.LENGTH_LONG);
		t.show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.app_details, menu);
		return true;
	}

	public void saveBlockedPermissions(View v) {
		for (String currentPermission : currentBlockedPermissions) {
			blockedPermissions.add(currentPermission);
		}
		Log.i(TAG, Arrays.toString(blockedPermissions.toArray()));
        try {
		    currentBlockedPermissions = mAppAccessService
				    .getBlockedPermissions(this.packageName);
            mAppAccessService.updateBlockedPermissions(packageName, uid,
    				blockedPermissions);
        } catch (RemoteException e) {  
                toastError();
                Log.v(TAG, "Remote Exception calling service AppAccessService");  
                e.printStackTrace();
         } 
		blockedPermissions = new ArrayList<String>();
	}
}
