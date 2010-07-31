package com.nethackff_tiles_pack1;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.regex.Pattern;

public class NetHackTilesInfoActivity extends Activity
{
	public TextView addTileInfo(LinearLayout layout, String title, String info)
	{
		TextView headerview = new TextView(this);
		headerview.setText(title);
		headerview.setTextSize(20);
		headerview.setTypeface(Typeface.DEFAULT_BOLD);
		layout.addView(headerview);

		TextView infoview = new TextView(this);
		infoview.setText(info + "\n");
		layout.addView(infoview);

		return infoview;
	}
	
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.tilesinfoactivity);

		//requestWindowFeature(Window.FEATURE_NO_TITLE);  
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,   
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		LinearLayout layout = (LinearLayout)findViewById(R.id.MainLayout);

		TextView view0 = (TextView)findViewById(R.id.TextView01);
		view0.setText(Html.fromHtml(getString(R.string.TextView01)));
		view0.setMovementMethod(LinkMovementMethod.getInstance());

		TextView view1 = addTileInfo(layout, getString(R.string.TilesAbigabaHeader), getString(R.string.TilesAbigabaInfo));
		Pattern pattern1 = Pattern.compile("www.multifoliate.com/nh/");
		Linkify.addLinks(view1, pattern1, "http://");

		TextView view2 = addTileInfo(layout, getString(R.string.TilesAbigabaAltHeader), getString(R.string.TilesAbigabaAltInfo));
		Linkify.addLinks(view2, pattern1, "http://");

		TextView view3 = addTileInfo(layout, getString(R.string.TilesAbsurdHeader), getString(R.string.TilesAbsurdInfo));
		Pattern pattern2 = Pattern.compile("www.aesthetictech.8m.com/Absurd.htm");
		Pattern pattern3 = Pattern.compile("nethack.wikia.com/wiki/The_Absurd_NetHack_Tileset");
		Pattern pattern6 = Pattern.compile("www.thinktwicenews.com");
		Linkify.addLinks(view3, pattern2, "http://");
		Linkify.addLinks(view3, pattern3, "http://");
		Linkify.addLinks(view3, pattern6, "http://");

		TextView view4 = addTileInfo(layout, getString(R.string.TilesAbsurdAltHeader), getString(R.string.TilesAbsurdAltInfo));
		Pattern pattern4 = Pattern.compile("bilious.homelinux.org/~paxed/nethack/nhsshot/tileset/absurd32_nethack.png");
		Linkify.addLinks(view4, pattern4, "http://");

		TextView view5 = addTileInfo(layout, getString(R.string.TilesRLTilesHeader), getString(R.string.TilesRLTilesInfo));
		Linkify.addLinks(view5, Linkify.ALL);
	}
}
