package com.nethackff;

import android.app.Activity;
//import android.app.ActivityManager;
//import android.app.AlertDialog;
//import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.inputmethod.InputMethodManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
//import android.widget.ScrollView;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread;
import java.lang.reflect.Method;

public class NetHackApp extends Activity implements Runnable, OnGestureListener
{
	NetHackTerminalView mainView;
	NetHackTerminalView messageView;
	NetHackTerminalView statusView;
	NetHackTerminalView menuView;

	NetHackKeyboard virtualKeyboard;
	
	/* For debugging only. */
	NetHackTerminalView dbgTerminalTranscriptView;
	static NetHackTerminalState dbgTerminalTranscriptState;

	static UIMode uiModeActual = UIMode.Invalid;
	
	NetHackTerminalView currentDbgTerminalView;
	
	class ModifierKey
	{
		public boolean active = false;
		public boolean down = false;
		public boolean used = false;
		public boolean sticky = false;

		public void resetState()
		{
			active = down = used = false;
		}
		
		public void keyUp()
		{
			down = false;
			if(!sticky || used)
			{
				active = false;
				used = false;
			}
		}

		public void keyDown()
		{
			if(active && sticky)
			{
				used = true;
			}
			down = true;
			active = true;
		}

		public void usedIfActive()
		{
			if(active)
			{
				used = true;
			}
			if(sticky && active && !down)
			{
				active = false;
				used = false;
			}
		}
	}

	ModifierKey altKey;
	ModifierKey ctrlKey;
	ModifierKey shiftKey;

	enum KeyAction
	{
		None,
		VirtualKeyboard,
		AltKey,
		CtrlKey,
		ShiftKey,
		EscKey
	}
	
	enum ColorMode
	{
		Invalid,
		WhiteOnBlack,
		BlackOnWhite
	}
	
	enum UIMode
	{
		Invalid,
		PureTTY,
		AndroidTTY
	}

	boolean optFullscreen = true;
	ColorMode optColorMode = ColorMode.Invalid;
	UIMode optUIModeNew = UIMode.Invalid;
	boolean optMoveWithTrackball = true;
	KeyAction optKeyBindAltLeft = KeyAction.AltKey;
	KeyAction optKeyBindAltRight = KeyAction.AltKey;
	KeyAction optKeyBindBack = KeyAction.None;
	KeyAction optKeyBindCamera = KeyAction.VirtualKeyboard;
	KeyAction optKeyBindMenu = KeyAction.None;
	KeyAction optKeyBindSearch = KeyAction.CtrlKey;
	KeyAction optKeyBindShiftLeft = KeyAction.ShiftKey;
	KeyAction optKeyBindShiftRight = KeyAction.ShiftKey;

