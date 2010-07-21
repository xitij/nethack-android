package com.nethackff;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;

public class NetHackPreferences extends PreferenceActivity
{
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		// Hmm, caused an exception for some reason.
		//	requestWindowFeature(Window.FEATURE_NO_TITLE);

		addPreferencesFromResource(R.xml.preferences);

		ListPreference tileSetList = (ListPreference)findPreference("TileSet"); 

		String tilesetnames[] = this.getIntent().getExtras().getStringArray("TileSetNames");
		String tilesetvalues[] = this.getIntent().getExtras().getStringArray("TileSetValues");

		tileSetList.setEntryValues(tilesetvalues);
		tileSetList.setEntries(tilesetnames);
	}
}

