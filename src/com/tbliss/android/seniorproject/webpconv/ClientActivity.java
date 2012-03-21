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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import android.util.Log;

public class ClientActivity {
	private static final String TAG = "ClientActivity";
	
	// Server application information
	private final String SERVER_IP = "99.159.44.144";  // Update to computer server running on
	private final int SERVER_PORT = 10000;  // Set port number
	
	// Header constants
	private final int V_FIXED_HEADER_INT_VALUE = 0x55AA33CC;
	private final int HEADER_INTS = 16;
	private final int MAX_DATA_BYTES = 448;
	private final int BYTES_PER_INT = 4;
	private final int SEGMENT_SIZE = 512; // (header_ints * bytes_per_int) + max_data_bytes
	private final int READ_BUFFER_SIZE = 65535;
	
	// Header indices
	private final int H_FIXED_HEADER_INT = 0;
	private final int H_DATA_TYPE = 1;
	private final int H_MESSAGE_TYPE = 2;
	private final int H_DATA_BYTES_USED = 3;
	private final int H_SEGMENT_NUMBER = 4;
	private final int H_TOTAL_SEGMENTS = 5;
	private final int H_CHECK_INFO = 6;
	
	// Header data type
	private final int V_DATA_MESSAGE = 0;
	private final int V_DATA_CHECK_START = 1;
	private final int V_DATA_CHECK_SEGMENT = 2;
	private final int V_DATA_REQUEST_MICR = 4;
	private final int V_DATA_MICR = 8;
	
	// Header message type (used when data type is DATA_MESSAGE)
	private final int V_MESSAGE_ERROR = 0;
	private final int V_MESSAGE_CONNECT = 1;
	private final int V_MESSAGE_CHAT = 2;
	private final int V_MESSAGE_DISCONNECT = 4;
	private final int V_MESSAGE_REQUESTUSERS = 8;
	private final int V_MESSAGE_JOIN = 16;
	private final int V_MESSAGE_REFUSE = 32;
	private final int V_MESSAGE_LISTUSERS = 64;
	private final int V_MESSAGE_BROAD = 128;
	
	// Header check info (if data type is REQUEST_MICR)
	private final int V_CHECK_INFO_NONE = 0;
	private final int V_CHECK_INFO_ROTATED = 1;
	private final int V_CHECK_INFO_BLACKEDEDGES = 2;
	private final int V_CHECK_INFO_BOTH = 3;
	
	private OutputStream m_out;
	private InputStream m_in;
	private Socket m_socket;
	public boolean CONNECTED;
	
	public ClientActivity(){
		Log.v(ClientActivity.TAG, "ClientActivity created");
		CONNECTED = false;
	}
	
