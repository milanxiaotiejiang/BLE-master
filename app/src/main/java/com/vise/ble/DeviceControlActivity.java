package com.vise.ble;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.vise.baseble.ViseBluetooth;
import com.vise.baseble.callback.IConnectCallback;
import com.vise.baseble.callback.data.ICharacteristicCallback;
import com.vise.baseble.common.State;
import com.vise.baseble.exception.BleException;
import com.vise.baseble.model.BluetoothLeDevice;
import com.vise.baseble.model.resolver.GattAttributeResolver;
import com.vise.baseble.utils.HexUtil;
import com.vise.log.ViseLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * 设备数据操作相关展示界面
 */
public class DeviceControlActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 101;

    private BluetoothSocket mBluetoothSocket;
    private InputStream mInputStream;
    private OutputStream mOutputStream;

    private static final String LIST_NAME = "NAME";
    private static final String LIST_UUID = "UUID";

    private SimpleExpandableListAdapter simpleExpandableListAdapter;
    private TextView mConnectionState;
    private TextView mGattUUID;
    private TextView mGattUUIDDesc;
    private TextView mDataAsString;
    private TextView mDataAsArray;
    private EditText mInput;
    private EditText mOutput;

    //设备信息
    private BluetoothLeDevice mDevice;
    //特征值
    private BluetoothGattCharacteristic mCharacteristic;
    //输出数据展示
    private StringBuilder mOutputInfo = new StringBuilder();
    //设备特征值集合
    private List<List<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<>();

    //发送队列，提供一种简单的处理方式，实际项目场景需要根据需求优化
    private Queue<byte[]> dataInfoQueue = new LinkedList<>();
    private Handler mHandler = new Handler();
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            send();
        }
    };

    private void send(byte[] data) {
        if (dataInfoQueue != null) {
            dataInfoQueue.clear();
            dataInfoQueue = splitPacketFor20Byte(data);
            handler.post(runnable);
        }
    }

    private void send() {
        if (dataInfoQueue != null && !dataInfoQueue.isEmpty()) {
            if (dataInfoQueue.peek() != null) {
                ViseBluetooth.getInstance().writeCharacteristic(mCharacteristic, dataInfoQueue.poll(), new ICharacteristicCallback() {
                    @Override
                    public void onSuccess(BluetoothGattCharacteristic characteristic) {
                        ViseLog.i("Send onSuccess!");
                    }

                    @Override
                    public void onFailure(BleException exception) {
                        ViseLog.i("Send onFail!");
                    }
                });
            }
            if (dataInfoQueue.peek() != null) {
                handler.postDelayed(runnable, 100);
            }
        }
    }

    /**
     * 数据分包
     *
     * @param data
     * @return
     */
    private Queue<byte[]> splitPacketFor20Byte(byte[] data) {
        Queue<byte[]> dataInfoQueue = new LinkedList<>();
        if (data != null) {
            int index = 0;
            do {
                byte[] surplusData = new byte[data.length - index];
                byte[] currentData;
                System.arraycopy(data, index, surplusData, 0, data.length - index);
                if (surplusData.length <= 20) {
                    currentData = new byte[surplusData.length];
                    System.arraycopy(surplusData, 0, currentData, 0, surplusData.length);
                    index += surplusData.length;
                } else {
                    currentData = new byte[20];
                    System.arraycopy(data, index, currentData, 0, 20);
                    index += 20;
                }
                dataInfoQueue.offer(currentData);
            } while (index < data.length);
        }
        return dataInfoQueue;
    }

    /**
     * 连接回调
     */
    private IConnectCallback connectCallback = new IConnectCallback() {
        @Override
        public void onConnectSuccess(BluetoothGatt gatt, int status) {
            ViseLog.i("Connect Success!");
            Toast.makeText(DeviceControlActivity.this, "Connect Success!", Toast.LENGTH_SHORT).show();
            mConnectionState.setText("true");
            invalidateOptionsMenu();
            if (gatt != null) {
                simpleExpandableListAdapter = displayGattServices(gatt.getServices());
            }
        }

        @Override
        public void onConnectFailure(BleException exception) {
            ViseLog.i("Connect Failure!");
            Toast.makeText(DeviceControlActivity.this, "Connect Failure!", Toast.LENGTH_SHORT).show();
            mConnectionState.setText("false");
            invalidateOptionsMenu();
            clearUI();
        }

        @Override
        public void onDisconnect() {
            ViseLog.i("Disconnect!");
            Toast.makeText(DeviceControlActivity.this, "Disconnect!", Toast.LENGTH_SHORT).show();
            mConnectionState.setText("false");
            invalidateOptionsMenu();
            clearUI();
        }
    };

    /**
     * 接收设备返回的数据回调
     */
    private ICharacteristicCallback bleCallback = new ICharacteristicCallback() {
        @Override
        public void onSuccess(BluetoothGattCharacteristic characteristic) {
            if (characteristic == null || characteristic.getValue() == null) {
                return;
            }
            ViseLog.i("notify success:" + HexUtil.encodeHexStr(characteristic.getValue()));
            mOutputInfo.append(HexUtil.encodeHexStr(characteristic.getValue())).append("\n");
            mOutput.setText(mOutputInfo.toString());
        }

        @Override
        public void onFailure(BleException exception) {
            if (exception == null) {
                return;
            }
            ViseLog.i("notify fail:" + exception.getDescription());
        }
    };

    private BroadcastReceiver paringReceived = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
