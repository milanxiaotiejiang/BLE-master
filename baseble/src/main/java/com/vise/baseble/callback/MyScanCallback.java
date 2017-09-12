package com.vise.baseble.callback;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import com.vise.baseble.ViseBluetooth;
import com.vise.baseble.common.BleConstant;
import com.vise.baseble.common.State;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.model.BluetoothLeDeviceStore;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zhangyuanyuan on 2017/9/12.
 */

public abstract class MyScanCallback implements BluetoothAdapter.LeScanCallback {

    private Handler handler = new Handler(Looper.getMainLooper());
    private AtomicBoolean hasFound = new AtomicBoolean(false);
    private BluetoothLeDeviceStore bluetoothLeDeviceStore;//用来存储扫描到的设备
    private Pattern pattern;
    private Matcher matcher;
    protected ViseBluetooth viseBluetooth;

    private int scanTimeout = BleConstant.TIME_FOREVER; //表示一直扫描
    private String filterDeviceName;//过滤设备的名称
    private int filterDeviceRssi;//过滤设备的信号
    private boolean isScan = true;//是否开始扫描
    private boolean isScanning = false;//是否正在扫描
    private String deviceName;//扫描指定名称的设备
    private String deviceMac;//扫描指定Mac地址的设备

    public MyScanCallback() {
        bluetoothLeDeviceStore = new BluetoothLeDeviceStore();
        pattern = Pattern.compile("^[\\x00-\\xff]*$");
    }

    public MyScanCallback setScanTimeout(int scanTimeout) {
        this.scanTimeout = scanTimeout;
        return this;
    }

    public MyScanCallback setScan(boolean scan) {
        isScan = scan;
        return this;
    }

    public MyScanCallback setViseBluetooth(ViseBluetooth viseBluetooth) {
        this.viseBluetooth = viseBluetooth;
        return this;
    }

    public MyScanCallback setFilterDeviceName(String filterDeviceName) {
        this.filterDeviceName = filterDeviceName;
        if (!TextUtils.isEmpty(this.filterDeviceName)) {
            pattern = Pattern.compile(this.filterDeviceName);
        }
        bluetoothLeDeviceStore.clear();
        return this;
    }

    public MyScanCallback setFilterDeviceRssi(int filterDeviceRssi) {
        this.filterDeviceRssi = filterDeviceRssi;
        bluetoothLeDeviceStore.clear();
        return this;
    }

    public MyScanCallback setDeviceName(String deviceName) {
        this.deviceName = deviceName;
        bluetoothLeDeviceStore.clear();
        return this;
    }

    public MyScanCallback setDeviceMac(String deviceMac) {
        this.deviceMac = deviceMac;
        bluetoothLeDeviceStore.clear();
        return this;
    }

    public boolean isScanning() {
        return isScanning;
    }

    public int getScanTimeout() {
        return scanTimeout;
    }

    public void scan() {
        if (isScan) {
            if (isScanning) {
                return;
            }
            if (scanTimeout > 0) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isScanning = false;
                        if (viseBluetooth != null) {
                            viseBluetooth.setState(State.SCAN_TIMEOUT);
                            viseBluetooth.stopLeScan(MyScanCallback.this);
                        }
                        scanTimeout();
                    }
                }, scanTimeout);
            }
            isScanning = true;
            if (viseBluetooth != null) {
                viseBluetooth.startLeScan(MyScanCallback.this);
            }
        } else {
            isScanning = false;
            if (viseBluetooth != null) {
                viseBluetooth.stopLeScan(MyScanCallback.this);
            }
        }
    }

    public MyScanCallback removeHandlerMsg() {
        handler.removeCallbacksAndMessages(null);
        return this;
    }

    @Override
    public void onLeScan(BluetoothDevice bluetoothDevice, int rssi, byte[] scanRecord) {
//        if (TextUtils.isEmpty(deviceName) && TextUtils.isEmpty(deviceMac)) {
            onDeviceFound(new BluetoothLeDevice(bluetoothDevice, rssi, scanRecord, System.currentTimeMillis(), false));
//        } else {
//            if (!hasFound.get()) {
//                if (bluetoothDevice != null && bluetoothDevice.getAddress() != null && deviceMac != null
//                        && deviceMac.equalsIgnoreCase(bluetoothDevice.getAddress().trim())) {
//                    hasFound.set(true);
//                    viseBluetooth.stopLeScan(MyScanCallback.this);
//                    onDeviceFound(new BluetoothLeDevice(bluetoothDevice, rssi, scanRecord, System.currentTimeMillis()));
//                } else if (bluetoothDevice != null && bluetoothDevice.getName() != null && deviceName != null
//                        && deviceName.equalsIgnoreCase(bluetoothDevice.getName().trim())) {
//                    hasFound.set(true);
//                    viseBluetooth.stopLeScan(MyScanCallback.this);
//                    onDeviceFound(new BluetoothLeDevice(bluetoothDevice, rssi, scanRecord, System.currentTimeMillis()));
//                }
//            }
//        }
    }

    protected void onDeviceFound(BluetoothLeDevice bluetoothLeDevice) {
        String tempName = bluetoothLeDevice.getName();
        Log.e("name", tempName);
        int tempRssi = bluetoothLeDevice.getRssi();
        if (!TextUtils.isEmpty(tempName)) {
            addDevice(bluetoothLeDevice, tempName, tempRssi);
        } else {
            addDevice(bluetoothLeDevice, "", tempRssi);
        }
        onDeviceFound(bluetoothLeDeviceStore);
    }

    private void addDevice(BluetoothLeDevice bluetoothLeDevice, String tempName, int tempRssi) {
        matcher = pattern.matcher(tempName);
        if (this.filterDeviceRssi < 0) {
            if (matcher.matches() && tempRssi >= this.filterDeviceRssi) {
                bluetoothLeDeviceStore.addDevice(bluetoothLeDevice);
            } else if (matcher.matches() && tempRssi < this.filterDeviceRssi) {
                bluetoothLeDeviceStore.removeDevice(bluetoothLeDevice);
            }
        } else {
            if (matcher.matches()) {
                bluetoothLeDeviceStore.addDevice(bluetoothLeDevice);
            }
        }
    }

    public abstract void onDeviceFound(BluetoothLeDeviceStore bluetoothLeDeviceStore);
//    public abstract void onDeviceFound(BluetoothLeDevice bluetoothLeDevice);

    public abstract void scanTimeout();
}
