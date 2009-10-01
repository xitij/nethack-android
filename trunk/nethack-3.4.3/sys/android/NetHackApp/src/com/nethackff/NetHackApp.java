package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import android.view.View;
import android.widget.LinearLayout;

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
	private char[] fmtBuffer;
	int numRows;
	int numColumns;

	int currentRow;
	int currentColumn;

	protected void onMeasure(int widthmeasurespec, int heightmeasurespec)
	{
		int minheight = getSuggestedMinimumHeight();
		int minwidth = getSuggestedMinimumWidth(); 

		// TODO: Prevent duplication
		Paint paint = new Paint();
		paint.setTypeface(Typeface.MONOSPACE);
		paint.setTextSize(10);
		paint.setAntiAlias(true);
		int charheight = (int)Math.ceil(paint.getFontSpacing());// + paint.ascent());
		int charwidth = (int)paint.measureText("X", 0, 1);

		int width, height;
		width = numColumns*charwidth;
		height = numRows*charheight;

		height += 2;	// MAGIC!
		
		if(width < minwidth)
		{
			width = minwidth;
		}
		if(height < minheight)
		{
			height = minheight;
		}
		
		int modex = MeasureSpec.getMode(widthmeasurespec);
		int modey = MeasureSpec.getMode(heightmeasurespec);
		if(modex == MeasureSpec.AT_MOST)
		{
			width = Math.min(MeasureSpec.getSize(widthmeasurespec), width);
		}
		else if(modex == MeasureSpec.EXACTLY)
		{
			width = MeasureSpec.getSize(widthmeasurespec);
		}
		if(modey == MeasureSpec.AT_MOST)
		{
			height = Math.min(MeasureSpec.getSize(heightmeasurespec), height);
		}
		else if(modey == MeasureSpec.EXACTLY)
		{
			height = MeasureSpec.getSize(heightmeasurespec);
		}
		setMeasuredDimension(width, height);
	}
	
	public static final int kColBlack = 0;
	public static final int kColRed = 1;
	public static final int kColGreen = 2;
	public static final int kColYellow = 3;
	public static final int kColBlue= 4;
	public static final int kColMagenta = 5;
	public static final int kColCyan = 6;
	public static final int kColWhite = 7;

	int colorForeground = kColWhite, colorBackground = kColBlack;

	char encodeFormat(int foreground, int background, boolean reverse)
	{
		if(reverse)
		{
			foreground = 7 - foreground;
			background = 7 - background;
		}
		return (char)((foreground << 3) + background);
	}

	int decodeFormatForeground(char fmt)
	{
		return (fmt >> 3) & 7;
	}

	int decodeFormatBackground(char fmt)
	{
		return fmt & 7;
	}
	char encodeCurrentFormat()
	{
		return encodeFormat(colorForeground, colorBackground, grReverseVideo);
	}
	void clearScreen()
	{
		for(int i = 0; i < numRows*numColumns; i++)
		{
			textBuffer[i] = ' ';
			fmtBuffer[i] = encodeCurrentFormat();
		}
	}

	void clampCursorPos()
	{
		if(currentRow < 0)
		{
			currentRow = 0;
		}
		else if(currentRow >= numRows)
		{
			// Should we scroll down in this case?
			currentRow = numRows - 1;
		}
		if(currentColumn < 0)
		{
			currentColumn = 0;
		}
		else if(currentColumn >= numColumns)
		{
			currentColumn = numColumns - 1;
		}
	}
	
	void moveCursorRel(int coldelta, int rowdelta)
	{
		currentRow += rowdelta;
		currentColumn += coldelta;
		clampCursorPos();
	}
	
	void moveCursorAbs(int newcol, int newrow)
	{
		currentRow = newrow;
		currentColumn = newcol;
		clampCursorPos();
	}

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
					fmtBuffer[(row - 1)*numColumns + col] = fmtBuffer[row*numColumns + col];
				}
			}
			for(int col = 0; col < numColumns; col++)
			{
				textBuffer[(numRows - 1)*numColumns + col] = ' ';
				fmtBuffer[(numRows - 1)*numColumns + col] = encodeCurrentFormat();
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
			fmtBuffer[currentRow*numColumns + currentColumn] = encodeCurrentFormat();
		}
		currentColumn++;
	}

	public void setCharAtPos(char c, int col, int row)
	{
		if(col >= 0 && col < numColumns && row >= 0 && row < numRows)
		{
			textBuffer[row*numColumns + col] = c;
			fmtBuffer[row*numColumns + col] = encodeCurrentFormat();
		}
	}
	public void writeRawStr(String s)
	{
		int len = s.length();
		for(int i = 0; i < len; i++)
		{
			writeRaw(s.charAt(i));
		}
	}

	private static final int ESC_NONE = 0;
	private static final int ESC = 1;
	private static final int ESC_LEFT_SQUARE_BRACKET = 2;

	private int escapeState;

	public void startEscapeSequence(int state)
	{
		escSeqLen = 0;
		escapeState = state;
	}

	public void updateEscapeSequence(char c)
	{
		if(escSeqLen < kMaxEscSeqLen)
		{
			escSeqStored[escSeqLen++] = c;
		}
		switch(escapeState)
		{
			case ESC:
				updateEscapeSequenceEsc(c);
				break;

			case ESC_LEFT_SQUARE_BRACKET:
				updateEscapeSequenceLeftSquareBracket(c);
				break;

			default:
				reportUnknownSequence();
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
				reportUnknownSequence();
				escapeState = ESC_NONE;
				break;
		}
	}

	public static final int kMaxEscParam = 16;
	public int []escSeqArgVal = new int[kMaxEscParam];
	public int escSeqArgCnt = 0;

	public static final int kMaxEscSeqLen = 64;	// Not sure...
	public char []escSeqStored = new char[kMaxEscSeqLen];
	public int escSeqLen = 0;

	public int getEscSeqArgVal(int deflt)
	{
		if(escSeqArgCnt < 0)
		{
			// No arguments specified.
			return deflt;
		}
		else
		{
			return escSeqArgVal[escSeqArgCnt];
		}
	}

	public void reportUnknownChar(char c)
	{
		if(currentColumn > 1)
		{
			lineFeed();
		}
		writeRawStr("Unknown character: " + (int)c);
		lineFeed();
	}
	
	public void reportUnknownSequence()
	{
		if(currentColumn > 1)
		{
			lineFeed();
		}
		writeRawStr("Unknown Esc sequence: ");
		for(int i = 0; i < escSeqLen; i++)
		{
			writeRaw(escSeqStored[i]);
		}
		lineFeed();
	}

	public boolean grReverseVideo = false;
	
	public void selectGraphicRendition()
	{
		int m = getEscSeqArgVal(0);
		switch(m)
		{
			case 0:
				grReverseVideo = false;
				break;
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
				reportUnknownSequence();
				break;
			case 7:
				grReverseVideo = true;
				break;
			default:
				reportUnknownSequence();
				break;
		}
	}

	public void updateEscapeSequenceLeftSquareBracket(char c)
	{
		switch(c)
		{
			case 'B':	// Move cursor down n lines
				moveCursorRel(0, getEscSeqArgVal(1));
				escapeState = ESC_NONE;
				return;
			case 'C':	// Move cursor right n lines 
				moveCursorRel(getEscSeqArgVal(1), 0);
				escapeState = ESC_NONE;
				return;
			case 'D':	// Move cursor left n lines 
				moveCursorRel(-getEscSeqArgVal(1), 0);
				escapeState = ESC_NONE;
				return;
			case 'A':	// Move cursor up n lines
				moveCursorRel(0, -getEscSeqArgVal(1));
				escapeState = ESC_NONE;
				return;
			case 'H':	// Cursor home
				if(escSeqArgCnt == 1)
				{
					moveCursorAbs(escSeqArgVal[1] - 1, escSeqArgVal[0] - 1);
				}
				else
				{
					moveCursorAbs(0, 0);
				}
				escapeState = ESC_NONE;
				return;
			case 'J':	// Clear screen
				// TODO: Read arguments here.
				clearScreen();
				escapeState = ESC_NONE;
				return;
			case 'K':
				if(getEscSeqArgVal(0) == 0)
				{
					// Clear line from cursor right
					for(int i = currentColumn; i < numColumns; i++)
					{
						setCharAtPos(' ', i, currentRow);
					}
				}
				else if(getEscSeqArgVal(0) == 1)
				{
					// Clear line from cursor left
					for(int i = currentColumn; i >= 0; i--)
					{
						setCharAtPos(' ', i, currentRow);
					}
				}
				else if(getEscSeqArgVal(0) == 2)
				{
					for(int i = 0; i < numColumns; i++)
					{
						setCharAtPos(' ', i, currentRow);
					}
				}
				else
				{
					reportUnknownSequence();
				}
				escapeState = ESC_NONE;
				return;
			case 'm':		// Select graphic rendition
				selectGraphicRendition();
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
			escSeqArgVal[escSeqArgCnt] = 0;
		}
		else
		{
			reportUnknownSequence();
			escapeState = ESC_NONE;
		}
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
				if(currentColumn > 0)
					currentColumn--;
				break;
			case 9:	// HT
				// TODO
reportUnknownChar(c);
				break;
			case 13:
				currentColumn = 0;
				return;
			case 10:	// CR
			case 11:	// VT
			case 12:	// LF
				lineFeed();
				return;
			case 14:	// SO
				// TODO
//				break;
			case 15:	// SI
				// TODO
//				break;
			case 24:	// CAN
			case 26:	// SUB
				// TODO
//				break;
			case 0x9b:	// CSI
reportUnknownChar(c);
				break;
			case 27:	// ESC
				startEscapeSequence(ESC);
				return;
		};

		if(escapeState == ESC_NONE)
		{
			if(c >= 32)
			{
				writeRaw(c);
			}
			else
			{
				// TEMP
				writeRaw(':');
				int a = c/10;
				int b = c - a*10;
				writeRaw((char)('0' + a));
				writeRaw((char)('0' + b));
			}
		}
		else
		{
			updateEscapeSequence(c);
		}
