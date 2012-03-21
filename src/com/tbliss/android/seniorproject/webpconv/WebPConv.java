/*
 * Copyright (C) 2012 Trevor Bliss
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

package com.tbliss.android.seniorproject.webpconv;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.lang.String;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

public class WebPConv extends Activity {
   public static String TAG = "WebPConv";
   private boolean testFlag = false;
   public static final String BASE_DIR_NAME = "WebPConv";
   public static final String TEST_FILE_NAME_JPEG = "jpeg_testimage_2.jpg";  //flash1_1600.jpg
   public static final String TEST_FILE_NAME_TIFF = "RECORD_1777.tif"; //RECORD_1771.tif
   public static final String TEST_FILE_NAME_WEBP = "checkfront888891210.webp"; //checkfront888891215.webp
   private String TIFF_IMAGE_PATH;
   private String JPEG_IMAGE_PATH;
   private String WEBP_IMAGE_PATH;
	
   // View objects
   private ImageView m_imageView1;
   private Button m_takePictureCustom;
   private Button m_takePictureNative;
   private Button m_convert;
   private Button m_retake;
   private Button m_connectServer;
   private Button m_runTests;
   private ImageView m_imageView_micr;
   private Button m_home;
   private Button m_retake_micr;
   protected TextView m_micr_text;  // protected so MICR code can be displayed from server thread

   // Bitmap image variables
   protected Bitmap m_imageBitmap;
   protected byte[] m_imageByteArray;
   protected static float IMG_QUALITY_FACTOR = 80;
    
   // Camera variables
   private static final String CAMERA_CUSTOM = "CAMERA_CUSTOM";
   private static final String CAMERA_NATIVE = "CAMERA_NATIVE";
   protected static int CAMERA_CUSTOM_RC = 0;
   protected static int CAMERA_NATIVE_RC = 1;
   private String m_cameraChoice;
   private Uri m_imageUri;
    
   // Client Activity for communicating with server
   private ClientActivity m_client;
   
   // Screens
   private static final int HOME_SCREEN = 0;
   private static final int PICTURE_SCREEN = 1;
   private static final int MICR_SCREEN = 2;
   private int m_currScreen = HOME_SCREEN;
   
    /**
     * Load WebP library for conversion from NDK
     */
   public static native String welcomeString();
   public static native int doConvJniGraphics2(Bitmap pic, float quality_factor, String filename);
   static {
      System.loadLibrary("webpconv");
   }
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        initMainLayout();
    }
    
    @Override
    protected void onSaveInstanceState (Bundle outState){	
    	super.onSaveInstanceState(outState);
    }
    
    @Override
    protected void onPause(){
    	super.onPause();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
    }
    
    /**
     * Handles back button functionality.
     * If at home screen exit app, otherwise return to home screen.
     */
    @Override
    public void onBackPressed() {  
       if (m_currScreen != HOME_SCREEN) {
          initMainLayout();
       } else {
          super.onBackPressed();
       }
    }
    
    /**
     * Loads main screen layout.
     */
    protected void initMainLayout(){
       setContentView(R.layout.main);
        
       m_currScreen = HOME_SCREEN;
       m_takePictureCustom = (Button) findViewById(R.id.take_picture_custom);
       m_takePictureNative = (Button) findViewById(R.id.take_picture_native);
       m_connectServer = (Button) findViewById(R.id.connect_server);
       m_runTests = (Button) findViewById(R.id.run_tests);
       TextView tv = (TextView)findViewById(R.id.text_view_1);
       tv.setText(welcomeString());  // Tests connection to NDK
       tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
       // Use custom camera class (not recommended)
       m_takePictureCustom.setOnClickListener(new OnClickListener(){
          public void onClick(View view){
             m_cameraChoice = CAMERA_CUSTOM;
             startCameraCustom();
          }
       });
        
       // Use native camera application
       m_takePictureNative.setOnClickListener(new OnClickListener(){
          public void onClick(View view){
             m_cameraChoice = CAMERA_NATIVE;
             startCameraNative();
          }
       });
        
       // Test connection to server
       m_connectServer.setOnClickListener(new OnClickListener(){
          public void onClick(View view){
             connectToServer();
          }
       });
        
        // Run test suite
       m_runTests.setOnClickListener(new OnClickListener(){
          public void onClick(View view){
             // Tests will log test results
             testFlag = true;
             startCameraNative();
          }
       });
    }
    
   /**
    * Load layout to display picture to user and give option to convert picture and submit to server.
    */
   protected void initPictureLayout(){
      setContentView(R.layout.pictureview);
    	
      m_currScreen = PICTURE_SCREEN;
    	m_imageView1 = (ImageView) findViewById(R.id.imageview_1);
    	if(m_imageBitmap != null){
    	   m_imageView1.setImageBitmap(m_imageBitmap);
    	} else {
    	   Toast toast = Toast.makeText(getApplicationContext(), "No picture to convert! Please retake", Toast.LENGTH_LONG);
    	   toast.show();
    	   initMainLayout();
    	}
    	m_convert = (Button) findViewById(R.id.button_convert);
    	m_retake = (Button) findViewById(R.id.button_retake);
    	
    	// Convert picture to WebP and send to server then load MICR layout
    	m_convert.setOnClickListener(new OnClickListener(){
    	   public void onClick(View view){
    	      convertPicture();
    	      initMicrLayout();
    	   }
    	});
    	
    	// Retake picture
    	m_retake.setOnClickListener(new OnClickListener(){
    	   public void onClick(View view){
    	      if(m_cameraChoice == CAMERA_CUSTOM){
    	         startCameraCustom();
    	      } else if (m_cameraChoice == CAMERA_NATIVE) {
    	         startCameraNative();
    	      }
    	   }
    	});
   }
    
   /**
    * Load layout which shows MICR code returned form server (if scan successful).
    * Also displays picture user took.
    */
   protected void initMicrLayout(){
      setContentView(R.layout.micrview);
    	
    	m_currScreen = MICR_SCREEN;
    	m_imageView_micr = (ImageView) findViewById(R.id.imageview_micr);
    	if(m_imageBitmap != null){
    	   m_imageView_micr.setImageBitmap(m_imageBitmap);
    	} else {
    	   Toast toast = Toast.makeText(getApplicationContext(), "No picture to display", Toast.LENGTH_LONG);
    	   toast.show();
    	}
    	m_home = (Button) findViewById(R.id.button_home);
    	m_retake_micr = (Button) findViewById(R.id.button_retake_micr);
    	m_micr_text = (TextView) findViewById(R.id.micr_text);
    	m_micr_text.setTextSize(TypedValue.COMPLEX_UNIT_PX, 28);
    	m_micr_text.setText("MICR code: \n" + "Retreiving MICR code...");
    	
    	// Return to home screen
    	m_home.setOnClickListener(new OnClickListener(){
    		public void onClick(View view){
    			initMainLayout();
    		}
    	});
    	
    	// Retake picture
    	m_retake_micr.setOnClickListener(new OnClickListener(){
    	   public void onClick(View view){
    	      if(m_cameraChoice == CAMERA_CUSTOM){
    	         startCameraCustom();
    			} else if (m_cameraChoice == CAMERA_NATIVE) {
    				startCameraNative();
    			} else {
    				// Not sure what camera, just go home
    				initMainLayout();
    			}
    	   }
    	});
   }
    
    /**
     * Handles what to do after camera returns with picture data.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data){
    	super.onActivityResult(requestCode, resultCode, data);
    	
    	if(requestCode == WebPConv.CAMERA_CUSTOM_RC){
    		if(resultCode == Activity.RESULT_OK){
    			// Create bitmap from raw camera data
    			Log.v(WebPConv.TAG, "returned from custom camera");
    			m_imageByteArray = data.getExtras().getByteArray(CameraPreview.IMAGE_BYTE_ARRAY);
    			BitmapFactory.Options opts = new BitmapFactory.Options();
    			opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
				m_imageBitmap = BitmapFactory.decodeByteArray(m_imageByteArray, 0, m_imageByteArray.length, opts);
				Log.v(WebPConv.TAG, "Bitmap height: " + m_imageBitmap.getHeight() + " width: " + m_imageBitmap.getWidth());
				initPictureLayout();
    		} else if (resultCode == Activity.RESULT_CANCELED){
    		   // Picture failed
    			Toast toast = Toast.makeText(getApplicationContext(), "No Picture Taken", Toast.LENGTH_SHORT);
				toast.show();
    		}
    	} else if (requestCode == WebPConv.CAMERA_NATIVE_RC){
    		if(resultCode == Activity.RESULT_OK){
    			Log.v(WebPConv.TAG, "returned from native camera");   			
    			try {
    				// Create bitmap to display
					InputStream inputStream = this.getContentResolver().openInputStream(m_imageUri);
					BitmapFactory.Options opts = new BitmapFactory.Options();
	    	      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
	    	      opts.inSampleSize = 2;
	    	      m_imageBitmap = BitmapFactory.decodeStream(inputStream, null, opts);  
	    	      
	    	      // Run test suite, log results
	    	      if (testFlag) {
	    	         runWebPTests(m_imageBitmap);
	    	        	initMainLayout();
	    	        	return;
	    	      }
	    	        
	    	      inputStream.close();
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
				Log.v(WebPConv.TAG, "Bitmap height: " + m_imageBitmap.getHeight() + " width: " + m_imageBitmap.getWidth());
				initPictureLayout();
    		} else if (resultCode == Activity.RESULT_CANCELED){
    		   // Picture failed
    			Toast toast = Toast.makeText(getApplicationContext(), "No Picture Taken", Toast.LENGTH_SHORT);
				toast.show();
    		}
    	}
    }
    
    /**
     * Start camera using Camera class.
     * This method is not recommended, camera preview gives distorted image.
     */
    private void startCameraCustom(){
    	// Start Camera class, in CameraPreview.class
		Intent cameraIntent = new Intent(WebPConv.this, CameraPreview.class);
		startActivityForResult(cameraIntent, WebPConv.CAMERA_CUSTOM_RC);
    }
    
    // Start camera using native camera app (camera that comes with phone).
    private void startCameraNative(){
    	ContentValues values = new ContentValues();
    	values.put(MediaStore.Images.Media.TITLE, "pic");
    	values.put(MediaStore.Images.Media.DESCRIPTION,"Image capture by camera for WebPConv");
    	m_imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
    	Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    	intent.putExtra(MediaStore.EXTRA_OUTPUT, m_imageUri);
    	startActivityForResult(intent, WebPConv.CAMERA_NATIVE_RC);
    }
    
    // Run test suite, log results in LogCat
    private void runWebPTests(Bitmap bitmap){
    	WebPTests.webPConvTest(bitmap);
    }
    
    // Test connection to server.  Connects to server, sleeps, then disconnects.
    private void connectToServer(){
       m_client = new ClientActivity();
       m_client.connect();
    	
       try {
          Thread.sleep(3000);
       } catch (InterruptedException e) {
          e.printStackTrace();
       }
       m_client.disconnect();    	
    }
     
    /**
     * Calls NDK library to convert picture to WebP format.  Then sends to server in background
     * thread (AsyncTask).  MICR layout with MICR code should be displayed after.
     */
    private void convertPicture(){
    	if(m_imageBitmap.getWidth() == 0){
    		Toast toast = Toast.makeText(getApplicationContext(), "Image error, please retake", Toast.LENGTH_SHORT);
			toast.show();
			return;
    	}
    	
    	// Creates directory on phone's SD card to save WebP image
    	File baseDir = new File(Environment.getExternalStorageDirectory(), BASE_DIR_NAME);
		if(!baseDir.exists()){
			baseDir.mkdir();
		}
			
		// Paths to sample check images on SD card
		String convFilename = Environment.getExternalStorageDirectory() + "/" + BASE_DIR_NAME + "/" + getDateString() + ".webp";
		TIFF_IMAGE_PATH = Environment.getExternalStorageDirectory() + File.separator + WebPConv.BASE_DIR_NAME + File.separator + WebPConv.TEST_FILE_NAME_TIFF;
		JPEG_IMAGE_PATH = Environment.getExternalStorageDirectory() + File.separator + WebPConv.BASE_DIR_NAME + File.separator + WebPConv.TEST_FILE_NAME_JPEG;
		WEBP_IMAGE_PATH = Environment.getExternalStorageDirectory() + File.separator + WebPConv.BASE_DIR_NAME + File.separator + WebPConv.TEST_FILE_NAME_WEBP;
		
		// Convert image and submit to server in background thread (AsyncTask)
		ConvertSubmitTask convSubmit = new ConvertSubmitTask(m_imageBitmap, convFilename);
		convSubmit.execute();		
    }
    
    // Gets readable date string to label saved pictures.
    protected static String getDateString(){
        Calendar rightNow = Calendar.getInstance();
        String dateString;
        String month;
        switch(rightNow.get(Calendar.MONTH)+1){
        case 1:
        	month = "jan";
        	break;
        case 2:
        	month = "feb";
        	break;
        case 3:
        	month = "mar";
        	break;
        case 4:
        	month = "apr";
        	break;
        case 5:
        	month = "may";
        	break;
        case 6:
        	month = "jun";
        	break;
        case 7:
        	month = "jul";
        	break;
        case 8:
        	month = "aug";
        	break;
        case 9:
        	month = "sep";
        	break;
        case 10:
        	month = "oct";
        	break;
        case 11:
        	month = "nov";
        	break;
        case 12:
        	month = "dec";
        	break;
        default:
        	month = "NoMonth";
        	break;
        }
        dateString = rightNow.get(Calendar.DATE) + month + rightNow.get(Calendar.YEAR) + "_" + 
        			 rightNow.get(Calendar.HOUR_OF_DAY) + "_" + rightNow.get(Calendar.MINUTE) +
        			 "_" + rightNow.get(Calendar.SECOND) + "_" + rightNow.get(Calendar.MILLISECOND);
		return dateString;
	}
    
    //-----------------------------------------------------------------------------------------------
    
    /**
     * Background thread for converting image to WebP format and submitting to server.
     * Loads loading bar (spinner) with cancel button while converting/sending.
     *
     */
    private class ConvertSubmitTask extends AsyncTask<Void, Integer, String> implements OnDismissListener{
    	private String TAG = "ConvertSubmitTask";
    	private Bitmap m_bitmap;
    	private String m_fileName;
    	private final int START_SUBMIT = 1;
    	private final ProgressDialog dialog = new ProgressDialog(WebPConv.this);
    	
    	public ConvertSubmitTask(Bitmap bitmap, String fileName){
    		m_bitmap = bitmap;
    		m_fileName = fileName;
    	}
    	
		protected String doInBackground(Void... params) {
			Log.v(this.TAG, "doInBackground - start");
			
			// calls NDK library to convert image
			int convRet = doConvJniGraphics2(m_bitmap, WebPConv.IMG_QUALITY_FACTOR, m_fileName);
			Log.v(this.TAG, "convRet: " + convRet);
			
			// If user pressed back button then stop
			if (isCancelled()) {
				Log.v(this.TAG, "isCancelled() called");
				return null;
			}
			
			// Update loading bar (spinner).
			publishProgress(this.START_SUBMIT);
			
			ClientActivity client = new ClientActivity();
			client.connect();
			Log.v(WebPConv.TAG, "connect returned");	
			
			if (isCancelled()) {
				Log.v(this.TAG, "isCancelled() called");
				client.disconnect();
				return null;
			}
			
			client.sendCheck(m_fileName);          // Send image taken from camera
			//client.sendCheck(TIFF_IMAGE_PATH);   // TIFF format test check image
			//client.sendCheck(JPEG_IMAGE_PATH);   // JPEG format test check image
			//client.sendCheck(WEBP_IMAGE_PATH);   // WEBP format test check image
			Log.v(WebPConv.TAG, "sendCheck returned");
			
			if (isCancelled()) {
				Log.v(this.TAG, "isCancelled() called");
				client.disconnect();
				return null;
			}
			
			String micr = client.getMICR();
			Log.v(WebPConv.TAG, "getMICR returned: "  + micr);
			
			client.disconnect();
			Log.v(WebPConv.TAG, "disconnect returned");

			Log.v(this.TAG, "doInBackground - conversion complete");	
			return micr;
		}
    	
		// Run on UI thread before doInBackground()
		protected void onPreExecute(){
			Log.v(this.TAG, "onPreExecute");
			super.onPreExecute();
			
			// Load progress bar	
			dialog.setOnDismissListener((OnDismissListener) this);
			dialog.setMessage("Converting image to WebP format");
	      dialog.setCancelable(true);
			dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	      dialog.show();
		}
		
		protected void onProgressUpdate(Integer... progress){
			// Change text of loading bar (converting picture -> uploading picture)
			super.onProgressUpdate(progress);
	
			if(progress[0] == this.START_SUBMIT){
				if(dialog.isShowing()){
					dialog.setMessage("Submitting image to server");
				}
			}
		}
		
		protected void onPostExecute(String micr){
			Log.v(this.TAG, "onPostExecute");
			super.onPostExecute(micr);		
			
			// Display MICR response if any returned from server
			if((micr == null) || (micr.length() == 0)){
				m_micr_text.setText("MICR code: \n" + "Could not retreive MICR, please retake");
			} else {
				m_micr_text.setText("MICR code: \n" + micr);
			}
			
			// Dismiss progress bar
			if(dialog.isShowing()){
				dialog.dismiss();
			}
		}
		
		protected void onCancelled(){
			Log.v(this.TAG, "onCancelled()");
			
			m_micr_text.setText("MICR code: \n" + "Upload Cancelled!");
			
			// Dismiss progress bar
			if(dialog.isShowing()){
				dialog.dismiss();
			}
			super.onCancelled();
		}
		
		// Called when dialog is dismissed
		public void onDismiss(DialogInterface dialog) {
			this.cancel(true);  // Cancel this AsyncTask
		}
    }
}