	public KeyAction getKeyActionFromKeyCode(int keyCode)
	{
		KeyAction keyAction = KeyAction.None;
		switch(keyCode)
		{
			case KeyEvent.KEYCODE_ALT_LEFT:
				keyAction = optKeyBindAltLeft; 	
				break;
			case KeyEvent.KEYCODE_ALT_RIGHT:
				keyAction = optKeyBindAltRight; 	
				break;
			case KeyEvent.KEYCODE_CAMERA:
				keyAction = optKeyBindCamera; 	
				break;
			case KeyEvent.KEYCODE_BACK:
				keyAction = optKeyBindBack; 	
				break;
			case KeyEvent.KEYCODE_MENU:
				keyAction = optKeyBindMenu;
				break;
			case KeyEvent.KEYCODE_SEARCH:
				keyAction = optKeyBindSearch; 	
				break;
			case KeyEvent.KEYCODE_SHIFT_LEFT:
				keyAction = optKeyBindShiftLeft; 	
				break;
			case KeyEvent.KEYCODE_SHIFT_RIGHT:
				keyAction = optKeyBindShiftRight; 	
				break;
			default:
				break;
		}
		return keyAction;		
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		KeyAction keyAction = getKeyActionFromKeyCode(keyCode);

		if(keyAction == KeyAction.VirtualKeyboard)
		{
//			InputMethodManager inputManager = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
//			inputManager.showSoftInput(mainView.getRootView(), InputMethodManager.SHOW_FORCED);
			keyboardShown = !keyboardShown;
			updateLayout();
			return true;
		}

		if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
		{
			return super.onKeyDown(keyCode, event);
		}

		if(keyAction == KeyAction.AltKey)
		{
			altKey.keyDown();
			return true;
		}
		if(keyAction == KeyAction.CtrlKey)
		{
			ctrlKey.keyDown();
			return true;
		}
		if(keyAction == KeyAction.ShiftKey)
		{
			shiftKey.keyDown();
			return true;
		}
		char c = 0;
		if(keyAction == KeyAction.EscKey)
		{
			c = 27;
		}
		if(optMoveWithTrackball)
		{
			switch(keyCode)
			{
				case KeyEvent.KEYCODE_DPAD_DOWN:
					c = 'j';
					break;
				case KeyEvent.KEYCODE_DPAD_UP:
					c = 'k';
					break;
				case KeyEvent.KEYCODE_DPAD_LEFT:
					c = 'h';
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					c = 'l';
					break;
				case KeyEvent.KEYCODE_DPAD_CENTER:
					c = ',';
					break;
			}
		}
		
		String s = "";

		if(c == 0)
		{
			c = (char)event.getUnicodeChar((shiftKey.active ? KeyEvent.META_SHIFT_ON : 0)
						| (altKey.active ? KeyEvent.META_ALT_ON : 0));
			if(ctrlKey.active)
			{
				// This appears to be how the ASCII numbers would have been
				// represented if we had a Ctrl key, so now we apply that
				// for the search key instead. This is for commands like kick
				// (^D).
				c = (char)(((int)c) & 0x1f);
			}
		}

		// Map the delete button to backspace.
		if(keyCode == KeyEvent.KEYCODE_DEL)
		{
			c = 8;
		}
		
		if(c != 0)
		{
			ctrlKey.usedIfActive();
			shiftKey.usedIfActive();
			altKey.usedIfActive();

			s += c;
			NetHackTerminalSend(s);
		}

		return true;
	}

	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		KeyAction keyAction = getKeyActionFromKeyCode(keyCode);

		if(keyAction == KeyAction.CtrlKey)
		{
			ctrlKey.keyUp();
		}
		if(keyAction == KeyAction.AltKey)
		{
			altKey.keyUp();
		}
		if(keyAction == KeyAction.ShiftKey)
		{
			shiftKey.keyUp();
		}

		if(keyAction == KeyAction.None)
		{
			if(keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_MENU)
			{
				return super.onKeyUp(keyCode, event);
			}
		}

