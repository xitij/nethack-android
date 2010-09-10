package com.nethackff;

import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class NetHackInstaller
{
	String appDir;

	protected AssetManager assetManager;

	public String getAppDir()
	{
		return appDir;
	}

	public NetHackInstaller(AssetManager assetman)
	{
		assetManager = assetman;	
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

}
