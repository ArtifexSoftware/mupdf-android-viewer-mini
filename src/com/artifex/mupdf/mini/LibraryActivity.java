package com.artifex.mupdf.mini;

import android.app.Activity;
import android.app.ListActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.io.File;
import java.io.FileFilter;
import java.util.Comparator;
import java.util.Timer;
import java.util.TimerTask;

public class LibraryActivity extends ListActivity
{
	protected final int UPDATE_DELAY = 5000;

	protected File topDirectory, currentDirectory;
	protected ArrayAdapter<LibraryItem> adapter;
	protected Timer updateTimer;

	protected static class LibraryItem {
		public File file;
		public String string;
		public LibraryItem(File file) {
			this.file = file;
			if (file.isDirectory())
				string = file.getName() + "/";
			else
				string = file.getName();
		}
		public LibraryItem(File file, String string) {
			this.file = file;
			this.string = string;
		}
		public String toString() {
			return string;
		}
	}

	protected boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))
			return true;
		return false;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		topDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		currentDirectory = topDirectory;

		adapter = new ArrayAdapter<LibraryItem>(this, android.R.layout.simple_list_item_1);
		setListAdapter(adapter);
	}

	@Override
	public void onResume() {
		super.onResume();
		TimerTask updateTask = new TimerTask() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						updateFileList();
					}
				});
			}
		};
		updateTimer = new Timer();
		updateTimer.scheduleAtFixedRate(updateTask, 0, UPDATE_DELAY);
	}

	@Override
	public void onPause() {
		super.onPause();
		updateTimer.cancel();
		updateTimer = null;
	}

	protected void updateFileList() {
		adapter.clear();

		if (!isExternalStorageReadable()) {
			setTitle("MuPDF");
			adapter.add(new LibraryItem(topDirectory, "[no external storage]"));
			return;
		}

		if (!currentDirectory.isDirectory()) {
			setTitle("MuPDF");
			adapter.add(new LibraryItem(topDirectory, "[not a directory]"));
			return;
		}

		String curPath = currentDirectory.getAbsolutePath();
		String topPath = topDirectory.getParentFile().getAbsolutePath();
		if (curPath.startsWith(topPath))
			curPath = curPath.substring(topPath.length() + 1); /* +1 for trailing slash */
		setTitle(curPath + "/");

		File parent = currentDirectory.getParentFile();
		if (parent != null && !currentDirectory.equals(topDirectory))
			adapter.add(new LibraryItem(parent, "../"));

		File[] files = currentDirectory.listFiles(new FileFilter() {
			public boolean accept(File file) {
				if (file.isDirectory()) return true;
				String suffix = file.getName().toLowerCase();
				if (suffix.endsWith(".pdf")) return true;
				if (suffix.endsWith(".xps")) return true;
				if (suffix.endsWith(".cbz")) return true;
				if (suffix.endsWith(".epub")) return true;
				if (suffix.endsWith(".fb2")) return true;
				return false;
			}
		});

		if (files == null)
			adapter.add(new LibraryItem(topDirectory, "[permission denied]"));
		else
			for (File file : files)
				adapter.add(new LibraryItem(file));

		adapter.sort(new Comparator<LibraryItem>() {
			public int compare(LibraryItem a, LibraryItem b) {
				boolean ad = a.file.isDirectory();
				boolean bd = b.file.isDirectory();
				if (ad && !bd) return -1;
				if (bd && !ad) return 1;
				if (a.string.equals("../")) return -1;
				return a.string.compareTo(b.string);
			}
		});
	}

	protected void onListItemClick(ListView l, View v, int position, long id) {
		LibraryItem item = adapter.getItem(position);

		if (item.file.isDirectory()) {
			currentDirectory = item.file;
			updateFileList();
			return;
		}

		Intent intent = new Intent(); // this, DocumentActivity.class);
		intent.setAction(Intent.ACTION_VIEW);
		intent.setData(Uri.fromFile(item.file));
		try {
			startActivity(intent);
		} catch (Exception e) {
			Log.e("MuPDF", e.toString());
		}
	}
}