		return true;
	}

	private boolean finishRequested = false;

	private synchronized void quit()
	{
		if(!finishRequested)
		{
			finishRequested = true;

			// This could be used to just shut down the Activity, but that's probably
			// not good enough for us - the NetHack native code library would often
			// stay resident, and appears to sadly not be reentrant:
			//	this.finish();

			// This could supposedly be used to kill ourselves - but, I only
			// got some sort of exception from it.
			//	ActivityManager am = (ActivityManager)getSystemService(ACTIVITY_SERVICE); 
			//	am.restartPackage("com.nethackff11");

			// This seems to work. For sure, this is not encouraged for Android
			// applications, but the only alternative I could find would be to dig
			// through the NetHack native source code and find any places that may not
			// get reinitialized if reusing the library without reloading it
			// (and I have found no way to force it to reload). Obviously, it could
			// be a lot of work and a lot of risk involved with that approach.
			// It's not just a theoretical problem either - I often got characters
			// that had tons (literally) of items when the game started, so something
			// definitely appeared to be corrupted. Given this, System.exit() is the
			// best option I can think of.
			System.exit(0);
		}
	}

	private boolean clearScreen = false;

	public int quitCount = 0;

	NetHackTerminalView currentView;
	NetHackTerminalView preLogView;
	boolean escSeq = false;
	boolean escSeqAndroid = false;
	String currentString = "";

	public void writeTranscript(String s)
	{
		String transcript = "";
		for(int i = 0; i < s.length(); i++)
		{
			char c = s.charAt(i);
			if(c < 32)
			{
				transcript += '^';
				int a = c/10;
				int b = c - a*10;
				transcript += (char)('0' + a);
				transcript += (char)('0' + b);
			}
			else
			{
				transcript += c;
			}
		}
		Log.i("NetHackDbg", transcript);
		dbgTerminalTranscriptView.write(transcript);
	}

	boolean refreshDisplay = false;

	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			if(NetHackHasQuit() != 0)
			{
				gameInitialized = false;
				terminalInitialized = false;

				quit();
				return;	
			}
			if(clearScreen)
			{
				clearScreen = false;
				mainView.terminal.clearScreen();
				mainView.invalidate();
				return;
			}

			String s = NetHackTerminalReceive();
			if(s.length() != 0)
			{
				for(int i = 0; i < s.length(); i++)
				{
					char c = s.charAt(i);
					if(!escSeq)
					{
						if(c == 27)
						{
							escSeq = true;
							escSeqAndroid = false;
						}
						else
						{
							currentString += c;
						}
					}
					else if(!escSeqAndroid)
					{
						if(c == 'A')
						{
							if(currentView == currentDbgTerminalView && currentView != null)
							{
								writeTranscript(currentString);
							}
							if(currentView == null)
							{
								Log.i("NetHackDbg", currentString);
							}
							else
							{
								currentView.write(currentString);
							}
							currentString = "";
							escSeqAndroid = true;
						}
						else
						{
							// Not the droids we were looking for.
							currentString += (char)27;
							currentString += c;
							escSeq = escSeqAndroid = false;
						}
					}
					else
					{
						if(c == '0')
						{
							if((currentView == null) && (preLogView != null))
							{
								currentView = preLogView;
								preLogView = null;
							}
							else
							{
								currentView = mainView;
							}
						}
						else if(c == '1')
						{
							currentView = messageView;
						}
						else if(c == '2')
						{
							currentView = statusView;
						}
						else if(c == '4')
						{
							currentView = menuView;
						}
						else if(c == 'S')
						{
							if(currentView == menuView)
							{
								menuShown = true;
								updateLayout();
							}
						}
						else if(c == 'H')
						{
							if(currentView == menuView)
							{
								menuShown = false;
								updateLayout();
							}
						}
						else if(c == '3')
						{
							// TEMP
							if(currentView != null)
							{
								preLogView = currentView;
							}
							currentView = null;							
						}
						else if(c == 'C')
						{
							if(currentView != null)
							{
								currentView.setDrawCursor(!currentView.getDrawCursor());
								//currentView.invalidate();
							}
						}
						escSeq = escSeqAndroid = false;	
					}
				}
				if(!escSeq)
				{
					if(currentView == currentDbgTerminalView && currentView != null)
					{
						writeTranscript(currentString);
					}
					if(currentView == null)
					{
						Log.i("NetHackDbg", currentString);
					}
					else
					{
						currentView.write(currentString);
					}
					currentString = "";
				}
			}

			if(refreshDisplay)
			{
/*
				String tmp = "" + (char)(((int)'r') & 0x1f);
// Is this safe??
				NetHackTerminalSend(tmp);
*/
				NetHackRefreshDisplay();
				refreshDisplay = false;
			}
		}
	};

	public synchronized boolean checkQuitCommThread()
	{
		if(shouldStopCommThread)
		{
			commThreadRunning = false;
			shouldStopCommThread = false;
			notify();
			return true;
		}
		return false;
	}

	public synchronized boolean isCommThreadRunning()
	{
		return commThreadRunning;
	}

	public void run()
	{
		if(!gameInitialized)
		{
			if(!compareAsset("version.txt"))
			{
				doCommand("/system/bin/mkdir", "/data/data/com.nethackff/nethackdir", "");
				doCommand("/system/bin/mkdir", "/data/data/com.nethackff/nethackdir/save", "");

				copyNetHackData();

				copyAsset("version.txt");

			}

			uiModeActual = optUIModeNew;
			boolean pureTTY = (uiModeActual == UIMode.PureTTY);
			if(NetHackInit(pureTTY ? 1 : 0) == 0)
			{
				// TODO
				return;
			}

			//	copyFile("/data/data/com.nethackff/dat/save/10035foo.gz", "/sdcard/10035foo.gz");

			gameInitialized = true;
			clearScreen = true;
		}

		while(true)
		{
			if(checkQuitCommThread())
			{
				return;
			}
			try
			{
				handler.sendEmptyMessage(0);
				Thread.sleep(10);
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e.getMessage());
			}
		}
	}

	boolean shouldStopCommThread = false;
	boolean commThreadRunning = false;

	public synchronized void stopCommThread()
	{
		if(!commThreadRunning)
		{
			return;
		}
		shouldStopCommThread = true;
		try
		{
			wait();
		}
		catch(InterruptedException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	public void onDestroy()
	{
		if(NetHackHasQuit() == 0)
		{
			Log.i("NetHack", "Auto-saving");
			if(NetHackSave() != 0)
			{
				Log.i("NetHack", "Auto-save succeeded");
			}
			else
			{
				Log.w("NetHack", "Auto-save failed");
			}
		}

		stopCommThread();
		super.onDestroy();
		//TestShutdown();
	}

	public void doCommand(String command, String arg0, String arg1)
	{
		try
		{
				String fullCmd = command + " " + arg0 + " " + arg1;
				Process p = Runtime.getRuntime().exec(fullCmd);
				p.waitFor();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}
		catch (InterruptedException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	public boolean compareAsset(String assetname)
	{
		boolean match = false;

		String destname = "/data/data/com.nethackff/" + assetname;
		File newasset = new File(destname);
		try
		{
			BufferedInputStream out = new BufferedInputStream(new FileInputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(this.getAssets().open(assetname));
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
		String destname = "/data/data/com.nethackff/" + assetname;
		File newasset = new File(destname);
		try
		{
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(this.getAssets().open(assetname));
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
			mainView.terminal.write("Failed to copy file '" + assetname + "'.\n");
		}
	}

	public void copyFile(String srcname, String destname)
	{
		File newasset = new File(destname);
		File srcfile = new File(srcname);
		try
		{
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(new FileInputStream(srcfile));
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
			mainView.terminal.write("Failed to copy file '" + srcname + "' to '" + destname + "'.\n");
		}
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
				copyAsset("nethackdir/" + assets[i]);
				doCommand("/system/bin/chmod", "666", "/data/data/com.nethackff/nethackdir/" + assets[i]);
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	public void deleteNetHackData()
	{
		AssetManager am = getResources().getAssets();
		String assets[] = null;
		try
		{
			assets = am.list("nethackdir");

			for(int i = 0; i < assets.length; i++)
			{
				doCommand("/system/bin/rm", "/data/data/com.nethackff/nethackdir/" + assets[i], "");
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e.getMessage());
		}
	}

	Thread commThread;

	GestureDetector gestureScanner;

	public boolean onTouchEvent(MotionEvent me)
	{
		return gestureScanner.onTouchEvent(me);
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) 
	{
		int newscrollx = mainView.getScrollX() + (int)distanceX;
		int newscrolly = mainView.getScrollY() + (int)distanceY;
		if(newscrollx < 0)
		{
			newscrollx = 0;
		}
		if(newscrolly < 0)
		{
			newscrolly = 0;
		}

		int termx = mainView.charWidth*mainView.sizeX;
		int termy = mainView.charHeight*mainView.sizeY;

		int maxx = termx - mainView.getWidth();
		int maxy = termy - mainView.getHeight();
		if(maxx < 0)
		{
			maxx = 0;
		}
		if(maxy < 0)
		{
			maxy = 0;
		}
		if(newscrollx >= maxx)
		{
			newscrollx = maxx - 1;
		}
		if(newscrolly >= maxy)
		{
			newscrolly = maxy - 1;
		}

		mainView.scrollTo(newscrollx, newscrolly);
		return true;
	}

	public boolean onDown(MotionEvent e)
	{
		return true;
	}
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY)
	{
		return true;
	}
	public void onLongPress(MotionEvent e)
	{
		// TEMP
		mainView.scrollToCursor();
		Log.i("NetHack", "onLongPress");
	}
	public void onShowPress(MotionEvent e)
	{
	}
	public boolean onSingleTapUp(MotionEvent e)
	{
		return true;
	}

	public synchronized void startCommThread()
	{
		if(!commThreadRunning)
		{
			commThreadRunning = true;
			commThread = new Thread(this);
			commThread.start();
		}
	}
	public void onResume()
	{
		super.onResume();

		while(isCommThreadRunning())
		{
			try
			{
				Thread.sleep(100);
			}
			catch(InterruptedException e)
			{
				throw new RuntimeException(e.getMessage());
			}
		}

		startCommThread();
	}
	
	public void onPause()
	{
		stopCommThread();

		super.onPause();
	}

	public void onStart()
	{
		super.onStart();

		UIMode uiModeBefore = optUIModeNew;
		
		getPrefs();

		// Probably makes sense to do this, in case the user held down some key
		// from before, or messed with the stickiness.
		ctrlKey.resetState();
		altKey.resetState();
		shiftKey.resetState();

		if(optFullscreen)
		{
			this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
					WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}
		else
		{
			this.getWindow().setFlags(0, WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		if(optUIModeNew != uiModeBefore)
		{
			Dialog dialog = new Dialog(this);
			dialog.setContentView(R.layout.uimodechanged);
			dialog.setTitle(getString(R.string.uimodechanged_title));
			dialog.show();
		}

		boolean blackonwhite = (optColorMode == ColorMode.BlackOnWhite);
		mainView.setWhiteBackgroundMode(blackonwhite);
		menuView.setWhiteBackgroundMode(blackonwhite);
		messageView.setWhiteBackgroundMode(blackonwhite);
		statusView.setWhiteBackgroundMode(blackonwhite);
	}	

	LinearLayout screenLayout;

	boolean keyboardShown = false;
	boolean menuShown = false;

	void updateLayout()
	{
		mainView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT, 1.0f));
		menuView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT, 1.0f));
		messageView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 0.0f));
		statusView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 0.0f));

		screenLayout.removeAllViews();
		if(!menuShown)
		{
			boolean pureTTY = (uiModeActual == UIMode.PureTTY);

			//layout.addView(dbgTerminalTranscript);
			if(!pureTTY)
			{
				screenLayout.addView(messageView);
			}
			screenLayout.addView(mainView);
			if(!pureTTY)
			{
				screenLayout.addView(statusView);
			}
		}
		else
		{
			screenLayout.addView(menuView);
		}
		if(currentDbgTerminalView != null)
		{
			screenLayout.addView(dbgTerminalTranscriptView);
		}
		if(keyboardShown)
		{
			screenLayout.addView(virtualKeyboard.virtualKeyboardView);
		}

		mainView.invalidate();
	}
	
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		altKey = new ModifierKey();
		ctrlKey = new ModifierKey();
		shiftKey = new ModifierKey();

		virtualKeyboard = new NetHackKeyboard(this);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
