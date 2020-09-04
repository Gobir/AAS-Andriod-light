package com.face.recognition.light;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;

import android.os.AsyncTask;

import android.widget.RelativeLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HttpRequest extends AsyncTask<Void,Void,String> {
    private ProgressDialog progressDialog;
    private Activity activity;
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog alertDialog;
    private final String action;
    private RelativeLayout checkIn, checkOut;
    private MainActivity.ToastHandler toastHandler;
    private FileReadWrite file;
    private String logsFileName = "logcat.txt";
    private Log log;
    private String baseUrl = "https://vmi338910.contaboserver.net/~devouss/face_recognition/api.php";
    private String id;

    public HttpRequest (final Activity activity, RelativeLayout checkIn, RelativeLayout checkOut, final String action, MainActivity.ToastHandler toastHandler, String id) {
        this.activity = activity;
        this.action = action;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.id = id;
        this.toastHandler = toastHandler;
        file = new FileReadWrite(activity);
        log = new Log();
        alertDialogBuilder  = new AlertDialog.Builder(activity);
        alertDialogBuilder.setCancelable(true);
        alertDialogBuilder.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                });
        alertDialog = alertDialogBuilder.create();
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        progressDialog = new ProgressDialog(activity);
        progressDialog.setTitle("Processing");
        progressDialog.setMessage("Sending your face capture for "+ action + ".\n\nThis process may take few minutes, please wait...");
        progressDialog.setCancelable(false);
        progressDialog.setCanceledOnTouchOutside(false);
        if(!activity.isFinishing()){
            progressDialog.show();
        }
    }

    @Override
    protected String doInBackground(Void... voids) {
        String okHttpResponseReturned;
        //Checking position if Within premises.
        OkHttpClient clientPosition = createClient();
        RequestBody formBodyPosition = new FormBody.Builder()
                .add("id", id)
                .add("action", action)
                .add("method", "verify")
                .build();
        Request requestPosition = createRequest(baseUrl, formBodyPosition);
        try {
            Response responsePosition = clientPosition.newCall(requestPosition).execute();
            okHttpResponseReturned = responsePosition.body().string();
            JSONObject jsonObject = new JSONObject(okHttpResponseReturned);
            if(jsonObject.has("status") && jsonObject.has("message")){
                String status, message = null;
                try {
                    status = jsonObject.get("status").toString();
                    message = jsonObject.get("message").toString();
                    if(status.equals("pass")){
                        //Verification made.
                        //Uploading face capture to server.
                        File imageFile = new File(MainActivity.currentImagePath);
                        OkHttpClient clientUpload = createClient();
                        RequestBody formBodyUpload = new MultipartBody.Builder()
                                .setType(MultipartBody.FORM)
                                .addFormDataPart("method", "upload")
                                .addFormDataPart("file", imageFile.getName(), RequestBody.create(MediaType.parse("image/jpg"), imageFile))
                                .build();
                        Request requestUpload = createRequest(baseUrl, formBodyUpload);
                        try {
                            Response responseUpload = clientUpload.newCall(requestUpload).execute();
                            okHttpResponseReturned = responseUpload.body().string();
                            if(responseUpload.code() == 200){
                                //Face capture successfully uploaded.
                                //Send image name to do authentication.
                                OkHttpClient clientAuth = createClient();
                                RequestBody formBodyAuth = new FormBody.Builder()
                                        .add("image_name", MainActivity.currentImageName)
                                        .add("id", id)
                                        .add("method", "register")
                                        .add("action", action)
                                        .build();
                                Request requestAuth = createRequest(baseUrl, formBodyAuth);
                                try {
                                    Response responseAuth = clientAuth.newCall(requestAuth).execute();
                                    okHttpResponseReturned = responseAuth.body().string();
                                    //Upload exceptions logs if available
                                    uploadExceptionsLogs(logsFileName);
                                    //Upload crashes report if available;
                                    uploadCrashReportsData();
                                } catch (IOException e) {
                                    okHttpResponseReturned = e.getMessage();
                                    log.save(log.printStackTraceToString(e), file);
                                }
                            }
                        } catch (IOException e) {
                            okHttpResponseReturned = e.getMessage();
                            log.save(log.printStackTraceToString(e), file);
                        }
                    }else{
                        okHttpResponseReturned = message;
                    }
                } catch (JSONException e) {
                    okHttpResponseReturned = e.getMessage();
                    log.save(log.printStackTraceToString(e), file);
                }
            }
        } catch (IOException e) {
            okHttpResponseReturned = e.getMessage();
            System.out.println(okHttpResponseReturned);
            log.save(log.printStackTraceToString(e), file);
        } catch (JSONException e) {
            okHttpResponseReturned = e.getMessage();
            log.save(log.printStackTraceToString(e), file);
        }
        return okHttpResponseReturned;
    }

    @Override
    protected void onPostExecute(String response) {
        super.onPostExecute(response);
        String message = null;
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if(!response.contains("Failed!") && !response.contains("Error")
                && !response.contains("Traceback (most recent call last):")
                && !response.isEmpty() && response != null){
            JSONObject jsonObject = null;
            try {
                jsonObject = new JSONObject(response);
                if(jsonObject.has("faces") && jsonObject.has("match")){
                    String match = null;
                    try {
                        match = jsonObject.get("match").toString();
                        if(match.equals("true")){
                            message = action + " Succeeded!";
                            MainActivity.currentImagePath = null;
                            MainActivity.currentImageName = null;
                        }else{
                            message = action + " Failed!\n\nPlease try to take a new capture.";
                        }
                    } catch (JSONException e) {
                        message = e.getMessage();
                        log.save(log.printStackTraceToString(e), file);
                    }
                }else{
                    message = action + " Failed! Invalid JSON server response.\n\n" + response;
                }
            } catch (JSONException e) {
                message = e.getMessage();
                log.save(log.printStackTraceToString(e), file);
            }
        }else{
            message = response;
        }
        alertDialog.setMessage(message);
        if(!activity.isFinishing()){
            alertDialog.show();
        }
    }

    private OkHttpClient createClient(){
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
        return client;
    }

    private Request createRequest(String Url, RequestBody formBodyPost){
        Request requestPost = new Request.Builder()
                .url(Url)
                .post(formBodyPost)
                .build();
        return requestPost;
    }

    public static String getTimeZone() {
        Calendar mCalendar = new GregorianCalendar();
        TimeZone mTimeZone = mCalendar.getTimeZone();
        int mUTCOffset = mTimeZone.getRawOffset();
        return "UTC+" + TimeUnit.HOURS.convert(mUTCOffset, TimeUnit.MILLISECONDS);
    }

    public static String getDateTime(String pattern){
        String dateTime = new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
        return dateTime;
    }

    private void echo (String msg){
        System.out.println(msg);
    }

    private void uploadExceptionsLogs(String logsFileName) {
        String LogRead = file.readFromFile(logsFileName, "internal");
        if (LogRead.length() != 0) {
            if(MainActivity.haveNetworkConnection(activity)){
                //Log file has content to upload to the server, do the upload.
                OkHttpClient clientLogs = createClient();
                RequestBody formBodyLogs = new FormBody.Builder()
                    .add("data", LogRead)
                    .add("id", id)
                    .add("time", getDateTime("HH:mm"))
                    .add("date", getDateTime("MM/dd/yyyy"))
                    .add("timezone", getTimeZone())
                    .add("errorType", "Exception")
                    .add("method", "errors")
                    .build();
                Request requestLogs = createRequest(baseUrl, formBodyLogs);
                try {
                    Response responseLogs = clientLogs.newCall(requestLogs).execute();
                } catch (IOException e) {
                    log.save(log.printStackTraceToString(e), file);
                }
            }
        }
    }

    private void uploadCrashReportsData(){
        String folderName = activity.getExternalFilesDir(null) + "/crashReports/";
        File dir = new File(folderName);
        if(dir.exists() && dir.isDirectory()) {
            File[] listFiles = dir.listFiles();
            String date = getDateTime("yyyy-MM-dd");
            StringBuilder sb = new StringBuilder();
            List<String> list = new ArrayList<>();
            for (int i = 0; i < listFiles.length; i++) {
                if (listFiles[i].isFile()) {
                    String fileName = listFiles[i].getName();
                    if (fileName.startsWith(date) && fileName.endsWith("_crash.txt")) {
                        sb.append(file.readFromFile(fileName, "external")).append("\n");
                        list.add(fileName);
                    }
                }
            }
            if (sb.length() != 0) {
                //Upload to the server the crash reports.
                if(MainActivity.haveNetworkConnection(activity)){
                    OkHttpClient clientCrash = createClient();
                    RequestBody formBodyCrash = new FormBody.Builder()
                            .add("data", sb.toString())
                            .add("id", id)
                            .add("time", getDateTime("HH:mm"))
                            .add("date", getDateTime("MM/dd/yyyy"))
                            .add("timezone", getTimeZone())
                            .add("errorType", "Crash")
                            .add("method", "errors")
                            .build();
                    Request requestCrash = createRequest(baseUrl, formBodyCrash);
                    try {
                        Response responseCrash = clientCrash.newCall(requestCrash).execute();
                        String okHttpResponseReturned = responseCrash.body().string();
                        if (!okHttpResponseReturned.contains("Error:")) {///ADD CORRESPONDING RESPONSE IN PHP and all the logs and crash methods created somewhere
                            for(int i = 0, size = list.size(); i < size; i++) {
                                File file = new File(folderName + list.get(i));
                                file.delete();
                            }
                        }else{
                            log.save(okHttpResponseReturned, file);
                        }
                    } catch (IOException e) {
                        log.save(log.printStackTraceToString(e), file);
                    }
                }
            }
        }
    }

}
