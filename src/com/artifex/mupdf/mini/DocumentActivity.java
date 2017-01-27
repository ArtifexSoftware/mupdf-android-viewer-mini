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
import android.view.WindowManager;
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
	protected float displayDPI;
	protected int canvasW, canvasH;

	protected View actionBar;
	protected View navigationBar;
	protected TextView titleLabel;
	protected TextView pageLabel;
	protected SeekBar pageSeekbar;
	protected ImageView canvas;

	protected int pageCount;
	protected int currentPage;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.document_activity);

		actionBar = findViewById(R.id.action_bar);
		navigationBar = findViewById(R.id.navigation_bar);
		titleLabel = (TextView)findViewById(R.id.title_label);
		pageLabel = (TextView)findViewById(R.id.page_label);
		pageSeekbar = (SeekBar)findViewById(R.id.page_seekbar);
		canvas = (ImageView)findViewById(R.id.canvas);

		worker = new Worker(this);
		worker.start();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayDPI = (metrics.xdpi + metrics.ydpi) / 2;

		/* Note: we only support file:// URIs. Supporting content:// will be trickier. */
		path = getIntent().getData().getPath();

		titleLabel.setText(path.substring(path.lastIndexOf('/') + 1));

		canvas.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				int oldCanvasW = canvasW;
				int oldCanvasH = canvasH;
				canvasW = v.getWidth();
				canvasH = v.getHeight();
				if (pageCount == 0) {
					loadDocument();
				}
				if (oldCanvasW != canvasW || oldCanvasH != canvasH) {
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
						toggleToolbars();
					}
					return true;
				}
				return false;
			}
		});

		pageSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
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

	protected void toggleToolbars() {
		if (actionBar.getVisibility() == View.VISIBLE) {
			actionBar.setVisibility(View.INVISIBLE);
			navigationBar.setVisibility(View.INVISIBLE);
		} else {
			actionBar.setVisibility(View.VISIBLE);
			navigationBar.setVisibility(View.VISIBLE);
		}
	}

	protected void loadDocument() {
		layoutEm = 8;
		if (canvasH > canvasW) {
			layoutW = canvasW * 72 / displayDPI;
			layoutH = canvasH * 72 / displayDPI;
		} else {
			layoutW = canvasH * 72 / displayDPI;
			layoutH = canvasW * 72 / displayDPI;
		}
		worker.add(new Worker.Task<Void,String>(path) {
			public String title;
			public void work() {
				try {
					doc = new Document(input);
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					title = doc.getMetaData(Document.META_INFO_TITLE);
					currentPage = 0;
				} catch (Exception x) {
					Log.e(APP, x.getMessage());
					doc = null;
					pageCount = 1;
					currentPage = 0;
				}
			}
			public void run() {
				if (title != null)
					titleLabel.setText(title);
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				pageSeekbar.setMax(pageCount - 1);
				pageSeekbar.setProgress(currentPage);
			}
		});
	}

	protected Bitmap drawPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Log.i(APP, "load page " + pageNumber);
			Page page = doc.loadPage(pageNumber);
			Log.i(APP, "draw page " + pageNumber);
			//bitmap = AndroidDrawDevice.drawPage(page, displayDPI);
			bitmap = AndroidDrawDevice.drawPageFit(page, canvasW, canvasH);
			//bitmap = AndroidDrawDevice.drawPageFitWidth(page, canvasW);
		} catch (Exception x) {
			Log.e(APP, x.getMessage());
		}
		return bitmap;
	}

	protected void updatePage() {
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
				pageSeekbar.setProgress(input);
			}
		});
	}
}
