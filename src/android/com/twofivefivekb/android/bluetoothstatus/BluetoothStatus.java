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

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;

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
import java.util.List;

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
    
    BluetoothGatt sgatt; 
    BluetoothGattCharacteristic mDataMDLP;                        //The BLE characteristic used for MLDP data transfers
    static final String MLDP_PRIVATE_SERVICE = "00035b03-58e6-07dd-021a-08123a000300"; //Private service for Microchip MLDP
    static final String MLDP_DATA_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a000301"; //Characteristic for MLDP Data, properties - notify, write
    static final String MLDP_CONTROL_PRIVATE_CHAR = "00035b03-58e6-07dd-021a-08123a0003ff"; //Characteristic for MLDP Control, properties - read, write
    static final String CHARACTERISTIC_NOTIFICATION_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";	//Special UUID for descriptor needed to enable notifications

        
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
              if(args.getString(0).equals("BLconnect"))
              {
                if(sgatt!=null)
                {
                  sgatt.disconnect();
                  sgatt.close();
                  sgatt=null;
                  mDataMDLP=null;
                }
                log(args.getString(0));
                BluetoothDevice device= bluetoothAdapter.getRemoteDevice(args.getString(1));
                sgatt = device.connectGatt(this.cordova.getActivity().getApplicationContext(), false, mGattCallback);
              }
              if(args.getString(0).equals("BLrequestMtu"))
              {
                log(args.getString(0)+" "+Integer.parseInt(args.getString(1)));
                if(sgatt!=null)
                {
                  sgatt.requestMtu(Integer.parseInt(args.getString(1)));                                      // Attempt to discover services after successful connection.
                }
              }
              if(args.getString(0).equals("BLdiscoverServices"))
              {
                log(args.getString(0));
                if(sgatt!=null)
                {
                  sgatt.discoverServices();                                      // Attempt to discover services after successful connection.
                }
              }
              if(args.getString(0).equals("BLgetServices"))
              {
                log(args.getString(0));
                if(sgatt!=null)
                {
                  List<BluetoothGattService> gattServices=sgatt.getServices();
                  if (gattServices == null) {                                                     //Verify that list of GATT services is valid
                      log("BLgetServices found no Services");
                      return false;
                  }
                  try
                  {
                    String uuid;                                                                    //String to compare received UUID with desired known UUIDs
                    for (BluetoothGattService gattService : gattServices) {                         //Test each service in the list of services
                        uuid = gattService.getUuid().toString();                                    //Get the string version of the service's UUID
                        if (uuid.equals(MLDP_PRIVATE_SERVICE)) {                                    //See if it matches the UUID of the MLDP service 
                            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics(); //If so then get the service's list of characteristics
                            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) { //Test each characteristic in the list of characteristics
                                uuid = gattCharacteristic.getUuid().toString();                     //Get the string version of the characteristic's UUID
                                if (uuid.equals(MLDP_DATA_PRIVATE_CHAR)) {                          //See if it matches the UUID of the MLDP data characteristic
                                    mDataMDLP = gattCharacteristic;                                 //If so then save the reference to the characteristic 
                                    log("Found MLDP data characteristics");
                                } 
                                final int characteristicProperties = gattCharacteristic.getProperties(); //Get the properties of the characteristic
                                if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_NOTIFY)) > 0) { //See if the characteristic has the Notify property
                                    sgatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification in the BluetoothGatt
                                    BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables notification on the server
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE); //Set the value of the descriptor to enable notification
                                    sgatt.writeDescriptor(descriptor);                     //Write the descriptor
                                }
                                if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_INDICATE)) > 0) { //See if the characteristic has the Indicate property
                                    sgatt.setCharacteristicNotification(gattCharacteristic, true); //If so then enable notification (and indication) in the BluetoothGatt
                                    BluetoothGattDescriptor descriptor = gattCharacteristic.getDescriptor(UUID.fromString(CHARACTERISTIC_NOTIFICATION_CONFIG)); //Get the descripter that enables indication on the server
                                    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE); //Set the value of the descriptor to enable indication
                                    sgatt.writeDescriptor(descriptor);                     //Write the descriptor
                                }
                                if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE)) > 0) { //See if the characteristic has the Write (acknowledged) property
                                    gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); //If so then set the write type (write with acknowledge) in the BluetoothGatt
                                }
                                if ((characteristicProperties & (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) { //See if the characteristic has the Write (unacknowledged) property
                                    gattCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); //If so then set the write type (write with no acknowledge) in the BluetoothGatt
                                }
                            }
                            break;                                                                  //Found the MLDP service and are not looking for any other services
                        }
                    }
                    if (mDataMDLP == null) {                                                        //See if the MLDP data characteristic was not found
                      log("findMldpGattService found no MLDP service");
                      callbackContext.error("mDataMDLP not found!");
                    }
                    else{
                      callbackContext.success();
                    }
                  }
                  catch(Exception e)
                  {
                    callbackContext.error(e.toString());
                    log(e.toString());
                  }
                }
              }
                
                
              if(args.getString(0).equals("BLsend"))
              {
                log(args.getString(0));
                if(mDataMDLP!=null)
                {
                  new Thread(new Runnable() {
                    public void run() {
                      try
                      {
                        log(args.getString(0));

                        mDataMDLP.setValue(args.getString(1));                     //Set value of MLDP characteristic to send die roll information

                        if (sgatt.writeCharacteristic(mDataMDLP)) {                       //Request the BluetoothGatt to do the Write
                          callbackContext.success();
                        } 
                        else {
                          callbackContext.error("writeCharacteristic failed");
                          log("writeCharacteristic failed");
                        }

                      }
                      catch(Exception e)
                      {
                        callbackContext.error(e.toString());
                        log(e.toString());
                      }
                    }
                  }).start();
                  
                }
                else
                {
                  log("Not Open!");
                  callbackContext.error("Note open!");
                }
                
              }
                
              if(args.getString(0).equals("BLreceive"))
              {

              }
  
                
                
                
                
              if(args.getString(0).equals("BLclose"))
              {
                log(args.getString(0));
                if(sgatt!=null)
                {
                  sgatt.disconnect();
                  sgatt.close();
                  mDataMDLP=null;
                  sgatt=null;
                }
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
                        log("Bluetooth was disabled");

                        sendJS("javascript:cordova.plugins.BluetoothStatus.BTenabled = false;");
                        sendJS("javascript:cordova.plugins.BluetoothStatus.btevent('BluetoothStatus.disabled');");

                        break;
                    case BluetoothAdapter.STATE_ON:
                        log("Bluetooth was enabled");

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
    
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) { //Change in connection state
            log("onConnectionStateChange("+gatt+","+status+","+newState+")");
            if (newState == BluetoothProfile.STATE_CONNECTED) {                         //See if we are connected
                log("BluetoothProfile.STATE_CONNECTED Connected to GATT server.");
            } 
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {                 //See if we are not connected
                log("BluetoothProfile.STATE_DISCONNECTED Connected to GATT server.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {              //Service discovery complete
            log("onServicesDiscovered("+gatt+","+status+")");
            if (status == BluetoothGatt.GATT_SUCCESS && sgatt != null) {       //See if the service discovery was successful
                log("BluetoothGatt.GATT_SUCCESS Connected to GATT server.");
            } 
            else {                                                                      //Service discovery was not successful
                log("BluetoothGatt.GATT_SUCCESS onServicesDiscovered received: " + status);
            }
        }

        //For information only. This application uses Indication to receive updated characteristic data, not Read
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Read has completed
            log("onCharacteristicRead("+gatt+","+status+","+characteristic+","+status+")");                                                   //Record that the write has completed
            if (status == BluetoothGatt.GATT_SUCCESS) {                                 //See if the read was successful
              log("onCharacteristicRead =>  "+characteristic.getStringValue(0));
            }
        }

        //For information only. This application sends small packets infrequently and does not need to know what the previous write completed
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) { //A request to Write has completed
            log("onCharacteristicWrite("+gatt+","+characteristic+","+status+")");                                                   //Record that the write has completed
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) { //Indication or notification was received
            log("onCharacteristicWrite("+gatt+","+characteristic+")");                                                   //Record that the write has completed
            log("onCharacteristicChanged =>  "+characteristic.getStringValue(0));
        }
        
        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status){
            log("onCharacteristicWrite("+gatt+","+mtu+","+status+")");                                                   //Record that the write has completed
        }
    };
        
}
