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

		//sequence 0 means initial condition
		//if merchant device nfc reader:
		//	sequence 0 - payer expected to send transaction data to merchant
		//	sequence 1 - merchant expected to send receipt to payer (not yet implemented)
		//if merchant device nfc phone:
		//	sequence 0 - merchant expected to send transaction request to payer
		//	sequence 1 - payer expected to send transaction data to merchant
		//	sequence 2 - merchant expected to send transaction cancelation (optional, not yet implemented)
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
			//UI init for NFC phone merchant device
			tSESN.setText("Waiting for merchant beam...");
			eSESN.setVisibility(View.GONE);
			eAmount.setVisibility(View.GONE);
			tAmount.setVisibility(View.GONE);
			bPay.setEnabled(false);
		}
		
		//NFC init
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		nfcAdapter.setNdefPushMessage(null, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
		
		//to prevent new activity creation after receiving NFC intent
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	}


	@Override
    public void onResume(){
    	super.onResume();
    	if(merchantDevice == 1){
    		//called when device receive NFC intent
	    	nfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, null, null);
	    	if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) processIntent(getIntent());
    	}
    }
	
	private void processIntent(Intent intent) {
		//debugging purpose
		Log.d(TAG,"process intent");
		tDebug.setText("beam intent found!\n");
		
		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		NdefMessage[] msgs;
		if (rawMsgs != null) {
			tDebug.append("ndef message found!\n");
			
			//get payload of received NFC data
			msgs = new NdefMessage[rawMsgs.length];
			for (int i = 0; i < rawMsgs.length; i++) {
				msgs[i] = (NdefMessage) rawMsgs[i];
			}
			byte[] receivedPacket = msgs[0].getRecords()[0].getPayload();
			
			//parse received NFC data
			prp = new Packet(aes_key).new ParseReceivedPacket(receivedPacket);
			if(prp.getErrorCode() != 0){
				Toast.makeText(getApplicationContext(), prp.getErrorMsg(), Toast.LENGTH_LONG).show();
				Log.d(TAG,"received:"+Converter.byteArrayToHexString(receivedPacket));	
			} else {
				//if parse return no error, change sequence to 1
				//UI change, user need to input amount before sending transaction data to merchant NFC phone
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
	    if ((merchantDevice == 0) && mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			// if merchant device is NFC reader, and write mode is enabled, and new NFC TAG is discovered
	    	Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    	
	    	//write NDEF message toSend to NFC TAG
	        if (writeTag(toSend, detectedTag)) {
	        	//if write success (== transaction success),
	        	//insert transaction data to log
	        	LogDB ldb = new LogDB(this, log_key);
	        	ldb.insertLastTransToLog(plainTransPacket);
	        	
	        	//update balance (only unverified balance)
	        	//update last transaction timestamp
	        	int balanceBefore = appdata.getDecryptedBalance(balance_key);
	        	int balanceNow = balanceBefore - amountInt;
	        	appdata.setBalance(balanceNow, balance_key);
	        	appdata.setLastTransTS(System.currentTimeMillis() / 1000);
	        	
	        	//UI purpose
				bPay.setEnabled(true);
				tAmount.setText(this.getString(R.string.tPayAmount));
				tSESN.setText(this.getString(R.string.tPaySESN));
				eAmount.setVisibility(View.VISIBLE);
				eSESN.setVisibility(View.VISIBLE);
				Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
				
				//close pay activity and open main activity
				backToMain();
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

				//make sure amount is not 0
				if(eAmount.getText().toString().length() > 0) {

					//if merchant device is NFC reader, then SESN edit text must have 3 digit numbers
					//if merchant device is NFC phone, ignore SESN edit text field
					if((eSESN.getText().toString().length() == 3) || (merchantDevice == 1)) {
						
						//hide soft keyboard
						InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
						inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
					    
//						nfcAdapter = NfcAdapter.getDefaultAdapter(this);
//						mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
						
						//get amount from edit text
						//get accn and last transaction timestamp from appdata
						String amount = eAmount.getText().toString();
						amountInt = Integer.parseInt(amount);
						long accnLong = appdata.getACCN();
						long lastTS = appdata.getLastTransTS();
						
						if(merchantDevice == 0){
							//if merchant device is NFC reader
							//get SESN from edit text
							String SESN = eSESN.getText().toString();
							int sesnInt = Integer.parseInt(SESN);
							
							//build transaction data packet 
							//create NDEF message consist of mime type "emoney/newPayment" and transaction data packet
							Packet packet = new Packet(amountInt, sesnInt, accnLong, lastTS, aes_key);
							byte[] packetArrayToSend = packet.buildTransPacket();
							toSend = packet.createNDEFMessage("emoney/newPayment", packetArrayToSend);
							
							//get transaction data packet with unencrypted paylod (for logging purpose)
							plainTransPacket = packet.getPlainPacket();
							
							//debugging purpose
							byte[] plainPayload = new byte[32];
							System.arraycopy(packet.getPlainPacket(), 7, plainPayload, 0, 32);
							tDebug.setText("Data packet to send:\n"+Converter.byteArrayToHexString(packetArrayToSend));
							tDebug.append("\nPlain payload:\n"+Converter.byteArrayToHexString(plainPayload));
							tDebug.append("\nCiphered payload:\n"+Converter.byteArrayToHexString(packet.getCipherPayload()));
							tDebug.append("\naes key:\n"+Converter.byteArrayToHexString(aes_key));
							tDebug.setVisibility(View.VISIBLE);

							//enable tag write mode
							enableTagWriteMode();
							
							//UI purpose
							bPay.setEnabled(false);			
							eAmount.setVisibility(View.GONE);
							eSESN.setVisibility(View.GONE);
							tAmount.append(" "+amount);
							tSESN.append(" "+SESN);
						} else if(merchantDevice == 1) {

							//build transaction data packet
							//Use SESN and timestamp from merchant request
							//create NDEF message consist of mime type "emoney/newPayment" and transaction data packet
							Packet packet = new Packet(amountInt, Converter.byteArrayToInteger(prp.getReceivedSESN()), 
									Converter.byteArrayToInteger(prp.getReceivedTS()), accnLong, lastTS, aes_key);
							byte[] packetArrayToSend = packet.buildTransPacket();
							toSend = packet.createNDEFMessage("emoney/newPayment", packetArrayToSend);

							//get transaction data packet with unencrypted paylod (for logging purpose)
							plainTransPacket = packet.getPlainPacket();
							
							//set device to push transaction data packet if another NFC phone in range
							nfcAdapter.setNdefPushMessage(toSend, this);

							//debugging purpose
							byte[] plainPayload = new byte[32];
							System.arraycopy(packet.getPlainPacket(), 7, plainPayload, 0, 32);
							tDebug.setText("Data packet to send:\n"+Converter.byteArrayToHexString(packetArrayToSend));
							tDebug.append("\nPlain payload:\n"+Converter.byteArrayToHexString(plainPayload));
							tDebug.append("\nCiphered payload:\n"+Converter.byteArrayToHexString(packet.getCipherPayload()));
							tDebug.append("\naes key:\n"+Converter.byteArrayToHexString(aes_key));
							tDebug.setVisibility(View.VISIBLE);
							
							//UI purpose
							bPay.setEnabled(false);			
							tAmount.append(" "+amount);
						}
					} else {
						Toast.makeText(getApplicationContext(), "fill SESN between 100-999", Toast.LENGTH_LONG).show();					
					}
				} else {
					Toast.makeText(getApplicationContext(), "fill amount ( < 1.000.000 )", Toast.LENGTH_LONG).show();
				}
				break;
			case R.id.bPayCancel:
				if(merchantDevice == 0){
					if(bPay.isEnabled() == false){
						//if user already pressed Pay and haven't tap the phone to NFC reader
						//disable tag write mode and user can change SESN / amount
						bPay.setEnabled(true);
						tDebug.setVisibility(View.GONE);
						tAmount.setText(this.getString(R.string.tPayAmount));
						tSESN.setText(this.getString(R.string.tPaySESN));
						eAmount.setVisibility(View.VISIBLE);
						eSESN.setVisibility(View.VISIBLE);
						disableTagWriteMode();
					} else {
						//if user haven't pressed Pay button, cancel will close pay activity and open main activity
						backToMain();
					}
				} else {
					if((bPay.isEnabled() == false) && (sequence == 0)){
						//if user haven't received merchant request NFC data, 
						//cancel will close pay activity and open main activity
						backToMain();
					} else {
						//if user already received merchant request,
						//cancel will bring Pay activity to it's initial state (sequence 0, waiting merchant request)
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
		//called when ndef push complete
		if((merchantDevice == 1) && (sequence == 1)){
			//if merchant device is nfc phonse
			//and after successfully pushing transaction data to merchant NFC phone
			//change sequence to 2
			//call handler with message = 2
			nfcAdapter.setNdefPushMessage(null, this);
			sequence = 2;
			hand.sendMessage(hand.obtainMessage(2));
		}
	}
	
	
	/**
	 * NFC TAG WRITE MODE ENABLED
	 */
	private void enableTagWriteMode() {
	    mWriteMode = true;
	    IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
	    IntentFilter[] mWriteTagFilters = new IntentFilter[] { tagDetected };
	    nfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);		
	}

	/**
	 * NFC TAG WRITE MODE DISABLED
	 */
	private void disableTagWriteMode() {
	    mWriteMode = false;
	    nfcAdapter.disableForegroundDispatch(this);
	}
	
	/**
	 * WRITE NFC TAG METHOD
	 * @param message
	 * @param tag
	 * @return
	 */
	private boolean writeTag(NdefMessage message, Tag tag) {
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
					//successfully push transaction data to merchant NFC phone
					
					//insert last transaction to log
					tDebug.setText("beam complete");
			    	LogDB ldb = new LogDB(Pay.this, log_key, prp.getReceivedACCN());
			    	ldb.insertLastTransToLog(plainTransPacket);
			    	
			    	//update new balance to appdata (only unverified balance)
			    	//update last transaction timestamp in appdata
			    	int balanceBefore = appdata.getDecryptedBalance(balance_key);
			    	int balanceNow = balanceBefore - amountInt;
			    	appdata.setBalance(balanceNow, balance_key);
			    	appdata.setLastTransTS(System.currentTimeMillis() / 1000);

			    	//notification
			    	Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
			    	
			    	//close this activity and open main activity
					backToMain();
					break;
			}
		}
	};

	@Override
	public void onBackPressed() {
		backToMain();
	}
	
	private void backToMain(){
		//close this activity and open main activity with Password in Intent
		Intent newIntent = new Intent(this,MainActivity.class);
		newIntent.putExtra("Password", passExtra);
		startActivity(newIntent);
		finish();
	}
}
