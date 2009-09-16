package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
//import android.widget.TextView;
import android.os.Bundle;
//import android.util.Log;
import android.view.KeyEvent;
//import android.view.MotionEvent;
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

			// TODO: Perhaps scroll here?

			return;
		}

		// Should we wrap here?

		if(currentColumn < numColumns && currentRow < numRows)
		{
			textBuffer[currentRow*numColumns + currentColumn] = c;
		}
		currentColumn++;
	}
	
	public TerminalView(Context context, int rows, int columns)
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
		outputText += "TerminalView!\n";
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


public class NetHackApp extends Activity
{
	TerminalView screen;
	
	public void addText(String s)
	{
//		screen.outputText += s;
//		screen.outputText += "\n";
	}
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if(super.onKeyDown(keyCode, event))
		{
			return true;
		}
		screen.write((char)event.getUnicodeChar());
		screen.invalidate();
		return true;
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		screen = new TerminalView(this, 80, 24);
		screen.Test1();
		
		/* Create a TextView and set its content.
		 * the text is retrieved by calling a native
		 * function.
		 */
		if(TestInit(20, 10) == 0)
		{
			return;
		}

		try
		{
			Thread.sleep(2000);
		} catch(InterruptedException e)
		{
			throw new RuntimeException(e.getMessage());
		}

		
		//addText(stringFromJNI());

		setContentView(screen);
		
		TestShutdown();
	}

	/* A native method that is implemented by the
	 * 'hello-jni' native library, which is packaged
	 * with this application.
	 */
	public native String  stringFromJNI();

	public native int TestInit(int numrows, int numcols); 
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
		System.loadLibrary("nethack");
	}
}
