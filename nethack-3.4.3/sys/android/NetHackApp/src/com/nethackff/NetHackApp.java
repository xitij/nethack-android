package com.nethackff;

import android.app.Activity;
import android.widget.TextView;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.lang.Thread;

public class NetHackApp extends Activity
{
	String outputText;

	public void addText(String s)
	{
		outputText += s;
		outputText += "\n";
	}
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if(super.onKeyDown(keyCode, event))
		{
			return true;
		}
		String keyCodeString = Integer.toString(keyCode);
		addText(keyCodeString);
		tv.setText(outputText);
		setContentView(tv);
		return true;
	}

	public boolean onTouchEvent(MotionEvent event)
	{
		if(super.onTouchEvent(event))
		{
			return true;
		}
		addText(stringFromJNI());
		tv.setText(outputText);
		setContentView(tv);
		return true;
	}

	TextView tv;
  
	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		Log.i("TESTING LOG", "TEST");
		outputText = "";
 
		/* Create a TextView and set its content.
		 * the text is retrieved by calling a native
		 * function.
		 */
		tv = new TextView(this);
		addText("Running");
		if(TestInit() == 0)
		{
			addText("Failed to initialize");
			tv.setText(outputText);
			setContentView(tv);
			return;
		}

		addText(stringFromJNI());
		addText(stringFromJNI());
		addText(stringFromJNI());
		addText(stringFromJNI());
		addText(stringFromJNI());
		tv.setText(outputText);
		setContentView(tv);
		try
		{
			Thread.sleep(500);
		} catch(InterruptedException e)
		{
			throw new RuntimeException(e.getMessage());
		}
		/*
		waiting(2);
		TestUpdate();
*/

		addText(stringFromJNI());
		addText(stringFromJNI());
		addText(stringFromJNI());
		addText("TEST--");
		tv.setText(outputText);
		setContentView(tv);

		TestShutdown();
	}

	/* A native method that is implemented by the
	 * 'hello-jni' native library, which is packaged
	 * with this application.
	 */
	public native String  stringFromJNI();

	public native int TestInit(); 
	public native void TestShutdown(); 
	public native void TestUpdate();
	
	/* This is another native method declaration that is *not*
	 * implemented by 'hello-jni'. This is simply to show that
	 * you can declare as many native methods in your Java code
	 * as you want, their implementation is searched in the
	 * currently loaded native libraries only the first time
	 * you call them.
	 *
	 * Trying to call this function will result in a
	 * java.lang.UnsatisfiedLinkError exception !
	 */
	public native String  unimplementedStringFromJNI();

	/* this is used to load the 'hello-jni' library on application
	 * startup. The library has already been unpacked into
	 * /data/data/com.example.HelloJni/lib/libhello-jni.so at
	 * installation time by the package manager.
	 */
	static {
		System.loadLibrary("test1");
	}
}
