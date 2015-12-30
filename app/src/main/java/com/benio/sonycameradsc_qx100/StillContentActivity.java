/*
 * Copyright 2014 Sony Corporation
 */

package com.benio.sonycameradsc_qx100;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;

import com.benio.sonycameradsc_qx100.utils.DisplayHelper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class StillContentActivity extends Activity {

    private static final String TAG = StillContentActivity.class.getSimpleName();

    public static final String PARAM_IMAGE = "image";

    public static final String PARAM_FILE_NAME = "name";

    public static String SAVE_PATH = Environment.getExternalStorageDirectory() + "/Sony/";

    private ImageView mImageView;

    private Button mButtonSavePicture;

    private byte[] mImageByteArray;

    public static Intent newIntent(Context context, String url) {
        Intent intent = new Intent(context, StillContentActivity.class);
        intent.putExtra(PARAM_IMAGE, url);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Log.d(TAG, "onCreate() exec");

        setContentView(R.layout.activity_still_content);

        mImageView = (ImageView) findViewById(R.id.image_still_content);
        mButtonSavePicture = (Button) findViewById(R.id.button_save_image);

        mButtonSavePicture.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() exec");

        mButtonSavePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_no_external_storage);
                    return;
                }

                saveImage(SAVE_PATH, System.currentTimeMillis() + ".jpg");
            }
        });

        Intent intent = getIntent();
        String fileName = intent.getStringExtra(PARAM_FILE_NAME);
        String url = intent.getStringExtra(PARAM_IMAGE);

        setTitle(fileName);
        setProgressBarIndeterminateVisibility(true);
        showImage(url);
    }

    private void saveImage(final String path, final String fileName) {
        if (TextUtils.isEmpty(path) || TextUtils.isEmpty(fileName)) {
            Log.e(TAG, "saveImage: path or fileName is empty");
            return;
        }

        if (mImageByteArray == null) {
            Log.e(TAG, "saveImage: mImageByteArray is null");
            return;
        }

        new Thread() {

            @Override
            public void run() {

                byte[] imageByte = mImageByteArray;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                Bitmap bitmap = BitmapFactory.decodeByteArray(imageByte, 0, imageByte.length, options);

                File dirFile = new File(path);
                if (!dirFile.exists()) {
                    dirFile.mkdir();
                }

                File file = new File(path + fileName);
                if (file.exists()) {
                    file.delete();
                }

                FileOutputStream outputStream = null;
                try {
                    outputStream = new FileOutputStream(file);
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                } catch (IOException e) {
                    Log.e(TAG, "saveImage: fail stream close");
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_content);
                } finally {
                    try {
                        if (outputStream != null) {
                            outputStream.flush();
                            outputStream.close();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "saveImage: fail stream close");
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_content);
                    }

                    if (bitmap != null) {
                        bitmap.recycle();
                    }

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DisplayHelper.toast(getApplicationContext(), //
                                    R.string.msg_done);
                        }
                    });
                }

            }
        }.start();
    }

    private void showImage(final String url) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                InputStream istream = null;
                BitmapFactory.Options options = new BitmapFactory.Options();
                try {
                    URL imageUrl = new URL(url);
                    istream = imageUrl.openStream();
                    byte[] image = readBytes(istream);

                    mImageByteArray = image;

                    // confirm image size
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(image, 0, image.length, options);

                    // resize rate
                    int scaleWidth = options.outWidth / mImageView.getWidth() + 1;
                    int scaleHeight = options.outHeight / mImageView.getHeight() + 1;
                    int resizeRate = Math.min(scaleWidth, scaleHeight);

                    // decode with specific resize rate.
                    options.inJustDecodeBounds = false;
                    options.inSampleSize = resizeRate;
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length, options);

                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            mImageView.setImageBitmap(bitmap);
                            mButtonSavePicture.setEnabled(true);
                        }
                    });

                } catch (IOException e) {
                    Log.w(TAG, "showImage: IOException:" + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_content);
                } finally {
                    if (istream != null) {
                        try {
                            istream.close();
                        } catch (IOException e) {
                            Log.e(TAG, "showImage: fail stream close");
                            DisplayHelper.toast(getApplicationContext(), //
                                    R.string.msg_error_content);
                        }
                    }
                    DisplayHelper.setProgressIndicator(StillContentActivity.this, false);
                }
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mImageByteArray = null;
    }

    @Override
    protected void onPause() {
        super.onPause();

        BitmapDrawable bmpDrawable = (BitmapDrawable) mImageView.getDrawable();
        if (bmpDrawable != null) {
            Bitmap bitmap = bmpDrawable.getBitmap();
            if (bitmap != null) {
                mImageView.setImageDrawable(null);
                bitmap.recycle();
            }
        }
    }

    /**
     * Reads byte array from input stream.
     *
     * @param in
     * @return
     * @throws IOException
     */
    private static byte[] readBytes(InputStream in) throws IOException {
        ByteArrayOutputStream tmpByteArray = new ByteArrayOutputStream();
        byte[] buffer = new byte[10240];
        try {
            while (true) {
                int readlen = in.read(buffer, 0, buffer.length);
                if (readlen < 0) {
                    break;
                }
                tmpByteArray.write(buffer, 0, readlen);
            }
        } finally {
            try {
                tmpByteArray.close();
            } catch (IOException e) {
                Log.d(TAG, "readByte() IOException");
            }
        }

        byte[] ret = tmpByteArray.toByteArray();
        return ret;
    }
}
