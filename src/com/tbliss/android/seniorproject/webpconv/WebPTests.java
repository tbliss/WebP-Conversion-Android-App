package com.tbliss.android.seniorproject.webpconv;

import java.io.File;
import java.util.Calendar;

import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

public class WebPTests{
	public static final String TAG = "WebPTests";
	private static final String BASE_DIR_NAME = WebPConv.BASE_DIR_NAME + File.separator + "Tests" + File.separator + WebPConv.getDateString();
	private static float[] QUALITY_FACTORS= { 20, 50, 80, 100 };
	
	/**
	 * Given a Bitmap image will convert to WebP format using four different quality factors: 20,
	 * 50, 80, 100.  Time will be recorded and results printed to log file. 
	 * 
	 * @param img Bitmap image to convert to WebP format
	 */
	public static void webPConvTest(Bitmap bitmap){
		Log.v(WebPTests.TAG, "Starting WebP Conversion Tests");
		
		// Make sure directory to store pictures exists
		File baseDir = new File(Environment.getExternalStorageDirectory(), WebPTests.BASE_DIR_NAME);
		if(!baseDir.exists()){
			baseDir.mkdir();
		}
		
		int bitmapHeight = bitmap.getHeight();
		int bitmapWidth = bitmap.getWidth();
		Log.v(WebPTests.TAG, "Width: " + bitmapWidth + "  Height: " + bitmapHeight + " config.method: 1");
		
		for (int i = 0; i < QUALITY_FACTORS.length; i++) {
		   Log.v(WebPTests.TAG, "Quality factor: " + QUALITY_FACTORS[i]);
			String filename = getFilename();
			
			// Convert to WebP
			long startTimeConv = Calendar.getInstance().getTimeInMillis(); 
			int convRet = WebPConv.doConvJniGraphics2(bitmap, QUALITY_FACTORS[i], filename);
			//Log.v(WebPTests.TAG, "convRet: " + convRet);
			long endTimeConv = Calendar.getInstance().getTimeInMillis();
			long timeDiffConv = endTimeConv - startTimeConv;
			Log.v(WebPTests.TAG, "Time to convert: " + timeDiffConv);
			
			// Submit to server
			long startTimeConn = Calendar.getInstance().getTimeInMillis();
			ClientActivity client = new ClientActivity();
         client.connect();
         client.sendCheck(filename);
         String micr = client.getMICR();
         client.disconnect();
         long endTimeConn = Calendar.getInstance().getTimeInMillis();
         long timeDiffConn = endTimeConn - startTimeConn;
         Log.v(WebPTests.TAG, "Time for server transfer: " + timeDiffConn);
         Log.v(WebPTests.TAG, "MICR: " + micr);
         Log.v(WebPTests.TAG, "Total time: " + (timeDiffConv + timeDiffConn));
		}
		
		Log.v(WebPTests.TAG, "Tests complete");
		return;
	}
	
	protected static String getFilename(){
		return Environment.getExternalStorageDirectory() + File.separator + WebPTests.BASE_DIR_NAME 
				+ File.separator + WebPConv.getDateString() + ".webp";
	}
}