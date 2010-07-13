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

public class NetHackTiledView extends NetHackView
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
		squareSizeX = (tileSizeX*zoomPercentage)/100;
		squareSizeY = (tileSizeY*zoomPercentage)/100;
	}

	int tilesPerRow = 1;
	int tileSizeX;
	int tileSizeY;

	public int zoomPercentage = 100;

	Paint bitmapPaint;
	
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
			cliprect.bottom = computeCoordY(terminal.changeRow2) + squareSizeY;
			cliprect.top = computeCoordY(terminal.changeRow1);
			cliprect.right = computeCoordX(terminal.changeColumn2) + squareSizeX;
			cliprect.left = computeCoordX(terminal.changeColumn1);
			invalidate(cliprect);
		}
	}

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

	protected void drawRow(Canvas canvas,
			int x, final int y,
			final char []txtBuffer,
			final int buffOffs, final int numChars, final int cursorIndex)
	{
		if(tileBitmap1 == null)
		{
			return;	
		}

		for(int index = 0; index < numChars; x += squareSizeX, index++)
		{
			int buffIndex = buffOffs + index;
			if(buffIndex >= 0 && buffIndex < txtBuffer.length)
			{
				char c = txtBuffer[buffIndex];
				if(c >= 0x100)
				{
					int topy = y;
	
					int tileindex = c - 0x100;
					int bitmapcharwidth = tileSizeX;
					int bitmapsquareSizeY = tileSizeY;
	
					int tilex = tileindex % tilesPerRow;
					int tiley = tileindex/tilesPerRow;
					
					canvas.drawBitmap(tileBitmap1, new Rect(tilex*bitmapcharwidth, tiley*bitmapsquareSizeY, (tilex + 1)*bitmapcharwidth, (tiley + 1)*bitmapsquareSizeY), new Rect(x, topy, x + squareSizeX, topy + squareSizeY), bitmapPaint);
				}
			}
		}
	}


	protected void onDraw(Canvas canvas)
	{
		int y;

		pendingRedraw = false;

		int rowView1 = 0;
		int rowView2 = Math.min(sizeY, terminal.numRows);
		int colView1 = 0;
		int colView2 = Math.min(sizeX, terminal.numColumns);

		Rect cliprect = new Rect();
		if(canvas.getClipBounds(cliprect))
		{
			colView1 = Math.max(computeViewColumnFromCoordX(cliprect.left), 0);
			colView2 = Math.min(computeViewColumnFromCoordX(cliprect.right + squareSizeX - 1), colView2);
			rowView1 = Math.max(computeViewRowFromCoordY(cliprect.top), 0);
			rowView2 = Math.min(computeViewRowFromCoordY(cliprect.bottom + squareSizeY - 1), rowView2);
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
			
			y += squareSizeY;
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

			drawRow(canvas, x, y, terminal.textBuffer, buffOffs, colView2 - colView1, cursorIndex);

			y += squareSizeY;
		}

		terminal.clearChange();

		if(drawCursor)
		{
			// Since we have drawn the cursor, we should probably register this current
			// position so that the next time we draw, we remember to erase the cursor
			// from its previous position.
			terminal.registerChange(terminal.currentColumn, terminal.currentRow);
		}

		// TEMP
/*
		Paint p = new Paint();
		int argb = Color.argb(0xff, 0xff, 0xff, 0xff);
		p.setColor(argb);
		canvas.drawLine(desiredCenterPosX - 5, desiredCenterPosY, desiredCenterPosX + 5, desiredCenterPosY, p);
		canvas.drawLine(desiredCenterPosX, desiredCenterPosY - 5, desiredCenterPosX, desiredCenterPosY + 5, p);

		int sx = sizePixelsX;
		int sy = sizePixelsY;
		canvas.drawLine(1, 1, 1, sy - 2, p);
		canvas.drawLine(sx - 2, 1, sx - 2, sy - 2, p);
		canvas.drawLine(1, 1, sx - 2, 1, p);
		canvas.drawLine(1, sy - 2, sx - 2, sy - 2, p);
*/
	}
}
