package com.harlan.wifichat.activity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.harlan.wifichat.R;
import com.harlan.wifichat.WifiChatApplication;
import com.harlan.wifichat.handler.Client;
import com.harlan.wifichat.handler.Server;
import com.harlan.wifichat.util.SingleToast;

// A simple Directory Browser and support several types of media playback.
public class BrowserFolderActivity extends ListActivity {

	private List<String> items = null;
	private File lastDirectory = null;
	WifiChatApplication mApp = (WifiChatApplication)getApplication();

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.activity_folder);
		// show the root directory
		getFiles(new File("/sdcard/").listFiles());
		lastDirectory = new File("/sdcard/");
	}
	
	
	//显示选项(打开文件、传输文件)
	private void showMenu(final File file) {
		Resources res = getResources();
		String[] entries = new String[] {
				res.getString(R.string.open_file),
				res.getString(R.string.transfer_file) };
		ListAdapter menuAdapter = new ArrayAdapter<String>(this,
				R.layout.screen_manager_menu, entries);

		new AlertDialog.Builder(this).setTitle(R.string.handle_file)
				.setIcon(R.drawable.ic_launcher)
				.setAdapter(menuAdapter, new OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						if (which == 0) {
							//打开文件
							// if it is a file instead of a directory, execute the file
							// according to the type using proper activity.
							Intent intent = new Intent("android.intent.action.VIEW");
							intent.addCategory("android.intent.category.DEFAULT");
							intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							Uri uri = Uri.fromFile(file);
							switch (getFileType(file.getName())) {
							case 0:
								intent.setDataAndType(uri, "text/plain");
								break;
							case 1:
								intent.setDataAndType(uri, "application/pdf");
								break;
							case 2:
								intent.setDataAndType(uri, "image/*");
								break;
							case 3:
								intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
								intent.putExtra("oneshot", 0);
								intent.putExtra("configchange", 0);
								intent.setDataAndType(uri, "audio/*");
								break;
							case 4:
								intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
								intent.putExtra("oneshot", 0);
								intent.putExtra("configchange", 0);
								intent.setDataAndType(uri, "video/*");
								break;
							default:
								SingleToast
										.showToast(
												BrowserFolderActivity.this,
												"Please View the content of the file with a File Manager.",
												2000);
								intent = null;
							}
							if (intent != null)
								startActivity(intent);
						} else if (which == 1) {
							//传输文件
							if(mApp.mIsServer){
								//服务端将文件上传到socket端口供下载
							    Server server = new Server(8922, mApp);
							    server.executeOnExecutor(Executors.newCachedThreadPool(),file.getPath());
								SingleToast
								.showToast(
										BrowserFolderActivity.this,
										"Server "+file.getPath()+"  ready",
										2000);
							}else{
								//客户端接收服务端文件至指定文件夹(此处不可到达)
//								Client client = new Client(mApp.mMyAddr, 8922, mApp);
//								client.execute("/sdcard/smartlink/");
							}

						}
					}
				}).show();
	}

	// perform according to the click
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		int selectedRow = (int) id;
		// the first row is always the Back function.
		if (selectedRow == 0) {
			if (lastDirectory.getPath().equals("/")) {
				SingleToast.showToast(this,
						"You have reached the root directory.", 2000);
			}
			// show the content of last level of directory.
			getFiles(lastDirectory.listFiles());
			// calculate the new last level directory for next use of "Back"
			String[] buffer = lastDirectory.getPath().split("\\/");
			String fileName = new String();
			for (int i = 0; i < buffer.length - 1; i++) {
				fileName += buffer[i] + "/";
			}
			fileName += "/";
			lastDirectory = new File(fileName);
		} else {
			// get the file name of the row clicked.
			File file = new File(items.get(selectedRow));
			if (file.isDirectory()) {
				// show the content of the directory if it is a directory
				getFiles(file.listFiles());
				// calculate the new last level directory for next use of "Back"
				String[] buffer = file.getPath().split("\\/");
				String fileName = new String();
				for (int i = 0; i < buffer.length - 1; i++) {
					fileName += buffer[i] + "/";
				}
				fileName += "/";
				lastDirectory = new File(fileName);
			} else {
				//如果是文件，进行相关处理
				showMenu(file);
			}
		}
	}

	// decide the file type from the file name
	private int getFileType(String fileName) {
		String[] buffer = fileName.split("\\.");
		if (buffer[buffer.length - 1].equals("txt"))
			return 0;
		else if (buffer[buffer.length - 1].equals("pdf"))
			return 1;
		else if (buffer[buffer.length - 1].equals("jpg")
				|| buffer[buffer.length - 1].equals("png"))
			return 2;
		else if (buffer[buffer.length - 1].equals("mp3"))
			return 3;
		else if (buffer[buffer.length - 1].equals("avi")
				|| buffer[buffer.length - 1].equals("mp4"))
			return 4;
		else
			return -1;
	}

	// get all the files in a directory and use the arrayadapter to show them on
	// the arraylist
	private void getFiles(File[] files) {
		items = new ArrayList<String>();
		items.add("Back To Previous Directory");
		// add every file to the list
		for (File file : files) {
			if (file.getPath().equals("/root"))
				continue;
			items.add(file.getPath());
		}
		ArrayAdapter<String> fileList = new ArrayAdapter<String>(this,
				R.layout.rowfolder, items);
		setListAdapter(fileList);
	}
}