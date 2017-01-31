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
	protected float viewScale;
	protected float scrollX, scrollY;
	protected Matrix transform;
	protected Scroller scroller;

	protected boolean error;
	protected Paint errorPaint;
	protected Path errorPath;

	protected float minScale, maxScale;

	public PageView(Context ctx, AttributeSet atts) {
		super(ctx, atts);

		scroller = new Scroller(ctx);

		transform = new Matrix();
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

	public void setBitmap(Bitmap b) {
		error = false;
		bitmap = b;
		invalidate();
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
		scrollX += dx;
		scrollY += dy;
		scroller.forceFinished(true);
		invalidate();
	}

	public void onFling(float dx, float dy) {
		if (bitmap == null) return;
		float canvasW = getWidth();
		float canvasH = getHeight();
		float bitmapW = bitmap.getWidth() * viewScale;
		float bitmapH = bitmap.getHeight() * viewScale;
		float maxX = bitmapW > canvasW ? bitmapW - canvasW : 0;
		float maxY = bitmapH > canvasH ? bitmapH - canvasH : 0;
		scroller.forceFinished(true);
		scroller.fling((int)scrollX, (int)scrollY, (int)-dx, (int)-dy, 0, (int)maxX, 0, (int)maxY);
		invalidate();
	}

	public void onScale(float focusX, float focusY, float scaleFactor) {
		if (bitmap == null) return;
		float pageFocusX = (focusX + scrollX) / viewScale;
		float pageFocusY = (focusY + scrollY) / viewScale;
		viewScale *= scaleFactor;
		if (viewScale < minScale) viewScale = minScale;
		if (viewScale > maxScale) viewScale = maxScale;
		scrollX = pageFocusX * viewScale - focusX;
		scrollY = pageFocusY * viewScale - focusY;
		scroller.forceFinished(true);
		invalidate();
	}

	public void onDraw(Canvas canvas) {
		float canvasW = getWidth();
		float canvasH = getHeight();
		float x, y;

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

		float bitmapW = bitmap.getWidth() * viewScale;
		if (bitmapW <= canvasW) {
			scrollX = 0;
			x = (canvasW - bitmapW) / 2;
		} else {
			if (scrollX < 0) scrollX = 0;
			if (scrollX > bitmapW - canvasW) scrollX = bitmapW - canvasW;
			x = -scrollX;
		}

		float bitmapH = bitmap.getHeight() * viewScale;
		if (bitmapH <= canvasH) {
			scrollY = 0;
			y = (canvasH - bitmapH) / 2;
		} else {
			if (scrollY < 0) scrollY = 0;
			if (scrollY > bitmapH - canvasH) scrollY = bitmapH - canvasH;
			y = -scrollY;
		}

		transform.setScale(viewScale, viewScale);
		transform.postTranslate(x, y);
		canvas.drawBitmap(bitmap, transform, null);
	}
}
