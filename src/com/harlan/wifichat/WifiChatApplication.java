package com.harlan.wifichat;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Application;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.os.Message;
import android.util.Log;

import com.harlan.wifichat.activity.WiFiDirectActivity;
import com.harlan.wifichat.bean.MessageRow;
import com.harlan.wifichat.service.ConnectionService;
import com.harlan.wifichat.util.Constants;
import com.harlan.wifichat.util.JSONUtils;
import com.harlan.wifichat.util.LogTrace;
import com.harlan.wifichat.util.PreferencesUtil;

public class WifiChatApplication extends Application {

	private static final String TAG = WifiChatApplication.class.getSimpleName();
	
	public WifiP2pManager mP2pMan = null;;
	public Channel mP2pChannel = null;
	public boolean mP2pConnected = false;
	public String mMyAddr = null;
	public String mDeviceName = null;   // the p2p name that is configurated from UI.
	
	public WifiP2pDevice mThisDevice = null;
	public WifiP2pInfo mP2pInfo = null;  // set when connection info available, reset when WIFI_P2P_CONNECTION_CHANGED_ACTION
	
	//是否是服务端
	public static boolean mIsServer = false;
	
	public WiFiDirectActivity mHomeActivity = null;
	public List<WifiP2pDevice> mPeers = new ArrayList<WifiP2pDevice>();  // update on every peers available
	public JSONArray mMessageArray = new JSONArray();		// limit to the latest 50 messages
	
	@Override
    public void onCreate() {
        super.onCreate();
    }
	
	/**
	 * whether p2p is enabled in this device. 
	 * my bcast listener always gets enable/disable intent and persist to shared pref
	 */
	public boolean isP2pEnabled() {
		String state = PreferencesUtil.getStringFromPref(this, PreferencesUtil.PREF_NAME, PreferencesUtil.P2P_ENABLED);
		if ( state != null && "1".equals(state.trim())){
			return true;
		}
		return false;
	}
	
	/**
     * upon p2p connection available, group owner start server socket channel
     * start socket server and select monitor the socket
     */
    public void startSocketServer() {
    	Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
    	msg.what = Constants.MSG_STARTSERVER;
    	ConnectionService.getInstance().getHandler().sendMessage(msg);
    }
    
    /**
     * upon p2p connection available, non group owner start socket channel connect to group owner.
     */
    public void startSocketClient(String hostname) {
    	Log.d(TAG, "startSocketClient : client connect to group owner : " + hostname);
    	Message msg = ConnectionService.getInstance().getHandler().obtainMessage();
    	msg.what = Constants.MSG_STARTCLIENT;
    	msg.obj = hostname;
    	ConnectionService.getInstance().getHandler().sendMessage(msg);
    }
    
    /**
     * check whether there exists a connected peer.
     */
    public WifiP2pDevice getConnectedPeer(){
    	WifiP2pDevice peer = null;
		for(WifiP2pDevice d : mPeers ){ 
			
    		LogTrace.d(TAG, "getConnectedPeer"," device : " + d.deviceName + " status: " + ConnectionService.getDeviceStatus(d.status));
    		if( d.status == WifiP2pDevice.CONNECTED){
    			peer = d;
    		}
    	}
    	return peer;
    }
    
    /**
     * insert a json string msg into messages json array
     */
    public void shiftInsertMessage(String jsonmsg){
    	JSONObject jsonobj = JSONUtils.getJsonObject(jsonmsg);
    	mMessageArray.put(jsonobj);
    	mMessageArray = JSONUtils.truncateJSONArray(mMessageArray, 10);  // truncate the oldest 10.
    }
    
    public String shiftInsertMessage(MessageRow row) {
    	JSONObject jsonobj = MessageRow.getAsJSONObject(row);
    	if( jsonobj != null ){
    		mMessageArray.put(jsonobj);
    	}
    	mMessageArray = JSONUtils.truncateJSONArray(mMessageArray, 10);  // truncate the oldest 10.
    	return jsonobj.toString();
    }
    
    public void clearMessages() {
    	mMessageArray = new JSONArray();
    }
    
    /**
     * get the intent to lauch any activity
     */
    public Intent getLauchActivityIntent(Class<?> cls, String initmsg){
    	Intent i = new Intent(this, cls);
    	i.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    	i.putExtra("FIRST_MSG", initmsg);
    	return i;
    }
    
    public void setMyAddr(String addr){
    	mMyAddr = addr;
    }
    
}
