/*
 * Copyright 2014 Sony Corporation
 */

package com.benio.sonycameradsc_qx100;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;

import com.benio.sonycameradsc_qx100.utils.DisplayHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

public class MovieContentActivity extends Activity {

    public static final String PARAM_MOVIE = "movie";

    public static final String PARAM_MOVIE_URL = "url";

    public static final String PARAM_FILE_NAME = "name";

    public static String SAVE_PATH = Environment.getExternalStorageDirectory() + "/Sony/";

    private static final String TAG = MovieContentActivity.class.getSimpleName();

    private SimpleStreamSurfaceView mStreamSurface;

    private Button mButtonSaveMovie;

    private SimpleRemoteApi mRemoteApi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        Log.d(TAG, "onCreate() exec");

        setContentView(R.layout.activity_movie_content);

        mStreamSurface = (SimpleStreamSurfaceView) findViewById(R.id.surfaceview_movie);
        mButtonSaveMovie = (Button) findViewById(R.id.button_save_movie);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SampleApplication app = (SampleApplication) getApplication();
        mRemoteApi = app.getRemoteApi();
        if (mRemoteApi == null) {
            Log.w(TAG, "RemoteApi is null");
            DisplayHelper.toast(getApplicationContext(), R.string.msg_error_content);
            return;
        }

        Log.d(TAG, "onResume() exec");
        if (mStreamSurface.isStarted()) {
            return;
        }

        setProgressBarIndeterminateVisibility(true);

        final Intent intent = getIntent();
        final String fileName = intent.getStringExtra(PARAM_FILE_NAME);
        final String uri = intent.getStringExtra(PARAM_MOVIE);
        final String url = intent.getStringExtra(PARAM_MOVIE_URL);

        mButtonSaveMovie.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mStreamSurface.stop();

                if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_no_external_storage);
                    return;
                }

                saveMovie(url, SAVE_PATH, fileName);
            }
        });

        setTitle(fileName);

        startStreaming(uri);
    }

    private void startStreaming(final String uri) {
        new Thread() {

            @Override
            public void run() {

                JSONObject replyJson = null;

                try {
                    // Make target device ready to start content streaming
                    replyJson = mRemoteApi.setStreamingContent(uri);
                    if (SimpleRemoteApi.isErrorReply(replyJson)) {
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_connection);
                        return;
                    }

                    JSONObject resultsObj = replyJson.getJSONArray("result").getJSONObject(0);

                    if (resultsObj.length() < 1) {
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_connection);
                        return;
                    }

                    // Obtain streaming URL from the result.
                    String streamingUrl = resultsObj.getString("playbackUrl");

                    DisplayHelper.setProgressIndicator(MovieContentActivity.this, false);

                    // Make target device to start content streaming
                    replyJson = mRemoteApi.startStreaming();

                    if (SimpleRemoteApi.isErrorReply(replyJson)) {
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_connection);
                        return;
                    }

                    // Start retrieving image stream.
                    mStreamSurface.start(streamingUrl, //
                            new SimpleStreamSurfaceView.StreamErrorListener() {

                                @Override
                                public void onError(StreamErrorReason reason) {
                                    Log.w(TAG, "Error startStreaming():" + reason.toString());
                                    DisplayHelper.toast(getApplicationContext(), //
                                            R.string.msg_error_connection);
                                    stopStreaming();
                                }
                            });

                } catch (IOException e) {
                    Log.w(TAG, "startStreaming: IOException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_connection);
                    DisplayHelper.setProgressIndicator(MovieContentActivity.this, false);
                } catch (JSONException e) {
                    Log.w(TAG, "startStreaming: JSONException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_connection);
                    DisplayHelper.setProgressIndicator(MovieContentActivity.this, false);
                }
            }
        }.start();
    }

    private void stopStreaming() {
        new Thread() {

            @Override
            public void run() {
                try {
                    mRemoteApi.stopStreaming();
                } catch (IOException e) {
                    Log.w(TAG, "stopStreaming: IOException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), R.string.msg_error_content);
                }
            }
        }.start();
    }

    private void saveMovie(final String movieUrl, final String path, final String fileName) {
        if (TextUtils.isEmpty(movieUrl) || TextUtils.isEmpty(path) || TextUtils.isEmpty(fileName)) {
            Log.e(TAG, "saveMovie: movieUrl, path or fileName is empty");
            return;
        }

        new Thread() {

            @Override
            public void run() {
                try {
                    JSONObject replyJson = mRemoteApi.stopStreaming();
                    if (SimpleRemoteApi.isErrorReply(replyJson)) {
                        Log.w(TAG, "stopStreaming error:" + replyJson.toString());
                        DisplayHelper.toast(getApplicationContext(), //
                                R.string.msg_error_content);
                    } else {
                        Log.v(TAG, "stopStreaming: success");

                        File dirFile = new File(path);
                        if (!dirFile.exists()) {
                            dirFile.mkdir();
                        }

                        File file = new File(path + fileName);
                        if (file.exists()) {
                            DisplayHelper.toast(getApplicationContext(), R.string.msg_done);
                            return;
                        }

                        FileOutputStream outputStream = null;
                        InputStream istream = null;
                        try {
                            URL url = new URL(movieUrl);
                            URLConnection urlConn = url.openConnection();
                            istream = urlConn.getInputStream();
                            byte[] bytes = new byte[4096];
                            outputStream = new FileOutputStream(file);
                            int len;
                            while ((len = istream.read(bytes)) != -1) {
                                outputStream.write(bytes, 0, len);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            Log.w(TAG, "saveMovie: IOException:" + e.getMessage());
                            DisplayHelper.toast(getApplicationContext(), //
                                    R.string.msg_error_content);
                        } finally {
                            try {
                                if (outputStream != null) {
                                    outputStream.flush();
                                    outputStream.close();
                                }

                                if (istream != null) {
                                    istream.close();
                                }
                            } catch (IOException e) {
                                Log.e(TAG, "saveMovie: fail stream close");
                                DisplayHelper.toast(getApplicationContext(), //
                                        R.string.msg_error_content);
                            }

                            DisplayHelper.toast(getApplicationContext(), R.string.msg_done);
                        }
                    }
                } catch (IOException e) {
                    Log.w(TAG, "stopStreaming: IOException: " + e.getMessage());
                    DisplayHelper.toast(getApplicationContext(), //
                            R.string.msg_error_content);
                }
            }
        }.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() exec");
        mStreamSurface.stop();
        stopStreaming();
    }
}