//            BluetoothDevice btDevice = ViseBluetooth.getInstance().getBluetoothAdapter().getRemoteDevice(blueAddress);

            try {
//                boolean ret = ClsUtils.setPin(btDevice.getClass(), btDevice, "1234");
//                ClsUtils.setPin(btDevice.getClass(), btDevice, "000000");
//                ClsUtils.cancelPairingUserInput(btDevice.getClass(), btDevice);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private BroadcastReceiver searchDevices = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Bundle b = intent.getExtras();
            Object[] lstName = b.keySet().toArray();

            // 显示所有收到的消息及其细节
            for (int i = 0; i < lstName.length; i++) {
                String keyName = lstName[i].toString();
                ViseLog.e("bluetooth", keyName + ">>>" + String.valueOf(b.get(keyName)));
            }
            final BluetoothDevice device;
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action)) {
                device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING://正在配对
                        ViseLog.d("BlueToothTestActivity", "正在配对......");
                        break;
                    case BluetoothDevice.BOND_BONDED://配对结束
                        ViseLog.d("BlueToothTestActivity", "完成配对");
                        Toast.makeText(DeviceControlActivity.this, "完成配对!", Toast.LENGTH_SHORT).show();
                        ViseBluetooth.getInstance().setState(State.CONNECT_SUCCESS);
                        mDevice.setPair(true);

                        break;
                    case BluetoothDevice.BOND_NONE://取消配对/未配对
                        ViseLog.d("BlueToothTestActivity", "取消配对");
                        Toast.makeText(DeviceControlActivity.this, "取消配对!", Toast.LENGTH_SHORT).show();
                        ViseBluetooth.getInstance().setState(State.CONNECT_FAILURE);
                        mDevice.setPair(false);
                    default:
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_control);
        init();
    }

    private void init() {
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mGattUUID = (TextView) findViewById(R.id.uuid);
        mGattUUIDDesc = (TextView) findViewById(R.id.description);
        mDataAsString = (TextView) findViewById(R.id.data_as_string);
        mDataAsArray = (TextView) findViewById(R.id.data_as_array);
        mInput = (EditText) findViewById(R.id.input);
        mOutput = (EditText) findViewById(R.id.output);

        mDevice = getIntent().getParcelableExtra(DeviceDetailActivity.EXTRA_DEVICE);
        if (mDevice != null) {
            ((TextView) findViewById(R.id.device_address)).setText(mDevice.getAddress());
        }

        findViewById(R.id.select_write_characteristic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGattServices();
            }
        });
        findViewById(R.id.select_notify_characteristic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGattServices();
            }
        });
        findViewById(R.id.select_read_characteristic).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showGattServices();
            }
        });
        findViewById(R.id.send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mCharacteristic == null) {
                    Toast.makeText(DeviceControlActivity.this, "Please select enable write characteristic!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mInput.getText() == null || mInput.getText().toString() == null) {
                    Toast.makeText(DeviceControlActivity.this, "Please input command!", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isHexData(mInput.getText().toString())) {
                    Toast.makeText(DeviceControlActivity.this, "Please input hex data command!", Toast.LENGTH_SHORT).show();
                    return;
                }
                send(HexUtil.decodeHex(mInput.getText().toString().toCharArray()));
            }
        });
        findViewById(R.id.send_notification).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                sendByBle(mInput.getText().toString());
                    checkBluetoothPermission();
            }
        });
        findViewById(R.id.btn_connect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connect(mDevice.getDevice());
            }
        });
    }

    private void checkBluetoothPermission(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            //校验是否已具有模糊定位权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                //具有权限
                sendFile();
            }
        } else {
            //系统不高于6.0直接执行
            sendFile();
        }
    }

    private void sendFile(){
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/news_article/juanfu.jpg";
        new SendInfoTask(mBluetoothSocket).execute(path);
    }

    private void sendByBle(String send_et) {
        if (TextUtils.isEmpty(send_et))
            return;

        byte[] buff = send_et.getBytes();
        int len = buff.length;
        int[] lens = dataSeparate(len);
        if (lens[1] != 0) {
            String str = new String(buff, 20 * lens[0], lens[1]);
            ViseBluetooth.getInstance().writeCharacteristic(mCharacteristic, str, new ICharacteristicCallback() {
                @Override
                public void onSuccess(BluetoothGattCharacteristic characteristic) {
                    ViseLog.i("Send onSuccess!");
                }

                @Override
                public void onFailure(BleException exception) {
                    ViseLog.i("Send onFail!");
                }
            });
        }
    }

    public int[] dataSeparate(int len) {
        int[] lens = new int[2];
        lens[0] = len / 20;
        lens[1] = len - 20 * lens[0];
        return lens;
    }

    @Override
    protected void onResume() {
        super.onResume();

//        ViseBluetooth.getInstance().connect(mDevice, false, connectCallback);
        register();

    }

    private void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        filter.setPriority(Integer.MAX_VALUE);
