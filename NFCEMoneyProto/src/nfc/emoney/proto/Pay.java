package nfc.emoney.proto;

import java.io.IOException;

import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Packet;
import nfc.emoney.proto.userdata.AppData;
import nfc.emoney.proto.userdata.LogDB;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Pay extends Activity implements OnClickListener , OnNdefPushCompleteCallback {

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
	
	private int amountInt;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.pay);

		appdata = new AppData(getApplicationContext());
		Intent myIntent = getIntent();
		aes_key = myIntent.getByteArrayExtra("aesKey");
		log_key = myIntent.getByteArrayExtra("logKey");
		balance_key = myIntent.getByteArrayExtra("balanceKey");
		
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
		
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		nfcAdapter.setNdefPushMessage(null, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
	}

	@Override
    public void onNewIntent(Intent intent) {
		// Tag writing mode
	    if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
	        Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	        if (writeTag(toSend, detectedTag)) {
	        	Toast.makeText(getApplicationContext(), "Packet sent successfully", Toast.LENGTH_LONG).show();
	        	
	        	LogDB ldb = new LogDB(this, log_key);
	        	ldb.insertLastTransToLog(plainTransPacket);
	        	
	        	int balanceBefore = appdata.getDecryptedBalance(balance_key);
	        	int balanceNow = balanceBefore - amountInt;
	        	appdata.setBalance(balanceNow, balance_key);
	        	
				bPay.setEnabled(true);
				tAmount.setText(this.getString(R.string.tPayAmount));
				tSESN.setText(this.getString(R.string.tPaySESN));
				eAmount.setVisibility(View.VISIBLE);
				eSESN.setVisibility(View.VISIBLE);
				
				Toast.makeText(getApplicationContext(), "Transaction Success!", Toast.LENGTH_LONG).show();
				finish();
	        } 
	    }
    }
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bPaySend:
				if(eAmount.getText().toString().length() > 0) {
					if(eSESN.getText().toString().length() == 3) {
						String amount = eAmount.getText().toString();
						String SESN = eSESN.getText().toString();
						amountInt = Integer.parseInt(amount);
						int sesnInt = Integer.parseInt(SESN);
						long accnLong = appdata.getACCN();
						long lastTS = appdata.getLATS();
						
						InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
						inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
					    
						nfcAdapter = NfcAdapter.getDefaultAdapter(this);
						mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Pay.this, Pay.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
						
						Packet packet = new Packet(amountInt, sesnInt, accnLong, lastTS, aes_key);
						byte[] packetArrayToSend = packet.buildTransPacket();
						toSend = packet.createNDEFMessage("data/trans", packetArrayToSend);
						
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
				if(bPay.isEnabled() == false){
					bPay.setEnabled(true);
					tDebug.setVisibility(View.GONE);
					tAmount.setText(this.getString(R.string.tPayAmount));
					tSESN.setText(this.getString(R.string.tPaySESN));
					eAmount.setVisibility(View.VISIBLE);
					eSESN.setVisibility(View.VISIBLE);
					disableTagWriteMode();
				}else{
					finish();
				}
				break;
		}
	}

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		// TODO Auto-generated method stub
		
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
}
