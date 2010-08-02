package com.slashemff_tiles_pack1;

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
	}
}
