package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;

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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class TerminalView extends View
{
	String outputText;

	private char[] textBuffer;
	int numRows;
	int numColumns;

	int currentRow;
	int currentColumn;

	public void lineFeed()
	{
		currentRow++;
		currentColumn = 0;

		if(currentRow >= numRows)
		{
			for(int row = 1; row < numRows; row++)
			{
				for(int col = 0; col < numColumns; col++)
				{
					textBuffer[(row - 1)*numColumns + col] = textBuffer[row*numColumns + col];
				}
			}
			for(int col = 0; col < numColumns; col++)
			{
				textBuffer[(numRows - 1)*numColumns + col] = ' ';
			}
			currentRow--;
		}
	}

	public void writeRaw(char c)
	{
		if(currentColumn >= numColumns)
		{
			lineFeed();
		}

		if(currentColumn < numColumns && currentRow < numRows)
		{
			textBuffer[currentRow*numColumns + currentColumn] = c;
		}
		currentColumn++;
	}

	private static final int ESC_NONE = 0;
	private static final int ESC = 1;
	private static final int ESC_LEFT_SQUARE_BRACKET = 2;

	private int escapeState;

	public void startEscapeSequence(int state)
	{
		escapeState = state;
	}

	public void updateEscapeSequence(char c)
	{
		switch(escapeState)
		{
			case ESC:
				updateEscapeSequenceEsc(c);
				break;

			case ESC_LEFT_SQUARE_BRACKET:
				updateEscapeSequenceLeftSquareBracket(c);
				break;

			default:
				// TODO
				escapeState = ESC_NONE;
				break;
		}
	}
	public void updateEscapeSequenceEsc(char c)
	{
		switch(c)
		{
			case '[':
				escapeState = ESC_LEFT_SQUARE_BRACKET;
				escSeqArgVal[0] = 0;
				escSeqArgCnt = -1;
				break;

			default:
				escapeState = ESC_NONE;
				break;
		}
	}

	public static final int kMaxEscParam = 16;
	public int []escSeqArgVal = new int[kMaxEscParam];
	public int escSeqArgCnt = 0;

	public void updateEscapeSequenceLeftSquareBracket(char c)
	{
		switch(c)
		{
			case 'B':
				escapeState = ESC_NONE;
				return;
			case 'D':
				escapeState = ESC_NONE;
				return;
			case 'H':	// Cursor Home
				escapeState = ESC_NONE;
				return;
		}
		if(c >= '0' && c <= '9')
		{
			if(escSeqArgCnt == -1)
			{
				escSeqArgCnt = 0;
			}
			escSeqArgVal[escSeqArgCnt] = escSeqArgVal[escSeqArgCnt]*10 + (c - '0');
		}
		else if(c == ';')
		{
			escSeqArgCnt++;
		}
		// else error?
		escapeState = ESC_NONE;
	}
	public void write(char c)
	{
		switch(c)
		{
			case 0:	// NUL
				break;
			case 7: // BEL
				break;
			case 8:	// BS
				// TODO
				break;
			case 9:	// HT
				// TODO
				break;
			case 13:
				// TODO
				break;
			case 10:	// CR
			case 11:	// VT
			case 12:	// LF
				lineFeed();
				break;
			case 14:	// SO
				// TODO
				break;
			case 15:	// SI
				// TODO
				break;
			case 24:	// CAN
			case 26:	// SUB
				// TODO
				break;
			case 27:	// ESC
				startEscapeSequence(ESC);
				break;
			case 0x9b:	// CSI
				break;
			default:
				if(escapeState == ESC_NONE)
				{
					if(c >= 32)
					{
						writeRaw(c);
					}
				}
				else
				{
					updateEscapeSequence(c);
				}
				break;
		}
	}

	public void write(String s)
	{
		int len = s.length();
		for(int i = 0; i < len; i++)
		{
			write(s.charAt(i));
		}
	}
	
	public TerminalView(Context context, int columns, int rows)
	{
		super(context);

		outputText = "";

		numRows = rows;
		numColumns = columns;
		
		textBuffer = new char[rows*columns];

		for(int i = 0; i < rows*columns; i++)
		{
			textBuffer[i] = ' ';
		}

		currentRow = 0;
		currentColumn = 0;
	}

	public String getContents()
	{
		String r = "";
		for(int i = 0; i < numRows; i++)
		{
			r += getRow(i);
			r += '\n';
		}
		return r;
	}
	
	public String getRow(int row)
	{
		String r;
		int offs = row*numColumns;
		r = "";
		for(int i = 0; i < numColumns; i++)
		{
			r += textBuffer[offs + i];
		}
		return r;
	}
	
	protected void onDraw(Canvas canvas)
	{
		Paint paint = new Paint();
		paint.setARGB(255, 255, 255, 255);
		paint.setTypeface(Typeface.MONOSPACE);
//		paint.setTextSize(14);
		paint.setTextSize(10);
		paint.setAntiAlias(true);
		int charheight = (int)Math.ceil(paint.getFontSpacing());// + paint.ascent());
		int charwidth = (int)paint.measureText("X", 0, 1);
		char tmp[] = {' ', ' '};
		int x = 0, y = 0;
		y += charheight;
		for(int row = 0; row < numRows; row++)
		{
			x = 0;
			for(int col = 0; col < numColumns; col++)
			{
				tmp[0] = textBuffer[row*numColumns + col];
				tmp[1] = '\0';
				canvas.drawText(tmp, 0, 1, (float)x, (float)y, paint);
				x += charwidth;
			}
			y += charheight;
		}
	}
}


