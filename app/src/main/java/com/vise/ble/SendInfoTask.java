package com.vise.ble;

import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by zhangyuanyuan on 2017/9/12.
 */

public class SendInfoTask extends AsyncTask<String, String, String> {


    private BluetoothSocket mBluetoothSocket;

    public SendInfoTask(BluetoothSocket mBluetoothSocket) {
        this.mBluetoothSocket = mBluetoothSocket;
    }

    @Override
    protected void onPostExecute(String result) {
        // TODO Auto-generated method stub
        super.onPostExecute(result);
    }

    @Override
    protected String doInBackground(String... arg0) {
        // TODO Auto-generated method stub

        if (mBluetoothSocket == null) {
            return "还没有创建连接";
        }

        if (arg0[0].length() > 0) {

            String path = arg0[0];

            File file = new File(path);
            if(!file.exists()){
                return null;
            }

            try {
                OutputStream outputStream = mBluetoothSocket.getOutputStream();

                FileInputStream fileInputStream = new FileInputStream(file);

                byte[] buffer = new byte[1024];
                int len = 0;
                while((len=fileInputStream.read(buffer))!=-1){
                    outputStream.write(buffer, 0, len);
                }

                outputStream.close();
                fileInputStream.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }


//            byte[] msgBuffer = arg0[0].getBytes();
//            try {
//                //  将msgBuffer中的数据写到outStream对象中
//                outputStream.write(msgBuffer);
//            } catch (IOException e) {
//                Log.e("error", "ON RESUME: Exception during write.", e);
//                return "发送失败";
//            }
        }

        return "发送成功";
    }
}
