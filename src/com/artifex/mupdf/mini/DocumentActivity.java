package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
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
	protected boolean hasLoaded;
	protected boolean isReflowable;
	protected Document doc;
	protected String title;
	protected ArrayList<OutlineActivity.Item> flatOutline;
	protected float layoutW, layoutH, layoutEm;
	protected float displayDPI;
	protected int canvasW, canvasH;
	protected SharedPreferences prefs;

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

		prefs = getPreferences(Context.MODE_PRIVATE);
		layoutEm = prefs.getFloat("layoutEm", 8);
		currentPage = prefs.getInt(path, 0);
		hasLoaded = false;

		pageView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
			public void onLayoutChange(View v, int l, int t, int r, int b,
					int ol, int ot, int or, int ob) {
				int oldCanvasW = canvasW;
				int oldCanvasH = canvasH;
				canvasW = v.getWidth();
				canvasH = v.getHeight();
				layoutW = canvasW * 72 / displayDPI;
				layoutH = canvasH * 72 / displayDPI;
				if (!hasLoaded) {
					hasLoaded = true;
					loadDocument();
					loadPage();
					loadOutline();
				} else if (oldCanvasW != canvasW || oldCanvasH != canvasH) {
					if (isReflowable)
						relayoutDocument();
					else
						loadPage();
				}
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
				gotoPage(newProgress);
			}
		});

		layoutPopupMenu = new PopupMenu(this, layoutButton);
		layoutPopupMenu.getMenuInflater().inflate(R.menu.layout_menu, layoutPopupMenu.getMenu());
		layoutPopupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			public boolean onMenuItemClick(MenuItem item) {
				float oldLayoutEm = layoutEm;
				switch (item.getItemId()) {
				case R.id.action_layout_6pt: layoutEm = 6; break;
				case R.id.action_layout_7pt: layoutEm = 7; break;
				case R.id.action_layout_8pt: layoutEm = 8; break;
				case R.id.action_layout_9pt: layoutEm = 9; break;
				case R.id.action_layout_10pt: layoutEm = 10; break;
				case R.id.action_layout_11pt: layoutEm = 11; break;
				case R.id.action_layout_12pt: layoutEm = 12; break;
				case R.id.action_layout_13pt: layoutEm = 13; break;
				case R.id.action_layout_14pt: layoutEm = 14; break;
				case R.id.action_layout_15pt: layoutEm = 15; break;
				case R.id.action_layout_16pt: layoutEm = 16; break;
				}
				if (oldLayoutEm != layoutEm)
					relayoutDocument();
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

	public void onPause() {
		super.onPause();
		SharedPreferences.Editor editor = prefs.edit();
		editor.putFloat("layoutEm", layoutEm);
		editor.putInt(path, currentPage);
		editor.commit();
	}

	public void onActivityResult(int request, int result, Intent data) {
		if (request == NAVIGATE_REQUEST && result >= RESULT_FIRST_USER)
			gotoPage(result - RESULT_FIRST_USER);
	}

	protected void loadDocument() {
		worker.add(new Worker.Task() {
			public void work() {
				try {
					Log.i(APP, "load document");
					doc = new Document(path);
					String metaTitle = doc.getMetaData(Document.META_INFO_TITLE);
					if (metaTitle != null)
						title = metaTitle;
					isReflowable = doc.isReflowable();
					if (isReflowable) {
						Log.i(APP, "layout document");
						doc.layout(layoutW, layoutH, layoutEm);
					}
					pageCount = doc.countPages();
				} catch (Throwable x) {
					Log.e(APP, x.getMessage());
					doc = null;
					pageCount = 1;
					currentPage = 0;
				}
			}
			public void run() {
				if (currentPage < 0 || currentPage >= pageCount)
					currentPage = 0;
				titleLabel.setText(title);
				if (isReflowable)
					layoutButton.setVisibility(View.VISIBLE);
				pageSeekbar.setMax(pageCount - 1);
			}
		});
	}

	protected void relayoutDocument() {
		worker.add(new Worker.Task() {
			public void work() {
				try {
					long mark = doc.makeBookmark(currentPage);
					Log.i(APP, "relayout document");
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					currentPage = doc.findBookmark(mark);
				} catch (Throwable x) {
					Log.e(APP, x.getMessage());
					pageCount = 1;
					currentPage = 0;
				}
			}
			public void run() {
				loadPage();
				loadOutline();
			}
		});
	}

	private void loadOutline() {
		worker.add(new Worker.Task() {
			private void flattenOutline(Outline[] outline, String indent) {
				for (Outline node : outline) {
					if (node.title != null)
						flatOutline.add(new OutlineActivity.Item(indent + node.title, node.page));
					if (node.down != null)
						flattenOutline(node.down, indent + "    ");
				}
			}
			public void work() {
				Log.i(APP, "load outline");
				Outline[] outline = doc.loadOutline();
				if (outline != null) {
					flatOutline = new ArrayList<OutlineActivity.Item>();
					flattenOutline(outline, "");
				} else {
					flatOutline = null;
				}
			}
			public void run() {
				if (flatOutline != null)
					outlineButton.setVisibility(View.VISIBLE);
			}
		});
	}

	protected void loadPage() {
		final int pageNumber = currentPage;
		worker.add(new Worker.Task() {
			public Bitmap bitmap;
			public void work() {
				try {
					Log.i(APP, "load page " + pageNumber);
					Page page = doc.loadPage(pageNumber);
					Log.i(APP, "draw page " + pageNumber);
					bitmap = AndroidDrawDevice.drawPageFitWidth(page, canvasW);
				} catch (Throwable x) {
					Log.e(APP, x.getMessage());
				}
			}
			public void run() {
				if (bitmap != null)
					pageView.setBitmap(bitmap, wentBack);
				else
					pageView.setError();
				pageLabel.setText((currentPage+1) + " / " + pageCount);
				pageSeekbar.setProgress(pageNumber);
				wentBack = false;
			}
		});
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
				wentBack = true;
				gotoPage(currentPage - 1);
			}
		}
	}

	protected void gotoNextPage() {
		if (pageView.goForward()) {
			if (currentPage < pageCount - 1) {
				gotoPage(currentPage + 1);
			}
		}
	}

	protected void gotoPage(int p) {
		if (p >= 0 && p < pageCount && p != currentPage) {
			currentPage = p;
			loadPage();
		}
	}
}
