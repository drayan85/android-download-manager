package com.yyxu.download.services;

import java.io.File;
import java.net.MalformedURLException;
import java.util.LinkedList;
import java.util.Queue;

import com.yyxu.download.utils.StorageUtils;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class DownloadManageTask extends AsyncTask<Void, Void, Boolean> {

	public static final String SDCARD_ROOT = Environment
			.getExternalStorageDirectory().getAbsolutePath() + "/";

	private final Context mContext;
	private final Handler mMainThreadHandler;
	private final String mFileRoot;
	private final Queue<String> mTaskQueue;

	private Handler mBackgroundHandler;

	private volatile AsyncTask<Void, Integer, Long> mTaskHolder;
	private DownloadTaskListener mTaskListener;

	// Executed in the UI thread.
	private final Runnable mTaskStarter = new Runnable() {
		@Override
		public void run() {
			String task = mTaskQueue.peek();
			if (task == null) {
				try {
					this.wait();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
			try {
				AsyncTask<Void, Integer, Long> newTask = newDownloadTask(task);
				mTaskHolder = newTask.execute();
			} catch (Exception e) {
				Log.v(null, e.getMessage(), e);
			}

			if (mBackgroundHandler != null) {
				mBackgroundHandler.post(mTaskWorker);
			}
		}
	};

	// Executed in the background.
	private final Runnable mTaskWorker = new Runnable() {
		@Override
		public void run() {
			String task = mTaskQueue.peek();
			try {
				if (mTaskHolder != null && mTaskHolder.get() != null) {
					mTaskQueue.remove();
					mTaskHolder = null;
					// Post processing.
					if (mTaskQueue.size() == 0) {
						// We're done here.
						return;
					} else if (mMainThreadHandler != null) {
						// There's still some work to do.
						mMainThreadHandler.post(mTaskStarter);
						return;
					}
				}
			} catch (Exception e) {
				Log.e(null, "" + e);
			}
			// Something went wrong...
			Log.e(null, "Downloading failed, url: " + task);
			Looper.myLooper().quit();
		}
	};

	public DownloadManageTask(Context context, String dirName,
			DownloadTaskListener listener) {
		mContext = context;
		mMainThreadHandler = new Handler();
		mTaskQueue = new LinkedList<String>();
		mFileRoot = SDCARD_ROOT + dirName;
		mTaskListener = listener;
	}

	public void post(String url) {
		if (!mTaskQueue.contains(url)) {
			mTaskQueue.offer(url);
		}
	}

	public void start() {
		execute();
	}

	public void stop() {
		Looper.myLooper().quit();
	}

	public void pause() {
		if (mTaskHolder != null) {
			DownloadTask task = (DownloadTask) mTaskHolder;
			task.onCancelled();
		}
	}

	@Override
	protected Boolean doInBackground(Void... params) {
		new Thread(new Runnable() {

			@Override
			public void run() {
				executeInBackground();
				final boolean result = (mTaskQueue.size() == 0);
				mMainThreadHandler.post(new Runnable() {
					@Override
					public void run() {
						finish(result);
					}
				});
			};

		}).start();
		return true;
	}

	private boolean executeInBackground() {
		File root = new File(mFileRoot);
		if (root.exists()) {
			StorageUtils.delete(root);
		}

		if (!root.mkdirs()) {
			Log.e(null, "Failed to make directories: " + root.getAbsolutePath());
			return false;
		}

		if (Looper.myLooper() == null) {
			Looper.prepare();
		}

		mBackgroundHandler = new Handler(Looper.myLooper());
		mMainThreadHandler.post(mTaskStarter);
		Looper.loop();
		// Have we executed all the tasks?
		return (mTaskQueue.size() == 0);
	}

	private void finish(boolean result) {
		if (result) {
			// TODO listener
		} else {
			if (mTaskHolder != null) {
				mTaskHolder.cancel(true);
			}
			// TODO listener
		}
	}

	private AsyncTask<Void, Integer, Long> newDownloadTask(String in)
			throws MalformedURLException {
		String out = mFileRoot;
		return new DownloadTask(mContext, in, out, mTaskListener);
	}

}
