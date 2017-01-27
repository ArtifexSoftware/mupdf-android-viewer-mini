package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class DocumentActivity extends Activity
{
	private final String APP = "MuPDF";

	protected Worker worker;
	protected String path;
	protected Document doc;
	protected float layoutW, layoutH, layoutEm;
	protected int canvasW, canvasH;
	protected TextView documentLabel;
	protected TextView pageLabel;
	protected SeekBar seekbar;
	protected ImageView canvas;

	protected int pageCount;
	protected int currentPage;

	protected Bitmap drawPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Log.i(APP, "load page " + pageNumber);
			Page page = doc.loadPage(pageNumber);
			Log.i(APP, "draw page " + pageNumber);
			bitmap = AndroidDrawDevice.drawPageFit(page, canvasW, canvasH);
		} catch (Exception x) {
			Log.e(APP, x.getMessage());
		}
		return bitmap;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.document_activity);

		documentLabel = (TextView)findViewById(R.id.label_document);
		pageLabel = (TextView)findViewById(R.id.label_page);
		seekbar = (SeekBar)findViewById(R.id.seekbar);
		canvas = (ImageView)findViewById(R.id.canvas);

		worker = new Worker(this);
		worker.start();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		layoutW = metrics.widthPixels * 72 / metrics.xdpi;
		layoutH = metrics.heightPixels * 72 / metrics.ydpi;
		layoutEm = 10;

		/* Note: we only support file:// URIs. Supporting content:// will be trickier. */
		path = getIntent().getData().getPath();

		documentLabel.setText(path.substring(path.lastIndexOf('/') + 1));

		worker.add(new Worker.Task<Void,String>(path) {
			public void work() {
				try {
					doc = new Document(input);
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					currentPage = 0;
				} catch (Exception x) {
					Log.e(APP, x.getMessage());
					doc = null;
					pageCount = 1;
					currentPage = 0;
				}
			}
			public void run() {
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				seekbar.setMax(pageCount - 1);
				seekbar.setProgress(currentPage);
			}
		});

		canvas.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				int newCanvasW = canvas.getWidth();
				int newCanvasH = canvas.getHeight();
				if (newCanvasW != canvasW || newCanvasH != canvasH) {
					canvasW = newCanvasW;
					canvasH = newCanvasH;
					updatePage();
				}
			}
		});

		canvas.setOnTouchListener(new View.OnTouchListener() {
			public float startX;
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					startX = event.getX();
					return true;
				case MotionEvent.ACTION_UP:
					float endX = event.getX();
					float a = canvasW / 3;
					float b = a * 2;
					if (startX <= a && endX <= a) {
						if (currentPage > 0) {
							currentPage --;
							updatePage();
						}
					}
					if (startX >= b && endX >= b) {
						if (currentPage < pageCount - 1) {
							currentPage ++;
							updatePage();
						}
					}
					if (startX > a && startX < b && endX > a && endX < b) {
						// TODO: toggle toolbar
					}
					return true;
				}
				return false;
			}
		});

		seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public int newProgress = -1;
			public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
				if (fromUser) {
					newProgress = progress;
					pageLabel.setText((progress+1) + " / " + pageCount);
				}
			}
			public void onStartTrackingTouch(SeekBar seekbar) {}
			public void onStopTrackingTouch(SeekBar seekbar) {
				if (newProgress >= 0 && newProgress != currentPage) {
					currentPage = newProgress;
					updatePage();
				}
			}
		});
	}

	public void updatePage() {
		worker.add(new Worker.Task<Bitmap,Integer>(currentPage) {
			public void work() {
				output = drawPage(input);
			}
			public void run() {
				if (output != null)
					canvas.setImageBitmap(output);
				else
					canvas.setImageResource(R.drawable.error_page);
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				seekbar.setProgress(input);
			}
		});
	}
}
