package com.harlan.wifichat.service;

import java.nio.channels.SocketChannel;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.harlan.wifichat.R;
import com.harlan.wifichat.WifiChatApplication;
import com.harlan.wifichat.activity.ChatActivity;
import com.harlan.wifichat.bean.MessageRow;
import com.harlan.wifichat.handler.ConnectionManager;
import com.harlan.wifichat.handler.WorkHandler;
import com.harlan.wifichat.util.Constants;
import com.harlan.wifichat.util.LogTrace;
import com.harlan.wifichat.util.PreferencesUtil;

public class ConnectionService extends Service implements ChannelListener, PeerListListener, ConnectionInfoListener {  // callback of requestPeers{
	
	private static final String TAG = "PTP_Serv";
	
	private static ConnectionService _sinstance = null;

	private  WorkHandler mWorkHandler;
    private  MessageHandler mHandler;
    
    boolean retryChannel = false;
    
    WifiChatApplication mApp;
    ChatActivity mActivity;    // shall I use weak reference here ?
	public ConnectionManager mConnMan;
	
	/**
     * @see android.app.Service#onCreate()
     */
    private void _initialize() {
    	if (_sinstance != null) {
    		LogTrace.d(TAG, "_initialize", " already initialized, do nothing.");
            return;
        }

    	_sinstance = this;
    	mWorkHandler = new WorkHandler(TAG);
        mHandler = new MessageHandler(mWorkHandler.getLooper());
        
        mApp = (WifiChatApplication)getApplication();
        mApp.mP2pMan = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
        
        mConnMan = new ConnectionManager(this);
    }
    
    public static ConnectionService getInstance(){
    	return _sinstance;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        _initialize();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
    	_initialize();
    	processIntent(intent);
    	return START_STICKY;
    }
    
    /**
     * process all wifi p2p intent caught by bcast recver.
     * P2P connection setup event sequence:
     * 1. after find, peers_changed to available, invited
     * 2. when connection established, this device changed to connected.
     * 3. for server, WIFI_P2P_CONNECTION_CHANGED_ACTION intent: p2p connected,
     *    for client, this device changed to connected first, then CONNECTION_CHANGED 
     * 4. WIFI_P2P_PEERS_CHANGED_ACTION: peer changed to connected.
     * 5. now both this device and peer are connected !
     * 
     * if select p2p server mode with create group, this device will be group owner automatically, with 
     * 1. this device changed to connected
     * 2. WIFI_P2P_CONNECTION_CHANGED_ACTION 
     */
    private void processIntent(Intent intent){
    	if( intent == null){
    		return;
    	}
    	
    	String action = intent.getAction();
    	LogTrace.d(TAG, "processIntent", intent.toString());
    	
    	if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {  // this devices's wifi direct enabled state.
              int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
              if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                  // Wifi Direct mode is enabled
            	  mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
            	  PreferencesUtil.setStringToPref(mApp, PreferencesUtil.PREF_NAME, PreferencesUtil.P2P_ENABLED, "1");
            	  LogTrace.d(TAG, "processIntent"," WIFI_P2P_STATE_CHANGED_ACTION : enabled, re-init p2p channel to framework ");
            	  
              } else {
            	  mApp.mThisDevice = null;  	// reset this device status
            	  mApp.mP2pChannel = null;
            	  mApp.mPeers.clear();
            	  LogTrace.d(TAG, "processIntent","WIFI_P2P_STATE_CHANGED_ACTION : disabled, null p2p channel to framework ");
            	  if( mApp.mHomeActivity != null ){
            		  mApp.mHomeActivity.updateThisDevice(null);
            		  mApp.mHomeActivity.resetData();
            	  }
            	  PreferencesUtil.setStringToPref(mApp, PreferencesUtil.PREF_NAME, PreferencesUtil.P2P_ENABLED, "0");
              }
          } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {  
        	  // a list of peers are available after discovery, use PeerListListener to collect
              // request available peers from the wifi p2p manager. This is an
              // asynchronous call and the calling activity is notified with a
              // callback on PeerListListener.onPeersAvailable()
        	  LogTrace.d(TAG, "processIntent"," WIFI_P2P_PEERS_CHANGED_ACTION: call requestPeers() to get list of peers");
              if (mApp.mP2pMan != null) {
            	  mApp.mP2pMan.requestPeers(mApp.mP2pChannel, (PeerListListener) this);
              }
          } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
              if (mApp.mP2pMan == null) {
                  return;
              }

              NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
              LogTrace.d(TAG, "processIntent"," WIFI_P2P_CONNECTION_CHANGED_ACTION : " + networkInfo.getReason() + " : " + networkInfo.toString());
              if (networkInfo.isConnected()) {
            	  Log.d(TAG, "processIntent: WIFI_P2P_CONNECTION_CHANGED_ACTION: p2p connected ");
                  // Connected with the other device, request connection info for group owner IP. Callback inside details fragment.
                  mApp.mP2pMan.requestConnectionInfo(mApp.mP2pChannel, this);  
              } else {
            	  Log.d(TAG, "processIntent: WIFI_P2P_CONNECTION_CHANGED_ACTION: p2p disconnected, mP2pConnected = false..closeClient.."); // It's a disconnect
            	  mApp.mP2pConnected = false;
            	  mApp.mP2pInfo = null;   // reset connection info after connection done.
            	  mConnMan.closeClient();
            	  
            	  if( mApp.mHomeActivity != null ){
            		  mApp.mHomeActivity.resetData();
            	  }
              }
          } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {  
        	  // this device details has changed(name, connected, etc)
        	  mApp.mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
        	  mApp.mDeviceName = mApp.mThisDevice.deviceName;
        	  LogTrace.d(TAG, "processIntent"," WIFI_P2P_THIS_DEVICE_CHANGED_ACTION " + mApp.mThisDevice.deviceName);
        	  if( mApp.mHomeActivity != null ){
        		  mApp.mHomeActivity.updateThisDevice(mApp.mThisDevice);
        	  }
          }
    }
    
    /**
     * The channel to the framework Wifi P2p has been disconnected. could try re-initializing 
     */
    @Override
    public void onChannelDisconnected() {
    	if( !retryChannel ){
    		LogTrace.d(TAG, "onChannelDisconnected "," retry initialize() ");
    		mApp.mP2pChannel = mApp.mP2pMan.initialize(this, mWorkHandler.getLooper(), null);
    		if( mApp.mHomeActivity != null) {
    			mApp.mHomeActivity.resetData();
    		}
    		retryChannel = true;
    	}else{
    		LogTrace.d(TAG, "onChannelDisconnected"," stop self, ask user to re-enable.");
    		if( mApp.mHomeActivity != null) {
    			mApp.mHomeActivity.onChannelDisconnected();
    		}
    		stopSelf();
    	}
    }
    
    /**
     * the callback of requestPeers upon WIFI_P2P_PEERS_CHANGED_ACTION intent.
     */
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
    	mApp.mPeers.clear();
    	mApp.mPeers.addAll(peerList.getDeviceList());
		LogTrace.d(TAG, "onPeersAvailable","update peer list...");
		
    	WifiP2pDevice connectedPeer = mApp.getConnectedPeer();
    	if( connectedPeer != null ){
    		LogTrace.d(TAG, "onPeersAvailable","exist connected peer : " + connectedPeer.deviceName);
    	} else {
    		
    	}
    	
    	if(mApp.mP2pInfo != null && connectedPeer != null ){
    		if( mApp.mP2pInfo.groupFormed && mApp.mP2pInfo.isGroupOwner ){
    			LogTrace.d(TAG, "onPeersAvailable","device is groupOwner: startSocketServer");
    			mApp.startSocketServer();
    		}else if( mApp.mP2pInfo.groupFormed && connectedPeer != null ){
    			// XXX client path goes to connection info available after connection established.
    			// LogTrace.d(TAG, "onConnectionInfoAvailable: device is client, connect to group owner: startSocketClient ");
    			// mApp.startSocketClient(mApp.mP2pInfo.groupOwnerAddress.getHostAddress());
    		}
    	}
    	
    	if( mApp.mHomeActivity != null){
    		mApp.mHomeActivity.onPeersAvailable(peerList);
    	}
    }
    
    /**
     * the callback of when the _Requested_ connectino info is available. 
     * WIFI_P2P_CONNECTION_CHANGED_ACTION intent, requestConnectionInfo()
     */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
    	Log.d(TAG, "onConnectionInfoAvailable: " + info.groupOwnerAddress.getHostAddress());
        if (info.groupFormed && info.isGroupOwner ) {
			// XXX server path goes to peer connected.
            //new FileServerAsyncTask(getActivity(), mContentView.findViewById(R.id.status_text)).execute();
        	//LogTrace.d(TAG, "onConnectionInfoAvailable: device is groupOwner: startSocketServer ");
        	// mApp.startSocketServer();
        } else if (info.groupFormed) {
        	LogTrace.d(TAG, "onConnectionInfoAvailable","device is client, connect to group owner: startSocketClient ");
        	mApp.startSocketClient(info.groupOwnerAddress.getHostAddress());
        }
        mApp.mP2pConnected = true;
        mApp.mP2pInfo = info;   // connection info available
    }
    
    private void enableStartChatActivity() {
    	if( mApp.mHomeActivity != null ){
			LogTrace.d(TAG, "enableStartChatActivity","nio channel ready, enable start chat !");
			mApp.mHomeActivity.onConnectionInfoAvailable(mApp.mP2pInfo);
    	}
    }
    
	@Override
	public IBinder onBind(Intent arg0) { return null; }
	
	public Handler getHandler() {
        return mHandler;
    }

	/**
     * message handler looper to handle all the msg sent to location manager.
     */
    final class MessageHandler extends Handler {
        public MessageHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
        }
    }
    
    /**
     * the main message process loop.
     */
    private void processMessage(android.os.Message msg) {
    	
        switch (msg.what) {
        case Constants.MSG_NULL:
        	break;
        case Constants.MSG_REGISTER_ACTIVITY:
        	LogTrace.d(TAG, "processMessage: onActivityRegister to chat fragment...","MSG_REGISTER_ACTIVITY");
        	onActivityRegister((ChatActivity)msg.obj, msg.arg1);
        	break;
        case Constants.MSG_STARTSERVER:
        	LogTrace.d(TAG, "processMessage: startServerSelector...","MSG_STARTSERVER");
        	if( mConnMan.startServerSelector() >= 0){
        		enableStartChatActivity();
        	}
        	break;
        case Constants.MSG_STARTCLIENT:
        	LogTrace.d(TAG, "processMessage: startClientSelector...","MSG_STARTCLIENT");
        	if( mConnMan.startClientSelector((String)msg.obj) >= 0){
        		enableStartChatActivity();
        	}
        	break;
        case Constants.MSG_NEW_CLIENT:
        	LogTrace.d(TAG, "processMessage:  onNewClient...","MSG_NEW_CLIENT");
        	mConnMan.onNewClient((SocketChannel)msg.obj);
        	break;
        case Constants.MSG_FINISH_CONNECT:
        	LogTrace.d(TAG, "processMessage:  onFinishConnect...","MSG_FINISH_CONNECT");
        	mConnMan.onFinishConnect((SocketChannel)msg.obj);
        	break;
        case Constants.MSG_PULLIN_DATA:
        	LogTrace.d(TAG, "processMessage:  onPullIndata ...","MSG_PULLIN_DATA");
        	onPullInData((SocketChannel)msg.obj, msg.getData());
        	break;
        case Constants.MSG_PUSHOUT_DATA:
        	LogTrace.d(TAG, "processMessage: onPushOutData...","MSG_PUSHOUT_DATA");
        	onPushOutData((String)msg.obj);
        	break;
        case Constants.MSG_SELECT_ERROR:
        	LogTrace.d(TAG, "processMessage: onSelectorError...","MSG_SELECT_ERROR");
        	mConnMan.onSelectorError();
        	break;
        case Constants.MSG_BROKEN_CONN:
        	LogTrace.d(TAG, "processMessage: onBrokenConn...","MSG_BROKEN_CONN");
        	mConnMan.onBrokenConn((SocketChannel)msg.obj);
        	break;
        default:
        	break;
        }
    }
    
    /**
     * register the activity that uses this service.
     */
    private void onActivityRegister(ChatActivity activity, int register){
    	Log.d(TAG, "onActivityRegister : activity register itself to service : " + register);
    	if( register == 1){
    		mActivity = activity;
    	}else{
    		mActivity = null;    // set to null explicitly to avoid mem leak.
    	}
    }
    
    /**
     * service handle data in come from socket channel
     */
    private String onPullInData(SocketChannel schannel, Bundle b){
    	String data = b.getString("DATA");
    	Log.d(TAG, "onDataIn : recvd msg : " + data);
    	mConnMan.onDataIn(schannel, data);  // pub to all client if this device is server.
    	MessageRow row = MessageRow.parseMessageRow(data);
    	// now first add to app json array
    	mApp.shiftInsertMessage(row);
    	showNotification(row);
    	// add to activity if it is on focus.
    	showInActivity(row);
    	return data;
    }
    
    /**
     * handle data push out request. 
     * If the sender is the server, pub to all client.
     * If the sender is client, only can send to the server.
     */
    private void onPushOutData(String data){
    	Log.d(TAG, "onPushOutData : " + data);
    	mConnMan.pushOutData(data);
    }
    
    /**
     * sync call to send data using conn man's channel, as conn man now is blocking on select
     */
    public int connectionSendData(String jsonstring) {
    	Log.d(TAG, "connectionSendData : " + jsonstring);
    	new SendDataAsyncTask(mConnMan, jsonstring).execute();
    	return 0;
    }
    
    /**
     * write data in an async task to avoid NetworkOnMainThreadException.
     */
    public class SendDataAsyncTask extends AsyncTask<Void, Void, Integer> {
    	private String data;
    	private ConnectionManager connman;
    	
    	public SendDataAsyncTask(ConnectionManager conn, String jsonstring) {
    		connman = conn;
    		data = jsonstring;
    	}
    	
		@Override
		protected Integer doInBackground(Void... params) {
			return connman.pushOutData(data);
		}
		 
		@Override
		protected void onPostExecute(Integer result) {
			Log.d(TAG, "SendDataAsyncTask : onPostExecute:  " + data + " len: " + result);
		}
    }
    
    /**
     * send a notification upon recv data, click the notification will bcast the pending intent, which
     * will launch the chatactivity fragment.
     */
    @SuppressWarnings("deprecation")
	public void showNotification(MessageRow row) {
    	
    	NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		Notification notification = new Notification(R.drawable.ic_action_discover, row.mMsg, System.currentTimeMillis());
    	notification.defaults |= Notification.DEFAULT_VIBRATE;
    	CharSequence title = row.mSender;
    	CharSequence text = row.mMsg;

    	//Intent notificationIntent = new Intent(this, WiFiDirectActivity.class);
    	Intent notificationIntent = mApp.getLauchActivityIntent(ChatActivity.class, row.mMsg);
    	// pendingIntent that will start a new activity.
    	PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT);

    	notification.setLatestEventInfo(this, title, text, contentIntent);
    	notificationManager.notify(1, notification);
    	LogTrace.d(TAG, "showNotification: " , row.mMsg);
    }
    
    /**
     * show the message in activity
     */
    private void showInActivity(final MessageRow row){
    	LogTrace.d(TAG, "showInActivity : " , row.mMsg);
    	if( mActivity != null ){
    		mActivity.showMessage(row);
    	} else {
    		if( mApp.mHomeActivity != null && mApp.mHomeActivity.mHasFocus == true ){
    			LogTrace.d(TAG, "showInActivity ","chat activity down, force start only when home activity has focus !");
    			mApp.mHomeActivity.startChatActivity(row.mMsg);
    		}else{
    			LogTrace.d(TAG, "showInActivity ","  Home activity down, do nothing, notification will launch it...");
    		}
    	}
    }
    
    public static String getDeviceStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
            	//Log.d(TAG, "getDeviceStatus : AVAILABLE");
                return "Available";
            case WifiP2pDevice.INVITED:
            	//Log.d(TAG, "getDeviceStatus : INVITED");
                return "Invited";
            case WifiP2pDevice.CONNECTED:
            	//Log.d(TAG, "getDeviceStatus : CONNECTED");
                return "Connected";
            case WifiP2pDevice.FAILED:
            	//Log.d(TAG, "getDeviceStatus : FAILED");
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
            	//Log.d(TAG, "getDeviceStatus : UNAVAILABLE");
                return "Unavailable";
            default:
                return "Unknown = " + deviceStatus;
        }
    }
 }
