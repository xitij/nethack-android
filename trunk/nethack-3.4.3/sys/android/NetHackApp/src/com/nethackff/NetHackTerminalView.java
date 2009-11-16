package com.nethackff;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

public class NetHackTerminalView extends View
{
	public boolean drawCursor = true;
	public boolean whiteBackgroundMode = false;

	public int offsetX = 0;
	public int offsetY = 0;
	public int sizeX;
	public int sizeY;
	public int sizePixelsX;
	public int sizePixelsY;

	Paint textPaint;

	int textSize = 10;

	public void computeSizePixels()
	{
		sizePixelsX = sizeX*charWidth;
		sizePixelsY = sizeY*charHeight;
	}
	
	public void write(String s)
	{
		terminal.write(s);

		if(terminal.changeColumn1 <= terminal.changeColumn2)
		{
			if(drawCursor)
			{
				// Since we will draw the cursor at the current position, we should probably consider
				// the current position as a change.
				terminal.registerChange(terminal.currentColumn, terminal.currentRow);
			}

			Rect cliprect = new Rect();
			cliprect.bottom = computeCoordY(terminal.changeRow2) + charHeight;
			cliprect.top = computeCoordY(terminal.changeRow1);
			cliprect.right = computeCoordX(terminal.changeColumn2) + charWidth;
			cliprect.left = computeCoordX(terminal.changeColumn1);
			invalidate(cliprect);
		}
	}
	
	protected void onMeasure(int widthmeasurespec, int heightmeasurespec)
	{
		int minheight = getSuggestedMinimumHeight();
		int minwidth = getSuggestedMinimumWidth();

		int width, height;
		width = sizePixelsX;
		height = sizePixelsY;

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

		offsetX = 0;
		offsetY = 0;
		sizeX = term.numColumns;
		sizeY = term.numRows;

		computeSizePixels();

		if(whiteBackgroundMode)
		{
			setBackgroundColor(0xffffffff);
		}
		else
		{
			setBackgroundColor(0xff000000);
		}
	}

	public void setSizeX(int numColumns)
	{
		sizeX = numColumns;
	}
	public void setSizeXFromPixels(int pixelSizeX)
	{
		sizeX = pixelSizeX/charWidth;
	}
	public void setSizeY(int numRows)
	{
		sizeY = numRows;
	}
	public void setSizeYFromPixels(int pixelSizeY)
	{
		sizeY = pixelSizeY/charHeight;
	}
	public void initStateFromView()
	{
		terminal.init(sizeX, sizeY);
	}
	public int getSizeX()
	{
		return sizeX;
	}
	public int getSizeY()
	{
		return sizeY;
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
				if(whiteBackgroundMode)
				{
					paint.setARGB(0xff, 0xff, 0xff, 0xff);
				}
				else
				{
					paint.setARGB(0xff, 0x00, 0x00, 0x00);
				}
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
				if(whiteBackgroundMode)
				{
					paint.setARGB(0xff, 0x00, 0x00, 0x00);
				}
				else
				{
					paint.setARGB(0xff, 0xff, 0xff, 0xff);
				}
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
				if(whiteBackgroundMode)
				{
					paint.setARGB(0xff, 0xff, 0xff, 0xff);
				}
				else
				{
					paint.setARGB(0xff, 0x00, 0x00, 0x00);
				}
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
				if(whiteBackgroundMode)
				{
					paint.setARGB(0xff, 0x00, 0x00, 0x00);
				}
				else
				{
					paint.setARGB(0xff, 0xff, 0xff, 0xff);
				}
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
		return charWidth*(column - offsetX);
	}

	int computeCoordY(int row)
	{
		return charHeight*(row - offsetY);
	}

	int computeColumnFromCoordX(int coordx)
	{
		return coordx/charWidth + offsetX;
	}

	int computeRowFromCoordY(int coordy)
	{
		return coordy/charHeight + offsetY;
	}

	int computeViewCoordX(int column)
	{
		return charWidth*column;
	}

	int computeViewCoordY(int row)
	{
		return charHeight*row;
	}

	int computeViewColumnFromCoordX(int coordx)
	{
		return coordx/charWidth;
	}

	int computeViewRowFromCoordY(int coordy)
	{
		return coordy/charHeight;
	}

	protected void onDraw(Canvas canvas)
	{
		int x, y;

		int rowView1 = 0;
		int rowView2 = sizeY;
		int colView1 = 0;
		int colView2 = sizeX;

		Rect cliprect = new Rect();
		if(canvas.getClipBounds(cliprect))
		{
			colView1 = Math.max(computeViewColumnFromCoordX(cliprect.left), 0);
			colView2 = Math.min(computeViewColumnFromCoordX(cliprect.right + charWidth - 1), sizeX);
			rowView1 = Math.max(computeViewRowFromCoordY(cliprect.top), 0);
			rowView2 = Math.min(computeViewRowFromCoordY(cliprect.bottom + charHeight - 1), sizeY);
		}

		x = 0;
		y = computeViewCoordY(rowView1);
		for(int rowView = rowView1; rowView < rowView2; rowView++)
		{
			x = computeViewCoordX(colView1);
			int currentx1 = -1;
			int currentcolor = -1;
			for(int colView = colView1; colView < colView2; colView++, x += charWidth)
			{
				int colTerm = colView + offsetX;
				int rowTerm = rowView + offsetY;
				char fmt;
				if(colTerm >= 0 && colTerm < terminal.numColumns
						&& rowTerm >= 0 && rowTerm < terminal.numRows)
				{
					fmt = terminal.fmtBuffer[rowTerm*terminal.numColumns + colTerm];
				}
				else
				{
					fmt = 0;	// Not sure!				
				}
				int color = terminal.decodeFormatBackground(fmt);

				if(colTerm == terminal.currentColumn && rowTerm == terminal.currentRow && drawCursor)
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
		y = charHeight + computeViewCoordY(rowView1) - ybackgroffs;
		for(int rowView = rowView1; rowView < rowView2; rowView++)
		{
			x = computeViewCoordX(colView1);
			int currentx1 = -1;
			int currentcolor = -1;
			String currentstr = "";
			for(int colView = colView1; colView < colView2; colView++, x += charWidth)
			{
				int colTerm = colView + offsetX;
				int rowTerm = rowView + offsetY;
				char c;
				char fmt;
				if(colTerm >= 0 && colTerm < terminal.numColumns
						&& rowTerm >= 0 && rowTerm < terminal.numRows)
				{
					fmt = terminal.fmtBuffer[rowTerm*terminal.numColumns + colTerm];
					c = terminal.getTextAt(colTerm, rowTerm);
				}
				else
				{
					fmt = 0;	// Not sure!				
					c = ' ';
				}
				int color = terminal.decodeFormatForeground(fmt);

				if(colTerm == terminal.currentColumn && rowTerm == terminal.currentRow && drawCursor)
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

		if(drawCursor)
		{
			// Since we have drawn the cursor, we should probably register this current
			// position so that the next time we draw, we remember to erase the cursor
			// from its previous position.
			terminal.registerChange(terminal.currentColumn, terminal.currentRow);
		}
	}
}
