package nfc.emoney.proto;

import nfc.emoney.proto.userdata.AppData;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener, OnNdefPushCompleteCallback{

	private NfcAdapter nfcA;
	private AppData appdata;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Init
        nfcA = NfcAdapter.getDefaultAdapter(this);
        // no nfc device
    	if (nfcA == null){
    		Toast.makeText(this, "No NFC found!", Toast.LENGTH_LONG).show();
        	finish();
        }
    	
    	//disable android beam
        nfcA.setNdefPushMessage(null, this);
        //set callback to onNdefPushComplete in this class
        nfcA.setOnNdefPushCompleteCallback(this, this);
        
        // Preferences
//        SharedPreferences Pref = PreferenceManager.getDefaultSharedPreferences(this);
        
        appdata = new AppData(getApplicationContext());
        if(appdata.getACCN() == 0){
        	startActivity(new Intent(this, Register.class)); 
        	finish();
        }
        else{
        	startActivity(new Intent(this, Login.class));
        	finish();
        }
        
        //Set Listener
        ((Button) findViewById(R.id.bPay)).setOnClickListener(this);
        ((Button) findViewById(R.id.bHistory)).setOnClickListener(this);
        ((Button) findViewById(R.id.bSync)).setOnClickListener(this);
        ((Button) findViewById(R.id.bOption)).setOnClickListener(this);
	}

	@Override
    protected void onResume() {
        super.onResume();
        if (nfcA != null) {
            if (!nfcA.isEnabled()) {
                showWirelessSettingsDialog();
            }
        }
    }
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch (v.getId()){
			case R.id.bPay:
				startActivity(new Intent(this, Pay.class));
				break;
			case R.id.bHistory:
				startActivity(new Intent(this, History.class));
				break;
			case R.id.bSync:
				//new thread
				//http post
				break;
			case R.id.bOption:
				startActivity(new Intent(this, Option.class));
				break;
		}
	}

	@Override
	public void onNdefPushComplete(NfcEvent event) {
		// TODO Auto-generated method stub
		
	}
	
	private void showWirelessSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.nfc_disabled);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                Intent intent = new Intent(Settings.ACTION_WIRELESS_SETTINGS);
                startActivity(intent);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialogInterface, int i) {
                finish();
            }
        });
        builder.create().show();
        return;
    }
}