public class NetHackApp extends Activity implements Runnable
{
	TerminalView screen;
	
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if(super.onKeyDown(keyCode, event))
		{
			return true;
		}
		String s = "";
		s += (char)event.getUnicodeChar();
		TerminalSend(s);
		screen.invalidate();
		return true;
	}

	private Handler handler = new Handler()
	{
		public void handleMessage(Message msg)
		{
			String s = TerminalReceive();
			if(s.length() != 0)
			{
				screen.write(s);
				screen.invalidate();
			}
		}
    };

    public void run()
    {
    	while(true)
    	{
    		try
    		{
    			handler.sendEmptyMessage(0);
    			Thread.sleep(100);
    		} catch(InterruptedException e)
    		{
    			throw new RuntimeException(e.getMessage());
    		}
    	}
    }

    public void onDestroy()
	{
		TestShutdown();
	}

    public void doCommand(String command, String arg0, String arg1)
    {
    	try {
    		// android.os.Exec is not included in android.jar so we need to use reflection.
    		Class execClass = Class.forName("android.os.Exec");
    		Method createSubprocess = execClass.getMethod("createSubprocess",
    		String.class, String.class, String.class, int[].class);
    		Method waitFor = execClass.getMethod("waitFor", int.class);

    		// Executes the command.
    		// NOTE: createSubprocess() is asynchronous.
    		int[] pid = new int[1];
    		FileDescriptor fd = (FileDescriptor)createSubprocess.invoke(
    				null, command, arg0, arg1, pid);

    		// Reads stdout.
    		// NOTE: You can write to stdin of the command using new FileOutputStream(fd).
    		FileInputStream in = new FileInputStream(fd);
    		BufferedReader reader = new BufferedReader(new InputStreamReader(in));
    		String output = "";
    		try {
    			String line;
    			while ((line = reader.readLine()) != null) {
    				output += line + "\n";
    			}
    		} catch (IOException e) {
    			// It seems IOException is thrown when it reaches EOF.
    		}

    		// Waits for the command to finish.
    		waitFor.invoke(null, pid[0]);
    		
    		// send output to the textbox
    		//screen.write(output);
    	} catch (ClassNotFoundException e) {
    		throw new RuntimeException(e.getMessage());
    	} catch (SecurityException e) {
    		throw new RuntimeException(e.getMessage());
    	} catch (NoSuchMethodException e) {
    		throw new RuntimeException(e.getMessage());
    	} catch (IllegalArgumentException e) {
    		throw new RuntimeException(e.getMessage());
    	} catch (IllegalAccessException e) {
    		throw new RuntimeException(e.getMessage());
    	} catch (InvocationTargetException e) {
    		throw new RuntimeException(e.getMessage());
    	}
	}

    public void copyAsset(String assetname)
    {
    	String destname = "/data/data/com.nethackff/" + assetname;
    	File newasset = new File(destname);
		try {
			newasset.createNewFile();
			BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(newasset));
			BufferedInputStream in = new BufferedInputStream(this.getAssets().open(assetname));
			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}
			out.flush();
			out.close();
			in.close();
		}
		catch (IOException ex)
		{
			screen.write("Failed to copy file '" + assetname + "'.\n");
		}
    }

    public void copyNetHackData()
    {
    	AssetManager am = getResources().getAssets();
    	String assets[] = null;
    	try
    	{
    		assets = am.list("dat");

       		for(int i = 0; i < assets.length; i++)
       		{
       			copyAsset("dat/" + assets[i]);
        	}
    	}
    	catch(IOException e)
    	{
    		throw new RuntimeException(e.getMessage());
    	}
    }

    public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		int width = 80;
		int height = 22;

		screen = new TerminalView(this, width, height);

		doCommand("/system/bin/mkdir", "/data/data/com.nethackff/dat", "");
		copyNetHackData();

		if(TestInit(width, height) == 0)
		{
			return;
		}

		setContentView(screen);

        Thread thread = new Thread(this);
        thread.start();
 	}

	public native int TestInit(int numcols, int numrows); 
	public native void TestShutdown(); 

	public native String TerminalReceive();
	public native void TerminalSend(String str);

	static {
		System.loadLibrary("nethack");
	}
}
