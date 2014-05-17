package nfc.emoney.proto;

import java.text.NumberFormat;
import java.util.Locale;

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
	private static final boolean debugTextViewVisibility = false;
	
	private NfcAdapter nfcA;
	private AppData appdata;
	TextView balance,balanceVerified,debug;
	ProgressBar balanceLoading;
	Button bPay,bHistory,bSync,bOption,bAbsen;
	private String password;
	private long lIMEI;
	private KeyDerive key;
	private byte[] aes_key, keyEncryption_key, log_key, balance_key;
	
	private long startDerive, stopDerive;
	
	String[] merchantDevice =new String[] {"NFC Reader", "NFC Smartphone"};
	int selectedItem=0;
	
	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			stopDerive = System.currentTimeMillis();
			Log.d(TAG,"[testing]derive key time: " + (stopDerive-startDerive) + " ms");
			//bellow code will be executed after derive key thread finish
			Bundle bundle = msg.getData();
			int balanceFromMsg = bundle.getInt("Balance", 0);
			int balanceVerifiedFromMsg = bundle.getInt("BalanceVerified", 0);
			
			//UI modification
			balance.setText(Converter.longToRupiah(balanceFromMsg));
			balance.setVisibility(View.VISIBLE);
			balanceVerified.setText("Verified: "+Converter.longToRupiah(balanceVerifiedFromMsg));
			balanceVerified.setVisibility(View.VISIBLE);
			balanceLoading.setVisibility(View.GONE);
			bPay.setEnabled(true);
			bHistory.setEnabled(true);
			bSync.setEnabled(true);
			bOption.setEnabled(true);
			bAbsen.setEnabled(true);
			
			debug.setText("KEK:\n"+Converter.byteArrayToHexString(key.getKeyEncryptionKey()));
			debug.append("\nBalance Key:\n"+Converter.byteArrayToHexString(key.getBalanceKey()));
			debug.append("\nLog Key:\n"+Converter.byteArrayToHexString(key.getLogKey()));
			debug.append("\nTransaction Key:\n"+Converter.byteArrayToHexString(appdata.getDecryptedKey(key.getKeyEncryptionKey())));
			if(debugTextViewVisibility) {
	        	debug.setVisibility(View.VISIBLE);
	        } else {
	        	debug.setVisibility(View.GONE);
	        }
		}
	};
		 
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		// Init NFC
        nfcA = NfcAdapter.getDefaultAdapter(this);
        // no nfc device
    	if (nfcA == null){
    		Toast.makeText(this, "No NFC found!", Toast.LENGTH_LONG).show();
        	finish();
        }
    	
    	//UI Initialization
    	balance = (TextView)findViewById(R.id.tMainBalanceUnverified);
    	balanceVerified = (TextView)findViewById(R.id.tMainBalanceVerified);
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
        bAbsen = (Button) findViewById(R.id.bAbsen);
        bAbsen.setOnClickListener(this);
        bAbsen.setEnabled(false);
        
        if(debugTextViewVisibility) {
        	debug.setVisibility(View.VISIBLE);
        } else {
        	debug.setVisibility(View.GONE);
        }
        
        //get device IMEI
    	TelephonyManager T = (TelephonyManager)getApplicationContext().getSystemService(Context.TELEPHONY_SERVICE);
		String IMEI = T.getDeviceId();
		lIMEI = Long.parseLong(IMEI);
		
        appdata = new AppData(getApplicationContext());
        Log.d(TAG,"create new AppData class successfully");
        if(appdata.getError() == true){
			Toast.makeText(this, "APPDATA ERROR!", Toast.LENGTH_LONG).show();
			finish();
		}
        
        //if ACCN empty, open register activity and close main activity
        if(appdata.getACCN() == 0){
        	startActivity(new Intent(this, Register.class)); 
        	finish();
        }
        else{
        	//check if registered IMEI in appdata is same with current IMEI
        	if(appdata.getIMEI() != lIMEI){
        		Toast.makeText(getApplicationContext(), "Registered device not same with current device", Toast.LENGTH_LONG).show();
        		finish();
        	}

        	//get intent myIntent
        	Intent myIntent = getIntent();
        	
        	//if Password field in myIntent empty, it means this app is just started.
        	//open login activity and close main activity
        	//if login success main activity will be opened again with not empty Password field in myIntent
        	if(myIntent.getStringExtra("Password") == null){
        		startActivity(new Intent(this, Login.class));
        		finish();
        	}else{
        		//get password entered in login activity
	        	password = myIntent.getStringExtra("Password");
	        	Log.d(TAG,"Password:"+password);
	        	
	        	//derive balance key, log key, and key encryption key in separate thread
	        	//get decrypted balance
    			key = new KeyDerive();
	        	Runnable runnable = new Runnable(){
	        		public void run(){
	        			Message msg = handler.obtainMessage();
	        			key.deriveKey(password, String.valueOf(lIMEI));
	        			int decryptedBalance = appdata.getDecryptedBalance(key.getBalanceKey());
	        			int decryptedBalanceVerified = appdata.getDecryptedVerifiedBalance(key.getBalanceKey());
	        			keyEncryption_key = key.getKeyEncryptionKey();
	        			aes_key = appdata.getDecryptedKey(keyEncryption_key);
	        			log_key = key.getLogKey();
	        			balance_key = key.getBalanceKey();
	        			
	        			Bundle bundle = new Bundle();
	        			bundle.putInt("Balance", decryptedBalance);
	        			bundle.putInt("BalanceVerified", decryptedBalanceVerified);
	        			msg.setData(bundle);
	        			handler.sendMessage(msg);
	        		}
	        	};
	        	Thread balanceThread = new Thread(runnable);
	        	startDerive = System.currentTimeMillis(); // derive key start breakpoint
	        	balanceThread.start();
        	}
        }
	}
	
	@Override
    protected void onResume() {
        super.onResume();
        //check if nfc enabled. if nfc is disabled, create dialog to offer enabling nfc in wireless setting
        if (nfcA != null) {
            if (!nfcA.isEnabled()) {
                showWirelessSettingsDialog();
            }
        }
    }
	
	@Override
	public void onClick(View v) {
		switch (v.getId()){
			case R.id.bPay:
				//user tap pay mode. prompt user whether to make payment to nfc reader or nfc phone
				//after choosing, this activity will be closed and pay activity will be opened 
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle("Select Merchant Device");         
				int selected = selectedItem;         
				builder.setSingleChoiceItems(merchantDevice, selected, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog,int which) {
						selectedItem = which;
						Toast.makeText(MainActivity.this,"Payment mode to "+merchantDevice[selectedItem]+" selected",Toast.LENGTH_SHORT).show();

						//if user choose nfc reader, then MerchantDevice value is 0
						//if user choose nfc phone, then MerchantDevice value is 1
						//this value is used in Pay.class to determine transaction data sending mechanism
						Intent payIntent = new Intent(MainActivity.this, Pay.class);
						payIntent.putExtra("MerchantDevice", selectedItem);
						payIntent.putExtra("Password", password);
						payIntent.putExtra("aesKey", aes_key);
						payIntent.putExtra("logKey", log_key);
						payIntent.putExtra("balanceKey", balance_key);
						
						dialog.dismiss();
						startActivity(payIntent);
						finish();
					}
				});
				AlertDialog alert = builder.create();
				alert.show();
				
				break;
			case R.id.bHistory:
				//user choose history mode
				//this activity will be closed and history activity will be opened
				Intent historyIntent = new Intent(this, History.class);
				historyIntent.putExtra("Password", password);
				historyIntent.putExtra("logKey", log_key);
				startActivity(historyIntent);
				finish();
				break;
			case R.id.bSync:
				//user tap sync mode
				//disable all button, show progress bar, hide balance
				balanceLoading.setVisibility(View.VISIBLE);
				balance.setVisibility(View.GONE);
				balanceVerified.setVisibility(View.GONE);
				bPay.setEnabled(false);
				bHistory.setEnabled(false);
				bSync.setEnabled(false);
				bOption.setEnabled(false);
				bAbsen.setEnabled(false);
				
				//do sync in separate thread
				Network sync = new Network(MainActivity.this, getApplicationContext(), keyEncryption_key, log_key, balance_key);
				sync.execute();
				break;
			case R.id.bOption:
				//user choose option mode
				//this activity will be closed and option activity will be opened
				Intent optionIntent = new Intent(this, Option.class);
				optionIntent.putExtra("Password", password);
				optionIntent.putExtra("aesKey", aes_key);
				optionIntent.putExtra("logKey", log_key);
				optionIntent.putExtra("balanceKey", balance_key);
				startActivity(optionIntent);
				finish();
				break;
			case R.id.bAbsen:
				Intent absenIntent = new Intent(this, Absen.class);
				absenIntent.putExtra("Password", password);
				startActivity(absenIntent);
				finish();
				break;
		}
	}
	
	/**
	 * create dialog that prompt user to enable nfc in wireless setting
	 * <br>if possitive button tapped, open wireless setting
	 * <br>if negative button tapped, finish this activity
	 */
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
