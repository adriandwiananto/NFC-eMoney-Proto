package nfc.emoney.proto;

import java.io.IOException;

import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Packet;
import nfc.emoney.proto.misc.Packet.ParseReceivedPacket;
import nfc.emoney.proto.userdata.AppData;
import nfc.emoney.proto.userdata.LogDB;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Pay extends Activity implements OnClickListener , OnNdefPushCompleteCallback {

	private final static String TAG = "{class} Pay";
	private NfcAdapter nfcAdapter;
	boolean mWriteMode = false;
	private PendingIntent mNfcPendingIntent;
	private NdefMessage toSend;
	
	private AppData appdata;
	Button bPay, bCancel;
	EditText eSESN, eAmount;
	TextView tDebug, tAmount, tSESN;
	private byte[] aes_key, log_key, balance_key;
	private byte[] plainTransPacket;
	
	private int merchantDevice = 99;	//0 for reader, 1 for smartphone, 99 for no device selected (never use!!)
	
	private int amountInt;
	
	private int sequence;
	
	ParseReceivedPacket prp;
	
	private String passExtra;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pay);

		sequence = 0;
		
		appdata = new AppData(getApplicationContext());
		Intent myIntent = getIntent();
		aes_key = myIntent.getByteArrayExtra("aesKey");
		log_key = myIntent.getByteArrayExtra("logKey");
		balance_key = myIntent.getByteArrayExtra("balanceKey");
		merchantDevice = myIntent.getIntExtra("MerchantDevice", 99);
		passExtra = myIntent.getStringExtra("Password");
		
		//UI Init
		bPay = (Button) findViewById(R.id.bPaySend);
		bPay.setOnClickListener(this);
		bCancel = (Button) findViewById(R.id.bPayCancel);
		bCancel.setOnClickListener(this);
		eAmount = (EditText) findViewById(R.id.ePayAmount);
		eSESN = (EditText) findViewById(R.id.ePaySESN);
		tDebug = (TextView)findViewById(R.id.tPayDebug);
		tAmount = (TextView)findViewById(R.id.tPayAmount);
		tSESN = (TextView)findViewById(R.id.tPaySESN);
		
		if(merchantDevice == 1){
			tSESN.setText("Waiting for merchant beam...");
			eSESN.setVisibility(View.GONE);
			eAmount.setVisibility(View.GONE);
			tAmount.setVisibility(View.GONE);
			bPay.setEnabled(false);
		}
		
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		nfcAdapter.setNdefPushMessage(null, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
		
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	}


	@Override
    public void onResume(){
    	super.onResume();
    	if(merchantDevice == 1){
	    	nfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, null, null);
	    	if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) processIntent(getIntent());
    	}
    }
	
	private void processIntent(Intent intent) {
		Log.d(TAG,"process intent");
		tDebug.setText("beam intent found!\n");
		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		NdefMessage[] msgs;
		if (rawMsgs != null) {
			tDebug.append("ndef message found!\n");
			msgs = new NdefMessage[rawMsgs.length];
			for (int i = 0; i < rawMsgs.length; i++) {
				msgs[i] = (NdefMessage) rawMsgs[i];
			}
			byte[] receivedPacket = msgs[0].getRecords()[0].getPayload();
			
			prp = new Packet(aes_key).new ParseReceivedPacket(receivedPacket);
			if(prp.getErrorCode() != 0){
				Toast.makeText(getApplicationContext(), prp.getErrorMsg(), Toast.LENGTH_LONG).show();
				Log.d(TAG,"received:"+Converter.byteArrayToHexString(receivedPacket));	
			} else {
				sequence = 1;
				bPay.setEnabled(true);
				tSESN.setVisibility(View.GONE);
				tAmount.setVisibility(View.VISIBLE);
				eAmount.setVisibility(View.VISIBLE);
			}
        }
	}

	@Override
    public void onNewIntent(Intent intent) {
		// Tag writing mode
		
	    if ((merchantDevice == 0) && mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
	        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	        if (writeTag(toSend, detectedTag)) {
	        	LogDB ldb = new LogDB(this, log_key);
	        	ldb.insertLastTransToLog(plainTransPacket);
	        	
	        	int balanceBefore = appdata.getDecryptedBalance(balance_key);
	        	int balanceNow = balanceBefore - amountInt;
	        	appdata.setBalance(balanceNow, balance_key);
	        	appdata.setLastTransTS(System.currentTimeMillis() / 1000);
	        	
				bPay.setEnabled(true);
				tAmount.setText(this.getString(R.string.tPayAmount));
				tSESN.setText(this.getString(R.string.tPaySESN));
				eAmount.setVisibility(View.VISIBLE);
				eSESN.setVisibility(View.VISIBLE);
				
				Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
				finish();
	        } 
	    }
	    
	    if(merchantDevice == 1){
	    	setIntent(intent);
	    }
    }
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bPaySend:
				if(eAmount.getText().toString().length() > 0) {
					if((eSESN.getText().toString().length() == 3) || (merchantDevice == 1)) {
						InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
						inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
					    
//						nfcAdapter = NfcAdapter.getDefaultAdapter(this);
//						mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
						String amount = eAmount.getText().toString();
						amountInt = Integer.parseInt(amount);
						long accnLong = appdata.getACCN();
						long lastTS = appdata.getLastTransTS();
						
						if(merchantDevice == 0){
							String SESN = eSESN.getText().toString();
							int sesnInt = Integer.parseInt(SESN);
							
							Packet packet = new Packet(amountInt, sesnInt, accnLong, lastTS, aes_key);
							byte[] packetArrayToSend = packet.buildTransPacket();
							toSend = packet.createNDEFMessage("emoney/newPayment", packetArrayToSend);
							
							byte[] plainPayload = new byte[32];
							System.arraycopy(packet.getPlainPacket(), 7, plainPayload, 0, 32);
							
							plainTransPacket = packet.getPlainPacket();
							
							tDebug.setText("Data packet to send:\n"+Converter.byteArrayToHexString(packetArrayToSend));
							tDebug.append("\nPlain payload:\n"+Converter.byteArrayToHexString(plainPayload));
							tDebug.append("\nCiphered payload:\n"+Converter.byteArrayToHexString(packet.getCipherPacket()));
							tDebug.append("\naes key:\n"+Converter.byteArrayToHexString(aes_key));
							tDebug.setVisibility(View.VISIBLE);
							enableTagWriteMode();
							
							bPay.setEnabled(false);			
							eAmount.setVisibility(View.GONE);
							eSESN.setVisibility(View.GONE);
							tAmount.append(" "+amount);
							tSESN.append(" "+SESN);
						} else if(merchantDevice == 1) {
							Packet packet = new Packet(amountInt, Converter.byteArrayToInteger(prp.getReceivedSESN()), 
									Converter.byteArrayToInteger(prp.getReceivedTS()), accnLong, lastTS, aes_key);
							byte[] packetArrayToSend = packet.buildTransPacket();
							toSend = packet.createNDEFMessage("emoney/newPayment", packetArrayToSend);

							nfcAdapter.setNdefPushMessage(toSend, this);
							
							byte[] plainPayload = new byte[32];
							System.arraycopy(packet.getPlainPacket(), 7, plainPayload, 0, 32);
							
							plainTransPacket = packet.getPlainPacket();
							
							tDebug.setText("Data packet to send:\n"+Converter.byteArrayToHexString(packetArrayToSend));
							tDebug.append("\nPlain payload:\n"+Converter.byteArrayToHexString(plainPayload));
							tDebug.append("\nCiphered payload:\n"+Converter.byteArrayToHexString(packet.getCipherPacket()));
							tDebug.append("\naes key:\n"+Converter.byteArrayToHexString(aes_key));
							tDebug.setVisibility(View.VISIBLE);
							
							bPay.setEnabled(false);			
							tAmount.append(" "+amount);
						}
					}
					else {
						Toast.makeText(getApplicationContext(), "fill SESN between 100-999", Toast.LENGTH_LONG).show();					
					}
				}			
				else {
					Toast.makeText(getApplicationContext(), "fill amount ( < 1.000.000 )", Toast.LENGTH_LONG).show();
				}
				break;
			case R.id.bPayCancel:
				if(merchantDevice == 0){
					if(bPay.isEnabled() == false){
						bPay.setEnabled(true);
						tDebug.setVisibility(View.GONE);
						tAmount.setText(this.getString(R.string.tPayAmount));
						tSESN.setText(this.getString(R.string.tPaySESN));
						eAmount.setVisibility(View.VISIBLE);
						eSESN.setVisibility(View.VISIBLE);
						disableTagWriteMode();
					}else{
						backToMain();
					}
				} else {
					if((bPay.isEnabled() == false) && (sequence == 0)){
						backToMain();
					} else {
						tSESN.setText("Waiting for merchant beam...");
						tSESN.setVisibility(View.VISIBLE);
						eSESN.setVisibility(View.GONE);
						eAmount.setVisibility(View.GONE);
						tAmount.setVisibility(View.GONE);
						bPay.setEnabled(false);
						sequence = 0;
					}
				}
				break;
		}
	}

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		// TODO Auto-generated method stub
		if((merchantDevice == 1) && (sequence == 1)){
			nfcAdapter.setNdefPushMessage(null, this);
			sequence = 2;
			hand.sendMessage(hand.obtainMessage(2));
		}
	}
	
	private void enableTagWriteMode() {
	    mWriteMode = true;
	    IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
	    IntentFilter[] mWriteTagFilters = new IntentFilter[] { tagDetected };
	    nfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);		
	}

	private void disableTagWriteMode() {
	    mWriteMode = false;
	    nfcAdapter.disableForegroundDispatch(this);
	}
	
	public boolean writeTag(NdefMessage message, Tag tag) {
	    int size = message.toByteArray().length;
	    try {
	        Ndef ndef = Ndef.get(tag);
	        if (ndef != null) {
	            ndef.connect();
	            if (!ndef.isWritable()) {
					Toast.makeText(getApplicationContext(),
					"Error: tag not writable",
					Toast.LENGTH_SHORT).show();
	                return false;
	            }
	            if (ndef.getMaxSize() < size) {
					Toast.makeText(getApplicationContext(),
					"Error: tag too small",
					Toast.LENGTH_SHORT).show();
	                return false;
	            }
	            ndef.writeNdefMessage(message);
	            return true;
	        } else {
	            NdefFormatable format = NdefFormatable.get(tag);
	            if (format != null) {
	                try {
	                    format.connect();
	                    format.format(message);
	                    return true;
	                } catch (IOException e) {
	                    return false;
	                }
	            } else {
	                return false;
	            }
	        }
	    } catch (Exception e) {
	        return false;
	    }
	}
	
	@SuppressLint("HandlerLeak")
	Handler hand = new Handler(){
		public void handleMessage(Message msg){
			switch (msg.what){
				case 2:
					tDebug.setText("beam complete");
			    	LogDB ldb = new LogDB(Pay.this, log_key, prp.getReceivedACCN());
			    	ldb.insertLastTransToLog(plainTransPacket);
			    	
			    	int balanceBefore = appdata.getDecryptedBalance(balance_key);
			    	int balanceNow = balanceBefore - amountInt;
			    	appdata.setBalance(balanceNow, balance_key);
			    	appdata.setLastTransTS(System.currentTimeMillis() / 1000);

			    	Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
					finish();
					break;
			}
		}
	};

	@Override
	public void onBackPressed() {
		backToMain();
	}
	
	private void backToMain(){
		Intent newIntent = new Intent(this,MainActivity.class);
		newIntent.putExtra("Password", passExtra);
		startActivity(newIntent);
		finish();
	}
}
