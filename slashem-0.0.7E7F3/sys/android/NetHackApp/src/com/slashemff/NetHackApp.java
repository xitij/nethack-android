package com.slashemff;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class NetHackApp extends Activity
{
	public static boolean firstTime = true;

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if(firstTime)
		{
			Intent intent = new Intent(this, NetHackGameActivity.class);
			Bundle bundle = new Bundle();
			intent.putExtras(bundle);
			startActivity(intent);

			firstTime = false;
		}
	}
}
