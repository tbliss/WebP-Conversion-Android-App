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

#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>
#include <android/bitmap.h>

#include "src/webp/encode.h"
#include "src/webp/types.h"
#include "src/webp/decode.h"
#include "src/webp/decode_vp8.h"
#include "src/enc/picture.c"

#define  LOG_TAG    "webpconv"
#define  LOGV(...)  __android_log_print(ANDROID_LOG_VERBOSE,LOG_TAG,__VA_ARGS__)

int MyMemoryWriter(const uint8_t* data, size_t data_size, const WebPPicture* const picture);

/**
 * Returns string. Tests connection to NDK from SDK
 */
jstring
Java_com_tbliss_android_seniorproject_webpconv_WebPConv_welcomeString( JNIEnv* env, 
                                                                       jobject javaThis) 
{
	LOGV("Calling first function in jni");
	return (*env)->NewStringUTF(env, "WebP Conversion Test");
}

/**
 * Convert bitmap object from Android code to WebP format. Save to file.
 */
jint Java_com_tbliss_android_seniorproject_webpconv_WebPConv_doConvJniGraphics2( JNIEnv* env,
																				 jobject javaThis,
																				 jobject jbitmap,
																				 jfloat jqualityFactor,
																				 jstring filename) 
{
	int ret = 0;
	float cqualityFactor;
	int stride, width, height;
	void* pixels; // pointer to address of bitmap
	int outputSize = 0;
	AndroidBitmapInfo bitmapInfo;
	uint8_t* outputPointer;
	uint8_t* cbitmapPointer;
	WebPConfig config;
	WebPMemoryWriter wrt;
	size_t dataWritten = 0;
	int bytesWritten = 0;
	FILE* fileout = NULL;
	const char* fname	= (*env)->GetStringUTFChars(env, filename, NULL);
	
	// Get Bitmap info (height/width/stride)
	if ((ret = AndroidBitmap_getInfo(env, jbitmap, &bitmapInfo)) < 0){
		LOGV("Could not get Bitmap info. error=%d", ret);
		return 0;
	}
	width = (int)bitmapInfo.width;
	height = (int)bitmapInfo.height;
	stride = (int)bitmapInfo.stride; // width * 4
	LOGV("Bitmap: width=%d, height=%d, stride=%d, format=%d", width, height, stride, bitmapInfo.format);
	
    // Lock Bitmap pixels
	if ((ret = AndroidBitmap_lockPixels(env, jbitmap, &pixels)) < 0) {
     LOGV("AndroidBitmap_lockPixels() failed ! error=%d", ret);
		 return 0;
    }

	cbitmapPointer = (uint8_t*)pixels;
	outputSize = stride * height;
	LOGV("output_size: %d", outputSize);
	outputPointer = (uint8_t*) malloc(outputSize);
	cqualityFactor = (float)jqualityFactor;
	
	// Setup a config
    if (!WebPConfigPreset(&config, WEBP_PRESET_PICTURE, cqualityFactor)) {
	    LOGV("WebPConfigPreset failed");
        return 0;   // version error
    }
    
    // ... additional tuning
	config.method = 1;
	LOGV("config.method = %d", config.method);
    
    if (WebPValidateConfig(&config) != 1) {
	    LOGV("Error with config");
	}

    // Setup the input data
    WebPPicture pic;
    if (!WebPPictureInit(&pic)) {
	    LOGV("WebPPictureInit failed");
        return 0;  // version error
    }

    pic.width = width;
    pic.height = height;
  	
    if (!WebPPictureImportRGBA(&pic, cbitmapPointer, stride)) {
		LOGV("WebPPictureImportRGB failed");
		return 0;
	}
	
    // Set up a byte-output write method. WebPMemoryWriter, for instance.
    pic.writer = MyMemoryWriter;
    pic.custom_ptr = &wrt;
    //InitMemoryWriter(&wrt);
	wrt.mem = &outputPointer;
	wrt.size = &dataWritten;
	wrt.max_size = outputSize;
	
    // Compress!
    ret = WebPEncode(&config, &pic);   // ok = 0 => error occurred!
	if (!ret) {
	    LOGV("ret == 0, WebPEncode fail");
	}
  
    WebPPictureFree(&pic);  // must be called independently of the 'ok' result.

	// Write to phone
	fileout = fopen(fname, "wb");
	if(!fileout){
		LOGV("cannot open output file %s", fname);
		return 0;
	}
	bytesWritten = fwrite(outputPointer, 1, dataWritten, fileout);
	LOGV("bytesWritten: %d", bytesWritten);
	
	// Unlock pixels of Bitmap
	fclose(fileout);
	AndroidBitmap_unlockPixels(env, jbitmap);
	free(outputPointer);
	(*env)->ReleaseStringUTFChars(env, filename, fname);
	
	return dataWritten;
}


/**
 * Method to write converted picture to pointer
 */
int MyMemoryWriter(const uint8_t* data, size_t data_size, const WebPPicture* const picture) {
    WebPMemoryWriter* const w = (WebPMemoryWriter*)picture->custom_ptr;
    size_t next_size;
    if (w == NULL) {
        return 1;
    }
    next_size = (*w->size) + data_size;
    if (next_size > w->max_size) {
        uint8_t* new_mem;
        size_t next_max_size = w->max_size * 2;
        if (next_max_size < next_size) next_max_size = next_size;
        if (next_max_size < 8192) next_max_size = 8192;
        new_mem = (uint8_t*)malloc(next_max_size);
        if (new_mem == NULL) {
            return 0;
        }
        if ((*w->size) > 0) {
            memcpy(new_mem, *w->mem, *w->size);
        }
        free(*w->mem);
        *w->mem = new_mem;
        w->max_size = next_max_size;
    }
    if (data_size) {
        memcpy((*w->mem) + (*w->size), data, data_size);
        *w->size += data_size;
    }
    return 1;
}
