package nfc.emoney.proto;

import nfc.emoney.proto.crypto.KeyDerive;
import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Network;
import nfc.emoney.proto.userdata.AppData;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener{

	private final static String TAG = "{class} MainActivity";
	private NfcAdapter nfcA;
	private AppData appdata;
	TextView balance,debug;
	ProgressBar balanceLoading;
	Button bPay,bHistory,bSync,bOption;
	private String password;
	private long lIMEI;
	private KeyDerive key;
	private byte[] aes_key, keyEncryption_key, log_key, balance_key;
	
	String[] merchantDevice =new String[] {"NFC Reader", "NFC Smartphone"};
	int selectedItem=0;
	
	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Bundle bundle = msg.getData();
			String stringFromMsg = bundle.getString("Balance");
			//UI modification
			balance.setText(stringFromMsg);
			balanceLoading.setVisibility(View.GONE);
			balance.setVisibility(View.VISIBLE);
			bPay.setEnabled(true);
			bHistory.setEnabled(true);
			bSync.setEnabled(true);
			bOption.setEnabled(true);
			
			debug.setText("KEK:\n"+Converter.byteArrayToHexString(key.getKeyEncryptionKey()));
			debug.append("\nBalance Key:\n"+Converter.byteArrayToHexString(key.getBalanceKey()));
			debug.append("\nLog Key:\n"+Converter.byteArrayToHexString(key.getLogKey()));
			debug.append("\nTransaction Key:\n"+Converter.byteArrayToHexString(appdata.getDecryptedKey(key.getKeyEncryptionKey())));
		}
	};
		 
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
    	
    	//UI Initialization
    	balance = (TextView)findViewById(R.id.tMainBalanceUnverified);
    	debug = (TextView)findViewById(R.id.tMainDebug);
    	balanceLoading = (ProgressBar)findViewById(R.id.pMain);
        bPay = (Button) findViewById(R.id.bPay);
        bPay.setOnClickListener(this);
        bPay.setEnabled(false);
        bHistory = (Button) findViewById(R.id.bHistory);
        bHistory.setOnClickListener(this);
        bHistory.setEnabled(false);
        bSync = (Button) findViewById(R.id.bSync);
        bSync.setOnClickListener(this);
        bSync.setEnabled(false);
        bOption = (Button) findViewById(R.id.bOption);
        bOption.setOnClickListener(this);
        bOption.setEnabled(false);
        
    	TelephonyManager T = (TelephonyManager)getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		String IMEI = T.getDeviceId();
		lIMEI = Long.parseLong(IMEI);
		
        appdata = new AppData(getApplicationContext());
        Log.d(TAG,"create new AppData class successfully");
        
        if(appdata.getACCN() == 0){
        	startActivity(new Intent(this, Register.class)); 
        	finish();
        }
        else{
        	
        	if(appdata.getIMEI() != lIMEI){
        		Toast.makeText(getApplicationContext(), "Registered device not same with current device", Toast.LENGTH_LONG).show();
        		finish();
        	}

        	Intent myIntent = getIntent();
        	
        	if(myIntent.getStringExtra("Password") == null){
        		startActivity(new Intent(this, Login.class));
        		finish();
        	}else{
	        	password = myIntent.getStringExtra("Password");
	        	Log.d(TAG,"Password:"+password);
	        	
    			key = new KeyDerive();

	        	Runnable runnable = new Runnable(){
	        		public void run(){
	        			Message msg = handler.obtainMessage();
	        			key.deriveKey(password, String.valueOf(lIMEI));
	        			int decryptedBalance = appdata.getDecryptedBalance(key.getBalanceKey());
	        			keyEncryption_key = key.getKeyEncryptionKey();
	        			aes_key = appdata.getDecryptedKey(keyEncryption_key);
	        			log_key = key.getLogKey();
	        			balance_key = key.getBalanceKey();
	        			
	        			Bundle bundle = new Bundle();
	        			bundle.putString("Balance", String.valueOf(decryptedBalance));
	        			msg.setData(bundle);
	        			handler.sendMessage(msg);
	        		}
	        	};
	        	
	        	Thread balanceThread = new Thread(runnable);
	        	balanceThread.start();
        	}
        }
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
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Select Merchant Device");         
				int selected = selectedItem;         
				builder.setSingleChoiceItems(merchantDevice, selected, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog,int which) {
						selectedItem = which;
						Toast.makeText(MainActivity.this,"Payment mode to "+merchantDevice[selectedItem]+" selected",Toast.LENGTH_SHORT).show();

						Intent payIntent = new Intent(MainActivity.this, Pay.class);
						payIntent.putExtra("MerchantDevice", selectedItem);
						payIntent.putExtra("Password", password);
						payIntent.putExtra("aesKey", aes_key);
						payIntent.putExtra("logKey", log_key);
						payIntent.putExtra("balanceKey", balance_key);
						
						dialog.dismiss();
						startActivity(payIntent);
						finish(); //add
					}
				});
				AlertDialog alert = builder.create();
				alert.show();
				
				break;
			case R.id.bHistory:
				Intent historyIntent = new Intent(this, History.class);
				historyIntent.putExtra("Password", password);
				historyIntent.putExtra("logKey", log_key);
				startActivity(historyIntent);
				finish();
				break;
			case R.id.bSync:
				//new thread
				//http post
				balanceLoading.setVisibility(View.VISIBLE);
				balance.setVisibility(View.GONE);
				bPay.setEnabled(false);
				bHistory.setEnabled(false);
				bSync.setEnabled(false);
				bOption.setEnabled(false);
				
				Network sync = new Network(MainActivity.this, getApplicationContext(), keyEncryption_key, log_key, balance_key);
				sync.execute();
				break;
			case R.id.bOption:
				Intent optionIntent = new Intent(this, Option.class);
				optionIntent.putExtra("Password", password);
				optionIntent.putExtra("aesKey", aes_key);
				optionIntent.putExtra("logKey", log_key);
				optionIntent.putExtra("balanceKey", balance_key);
				startActivity(optionIntent);
				finish();
				break;
		}
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
