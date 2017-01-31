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
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.PopupMenu;
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
	protected View layoutButton;
	protected PopupMenu layoutPopupMenu;
	protected View outlineButton;

	protected View navigationBar;
	protected TextView pageLabel;
	protected SeekBar pageSeekbar;

	protected PageView pageView;
	protected GestureDetector detector;
	protected ScaleGestureDetector scaleDetector;

	protected int pageCount;
	protected int currentPage;
	protected boolean wentBack;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		setContentView(R.layout.document_activity);

		pageView = (PageView)findViewById(R.id.page_view);
		actionBar = findViewById(R.id.action_bar);
		titleLabel = (TextView)findViewById(R.id.title_label);
		layoutButton = (View)findViewById(R.id.layout_button);
		outlineButton = (View)findViewById(R.id.outline_button);
		navigationBar = findViewById(R.id.navigation_bar);
		pageLabel = (TextView)findViewById(R.id.page_label);
		pageSeekbar = (SeekBar)findViewById(R.id.page_seekbar);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayDPI = metrics.densityDpi;

		/* Note: we only support file:// URIs. Supporting content:// will be trickier. */
		path = getIntent().getData().getPath();

		title = path.substring(path.lastIndexOf('/') + 1);
		titleLabel.setText(title);

		worker = new Worker(this);
		worker.start();

		pageView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				int oldCanvasW = canvasW;
				int oldCanvasH = canvasH;
				canvasW = v.getWidth();
				canvasH = v.getHeight();
				if (pageCount == 0)
					loadDocument();
				if (oldCanvasW != canvasW || oldCanvasH != canvasH)
					updatePage();
			}
		});

		detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			public boolean onSingleTapUp(MotionEvent event) {
				if (!pageView.onSingleTapUp(event.getX(), event.getY())) {
					float x = event.getX();
					float a = canvasW / 3;
					float b = a * 2;
					if (x <= a) gotoPreviousPage();
					if (x >= b) gotoNextPage();
					if (x > a && x < b) toggleToolbars();
				}
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
				// TODO: render high res bitmap patch
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

		layoutPopupMenu = new PopupMenu(this, layoutButton);
		{
			Menu m = layoutPopupMenu.getMenu();
			m.add(0, 6, 0, "6pt");
			m.add(0, 7, 0, "7pt");
			m.add(0, 8, 0, "8pt");
			m.add(0, 9, 0, "9pt");
			m.add(0, 10, 0, "10pt");
			m.add(0, 11, 0, "11pt");
			m.add(0, 12, 0, "12pt");
			m.add(0, 13, 0, "13pt");
			m.add(0, 14, 0, "14pt");
			m.add(0, 15, 0, "15pt");
			m.add(0, 16, 0, "16pt");
		}
		layoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				int id = item.getItemId();
				if (id > 5 && id < 24 && id != layoutEm) {
					layoutEm = id;
					relayoutDocument();
				}
				return true;
			}
		});
		layoutButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				layoutPopupMenu.show();
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
			actionBar.setVisibility(View.GONE);
			navigationBar.setVisibility(View.GONE);
		} else {
			actionBar.setVisibility(View.VISIBLE);
			navigationBar.setVisibility(View.VISIBLE);
		}
	}

	protected void gotoPreviousPage() {
		if (pageView.goBackward()) {
			if (currentPage > 0) {
				currentPage --;
				wentBack = true;
				updatePage();
			}
		}
	}

	protected void gotoNextPage() {
		if (pageView.goForward()) {
			if (currentPage < pageCount - 1) {
				currentPage ++;
				updatePage();
			}
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
			private boolean isReflowable;
			public void work() {
				try {
					doc = new Document(input);
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					updateOutline();
					isReflowable = doc.isReflowable();
					String metaTitle = doc.getMetaData(Document.META_INFO_TITLE);
					if (metaTitle != null)
						title = metaTitle;
					currentPage = 0;
				} catch (Throwable x) {
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
				if (isReflowable)
					layoutButton.setVisibility(View.VISIBLE);
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

	protected void relayoutDocument() {
		final float savedPosition = (float)currentPage / pageCount;
		worker.add(new Worker.Task<Void,Void>(null) {
			public void work() {
				doc.layout(layoutW, layoutH, layoutEm);
				pageCount = doc.countPages();
				updateOutline();
			}
			public void run() {
				currentPage = (int)(savedPosition * pageCount);
				updatePage();
			}
		});
	}

	private Bitmap drawPage(int pageNumber) {
		Bitmap bitmap = null;
		try {
			Log.i(APP, "load page " + pageNumber);
			Page page = doc.loadPage(pageNumber);
			Log.i(APP, "draw page " + pageNumber);
			//bitmap = AndroidDrawDevice.drawPage(page, displayDPI);
			//bitmap = AndroidDrawDevice.drawPageFit(page, canvasW, canvasH);
			bitmap = AndroidDrawDevice.drawPageFitWidth(page, canvasW);
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
				if (output != null)
					pageView.setBitmap(output, wentBack);
				else
					pageView.setError();
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				pageSeekbar.setProgress(input);
				wentBack = false;
			}
		});
	}
}
