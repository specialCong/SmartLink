package com.harlan.wifichat.handler;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import com.harlan.wifichat.util.LogTrace;
import com.harlan.wifichat.util.SingleToast;

public class Client extends AsyncTask<String, Void, String> {
	private static final String TAG = Client.class.getSimpleName();

	private DataInputStream getMessageStream = null;
	private DataOutputStream dateOutputStream = null;
	private Socket clientSocket = null;
	private int numberOfTry = 0;
	private String serverIP = null;
	private int serverPort = 0;
	private Context mContext;

	private Handler myHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			SingleToast.showToast(mContext, (String) msg.obj, 5000);
			super.handleMessage(msg);
		}
	};

	// constructor. need to specify the ip and port.
	// also need a handler instance to be able to show message on UI thread
	public Client(String serverIP, int serverPort, Context mContext) {
		this.serverIP = serverIP;
		this.serverPort = serverPort;
		this.mContext = mContext;
	}

	private boolean initializeClient(String serverIP, int serverPort) {
		try {
			// create socket and connect to server. get input/ouput streams
			// try several times in case client start first.
			numberOfTry++;
			clientSocket = new Socket(serverIP, serverPort);
			return true;
		} catch (UnknownHostException e) {
			if (numberOfTry < 10)
				initializeClient(serverIP, serverPort);
			else
				showMessage("Client Error: Cannot Connect to the Server after 10 attempts");
			return false;
		} catch (IOException e) {
			showMessage("Client Error: IO Error.");
			e.printStackTrace();
			return false;
		}
	}

	// method to show message on UI thread
	public void showMessage(String str) {
		// str.replace('\n', '\0');
		Message msg = new Message();
		msg.obj = (Object) str;
		myHandler.sendMessage(msg);
		return;
	}

	// method to close the connection
	public boolean closeConnection() {
		try {
			clientSocket.close();
			return true;
		} catch (IOException e) {
			showMessage("Client Error: IO Error.");
			return false;
		}
	}

	// communicate with server in background thread. check files and chunks
	// needed and whether the server has them.
	@Override
	protected String doInBackground(String... fileSavePaths) {
		LogTrace.e(TAG, "doInBackground", "serverIP:" + serverIP);
		LogTrace.e(TAG, "doInBackground", "serverPort:" + serverPort);
		if (!initializeClient(serverIP, serverPort)) {
			showMessage("Client Error.");
			LogTrace.e(TAG, "doInBackground", "Client Error");
			return "getServerFile failed";
		} else {
			LogTrace.e(TAG, "doInBackground", "initializeClient succ");
			try {
				sendMessage();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
		String fileSavePath = getMessage(fileSavePaths[0]);
		closeConnection();
		return fileSavePath;
	}

	@Override
	protected void onPreExecute() {
		LogTrace.e(TAG, "onPreExecute", "onPreExecute");
	}

	@Override
	protected void onPostExecute(final String result) {
		LogTrace.e(TAG, "onPostExecute", "Client: Finished");
		
		showMessage("Client: "+result);
		
	}

	private String getMessage(String fileSavePath) {

		LogTrace.e(TAG, "getMessage", "fileSavePath:" + fileSavePath);

		File filePath = new File(fileSavePath);
		if (!filePath.exists() && !filePath.mkdirs()) {
			LogTrace.e(TAG, "getMessage", "mkdir failed");
		}

		// fileSavePath:本地保存路径，文件名会自动从服务器端继承而来。
		DataInputStream inputStream = null;
		try {
			inputStream = getMessageStream();
		} catch (Exception e) {
			System.out.print("接收消息缓存错误\n");
			e.printStackTrace();
			LogTrace.e(TAG, "getMessage", "DataInputStream failed");
			return "DataInputStream failed";
		}

		try {
			LogTrace.e(TAG, "getMessage", "try start");
			int bufferSize = 8192;
			byte[] buf = new byte[bufferSize];
			int passedlen = 0;
			long len = 0;

			// 本地保存路径
			fileSavePath += inputStream.readUTF();

			LogTrace.e(TAG, "getMessage", "try fileSavePath:" + fileSavePath);

			 DataOutputStream fileOut = new DataOutputStream(
					 new BufferedOutputStream(new BufferedOutputStream(new FileOutputStream(fileSavePath))));
			
			
			len = inputStream.readLong();

			LogTrace.e(TAG, "getMessage", "new filesavepath:" + fileSavePath);

			LogTrace.e(TAG, "getMessage", "文件的长度为:" + len + "\n");

			LogTrace.e(TAG, "getMessage", "开始接收文件!" + "\n");

			while (true) {
				LogTrace.e(TAG, "getMessage", "start getMesg");
				int read = 0;
				if (inputStream != null) {
					read = inputStream.read(buf);
				}
				passedlen += read;
				if (read == -1) {
					break;
				}
				// 下面进度条本为图形界面的prograssBar做的，这里如果是打文件，可能会重复打印出一些相同的百分比
				System.out.println("文件接收了" + (passedlen * 100 / len) + "%\n");

				LogTrace.e(TAG, "getMessage", "文件接收了" + (passedlen * 100 / len)
						+ "%\n");
				fileOut.write(buf, 0, read);
			}
			
			System.out.println("接收完成，文件存为" + fileSavePath + "\n");
			LogTrace.e(TAG, "getMessage", "接收完成，文件存为" + fileSavePath + "\n");

			String fileSave = "File received:" + fileSavePath;
			
			fileOut.close();
			
			return fileSave;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("接收消息错误" + "\n");
			return "";
		}
	}

	public DataInputStream getMessageStream() throws Exception {
		try {
			getMessageStream = new DataInputStream(new BufferedInputStream(
					clientSocket.getInputStream()));
			return getMessageStream;
		} catch (Exception e) {
			e.printStackTrace();
			if (getMessageStream != null)
				getMessageStream.close();
			throw e;
		}
	}

	public void sendMessage() throws Exception {
		try {
			dateOutputStream = new DataOutputStream(clientSocket.getOutputStream());
			dateOutputStream.writeByte(0x3);
			dateOutputStream.flush();
		} catch (Exception e) {
			e.printStackTrace();
			if (dateOutputStream != null)
				dateOutputStream.close();
			throw e;
		} finally {
		}
	}

}