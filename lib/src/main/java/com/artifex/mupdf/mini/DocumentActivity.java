package com.artifex.mupdf.mini;

import com.artifex.mupdf.fitz.*;
import com.artifex.mupdf.fitz.android.*;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ClipData;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.FileUriExposedException;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Stack;

public class DocumentActivity extends Activity
{
	private final String APP = "MuPDF";

	public final int NAVIGATE_REQUEST = 1;

	protected final int MAXIMUM_OUTLINE_ITEMS = 1000;
	protected final int MAXIMUM_OUTLINE_DEPTH = 4;

	protected final float EXCLUSION_HEIGHT_FACTOR = 2.0f;

	protected Worker worker;
	protected SharedPreferences prefs;

	protected Document doc;

	protected String key;
	protected String mimetype;
	protected SeekableInputStream stream;
	protected byte[] buffer;

	protected boolean returnToLibraryActivity;
	protected boolean hasLoaded;
	protected boolean isReflowable;
	protected boolean fitPage;
	protected String title;
	protected ArrayList<OutlineActivity.Item> flatOutline;
	protected float layoutW, layoutH, layoutEm;
	protected float displayDPI;
	protected int canvasW, canvasH;
	protected float pageZoom;

	protected View currentBar;
	protected PageView pageView;
	protected TextView titleLabel;
	protected View bottomBar;
	protected View backgroundLayout;
	protected ActionBar actionBar;
	protected TextView pageLabel;
	protected SeekBar pageSeekbar;
	private Menu actionBarMenu;
	private SearchView searchView;
	private MenuItem searchMenuItem;
	private MenuItem searchPreviousMenuItem;
	private MenuItem searchNextMenuItem;

	protected boolean pageCountChanged;
	protected int pageCount;
	protected int currentPage;
	protected int searchHitPage;
	protected String searchNeedle;
	protected boolean stopSearch;
	protected Stack<Integer> history;
	protected boolean wentBack;
	protected boolean toggledUI;
	protected Insets systemInsets = Insets.NONE;
	protected boolean newSearchHitPage;

	private String toHex(byte[] digest) {
		StringBuilder builder = new StringBuilder(2 * digest.length);
		for (byte b : digest)
			builder.append(String.format("%02x", b));
		return builder.toString();
	}

	private void openInput(Uri uri, long size, String mimetype) throws IOException {
		ContentResolver cr = getContentResolver();

		Log.i(APP, "Opening document " + uri);

		InputStream is = cr.openInputStream(uri);
		byte[] buf = null;
		int used = -1;
		try {
			final int limit = 8 * 1024 * 1024;
			if (size < 0) { // size is unknown
				buf = new byte[limit];
				used = is.read(buf);
				boolean atEOF = is.read() == -1;
				if (used < 0 || (used == limit && !atEOF)) // no or partial data
					buf = null;
			} else if (size <= limit) { // size is known and below limit
				buf = new byte[(int) size];
				used = is.read(buf);
				if (used < 0 || used < size) // no or partial data
					buf = null;
			}
			if (buf != null && buf.length != used) {
				byte[] newbuf = new byte[used];
				System.arraycopy(buf, 0, newbuf, 0, used);
				buf = newbuf;
			}
		} catch (OutOfMemoryError e) {
			buf = null;
		} finally {
			is.close();
		}

		if (buf != null) {
			Log.i(APP, "  Opening document from memory buffer of size " + buf.length);
			buffer = buf;
		} else {
			Log.i(APP, "  Opening document from stream");
			stream = new ContentInputStream(cr, uri, size);
		}
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		displayDPI = metrics.densityDpi;

		setContentView(R.layout.document_activity);
		bottomBar = findViewById(R.id.bottom_bar);
		backgroundLayout = findViewById(R.id.background_layout);
		actionBar = getActionBar();

		// Enable edge-to-edge mode with ActionBar
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
			getWindow().setDecorFitsSystemWindows(false);
		} else {
			getWindow().getDecorView().setSystemUiVisibility(
				View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
				View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | 
				View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
			);
		}