	// Connect to server
	public void connect(){
		Log.v(ClientActivity.TAG, "connect() called: " + SERVER_IP);
		int[] header = new int[HEADER_INTS];
		try {
			InetAddress serverAddr = InetAddress.getByName(SERVER_IP);
			m_socket = new Socket(serverAddr, SERVER_PORT);
			m_out = m_socket.getOutputStream();
			m_in = m_socket.getInputStream();
			
			// Build connection header
			header[H_FIXED_HEADER_INT] = V_FIXED_HEADER_INT_VALUE;
			header[H_DATA_TYPE] = V_DATA_MESSAGE;
			header[H_MESSAGE_TYPE] = V_MESSAGE_CONNECT;
			header[H_DATA_BYTES_USED] = 0; // Not sending any data
			header[H_SEGMENT_NUMBER] = 1;
			header[H_TOTAL_SEGMENTS] = 1;
					
			// Convert int[] to byte[]
			Log.v(ClientActivity.TAG, "Header built, converting to byte[]");
			byte[] data;
			ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_INTS*4);
			byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			for(int i = 0; i < HEADER_INTS; i++){
				byteBuffer.putInt(header[i]);
			}
			data = byteBuffer.array();
			
			// Send header file
			//Log.v(ClientActivity.TAG, "Writing header/data to server: " + data.toString());
			for(int i = 0; i < HEADER_INTS; i++){
				int startIndex = i*4; // index of where next int starts. ints are 4 bytes
				m_out.write(data, startIndex, 4);
			}
			
			// Write full data segment at once (no data here)
			byte[] emptyData = new byte[MAX_DATA_BYTES];
			m_out.write(emptyData, 0, MAX_DATA_BYTES);
			
			// Get server response
			byte[] serverResponse = new byte[READ_BUFFER_SIZE];
			// Know on connect server sends 2 messages, second one has client name index 60-66/67
			for(int i = 0; i < 2; i++){
				int bytesRead = m_in.read(serverResponse, 0, READ_BUFFER_SIZE);
			}
			this.CONNECTED = true;
	
		} catch (UnknownHostException e) {
			Log.v(ClientActivity.TAG, "Problem connecting to server");
			e.printStackTrace();
		} catch (IOException e) {
			Log.v(ClientActivity.TAG, "Socket error");
			e.printStackTrace();
		}
		Log.v(ClientActivity.TAG, "connect() finished");
	}
	
	// Disconnect from server
	public void disconnect(){
		Log.v(ClientActivity.TAG, "disconnect() called");
		if(this.CONNECTED == false){
         return;
      }
      int[] header = new int[HEADER_INTS];
      try {
         // Build connection header
         header[H_FIXED_HEADER_INT] = V_FIXED_HEADER_INT_VALUE;
         header[H_DATA_TYPE] = V_DATA_MESSAGE;
         header[H_MESSAGE_TYPE] = V_MESSAGE_DISCONNECT;
         header[H_DATA_BYTES_USED] = 0; // Not sending any data
         header[H_SEGMENT_NUMBER] = 1;
         header[H_TOTAL_SEGMENTS] = 1;
               
         // Convert int[] to byte[]
         Log.v(ClientActivity.TAG, "Header built, converting to byte[]");
         byte[] data;
         ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_INTS*4);
         byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
         for(int i = 0; i < HEADER_INTS; i++){
            byteBuffer.putInt(header[i]);
         }
         data = byteBuffer.array();
         
         // Send header file
         Log.v(ClientActivity.TAG, "Writing header/data to server: " + data.toString());
         for(int i = 0; i < HEADER_INTS; i++){
            int startIndex = i*4; // index of where next int starts. ints are 4 bytes
            m_out.write(data, startIndex, 4);
         }
         
         // Write full data segment at once (no data here)
         byte[] emptyData = new byte[MAX_DATA_BYTES];
         m_out.write(emptyData, 0, MAX_DATA_BYTES);

         this.CONNECTED = false; 
      } catch (UnknownHostException e) {
         Log.v(ClientActivity.TAG, "Problem disconnecting from server");
         e.printStackTrace();
      } catch (IOException e) {
         Log.v(ClientActivity.TAG, "Disconnect error");
         e.printStackTrace();
      }
		
		try {
			if(m_in != null)
				m_in.close();
			if(m_out != null)
				m_out.close();
			if(m_socket != null)
				m_socket.close();
		} catch (IOException e) {
			Log.v(ClientActivity.TAG, "Error closing input/output stream");
			e.printStackTrace();
		}
      Log.v(ClientActivity.TAG, "disconnect() finished");
	}
	
	/**
	 * Send check image to server
	 */
	public int sendCheck(String imgFilePath){
		Log.v(ClientActivity.TAG, "sendCheck: " + imgFilePath);
		// Check to make sure connected
		if(this.CONNECTED == false){
			Log.v(ClientActivity.TAG, "sendCheck - not connected");
			return 1;
		}
		
		int[] header = new int[HEADER_INTS];
		File file = new File(imgFilePath);
		InputStream inStream = null;
		int bytesRead = 0;
		long fileByteCount = file.length();
		byte[] imgByteArray = new byte[(int)fileByteCount];
		try {
			inStream = new FileInputStream(file);
			bytesRead = inStream.read(imgByteArray, 0, (int)fileByteCount);
			if(inStream != null){
				inStream.close();
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		Log.v(ClientActivity.TAG, "bytes read (imgByteArray): " + bytesRead);
		Log.v(ClientActivity.TAG, "size of imgByteArray: " + imgByteArray.length);
		
		int bytesRemaining = imgByteArray.length;
		int startImageIndex = 0;
		int endImageIndex = 0;
		int segmentCounter = 1;
		while(bytesRemaining > 0){
			byte[] dataSeg = new byte[MAX_DATA_BYTES];
			
			if(bytesRemaining < MAX_DATA_BYTES){
				// Only copy over where data is
				endImageIndex = startImageIndex + bytesRemaining;
				header[H_DATA_BYTES_USED] = bytesRemaining;
			} else {
				// Copy full chunk
				endImageIndex = startImageIndex + MAX_DATA_BYTES;
				header[H_DATA_BYTES_USED] = MAX_DATA_BYTES;
			}
			
			// Copy bytes from image to dataSeg buffer
			for(int i = startImageIndex; i < endImageIndex; i++){
				dataSeg[i-startImageIndex] = imgByteArray[i];
			}

			// Build header
			header[H_FIXED_HEADER_INT] = V_FIXED_HEADER_INT_VALUE;
			header[H_DATA_TYPE] = V_DATA_CHECK_SEGMENT;
			header[H_MESSAGE_TYPE] = V_MESSAGE_ERROR;
			header[H_SEGMENT_NUMBER] = segmentCounter;
			header[H_TOTAL_SEGMENTS] = 0;			
			
			// Convert int[] to byte[]
			ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_INTS*4);
			byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
			for(int i = 0; i < HEADER_INTS; i++){
				byteBuffer.putInt(header[i]);
			}
			byte[] headerData = byteBuffer.array();
			
			try{
				// Send header file
				//Log.v(ClientActivity.TAG, "sendCheck: Writing header/data to server");
				for(int i = 0; i < HEADER_INTS; i++){
					int startIndex = i*4; // index of where next int starts. ints are 4 bytes
					m_out.write(headerData, startIndex, 4);
				}
				
				// Write full data segment at once
				m_out.write(dataSeg, 0, MAX_DATA_BYTES);
			} catch (IOException e) {
				Log.v(ClientActivity.TAG, "Error sending data to server");
				e.printStackTrace();
				return 2;
			}
			
			segmentCounter++;
			startImageIndex += MAX_DATA_BYTES;  // Move over one full data chunk
			bytesRemaining -= MAX_DATA_BYTES;
		}

		return 0;
	}
	
	public String getMICR(){
		if(this.CONNECTED == false){
			Log.v(ClientActivity.TAG, "getMICR - not connected");
			return "";
		}
		
		String micr = "";
		int[] header = new int[HEADER_INTS];
		header[H_FIXED_HEADER_INT] = V_FIXED_HEADER_INT_VALUE;
		header[H_DATA_TYPE] = V_DATA_REQUEST_MICR;
		header[H_MESSAGE_TYPE] = V_MESSAGE_ERROR;
		header[H_DATA_BYTES_USED] = 0;
		header[H_SEGMENT_NUMBER] = 1;
		header[H_TOTAL_SEGMENTS] = 1;
		header[H_CHECK_INFO] = 1;
		
		// Convert int[] to byte[]
		Log.v(ClientActivity.TAG, "Header built, converting to byte[]");
		ByteBuffer byteBuffer = ByteBuffer.allocate(HEADER_INTS*4);
		byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
		for(int i = 0; i < HEADER_INTS; i++){
			byteBuffer.putInt(header[i]);
		}
		byte[] headerData = byteBuffer.array();
		
		// Send header file
		Log.v(ClientActivity.TAG, "Writing header/data to server");
		try {
			for(int i = 0; i < HEADER_INTS; i++){
				int startIndex = i*4; // index of where next int starts. ints are 4 bytes
				m_out.write(headerData, startIndex, 4);
			}
			
			// Write full data segment at once (empty)
			byte[] emptyData = new byte[MAX_DATA_BYTES];
			m_out.write(emptyData, 0, MAX_DATA_BYTES);
		} catch (IOException e) {
			Log.v(ClientActivity.TAG, "getMICR: error writting header");
			e.printStackTrace();
		}
		
		// Get MICR from server response
		try {
			byte[] serverResponse = new byte[READ_BUFFER_SIZE];
			for(int i = 0; i < 2; i++){
				int responseBytesRead = m_in.read(serverResponse, 0, READ_BUFFER_SIZE);
				Log.v(ClientActivity.TAG, "line read: " + i);
				for(int j = 0; j < 110; j++){
					Log.v(ClientActivity.TAG, "serverResponse["+j+"]: " + serverResponse[j]);
				}
				
				// On second read MICR spaces start at index 60, MICR ends on 0 (null)
				// start at index 60, go until end of spaces, grab MICR until null
				if(i == 1){
					StringBuilder stringBuilder = new StringBuilder();
					if(serverResponse[8] == 0){
						// index 8 is 0 when error with read
						Log.v(ClientActivity.TAG, "getMICR: index 8 == 0");
						return "";
					}				
					for(int k = 60; k < 200; k++){
						Log.v(ClientActivity.TAG, "serverResonse["+(k-1)+"]: " + serverResponse[k-1]);
						if (serverResponse[k] != 32) {
							while (serverResponse[k] != 0) {
								stringBuilder.append((char)serverResponse[k]);
								k++;
							}
							k = READ_BUFFER_SIZE; // break for loop
						}
					}
					micr = stringBuilder.toString();
				}
			}
		} catch (IOException e) {
			Log.v(ClientActivity.TAG, "getMICR: error reading response");
			e.printStackTrace();
		}
		
		return micr;
	}
	
	public String getServerIp(){
	   return SERVER_IP;
	}
}