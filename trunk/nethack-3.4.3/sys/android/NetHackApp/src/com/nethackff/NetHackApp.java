package com.nethackff;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Window;
import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
//import java.io.FileDescriptor;
//import java.io.FileInputStream;
//import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class NetHackApp extends Activity
{
	public NetHackGameActivity game;

	static final int MSG_SHOW_DIALOG_SD_CARD_NOT_FOUND = 100;	// TEMP
	static final int MSG_INSTALL_BEGIN = 101;
	static final int MSG_INSTALL_END = 102;
	static final int MSG_INSTALL_PROGRESS = 103;
	static final int MSG_SHOW_DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE = 104;
	static final int MSG_LAUNCH_GAME = 105;
	static final int MSG_QUIT = 106;

	static final int DIALOG_SD_CARD_NOT_FOUND = 0;
	static final int DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE = 1;
	static final int DIALOG_INSTALL_PROGRESS = 2;
	
	private ProgressDialog progressDialog = null;

	// TODO: Protect?
	public Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			switch(msg.what)
			{
				case MSG_SHOW_DIALOG_SD_CARD_NOT_FOUND:
Log.i("NetHackDbg", "MSG_SHOW_DIALOG_SD_CARD_NOT_FOUND");
					NetHackApp.this.showDialog(DIALOG_SD_CARD_NOT_FOUND);
					break;
				case MSG_SHOW_DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE:
Log.i("NetHackDbg", "MSG_SHOW_DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE");
					NetHackApp.this.showDialog(DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE);
					break;
				case MSG_INSTALL_BEGIN:
					NetHackApp.this.showDialog(DIALOG_INSTALL_PROGRESS);
					if(progressDialog != null)
					{
						progressDialog.setMax(msg.arg1);
					}
					break;
				case MSG_INSTALL_END:
					progressDialog = null;
					NetHackApp.this.dismissDialog(DIALOG_INSTALL_PROGRESS);
					break;
				case MSG_INSTALL_PROGRESS:
					if(progressDialog != null)
					{
						progressDialog.setProgress(msg.arg1);
					}
					break;
				case MSG_LAUNCH_GAME:
					Log.i("NetHackDbg", "MSG_LAUNCH_GAME");	// TEMP
// TODO: Generate exception if game already exists somehow?
					game = new NetHackGameActivity(NetHackApp.this);
					game.onCreate();
					game.onStart();
					game.onResume();
//					Intent intent = new Intent(NetHackApp.this, NetHackGameActivity.class);
//					Bundle bundle = new Bundle();
//					intent.putExtras(bundle);
//					startActivity(intent);
					break;
				case MSG_QUIT:
					Log.i("NetHackDbg", "MSG_LAUNCH_QUIT");	// TEMP
					// Is this the way to do it? TODO: Make sure thread exits cleanly and stuff.
					finish();
					break;
				default:
					// What?
					break;
			}
		}
	};

	String appDir;

	NetHackInstaller installer;
	

	public String getAppDir()
	{
		return appDir;
	}

	enum DialogResponse
	{
		Invalid,
		Yes,
		No,
		Retry
	};

	protected Dialog onCreateDialog(int id)
	{
	    Dialog dialog;
	    switch(id)
	    {
		    case DIALOG_SD_CARD_NOT_FOUND:
		    {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);  
				builder	.setMessage("The application normally stores data on the SD card, but there was a problem accessing it. Please choose an action.")
						.setCancelable(false)
						.setPositiveButton("Retry", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.Retry);
							}
						})
						.setNeutralButton("Use Internal Memory", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.Yes);
							}
						})
						.setNegativeButton("Exit", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.No);
								//NetHackApp.this.finish();
							}
						});
				dialog = builder.create();
				//alert.show();
				break;
	    	}
		    case DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE:
		    {
				AlertDialog.Builder builder = new AlertDialog.Builder(this);  
				builder	.setMessage("An existing installation was expected on the SD card, but it doesn't seem to be available. Please choose an action.")
						.setCancelable(false)
						.setPositiveButton("Retry", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.Retry);
							}
						})
						.setNeutralButton("Reinstall on Internal Memory", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.Yes);
								//dialog.cancel();
							}
						})
						.setNegativeButton("Exit", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installer.installThread.setDialogResponse(DialogResponse.No);
								//NetHackApp.this.finish();
							}
						});
				dialog = builder.create();
				//alert.show();
				break;
		    }
		    case DIALOG_INSTALL_PROGRESS:
				progressDialog = new ProgressDialog(this);
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progressDialog.setMessage("Installing at " + appDir);
				progressDialog.setCancelable(false);
				dialog = progressDialog;
				break;
		    default:
		        dialog = null;
		    	break;
	    }
	    return dialog;
    }


	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		// TODO: Make sure we remove the actual XML data for this.
		//setContentView(R.layout.install);

		installer = new NetHackInstaller(this.getAssets(), this, true);
	}

	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		if(game != null)
		{
			game.onConfigurationChanged(newConfig);
		}
	}

	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.layout.menu, menu);
		return true;
	}

	public void onDestroy()
	{
		if(game != null)
		{
			game.stopCommThread();
		}
		super.onDestroy();
		//TestShutdown();
	}

	public boolean onKeyDown(int keycode, KeyEvent event)
	{
		if(game != null)
		{
			return game.onKeyDown(keycode, event); 
		}
		else
		{
			return super.onKeyDown(keycode, event);	
		}
	}

	public boolean onKeyDownSuper(int keycode, KeyEvent event)
	{
		return super.onKeyDown(keycode, event);	
	}

	public boolean onKeyUp(int keycode, KeyEvent event)
	{
		if(game != null)
		{
			return game.onKeyUp(keycode, event); 
		}
		else
		{
			return super.onKeyUp(keycode, event);	
		}
	}

	public boolean onKeyUpSuper(int keycode, KeyEvent event)
	{
		return super.onKeyUp(keycode, event);	
	}

	public void onPause()
	{
		if(game != null)
		{
			if(game.jni.NetHackHasQuit() == 0)
			{
				Log.i("NetHack", "Auto-saving");
				if(game.jni.NetHackSave() != 0)
				{
					Log.i("NetHack", "Auto-save succeeded");
				}
				else
				{
					Log.w("NetHack", "Auto-save failed");
				}
			}		
			game.stopCommThread();
		}

		super.onPause();
	}

	public void onResume()
	{
		super.onResume();

		if(game != null)
		{
			game.onResume();
		}
	}

	public boolean onTouchEvent(MotionEvent me)
	{
		if(game != null)
		{
			return game.onTouchEvent(me);
		}
		else
		{
			return super.onTouchEvent(me);	
		}
	}

	public boolean onOptionsItemSelected(MenuItem item)
	{
		if(game != null)
		{
			return game.onOptionsItemSelected(item);
		}
		else
		{
			return super.onOptionsItemSelected(item);
		}
	}

}
