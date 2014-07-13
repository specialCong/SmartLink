/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.harlan.wifichat.activity;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Toast;

import com.harlan.wifichat.R;
import com.harlan.wifichat.WifiChatApplication;
import com.harlan.wifichat.fragment.DeviceDetailFragment;
import com.harlan.wifichat.fragment.DeviceListFragment;
import com.harlan.wifichat.fragment.DeviceListFragment.DeviceActionListener;
import com.harlan.wifichat.handler.Client;
import com.harlan.wifichat.service.ConnectionService;
import com.harlan.wifichat.util.AnalyticsUtils;
import com.harlan.wifichat.util.Constants;
import com.harlan.wifichat.util.LogTrace;
import com.harlan.wifichat.util.SingleToast;

/**
 * An activity that uses WiFi Direct APIs to discover and connect with available
 * devices. WiFi Direct APIs are asynchronous and rely on callback mechanism
 * using interfaces to notify the application of operation success or failure.
 * The application should also register a BroadcastReceiver for notification of
 * WiFi state related events.
 */
public class WiFiDirectActivity extends Activity implements DeviceActionListener {

    public static final String TAG = WiFiDirectActivity.class.getSimpleName();
    
    WifiChatApplication mApp = null;

    public boolean mHasFocus = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifidirect);   // statically draw two <fragment class=>

        mApp = (WifiChatApplication)getApplication();
        
        mApp.mHomeActivity = this;
        
        // If service not started yet, start it.
        Intent serviceIntent = new Intent(this, ConnectionService.class);
        startService(serviceIntent);  // start the connection service
        getOverflowMenu();
        LogTrace.d(TAG, "onCreate "," home activity launched, start service anyway.");
    }
    
    protected void getOverflowMenu() {         
        try {
           ViewConfiguration config = ViewConfiguration.get(this);
           Field menuKeyField = ViewConfiguration.class.getDeclaredField("sHasPermanentMenuKey");
           if(menuKeyField != null) {
               menuKeyField.setAccessible(true);
               menuKeyField.setBoolean(config, false);
           }
       } catch (Exception e) {
           e.printStackTrace();
       }
    }

    /** 
     *
     */
    @Override
    public void onResume() {
        super.onResume();
        mHasFocus = true;
        if( mApp.mThisDevice != null ){
        	LogTrace.d(TAG, "onResume "," redraw this device details");
        	updateThisDevice(mApp.mThisDevice);
        	// if p2p connetion info available, and my status is connected, enabled start chatting !
            if( mApp.mP2pInfo != null && mApp.mThisDevice.status == WifiP2pDevice.CONNECTED){
            	LogTrace.d(TAG, "onResume"," redraw detail fragment");
            	onConnectionInfoAvailable(mApp.mP2pInfo);
            } else {
            	//stop client, if any.
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }
    
	@Override
	public void onStop() {  // the activity is no long visible
		super.onStop();
		mHasFocus = false;
	}

    @Override
    public void onDestroy() {
    	super.onDestroy();
    	mApp.mHomeActivity = null;
    	LogTrace.d(TAG, "onDestroy"," reset app home activity.");
    }

    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
    	runOnUiThread(new Runnable() {
    		@Override public void run() {
    			DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
    			DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
    			if (fragmentList != null) {
    				fragmentList.clearPeers();
    			}
    			if (fragmentDetails != null) {
    				fragmentDetails.resetViews();
    			}
    		}
    	});
    }
    
    /**
     * process WIFI_P2P_THIS_DEVICE_CHANGED_ACTION intent, refresh this device.
     */
    public void updateThisDevice(final WifiP2pDevice device){
    	runOnUiThread(new Runnable() {
    		@Override public void run() {
    			DeviceListFragment fragment = (DeviceListFragment)getFragmentManager().findFragmentById(R.id.frag_list);
    			fragment.updateThisDevice(device);
    		}
    	});
    }
    
    /**
     * update the device list fragment.
     */
    public void onPeersAvailable(final WifiP2pDeviceList peerList){
    	runOnUiThread(new Runnable() {
    		@Override public void run() {
    			DeviceListFragment fragmentList = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
    	    	fragmentList.onPeersAvailable(mApp.mPeers);  // use application cached list.
    	    	DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
    	    	
    	    	for(WifiP2pDevice d : peerList.getDeviceList()){
    	    		if( d.status == WifiP2pDevice.FAILED ){
    	    			LogTrace.d(TAG, "onPeersAvailable"," Peer status is failed " + d.deviceName );
    	    	    	fragmentDetails.resetViews();
    	    		}
    	    	}
    		}
    	});
    }
    
    /**
     * handle p2p connection available, update UI.
     */
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
    	runOnUiThread(new Runnable() {
    		@Override public void run() {
    			DeviceDetailFragment fragmentDetails = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
    			fragmentDetails.onConnectionInfoAvailable(info);
    		}
    	});
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.action_items, menu);
        return true;
    }

    /**
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        	case android.R.id.home:   // using app icon for navigation up or home:
        		Log.d(TAG, " navigating up or home clicked.");
        		// startActivity(new Intent(home.class, Intent.FLAG_ACTIVITY_CLEAR_TOP));
        		return true;
        		
            case R.id.atn_direct_enable:
//            	if( !mApp.isP2pEnabled() ){
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.
            		AnalyticsUtils.getInstance(mApp).trackEvent(Constants.CAT_LOCATION,Constants.ACT_CREATE, Constants.LAB_HOME, 1);
                    startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
//                } else {
//                    SingleToast.showToast(mApp, "WiFi direct already enabled", 2000);
//                }
                return true;
            case R.id.atn_direct_discover:
                if( !mApp.isP2pEnabled() ){
                	
                	SingleToast.showToast(mApp, R.string.p2p_off_warning, 2000);
                    return true;
                }

                // show progressbar when discoverying.
                final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
                fragment.onInitiateDiscovery();  
                
                LogTrace.d(TAG, "onOptionsItemSelected"," start discoverying ");
                AnalyticsUtils.getInstance(mApp).trackEvent(Constants.CAT_LOCATION, Constants.ACT_CREATE, Constants.LAB_HOME, 2);
                mApp.mP2pMan.discoverPeers(mApp.mP2pChannel, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        SingleToast.showToast(mApp, "Discovery Initiated", 2000);
                        LogTrace.d(TAG, "onSuccess","onOptionsItemSelected : discovery succeed... " );
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                    	LogTrace.d(TAG,"onFailure", "onOptionsItemSelected : discovery failed !!! " + reasonCode);
                    	fragment.clearPeers();
                        Toast.makeText(mApp, "Discovery Failed, try again...", Toast.LENGTH_SHORT).show();
                    }
                });
                return true;
                
            case R.id.disconnect:
            	LogTrace.d(TAG, "onOptionsItemSelected"," disconnect all connections and stop server ");
            	ConnectionService.getInstance().mConnMan.closeClient();
            	ConnectionService.getInstance().mConnMan.closeServer();
            	SingleToast.showToast(mApp, "alread disconnected", 2000);
            	return true;
            	
            case R.id.transferfile:
            	if(mApp.mIsServer){
            		//服务端文件夹
            	 	LogTrace.d(TAG, "onOptionsItemSelected","transferfile ");
                	Intent browseFolder= new Intent(this,BrowserFolderActivity.class);
                	startActivity(browseFolder);
            	}else{
            		//客户端接收服务端文件至指定文件夹
            		Client client = new Client(Constants.ServerHostAddress, 8922, mApp);
            		client.executeOnExecutor(Executors.newCachedThreadPool(),"/sdcard/smartlink/");
            	}
            	return true;
            
            case R.id.quit:
            	LogTrace.d(TAG, "onOptionsItemSelected","quit ");
            	ConnectionService.getInstance().mConnMan.closeClient();
            	ConnectionService.getInstance().mConnMan.closeServer();
            	WiFiDirectActivity.this.finish();
				android.os.Process.killProcess(android.os.Process.myPid());// 关闭进程
            	return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * user taps on peer from discovered list of peers, show this peer's detail.
     */
    public void showDetails(WifiP2pDevice device) {
        DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        fragment.showDetails(device);
    }

    /**
     * user clicked connect button after discover peers.
     */
    public void connect(WifiP2pConfig config) {
    	LogTrace.d(TAG, "connect"," connect to server : " + config.deviceAddress);
    	// perform p2p connect upon users click the connect button. after connection, manager request connection info.
        mApp.mP2pMan.connect(mApp.mP2pChannel, config, new ActionListener() {
            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
            	SingleToast.showToast(mApp, "Connect success..", 2000);
            }

            @Override
            public void onFailure(int reason) {
            	SingleToast.showToast(mApp, "Connect failed. Retry", 2000);
            }
        });
    }

    /**
     * user clicked disconnect button, disconnect from group owner.
     */
    public void disconnect() {
        final DeviceDetailFragment fragment = (DeviceDetailFragment) getFragmentManager().findFragmentById(R.id.frag_detail);
        fragment.resetViews();
        LogTrace.d(TAG, "disconnect"," removeGroup " );
        mApp.mP2pMan.removeGroup(mApp.mP2pChannel, new ActionListener() {
            @Override
            public void onFailure(int reasonCode) {
                LogTrace.d(TAG, "Disconnect failed"," Reason : 1=error, 2=busy; " + reasonCode);
                SingleToast.showToast(mApp, "disconnect failed.." + reasonCode, 2000);
            }

            @Override
            public void onSuccess() {
            	LogTrace.d(TAG, "onSuccess","Disconnect succeed. ");
                fragment.getView().setVisibility(View.GONE);
            }
        });
    }

    /**
     * The channel to the framework(WiFi direct) has been disconnected.
     * This is diff than the p2p connection to group owner.
     */
    public void onChannelDisconnected() {
    	SingleToast.showToast(mApp," Severe! Channel is probably lost premanently. Try Disable/Re-Enable P2P.", 2000);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("WiFi Direct down, please re-enable WiFi Direct")
	       .setCancelable(true)
	       .setPositiveButton("Re-enable WiFi Direct", new DialogInterface.OnClickListener() {
	           public void onClick(DialogInterface dialog, int id) {
	        	   startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
	           }
	       })
	       .setNegativeButton("Cancle", new DialogInterface.OnClickListener() {
	           public void onClick(DialogInterface dialog, int id) {
	                finish();
	           }
	       });
	
        AlertDialog info = builder.create();
        info.show();
    }

    @Override
    public void cancelDisconnect() {
        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (mApp.mP2pMan != null) {
            final DeviceListFragment fragment = (DeviceListFragment) getFragmentManager().findFragmentById(R.id.frag_list);
            if (fragment.getDevice() == null || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE || fragment.getDevice().status == WifiP2pDevice.INVITED) {
                mApp.mP2pMan.cancelConnect(mApp.mP2pChannel, new ActionListener() {
                    @Override
                    public void onSuccess() {
                    	SingleToast.showToast(mApp," Aborting connection", 2000);
                        LogTrace.d(TAG, "cancelConnect"," success canceled...");
                    }
                    @Override
                    public void onFailure(int reasonCode) {
                    	SingleToast.showToast(mApp," cancelConnect: request failed. Please try again.. ", 2000);
                        LogTrace.d(TAG, "cancelConnect"," cancel connect request failed..." + reasonCode);
                    }
                });
            }
        }
    }
    
    /**
     * launch chat activity
     */
    public void startChatActivity(final String initMsg) {
    	if( ! mApp.mP2pConnected ){
    		Log.d(TAG, "startChatActivity : p2p connection is missing, do nothng...");
    		return;
    	}
    	
    	LogTrace.d(TAG, "startChatActivity"," start chat activity fragment..." + initMsg);
    	runOnUiThread(new Runnable() {
    		@Override public void run() {
    			Intent i = mApp.getLauchActivityIntent(ChatActivity.class, initMsg);
    	    	startActivity(i);
    		}
    	});
    }
}