		// Set up window insets listener for edge-to-edge
		backgroundLayout.setOnApplyWindowInsetsListener((v, insets) -> {
			applyInsets(insets);
			return insets;
		});

		actionBar.hide();
		actionBar.setDisplayHomeAsUpEnabled(false);
		actionBar.setTitle(title);

		Uri uri = getIntent().getData();
		mimetype = getIntent().getType();

		if (uri == null) {
			Toast.makeText(this, getString(R.string.toast_no_document_uri), Toast.LENGTH_SHORT).show();
			return;
		}

		returnToLibraryActivity = getIntent().getIntExtra(getComponentName().getPackageName() + ".ReturnToLibraryActivity", 0) != 0;

		key = uri.toString();

		Log.i(APP, "OPEN URI " + uri.toString());
		Log.i(APP, "  MAGIC (Intent) " + mimetype);

		title = "";
		long size = -1;
		Cursor cursor = null;

		try {
			cursor = getContentResolver().query(uri, null, null, null, null, null);
			if (cursor != null && cursor.moveToFirst()){
				int idx;

				idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_STRING)
					title = cursor.getString(idx);

				idx = cursor.getColumnIndex(OpenableColumns.SIZE);
				if (idx >= 0 && cursor.getType(idx) == Cursor.FIELD_TYPE_INTEGER)
					size = cursor.getLong(idx);

				if (size == 0)
					size = -1;
			}
		} catch (Exception x) {
			// Ignore any exception and depend on default values for title
			// and size (unless one was decoded
		} finally {
			if (cursor != null)
				cursor.close();
		}

		Log.i(APP, "  NAME " + title);
		Log.i(APP, "  SIZE " + size);

		if (mimetype == null || mimetype.equals("application/octet-stream")) {
			mimetype = getContentResolver().getType(uri);
			Log.i(APP, "  MAGIC (Resolver) " + mimetype);
		}
		if (mimetype == null || mimetype.equals("application/octet-stream")) {
			mimetype = title;
			Log.i(APP, "  MAGIC (Filename) " + mimetype);
		}

		try {
			openInput(uri, size, mimetype);
		} catch (Exception x) {
			Log.e(APP, x.toString());
			String text = x.getMessage();
			if (text == null)
				text = x.getClass().getName();
			Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
		}

		history = new Stack<Integer>();

		worker = new Worker(this);
		worker.start();

		prefs = getPreferences(Context.MODE_PRIVATE);
		layoutEm = prefs.getFloat("layoutEm", 8);
		fitPage = prefs.getBoolean("fitPage", false);
		currentPage = prefs.getInt(key, 0);
		searchHitPage = -1;
		hasLoaded = false;

		pageView = (PageView)findViewById(R.id.page_view);
		pageView.setActionListener(this);

		pageLabel = (TextView)findViewById(R.id.page_label);
		pageSeekbar = (SeekBar)findViewById(R.id.page_seekbar);
		pageSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			public int newProgress = -1;
			public void onProgressChanged(SeekBar seekbar, int progress, boolean fromUser) {
				if (fromUser) {
					newProgress = progress;
					showPageNumber(progress + 1);
				}
			}
			public void onStartTrackingTouch(SeekBar seekbar) {}
			public void onStopTrackingTouch(SeekBar seekbar) {
				gotoPage(newProgress);
			}
		});

		if (Build.VERSION.SDK_INT >= 29)
			bottomBar.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
				public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					View parent = (View) v.getParent();
					android.graphics.Rect exclusion;

					exclusion = new android.graphics.Rect(0, 0, v.getWidth(), v.getHeight());
					v.setSystemGestureExclusionRects(Collections.singletonList(exclusion));

					int extended_top = parent.getHeight() - (int) (EXCLUSION_HEIGHT_FACTOR * v.getHeight());
					exclusion = new android.graphics.Rect(0, extended_top, parent.getWidth(), parent.getHeight());
					parent.setSystemGestureExclusionRects(Collections.singletonList(exclusion));
				}
			});
	}

	protected void showPageNumber(int pageNumber) {
		if (pageCountChanged && bottomBar.getVisibility() == View.VISIBLE)
		{
			// set width of longest possible text as minimum width this
			// ensures that the size of pageLabel doesn't change whether
			// it renders 1 / pageCount or pageCount / pageCount,
			// which makes pageSeekbar to the left of pageLabel also have
			// a stable width.

			TextPaint paint = pageLabel.getPaint();
			float maxWidth =
				pageLabel.getPaddingLeft() +
				paint.measureText(pageCount + " / " + pageCount) + // longest possible text
				pageLabel.getPaddingRight();
			int minWidth = (int) Math.ceil(maxWidth);
			if (minWidth > 0)
			{
				pageLabel.setMinWidth(minWidth);
				pageCountChanged = false;
			}
		}

		pageLabel.setText(pageNumber + " / " + pageCount);
	}

	protected void applyInsets(WindowInsets windowInsets) {
		systemInsets = Insets.NONE;
		Insets systemBarInsets = windowInsets.getInsets(WindowInsets.Type.systemBars());
		systemInsets = Insets.max(systemInsets, systemBarInsets);
		Insets cutoutInsets = windowInsets.getInsets(WindowInsets.Type.displayCutout());
		systemInsets = Insets.max(systemInsets, cutoutInsets);
		bottomBar.setPadding(0, 0, 0, systemInsets.bottom);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.action_bar_menu, menu);
		actionBarMenu = menu;

		// Configure SearchView
		searchMenuItem = menu.findItem(R.id.action_search);
		searchView = (SearchView) searchMenuItem.getActionView();

		// Configure search navigation buttons
		searchPreviousMenuItem = menu.findItem(R.id.action_search_previous);
		searchNextMenuItem = menu.findItem(R.id.action_search_next);
		if (searchPreviousMenuItem != null) searchPreviousMenuItem.setVisible(false);
		if (searchNextMenuItem != null) searchNextMenuItem.setVisible(false);

		// Set query hint
		searchView.setQueryHint(getString(R.string.text_search_hint));

		// Set IME options to ensure search action is available
		searchView.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
		searchView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_NORMAL | EditorInfo.TYPE_CLASS_TEXT);

		// Set up search text listener
		searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
			@Override
			public boolean onQueryTextSubmit(String query) {
				search(1);  // Search forward on submit
				showSearchNavigationButtons(true);
				return true;
			}

			@Override
			public boolean onQueryTextChange(String newText) {
				if (newText.isEmpty()) {
					stopSearch = true;
					searchHitPage = -1;
					searchNeedle = null;
					pageView.resetHits();
					showSearchNavigationButtons(false);
				}
				return true;
			}
		});

		// Handle search close
		searchView.setOnCloseListener(new SearchView.OnCloseListener() {
			@Override
			public boolean onClose() {
				hideSearch();
				return false;  // Allow default close behavior
			}
		});

		// Enable SearchView's built-in navigation buttons
		searchView.setIconifiedByDefault(false);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_search) {
			searchMenuItem.expandActionView();
			return true;
		} else if (id == R.id.action_search_previous) {
			if (searchNeedle != null) search(-1);
			return true;
		} else if (id == R.id.action_search_next) {
			if (searchNeedle != null) search(1);
			return true;
		} else if (id == R.id.action_share) {
			shareDocument();
			return true;
		} else if (id == R.id.action_zoom) {
			fitPage = !fitPage;
			loadPage();
			return true;
		} else if (id == R.id.action_layout) {
			PopupMenu popup = new PopupMenu(this, findViewById(R.id.action_layout));
			popup.getMenuInflater().inflate(R.menu.layout_menu, popup.getMenu());
			popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
				public boolean onMenuItemClick(MenuItem item) {
					float oldLayoutEm = layoutEm;
					int id = item.getItemId();
					if (id == R.id.action_layout_6pt) layoutEm = 6;
					else if (id == R.id.action_layout_7pt) layoutEm = 7;
					else if (id == R.id.action_layout_8pt) layoutEm = 8;
					else if (id == R.id.action_layout_9pt) layoutEm = 9;
					else if (id == R.id.action_layout_10pt) layoutEm = 10;
					else if (id == R.id.action_layout_11pt) layoutEm = 11;
					else if (id == R.id.action_layout_12pt) layoutEm = 12;
					else if (id == R.id.action_layout_13pt) layoutEm = 13;
					else if (id == R.id.action_layout_14pt) layoutEm = 14;
					else if (id == R.id.action_layout_15pt) layoutEm = 15;
					else if (id == R.id.action_layout_16pt) layoutEm = 16;
					if (oldLayoutEm != layoutEm)
						relayoutDocument();
					return true;
				}
			});
			popup.show();
			return true;
		} else if (id == R.id.action_outline) {
			Intent intent = new Intent(DocumentActivity.this, OutlineActivity.class);
			Bundle bundle = new Bundle();
			bundle.putInt("POSITION", currentPage);
			bundle.putSerializable("OUTLINE", flatOutline);
			intent.putExtras(bundle);
			startActivityForResult(intent, NAVIGATE_REQUEST);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_PAGE_UP:
		case KeyEvent.KEYCODE_COMMA:
		case KeyEvent.KEYCODE_B:
			goBackward();
			return true;
		case KeyEvent.KEYCODE_PAGE_DOWN:
		case KeyEvent.KEYCODE_PERIOD:
		case KeyEvent.KEYCODE_SPACE:
			goForward();
			return true;
		case KeyEvent.KEYCODE_M:
			history.push(currentPage);
			return true;
		case KeyEvent.KEYCODE_T:
			if (!history.empty()) {
				currentPage = history.pop();
				loadPage();
			}
			return true;
		}
		return super.onKeyUp(keyCode, event);
	}

	public void onPageViewSizeChanged(int w, int h) {
		pageZoom = 1;
		canvasW = w;
		canvasH = h;
		layoutW = canvasW * 72 / displayDPI;
		layoutH = canvasH * 72 / displayDPI;
		if (!hasLoaded) {
			hasLoaded = true;
			openDocument();
		} else if (isReflowable) {
			relayoutDocument();
		} else {
			loadPage();
		}
	}

	public void onPageViewZoomChanged(float zoom) {
		if (zoom != pageZoom) {
			pageZoom = zoom;
			loadPage();
		}
	}

	protected void openDocument() {
		worker.add(new Worker.Task() {
			boolean needsPassword;
			public void work() {
				Log.i(APP, "open document");
				if (buffer != null)
					doc = Document.openDocument(buffer, mimetype);
				else
					doc = Document.openDocument(stream, mimetype);
				needsPassword = doc.needsPassword();
			}
			public void run() {
				if (needsPassword)
					askPassword(R.string.dlog_password_message);
				else
					loadDocument();
			}
		});
	}

	protected void askPassword(int message) {
		final EditText passwordView = new EditText(this);
		passwordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		passwordView.setTransformationMethod(PasswordTransformationMethod.getInstance());

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.dlog_password_title);
		builder.setMessage(message);
		builder.setView(passwordView);
		builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				checkPassword(passwordView.getText().toString());
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				finish();
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
			public void onCancel(DialogInterface dialog) {
				finish();
			}
		});
		builder.create().show();
	}

	protected void checkPassword(final String password) {
		worker.add(new Worker.Task() {
			boolean passwordOkay;
			public void work() {
				Log.i(APP, "check password");
				passwordOkay = doc.authenticatePassword(password);
			}
			public void run() {
				if (passwordOkay)
					loadDocument();
				else
					askPassword(R.string.dlog_password_retry);
			}
		});
	}

	public void onPause() {
		super.onPause();
		if (prefs != null) {
			SharedPreferences.Editor editor = prefs.edit();
			editor.putFloat("layoutEm", layoutEm);
			editor.putBoolean("fitPage", fitPage);
			editor.putInt(key, currentPage);
			editor.apply();
		}
	}

	public void onBackPressed() {
		if (history.empty()) {
			super.onBackPressed();
			if (returnToLibraryActivity) {
				Intent intent = getPackageManager().getLaunchIntentForPackage(getComponentName().getPackageName());
				startActivity(intent);
			}
		} else {
			currentPage = history.pop();
			loadPage();
		}
	}

	public void onActivityResult(int request, int result, Intent data) {
		if (request == NAVIGATE_REQUEST && result >= RESULT_FIRST_USER)
			gotoPage(result - RESULT_FIRST_USER);
	}

	protected void showKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.showSoftInput(searchView, 0);
	}

	protected void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null)
			imm.hideSoftInputFromWindow(searchView.getWindowToken(), 0);
	}

	protected void resetSearch() {
		stopSearch = true;
		searchHitPage = -1;
		searchNeedle = null;
		pageView.resetHits();
		showSearchNavigationButtons(false);
	}

	private void showSearchNavigationButtons(boolean show) {
		if (searchPreviousMenuItem != null) searchPreviousMenuItem.setVisible(show);
		if (searchNextMenuItem != null) searchNextMenuItem.setVisible(show);
	}

	protected void runSearch(final int startPage, final int direction, final String needle) {
		stopSearch = false;
		worker.add(new Worker.Task() {
			int searchPage = startPage;
			public void work() {
				if (stopSearch || needle != searchNeedle)
					return;
				for (int i = 0; i < 9; ++i) {
					Log.i(APP, "search page " + searchPage);
					Page page = doc.loadPage(searchPage);
					Quad[][] hits = page.search(needle);
					page.destroy();
					if (hits != null && hits.length > 0) {
						newSearchHitPage = true;
						searchHitPage = searchPage;
						break;
					}
					searchPage += direction;
					if (searchPage < 0 || searchPage >= pageCount)
						break;
				}
			}
			public void run() {
				if (stopSearch || needle != searchNeedle) {
					showPageNumber(currentPage + 1);
				} else if (searchHitPage == currentPage) {
					loadPage();
				} else if (searchHitPage >= 0) {
					history.push(currentPage);
					currentPage = searchHitPage;
					loadPage();
				} else {
					if (searchPage >= 0 && searchPage < pageCount) {
						showPageNumber(searchPage + 1);
						worker.add(this);
					} else {
						showPageNumber(currentPage + 1);
						Log.i(APP, "search not found");
						Toast.makeText(DocumentActivity.this, getString(R.string.toast_search_not_found), Toast.LENGTH_SHORT).show();
					}
				}
			}
		});
	}

	protected void search(int direction) {
		int startPage;
		if (searchHitPage == currentPage)
			startPage = currentPage + direction;
		else
			startPage = currentPage;
		searchHitPage = -1;
		searchNeedle = searchView.getQuery().toString();
		if (searchNeedle.length() == 0)
			searchNeedle = null;
		if (searchNeedle != null)
			if (startPage >= 0 && startPage < pageCount)
				runSearch(startPage, direction, searchNeedle);
	}

	protected void loadDocument() {
		worker.add(new Worker.Task() {
			public void work() {
				try {
					Log.i(APP, "load document");
					String metaTitle = doc.getMetaData(Document.META_INFO_TITLE);
					if (metaTitle != null && !metaTitle.equals(""))
						title = metaTitle;
					isReflowable = doc.isReflowable();
					if (isReflowable) {
						Log.i(APP, "layout document");
						doc.layout(layoutW, layoutH, layoutEm);
					}
					pageCount = doc.countPages();
					pageCountChanged = true;
				} catch (Throwable x) {
					doc = null;
					pageCount = 1;
					pageCountChanged = true;
					currentPage = 0;
					throw x;
				}
			}
			public void run() {
				pageCountChanged = true;
				if (currentPage < 0 || currentPage >= pageCount)
					currentPage = 0;
				actionBar.setTitle(title);
				if (actionBarMenu != null) {
					actionBarMenu.findItem(R.id.action_layout).setVisible(isReflowable);
					actionBarMenu.findItem(R.id.action_zoom).setVisible(!isReflowable);
				}
				loadPage();
				loadOutline();
			}
		});
	}

	protected void relayoutDocument() {
		worker.add(new Worker.Task() {
			public void work() {
				try {
					long mark = doc.makeBookmark(doc.locationFromPageNumber(currentPage));
					Log.i(APP, "relayout document");
					doc.layout(layoutW, layoutH, layoutEm);
					pageCount = doc.countPages();
					currentPage = doc.pageNumberFromLocation(doc.findBookmark(mark));
				} catch (Throwable x) {
					pageCount = 1;
					currentPage = 0;
					throw x;
				}
			}
			public void run() {
				history.clear();
				pageCountChanged = true;
				loadPage();
				loadOutline();
			}
		});
	}

	private void loadOutline() {
		worker.add(new Worker.Task() {
			boolean outlineTruncated = false;
			private void flattenOutline(Outline[] outline, String indent, int depth) {
				for (Outline node : outline) {
					if (node.title != null)
					{
						int outlinePage = doc.pageNumberFromLocation(doc.resolveLink(node));
						if (flatOutline.size() >= MAXIMUM_OUTLINE_ITEMS)
							outlineTruncated = true;
						else
							flatOutline.add(new OutlineActivity.Item(indent + node.title, node.uri, outlinePage));
					}
					if (node.down != null)
					{
						if (depth >= MAXIMUM_OUTLINE_DEPTH || flatOutline.size() >= MAXIMUM_OUTLINE_ITEMS)
							outlineTruncated = true;
						else
							flattenOutline(node.down, indent + "    ", depth + 1);
					}
				}
			}
			public void work() {
				Log.i(APP, "load outline");
				Outline[] outline = doc.loadOutline();
				if (outline != null) {
					flatOutline = new ArrayList<OutlineActivity.Item>();
					flattenOutline(outline, "", 0);
				} else {
					flatOutline = null;
				}
			}
			public void run() {
				if (flatOutline != null && actionBarMenu != null)
					actionBarMenu.findItem(R.id.action_outline).setVisible(true);
				if (outlineTruncated)
					Toast.makeText(DocumentActivity.this, getString(R.string.toast_outline_too_large), Toast.LENGTH_SHORT).show();
			}
		});
	}

	protected void loadPage() {
		final int pageNumber = currentPage;
		final float zoom = pageZoom;
		stopSearch = true;
		worker.add(new Worker.Task() {
			public Bitmap bitmap;
			public Rect[] linkBounds;
			public String[] linkURIs;
			public Quad[][] hits;
			public void work() {
				try {
					Log.i(APP, "load page " + pageNumber);
					Page page = doc.loadPage(pageNumber);
					Log.i(APP, "draw page " + pageNumber + " zoom=" + zoom);
					Matrix ctm;
					if (fitPage)
						ctm = AndroidDrawDevice.fitPage(page, canvasW, canvasH);
					else
						ctm = AndroidDrawDevice.fitPageWidth(page, canvasW);
					Link[] links = page.getLinks();
					if (links == null)
					{
						linkBounds = new Rect[0];
						linkURIs = new String[0];
					}
					else
					{
						linkBounds = new Rect[links.length];
						linkURIs = new String[links.length];
						for (int i = 0; i < links.length; i++)
						{
							linkBounds[i] = links[i].getBounds().transform(ctm);
							linkURIs[i] = links[i].getURI();
						}
					}
					if (searchNeedle != null) {
						hits = page.search(searchNeedle);
						if (hits != null)
							for (Quad[] hit : hits)
								for (Quad chr : hit)
									chr.transform(ctm);
					}
					if (zoom != 1)
						ctm.scale(zoom);
					bitmap = AndroidDrawDevice.drawPage(page, ctm);
				} catch (Throwable x) {
					Log.e(APP, x.getMessage());
				}
			}
			public void run() {
				if (bitmap != null)
					pageView.setBitmap(bitmap, zoom, wentBack, toggledUI, newSearchHitPage, linkBounds, linkURIs, hits);
				else
					pageView.setError();
				showPageNumber(currentPage + 1);
				pageSeekbar.setMax(pageCount - 1);
				pageSeekbar.setProgress(pageNumber);
				wentBack = false;
				toggledUI = false;
				newSearchHitPage = false;
			}
		});
	}

	protected void showSearch() {
		searchMenuItem.expandActionView();
		searchView.setQuery("", false);
		searchView.requestFocus();
		// Show SearchView's built-in navigation buttons
		searchView.setIconified(false);
	}

	protected void hideSearch() {
		searchMenuItem.collapseActionView();
		searchView.setQuery("", false);
		resetSearch();
		showSearchNavigationButtons(false);
	}

	protected void shareDocument() {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType(mimetype);
		Uri uri = getIntent().getData();
		ClipData.Item item = new ClipData.Item(uri);
		ClipData clip = new ClipData(title != null ? title : "", new String[]{mimetype}, item);
		shareIntent.setClipData(clip);
		shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, title);
		shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		
		try {
			startActivity(Intent.createChooser(shareIntent, getString(R.string.share_document)));
		} catch (android.content.ActivityNotFoundException e) {
			Toast.makeText(this, getString(R.string.toast_no_apps_to_share), Toast.LENGTH_SHORT).show();
		}
	}

	public void toggleUI() {
		toggledUI = true;
		if (bottomBar.getVisibility() == View.VISIBLE) {
			actionBar.hide();
			bottomBar.setVisibility(View.GONE);
			// Collapse SearchView if expanded
			if (searchMenuItem != null && searchMenuItem.isActionViewExpanded()) {
				searchMenuItem.collapseActionView();
			}
		} else {
			actionBar.show();
			bottomBar.setVisibility(View.VISIBLE);
			showPageNumber(currentPage + 1);
		}
	}

	public void goBackward() {
		if (currentPage > 0) {
			wentBack = true;
			currentPage --;
			loadPage();
		}
	}

	public void goForward() {
		if (currentPage < pageCount - 1) {
			currentPage ++;
			loadPage();
		}
	}

	public void gotoPage(int p) {
		if (p >= 0 && p < pageCount && p != currentPage) {
			history.push(currentPage);
			currentPage = p;
			loadPage();
		}
	}

	public void gotoPage(String uri) {
		gotoPage(doc.pageNumberFromLocation(doc.resolveLink(uri)));
	}

	public void gotoURI(String uri) {
		Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET); // FLAG_ACTIVITY_NEW_DOCUMENT in API>=21
		try {
			startActivity(intent);
		} catch (FileUriExposedException x) {
			Log.e(APP, x.toString());
			Toast.makeText(DocumentActivity.this, getString(R.string.toast_file_uris_not_allowed) + uri, Toast.LENGTH_LONG).show();
		} catch (Throwable x) {
			Log.e(APP, x.getMessage());
			Toast.makeText(DocumentActivity.this, x.getMessage(), Toast.LENGTH_SHORT).show();
		}
	}
}