//        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NO_STATUS_BAR,
//      		WindowManager.LayoutParams.FLAG_NO_STATUS_BAR);

		int width = 80;
		int height = 24;		// 26

		int messageRows = 2;
		int statusRows = 2;

		if(!terminalInitialized)
		{
//			mainTerminalState = new NetHackTerminalState(width, height - messageRows - statusRows);
			mainTerminalState = new NetHackTerminalState(width, height);
//			statusTerminalState = new NetHackTerminalState(width, statusRows);

			terminalInitialized = true;
		}

//		messageTerminalState = new NetHackTerminalState(width, messageRows);
		messageTerminalState = new NetHackTerminalState();
//		statusTerminalState = new NetHackTerminalState(53, statusRows);
//		dbgTerminalTranscriptState = new NetHackTerminalState(53, 5);
		statusTerminalState = new NetHackTerminalState();
		menuTerminalState = new NetHackTerminalState();

		getPrefs();

		boolean pureTTY;
		if(!gameInitialized)
		{
			uiModeActual = optUIModeNew;
		}
		pureTTY = (uiModeActual == UIMode.PureTTY);

		mainView = new NetHackTerminalView(this, mainTerminalState);
//		mainView.offsetY = messageRows;
		if(!pureTTY)
		{
			mainView.sizeY -= messageRows + statusRows;
			mainView.offsetY = 1;

			/* TEMP */
//			mainView.sizeY -= 10;
		}
