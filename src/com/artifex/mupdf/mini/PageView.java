package com.artifex.mupdf.mini;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Scroller;

public class PageView extends View
{
	protected float viewScale, minScale, maxScale;
	protected Bitmap bitmap;
	protected int bitmapW, bitmapH;
	protected int canvasW, canvasH;
	protected int scrollX, scrollY;

	protected Scroller scroller;
	protected boolean error;
	protected Paint errorPaint;
	protected Path errorPath;

	public PageView(Context ctx, AttributeSet atts) {
		super(ctx, atts);

		scroller = new Scroller(ctx);

		viewScale = 1;
		minScale = 1;
		maxScale = 2;

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

	public void setError() {
		error = true;
		bitmap = null;
		invalidate();
	}

	public void setBitmap(Bitmap b, boolean wentBack) {
		error = false;
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
	}

	public void onDown() {
		if (bitmap == null) return;
		scroller.forceFinished(true);
	}

	public boolean onSingleTapUp(float x, float y) {
		if (bitmap == null) return false;
		// TODO: detect tap on links
		return false;
	}

	public void onScroll(float dx, float dy) {
		if (bitmap == null) return;
		scrollX += (int)dx;
		scrollY += (int)dy;
		scroller.forceFinished(true);
		invalidate();
	}

	public void onFling(float dx, float dy) {
		if (bitmap == null) return;
		int maxX = bitmapW > canvasW ? bitmapW - canvasW : 0;
		int maxY = bitmapH > canvasH ? bitmapH - canvasH : 0;
		scroller.forceFinished(true);
		scroller.fling(scrollX, scrollY, (int)-dx, (int)-dy, 0, maxX, 0, maxY);
		invalidate();
	}

	public void onScale(float focusX, float focusY, float scaleFactor) {
		if (bitmap == null) return;
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

	public boolean goBackward() {
		scroller.forceFinished(true);
		if (scrollY <= 0) {
			if (scrollX <= 0)
				return true;
			scroller.startScroll(scrollX, scrollY, -canvasW * 9 / 10, bitmapH - canvasH - scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, -canvasH * 9 / 10, 250);
		}
		invalidate();
		return false;
	}

	public boolean goForward() {
		scroller.forceFinished(true);
		if (scrollY + canvasH >= bitmapH) {
			if (scrollX + canvasW >= bitmapW)
				return true;
			scroller.startScroll(scrollX, scrollY, canvasW * 9 / 10, -scrollY, 500);
		} else {
			scroller.startScroll(scrollX, scrollY, 0, canvasH * 9 / 10, 250);
		}
		invalidate();
		return false;
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
	}
}
