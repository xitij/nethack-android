package com.nethackff;

import android.app.Activity;
//import android.app.ActivityManager;
//import android.app.AlertDialog;
//import android.app.AlertDialog.Builder;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.res.AssetManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images.Media;
import android.util.DisplayMetrics;
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
import android.view.View.OnKeyListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
//import android.widget.ScrollView;

import java.io.BufferedReader;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.LinkedList;

public class NetHackApp extends Activity implements Runnable, OnGestureListener
{
	NetHackTerminalView mainView;
	NetHackTerminalView messageView;
	NetHackTerminalView statusView;
	NetHackTerminalView menuView;
	NetHackTiledView tiledView;

	NetHackView getMapView()
	{
		if(isTiledViewMode())
		{
			return tiledView;
		}
		else
		{
			return mainView;
		}
	}

	/**
	 * True if the native code has told us to use a tiled map view. Note that it's
	 * possible for this to be false even if the human user has requested tiles,
	 * because we could be on the TTY Rogue level, or the desired mode may not have
	 * propagated throught the native code yet. 
	 */
	static boolean tilesEnabled = false;

	boolean isTiledViewMode()
	{
		return (uiModeActual == UIMode.AndroidTiled) && tilesEnabled;
	}
	boolean isPureTTYMode()
	{
		return uiModeActual == UIMode.PureTTY;	
	}

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

	enum Orientation
	{
		Invalid,
		Sensor,
		Portrait,
		Landscape,
		Unspecified
	}
	
	enum KeyAction
	{
		None,
		VirtualKeyboard,
		AltKey,
		CtrlKey,
		ShiftKey,
		EscKey,
		ZoomIn,
		ZoomOut,
		ForwardToSystem		// Forward for O/S to handle.
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
		AndroidTTY,
		AndroidTiled,
	}

	enum FontSize
	{
		FontSize10,
		FontSize11,
		FontSize12,
		FontSize13,
		FontSize14,
		FontSize15
	}

	enum CharacterSet
	{
		Invalid,
		ANSI128,
		IBM,
		Amiga
	}

	enum TouchscreenMovement
	{
		Disabled,
		MouseClick,
		Grid3x3,
		CenterOnLocation,
		CenterOnPlayer,
	}

	boolean optScrollSmoothly = true;
	boolean optScrollWithPlayer = true;
	boolean optAllowTextReformat = true;
	boolean optFullscreen = true;
	ColorMode optColorMode = ColorMode.Invalid;
	UIMode optUIModeNew = UIMode.Invalid;
	CharacterSet optCharacterSet = CharacterSet.Invalid;
	NetHackTerminalView.ColorSet optCharacterColorSet = NetHackTerminalView.ColorSet.Amiga;
	FontSize optFontSize = FontSize.FontSize10;
	Orientation optOrientation = Orientation.Invalid;
	boolean optMoveWithTrackball = true;
	TouchscreenMovement optTouchscreenTap = TouchscreenMovement.MouseClick;
	TouchscreenMovement optTouchscreenHold = TouchscreenMovement.CenterOnPlayer;
	KeyAction optKeyBindAltLeft = KeyAction.AltKey;
	KeyAction optKeyBindAltRight = KeyAction.AltKey;
	KeyAction optKeyBindBack = KeyAction.ForwardToSystem;
	KeyAction optKeyBindCamera = KeyAction.VirtualKeyboard;
	KeyAction optKeyBindMenu = KeyAction.None;
	KeyAction optKeyBindSearch = KeyAction.CtrlKey;
	KeyAction optKeyBindShiftLeft = KeyAction.ShiftKey;
	KeyAction optKeyBindShiftRight = KeyAction.ShiftKey;
	KeyAction optKeyBindVolumeUp = KeyAction.ZoomIn;
	KeyAction optKeyBindVolumeDown = KeyAction.ZoomOut;

