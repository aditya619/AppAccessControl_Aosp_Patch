package com.acl.appaccesscontrol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;

public class ListAppsActivity extends Activity {

	public final String PACKAGE_NAME_TEXT = "packageName";
	public final String APP_NAME_TEXT = "appName";
	public final String UID_TEXT = "uid";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_list_apps);

		final PackageManager pm = getPackageManager();

		List<ApplicationInfo> apps = pm
				.getInstalledApplications(PackageManager.GET_META_DATA);

		ListView lv = (ListView) findViewById(R.id.listView1);
		final List<Map<String, String>> listItems = new ArrayList<Map<String, String>>();
		for (ApplicationInfo appInfo : apps) {
			//if ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 1) {
				Map<String, String> item = new HashMap<String, String>();
				Integer uid = appInfo.uid;
				item.put(PACKAGE_NAME_TEXT, appInfo.packageName);
				item.put(APP_NAME_TEXT, (String) appInfo.loadLabel(pm));
				item.put(UID_TEXT, uid.toString());
				listItems.add(item);
			//}
		}

        Log.i("AppList", Arrays.toString(listItems.toArray()));
		int[] controlIds = { android.R.id.text1, android.R.id.text2 };
		String[] keys = { APP_NAME_TEXT, PACKAGE_NAME_TEXT };

		SimpleAdapter adapter = new SimpleAdapter(this, listItems,
				android.R.layout.simple_list_item_2, keys, controlIds);

		lv.setAdapter(adapter);
		lv.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				Map<String, String> listItem = listItems.get(position);
				// int[] gids = null;
				// try {
				// gids = pm.getPackageGids(listItem.get(PACKAGE_NAME_TEXT));
				// } catch (NameNotFoundException e) {
				// // TODO Auto-generated catch block
				// e.printStackTrace();
				// }
				// Toast.makeText(getApplicationContext(),
				// Arrays.toString(gids),
				// Toast.LENGTH_LONG).show();
				Intent intent = new Intent(ListAppsActivity.this,
						AppDetails.class);
				intent.putExtra(PACKAGE_NAME_TEXT,
						listItem.get(PACKAGE_NAME_TEXT));
                intent.putExtra(UID_TEXT,
						listItem.get(UID_TEXT));
				startActivity(intent);
			}

		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.list_apps, menu);
		return true;
	}

}
