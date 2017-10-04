package com.artifex.mupdf.mini;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.LinkedBlockingQueue;

public class Worker implements Runnable
{
	public static class Task implements Runnable {
		public void work() {} /* The 'work' method will be executed on the background thread. */
		public void run() {} /* The 'run' method will be executed on the UI thread. */
	}

	protected Activity activity;
	protected LinkedBlockingQueue<Task> queue;
	protected boolean alive;

	public Worker(Activity act) {
		activity = act;
		queue = new LinkedBlockingQueue<Task>();
	}

	public void start() {
		alive = true;
		new Thread(this).start();
	}

	public void stop() {
		alive = false;
	}

	public void add(Task task) {
		try {
			queue.put(task);
		} catch (InterruptedException x) {
			Log.e("MuPDF Worker", x.getMessage());
		}
	}

	public void run() {
		while (alive) {
			try {
				Task task = queue.take();
				task.work();
				activity.runOnUiThread(task);
			} catch (final Throwable x) {
				Log.e("MuPDF Worker", x.getMessage());
				activity.runOnUiThread(new Runnable() {
					public void run() {
						Toast.makeText(activity, x.getMessage(), Toast.LENGTH_SHORT).show();
					}
				});
			}
		}
	}
}
