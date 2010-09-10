package com.nethackff;

import android.content.res.AssetManager;
import android.content.SharedPreferences;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class NetHackInstaller
{
	// TODO: Check if this ended up being necessary.
	NetHackApp activityNetHackApp;

	String appDir;

	InstallThread installThread;

	int installProgress = 0;

	protected AssetManager assetManager;

	public boolean isExistingInstallationUpToDate()
	{
		return compareAsset("version.txt");
	}


	public boolean doesExistingInstallationExist()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityNetHackApp.getBaseContext());
		if(prefs.contains("InstalledOnExternalMemory"))
		{
			return true;
		}
		return false;
	}
	

	public void setAppDir(boolean installexternal)
	{
		if(installexternal)
		{
			File externalFile = activityNetHackApp.getExternalFilesDir(null);
			// TODO: Deal with case of externalFile = NULL (unexpected SD card unavailability change)
			appDir = externalFile.getAbsolutePath();
		}
		else
		{
			appDir = activityNetHackApp.getFilesDir().getAbsolutePath(); 
		}
		activityNetHackApp.appDir = appDir;
	}
		
	public String getAppDir()
	{
		return appDir;
	}

	public String getNetHackDir()
	{
		return getAppDir() + "/nethackdir"; 
	}

	public NetHackInstaller(AssetManager assetman, NetHackApp activitynethackapp, boolean launchthread)
	{
		assetManager = assetman;
		activityNetHackApp = activitynethackapp;

		if(launchthread)
		{
			installThread = new InstallThread();
			installThread.start();
		}
	}

	public boolean compareAsset(String assetname)
	{
		boolean match = false;

		String destname = getAppDir() + "/" + assetname;
		File newasset = new File(destname);
		try
		{
			BufferedInputStream out = new BufferedInputStream(new FileInputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(assetManager.open(assetname));
			match = true;
			while(true)
			{
				int b = in.read();
				int c = out.read();
				if(b != c)
				{
					match = false;
					break;
				}
				if(b == -1)
				{
					break;
				}
			}
			out.close();
			in.close();
		}
		catch (IOException ex)
		{
			match = false;
		}
		return match;
	}

	public void copyAsset(String assetname)
	{
		copyAsset(assetname, getAppDir() + "/" + assetname);
	}

	public void copyAsset(String srcname, String destname)
	{
		File newasset = new File(destname);
		try
		{
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(assetManager.open(srcname));
			int b;
			while((b = in.read()) != -1)
			{
				out.write(b);
			}
			out.flush();
			out.close();
			in.close();
		}
		catch (IOException ex)
		{
			//mainView.terminal.write("Failed to copy file '" + srcname + "'.\n");
		}
	}

	public void copyNetHackData()
	{
		String assets[] = null;
		try
		{
			assets = assetManager.list("nethackdir");

			for(int i = 0; i < assets.length; i++)
			{
				String destname = getNetHackDir() + "/" + assets[i]; 
				copyAsset("nethackdir/" + assets[i], destname);
				NetHackFileHelpers.chmod(destname, 0666);

				reportProgress();
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	public void reportProgress()
	{
		installProgress++;
		activityNetHackApp.handler.sendMessage(Message.obtain(activityNetHackApp.handler, NetHackApp.MSG_INSTALL_PROGRESS, installProgress, 0, null));
	}

	public int beginInstall()
	{
		int totalprogress = 0;
		String assets[] = null;
		try
		{
			assets = assetManager.list("nethackdir");
			totalprogress += assets.length;
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}

		totalprogress += 8;	// MAGIC!

		return totalprogress;

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

		copyAsset("version.txt");
		reportProgress();
		copyAsset("NetHack.cnf", nethackdir + "/.nethackrc");
		reportProgress();
		copyAsset("charset_amiga.cnf", nethackdir + "/charset_amiga.cnf");
		reportProgress();
		copyAsset("charset_ibm.cnf", nethackdir + "/charset_ibm.cnf");
		reportProgress();
		copyAsset("charset_128.cnf", nethackdir + "/charset_128.cnf");
		reportProgress();

		activityNetHackApp.handler.sendEmptyMessage(NetHackApp.MSG_INSTALL_END);
	}

	public void install(boolean installexternal)
	{
		setAppDir(installexternal);

		int finalProgress = beginInstall();
		activityNetHackApp.handler.sendMessage(Message.obtain(activityNetHackApp.handler, NetHackApp.MSG_INSTALL_BEGIN, finalProgress, 0, null));
		performInstall();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityNetHackApp.getBaseContext());
		SharedPreferences.Editor prefsEditor = prefs.edit();
		prefsEditor.putBoolean("InstalledOnExternalMemory", installexternal);
		prefsEditor.commit();
	}

	public class InstallThread extends Thread implements Runnable
	{
		public synchronized void setDialogResponse(NetHackApp.DialogResponse r)
		{
			dialogResponse = r;
		}

		public synchronized NetHackApp.DialogResponse getDialogResponse()
		{
			return dialogResponse;
		}

		protected NetHackApp.DialogResponse dialogResponse = NetHackApp.DialogResponse.Invalid;

		public boolean isExistingInstallationAvailable()
		{
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityNetHackApp.getBaseContext());
			if(prefs.getBoolean("InstalledOnExternalMemory", false))
			{
Log.i("NetHackDbg", "isExistingInstallationAvailable - on external memory");
				if(!NetHackFileHelpers.checkExternalStorageReady())
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
			while(getDialogResponse() == NetHackApp.DialogResponse.Invalid)
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

		public NetHackApp.DialogResponse askUserIfReinstallOnInternalMemory()
		{
			setDialogResponse(NetHackApp.DialogResponse.Invalid);
			activityNetHackApp.handler.sendEmptyMessage(NetHackApp.MSG_SHOW_DIALOG_EXISTING_EXTERNAL_INSTALLATION_UNAVAILABLE);
			waitForResponse();
			return dialogResponse;
		}
		public NetHackApp.DialogResponse askUserIfInstallOnInternalMemory()
		{
			setDialogResponse(NetHackApp.DialogResponse.Invalid);
			activityNetHackApp.handler.sendEmptyMessage(NetHackApp.MSG_SHOW_DIALOG_SD_CARD_NOT_FOUND);
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
					NetHackApp.DialogResponse r = askUserIfReinstallOnInternalMemory();
					if(r == NetHackApp.DialogResponse.Yes)
					{
						install(false);
					}
					else if(r == NetHackApp.DialogResponse.No)
					{
						return false;
					}
				}
				
				SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activityNetHackApp.getBaseContext());
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
				while(installexternally && !NetHackFileHelpers.checkExternalStorageReady())
				{
					NetHackApp.DialogResponse r = askUserIfInstallOnInternalMemory();
					if(r == NetHackApp.DialogResponse.Yes)
					{
						installexternally = false;
					}
					else if(r == NetHackApp.DialogResponse.No)
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
				activityNetHackApp.handler.sendEmptyMessage(NetHackApp.MSG_LAUNCH_GAME);
			}
			else
			{
				activityNetHackApp.handler.sendEmptyMessage(NetHackApp.MSG_QUIT);
			}
		}
	};
}