	String optTileSetName;	

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
			case KeyEvent.KEYCODE_BACK:
				keyAction = optKeyBindBack;
				break;
			case KeyEvent.KEYCODE_CAMERA:
				keyAction = optKeyBindCamera;
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
			case KeyEvent.KEYCODE_VOLUME_UP:
				keyAction = optKeyBindVolumeUp;
				break;
			case KeyEvent.KEYCODE_VOLUME_DOWN:
				keyAction = optKeyBindVolumeDown;
				break;
			default:
				break;
		}
		return keyAction;		
	}

	enum MoveDir
	{
		None,
		UpLeft,
		Up,
		UpRight,
		Left,
		Center,
		Right,
		DownLeft,
		Down,
		DownRight
	};

	public char getCharForDir(MoveDir dir)
	{
		char c = '\0';
		switch(dir)
		{
			case None:
				break;
			case UpLeft:
				c = 'y';
				break;
			case Up:
				c = 'k';
				break;
			case UpRight:
				c = 'u';
				break;
			case Left:
				c = 'h';
				break;
			case Center:
				c = '.';	// Not sure
				break;
			case Right:
				c = 'l';
				break;
			case DownLeft:
				c = 'b';
				break;
			case Down:
				c = 'j';
				break;
			case DownRight:
				c = 'n';
				break;
		}
		return c;
	}

	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		KeyAction keyAction = getKeyActionFromKeyCode(keyCode);

		if(keyAction == KeyAction.VirtualKeyboard)
		{
//			InputMethodManager inputManager = (InputMethodManager)this.getSystemService(Context.INPUT_METHOD_SERVICE);
//			inputManager.showSoftInput(mainView.getRootView(), InputMethodManager.SHOW_FORCED);
			boolean newval = !optKeyboardShownInConfig[screenConfig.ordinal()];
			optKeyboardShownInConfig[screenConfig.ordinal()] = newval;

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
			SharedPreferences.Editor prefsEditor = prefs.edit();
			if(screenConfig == ScreenConfig.Portrait)
			{
				prefsEditor.putBoolean("KeyboardShownInPortrait", newval);
			}
			else
			{
				prefsEditor.putBoolean("KeyboardShownInLandscape", newval);
			}
			prefsEditor.commit();
			updateLayout();

			// Take this as an opportunity to scroll to the player, as we may have exposed
			// or obscured a part of the map view.
			getMapView().pendingRedraw = true;
			scrollWithPlayerRefresh = true;

			return true;
		}

		if(keyAction == KeyAction.ZoomIn || keyAction == KeyAction.ZoomOut)
		{
			NetHackView view = getMapView();
			if(keyAction == KeyAction.ZoomIn)
			{
				view.zoomIn();
				scrollWithPlayerRefresh = true;
			}
			else if(keyAction == KeyAction.ZoomOut)
			{
				view.zoomOut();
				scrollWithPlayerRefresh = true;
			}
			else
			{
				return true;	
			}
		}

		if(keyAction == KeyAction.ForwardToSystem)
		{
			return super.onKeyDown(keyCode, event);
		}

		if(keyCode == KeyEvent.KEYCODE_MENU)
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
			MoveDir dir = MoveDir.None;
			switch(keyCode)
			{
				case KeyEvent.KEYCODE_DPAD_DOWN:
					dir = MoveDir.Down;
					//c = 'j';
					break;
				case KeyEvent.KEYCODE_DPAD_UP:
					dir = MoveDir.Up;
					//c = 'k';
					break;
				case KeyEvent.KEYCODE_DPAD_LEFT:
					dir = MoveDir.Left;
					//c = 'h';
					break;
				case KeyEvent.KEYCODE_DPAD_RIGHT:
					dir = MoveDir.Right;
					//c = 'l';
					break;
				case KeyEvent.KEYCODE_DPAD_CENTER:
					//dir = MoveDir.Center;
					c = ',';
					break;
			}
			if(dir != MoveDir.None)
			{
				c = getCharForDir(dir);
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
			//	am.restartPackage("com.nethackff");

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

	NetHackTerminalView currentTerminalView;
	NetHackTerminalView preLogView;
	NetHackTiledView currentTiledView;
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

	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);

		Configuration config = getResources().getConfiguration();		
		if(config.orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			screenConfig = ScreenConfig.Portrait;
		}
		else
		{
			screenConfig = ScreenConfig.Landscape;
		}

		rebuildViews(true);	// Not sure if there is a good reason to use this immediate mode here.
	}

	final int menuViewWidth = 80;
	final int statusViewWidth = 80;

	public void initViewsCommon(boolean initial)
	{
		Display display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int sizeX = display.getWidth();
		int sizeY = display.getHeight();

		messageView.setSizeXFromPixels(sizeX);
		messageView.setSizeY(messageRows);

		messageView.extraSizeY = 1;

		messageView.computeSizePixels();
		messageView.initStateFromView();

		menuView.setSizeX(menuViewWidth);
		menuView.setSizeY(24);
		menuView.computeSizePixels();

		menuView.reformatText = optAllowTextReformat;

		if(initial)
		{
			menuView.initStateFromView();
		}
		if(menuView.reformatText)
		{
			menuView.setSizeXFromPixels(sizeX);
		}
		else
		{
			menuView.setSizeX(menuViewWidth);
		}

		// Compute how many characters would fit on screen in the status line. This gets passed
		// on to the native code so it knows if it should shorten the status or not.
		int statuswidthonscreen = sizeX/statusView.squareSizeX;

		// Regardless of how much we can actually fit, we still keep the width of the actual
		// view constant. This is done so that in case the text on the first line still doesn't fit
		// on screen after being shortened, it doesn't wrap around and kill the whole second line.
		// We may need to add the ability to scroll this view.
		statusView.setSizeX(statusViewWidth);

		statusView.setSizeY(statusRows);
		statusView.computeSizePixels();
		statusView.initStateFromView();

		NetHackSetScreenDim(messageView.getSizeX(), messageRows, statuswidthonscreen);

		mainView.colorSet = optCharacterColorSet;
	}
	
	public void rebuildViews(boolean immediate)
	{
		screenLayout.removeAllViews();

		initViewsCommon(false);	

		Display display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
		int sizeX = display.getWidth();
		int sizeY = display.getHeight();

		screenLayout.removeAllViews();
		screenLayout = new LinearLayout(this);

		virtualKeyboard = new NetHackKeyboard(this);

		updateLayout();

		screenLayout.setOrientation(LinearLayout.VERTICAL);
		setContentView(screenLayout);

		// I had some problems where I would switch in and out of tiled mode
		// from the preference menu, and the application would freeze up. It seems
		// to be somehow related to the communication buffers in the native code,
		// possibly some sort of threading deadlock. I'm not sure if this is a true
		// fix or just a workaround, but deferring the call to NetHackRefreshDisplay()
		// seems to help. One theory I had was that when called from onStart(), the
		// communication thread isn't running yet and can't consume from the buffers
		// that the native thread may be waiting for, but I had some indication of that
		// not being the cause.
		if(!immediate)
		{
			refreshDisplay = true;
		}
		else
		{
			NetHackRefreshDisplay();
		}

	}

	public long autoScrollLastTime = -1;
	public int autoScrollX = 0;
	public int autoScrollY = 0;

	public void startAutoScroll(int deltax, int deltay)
	{
		autoScrollX = deltax;
		autoScrollY = deltay;
		autoScrollLastTime = System.currentTimeMillis();

		if(!optScrollSmoothly)
		{
			performAutoScroll(1.0f);
			stopAutoScroll();
			return;	
		}
	}
	public void stopAutoScroll()
	{
		autoScrollX = 0;
		autoScrollY = 0;
		autoScrollLastTime = -1;
	}

	public void performAutoScroll(float p)
	{
		int deltax = (int)Math.floor(autoScrollX*p + 0.5f);
		int deltay = (int)Math.floor(autoScrollY*p + 0.5f);
		if(deltax != 0 || deltay != 0)
		{
			scrollToLimited(getMapView(), getMapView().getScrollX() + deltax, getMapView().getScrollY() + deltay, true);
			autoScrollX -= deltax;
			autoScrollY -= deltay;
			long t = System.currentTimeMillis();
			autoScrollLastTime = t;
		}
	}

	public void updateAutoScroll()
	{
		if(!optScrollSmoothly)
		{
			return;	
		}
		if(autoScrollX != 0 || autoScrollY != 0)
		{
			long t = System.currentTimeMillis();
			long dt = 0;
			if(autoScrollLastTime >= 0)
			{
				dt = t - autoScrollLastTime;
			}
			else
			{
				dt = t;				
			}

			float p = 1.0f - (float)Math.exp(-(float)dt/150.0f);
			performAutoScroll(p);
		}
	}

	public void updateScrollWithPlayer()
	{
		PlayerPos pp = new PlayerPos();
		getPlayerPosInView(pp);
		int playerposx = pp.posX;
		int playerposy = pp.posY;
		if(optScrollWithPlayer)
		{
			NetHackView view = getMapView();
			if(!view.pendingRedraw && (scrollWithPlayerLastPosX != playerposx || scrollWithPlayerLastPosY != playerposy || scrollWithPlayerRefresh) && playerposx >= 0 && playerposy >= 0)
			{
				int newplayerpixelposx = view.computeViewCoordX(playerposx) + view.squareSizeX/2;
				int newplayerpixelposy = view.computeViewCoordY(playerposy) + view.squareSizeY/2;

				float relposx = ((float)(newplayerpixelposx - view.getScrollX()))/view.getWidth();
				float relposy = ((float)(newplayerpixelposy - view.getScrollY()))/view.getHeight();

				float margin = 0.25f;
				int mintiles = 2;

				float marginpixels = margin*Math.min(view.getWidth(), view.getHeight());
				float marginx = (Math.max(marginpixels, mintiles*view.squareSizeX) + 0.5f*view.squareSizeX)/(float)view.getWidth();
				float marginy = (Math.max(marginpixels, mintiles*view.squareSizeY) + 0.5f*view.squareSizeY)/(float)view.getHeight();

				marginx = Math.min(marginx, 0.5f);
				marginy = Math.min(marginy, 0.5f);

				int dx = 0, dy = 0;
				if(relposx < marginx || relposx > 1.0f - marginx)
				{
					float scrollrelx;
					if(relposx < 0.5)
					{	
						scrollrelx = relposx - marginx;
					}
					else
					{
						scrollrelx = relposx - (1.0f - marginx);
					}
					dx = (int)Math.floor(scrollrelx*view.getWidth() + 0.5f);
				}
				float scrollrely = 0.0f;
				if(relposy < marginy || relposy > 1.0f - marginy)
				{
					if(relposy < 0.5)
					{	
						scrollrely = relposy - marginy;
					}
					else
					{
						scrollrely = relposy - (1.0f - marginy);
					}
					dy = (int)Math.floor(scrollrely*view.getHeight() + 0.5f);
				}

				if(dx != 0 || dy != 0)
				{
					startAutoScroll(dx, dy);
				}

				scrollWithPlayerLastPosX = playerposx;
				scrollWithPlayerLastPosY = playerposy;
				scrollWithPlayerRefresh = false;
			}
		}
		else
		{
			scrollWithPlayerLastPosX = playerposx;
			scrollWithPlayerLastPosY = playerposy;
			scrollWithPlayerRefresh = false;
		}
	}

	public int scrollWithPlayerLastPosX = -1;
	public int scrollWithPlayerLastPosY = -1;
	public boolean scrollWithPlayerRefresh = false;

	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			if(deferredCenterOnPlayer)
			{
				centerOnPlayer();
			}
			updateScrollWithPlayer();
			updateAutoScroll();
				
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
				getMapView().invalidate();
				return;
			}

			if(NetHackGetPlayerPosShouldRecenter() != 0)
			{
				// This doesn't seem to work very well in pure TTY mode. 
				if(!isPureTTYMode())
				{
					centerOnPlayer();
				}
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
							if(currentTerminalView == currentDbgTerminalView && currentTerminalView != null)
							{
								writeTranscript(currentString);
							}
							if(currentTerminalView == null && currentTiledView == null)
							{
								Log.i("NetHackDbg", currentString);
							}
							else if(currentTiledView != null)
							{
								currentTiledView.write(currentString);
							}
							else
							{
								currentTerminalView.write(currentString);
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
							if((currentTerminalView == null) && currentTiledView == null && (preLogView != null))
							{
								currentTerminalView = preLogView;
								currentTiledView = null;
								preLogView = null;
							}
							else
							{
								currentTerminalView = mainView;
								currentTiledView = null;
							}
						}
						else if(c == '1')
						{
							currentTerminalView = messageView;
							currentTiledView = null;
						}
						else if(c == '2')
						{
							currentTerminalView = statusView;
							currentTiledView = null;
						}
						else if(c == '4')
						{
							currentTerminalView = menuView;
							currentTiledView = null;
						}
						else if(c == '5')
						{
							currentTerminalView = null;
							currentTiledView = tiledView;
						}
						else if(c == 'S')
						{
							if(currentTerminalView == menuView)
							{
								menuShown = true;
								menuView.scrollTo(0, 0);
								updateLayout();
							}
						}
						else if(c == 'H')
						{
							if(currentTerminalView == menuView)
							{
								menuShown = false;
								updateLayout();
							}
						}
						else if(c == '3')
						{
							// TEMP
							if(currentTerminalView != null)
							{
								preLogView = currentTerminalView;
							}
							currentTerminalView = null;							
						}
						else if(c == 'C')
						{
							if(currentTerminalView != null)
							{
								currentTerminalView.setDrawCursor(!currentTerminalView.getDrawCursor());
								//currentView.invalidate();
							}
						}
						else if(c == 'R')	// On Rogue level, or otherwise switching to TTY view.
						{
							tilesEnabled = false;
							mainView.terminal.clearScreen();
							rebuildViews(false);

							scrollWithPlayerRefresh = true;
						}
						else if(c == 'r')	// Not on Rogue level
						{
							tilesEnabled = true;
							if(tiledView != null)
							{
								tiledView.terminal.clearScreen();
							}
							rebuildViews(false);				

							scrollWithPlayerRefresh = true;
						}
						escSeq = escSeqAndroid = false;	
					}
				}
				if(!escSeq)
				{
					if(currentTerminalView == currentDbgTerminalView && currentTerminalView != null)
					{
						writeTranscript(currentString);
					}
					if(currentTerminalView == null && currentTiledView == null)
					{
						Log.i("NetHackDbg", currentString);
					}
					else if(currentTiledView != null)
					{
						currentTiledView.write(currentString);
					}
					else
					{
						currentTerminalView.write(currentString);
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

	public void chmod(String filename, int permissions)
	{
		// This was a bit problematic - there is an android.os.FileUtils.setPermissions()
		// function, but apparently that is not a part of the supported interface.
		// I found some other options:
		// - java.io.setReadOnly() exists, but seems limited.
		// - java.io.File.setWritable() is a part of Java 1.6, but doesn't seem to exist in Android.
		// - java.nio.file.attribute.PosixFilePermission also doesn't seem to exist under Android.
		// - doCommand("/system/bin/chmod", permissions, filename) was what I used to do, but it was crashing for some.
		// I don't think these permissions are actually critical for anything in the application,
		// so for now, we will try to use the undocumented function and just be careful to catch any exceptions
		// and print some output spew. /FF
		
		try
		{
		    Class<?> fileUtils = Class.forName("android.os.FileUtils");
		    Method setPermissions =
		        fileUtils.getMethod("setPermissions", String.class, int.class, int.class, int.class);
		    int a = (Integer) setPermissions.invoke(null, filename, permissions, -1, -1);
		    if(a != 0)
		    {
				Log.i("NetHackDbg", "android.os.FileUtils.setPermissions() returned " + a + " for '" + filename + "', probably didn't work.");
		    }
		}
		catch(ClassNotFoundException e)
		{
			Log.i("NetHackDbg", "android.os.FileUtils.setPermissions() failed - ClassNotFoundException.");
		}
		catch(IllegalAccessException e)
		{
			Log.i("NetHackDbg", "android.os.FileUtils.setPermissions() failed - IllegalAccessException.");
		}
		catch(InvocationTargetException e)
		{
			Log.i("NetHackDbg", "android.os.FileUtils.setPermissions() failed - InvocationTargetException.");
		}
		catch(NoSuchMethodException e)
		{
			Log.i("NetHackDbg", "android.os.FileUtils.setPermissions() failed - NoSuchMethodException.");
		}
	}
	
	public void mkdir(String dirname)
	{
		// This is how it used to be done, but it's probably not a good idea
		// to rely on some external command in a hardcoded path... /FF
		//	doCommand("/system/bin/mkdir", dirname, "");

		boolean status = new File(dirname).mkdir();

		// Probably good to keep the debug spew here for now. /FF
		if(status)
		{
			Log.i("NetHackDbg", "Created dir '" + dirname + "'");

			// Probably best to keep stuff accessible, for now.
			chmod(dirname, 0777);
		}
		else
		{
			Log.i("NetHackDbg", "Failed to create dir '" + dirname + "', may already exist");
		}
	}

	public String getAppDir()
	{
		return appDir;
	}

	public String getNetHackDir()
	{
		return getAppDir() + "/nethackdir"; 
	}
	public void run()
	{
		if(!gameInitialized)
		{
			// Up until version 1.2.1, the application hardcoded the path.
			// Not sure if this caused a problem in practice, but it's possible
			// that for example people running the application from the SD card
			// with a mod could run into trouble, and it's much more proper to
			// use getFilesDir(). But, unfortunately, that may not actually return
			// the same value for people with existing installations (in my case,
			// it returns "/data/data/com.nethackff/files", so we have to be really
			// careful to not lose saved data. For that reason, we check for the
			// presence of the "version.txt" file at the old hardcoded location,
			// and if it's there, we continue to use the old location.
			String obsoletePath = "/data/data/com.nethackff";
			if(new File(obsoletePath + "/version.txt").exists())
			{
				appDir = obsoletePath;
			}
			else
			{
				appDir = getFilesDir().getAbsolutePath();	
			}
			Log.i("NetHackDbg", "Using directory '" + appDir + "' for application files.");

			String nethackdir = getNetHackDir();

			if(!compareAsset("version.txt"))
			{
				mkdir(nethackdir);
				mkdir(nethackdir + "/save");

				copyNetHackData();

				copyAsset("version.txt");
				copyAsset("NetHack.cnf", nethackdir + "/.nethackrc");
				copyAsset("charset_amiga.cnf", nethackdir + "/charset_amiga.cnf");
				copyAsset("charset_ibm.cnf", nethackdir + "/charset_ibm.cnf");
				copyAsset("charset_128.cnf", nethackdir + "/charset_128.cnf");
			}

			uiModeActual = optUIModeNew;
			int uimode = 1;
			if(uiModeActual == UIMode.AndroidTTY)
			{
				uimode = 0;
				tilesEnabled = false;
			}
			if(uiModeActual == UIMode.AndroidTiled)
			{
				uimode = 2;
				tilesEnabled = true;	// Native code knows to expect this.
			}
			if(NetHackInit(uimode, nethackdir) == 0)
			{
				// TODO
				return;
			}

			messageView.terminal.clearScreen();	// Remove the "Please wait..." stuff.

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
		stopCommThread();
		super.onDestroy();
		//TestShutdown();
	}

	// This should work, but relying on external commands is generally undesirable,
	// and all current use of it has been eliminated. /FF
	/*
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
	*/

	public boolean compareAsset(String assetname)
	{
		boolean match = false;

		String destname = getAppDir() + "/" + assetname;
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
		copyAsset(assetname, getAppDir() + "/" + assetname);
	}
	
	public void copyAsset(String srcname, String destname)
	{
		File newasset = new File(destname);
		try
		{
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(this.getAssets().open(srcname));
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
			mainView.terminal.write("Failed to copy file '" + srcname + "'.\n");
		}
	}

	public void copyFileRaw(String srcname, String destname) throws IOException
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
		catch(IOException ex)
		{
			throw ex;
		}
	}
	public void copyFile(String srcname, String destname)
	{
		try
		{
			copyFileRaw(srcname, destname);
		}
		catch(IOException ex)
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
				String destname = getNetHackDir() + "/" + assets[i]; 
				copyAsset("nethackdir/" + assets[i], destname);
				chmod(destname, 0666);
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

	public void scrollToLimited(NetHackView scrollView, int newscrollx, int newscrolly, boolean setnewdesiredcentertoactual)
	{
		int termx, termy;
		if(scrollView == menuView)
		{
			termx = menuView.squareSizeX*menuView.sizeX;
//			termy = menuView.squareSizeY*menuView.sizeY;
			termy = menuView.squareSizeY*menuView.getNumDisplayedLines();

		}
		else if(scrollView == tiledView)
		{
			termx = tiledView.squareSizeX*tiledView.sizeX;
			termy = tiledView.squareSizeY*tiledView.sizeY;
		}
		else
		{
			termx = mainView.squareSizeX*mainView.sizeX;
			termy = mainView.squareSizeY*mainView.sizeY;
		}

		if(newscrollx < 0)
		{
			newscrollx = 0;
		}
		if(newscrolly < 0)
		{
			newscrolly = 0;
		}

		int maxx = termx - scrollView.getWidth();
		int maxy = termy - scrollView.getHeight();
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

		scrollView.scrollTo(newscrollx, newscrolly);

		if(setnewdesiredcentertoactual)
		{
			scrollView.desiredCenterPosX = newscrollx + scrollView.getWidth()*0.5f;
			scrollView.desiredCenterPosY = newscrolly + scrollView.getHeight()*0.5f;
		}
	}
	
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) 
	{
		NetHackView scrollView = getMapView();
	
		if(!isPureTTYMode() && menuShown)
		{
			scrollView = menuView;
		}

		int newscrollx = scrollView.getScrollX() + (int)distanceX;
		int newscrolly = scrollView.getScrollY() + (int)distanceY;
		scrollToLimited(scrollView, newscrollx, newscrolly, true);

		stopAutoScroll();

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
		executeTouchAction(optTouchscreenHold, e);
	}
	public void onShowPress(MotionEvent e)
	{
	}

	public void getSquareFromMapTouch(MotionEvent e, int squarexyout[])
	{
		NetHackView mapview = getMapView();

		// TODO: Think more about this - should at least store it, maybe.
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		int loconscreen[] = new int[2];
		mapview.getLocationOnScreen(loconscreen);

		// TODO: Check if within view?
		int viewx = (int)Math.floor(e.getRawX() - loconscreen[0] + mapview.getScrollX() + 0.5f);
		int viewy = (int)Math.floor(e.getRawY() - loconscreen[1] + mapview.getScrollY() + 0.5f);
		int squarex = mapview.computeViewColumnFromCoordXClamped(viewx) + mapview.offsetX;
		int squarey = mapview.computeViewColumnFromCoordYClamped(viewy) + mapview.offsetY;
		squarexyout[0] = squarex;
		squarexyout[1] = squarey;
	}
	public void executeTouchAction(TouchscreenMovement action, MotionEvent e)
	{
		NetHackView mapview = getMapView();
		if(mapview == null)
		{
			return;
		}

		if(action == TouchscreenMovement.MouseClick)
		{
			int squarexy[] = new int[2];
			getSquareFromMapTouch(e, squarexy);
			NetHackMapTap(squarexy[0], squarexy[1]);
		}
		else if(action == TouchscreenMovement.Grid3x3)
		{
			// TODO: Think more about this - should at least store it, maybe.
			DisplayMetrics metrics = new DisplayMetrics();
			getWindowManager().getDefaultDisplay().getMetrics(metrics);

			int loconscreen[] = new int[2];
			mapview.getLocationOnScreen(loconscreen);

			float xx = e.getRawX()/(float)metrics.widthPixels;
			float yy = (e.getRawY() - loconscreen[1])/(float)mapview.getHeight();
			int dx = ((xx <= 0.333f) ? -1 : (xx >= 0.667f ? 1 : 0));
			int dy = ((yy <= 0.333f) ? -1 : (yy >= 0.667f ? 1 : 0));

			MoveDir dir = MoveDir.None;
			// Very lame... should do something with the numbers here, except
			// Java makes it hard to assign values to enums... can probably use an array to look up in.
			if(dy == -1)
			{
				if(dx == -1)
				{
					dir = MoveDir.UpLeft;
				}
				else if(dx == 1)
				{
					dir = MoveDir.UpRight;
				}
				else
				{
					dir = MoveDir.Up;
				}
			}
			else if(dy == 1)
			{
				if(dx == -1)
				{
					dir = MoveDir.DownLeft;
				}
				else if(dx == 1)
				{
					dir = MoveDir.DownRight;
				}
				else
				{
					dir = MoveDir.Down;
				}
			}
			else
			{
				if(dx == -1)
				{
					dir = MoveDir.Left;
				}
				else if(dx == 1)
				{
					dir = MoveDir.Right;
				}
				else
				{
					dir = MoveDir.Center;
				}		
			}
			if(dir != MoveDir.Center && dir != MoveDir.None)
			{
				char c = getCharForDir(dir);
				String tmp = "";
				tmp += c;
				NetHackTerminalSend(tmp);
			}
		}
		else if(action == TouchscreenMovement.CenterOnPlayer)
		{
			centerOnPlayer();
		}
		else if(action == TouchscreenMovement.CenterOnLocation)
		{
			int squarexy[] = new int[2];
			getSquareFromMapTouch(e, squarexy);
			centerOnSquare(squarexy[0], squarexy[1]);
		}
	}

	public boolean onSingleTapUp(MotionEvent e)
	{
		executeTouchAction(optTouchscreenTap, e);

		return true;
	}

	class PlayerPos
	{
		int posX, posY;
	};

	public void getPlayerPosInView(PlayerPos p)
	{
		// Probably would be better to get these two with one function call, but
		// seems a bit messy to return two values at once through JNI.
		int posx = NetHackGetPlayerPosX();
		int posy = NetHackGetPlayerPosY();

		// This is a bit funky. I think the difference of two between posx and posy
		// comes from two different things:
		// - NetHack subtracts one from the column as stored in u.ux, but not from the row? 
		//   (see wintty.c:    cw->curx = --x;	/* column 0 is never used */
 		// - Y offset of 1 in tty_create_nhwindow():
		//	 (see wintty.c:    newwin->offy = 1;
		posx--;
		posy++;

		if(isTiledViewMode())
		{
			posx -= tiledView.offsetX;
			posy -= tiledView.offsetY;

			posy--;
		}
		else
		{
			posx -= mainView.offsetX;
			posy -= mainView.offsetY;
		}
		p.posX = posx;
		p.posY = posy;
	}

	boolean deferredCenterOnPlayer = false;
	
	public void centerOnPlayer()
	{
		NetHackView view = getMapView();
		if(view.getWidth() == 0 || view.getHeight() == 0)
		{
			// This is a bit of a hack - it looks like we sometimes (like when starting the game)
			// get here before the view has been measured or something, and end up getting back
			// 0 as the dimensions. This would make us scroll to the wrong position. To avoid this,
			// we just set this flag, which will make us try again on the next update. There
			// is probably some better solution, but this seems to work OK.
			deferredCenterOnPlayer = true;
			return;
		}
		deferredCenterOnPlayer = false;

		PlayerPos pp = new PlayerPos();
		getPlayerPosInView(pp);
		int posx = pp.posX;
		int posy = pp.posY;
		centerOnSquare(posx, posy);
	}

	public void centerOnSquare(int posx, int posy)
	{
		NetHackView view = getMapView();
		if(view.getWidth() == 0 || view.getHeight() == 0)
		{
			return;
		}
		int cursorcenterx = posx*view.squareSizeX + view.squareSizeX/2;
		int cursorcentery = posy*view.squareSizeY + view.squareSizeY/2;
		int newscrollx = cursorcenterx - view.getWidth()/2;
		int newscrolly = cursorcentery - view.getHeight()/2;

		startAutoScroll(newscrollx - view.getScrollX(), newscrolly - view.getScrollY());
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

		super.onPause();
	}

	public void onStart()
	{
		super.onStart();

		UIMode uiModeBefore = optUIModeNew;

		int textsizebefore = getOptFontSize();
		boolean allowreformatbefore = optAllowTextReformat;

		CharacterSet characterSetBefore = optCharacterSet;
		NetHackTerminalView.ColorSet colorSetBefore = optCharacterColorSet;
		Orientation orientationBefore = optOrientation;

		boolean keyboardInPortraitBefore = optKeyboardShownInConfig[ScreenConfig.Portrait.ordinal()];
		boolean keyboardInLandscapeBefore = optKeyboardShownInConfig[ScreenConfig.Landscape.ordinal()];

		String tilesetbefore = optTileSetName;
		
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
			// Switching in or out of pure TTY mode still requires a full
			// re-initialization of the application.
			if(optUIModeNew == UIMode.PureTTY || uiModeActual == UIMode.PureTTY)
			{
				Dialog dialog = new Dialog(this);
				dialog.setContentView(R.layout.uimodechanged);
				dialog.setTitle(getString(R.string.uimodechanged_title));
				dialog.show();
			}
			else
			{
				// Switching between tiled and TTY Android mode we should be able
				// to do without restarting.
				if(optUIModeNew == UIMode.AndroidTTY)
				{
					NetHackSetTilesEnabled(0);
				}
				else
				{
					NetHackSetTilesEnabled(1);		
				}
				uiModeActual = optUIModeNew;
			}				
		}

		if(optCharacterSet != characterSetBefore)
		{
			int index = -1;
			switch(optCharacterSet)
			{
				case ANSI128:
					index = 0;
					break;
				case IBM:
					index = 1;
					break;
				case Amiga:
					index = 2;
					break;
					
			}
			if(index >= 0)
			{
				// TEMP
				Log.i("NetHackDbg", "Switching to mode " + index);

				NetHackSwitchCharSet(index);
			}
		}
		
		boolean blackonwhite = (optColorMode == ColorMode.BlackOnWhite);
		mainView.setWhiteBackgroundMode(blackonwhite);
		menuView.setWhiteBackgroundMode(blackonwhite);
		messageView.setWhiteBackgroundMode(blackonwhite);
		statusView.setWhiteBackgroundMode(blackonwhite);
		int textsize = getOptFontSize();
		mainView.setTextSize(textsize);
		messageView.setTextSize(textsize);
		statusView.setTextSize(textsize);
		menuView.setTextSize(textsize);

		if(orientationBefore != optOrientation)
		{
			switch(optOrientation)
			{
				case Sensor:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
					break;
				case Portrait:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
					break;
				case Landscape:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
					break;
				case Unspecified:
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
					break;
			}
		}

		boolean shouldrebuild = false;
		if(textsizebefore != textsize || optAllowTextReformat != allowreformatbefore || optCharacterColorSet != colorSetBefore)
		{
			shouldrebuild = true;
		}
		if(keyboardInPortraitBefore != optKeyboardShownInConfig[ScreenConfig.Portrait.ordinal()])
		{
			shouldrebuild = true;
		}
		if(keyboardInLandscapeBefore != optKeyboardShownInConfig[ScreenConfig.Landscape.ordinal()])
		{
			shouldrebuild = true;
		}
		if(uiModeActual == UIMode.AndroidTiled)
		{
			if(!tilesetbefore.equals(optTileSetName) || tiledView.tileBitmap == null)
			{
				shouldrebuild = true;
				usePreferredTileSet();
			}
		}
		if(shouldrebuild)
		{
			rebuildViews(false);
		}
		if(tiledView != null)
		{
			tiledView.setWhiteBackgroundMode(blackonwhite);
		}
	}	

	LinearLayout screenLayout;

	enum ScreenConfig
	{
		Landscape,
		Portrait
	}
	ScreenConfig screenConfig;
	boolean menuShown = false;

	boolean []optKeyboardShownInConfig;

	void updateLayout()
	{
		mainView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 1.0f));
		menuView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 1.0f));
		messageView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 0.0f));
		statusView.setLayoutParams(
				new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.WRAP_CONTENT, 0.0f));

		if(tiledView != null)
		{
			tiledView.setLayoutParams(
					new LinearLayout.LayoutParams(LayoutParams.FILL_PARENT,
					LayoutParams.WRAP_CONTENT, 1.0f));
		}

		screenLayout.removeAllViews();
		if(!menuShown)
		{
			boolean pureTTY = isPureTTYMode();

			//layout.addView(dbgTerminalTranscript);
			if(!pureTTY)
			{
				screenLayout.addView(messageView);
			}
			if(isTiledViewMode())
			{
				screenLayout.addView(tiledView);
			}
			else
			{
				screenLayout.addView(mainView);
			}

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
		if(optKeyboardShownInConfig[screenConfig.ordinal()])
		{
			screenLayout.addView(virtualKeyboard.virtualKeyboardView);
		}

		getMapView().invalidate();
	}

	
	public void initDisplay()
	{
		virtualKeyboard = new NetHackKeyboard(this);

		initViewsCommon(true);

		messageView.setDrawCursor(false);
		statusView.setDrawCursor(false);

		currentTerminalView = mainView;
		currentTiledView = null;

		//currentDbgTerminalView = messageView;
		if(currentDbgTerminalView != null)
		{
			Display display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
			int sizeX = display.getWidth();

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
	}
	
	int messageRows = 2;
	int statusRows = 2;

	public int getOptFontSize()
	{
		int sz = 10;
		switch(optFontSize)
		{
			case FontSize10:
				sz = 10;
				break;
			case FontSize11:
				sz = 11;
				break;
			case FontSize12:
				sz = 12;
				break;
			case FontSize13:
				sz = 13;
				break;
			case FontSize14:
				sz = 14;
				break;
			case FontSize15:
				sz = 15;
				break;
			default:
				break;
		}
		return sz;
	}

	Bitmap fontBitmap;

	class TileSetInfo
	{
		public String packageName;
		public String tileSetName;
		public String bitmapName;
		public String infoString;
		public int tileSizeX;
		public int tileSizeY;
		public int defaultZoomPercentage;
	};

	public void useTileSet(TileSetInfo info)
	{
		int tilesizex = 0, tilesizey = 0;
//			tileBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.x11tiles);

		Uri path = Uri.parse("android.resource://" + info.packageName + "/drawable/" + info.bitmapName);

		try
		{
			Bitmap bmp = Media.getBitmap(getContentResolver(), path);
			if(bmp != null)
			{
				tiledView.setBitmap(bmp, info.tileSizeX, info.tileSizeY, info.defaultZoomPercentage);
			}
		}
		catch(FileNotFoundException e)
		{
		}
		catch(IOException e)
		{
		}
	}

	public LinkedList<TileSetInfo> getTileSetsInPackage(ApplicationInfo curr)
	{
		LinkedList<TileSetInfo> tilesetlist = new LinkedList<TileSetInfo>();

		try
		{
			{
				try
				{
					Resources res = getBaseContext().getPackageManager().getResourcesForApplication(curr);
					//int resId = getResources().getIdentifier("@string/TileSetName", "string", curr.packageName);
					//int resId = res.getIdentifier("@string/TileSetName", "string", curr.packageName);
					int idname = res.getIdentifier("TileSetNames", "array", curr.packageName);
					int idtilesizex = res.getIdentifier("TileSetTileSizesX", "array", curr.packageName);
					int idtilesizey = res.getIdentifier("TileSetTileSizesY", "array", curr.packageName);
					int idbitmap = res.getIdentifier("TileSetBitmaps", "array", curr.packageName);
					int idzoom = res.getIdentifier("TileSetDefaultZoom", "array", curr.packageName);
					int idinfo = res.getIdentifier("TileSetInfo", "array", curr.packageName);

					String[] tilesetnames = res.getStringArray(idname);
					for(int i = 0; i < tilesetnames.length; i++)
					{
						TileSetInfo info = new TileSetInfo();
						info.packageName = curr.packageName;
						info.tileSetName = tilesetnames[i];
						info.bitmapName = res.getStringArray(idbitmap)[i];
						info.infoString = res.getStringArray(idinfo)[i];
						info.tileSizeX = res.getIntArray(idtilesizex)[i];
						info.tileSizeY = res.getIntArray(idtilesizey)[i];
						info.defaultZoomPercentage = res.getIntArray(idzoom)[i];
						tilesetlist.add(info);
					}
				}
				catch(NotFoundException e)
				{
				}
			}
		}
		catch(NameNotFoundException e)
		{
		}
		return tilesetlist;
	}
	
	public LinkedList<TileSetInfo> findTileSets()
	{
		LinkedList<TileSetInfo> tilesetlist = new LinkedList<TileSetInfo>();

		try
		{
			// A bit lame, should be a better way to find the ApplicationInfo of ourselves.
			tilesetlist.addAll(getTileSetsInPackage(this.getPackageManager().getApplicationInfo("com.nethackff", 0)));
		}
		catch(NameNotFoundException e)
		{
		}

		List<ApplicationInfo> appsList = getBaseContext().getPackageManager().getInstalledApplications(0);
		Iterator<ApplicationInfo> appsIter = appsList.iterator(); 
		int tilesizex = 1, tilesizey = 1;
		while(appsIter.hasNext())
		{ 
			ApplicationInfo curr = appsIter.next(); 
			if(curr.packageName.startsWith("com.nethackff_tiles_"))
			{
				tilesetlist.addAll(getTileSetsInPackage(curr));
			}
		}

		return tilesetlist;
	}

	public void usePreferredTileSet()
	{
		LinkedList<TileSetInfo> tilesetlist = findTileSets();
		if(tilesetlist.size() > 0)
		{
			ListIterator<TileSetInfo> iter = tilesetlist.listIterator();
			boolean found = false;
			while(iter.hasNext())
			{
				TileSetInfo info = iter.next();
				if(info.tileSetName.equals(optTileSetName))
				{
					useTileSet(info);
					found = true;
					break;
				}
			}
			if(!found)
			{
				useTileSet(tilesetlist.get(0));
			}
		}
	}
		
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		altKey = new ModifierKey();
		ctrlKey = new ModifierKey();
		shiftKey = new ModifierKey();

		requestWindowFeature(Window.FEATURE_NO_TITLE);
//        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NO_STATUS_BAR,
//      		WindowManager.LayoutParams.FLAG_NO_STATUS_BAR);

		int width = 80;
		int height = 24;		// 26

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

		optKeyboardShownInConfig = new boolean[ScreenConfig.values().length];
		optKeyboardShownInConfig[ScreenConfig.Portrait.ordinal()] = false;
		optKeyboardShownInConfig[ScreenConfig.Landscape.ordinal()] = false;

		getPrefs();
		optCharacterSet = CharacterSet.Invalid;
		optOrientation = Orientation.Invalid;	// Make sure it gets detected in onStart().

		if(!gameInitialized)
		{
			uiModeActual = optUIModeNew;
		}

		int textsize = getOptFontSize();
		mainView = new NetHackTerminalView(this, mainTerminalState);
		mainView.setTextSize(textsize);
		//		mainView.offsetY = messageRows;
		if(!isPureTTYMode())
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

		fontBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.dungeonfont);
		mainView.fontBitmap = fontBitmap;

		tiledView = new NetHackTiledView(this);
		// TODO: Try to avoid the call to usePreferredTileSet() in AndroidTTY mode -
		// it may waste some resources.
		if(uiModeActual == UIMode.AndroidTiled || uiModeActual == UIMode.AndroidTTY)
		{
			usePreferredTileSet();
		}
		tiledView.sizeY -= messageRows + statusRows;
		tiledView.offsetY = 1;
		tiledView.computeSizePixels();

		Configuration config = getResources().getConfiguration();		
		if(config.orientation == Configuration.ORIENTATION_PORTRAIT)
		{
			screenConfig = ScreenConfig.Portrait;
		}
		else
		{
			screenConfig = ScreenConfig.Landscape;
		}
		
		messageView = new NetHackTerminalView(this, messageTerminalState);
		statusView = new NetHackTerminalView(this, statusTerminalState);
		menuView = new NetHackTerminalView(this, menuTerminalState); 
		messageView.setTextSize(textsize);
		statusView.setTextSize(textsize);
		menuView.setTextSize(textsize);

		// Needed for gold symbol on special Rogue level:
		statusView.fontBitmap = fontBitmap;

		initDisplay();
		
		if(!gameInitialized)
		{
//			mainView.terminal.write("Please wait, initializing...\n");
			messageView.terminal.write("Please wait, initializing...\n");
		}

		gestureScanner = new GestureDetector(this);
	}

	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		getMenuInflater().inflate(R.layout.menu, menu);
		return true;
	}

	public void configExport(String outname)
	{
		try
		{
			copyFileRaw(getNetHackDir() + "/.nethackrc", outname);

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

	public void configImport(String inname)
	{
		try
		{
			copyFileRaw(inname, getNetHackDir() + "/.nethackrc"); 

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

	public void configImportExport(String filename, boolean cfgimport)
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
	
	public void configImportExportDialog(final boolean cfgimport)
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
				Intent intent = new Intent(this, NetHackPreferences.class);
				Bundle bundle = new Bundle();

				LinkedList<TileSetInfo> tilesetlist = findTileSets();
				String tilesetnames[] = new String[tilesetlist.size()];
				String tilesetvalues[] = new String[tilesetlist.size()];
				String tilesetinfo[] = new String[tilesetlist.size()];
				
				ListIterator<TileSetInfo> iter = tilesetlist.listIterator();
				int index = 0;
				while(iter.hasNext())
				{
					TileSetInfo info = iter.next();
					tilesetnames[index] = info.tileSetName;
					tilesetvalues[index] = info.tileSetName;
					tilesetinfo[index] = info.infoString;
					index++;
				}
				bundle.putStringArray("TileSetNames", tilesetnames);
				bundle.putStringArray("TileSetValues", tilesetvalues);
				bundle.putStringArray("TileSetInfo", tilesetinfo);
				intent.putExtras(bundle);
				startActivity(intent);
				return true;
			}
			case R.id.importconfig:
			{
				configImportExportDialog(true);
				return true;
			}
			case R.id.exportconfig:
			{
				configImportExportDialog(false);
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
		optKeyBindBack = getKeyActionEnumFromString(prefs.getString("BackButtonFunc", "ForwardToSystem"));
		optKeyBindCamera = getKeyActionEnumFromString(prefs.getString("CameraButtonFunc", "VirtualKeyboard"));
		optKeyBindSearch = getKeyActionEnumFromString(prefs.getString("SearchButtonFunc", "CtrlKey"));
		optKeyBindAltLeft = getKeyActionEnumFromString(prefs.getString("LeftAltKeyFunc", "AltKey"));
		optKeyBindAltRight = getKeyActionEnumFromString(prefs.getString("RightAltKeyFunc", "AltKey"));
		optKeyBindShiftLeft = getKeyActionEnumFromString(prefs.getString("LeftShiftKeyFunc", "ShiftKey"));
		optKeyBindShiftRight = getKeyActionEnumFromString(prefs.getString("RightShiftKeyFunc", "ShiftKey"));
		optKeyBindVolumeUp = getKeyActionEnumFromString(prefs.getString("VolumeUpButtonFunc", "ZoomIn"));
		optKeyBindVolumeDown = getKeyActionEnumFromString(prefs.getString("VolumeDownButtonFunc", "ZoomOut"));

		altKey.sticky = prefs.getBoolean("StickyAlt", false);
		ctrlKey.sticky = prefs.getBoolean("StickyCtrl", false);
		shiftKey.sticky = prefs.getBoolean("StickyShift", false);
		optFullscreen = prefs.getBoolean("Fullscreen", true);
		optAllowTextReformat = prefs.getBoolean("AllowTextReformat", true);
		optMoveWithTrackball = prefs.getBoolean("MoveWithTrackball", true);
		optTouchscreenTap = TouchscreenMovement.valueOf(prefs.getString("TouchscreenTap", "MouseClick"));
		optTouchscreenHold = TouchscreenMovement.valueOf(prefs.getString("TouchscreenHold", "CenterOnPlayer"));
		optColorMode = ColorMode.valueOf(prefs.getString("ColorMode", "WhiteOnBlack"));
		optUIModeNew = UIMode.valueOf(prefs.getString("UIMode", "AndroidTiled"));
		optCharacterSet = CharacterSet.valueOf(prefs.getString("CharacterSet", "Amiga"));
		optCharacterColorSet = NetHackTerminalView.ColorSet.valueOf(prefs.getString("CharacterColorSet", "Amiga"));
		optFontSize = FontSize.valueOf(prefs.getString("FontSize", "FontSize10"));
		optOrientation = Orientation.valueOf(prefs.getString("Orientation", "Unspecified"));

		optTileSetName = prefs.getString("TileSet", "Standard 16x16");	// Could potentially get the default from first in getTileSetsInPackage() list.
		optKeyboardShownInConfig[ScreenConfig.Portrait.ordinal()] = prefs.getBoolean("KeyboardShownInPortrait", true);
		optKeyboardShownInConfig[ScreenConfig.Landscape.ordinal()] = prefs.getBoolean("KeyboardShownInLandscape", false);

		optScrollSmoothly = prefs.getBoolean("ScrollSmoothly", true);
		optScrollWithPlayer = prefs.getBoolean("ScrollToFollowPlayer", true);
	}

	public static String appDir;
	public static boolean terminalInitialized = false;
	public static boolean gameInitialized = false;
	public static NetHackTerminalState mainTerminalState;
	public /*static*/ NetHackTerminalState messageTerminalState;
	public /*static*/ NetHackTerminalState statusTerminalState;
	public NetHackTerminalState menuTerminalState;

	public native int NetHackInit(int puretty, String nethackdir);
	public native void NetHackShutdown();
	public native String NetHackTerminalReceive();
	public native void NetHackTerminalSend(String str);
	public native void NetHackMapTap(int x, int y);
	public native int NetHackHasQuit();
	public native int NetHackSave();
	public native void NetHackSetScreenDim(int msgwidth, int nummsglines, int statuswidth);
	public native void NetHackRefreshDisplay();
	public native void NetHackSwitchCharSet(int charsetindex);
	public native void NetHackSetTilesEnabled(int tilesenabled);
	
	public native int NetHackGetPlayerPosX();
	public native int NetHackGetPlayerPosY();
	public native int NetHackGetPlayerPosShouldRecenter();
	
	static
	{
		System.loadLibrary("nethack");
	}
}
