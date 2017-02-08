package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.Scroller;

public class PageView extends View implements
	GestureDetector.OnGestureListener,
	ScaleGestureDetector.OnScaleGestureListener
{
	protected DocumentActivity actionListener;

	protected float viewScale, minScale, maxScale;
	protected Bitmap bitmap;
	protected Page page;
	protected int bitmapW, bitmapH;
	protected int canvasW, canvasH;
	protected int scrollX, scrollY;
	protected Link[] links;
	protected boolean showLinks;

	protected GestureDetector detector;
	protected ScaleGestureDetector scaleDetector;
	protected Scroller scroller;
	protected boolean error;
	protected Paint errorPaint;
	protected Path errorPath;
	protected Paint linkPaint;

	public PageView(Context ctx, AttributeSet atts) {
		super(ctx, atts);

		scroller = new Scroller(ctx);
		detector = new GestureDetector(ctx, this);
		scaleDetector = new ScaleGestureDetector(ctx, this);

		viewScale = 1;
		minScale = 1;
		maxScale = 2;

		linkPaint = new Paint();
		linkPaint.setARGB(32, 0, 0, 255);

		errorPaint = new Paint();
		errorPaint.setARGB(255, 255, 80, 80);
		errorPaint.setStrokeWidth(5);
		errorPaint.setStyle(Paint.Style.STROKE);

		errorPath = new Path();
		errorPath.moveTo(-100, -100);
		errorPath.lineTo(100, 100);
		errorPath.moveTo(100, -100);
		errorPath.lineTo(-100, 100);
	}

	public void setActionListener(DocumentActivity l) {
		actionListener = l;
	}

	public void setError() {
		if (bitmap != null)
			bitmap.recycle();
		error = true;
		links = null;
		bitmap = null;
		invalidate();
	}

	public void setBitmap(Bitmap b, boolean wentBack, Link[] ls) {
		if (bitmap != null)
			bitmap.recycle();
		error = false;
		links = ls;
		bitmap = b;
		bitmapW = (int)(bitmap.getWidth() * viewScale);
		bitmapH = (int)(bitmap.getHeight() * viewScale);
		scroller.forceFinished(true);
		scrollX = wentBack ? bitmapW - canvasW : 0;
		scrollY = wentBack ? bitmapH - canvasH : 0;
		invalidate();
	}

	public void onSizeChanged(int w, int h, int ow, int oh) {
		canvasW = w;
		canvasH = h;
		actionListener.onPageViewSizeChanged(w, h);
	}

	public boolean onTouchEvent(MotionEvent event) {
		detector.onTouchEvent(event);
		scaleDetector.onTouchEvent(event);
		return true;
	}

	public boolean onDown(MotionEvent e) {
		scroller.forceFinished(true);
		return true;
	}

	public void onShowPress(MotionEvent e) {
	}

	public void onLongPress(MotionEvent e) {
		showLinks = !showLinks;
		invalidate();
	}

	public boolean onSingleTapUp(MotionEvent e) {
		boolean foundLink = false;
		float x = e.getX();
		float y = e.getY();
		if (showLinks && links != null) {
			float dx = (bitmapW <= canvasW) ? (bitmapW - canvasW) / 2 : scrollX;
			float dy = (bitmapH <= canvasH) ? (bitmapH - canvasH) / 2 : scrollY;
			float mx = (x + dx) / viewScale;
			float my = (y + dy) / viewScale;
			for (Link link : links) {
				Rect b = link.bounds;
				if (mx >= b.x0 && mx <= b.x1 && my >= b.y0 && my <= b.y1) {
					System.out.println("hit link " + link.page + " " + link.uri);
					if (link.page >= 0)
						actionListener.gotoPage(link.page);
					else if (link.uri != null)
						actionListener.gotoURI(link.uri);
					foundLink = true;
					break;
				}
			}
		}
		if (!foundLink) {
			float a = canvasW / 3;
			float b = a * 2;
			if (x <= a) goBackward();
			if (x >= b) goForward();
			if (x > a && x < b) actionListener.toggleUI();
		}
		invalidate();
		return true;
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
		if (bitmap != null) {
			scrollX += (int)dx;
			scrollY += (int)dy;
			scroller.forceFinished(true);
			invalidate();
		}
		return true;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float dx, float dy) {
		if (bitmap != null) {
			int maxX = bitmapW > canvasW ? bitmapW - canvasW : 0;
			int maxY = bitmapH > canvasH ? bitmapH - canvasH : 0;
			scroller.forceFinished(true);
			scroller.fling(scrollX, scrollY, (int)-dx, (int)-dy, 0, maxX, 0, maxY);
			invalidate();
		}
		return true;
	}

	public boolean onScaleBegin(ScaleGestureDetector det) { return true; }

	public boolean onScale(ScaleGestureDetector det) {
		if (bitmap != null) {
			float focusX = det.getFocusX();
			float focusY = det.getFocusY();
			float scaleFactor = det.getScaleFactor();
			float pageFocusX = (focusX + scrollX) / viewScale;
			float pageFocusY = (focusY + scrollY) / viewScale;
			viewScale *= scaleFactor;
			if (viewScale < minScale) viewScale = minScale;
			if (viewScale > maxScale) viewScale = maxScale;
			bitmapW = (int)(bitmap.getWidth() * viewScale);
			bitmapH = (int)(bitmap.getHeight() * viewScale);
			scrollX = (int)(pageFocusX * viewScale - focusX);
			scrollY = (int)(pageFocusY * viewScale - focusY);
			scroller.forceFinished(true);
			invalidate();
		}
		return true;
	}

	public void onScaleEnd(ScaleGestureDetector det) { }

	public void goBackward() {
		scroller.forceFinished(true);
		if (scrollY <= 0) {
			if (scrollX <= 0) {
				actionListener.goBackward();
				return;
			}
			scroller.startScroll(scrollX, scrollY, -canvasW * 9 / 10, bitmapH - canvasH - scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, -canvasH * 9 / 10, 250);
		}
		invalidate();
	}

	public void goForward() {
		scroller.forceFinished(true);
		if (scrollY + canvasH >= bitmapH) {
			if (scrollX + canvasW >= bitmapW) {
				actionListener.goForward();
				return;
			}
			scroller.startScroll(scrollX, scrollY, canvasW * 9 / 10, -scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, canvasH * 9 / 10, 250);
		}
		invalidate();
	}

	public void onDraw(Canvas canvas) {
		int x, y;

		if (bitmap == null) {
			if (error) {
				canvas.translate(canvasW / 2, canvasH / 2);
				canvas.drawPath(errorPath, errorPaint);
			}
			return;
		}

		if (scroller.computeScrollOffset()) {
			scrollX = scroller.getCurrX();
			scrollY = scroller.getCurrY();
			invalidate(); /* keep animating */
		}

		if (bitmapW <= canvasW) {
			scrollX = 0;
			x = (canvasW - bitmapW) / 2;
		} else {
			if (scrollX < 0) scrollX = 0;
			if (scrollX > bitmapW - canvasW) scrollX = bitmapW - canvasW;
			x = -scrollX;
		}

		if (bitmapH <= canvasH) {
			scrollY = 0;
			y = (canvasH - bitmapH) / 2;
		} else {
			if (scrollY < 0) scrollY = 0;
			if (scrollY > bitmapH - canvasH) scrollY = bitmapH - canvasH;
			y = -scrollY;
		}

		canvas.translate(x, y);
		canvas.scale(viewScale, viewScale);
		canvas.drawBitmap(bitmap, 0, 0, null);

		if (showLinks && links != null) {
			for (Link link : links) {
				Rect b = link.bounds;
				canvas.drawRect(b.x0, b.y0, b.x1, b.y1, linkPaint);
			}
		}
	}
}
