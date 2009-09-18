package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.KeyEvent;
import java.lang.Thread;

class TerminalView extends View
{
	String outputText;

	private char[] textBuffer;
	int numRows;
	int numColumns;

	int currentRow;
	int currentColumn;

	public void write(char c)
	{
		if(c == '\n')
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
			return;
		}

		// Should we wrap here?

		if(currentColumn < numColumns && currentRow < numRows)
		{
			textBuffer[currentRow*numColumns + currentColumn] = c;
		}
		currentColumn++;
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

		write('>');
		write(' ');
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
	
	public void Test1()
	{
		outputText += "Working!\n";
		outputText += getContents();
	}
	
	protected void onDraw(Canvas canvas)
	{
		Paint paint = new Paint();
		paint.setARGB(255, 255, 255, 255);
		paint.setTypeface(Typeface.MONOSPACE);
		paint.setTextSize(14);
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

	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		int width = 80;
		int height = 14;
		
		screen = new TerminalView(this, width, height);
		screen.Test1();
		
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
