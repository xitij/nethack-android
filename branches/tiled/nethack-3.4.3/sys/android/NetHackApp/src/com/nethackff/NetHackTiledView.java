package com.nethackff;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;

public class NetHackTiledView extends View
{
	private boolean drawCursor = true;
	private boolean whiteBackgroundMode = false;

	public Bitmap tileBitmap1;

	void setBitmap(Bitmap bm, int tilesizex, int tilesizey, int defaultzoompercentage)
	{
		tileBitmap1 = bm;

		tileSizeX = tilesizex;
		tileSizeY = tilesizey;

		tilesPerRow = bm.getWidth()/tilesizex;
		
		zoomPercentage = defaultzoompercentage;

		updateZoom();
	}

	void updateZoom()
	{
		charWidth = (tileSizeX*zoomPercentage)/100;
		charHeight = (tileSizeY*zoomPercentage)/100;
	}

	int tilesPerRow = 1;
	int tileSizeX;
	int tileSizeY;

	public int offsetX = 0;
	public int offsetY = 0;
	public int sizeX;
	public int sizeY;
	public int sizePixelsX;
	public int sizePixelsY;

	public int extraSizeX = 0;
	public int extraSizeY = 0;

	public int zoomPercentage = 100;

	Paint bitmapPaint;
	
	public int getNumDisplayedLines()
	{
		return sizeY;			
	}
	
	public void setDrawCursor(boolean b)
	{
		if(b != drawCursor)
		{
			terminal.registerChange(terminal.currentColumn, terminal.currentRow);
			drawCursor = b;
		}
	}
	
	public boolean getDrawCursor()
	{
		return drawCursor;
	}

	public void scrollToCenterAtPos(int centercolumn, int centerrow)
	{
		int cursorcenterx = centercolumn*charWidth + charWidth/2;
		int cursorcentery = centerrow*charHeight + charHeight/2;
		int newscrollx = cursorcenterx - getWidth()/2;
		int newscrolly = cursorcentery - getHeight()/2;

		int termx = charWidth*sizeX;
		int termy = charHeight*getNumDisplayedLines();

		int maxx = termx - getWidth();
		int maxy = termy - getHeight();		// Note: could be negative, so we do the max clamping first.
		if(newscrollx >= maxx)
		{
			newscrollx = maxx - 1;
		}
		if(newscrolly >= maxy)
		{
			newscrolly = maxy - 1;
		}
		if(newscrollx < 0)
		{
			newscrollx = 0;
		}
		if(newscrolly < 0)
		{
			newscrolly = 0;
		}

		scrollTo(newscrollx, newscrolly);
		desiredCenterPosX = cursorcenterx;
		desiredCenterPosY = cursorcentery;
	}
	public void scrollToCursor()
	{
		scrollToCenterAtPos(terminal.currentColumn, terminal.currentRow);
	}

