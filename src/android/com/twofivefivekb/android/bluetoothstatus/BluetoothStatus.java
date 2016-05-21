package com.twofivefivekb.android.bluetoothstatus;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.util.Log;
import android.os.ParcelUuid;

import java.lang.Exception;
import java.util.UUID;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.Arrays;

public class BluetoothStatus extends CordovaPlugin {
    private static CordovaWebView mwebView;
    private static CordovaInterface mcordova;

    private static final String LOG_TAG = "BluetoothStatus";
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    
    private String pin="";
    
    BluetoothSocket socked=null;
    OutputStream o;
    InputStream i;
        
    @Override
    public boolean execute(final String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if (action.equals("enableBT")) {
            enableBT();
            return true;
        } else if (action.equals("promptForBT")) {
            promptForBT();
            return true;
        } else if(action.equals("initPlugin")) {
            initPlugin();
            return true;
        }
        else if(action.equals("makeVisible")) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
            mcordova.getActivity().startActivity(intent);
            return true;
        }
        else if(action.equals("setName")) {
            bluetoothAdapter.setName(args.getString(0));
            return true;
        }
        else if(action.equals("bt")) {
            try
            {
              if(args.getString(0).equals("startDiscovery"))
              {
                log(args.getString(0));
                bluetoothAdapter.startDiscovery();          
                callbackContext.success();
              }
              if(args.getString(0).equals("cancelDiscovery"))
              {
                log(args.getString(0));
                bluetoothAdapter.cancelDiscovery();                
                callbackContext.success();
              }
              if(args.getString(0).equals("finduuids"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                device.fetchUuidsWithSdp();
                callbackContext.success();
              }
              if(args.getString(0).equals("getuuids"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                JSONArray json = new JSONArray();
                for(ParcelUuid u: device.getUuids())
                {
                  log(u.toString());
                  json.put(u.toString());
                }              
                callbackContext.success(json);
              }
              if(args.getString(0).equals("pair"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                pin=args.getString(2);
                device.createBond();
                callbackContext.success();
              }
              if(args.getString(0).equals("connect"))
              {
                if(socked!=null)
                {
                  socked.close();
                  socked=null;
                }
                new Thread(new Runnable() {
                  public void run() {
                    try
                    {
                      log(args.getString(0));
                      BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                      UUID u = UUID.fromString(args.getString(2));
                      socked= device.createInsecureRfcommSocketToServiceRecord(u);
                      socked.connect();
                      o =socked.getOutputStream();
                      i= socked.getInputStream();
                      callbackContext.success();
                    }
                    catch(Exception e)
                    {
                      callbackContext.error(e.toString());
                      log(e.toString());
                    }
                  }
                }).start();
              }
              if(args.getString(0).equals("isConnected"))
              {
                log(args.getString(0));
                boolean b=false;
                if(socked!=null)
                {
                  b=socked.isConnected();
                }  
                log(""+b);
                callbackContext.success(""+b);
              }
              
              if(args.getString(0).equals("send"))
              {
                log(args.getString(0));
                o.write(args.getString(1).getBytes());
                callbackContext.success();
              }
              if(args.getString(0).equals("receive"))
              {
                log(args.getString(0));
                String s="";
                while(i.available()>0)
                {
                  byte[] b= new byte[1];
                  i.read(b);
                  s+=""+((char)b[0]);
                }
                log(s);
                callbackContext.success(s);
              }
              if(args.getString(0).equals("close"))
              {
                log(args.getString(0));
                if(socked!=null)
                {
                  socked.close();
                  socked=null;
                }
                callbackContext.success();
              }
    
              if(args.getString(0).equals("state"))
              {
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                log("getAddress() "+device.getAddress());
                log("getBondState() "+device.getBondState());
                log("getName() "+device.getName());
                log("getType() "+device.getType());
                log("toString() "+device.toString());
                JSONObject json = new JSONObject();
                json.put("Address",""+device.getAddress());
                json.put("Bond",""+device.getBondState());
                json.put("Name",""+device.getName());
                json.put("Type",""+device.getType());
                callbackContext.success(json);
              }
            }
            catch(Exception e)
            {
              callbackContext.error(e.toString());
              log(e.toString());
            }
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mcordova.getActivity().unregisterReceiver(mReceiver);
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);

        mwebView = super.webView;
        mcordova = cordova;

        bluetoothManager = (BluetoothManager) webView.getContext().getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();

        // Register for broadcasts on BluetoothAdapter state change
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        filter.addAction(BluetoothDevice.ACTION_UUID);
        
        filter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);

        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);

        mcordova.getActivity().registerReceiver(mReceiver, filter);
    }

    private void enableBT() {
        //enable bluetooth without prompting
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "Bluetooth is not supported");
        } else if (!bluetoothAdapter.isEnabled()) {
            bluetoothAdapter.enable();
        }
    }

    private void promptForBT() {
        //prompt user for enabling bluetooth
        Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        mcordova.getActivity().startActivity(enableBTIntent);
    }

    private void initPlugin() {
        //test if B supported
        if (bluetoothAdapter == null) {
            Log.e(LOG_TAG, "Bluetooth is not supported");
        } else {
            Log.e(LOG_TAG, "Bluetooth is supported");

            sendJS("javascript:cordova.plugins.BluetoothStatus.hasBT = true;");

            //test if BLE supported
            if (!mcordova.getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                Log.e(LOG_TAG, "BluetoothLE is not supported");
            } else {
                Log.e(LOG_TAG, "BluetoothLE is supported");
                sendJS("javascript:cordova.plugins.BluetoothStatus.hasBTLE = true;");
            }

            //test if BT enabled
            if (bluetoothAdapter.isEnabled()) {
                Log.e(LOG_TAG, "Bluetooth is enabled");

                sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = true;");
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('BluetoothStatus.enabled');");
            } else {
                Log.e(LOG_TAG, "Bluetooth is not enabled");
            }
        }
    }

    private void sendJS(final String js) {
        mcordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mwebView.loadUrl(js);
            }
        });
    }

    private void log(final String log) {
        sendJS("javascript:console.log('"+log+"');");
    }

    //broadcast receiver for BT intent changes
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_OFF:
                        Log.e(LOG_TAG, "Bluetooth was disabled");

                        sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = false;");
                        sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('BluetoothStatus.disabled');");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        Log.e(LOG_TAG, "Bluetooth was enabled");

                        sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = true;");
                        sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('BluetoothStatus.enabled');");

                        break;
                }
            }
            
            if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+"DeviceList " + device.getName() + "\n" + device.getAddress());
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.found','"+device.getAddress()+"');");
            }
            
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                log(action+" "+"Discovery Finished");
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.discovery_finised');");
            }
            
            if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_STARTED)) {
                log(action+" "+"Discovery Started");
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.discovery_started');");
            }

            if (action.equals(BluetoothDevice.ACTION_UUID)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+device.getName());
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.UUID');");
            }

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+device.getName()+" "+device.getBondState());
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.bound');");
            }
            
            if (action.equals(BluetoothDevice.ACTION_PAIRING_REQUEST)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+device.getName()+" "+device.getBondState());
                log(Arrays.toString(pin.getBytes()));
                boolean b= device.setPin(pin.getBytes());
                log("pin "+b);
            }

            if (action.equals(BluetoothDevice.ACTION_ACL_CONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+device.getName()+" "+device.getName()+" "+device.getBondState());
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.connected');");
            }
            if (action.equals(BluetoothDevice.ACTION_ACL_DISCONNECTED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                log(action+" "+device.getName()+" "+device.getName()+" "+device.getBondState());
                sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('bl.disconnected');");
            }
            
        }
    };
        
}
