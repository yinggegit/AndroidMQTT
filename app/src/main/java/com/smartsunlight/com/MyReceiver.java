package com.smartsunlight.com;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if(intent.getAction().equals("com.example.androidtest.receiver")){
            Log.e("MyReceiver","start");
            Intent sevice = new Intent(context, MQTTService.class);
            context.startService(sevice);
        }
    }
}
