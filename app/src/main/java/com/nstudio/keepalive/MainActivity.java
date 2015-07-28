package com.nstudio.keepalive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;


public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent service = new Intent(this, KeepAliveService.class);
        service.setAction(KeepAliveService.ACTION_START);
        startService(service);
        finish();
    }
}
