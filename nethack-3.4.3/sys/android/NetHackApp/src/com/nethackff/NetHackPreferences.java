package com.nethackff;

import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
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
