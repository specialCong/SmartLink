package com.harlan.wifichat.handler;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;

import com.harlan.wifichat.util.LogTrace;
import com.harlan.wifichat.util.SingleToast;

public class Server extends AsyncTask<String, Void, String> {
	private static final String TAG = Server.class.getSimpleName();
	
	private ServerSocket serverSocket = null;
	private int serverPort = 8922;
	private Socket clientSocket = null;


	private Context mContext;
	private Handler myHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			SingleToast.showToast(mContext, (String) msg.obj, 2000);
			super.handleMessage(msg);
		}
	};

	// constructor, need a handler to be able to show message on UI thread
	public Server(Context mContext) {
		this.mContext = mContext;
	}

	public Server(int serverPort, Context mContext) {
		this.serverPort = serverPort;
		this.mContext = mContext;
	}

	// initialization method, create server socket, listen accept client and get
	// streams
	private boolean initializeServer(int serverPort) {
		
		if(serverSocket!=null&&serverSocket.getLocalPort()==serverPort){
			return true;
		}
		
		try {
			// create serversocket
//			serverSocket = new ServerSocket(serverPort);
			serverSocket =new  ServerSocket(); 
			serverSocket.setReuseAddress(true); 
			serverSocket.bind(new InetSocketAddress(serverPort)); 
			
			
			LogTrace.d(TAG, "initializeServer", "serverSocket.isBound():"+serverSocket.isBound());
			// showMessage("Server: Socket Created.");
		} catch (IOException e) {
			LogTrace.d(TAG, "initializeServer", "Could not listen on port");
//			showMessage("Server Error: Could not listen on port: " + serverPort);
			return false;
		}
		
		try {
			// showMessage("Server: Waiting for Client.");
			clientSocket = serverSocket.accept();
			showMessage("Server: Client Accepted");
		} catch (IOException e) {
			showMessage("Server Error: Accept failed.");
			return false;
		}
		return true;
	}

	// close connection
	private boolean closeConnection() {
		try {
			serverSocket.close();
//			LogTrace.e(TAG, "closeConnection", "serverSocket.isBound():"+serverSocket.isBound());
//			LogTrace.e(TAG, "closeConnection", "serverSocket.isClosed():"+serverSocket.isClosed());
			serverSocket = null;
			return true;
		} catch (IOException e) {
			showMessage("Server Error: IO Error.");
			return false;
		}
	}

	// use handler to show message on UI Thread
	public void showMessage(String str) {
		Message msg = new Message();
		msg.obj = (Object) str;
		myHandler.sendMessage(msg);
		return;
	}



	// use asynctask to do the client/server. All functions are done in
	// background thread
	@Override
	protected String doInBackground(String... filePath) {
		
		LogTrace.d(TAG, "doInBackground", "serverPort:"+serverPort);
		LogTrace.d(TAG, "doInBackground", "filePath:"+filePath[0]);
		
		// initialize server
		if (!initializeServer(serverPort)) {
			LogTrace.d(TAG, "doInBackground", "Server Error.");
			return null;
		}else{
			LogTrace.d(TAG, "doInBackground", "Server succ.");
		}
		try {
			
			LogTrace.d(TAG, "doInBackground", "Server try.");
			while (true) {
				File fi = new File(filePath[0]);
				
				LogTrace.d(TAG, "doInBackground", "文件长度:" + (int) fi.length());
				
				LogTrace.d(TAG, "doInBackground", "file path:" + (int) fi.length());
				
				clientSocket = serverSocket.accept();
				
//				DataInputStream dis = new DataInputStream(
//						new BufferedInputStream(inFromClient));
				DataInputStream dis = new DataInputStream(
				new BufferedInputStream(clientSocket.getInputStream()));
				
				
				dis.readByte();
				
				DataInputStream fis = new DataInputStream(
						new BufferedInputStream(
								new FileInputStream(filePath[0])));
//				DataOutputStream ps = new DataOutputStream(outToClient);
				DataOutputStream ps = new DataOutputStream(clientSocket.getOutputStream());
				
				// 将文件名及长度传给客户端
				ps.writeUTF(fi.getName());
				
				LogTrace.d(TAG, "doInBackground", "fi.getName():" + fi.getName());
				ps.flush();
				ps.writeLong((long) fi.length());
				LogTrace.d(TAG, "doInBackground", "fi.length():" + fi.length());
				ps.flush();
				
				int passedlen = 0;
				long len = fi.length();
				
				int bufferSize = 8192;
				byte[] buf = new byte[bufferSize];

				while (true) {
					int read = 0;
					if (fis != null) {
						read = fis.read(buf);
					}
					passedlen += read;
					if (read == -1) {
						break;
					}
					
					System.out.println("文件传送了 passedlen:" + passedlen);
					System.out.println("文件传送了" + (passedlen * 100 / len) + "%\n");
					ps.write(buf, 0, read);
				}
				ps.flush();
				// 注意关闭socket链接哦，不然客户端会等待server的数据过来，
				// 直到socket超时，导致数据不完整。
				fis.close();
				clientSocket.close();
				LogTrace.d(TAG, "doInBackground", "文件传输完成");
				System.out.println("文件传输完成");
				closeConnection();
			}
		} catch (Exception e) {
			e.printStackTrace();
			LogTrace.d(TAG, "doInBackground", "Exception");
		}
		return null;
	}

	protected void onPostExecute(String result) {
		LogTrace.d(TAG, "onPostExecute", "Server: Finished.");
		showMessage("Server: File has sended.");
	}
}