/*
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
*/
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
		fmtBuffer = new char[rows*columns];

		clearScreen();

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

	void setPaintColor(Paint paint, int col)
	{
		switch(col)
		{
			case kColBlack:
				paint.setARGB(0xff, 0x00, 0x00, 0x00);
				break;
			case kColRed:
				paint.setARGB(0xff, 0xff, 0x00, 0x00);
				break;
			case kColGreen:
				paint.setARGB(0xff, 0x00, 0xff, 0x00);
				break;
			case kColYellow:
				paint.setARGB(0xff, 0xff, 0xff, 0x00);
				break;
			case kColBlue:
				paint.setARGB(0xff, 0x00, 0x00, 0xff);
				break;
			case kColMagenta:
				paint.setARGB(0xff, 0xff, 0x00, 0xff);
				break;
			case kColCyan:
				paint.setARGB(0xff, 0x00, 0xff, 0xff);
				break;
			case kColWhite:
				paint.setARGB(0xff, 0xff, 0xff, 0xff);
				break;
			default:
				paint.setARGB(0x80, 0x80, 0x80, 0x80);
				break;
		}
	}
	
	protected void onDraw(Canvas canvas)
	{
		Paint paint = new Paint();
		paint.setTypeface(Typeface.MONOSPACE);
		paint.setTextSize(10);
		paint.setAntiAlias(true);
		int charheight = (int)Math.ceil(paint.getFontSpacing());// + paint.ascent());
		int charwidth = (int)paint.measureText("X", 0, 1);
		char tmp[] = {' ', ' '};
		int x = 0, y = 0;
		y += charheight;
		int ybackgroffs = 3;
		for(int row = 0; row < numRows; row++)
		{
			x = 0;
			for(int col = 0; col < numColumns; col++)
			{
				tmp[0] = textBuffer[row*numColumns + col];
				tmp[1] = '\0';
				char fmt = fmtBuffer[row*numColumns + col];
				setPaintColor(paint, decodeFormatBackground(fmt));
				canvas.drawRect(x, y - charheight + ybackgroffs, x + charwidth, y + ybackgroffs, paint);
				setPaintColor(paint, decodeFormatForeground(fmt));
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

	/* For debugging only. */
	TerminalView dbgTerminalTranscript;

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
				for(int i = 0; i < s.length(); i++)
				{
					char c = s.charAt(i);
					if(c < 32)
					{
						dbgTerminalTranscript.writeRaw('^');
						int a = c/10;
						int b = c - a*10;
						dbgTerminalTranscript.writeRaw((char)('0' + a));
						dbgTerminalTranscript.writeRaw((char)('0' + b));
					}
					else
					{
						dbgTerminalTranscript.writeRaw(c);
						dbgTerminalTranscript.invalidate();
					}
				}
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

		dbgTerminalTranscript = new TerminalView(this, 80, 2);
		dbgTerminalTranscript.colorForeground = TerminalView.kColRed;

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);

		//layout.addView(dbgTerminalTranscript);
		layout.addView(screen);
		
		doCommand("/system/bin/mkdir", "/data/data/com.nethackff/dat", "");
		copyNetHackData();

		if(TestInit(width, height) == 0)
		{
			return;
		}

		setContentView(layout);

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