//        registerReceiver(paringReceived, filter);

        IntentFilter intent = new IntentFilter();
        intent.addAction(BluetoothDevice.ACTION_FOUND);//搜索发现设备
        intent.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);//状态改变
        intent.addAction(BluetoothAdapter.ACTION_SCAN_MODE_CHANGED);//行动扫描模式改变了
        intent.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);//动作状态发生了变化
        registerReceiver(searchDevices, intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        ViseBluetooth.getInstance().disconnect();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ViseBluetooth.getInstance().clear();
//        unregisterReceiver(paringReceived);
        unregisterReceiver(searchDevices);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.connect, menu);
        if (ViseBluetooth.getInstance().isConnected()) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        if (ViseBluetooth.getInstance().getState() == State.CONNECT_PROCESS) {
            menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_progress_indeterminate);
        } else {
            menu.findItem(R.id.menu_refresh).setActionView(null);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_connect://连接设备
                invalidateOptionsMenu();
                if (!ViseBluetooth.getInstance().isConnected()) {
//                    ViseBluetooth.getInstance().connect(mDevice, false, connectCallback);
                    int rssi = mDevice.getFirstRssi();
                    if (rssi > 0) {
                        if(mDevice.isPair()){
                            ViseBluetooth.getInstance().removeBond(mDevice.getDevice().getClass(), mDevice.getDevice());
                        }else{
                            ViseBluetooth.getInstance().createBond(mDevice.getDevice().getClass(), mDevice.getDevice());
                        }
                    } else {
                        ViseBluetooth.getInstance().connect(mDevice, false, connectCallback);
                    }
                }
                break;
            case R.id.menu_disconnect://断开设备
                invalidateOptionsMenu();
                if (ViseBluetooth.getInstance().isConnected()) {
                    ViseBluetooth.getInstance().disconnect();
                }
                break;
        }
        return true;
    }

    private void connect(final BluetoothDevice device) {
        new Thread(new Runnable() {

            @Override
            public void run() {
                ViseBluetooth.getInstance().getBluetoothAdapter().cancelDiscovery();

                BluetoothDevice bluetoothDevice = ViseBluetooth.getInstance().getBluetoothAdapter().getRemoteDevice(device.getAddress());
                try {
                    //通过和服务器协商的uuid来进行连接     0000ffe1-0000-1000-8000-00805f9b34fb
//                    mBluetoothSocket = bluetoothDevice.createRfcommSocketToServiceRecord(UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb"));
                    mBluetoothSocket =(BluetoothSocket) bluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device, 2);

                    //通过反射得到bltSocket对象，与uuid进行连接得到的结果一样，但这里不提倡用反射的方法
//            mBluetoothSocket = (BluetoothSocket) device.getClass().getMethod("createRfcommSocket", new Class[]{int.class}).invoke(device, 1);

                    if (mBluetoothSocket != null)
                        mBluetoothSocket.connect();
                    if (mBluetoothSocket != null) {
                        mInputStream = mBluetoothSocket.getInputStream();
                        mOutputStream = mBluetoothSocket.getOutputStream();

                    }

                } catch (IOException e) {

                    try {

//                        mBluetoothSocket =(BluetoothSocket) bluetoothDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(device,1);
//                        mBluetoothSocket.connect();

                    }
                    catch (Exception e2) {
                        e2.printStackTrace();
                    }


                    try {
                        if (mInputStream != null)
                            mInputStream.close();
                        if (mBluetoothSocket != null) {
                            mBluetoothSocket.close();
                            mBluetoothSocket = null;
                        }
                    } catch (Exception e2) {
                        // TODO: handle exception
                    }
                    e.printStackTrace();
                } catch (NoSuchMethodException e) {
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }


    /**
     * 根据GATT服务显示该服务下的所有特征值
     *
     * @param gattServices GATT服务
     * @return
     */
    private SimpleExpandableListAdapter displayGattServices(final List<BluetoothGattService> gattServices) {
        if (gattServices == null) return null;
        String uuid;
        final String unknownServiceString = getResources().getString(R.string.unknown_service);
        final String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        final List<Map<String, String>> gattServiceData = new ArrayList<>();
        final List<List<Map<String, String>>> gattCharacteristicData = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (final BluetoothGattService gattService : gattServices) {
            final Map<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, GattAttributeResolver.getAttributeName(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            final List<Map<String, String>> gattCharacteristicGroupData = new ArrayList<>();
            // 从当前循环所指向的服务中读取特征值列表
            final List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            final List<BluetoothGattCharacteristic> charas = new ArrayList<>();

            // Loops through available Characteristics.
            for (final BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                final Map<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(LIST_NAME, GattAttributeResolver.getAttributeName(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }

            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        final SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(this, gattServiceData, android.R.layout
                .simple_expandable_list_item_2, new String[]{LIST_NAME, LIST_UUID}, new int[]{android.R.id.text1, android.R.id.text2},
                gattCharacteristicData, android.R.layout.simple_expandable_list_item_2, new String[]{LIST_NAME, LIST_UUID}, new
                int[]{android.R.id.text1, android.R.id.text2});
        return gattServiceAdapter;
    }

    private void showInfo(String uuid, byte[] dataArr) {
        mGattUUID.setText(uuid != null ? uuid : getString(R.string.no_data));
        mGattUUIDDesc.setText(GattAttributeResolver.getAttributeName(uuid, getString(R.string.unknown)));
        mDataAsArray.setText(HexUtil.encodeHexStr(dataArr));
        mDataAsString.setText(new String(dataArr));
    }

    private void clearUI() {
        mGattUUID.setText(R.string.no_data);
        mGattUUIDDesc.setText(R.string.no_data);
        mDataAsArray.setText(R.string.no_data);
        mDataAsString.setText(R.string.no_data);
        mInput.setText("");
        mOutput.setText("");
        ((EditText) findViewById(R.id.show_write_characteristic)).setText("");
        ((EditText) findViewById(R.id.show_notify_characteristic)).setText("");
        mOutputInfo = new StringBuilder();
        simpleExpandableListAdapter = null;
    }

    /**
     * 显示GATT服务展示的信息
     */
    private void showGattServices() {
        if (simpleExpandableListAdapter == null) {
            return;
        }
        final AlertDialog.Builder builder = new AlertDialog.Builder(DeviceControlActivity.this);
        View view = LayoutInflater.from(DeviceControlActivity.this).inflate(R.layout.item_gatt_services, null);
        ExpandableListView expandableListView = (ExpandableListView) view.findViewById(R.id.dialog_gatt_services_list);
        expandableListView.setAdapter(simpleExpandableListAdapter);
        builder.setView(view);
        final AlertDialog dialog = builder.show();
        expandableListView.setOnChildClickListener(new ExpandableListView.OnChildClickListener() {
            @Override
            public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
                dialog.dismiss();
                final BluetoothGattCharacteristic characteristic = mGattCharacteristics.get(groupPosition).get(childPosition);
                final int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {

                    mCharacteristic = characteristic;
                    ((EditText) findViewById(R.id.show_write_characteristic)).setText(characteristic.getUuid().toString());
                } else if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {

                    ViseBluetooth.getInstance().readCharacteristic(characteristic, new ICharacteristicCallback() {
                        @Override
                        public void onSuccess(final BluetoothGattCharacteristic characteristic) {
                            if (characteristic == null) {
                                return;
                            }
                            ViseLog.i("readCharacteristic onSuccess:" + HexUtil.encodeHexStr(characteristic.getValue()));
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    showInfo(characteristic.getUuid().toString(), characteristic.getValue());
                                }
                            });
                        }

                        @Override
                        public void onFailure(BleException exception) {
                            if (exception == null) {
                                return;
                            }
                            ViseLog.i("readCharacteristic onFailure:" + exception.getDescription());
                        }
                    });

//                    if (characteristic.getUuid().toString().equals("0000ffe1-0000-1000-8000-00805f9b34fb")) {
//                        ViseBluetooth.getInstance().getBluetoothGatt().setCharacteristicNotification(characteristic, true);
//                        BluetoothGattDescriptor clientConfig = characteristic.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"));
//                        clientConfig.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//                        ViseBluetooth.getInstance().getBluetoothGatt().writeDescriptor(clientConfig);
//
//                    }
                    mCharacteristic = characteristic;

                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    ((EditText) findViewById(R.id.show_notify_characteristic)).setText(characteristic.getUuid().toString());
                    ViseBluetooth.getInstance().enableCharacteristicNotification(characteristic, bleCallback, false);
                } else if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    ((EditText) findViewById(R.id.show_notify_characteristic)).setText(characteristic.getUuid().toString());
                    ViseBluetooth.getInstance().enableCharacteristicNotification(characteristic, bleCallback, true);
                }
                return true;
            }
        });
    }

    private boolean isHexData(String str) {
        if (str == null) {
            return false;
        }
        char[] chars = str.toCharArray();
        if ((chars.length & 1) != 0) {//个数为奇数，直接返回false
            return false;
        }
        for (char ch : chars) {
            if (ch >= '0' && ch <= '9') continue;
            if (ch >= 'A' && ch <= 'F') continue;
            if (ch >= 'a' && ch <= 'f') continue;
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //同意权限
                sendFile();
            } else {
                // 权限拒绝，提示用户开启权限
                finish();
            }
        }
    }
}
