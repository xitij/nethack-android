package com.nethackff;

import java.io.File;

import com.nethackff.configeditor.ConfigEditor;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

public class NetHackPreferences extends PreferenceActivity
{
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Hmm, caused an exception for some reason.
		//	requestWindowFeature(Window.FEATURE_NO_TITLE);

		addPreferencesFromResource(R.xml.preferences);

		NetHackListPreferenceTileSet tileSetList = (NetHackListPreferenceTileSet)findPreference("TileSet"); 

		setTileSetPreference(tileSetList);
		
		setKeyBindPreferenceIntent();
		
		setConfigOptionsPreferenceIntent();
		
		setConfigBackupPreferenceListener();
		
		setPreferencesBackupPreferenceListener();
		
		setBackupKeyBindingsPreferenceListener();
		
		setImportConfigPreferenceListener();
		
		setImportPreferencesPreferenceListener();
		
		setImportKeyBindingsPreferenceListener();
	}

	private void setImportKeyBindingsPreferenceListener()
	{
		Preference importKeyBindings = findPreference("importKeyBindings");
		importKeyBindings.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.IMPORT_KEYBINDINGS);
				return true;
			}
		});
	}

	private void setImportPreferencesPreferenceListener()
	{
		Preference importPreferences = findPreference("importPreferences");
		importPreferences.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.IMPORT_PREFERENCES);
				return true;
			}
		});
	}

	private void setImportConfigPreferenceListener()
	{
		Preference importConfig = findPreference("importConfig");
		importConfig.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.IMPORT_CONFIG);
				return true;
			}
		});
	}

	private void setBackupKeyBindingsPreferenceListener()
	{
		Preference backupPreferences = findPreference("backupKeyBindings");
		backupPreferences.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.EXPORT_KEYBINDINGS);
				return true;
			}
		});
	}

	private void setPreferencesBackupPreferenceListener()
	{
		Preference backupPreferences = findPreference("backupPreferences");
		backupPreferences.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.EXPORT_PREFERENCES);
				return true;
			}
		});
	}

	private void setConfigBackupPreferenceListener()
	{
		Preference backupConfig = findPreference("backupConfig");
		backupConfig.setOnPreferenceClickListener(new OnPreferenceClickListener()
		{
			
			@Override
			public boolean onPreferenceClick(Preference preference)
			{
				ConfigUtil.importExportDialog(NetHackPreferences.this, ConfigUtil.ImportExportOperation.EXPORT_CONFIG);
				return true;
			}
		});
	}

	private void setConfigOptionsPreferenceIntent()
	{
		Preference configOptions = findPreference("configOptions");
		File file = new File(ConfigUtil.getNetHackDir() + "/.nethackrc");
		Uri uri = Uri.fromFile(file);
		Intent editConfigIntent = new Intent(Intent.ACTION_VIEW ,uri);
		editConfigIntent.setDataAndType(uri, "text/plain"); 
		editConfigIntent.setClass(this, ConfigEditor.class);
		configOptions.setIntent(editConfigIntent);
	}

	private void setKeyBindPreferenceIntent()
	{
		Preference keyBindPreference = findPreference("keyBindings");
		Intent intent = new Intent(getApplicationContext(), KeyBindingListActivity.class);
		keyBindPreference.setIntent(intent);
	}

	private void setTileSetPreference(NetHackListPreferenceTileSet tileSetList) {
		String tilesetnames[] = this.getIntent().getExtras().getStringArray("TileSetNames");
		String tilesetvalues[] = this.getIntent().getExtras().getStringArray("TileSetValues");
		String tilesetinfo[] = this.getIntent().getExtras().getStringArray("TileSetInfo");
		
		tileSetList.setEntryValues(tilesetvalues);
		tileSetList.setEntries(tilesetnames);
		tileSetList.setTileSetInfo(tilesetinfo);
		tileSetList.setDefaultValue(tilesetvalues[0]);
		
		tileSetList.setInfoFromValue();
	}
}
