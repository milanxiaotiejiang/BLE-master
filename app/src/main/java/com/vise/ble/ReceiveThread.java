package com.vise.ble;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;
import java.io.InputStream;

/**
 * Created by zhangyuanyuan on 2017/9/12.
 */

public class ReceiveThread extends Thread {


    private BluetoothSocket mBluetoothSocket;
    private InputStream inputStream;

    public ReceiveThread(BluetoothSocket mBluetoothSocket, InputStream inputStream) {
        this.mBluetoothSocket = mBluetoothSocket;
        this.inputStream = inputStream;
    }

    @Override
    public void run() {

        while (mBluetoothSocket != null) {
            //定义一个存储空间buff
            byte[] buff = new byte[1024];
            try {
                inputStream = mBluetoothSocket.getInputStream();

                System.out.println("waitting for instream");

                inputStream.read(buff); //读取数据存储在buff数组中


                processBuffer(buff, 1024);

                //System.out.println("receive content:"+ReceiveData);
            } catch (IOException e) {

                e.printStackTrace();
            }
        }
    }

    private void processBuffer(byte[] buff, int size) {
        int length = 0;
        for (int i = 0; i < size; i++) {
            if (buff[i] > '\0') {
                length++;
            } else {
                break;
            }
        }

        byte[] newbuff = new byte[length];  //newbuff字节数组，用于存放真正接收到的数据

        for (int j = 0; j < length; j++) {
            newbuff[j] = buff[j];
        }

    }

}
