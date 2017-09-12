package com.vise.ble;

import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.util.Log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by zhangyuanyuan on 2017/9/12.
 */

public class SendInfoTask extends AsyncTask<String, String, String> {


    private BluetoothSocket mBluetoothSocket;
    private OutputStream outputStream;

    public SendInfoTask(BluetoothSocket mBluetoothSocket, OutputStream outputStream) {
        this.mBluetoothSocket = mBluetoothSocket;
        this.outputStream = outputStream;
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

            byte[] msgBuffer = arg0[0].getBytes();
            try {
                //  将msgBuffer中的数据写到outStream对象中
                outputStream.write(msgBuffer);
            } catch (IOException e) {
                Log.e("error", "ON RESUME: Exception during write.", e);
                return "发送失败";
            }
        }

        return "发送成功";
    }
}
