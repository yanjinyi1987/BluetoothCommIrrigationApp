package geekband.yanjinyi1987.com.bluetoothcomm;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import geekband.yanjinyi1987.com.bluetoothcomm.fragment.BluetoothConnection;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    public static final int DEVICE_CONNECTED = 1;
    public static final int MESSAGE_READ=0;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean noBluetooth;
    private boolean bluetoothDisable;
    private static final int REQUEST_ENABLE_BT=1;
    private ArrayList<String> mConnectedBTDevices = new ArrayList<>();
    private boolean rwReady=false;

    //与fragmentDialog通信的Handler
    private Handler mMainActivityHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            BluetoothDevice mBluetoothDevice = null;
            BluetoothSocket mBluetoothSocket=null;
            switch(msg.what) {
                case DEVICE_CONNECTED:
                    mBluetoothDevice = (BluetoothDevice) (((ArrayList<Object>) msg.obj).get(0));
                    mBluetoothSocket = (BluetoothSocket) (((ArrayList<Object>) msg.obj).get(1));

                    //建立读写通道哦！UI主线程与读写线程的交互
                    mSSPRWThread = new SSPRWThread(mBluetoothSocket);
                    mSSPRWThread.start();
                    //将功能区使能
                    rwReady=true;
                    break;
                default:
                    break;
            }
        }
    };

    //与读写线程通信的Handler
    private Handler mReadSSPHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what) {
                case MESSAGE_READ:
                    int length = msg.arg1;
                    byte[] info =(byte[])(msg.obj); //问题在于这里直接引用了buffer，而buffer的数据会被read覆盖，这样造成了问题。
                    StringBuilder stringBuilder = new StringBuilder();
                    for(int i=0;i<length;i++) {
                        Log.i("byte in handler",String.valueOf((char)info[i]));
                        stringBuilder.append((char)info[i]);
                    }
                    String strInfo = stringBuilder.toString();
                    Log.i("MainActivity",strInfo);
                    mReceivedSPPDataText.setText(mReceivedSPPDataText.getText()+strInfo);
                    break;
                default:
                    break;
            }
        }
    };

    //处理蓝牙接收器的状态变化
    /**
     * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter.html#STATE_TURNING_ON
     * int STATE_OFF : Indicates the local Bluetooth adapter is off.
     * int STATE_ON  : Indicates the local Bluetooth adapter is on, and ready for use.
     * int STATE_TURNING_OFF : Indicates the local Bluetooth adapter is turning off.
     *                         Local clients should immediately attempt graceful disconnection of any remote links.
     *int STATE_TURNING_ON: Indicates the local Bluetooth adapter is turning on.
     *                      However local clients should wait for STATE_ON before attempting to use the adapter.
     */
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
                int state_previous = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE,-1);
                switch (state) {
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        mConnectedBTDevices.clear(); //清除连接列表
                        //关闭读写通道
                        if(rwReady==true) {
                            mSSPRWThread.cancel();
                        }
                        //disable功能区
                        rwReady=false;
                        break;
                    case BluetoothAdapter.STATE_ON:

                        break;
                    default:
                        break;
                }
            }
        }
    };
    private Button mBTConnectionButton;
    private EditText mAtCommandText;
    private Button mSendAtCommandButton;
    private EditText mReceivedSPPDataText;
    private SSPRWThread mSSPRWThread;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch(requestCode) {
            case REQUEST_ENABLE_BT:
                if(resultCode==RESULT_OK) {
                    //蓝牙设备已经被使能了，then do the job，paired or discovery and then connecting，看sample我们
                    //需要做一个listview来实现这一点。
                    /*
                    mBTConnectionButton.setEnabled(false);
                    //先打开系统自带的蓝牙设置界面来配对和连接蓝牙，有时间再自己写一个DialogFragment的例子
                    Intent settingsIntent = new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
                    //但是打开的这个Activity好像只有显示配对和查找配对设备的功能，没有连接的功能哦。
                    startActivity(settingsIntent);
                    */
                    if(noBluetooth==false) {
                        callBtConnectionDialog();
                    }
                    else {
                        try {
                            throw(new Exception("程序不可能运行到这里"));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                else if(resultCode == RESULT_CANCELED) {
                    //蓝牙设备没有被使能
                }
                else {
                    //不可能到这里来
                    Toast.makeText(this,"Error！",Toast.LENGTH_LONG).show();
                }
                break;
            default:
                break;
        }
    }

    void callBtConnectionDialog() {
        BluetoothConnection btDialog = BluetoothConnection.newInstance(mBluetoothAdapter,mConnectedBTDevices,mMainActivityHandler);
        btDialog.show(getFragmentManager(), "蓝牙设置");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        //添加IntentFilter来监听Bluetooth的状态变化
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        //注册接受器
        registerReceiver(mBroadcastReceiver,intentFilter);
    }

    @Override
    protected void onDestroy() {
        Log.i("MainActivity","onDestroy");
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);
        if(rwReady==true) {
            mConnectedBTDevices.clear();
            mSSPRWThread.cancel();
        }
    }

    /*
        Bluetooth init
         */
    void initBluetooth() {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            //设备不支持蓝牙
            Toast.makeText(this,"您的设备不支持蓝牙",Toast.LENGTH_LONG).show();
            noBluetooth = true;
            return;
        }
        //蓝牙设备是存在的
        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT); //对应onActivityResult
        }
        else {
            callBtConnectionDialog();
        }
    }
    /*
    关于白天黑夜 ，春夏秋冬什么的，好像是可以通过手机上的时间或者网络获取的
    需要选择的只是植物的种类
    在传递较多数据时，应该对这些数据做校验以确保传输正确。
     */
    void initViews()
    {
        mBTConnectionButton = (Button) findViewById(R.id.connect_bt_device);
        mAtCommandText = (EditText) findViewById(R.id.AT_command_text);
        mReceivedSPPDataText = (EditText) findViewById(R.id.received_SPP_data_text);
        mSendAtCommandButton = (Button) findViewById(R.id.send_AT_command);

        mBTConnectionButton.setOnClickListener(this);
        mSendAtCommandButton.setOnClickListener(this);
    }
    //接受传回的结果肯定是异步的哦！
    @Override
    public void onClick(View v) {
        switch(v.getId()) {
            case R.id.connect_bt_device:
                initBluetooth();
                break;
            case R.id.send_AT_command:
                String at_command = mAtCommandText.getText().toString();
                if(at_command!=null && at_command.length()>0) {
                    //发送命令
                    mSSPRWThread.write(at_command.getBytes());
                }
                break;
            default:
                break;
        }

    }

    private class SSPRWThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public SSPRWThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            StringBuilder stringBuilder = new StringBuilder();
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer); //这种问题应该怎么处理呢？
                    // Send the obtained bytes to the UI activity
                    mReadSSPHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();
                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) { }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}
