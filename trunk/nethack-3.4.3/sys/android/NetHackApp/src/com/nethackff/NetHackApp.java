package com.nethackff;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
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

	private Handler handler = new Handler()
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
					Intent intent = new Intent(NetHackApp.this, NetHackGameActivity.class);
					Bundle bundle = new Bundle();
					intent.putExtras(bundle);
					startActivity(intent);
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

	public int beginInstall()
	{
		int totalprogress = 0;
		AssetManager am = getResources().getAssets();
		String assets[] = null;
		try
		{
			assets = am.list("nethackdir");
			totalprogress += assets.length;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}

		totalprogress += 8;	// MAGIC!

		return totalprogress;

	}

	public void copyNetHackData()
	{
		AssetManager am = getResources().getAssets();
		String assets[] = null;
		try
		{
			assets = am.list("nethackdir");

			for(int i = 0; i < assets.length; i++)
			{
				String destname = getNetHackDir() + "/" + assets[i]; 
				installer.copyAsset("nethackdir/" + assets[i], destname);
				NetHackFileHelpers.chmod(destname, 0666);

				reportProgress();
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	NetHackInstaller installer;
	

	public String getAppDir()
	{
		return appDir;
	}

	public String getNetHackDir()
	{
		return getAppDir() + "/nethackdir"; 
	}

	int installProgress = 0;

	public void reportProgress()
	{
		installProgress++;
		handler.sendMessage(Message.obtain(handler, MSG_INSTALL_PROGRESS, installProgress, 0, null));
	}

	public void performInstall()
	{
		installProgress = 0;

		String nethackdir = getNetHackDir();

		NetHackFileHelpers.mkdir(nethackdir);
		reportProgress();
		NetHackFileHelpers.mkdir(nethackdir + "/save");
		reportProgress();

		copyNetHackData();
		reportProgress();

		installer.copyAsset("version.txt");
		reportProgress();
		installer.copyAsset("NetHack.cnf", nethackdir + "/.nethackrc");
		reportProgress();
		installer.copyAsset("charset_amiga.cnf", nethackdir + "/charset_amiga.cnf");
		reportProgress();
		installer.copyAsset("charset_ibm.cnf", nethackdir + "/charset_ibm.cnf");
		reportProgress();
		installer.copyAsset("charset_128.cnf", nethackdir + "/charset_128.cnf");
		reportProgress();

		handler.sendEmptyMessage(MSG_INSTALL_END);
	}

	enum DialogResponse
	{
		Invalid,
		Yes,
		No,
		Retry
	};

	public class InstallThread extends Thread implements Runnable
	{
		public synchronized void setDialogResponse(DialogResponse r)
		{
			dialogResponse = r;
		}

		public synchronized DialogResponse getDialogResponse()
		{
			return dialogResponse;
		}

		protected DialogResponse dialogResponse = DialogResponse.Invalid;

		public boolean checkExternalStorageReady()
		{
			String state = Environment.getExternalStorageState();
			if(Environment.MEDIA_MOUNTED.equals(state))
			{
				return true;
			}
			else
			{
				return false;
			}			
		}

		public boolean doesExistingInstallationExist()
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			if(prefs.contains("InstalledOnExternalMemory"))
			{
				return true;
			}
			return false;
		}
	
		public boolean isExistingInstallationAvailable()
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			if(prefs.getBoolean("InstalledOnExternalMemory", false))
			{
Log.i("NetHackDbg", "isExistingInstallationAvailable - on external memory");
				if(!checkExternalStorageReady())
				{
Log.i("NetHackDbg", "isExistingInstallationAvailable - external memory not ready");
					return false;
				}
Log.i("NetHackDbg", "isExistingInstallationAvailable - external memory ready?");
			}
			else
			{
Log.i("NetHackDbg", "isExistingInstallationAvailable - on internal memory");
			}
			return true;
		}

		public void waitForResponse()
		{
			while(getDialogResponse() == DialogResponse.Invalid)
			{
				try
				{
					//handler.sendEmptyMessage(0);
					Thread.sleep(100);
				}
				catch(InterruptedException e)
				{
					throw new RuntimeException(e.getMessage());
				}
			}
		}

		public boolean isExistingInstallationUpToDate()
		{
			return installer.compareAsset("version.txt");
		}

		public void setAppDir(boolean installexternal)
		{
			if(installexternal)
			{
				File externalFile = getExternalFilesDir(null);
				// TODO: Deal with case of externalFile = NULL (unexpected SD card unavailability change)
				appDir = externalFile.getAbsolutePath();
			}
			else
			{
				appDir = getFilesDir().getAbsolutePath(); 
			}
			installer.appDir = appDir;
		}

		public void install(boolean installexternal)
		{
			setAppDir(installexternal);

			int finalProgress = beginInstall();
			handler.sendMessage(Message.obtain(handler, MSG_INSTALL_BEGIN, finalProgress, 0, null));
			performInstall();

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			SharedPreferences.Editor prefsEditor = prefs.edit();
			prefsEditor.putBoolean("InstalledOnExternalMemory", installexternal);
			prefsEditor.commit();
		}

		public DialogResponse askUserIfReinstallOnInternalMemory()
		{
			setDialogResponse(DialogResponse.Invalid);
			handler.sendEmptyMessage(MSG_SHOW_DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE);
			waitForResponse();
			return dialogResponse;
		}
		public DialogResponse askUserIfInstallOnInternalMemory()
		{
			setDialogResponse(DialogResponse.Invalid);
			handler.sendEmptyMessage(MSG_SHOW_DIALOG_SD_CARD_NOT_FOUND);
			waitForResponse();
			return dialogResponse;
		}
		public boolean performInitialChecks()
		{
			// Check preferences to see if there is an existing installation.
			if(doesExistingInstallationExist())
			{
				// If there is an existing installation, check if it's available.
				while(!isExistingInstallationAvailable())
				{
					DialogResponse r = askUserIfReinstallOnInternalMemory();
					if(r == DialogResponse.Yes)
					{
						install(false);
					}
					else if(r == DialogResponse.No)
					{
						return false;
					}
				}
				
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
				boolean existinginstallationexternal = prefs.getBoolean("InstalledOnExternalMemory", false);

				setAppDir(existinginstallationexternal);

				// If available, check if up to date.
				if(isExistingInstallationUpToDate())
				{
					// If up to date, start.
					return true;
				}
				else
				{
Log.i("NetHackDbg", "Existing installation not up to date!");
					install(existinginstallationexternal);
					return true;
				}
			}
			else
			{
				boolean installexternally = true;
				while(installexternally && !checkExternalStorageReady())
				{
					DialogResponse r = askUserIfInstallOnInternalMemory();
					if(r == DialogResponse.Yes)
					{
						installexternally = false;
					}
					else if(r == DialogResponse.No)
					{
						return false;
					}
				}
				install(installexternally);
				return true;
			}
		}
		public void run()
		{
			if(performInitialChecks())
			{
				handler.sendEmptyMessage(MSG_LAUNCH_GAME);
			}
			else
			{
				handler.sendEmptyMessage(MSG_QUIT);
			}
		}
	};

	InstallThread installThread;

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
								installThread.setDialogResponse(DialogResponse.Retry);
							}
						})
						.setNeutralButton("Use Internal Memory", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installThread.setDialogResponse(DialogResponse.Yes);
							}
						})
						.setNegativeButton("Exit", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installThread.setDialogResponse(DialogResponse.No);
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
								installThread.setDialogResponse(DialogResponse.Retry);
							}
						})
						.setNeutralButton("Reinstall on Internal Memory", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installThread.setDialogResponse(DialogResponse.Yes);
								//dialog.cancel();
							}
						})
						.setNegativeButton("Exit", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								installThread.setDialogResponse(DialogResponse.No);
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


	static boolean firstTime = true;	// TODO: Try to get rid of.

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.install);

		installer = new NetHackInstaller(this.getAssets());

		// TODO
		if(firstTime)
		{
			installThread = new InstallThread();
			installThread.start();
			firstTime = false;
		}
	}
}
