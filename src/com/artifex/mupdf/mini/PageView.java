package com.artifex.mupdf.mini;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Scroller;

public class PageView extends View
{
	protected Bitmap bitmap;
	protected float bitmapDPI;
	protected float displayDPI, viewDPI;
	protected float scrollX, scrollY;
	protected Matrix transform;
	protected Scroller scroller;

	protected boolean error;
	protected Paint errorPaint;
	protected Path errorPath;

	protected float minDPI, maxDPI;

	public PageView(Context ctx, AttributeSet atts) {
		super(ctx, atts);

		scroller = new Scroller(ctx);

		transform = new Matrix();
		bitmapDPI = 1;
		viewDPI = 1;

		errorPaint = new Paint();
		errorPaint.setARGB(255, 255, 80, 80);
		errorPaint.setStrokeWidth(3);
		errorPaint.setStyle(Paint.Style.STROKE);

		errorPath = new Path();
		errorPath.moveTo(0, 0);
		errorPath.lineTo(100, 100);
		errorPath.moveTo(100, 0);
		errorPath.lineTo(0, 100);
	}

	public void setDisplayDPI(float dpi) {
		displayDPI = dpi;
		viewDPI = dpi;
		minDPI = dpi / 4;
		maxDPI = dpi * 2;
	}

	public void setBitmap(Bitmap b, float z) {
		if (b != null) {
			error = false;
			bitmap = b;
			bitmapDPI = z;
		} else {
			error = true;
			viewDPI = displayDPI;
			bitmapDPI = displayDPI;
			scrollX = 0;
			scrollY = 0;
		}
		invalidate();
	}

	public float getDPI() {
		return viewDPI;
	}

	public void onDown() {
		if (error) return;
		scroller.forceFinished(true);
	}
		
	public void onScroll(float dx, float dy) {
		if (error) return;

		scroller.forceFinished(true);

		scrollX += dx;
		scrollY += dy;

		invalidate();
	}

	public void onScale(float focusX, float focusY, float scaleFactor) {
		if (error) return;

		scroller.forceFinished(true);

		float pageFocusX = (focusX + scrollX) / viewDPI;
		float pageFocusY = (focusY + scrollY) / viewDPI;
		if (viewDPI * scaleFactor >= minDPI && viewDPI * scaleFactor <= maxDPI) {
			viewDPI = viewDPI * scaleFactor;
			scrollX = pageFocusX * viewDPI - focusX;
			scrollY = pageFocusY * viewDPI - focusY;
			invalidate();
		}
	}

	public void onFling(float dx, float dy) {
		if (error) return;

		float canvasW = getWidth();
		float canvasH = getHeight();
		float bitmapW = bitmap.getWidth() * viewDPI / bitmapDPI;
		float bitmapH = bitmap.getHeight() * viewDPI / bitmapDPI;
		float maxX = bitmapW > canvasW ? bitmapW - canvasW : 0;
		float maxY = bitmapH > canvasH ? bitmapH - canvasH : 0;

		scroller.forceFinished(true);
		scroller.fling((int)scrollX, (int)scrollY, (int)-dx, (int)-dy, 0, (int)maxX, 0, (int)maxY);

		invalidate();
	}

	public void onDraw(Canvas canvas) {
		float x, y;

		if (bitmap == null) {
			if (error) {
				float w = getWidth();
				float h = getHeight();
				canvas.translate((w - 100) / 2, (h - 100) / 2);
				canvas.drawPath(errorPath, errorPaint);
			}
			return;
		}

		if (scroller.computeScrollOffset()) {
			scrollX = scroller.getCurrX();
			scrollY = scroller.getCurrY();
			invalidate(); /* keep animating */
		}

		float canvasW = getWidth();
		float bitmapW = bitmap.getWidth() * viewDPI / bitmapDPI;
		if (bitmapW <= canvasW) {
			scrollX = 0;
			x = (canvasW - bitmapW) / 2;
		} else {
			if (scrollX < 0) scrollX = 0;
			if (scrollX > bitmapW - canvasW) scrollX = bitmapW - canvasW;
			x = -scrollX;
		}

		float canvasH = getHeight();
		float bitmapH = bitmap.getHeight() * viewDPI / bitmapDPI;
		if (bitmapH <= canvasH) {
			scrollY = 0;
			y = (canvasH - bitmapH) / 2;
		} else {
			if (scrollY < 0) scrollY = 0;
			if (scrollY > bitmapH - canvasH) scrollY = bitmapH - canvasH;
			y = -scrollY;
		}

		float scale = viewDPI / bitmapDPI;
		transform.setScale(scale, scale);
		transform.postTranslate(x, y);
		canvas.drawBitmap(bitmap, transform, null);
	}
}
