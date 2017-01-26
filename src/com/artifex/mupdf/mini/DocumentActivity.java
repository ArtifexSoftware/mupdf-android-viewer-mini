package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class DocumentActivity extends Activity
{
	protected Worker worker;
	protected String path;
	protected Document doc;
	protected int canvasW;
	protected int canvasH;
	protected TextView documentLabel;
	protected TextView pageLabel;
	protected SeekBar seekbar;
	protected ImageView canvas;

	protected int pageCount;
	protected int currentPage;

	protected Bitmap drawPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Page page = doc.loadPage(pageNumber);
			Rect bounds = page.getBounds();
			float pageW = bounds.x1 - bounds.x0;
			float pageH = bounds.y1 - bounds.y0;

			/* Scale page to fit the canvas */
			float hscale = (float)canvasW / pageW;
			float vscale = (float)canvasH / pageH;
			float scale = hscale < vscale ? hscale : vscale;
			hscale = (float)Math.floor(pageW * scale) / pageW;
			vscale = (float)Math.floor(pageH * scale) / pageH;
			Matrix ctm = new Matrix(hscale, vscale);

			Log.i("MuPDF", "render page " + pageNumber);

			RectI bbox = new RectI(bounds.transform(ctm));
			bitmap = Bitmap.createBitmap(bbox.x1 - bbox.x0, bbox.y1 - bbox.y0, Bitmap.Config.ARGB_8888);
			AndroidDrawDevice dev = new AndroidDrawDevice(bitmap, bbox, bbox);
			page.run(dev, ctm, null);
			dev.close();
			dev.destroy();
		} catch (Exception x) {
			Log.e("MuPDF", x.getMessage());
		}
		return bitmap;
	}

	@Override
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

		/* Note: we only support file:// URIs. Supporting content:// will be trickier. */
		path = getIntent().getData().getPath();

		documentLabel.setText(path.substring(path.lastIndexOf('/') + 1));

		worker.add(new Worker.Task<Void,String>(path) {
			public void work() {
				try {
					doc = new Document(input);
					pageCount = doc.countPages();
					currentPage = 0;
				} catch (Exception x) {
					Log.e("MuPDF", x.getMessage());
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
