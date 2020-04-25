package com.smartsunlight.com;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.greenrobot.eventbus.EventBus;

public class MQTTService extends Service{
    public static final String TAG = "MQTTService";
    private static MqttAndroidClient client;
    private MqttConnectOptions conOpt;

    private String host = "tcp://192.168.230.1:1883";
    //private String host = "tcp://192.168.1.10:1883";
	
    //private String userName = "amdin";
    //private String passWord ="123456";

    //private String host = "tcp://lora.smartkit.io:1881";
  
    private String userName = "ying"; /*Useless, except you config*/
    private String passWord ="ying";/*Useless, except you config*/

    private String clientId ="";
    private MyReceiver myReceiver;
    private static boolean isCloseService=false;

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        init();
        return super.onStartCommand(intent, flags, startId);
    }

    public static void publish(String msg,String topic){

        //   String topic = myTopic;
        Integer qos = 2;
        Boolean retained = false;
		
        try {
            client.publish(topic, msg.getBytes(), qos.intValue(), retained.booleanValue());
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public static boolean subscribe(String[] topicName, int qos[]){
        boolean flag = false;
        if (client != null && client.isConnected()) {
            try {

                client.subscribe(topicName, qos, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.e("Subscribed","Subscribed");
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e("Failed to subscribe","Failed to subscribe");

                    }
                });

                flag = true;
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
        else {

        }
        return flag;
    }
    private void init() {
        // 服务器地址（协议+地址+端口号）
        String uri = host;

        client = new MqttAndroidClient(getApplicationContext(), uri, clientId);

        // 设置MQTT监听并且接受消息
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                //断开连接必须重新订阅才能收到消息
                if(reconnect){
                    MQTTConMessage msg = new MQTTConMessage();
                    msg.setMessage("connect");
                    Log.e("重新订阅成功", "重新订阅成功 ");
                    EventBus.getDefault().postSticky(msg);
                }

            }

            @Override
            public void connectionLost(Throwable cause) {
                Log.e("连接失败", "重连"+cause);
                doClientConnection();

            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String str2 = topic + ";qos:" + message.getQos() + ";retained:" + message.isRetained();
                Log.e(TAG, str2);
                String str1 = new String(message.getPayload());
                Log.e(TAG, "收到消息:" + str1);
                MQTTMessage msg = new MQTTMessage();
                msg.setMessage(str1);
                msg.setTopic(topic);
                Log.e("主题", topic);
                EventBus.getDefault().postSticky(msg);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        conOpt = new MqttConnectOptions();
        conOpt.setAutomaticReconnect(true);
        // 清除缓存
        conOpt.setCleanSession(true);
        // 设置超时时间，单位：秒
        conOpt.setConnectionTimeout(60);
        // 心跳包发送间隔，单位：秒
        conOpt.setKeepAliveInterval(5);
        // 用户名
        conOpt.setUserName(userName);
        // 密码
        conOpt.setPassword(passWord.toCharArray());


        myReceiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(myReceiver, filter);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
        try {
            if(client!=null){
                client.disconnect();
                client.unregisterResources();
            }

        } catch (MqttException e) {
            e.printStackTrace();
        }
        if(isCloseService){
            isCloseService=false;
            //完全退出
            Log.e("完全退出","true");
        }
        else{
            //服务停止，重新开启服务。
            Log.e("服务已被杀死","false");
            stopForeground(true);
            Intent intent = new Intent("com.example.androidtest.receiver");
            sendBroadcast(intent);
        }

    }
    public static void closeConnect(){
        isCloseService=true;
    }
    /** 连接MQTT服务器 */
    private void doClientConnection() {

        if (!client.isConnected() && isConnectIsNomarl()) {
            try {
                client.connect(conOpt, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        MQTTConMessage msg = new MQTTConMessage();
                        msg.setMessage("connect");
                        Log.e(TAG, "连接成功 ");
                        EventBus.getDefault().postSticky(msg);
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable arg1) {
                        Log.e("arg1",arg1+"");
                        arg1.printStackTrace();
                        MQTTConMessage msg = new MQTTConMessage();
                        msg.setMessage("disconnect");
                        EventBus.getDefault().postSticky(msg);
                        Log.e(TAG, "qq连接失败，重连");
                    }
                });
            } catch (MqttException e) {

                e.printStackTrace();
            }
        }

    }

    /** 判断网络是否连接 */
    private boolean isConnectIsNomarl() {
        ConnectivityManager connectivityManager = (ConnectivityManager) this.getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo info = connectivityManager.getActiveNetworkInfo();

        if (info != null && info.isConnected()) {
            String name = info.getTypeName();
            Log.e(TAG, "MQTT当前网络名称：" + name);
            return true;
        } else {
            Log.e(TAG, "MQTT 没有可用网络");
            return false;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!isConnectIsNomarl()) {
                Log.e("网络错误","网络错误");
            } else {
                doClientConnection();
            }
        }
    }
}
