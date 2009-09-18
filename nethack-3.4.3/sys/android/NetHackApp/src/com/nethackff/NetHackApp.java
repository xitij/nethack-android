package com.nethackff;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;
//import android.widget.TextView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
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
		// TEMP - not the right place!

//		String inp = TerminalReceive();
		
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

		// TEMP - this is probably no good!
//		invalidate();
	}
}


public class NetHackApp extends Activity implements Runnable
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
/*
		char []buff = { ' ', ' ' };
//		buff[0] = (char)event.getUnicodeChar();
	buff[0] = 'O';
		buff[1] = '\0';
		*/
		String s = "";
		s += (char)event.getUnicodeChar();
		TerminalSend(s);
		screen.invalidate();
		return true;
	}

	/*
	public boolean onTouchEvent(MotionEvent event)
	{
		if(super.onTouchEvent(event))
		{
			return true;
		}

		String s = TerminalReceive();
		screen.write(s);
		screen.invalidate();
		return true;
	}
*/
	
	/*
	final Handler handler = new Handler(new Handler.Callback() {

		@Override
		public boolean handleMessage(Message msg) {
		textView.setText("index="+index++);
		return false;
	}
*/

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

		screen = new TerminalView(this, 80, 14);
		screen.Test1();
		
		/* Create a TextView and set its content.
		 * the text is retrieved by calling a native
		 * function.
		 */
		if(TestInit(20, 10) == 0)
		{
			return;
		}

/*
		try
		{
			Thread.sleep(2000);
		} catch(InterruptedException e)
		{
			throw new RuntimeException(e.getMessage());
		}
*/
		
		//addText(stringFromJNI());

		setContentView(screen);

        Thread thread = new Thread(this);
        thread.start();
 
		//TestShutdown();
	}

	public native int TestInit(int numrows, int numcols); 
	public native void TestShutdown(); 
	public native void TestUpdate();

	public native String TerminalReceive();
//	public native void TerminalSend(char []str);
	public native void TerminalSend(String str);

	/* this is used to load the 'hello-jni' library on application
	 * startup. The library has already been unpacked into
	 * /data/data/com.example.HelloJni/lib/libhello-jni.so at
	 * installation time by the package manager.
	 */
	static {
		System.loadLibrary("nethack");
	}
}
