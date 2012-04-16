package com.nethackff;

import java.io.File;
import java.io.IOException;

import com.nethackff.configeditor.ConfigEditor;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;

public class ConfigOptions extends ListActivity
{

	@Override
	public void onCreate(Bundle savedinstanceState)
	{
		super.onCreate(savedinstanceState);
		String[] configOptionEntries = getResources().getStringArray(R.array.ConfigOptionsEntries);
		ListAdapter adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, configOptionEntries);
		setListAdapter(adapter);
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id)
	{
		String clickedItem = (String) l.getItemAtPosition(position);
		if (clickedItem.equals(getString(R.string.edit_config)))
		{
			startEditConfigFileActivity();
			return;
		}
		if (clickedItem.equals(getString(R.string.import_config)))
		{
			configImportExportDialog(true);
			return;
		}
		if (clickedItem.equals(getString(R.string.export_config)))
		{
			configImportExportDialog(false);
			return;
		}
	}

	private void startEditConfigFileActivity()
	{
		File file = new File(getNetHackDir() + "/.nethackrc");
		Uri uri = Uri.fromFile(file);
		Intent editConfigIntent = new Intent(Intent.ACTION_VIEW ,uri);
		editConfigIntent.setDataAndType(uri, "text/plain"); 
		editConfigIntent.setClass(this, ConfigEditor.class);
		startActivity(editConfigIntent);
	}
	
	private void configImportExportDialog(final boolean cfgimport)
	{
		final AlertDialog.Builder dialog = new AlertDialog.Builder(this);
		if(cfgimport)
		{
			dialog.setTitle(getString(R.string.configimport_title));
			dialog.setMessage(getString(R.string.configimport_msg));
		}
		else
		{
			dialog.setTitle(getString(R.string.configexport_title));
			dialog.setMessage(getString(R.string.configexport_msg));
		}
		final EditText input = new EditText(this);
		input.getText().append(getString(R.string.config_defaultfile));

		dialog.setView(input);
		dialog.setPositiveButton(getString(R.string.dialog_OK), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface d, int whichbutton)
			{
				String value = input.getText().toString();
				configImportExport(value, cfgimport);
			}
		});
		dialog.setNegativeButton(getString(R.string.dialog_Cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface d, int whichbutton) {}
		});
		
		input.setOnKeyListener(new OnKeyListener()
		{
			public boolean onKey(View v, int keyCode, KeyEvent event)
			{
				if(keyCode == KeyEvent.KEYCODE_ENTER)
				{
					return true;
				}
				return false;
			}
		});

		dialog.show();

	}
	
	private void configImportExport(String filename, boolean cfgimport)
	{
		if(cfgimport)
		{
			configImport(filename);
		}
		else
		{
			configExport(filename);
		}
	}
	
	private void configImport(String inname)
	{
		try
		{
			NetHackFileHelpers.copyFileRaw(inname, getNetHackDir() + "/.nethackrc"); 

			AlertDialog.Builder alert = new AlertDialog.Builder(this);  
			alert.setTitle(getString(R.string.dialog_Success));
			alert.setMessage(getString(R.string.configimport_success) + " '" + inname + "'. " + getString(R.string.configimport_success2));
			alert.show();
		}
		catch(IOException e)
		{
			AlertDialog.Builder alert = new AlertDialog.Builder(this);  
			alert.setTitle(getString(R.string.dialog_Error));
			alert.setMessage(getString(R.string.configimport_failed) + " '" + inname + "'. " + getString(R.string.configimport_failed2));
			alert.show();
		}
	}
	
	private void configExport(String outname)
	{
		try
		{
			NetHackFileHelpers.copyFileRaw(getNetHackDir() + "/.nethackrc", outname);

			AlertDialog.Builder alert = new AlertDialog.Builder(this);  
			alert.setTitle(getString(R.string.dialog_Success));
			alert.setMessage(getString(R.string.configexport_success) + " '" + outname + "'.");
			alert.show();
		}
		catch(IOException e)
		{
			AlertDialog.Builder alert = new AlertDialog.Builder(this);  
			alert.setTitle(getString(R.string.dialog_Error));
			alert.setMessage(getString(R.string.configexport_failed) + " '" + outname + "'.");
			alert.show();
		}
	}
	
	private String getNetHackDir()
	{
		return NetHackGame.appDir + "/nethackdir"; 
	}
}