//		mainView.sizeY -= 3;
		mainView.computeSizePixels();
//		mainView.sizePixelsY -= 40;	// TEMP
//		mainView.sizePixelsY -= 120;	// TEMP
		mainView.sizePixelsY = 32;	// Hopefully not really relevant - will grow as needed.

		Display display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int sizeX = display.getWidth();
		int sizeY = display.getHeight();

		Configuration config = getResources().getConfiguration();		
		if(config.orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			keyboardShown = true;
		}
		else
		{
			keyboardShown = false;
		}
		
		messageView = new NetHackTerminalView(this, messageTerminalState);
		statusView = new NetHackTerminalView(this, statusTerminalState);
		menuView = new NetHackTerminalView(this, menuTerminalState); 

		messageView.setSizeXFromPixels(sizeX);
		messageView.setSizeY(messageRows);
		messageView.computeSizePixels();
		statusView.setSizeXFromPixels(sizeX);
		statusView.setSizeY(statusRows);
		statusView.computeSizePixels();

		menuView.setSizeX(sizeX);
		menuView.setSizeY(24);
		menuView.computeSizePixels();

		messageView.initStateFromView();
		statusView.initStateFromView();
		menuView.initStateFromView();

		NetHackSetScreenDim(statusView.getSizeX(), messageRows);

		// TEMP
