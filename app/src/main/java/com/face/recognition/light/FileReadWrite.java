package com.face.recognition.light;

/**
 * FileReadWrite.java
 *
 * Reads and writes into a file.
 */

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

public class FileReadWrite {

    private Context context;
    private Log log;

    public FileReadWrite(Context context){
        this.context = context;
        log = new Log();
    }

    public String initFile(String fileName) {
        try {
            new OutputStreamWriter(context.openFileOutput(fileName, context.MODE_PRIVATE));
            return "";
        }
        catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

    public String readFromFile(String fileName, String type) {
        try {
            FileInputStream fis = null;
            if(type.equals("internal")){
                File file = context.getFileStreamPath(fileName);
                if(!file.exists()){
                    initFile(fileName);
                }
                fis = context.openFileInput(fileName);
            }else if(type.equals("external")){
                fis = new FileInputStream(new File(context.getExternalFilesDir(null) + "/crashReports/", fileName));
            }
            InputStreamReader isr = new InputStreamReader(fis, "UTF-8");
            BufferedReader bufferedReader = new BufferedReader(isr);
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            isr.close();
            return sb.toString();
        } catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (UnsupportedEncodingException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

    public String writeToFile(String fileName, String data) {
        try {
            OutputStreamWriter outputStreamWriter = new OutputStreamWriter(context.openFileOutput(fileName, context.MODE_APPEND));
            outputStreamWriter.write(data);
            outputStreamWriter.close();
            return "Data stored locally";
        }
        catch (FileNotFoundException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (UnsupportedEncodingException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        } catch (IOException e) {
            log.save(log.printStackTraceToString(e), this);
            return "Error: " + e.getMessage();
        }
    }

}
