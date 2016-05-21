var exec = require('cordova/exec');

var BluetoothStatus = function() {
};

BluetoothStatus.initPlugin = function() {
    //wait for device to be ready
    document.addEventListener("deviceready", function () {
        exec(null, null, "BluetoothStatus", "initPlugin", []);
    }, false);
};

BluetoothStatus.enableBT = function() {
    exec(null, null, "BluetoothStatus", "enableBT", []);
};

BluetoothStatus.promptForBT = function() {
    exec(null, null, "BluetoothStatus", "promptForBT", []);
};

BluetoothStatus.makeVisible = function() {
    exec(null, null, "BluetoothStatus", "makeVisible", []);
};
;

BluetoothStatus.setName = function() {
    exec(null, null, "BluetoothStatus", "setName", []);
};

BluetoothStatus.bt = function(sucess,error) {
    exec(sucess, error, "BluetoothStatus", "bt", Array.prototype.slice.call(arguments,2));
}
BluetoothStatus.eventlist=[];
BluetoothStatus.btaddevent = function(type,callback) {
    BluetoothStatus.eventlist.push([type,callback]);
}

BluetoothStatus.btevent = function(type,data){
    BluetoothStatus.eventlist.forEach(function (event){
        if(event[0]==type){
            event[1](data);
        }        
    });    
}

BluetoothStatus.hasBT = false;
BluetoothStatus.hasBTLE = false;
BluetoothStatus.BTenabled = false;
BluetoothStatus.iosAuthorized = true;

module.exports = BluetoothStatus;
