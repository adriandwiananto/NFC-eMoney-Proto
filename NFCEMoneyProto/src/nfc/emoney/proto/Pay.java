package nfc.emoney.proto;

import java.io.IOException;
import java.util.Arrays;

import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Packet;
import nfc.emoney.proto.misc.Packet.ParseReceivedPacket;
import nfc.emoney.proto.misc.Receipt;
import nfc.emoney.proto.userdata.AppData;
import nfc.emoney.proto.userdata.LogDB;
import nfc.emoney.proto.userdata.LogDB.LogOperation;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
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
import android.view.Gravity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Pay extends Activity implements OnClickListener , OnNdefPushCompleteCallback {

	private final static String TAG = "{class} Pay";
	private static final boolean debugTextViewVisibility = false;
	private NfcAdapter nfcAdapter;
	boolean mWriteMode = false;
	private PendingIntent mNfcPendingIntent;
	private NdefMessage toSend;
	
	private AppData appdata;
	Button bPay, bCancel;
	EditText eSESN, eAmount;
	TextView tDebug, tAmount, tSESN, tReceipt;
	private byte[] aes_key, log_key, balance_key;
	private byte[] plainTransPacket;
	
	private int merchantDevice = 99;	//0 for reader, 1 for smartphone, 99 for no device selected (never use!!)
	
	private int amountInt;
	
	private int sequence;
	
	private long logRowNumber = 0;
	
	ParseReceivedPacket prp;
	
	private String passExtra;
	
	private long startTrans, stopTrans;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pay);

		//if merchant device nfc reader:
		//	sequence 0 - expected to send transaction data to merchant
		//	sequence 1 - waiting for receipt
		//if merchant device nfc phone:
		//	sequence 0 - waiting for merchant request
		//	sequence 1 - accepted merchant request, expected to send transaction data to merchant
		//	sequence 2 - successfully pushing transaction data to merchant, waiting for receipt
		sequence = 0;
		
		appdata = new AppData(getApplicationContext());
		if(appdata.getError() == true){
			Toast.makeText(this, "APPDATA ERROR!", Toast.LENGTH_LONG).show();
			finish();
		}
		
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
		tReceipt = (TextView)findViewById(R.id.tPayWaitReceipt);
		
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
    	Log.d(TAG,"onResume");
    	if(merchantDevice == 1){
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
				return;
			}

			//if parse return no error
			if(merchantDevice == 1){
				if(sequence == 0){
					//change sequence to 1
					//UI change, user need to input amount before sending transaction data to merchant NFC phone
					sequence = 1;
					bPay.setEnabled(true);
					tSESN.setVisibility(View.GONE);
					tAmount.setVisibility(View.VISIBLE);
					//eAmount.setVisibility(View.VISIBLE);
					//rev1:amount sent within merchant request, user only need to confirm it
					tAmount.setGravity(Gravity.CENTER);
					tAmount.setText("Payment Amount Requested:\n"+Converter.longToRupiah(Converter.byteArrayToLong(prp.getReceivedAMNT())));
					tAmount.setTextSize(25);
					
				} else if(sequence == 2) {
	    	        if(processReceipt() == false){
						Toast.makeText(this, "Wrong receipt sent by merchant's NFC phone!", Toast.LENGTH_LONG).show();
						return;
					}
				}
			} else { //merchantDevice == 0
				if(sequence == 1){
					if(processReceipt() == false){
						Toast.makeText(this, "Wrong receipt sent by merchant's NFC reader!", Toast.LENGTH_LONG).show();
						return;
					}
				}
			}
        }
	}

	@Override
    public void onNewIntent(Intent intent) {
    	Log.d(TAG,"onNewIntent");
    	Log.d(TAG,"intent: "+intent.getAction());
    	Log.d(TAG,"merchant device: "+merchantDevice+"\nsequence: "+sequence);
    	
    	if ((merchantDevice == 0) && (sequence == 0) && mWriteMode && 
    			NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
        	Log.d(TAG,"tag discovered");
        	startTrans = System.currentTimeMillis();
    		
			// if merchant device is NFC reader, and write mode is enabled, and new NFC TAG is discovered
	    	Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    	
	    	if(writeTag(toSend, detectedTag)) {
	    		stopTrans = System.currentTimeMillis();
	    		Log.d(TAG,"[testing]trans time = " + (stopTrans - startTrans) + " ms");
		    	//write NDEF message toSend to NFC TAG
	        	sequence = 1;
	        	
	        	//if write success (== transaction success),
	        	//insert transaction data to log
	        	LogDB ldb = new LogDB(this, log_key);
	        	logRowNumber = ldb.insertLastTransToLog(plainTransPacket);
	        	
	        	//update balance (only unverified balance)
	        	//update last transaction timestamp
	        	int balanceBefore = appdata.getDecryptedBalance(balance_key);
	        	int balanceNow = balanceBefore - amountInt;
	        	appdata.setBalance(balanceNow, balance_key);
	        	appdata.setLastTransTS(System.currentTimeMillis() / 1000);
	        	
	        	//UI purpose
				//bPay.setEnabled(true);
				//tAmount.setText(this.getString(R.string.tPayAmount));
				//tSESN.setText(this.getString(R.string.tPaySESN));
				//eAmount.setVisibility(View.VISIBLE);
				//eSESN.setVisibility(View.VISIBLE);
				Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
				
				//close pay activity and open main activity
				//backToMain();
				
				//UI purpose
		    	tAmount.setVisibility(View.GONE);
		    	eAmount.setVisibility(View.GONE);
		    	tSESN.setVisibility(View.GONE);
		    	eSESN.setVisibility(View.GONE);
		    	bPay.setVisibility(View.GONE);
		    	tReceipt.setVisibility(View.VISIBLE);
		    	tReceipt.setTextSize(25);
		    	bCancel.setText("Finish");
	    	}
	    } else if ((merchantDevice == 0) && (sequence == 1) && 
		    			(NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) || 
		    					NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()))) {
	    	//Use ELSE IF. IF will cause exception thrown. After writing tag, sequence is changed to 1. 
	    	//If IF is used, this condition will be fulfilled and processIntent will throw exception.
    		Log.d(TAG,"NDEF discovered");
    		processIntent(intent);
    	}
    	
	    if(merchantDevice == 1){
	    	setIntent(intent);
	    }
    }

	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.bPaySend:
				//make sure amount is not 0
				//if(eAmount.getText().toString().length() > 0) {
				//rev1:amount sent within merchant request, user only need to confirm it
				if(eAmount.getText().toString().length() > 0 || (merchantDevice == 1)) {

					//if merchant device is NFC reader, then SESN edit text must have 3 digit numbers
					//if merchant device is NFC phone, ignore SESN edit text field
					if((eSESN.getText().toString().length() == 3) || (merchantDevice == 1)) {
						
						//hide soft keyboard
						//InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
						//inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
					    
						//rev1:amount sent within merchant request, user only need to confirm it
						//transaction to nfc smartphone doesn't have any edit text, hence getCurrentFocus() will be null
						if(merchantDevice == 0){
							InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
							inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
						}
						
						//nfcAdapter = NfcAdapter.getDefaultAdapter(this);
						//mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
						
						//get amount from edit text
						//get accn and last transaction timestamp from appdata
						//String amount = eAmount.getText().toString();
						//amountInt = Integer.parseInt(amount);
						long accnLong = appdata.getACCN();
						long lastTS = appdata.getLastTransTS();
						
						//check if transaction amount < unverified balance
						boolean proceedTrans = false;
						int amountRemaining = appdata.getDecryptedBalance(balance_key) - amountInt;
						if(amountRemaining >= 0){
							proceedTrans = true;
						}
						
						if((merchantDevice == 0) && (proceedTrans == true)){
							//if merchant device is NFC reader
							//get SESN from edit text
							String SESN = eSESN.getText().toString();
							int sesnInt = Integer.parseInt(SESN);
							
							//get amount from edit text
							//get accn and last transaction timestamp from appdata
							//rev1:amount sent within merchant request, user only need to confirm it
							String amount = eAmount.getText().toString();
							amountInt = Integer.parseInt(amount);
							
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
							if(debugTextViewVisibility) {
					        	tDebug.setVisibility(View.VISIBLE);
					        } else {
					        	tDebug.setVisibility(View.GONE);
					        }

							//enable tag write mode
							enableTagWriteMode();
							
							//UI purpose
							bPay.setEnabled(false);			
							eAmount.setVisibility(View.GONE);
							eSESN.setVisibility(View.GONE);
							//tAmount.append(" "+amount);
							tAmount.append(" "+Converter.longToRupiah(amountInt));
							tAmount.setTextSize(25);
							tSESN.append(" "+SESN);
							tSESN.setTextSize(25);
						} else if((merchantDevice == 1) && (proceedTrans == true)) {

							//build transaction data packet
							//Use SESN and timestamp from merchant request
							//create NDEF message consist of mime type "emoney/newPayment" and transaction data packet
							//Packet packet = new Packet(amountInt, Converter.byteArrayToInteger(prp.getReceivedSESN()), 
							//		Converter.byteArrayToInteger(prp.getReceivedTS()), accnLong, lastTS, aes_key);
							//rev1:amount sent within merchant request, user only need to confirm it
							amountInt = Converter.byteArrayToInteger(prp.getReceivedAMNT());
							Packet packet = new Packet(Converter.byteArrayToInteger(prp.getReceivedAMNT()), Converter.byteArrayToInteger(prp.getReceivedSESN()), 
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
							if(debugTextViewVisibility) {
					        	tDebug.setVisibility(View.VISIBLE);
					        } else {
					        	tDebug.setVisibility(View.GONE);
					        }
							
							//UI purpose
							bPay.setEnabled(false);			
							//rev1:amount sent within merchant request, user only need to confirm it
							//tAmount.append(" "+Converter.longToRupiah(amountInt));
							//eAmount.setVisibility(View.GONE);
						} else { //proceedTrans == false
							Toast.makeText(this, "Insufficient balance!", Toast.LENGTH_LONG).show();
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
					//if(bPay.isEnabled() == false){
					if((sequence == 0) && (bPay.isEnabled() == false)){
						//if user already pressed Pay and haven't tap the phone to NFC reader
						//disable tag write mode and user can change SESN / amount
						bPay.setEnabled(true);
						tDebug.setVisibility(View.GONE);
						tAmount.setText(this.getString(R.string.tPayAmount));
						tAmount.setTextSize(new Button(this).getTextSize()); // return to default text size
						tSESN.setText(this.getString(R.string.tPaySESN));
						tSESN.setTextSize(new Button(this).getTextSize()); // return to default text size
						eAmount.setVisibility(View.VISIBLE);
						eSESN.setVisibility(View.VISIBLE);
						disableTagWriteMode();
					} else {
						//if user haven't pressed Pay button, cancel will close pay activity and open main activity
						backToMain();
					}
				} else {
					//if((bPay.isEnabled() == false) && (sequence == 0)){
					if((sequence == 0) || (sequence == 2)) {
						//if user haven't received merchant request NFC data, (sequence = 0) 
						//cancel will close pay activity and open main activity
						
						//if user press finish in waiting for receipt (sequence = 2)
						//cancel will close pay activity and open main activity
						backToMain();
					} else {
						//if user already received merchant request, (sequence = 1)
						//cancel will bring Pay activity to it's initial state (sequence = 0)
						tSESN.setText("Waiting for merchant beam...");
						tSESN.setVisibility(View.VISIBLE);
						eSESN.setVisibility(View.GONE);
						eAmount.setVisibility(View.GONE);
						tAmount.setVisibility(View.GONE);
						tAmount.setText(this.getString(R.string.tPayAmount));
						tAmount.setTextSize(new Button(this).getTextSize());
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
			hand.sendMessage(hand.obtainMessage(1));
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
				case 1:
					//successfully push transaction data to merchant NFC phone
					tDebug.setText("beam complete");
					
					//insert last transaction to log
			    	LogDB ldb = new LogDB(Pay.this, log_key, prp.getReceivedACCN());
			    	logRowNumber = ldb.insertLastTransToLog(plainTransPacket);
			    	
			    	//update new balance to appdata (only unverified balance)
			    	//update last transaction timestamp in appdata
			    	int balanceBefore = appdata.getDecryptedBalance(balance_key);
			    	int balanceNow = balanceBefore - amountInt;
			    	appdata.setBalance(balanceNow, balance_key);
			    	appdata.setLastTransTS(System.currentTimeMillis() / 1000);

			    	//notification
			    	Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
			    	
			    	//close this activity and open main activity
					//backToMain();
			    	
			    	//UI purpose
			    	tAmount.setVisibility(View.GONE);
			    	eAmount.setVisibility(View.GONE);
			    	bPay.setVisibility(View.GONE);
			    	tReceipt.setVisibility(View.VISIBLE);
			    	tReceipt.setTextSize(25);
			    	bCancel.setText("Finish");
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
	
	private boolean processReceipt(){
		byte[] sentSesnHeader = Arrays.copyOfRange(plainTransPacket, 3, 5);
		byte[] sentTimestamp = Arrays.copyOfRange(plainTransPacket, 13, 17);
		byte[] sentAmount = Arrays.copyOfRange(plainTransPacket, 17, 21);
		byte[] sentLastTransTimestamp = Arrays.copyOfRange(plainTransPacket, 21, 25);
		byte[] sentSesnPayload = Arrays.copyOfRange(plainTransPacket, 25, 27);
		
		LogDB db = new LogDB(this, log_key);
		LogOperation lo = db.new LogOperation();
        Cursor cur = db.getLogBlob();
        //cur.move((int)logRowNumber);
        cur.moveToPosition((int)logRowNumber-1);
        byte[] thisTransLog = lo.getDecrpytedLogPerRow(cur);
        byte[] accnmInLog = Arrays.copyOfRange(thisTransLog, 8, 14);
        byte[] amountInLog = Arrays.copyOfRange(thisTransLog, 20, 24);
        byte[] timestampInLog = Arrays.copyOfRange(thisTransLog, 24, 28);
		
		//check if received receipt is same with transaction data sent to merchant
		//check if received receipt is same with transaction data saved in log db
		if(Arrays.equals(prp.getReceivedSESN(), sentSesnHeader) == false){
			return false;
		}
		if(Arrays.equals(prp.getReceivedSESN(), sentSesnPayload) == false){
			return false;
		}
		if(Arrays.equals(prp.getReceivedTS(), sentTimestamp) == false){
			return false;
		}
		if(Arrays.equals(prp.getReceivedAMNT(), sentAmount) == false){
			return false;
		}
		if(Arrays.equals(prp.getReceivedLATS(), sentLastTransTimestamp) == false){
			return false;
		}
        if(merchantDevice == 1){
        	if(Arrays.equals(prp.getReceivedACCN(), accnmInLog) == false){
	        	//if merchant device is nfc reader, skip this check
	        	return false;
        	}
    	}
        if(Arrays.equals(prp.getReceivedAMNT(), amountInLog) == false){
        	return false;
        }
        if(Arrays.equals(prp.getReceivedTS(), timestampInLog) == false){
        	return false;
        }
        
        //receipt valid
        
        if(merchantDevice == 0){
	        //if merchant is nfc reader, write ACCN from receipt to log
	        System.arraycopy(prp.getReceivedACCN(), 0, thisTransLog, 8, 6);
	        db.insertTransToLog(logRowNumber, thisTransLog);
        }
        
        //PRINT PDF IN HERE!!!
        Receipt rcp = new Receipt(Pay.this, Converter.byteArrayToLong(prp.getReceivedTS()), 
					        		Converter.byteArrayToLong(prp.getReceivedACCN()), appdata.getACCN(), 
					        		Converter.byteArrayToInteger(prp.getReceivedAMNT()));
        
        String dialogMsg;
        if(rcp.writeReceiptPdfToFile()){
        	dialogMsg = "Receipt received!\n"+
					"Amount Sent: "+Converter.longToRupiah(Converter.byteArrayToLong(prp.getReceivedAMNT()))+"\n"+
					"Merchant ID: "+Converter.byteArrayToLong(prp.getReceivedACCN()); 
        } else {
        	dialogMsg = "Fail to write to external storage";
        }
        
        //popup notification
    	new AlertDialog.Builder(this)
		.setTitle("Notification")
		.setMessage(dialogMsg)
		.setNeutralButton("OK", new DialogInterface.OnClickListener()
		{
		    @Override
		    public void onClick(DialogInterface dialog, int which) {
		    	backToMain();
		    }
		})
		.show();
    	
        return true;
	}
}
