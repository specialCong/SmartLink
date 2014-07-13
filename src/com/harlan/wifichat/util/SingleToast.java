package com.harlan.wifichat.util;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

//自定义的Toast类，防止toast一直弹出
public class SingleToast {

	private static Toast mToast;
	private static Handler mHandler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			mToast.setText((String)msg.obj);
			super.handleMessage(msg);
		}
	};
	private static Runnable r = new Runnable() {
		public void run() {
			mToast.cancel();
		}
	};

	public static void showToast(Context mContext, String text, int duration) {

		mHandler.removeCallbacks(r);
		if (mToast != null){
			Message  msg = new Message();
			msg.obj = text;
			mHandler.sendMessage(msg);
		}
		else{
	    mToast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
		}
		mHandler.postDelayed(r, duration);

		mToast.show();
	}

	public static void showToast(Context mContext, int resId, int duration,int gravity) {
		showToast(mContext, mContext.getResources().getString(resId), duration,gravity);
	}
	
	public static void showToast(Context mContext, String text, int duration,int gravity) {

		mHandler.removeCallbacks(r);
		if (mToast != null)
			mToast.setText(text);
		else
	    mToast = Toast.makeText(mContext, text, Toast.LENGTH_LONG);
		mToast.setGravity(gravity, 10, 10);
		mHandler.postDelayed(r, duration);

		mToast.show();
	}

	public static void showToast(Context mContext, int resId, int duration) {
		showToast(mContext, mContext.getResources().getString(resId), duration);
	}

}
