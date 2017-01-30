package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;

public class DocumentActivity extends Activity
{
	private final String APP = "MuPDF";

	public final int NAVIGATE_REQUEST = 1;

	protected Worker worker;
	protected String path;
	protected Document doc;
	protected String title;
	protected ArrayList<OutlineActivity.Item> flatOutline;
	protected float layoutW, layoutH, layoutEm;
	protected float displayDPI;
	protected int canvasW, canvasH;

	protected View actionBar;
	protected TextView titleLabel;
	protected Button outlineButton;

	protected View navigationBar;
	protected TextView pageLabel;
	protected SeekBar pageSeekbar;

	protected PageView pageView;
	protected GestureDetector detector;
	protected ScaleGestureDetector scaleDetector;

	protected int pageCount;
	protected int currentPage;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.document_activity);

		actionBar = findViewById(R.id.action_bar);
		navigationBar = findViewById(R.id.navigation_bar);
		outlineButton = (Button)findViewById(R.id.outline_button);
		titleLabel = (TextView)findViewById(R.id.title_label);
		pageLabel = (TextView)findViewById(R.id.page_label);
		pageSeekbar = (SeekBar)findViewById(R.id.page_seekbar);
		pageView = (PageView)findViewById(R.id.page_view);

		worker = new Worker(this);
		worker.start();

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayDPI = (metrics.xdpi + metrics.ydpi) / 2;

		pageView.setDisplayDPI(displayDPI);

		/* Note: we only support file:// URIs. Supporting content:// will be trickier. */
		path = getIntent().getData().getPath();

		title = path.substring(path.lastIndexOf('/') + 1);
		titleLabel.setText(title);

		pageView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
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

		detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			public boolean onSingleTapUp(MotionEvent event) {
				Log.i(APP, "onSingleTapUp");
				float x = event.getX();
				float a = canvasW / 3;
				float b = a * 2;
				if (x <= a) gotoPreviousPage();
				if (x >= b) gotoNextPage();
				if (x > a && x < b) toggleToolbars();
				return true;
			};
			public boolean onDown(MotionEvent e) {
				pageView.onDown();
				return true;
			}
			public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
				pageView.onScroll(dx, dy);
				return true;
			}
			public boolean onFling(MotionEvent e1, MotionEvent e2, float dx, float dy) {
				pageView.onFling(dx, dy);
				return true;
			}
		});

		scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
			public boolean onScale(ScaleGestureDetector det) {
				pageView.onScale(det.getFocusX(), det.getFocusY(), det.getScaleFactor());
				return true;
			}
			public void onScaleEnd(ScaleGestureDetector det) {
				// TODO: re-render bitmap at new resolution
			}
		});

		pageView.setOnTouchListener(new View.OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				detector.onTouchEvent(event);
				scaleDetector.onTouchEvent(event);
				return true;
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

		outlineButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(DocumentActivity.this, OutlineActivity.class);
				Bundle bundle = new Bundle();
				bundle.putInt("POSITION", currentPage);
				bundle.putSerializable("OUTLINE", flatOutline);
				intent.putExtras(bundle);
				startActivityForResult(intent, NAVIGATE_REQUEST);
			}
		});
	}

	public void onActivityResult(int request, int result, Intent data) {
		if (request == NAVIGATE_REQUEST && result >= RESULT_FIRST_USER) {
			int newPage = result - RESULT_FIRST_USER;
			if (newPage >= 0 && newPage < pageCount && newPage != currentPage) {
				currentPage = newPage;
				updatePage();
			}
		}
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

	protected void gotoPreviousPage() {
		if (currentPage > 0) {
			currentPage --;
			updatePage();
		}
	}

	protected void gotoNextPage() {
		if (currentPage < pageCount - 1) {
			currentPage ++;
			updatePage();
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
			public void work() {
				try {
					doc = new Document(input);
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					updateOutline();
					String metaTitle = doc.getMetaData(Document.META_INFO_TITLE);
					if (metaTitle != null)
						title = metaTitle;
					currentPage = 0;
				} catch (Exception x) {
					Log.e(APP, x.getMessage());
					doc = null;
					pageCount = 1;
					currentPage = 0;
				}
			}
			public void run() {
				titleLabel.setText(title);
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				pageSeekbar.setMax(pageCount - 1);
				pageSeekbar.setProgress(currentPage);
				if (flatOutline != null)
					outlineButton.setVisibility(View.VISIBLE);
			}
		});
	}

	private void flattenOutline(Outline[] outline, String indent) {
		for (Outline node : outline) {
			if (node.title != null)
				flatOutline.add(new OutlineActivity.Item(indent + node.title, node.page));
			if (node.down != null)
				flattenOutline(node.down, indent + "    ");
		}
	}

	private void updateOutline() {
		Outline[] outline = doc.loadOutline();
		if (outline != null) {
			flatOutline = new ArrayList<OutlineActivity.Item>();
			flattenOutline(outline, "");
		} else {
			flatOutline = null;
		}
	}

	private Bitmap drawPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Log.i(APP, "load page " + pageNumber);
			Page page = doc.loadPage(pageNumber);
			Log.i(APP, "draw page " + pageNumber);
			//bitmap = AndroidDrawDevice.drawPage(page, displayDPI);
			bitmap = AndroidDrawDevice.drawPageFit(page, canvasW, canvasH);
			//bitmap = AndroidDrawDevice.drawPageFitWidth(page, canvasW);
		} catch (Throwable x) {
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
				pageView.setBitmap(output, displayDPI);
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				pageSeekbar.setProgress(input);
			}
		});
	}
}
