package com.nethackff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

public class NetHackTerminalView extends View
{
	Paint textPaint;

	int textSize = 10;


	protected void onMeasure(int widthmeasurespec, int heightmeasurespec)
	{
		int minheight = getSuggestedMinimumHeight();
		int minwidth = getSuggestedMinimumWidth();

		// TODO: Prevent duplication
		Paint paint = new Paint();
		paint.setTypeface(Typeface.MONOSPACE);
		paint.setTextSize(textSize);
		paint.setAntiAlias(true);
		int charheight = (int)Math.ceil(paint.getFontSpacing());// + paint.ascent());
		int charwidth = (int)paint.measureText("X", 0, 1);

		int width, height;
		width = terminal.numColumns*charwidth;
		height = terminal.numRows*charheight;

//		height += 2; // MAGIC!

// TEMP
//height -= 48;
//height -= 4;
		
		if (width < minwidth)
		{
			width = minwidth;
		}
		if (height < minheight)
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

	NetHackTerminalState terminal;

	public NetHackTerminalView(Context context, NetHackTerminalState term)
	{
		super(context);

		terminal = term;

		// Paint paint = createPaint();
		textPaint = new Paint();
		textPaint.setTypeface(Typeface.MONOSPACE);
		textPaint.setTextSize(textSize);
		textPaint.setAntiAlias(true);

		charHeight = (int)Math.ceil(textPaint.getFontSpacing());
		charWidth = (int)textPaint.measureText("X", 0, 1);
	}


	void setPaintColorForeground(Paint paint, int col)
	{
		if((col & 8) != 0)
		{
			paint.setFakeBoldText(true);
			col &= ~8;
		}
		else
		{
			paint.setFakeBoldText(false);
		}
		if((col & 16) != 0)
		{
			paint.setUnderlineText(true);
			col &= ~16;
		}
		else
		{
			paint.setUnderlineText(false);
		}
		switch(col)
		{
			case NetHackTerminalState.kColBlack:
				paint.setARGB(0xff, 0x00, 0x00, 0x00);
				break;
			case NetHackTerminalState.kColRed:
				paint.setARGB(0xff, 0xff, 0x00, 0x00);
				break;
			case NetHackTerminalState.kColGreen:
				paint.setARGB(0xff, 0x00, 0xff, 0x00);
				break;
			case NetHackTerminalState.kColYellow:
				paint.setARGB(0xff, 0xff, 0xff, 0x00);
				break;
			case NetHackTerminalState.kColBlue:
				paint.setARGB(0xff, 0x00, 0x00, 0xff);
				break;
			case NetHackTerminalState.kColMagenta:
				paint.setARGB(0xff, 0xff, 0x00, 0xff);
				break;
			case NetHackTerminalState.kColCyan:
				paint.setARGB(0xff, 0x00, 0xff, 0xff);
				break;
			case NetHackTerminalState.kColWhite:
				paint.setARGB(0xff, 0xff, 0xff, 0xff);
				break;
			default:
				paint.setARGB(0x80, 0x80, 0x80, 0x80);
				break;
		}
	}

	void setPaintColorBackground(Paint paint, int col)
	{
		switch(col)
		{
			case NetHackTerminalState.kColBlack:
				paint.setARGB(0xff, 0x00, 0x00, 0x00);
				break;
			case NetHackTerminalState.kColRed:
				paint.setARGB(0xff, 0xff, 0x00, 0x00);
				break;
			case NetHackTerminalState.kColGreen:
				paint.setARGB(0xff, 0x00, 0xff, 0x00);
				break;
			case NetHackTerminalState.kColYellow:
				paint.setARGB(0xff, 0xff, 0xff, 0x00);
				break;
			case NetHackTerminalState.kColBlue:
				paint.setARGB(0xff, 0x00, 0x00, 0xff);
				break;
			case NetHackTerminalState.kColMagenta:
				paint.setARGB(0xff, 0xff, 0x00, 0xff);
				break;
			case NetHackTerminalState.kColCyan:
				paint.setARGB(0xff, 0x00, 0xff, 0xff);
				break;
			case NetHackTerminalState.kColWhite:
				paint.setARGB(0xff, 0xff, 0xff, 0xff);
				break;
			default:
				paint.setARGB(0x80, 0x80, 0x80, 0x80);
				break;
		}
	}

	int charHeight = 0;
	int charWidth = 0;

	int computeCoordX(int column)
	{
		return charWidth*column;
	}

	int computeCoordY(int row)
	{
		return row*charHeight;
	}

	int computeColumnFromCoordX(int coordx)
	{
		return coordx/charWidth;
	}

	int computeRowFromCoordY(int coordy)
	{
		return coordy/charHeight;
	}

	protected void onDraw(Canvas canvas)
	{
		int x, y;

		int row1 = 0;
		int row2 = terminal.numRows;
		int col1 = 0;
		int col2 = terminal.numColumns;

		Rect cliprect = new Rect();
		if(canvas.getClipBounds(cliprect))
		{
			col1 = Math.max(computeColumnFromCoordX(cliprect.left), 0);
			col2 = Math.min(computeColumnFromCoordX(cliprect.right + charWidth - 1), terminal.numColumns);
			row1 = Math.max(computeRowFromCoordY(cliprect.top), 0);
			row2 = Math.min(computeRowFromCoordY(cliprect.bottom + charHeight - 1), terminal.numRows);
		}

		x = 0;
		y = computeCoordY(row1);
		for(int row = row1; row < row2; row++)
		{
			x = computeCoordX(col1);
			int currentx1 = -1;
			int currentcolor = -1;
			for(int col = col1; col < col2; col++, x += charWidth)
			{
				char fmt = terminal.fmtBuffer[row*terminal.numColumns + col];
				int color = terminal.decodeFormatBackground(fmt);

				if(col == terminal.currentColumn && row == terminal.currentRow)
				{
					color = 7 - color;
				}
				if(color == currentcolor)
				{
					continue;
				}
				if(currentx1 >= 0)
				{
					setPaintColorBackground(textPaint, currentcolor);
					canvas.drawRect(currentx1, y, x, y + charHeight, textPaint);
				}
				currentx1 = x;
				currentcolor = color;
			}
			setPaintColorBackground(textPaint, currentcolor);
			canvas.drawRect(currentx1, y, x, y + charHeight, textPaint);
			y += charHeight;
		}

		x = 0;

		int ybackgroffs = 2;
		y = charHeight + computeCoordY(row1) - ybackgroffs;
		for(int row = row1; row < row2; row++)
		{
			x = computeCoordX(col1);
			int currentx1 = -1;
			int currentcolor = -1;
			String currentstr = "";
			for(int col = col1; col < col2; col++, x += charWidth)
			{
				char fmt = terminal.fmtBuffer[row*terminal.numColumns + col];
				int color = terminal.decodeFormatForeground(fmt);
				char c = terminal.textBuffer[row*terminal.numColumns + col];

				if(col == terminal.currentColumn && row == terminal.currentRow)
				{
					boolean bold = false;
					if(color >= 8)
					{
						color -= 8;
						bold = true;
					}
					color = 7 - color;
					if(bold)
					{
						color += 8;
					}
				}

				if(color == currentcolor)
				{
					currentstr += c;
					continue;
				}
				if(currentx1 >= 0)
				{
					setPaintColorForeground(textPaint, currentcolor);
					canvas.drawText(currentstr, 0, currentstr.length(),
							(float)currentx1, (float)y, textPaint);
				}
				currentx1 = x;
				currentcolor = color;
				currentstr = "" + c;
			}
			setPaintColorForeground(textPaint, currentcolor);
			canvas.drawText(currentstr, 0, currentstr.length(),
					(float)currentx1, (float)y, textPaint);
			y += charHeight;
		}

		terminal.clearChange();

		// Since we have drawn the cursor, we should probably register this current
		// position so that the next time we draw, we remember to erase the cursor
		// from its previous position.
		terminal.registerChange(terminal.currentColumn, terminal.currentRow);
	}
}
