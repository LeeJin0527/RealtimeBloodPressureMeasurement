/**
 * application이 처음으로 구동 될 때 시작될 activity입니다.
 * scanning된 device목록을 리스트 형식으로 보여줍니다.
 *
 * todo : 특정 device만 scanning되도록 하는게 좋을듯(service uuid로 filter?)
 *
 *
 */
package com.example.heartbeat;

import android.app.Activity;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SnapHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter mBLEadapter;
    private boolean mScanning;
    private Handler mHandler;

    private ArrayList<BluetoothDevice> mBLElist = null;

    private BLEDataAdapter mBLEDeviceAdapter = null;

    private RecyclerView mBLERecyclerView = null;

    private static final int REQUEST_ENABLE_BT = 1;

    private static final long SCAN_PERIOD = 10000;
    //15초 뒤에 스캔 중지


    ScanFilter filter = null;
    ScanSettings scanSettings = null;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mHandler = new Handler();

        //해당 기기에서 BLE를 지원하는지 확인
        //지원하지 않는 다면 해당 activity 종료
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)){
            Toast.makeText(this, "BLE를 지원하지 않는 기기입니다!", Toast.LENGTH_SHORT).show();
            finish();
        }
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBLEadapter = bluetoothManager.getAdapter();
        if(mBLEadapter == null){
            Toast.makeText(this, "BLE를 지원하지 않는 기기입니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }



        setContentView(R.layout.mainactivity_recyclerview);
        mBLERecyclerView = findViewById(R.id.recyclerview);

    }
/*
    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.main, menu);
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        return true;
    }

*/
/*
    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        switch(item.getItemId()){
            case R.id.menu_scan:
                System.out.println("press menu scan!");
                mBLEDeviceAdapter.clear();
                mBLEDeviceAdapter.notifyDataSetChanged();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                System.out.println("press menu stop!");
                scanLeDevice(false);
                mScanning = false;
                break;
        }
        return true;
    }
*/


    @Override
    protected void onResume(){
        super.onResume();
        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBLEadapter.isEnabled()) {
            if (!mBLEadapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        mBLElist = new ArrayList<BluetoothDevice>();

        // Initializes recycler view adapter and onClickListener.
        mBLEDeviceAdapter = new BLEDataAdapter(mBLElist);
        mBLEDeviceAdapter.setItemClickListener(new BLEDataAdapter.ItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                Log.i("확인용", String.valueOf(position));
                final String BLEDeviceName = mBLElist.get(position).getName();
                final String BLEDeviceMAC = mBLElist.get(position).getAddress();

                final Intent intent = new Intent(view.getContext(), MenuActivity.class);
                intent.putExtra(BLEControlActivity.EXTRAS_DEVICE_NAME, BLEDeviceName);
                intent.putExtra(BLEControlActivity.EXTRAS_DEVICE_ADDRESS, BLEDeviceMAC);
                if (mScanning) {
                    mBLEadapter.stopLeScan(mLeScanCallback);
                    mScanning = false;
                }
                startActivity(intent);

            }
        });
        mBLERecyclerView.setAdapter(mBLEDeviceAdapter);

        mBLERecyclerView.setLayoutManager(new LinearLayoutManager(this, RecyclerView.HORIZONTAL,false));

        SnapHelper snapHelper = new PagerSnapHelper();
        if(mBLERecyclerView.getOnFlingListener() == null)snapHelper.attachToRecyclerView(mBLERecyclerView);

        mBLERecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener(){
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(mBLERecyclerView, newState);
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(mBLERecyclerView, dx, dy);
                LinearLayoutManager layoutManager =LinearLayoutManager.class.cast(mBLERecyclerView.getLayoutManager());
            }
        });

        scanLeDevice(true);


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }
    //todo : 이건 뭔지 모르겟음


    private void scanLeDevice(final boolean enable){

        List<ScanFilter> filters = new ArrayList<>();
        UUID myUUID = UUID.fromString("00000ffff-0000-1000-8000-00805f9b34fb");
        filter = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(myUUID)).build();
        //filter = new ScanFilter.Builder().setDeviceAddress("80:6F:B0:90:02:3C").build();
        //filter = new ScanFilter.Builder().setDeviceName("HSP2_2.7.0.1").build();
        filters.add(filter);

        scanSettings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();
/*
        if(enable){
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBLEadapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            }, SCAN_PERIOD);

            mScanning = true;
            //mBLEadapter.startLeScan(mLeScanCallback);
            //mBLEadapter.getBluetoothLeScanner().startScan(filters, scanSettings, mScanCallback);

        }

 */

        mBLEadapter.getBluetoothLeScanner().startScan(filters, scanSettings, mScanCallback);
        //mBLEadapter.getBluetoothLeScanner().startScan(mScanCallback);
        invalidateOptionsMenu();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            mBLEDeviceAdapter.addDevice(result.getDevice());
            mBLEDeviceAdapter.notifyDataSetChanged();

            Log.i("확인용","밖에 호출!");

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.i("확인용","배치 호출!");
            super.onBatchScanResults(results);
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.i("확인용","에러 호출!");
            Log.i("확인용", String.valueOf(errorCode));
            super.onScanFailed(errorCode);
        }
    };

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

                @Override
                public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mBLEDeviceAdapter.addDevice(device);
                            mBLEDeviceAdapter.notifyDataSetChanged();
                        }
                    });
                }
            };
}