//		mainTerminalState.offsetY = 2;		

		messageView.setDrawCursor(false);
		statusView.setDrawCursor(false);

		currentView = mainView;

		if(!gameInitialized)
		{
			mainView.terminal.write("Please wait, initializing...\n");
		}

		//currentDbgTerminalView = messageView;
		if(currentDbgTerminalView != null)
		{
			dbgTerminalTranscriptState = new NetHackTerminalState();
			dbgTerminalTranscriptState.colorForeground = NetHackTerminalState.kColGreen;
			dbgTerminalTranscriptView = new NetHackTerminalView(this, dbgTerminalTranscriptState);
			dbgTerminalTranscriptView.setSizeXFromPixels(sizeX);
			dbgTerminalTranscriptView.setSizeY(5);
			dbgTerminalTranscriptView.initStateFromView();
		}

		screenLayout = new LinearLayout(this);
		updateLayout();

		screenLayout.setOrientation(LinearLayout.VERTICAL);
		setContentView(screenLayout);
//		setContentView(mainView);

		refreshDisplay = true;

		gestureScanner = new GestureDetector(this);
	}

	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.layout.menu, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item)
	{  
		switch(item.getItemId())
		{
			case R.id.about:
			{
				Dialog dialog = new Dialog(this);
				dialog.setContentView(R.layout.about);
				dialog.setTitle(getString(R.string.about_title));
				dialog.show();
				return true;
			}
			case R.id.preferences:
			{
				startActivity(new Intent(this, NetHackPreferences.class));
				return true;
			}
		}
		return false;  
	}

	private KeyAction getKeyActionEnumFromString(String s)
	{
		return KeyAction.valueOf(s);
	}
	
	private void getPrefs()
	{
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		optKeyBindCamera = getKeyActionEnumFromString(prefs.getString("CameraButtonFunc", "VirtualKeyboard"));
		optKeyBindSearch = getKeyActionEnumFromString(prefs.getString("SearchButtonFunc", "CtrlKey"));
		optKeyBindAltLeft = getKeyActionEnumFromString(prefs.getString("LeftAltKeyFunc", "AltKey"));
		optKeyBindAltRight = getKeyActionEnumFromString(prefs.getString("RightAltKeyFunc", "AltKey"));
		optKeyBindShiftLeft = getKeyActionEnumFromString(prefs.getString("LeftShiftKeyFunc", "ShiftKey"));
		optKeyBindShiftRight = getKeyActionEnumFromString(prefs.getString("RightShiftKeyFunc", "ShiftKey"));
		altKey.sticky = prefs.getBoolean("StickyAlt", false);
		ctrlKey.sticky = prefs.getBoolean("StickyCtrl", false);
		shiftKey.sticky = prefs.getBoolean("StickyShift", false);
		optFullscreen = prefs.getBoolean("Fullscreen", true);
		optMoveWithTrackball = prefs.getBoolean("MoveWithTrackball", true);
		optColorMode = ColorMode.valueOf(prefs.getString("ColorMode", "WhiteOnBlack"));
		optUIModeNew = UIMode.valueOf(prefs.getString("UIMode", "AndroidTTY"));
	}

	public static boolean terminalInitialized = false;
	public static boolean gameInitialized = false;
	public static NetHackTerminalState mainTerminalState;
	public /*static*/ NetHackTerminalState messageTerminalState;
	public /*static*/ NetHackTerminalState statusTerminalState;
	public NetHackTerminalState menuTerminalState;
	
	public native int NetHackInit(int puretty);
	public native void NetHackShutdown();
	public native String NetHackTerminalReceive();
	public native void NetHackTerminalSend(String str);
	public native int NetHackHasQuit();
	public native int NetHackSave();
	public native void NetHackSetScreenDim(int width, int nummsglines);
	public native void NetHackRefreshDisplay();
	
	static
	{
		System.loadLibrary("nethack");
	}
}