	public void computeSizePixels()
	{
		sizePixelsX = sizeX*charWidth + extraSizeX;
		sizePixelsY = sizeY*charHeight + extraSizeY;
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

		// This was done temporarily before, but hopefully there is no need for it now.
		//	if(reformatText)
		//	{
		//		height = height > 1000 ? height : 1000;	
		//	}

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

	public NetHackTiledView(Context context)
	{
		super(context);

/* TEMP */
		int width = 80;
		int height = 24;		// 26
		terminal = new NetHackTerminalState(width, height);

		bitmapPaint = new Paint();
//		bitmapPaint.setAntiAlias(true);
		bitmapPaint.setAntiAlias(false);

		offsetX = 0;
		offsetY = 0;
		sizeX = terminal.numColumns;
		sizeY = terminal.numRows;

		computeSizePixels();

		updateBackground();
	}
	public void setWhiteBackgroundMode(boolean b)
	{
		whiteBackgroundMode = b;
		updateBackground();
	}
	public void updateBackground()
	{
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

	enum ColorSet
	{
		AnsiTerminal,
		IBM,
		Amiga
	};
	ColorSet colorSet = ColorSet.AnsiTerminal;

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
		onDrawFixed(canvas);

		//	Paint p = new Paint();
		//	int argb = Color.argb(0xff, 0xff, 0xff, 0xff);
		//	p.setColor(argb);
		//	canvas.drawLine(desiredCenterPosX - 5, desiredCenterPosY, desiredCenterPosX + 5, desiredCenterPosY, p);		
		//	canvas.drawLine(desiredCenterPosX, desiredCenterPosY - 5, desiredCenterPosX, desiredCenterPosY + 5, p);		
	}

	public float desiredCenterPosX = 0;
	public float desiredCenterPosY = 0;

	protected void drawTileRow(Canvas canvas, String currentstr, int currentx1, int y)
	{
		int len = currentstr.length();
		int x1 = currentx1;

		if(tileBitmap1 != null)
		{
			x1 = currentx1;
			for(int i = 0; i < len; i++)
			{
				char c = currentstr.charAt(i);
				if(c >= 0x100)
				{
					int topy = y;

					int tileindex = c - 0x100;
					int bitmapcharwidth = tileSizeX;
					int bitmapcharheight = tileSizeY;

					int tilex = tileindex % tilesPerRow;
					int tiley = tileindex/tilesPerRow;
					
					canvas.drawBitmap(tileBitmap1, new Rect(tilex*bitmapcharwidth, tiley*bitmapcharheight, (tilex + 1)*bitmapcharwidth, (tiley + 1)*bitmapcharheight), new Rect(x1, topy, x1 + charWidth, topy + charHeight), bitmapPaint);
				}
				x1 += charWidth;
			}
			return;
		}
		
/*
		float positions[] = new float[len*6];

		char []newstr = new char[len*3];
		int k = 0;
		for(int i = 0; i < len; i++)
		{
			float nextx1 = x1 + charWidth;
			positions[k++] = x1;
			positions[k++] = y;
			x1 += charWidth/4;
			positions[k++] = x1;
			positions[k++] = y;
			x1 += charWidth/4;
			positions[k++] = x1;
			positions[k++] = y;
			x1 = nextx1;
			char c = currentstr.charAt(i);
			int cint = c;// - 0x100;
			cint = cint % 1000;
			newstr[3*i] = Character.forDigit(cint/100, 10);
			newstr[3*i + 1] = Character.forDigit((cint%100)/10, 10);
			newstr[3*i + 2] = Character.forDigit(cint%10, 10);
		}
		canvas.drawPosText(newstr, 0, len*3, positions);
*/
	}
	
	protected void drawRowForeground1(Canvas canvas,
			int x, final int y,
			final char []txtBuffer, final char []fmtBuffer,
			final int buffOffs, final int numChars, final int cursorIndex)
	{
		int currentx1 = -1;
		int currentcolor = -1;
		boolean currentcursor = false;
		String currentstr = "";
		for(int index = 0; index < numChars; x += charWidth, index++)
		{
			char c;
			char fmt;
			int buffIndex = buffOffs + index;
			if(buffIndex >= 0 && buffIndex < fmtBuffer.length)
			{
				fmt = fmtBuffer[buffIndex];
				c = txtBuffer[buffIndex];
			}
			else
			{
				fmt = 0;	// Not sure!				
				c = ' ';
			}
			int color = terminal.decodeFormatForeground(fmt);

			boolean cursor = false;
			if(cursorIndex == index && drawCursor)
			{
				cursor = true;
			}

			if(color == currentcolor && cursor == currentcursor)
			{
				currentstr += c;
				continue;
			}
			if(currentx1 >= 0)
			{
//				setPaintColorForeground(textPaint, currentcolor, bitmapPaint, currentcursor);
				drawTileRow(canvas, currentstr, currentx1, y);
			}
			currentx1 = x;
			currentcolor = color;
			currentcursor = cursor;
			currentstr = "" + c;
		}
//		setPaintColorForeground(textPaint, currentcolor, bitmapPaint, currentcursor);
		drawTileRow(canvas, currentstr,
				currentx1, y);
	}


	protected void onDrawFixed(Canvas canvas)
	{
		int y;

		int rowView1 = 0;
		int rowView2 = Math.min(sizeY, terminal.numRows);
		int colView1 = 0;
		int colView2 = Math.min(sizeX, terminal.numColumns);

		Rect cliprect = new Rect();
		if(canvas.getClipBounds(cliprect))
		{
			colView1 = Math.max(computeViewColumnFromCoordX(cliprect.left), 0);
			colView2 = Math.min(computeViewColumnFromCoordX(cliprect.right + charWidth - 1), colView2);
			rowView1 = Math.max(computeViewRowFromCoordY(cliprect.top), 0);
			rowView2 = Math.min(computeViewRowFromCoordY(cliprect.bottom + charHeight - 1), rowView2);
		}

// TODO: Simplify this crap!
		y = computeViewCoordY(rowView1);
		for(int rowView = rowView1; rowView < rowView2; rowView++)
		{
			final int rowTerm = rowView + offsetY;
			final int buffOffs = rowTerm*terminal.numColumns + colView1 + offsetX; 
			int cursorIndex = -1;
			if(rowTerm == terminal.currentRow)
			{
				cursorIndex = terminal.currentColumn - colView1 - offsetX;
			}

			final int x = computeViewCoordX(colView1);
			
			y += charHeight;
		}

		int ybackgroffs = 2;
		y = computeViewCoordY(rowView1);
		for(int rowView = rowView1; rowView < rowView2; rowView++)
		{
			final int rowTerm = rowView + offsetY;
			final int buffOffs = rowTerm*terminal.numColumns + colView1 + offsetX; 
			int cursorIndex = -1;
			if(rowTerm == terminal.currentRow)
			{
				cursorIndex = terminal.currentColumn - colView1 - offsetX;
			}

			int x = computeViewCoordX(colView1);

			drawRowForeground1(canvas, x, y, terminal.textBuffer, terminal.fmtBuffer, buffOffs, colView2 - colView1, cursorIndex);

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
