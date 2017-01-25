package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;
import android.widget.ImageView;
import android.widget.SeekBar;

public class DocumentActivity extends Activity
{
	protected Worker worker;
	protected String path;
	protected Document doc;
	protected TextView documentLabel;
	protected TextView pageLabel;
	protected SeekBar seekbar;
	protected ImageView canvas;

	protected int pageCount;
	protected int currentPage;

	protected Bitmap renderPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Page page = doc.loadPage(pageNumber);
			Matrix ctm = new Matrix();
			Rect bounds = page.getBounds().transform(ctm);
			RectI bbox = new RectI(bounds);
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

		updatePage();
	}

	public void updatePage() {
		worker.add(new Worker.Task<Bitmap,Integer>(currentPage) {
			public void work() {
				output = renderPage(input);
			}
			public void run() {
				canvas.setImageBitmap(output);
				seekbar.setProgress(input);
			}
		});
	}
}
