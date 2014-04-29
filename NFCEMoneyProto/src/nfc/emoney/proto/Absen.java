package nfc.emoney.proto;

import java.io.IOException;
import java.nio.ByteBuffer;

import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Packet;
import nfc.emoney.proto.userdata.AppData;
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
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Absen extends Activity implements OnClickListener, OnNdefPushCompleteCallback{

	private final static String TAG = "{class} MainActivity";
	private static final boolean debugTextViewVisibility = false;
	
	private NfcAdapter nfcAdapter;
	boolean mWriteMode = false;
	private PendingIntent mNfcPendingIntent;
	private NdefMessage toSend;

	private AppData appdata;

	private String passExtra;
	
	Button bProceed, bCancel;
	EditText eRandom;
	TextView tRandom, tShow;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.absen);
	
		appdata = new AppData(getApplicationContext());
		if(appdata.getError() == true){
			Toast.makeText(this, "APPDATA ERROR!", Toast.LENGTH_LONG).show();
			finish();
		}

		Intent myIntent = getIntent();
		passExtra = myIntent.getStringExtra("Password");
		
		bProceed = (Button)findViewById(R.id.bAbsenProceed);
		bProceed.setOnClickListener(this);
		bCancel = (Button)findViewById(R.id.bAbsenCancel);
		bCancel.setOnClickListener(this);
		eRandom = (EditText)findViewById(R.id.eAbsenRandom);
		tRandom = (TextView)findViewById(R.id.tAbsenRandom);
		tShow = (TextView)findViewById(R.id.tAbsenShow);
		
		//NFC init
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		nfcAdapter.setNdefPushMessage(null, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
		
		//to prevent new activity creation after receiving NFC intent
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Absen.this, Absen.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	}
	
	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.bAbsenProceed:
				if(eRandom.getText().toString().length() == 3){
					//hide soft keyboard
					InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
					inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
					
					String randomStr = eRandom.getText().toString();
					int randomInt = Integer.parseInt(randomStr);

					if(randomInt < 100 && randomInt > 999) {
						Toast.makeText(this, "Please input random number between 100~999", Toast.LENGTH_SHORT).show();
					}else{
						long accnLong = appdata.getACCN();
						Packet packet = new Packet();
						
						byte[] absenFrame = new byte[17];
						absenFrame[0] = 17;
						absenFrame[1] = 2;
						absenFrame[2] = 2;
						System.arraycopy(ByteBuffer.allocate(4).putInt(randomInt).array(), 2, absenFrame, 3, 2); //SESN (2)
						absenFrame[5] = 0; //EH (2)
						absenFrame[6] = 0; //EH
						System.arraycopy(ByteBuffer.allocate(8).putLong(accnLong).array(),2, absenFrame, 7, 6); //ACCN(6)
						System.arraycopy(ByteBuffer.allocate(8).putLong(System.currentTimeMillis()/1000).array(),4, absenFrame, 13, 4); //TS(4)
						toSend = packet.createNDEFMessage("emoney/absenData", absenFrame);
						nfcAdapter.setNdefPushMessage(toSend, this);
						
						enableTagWriteMode();
						
						bProceed.setEnabled(false);
						eRandom.setVisibility(View.GONE);
						tRandom.setVisibility(View.GONE);
						tShow.setText("Random Number:\n"+randomStr);
						tShow.setVisibility(View.VISIBLE);
					}
				}else{
					Toast.makeText(this, "Please input 3 digit random number", Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.bAbsenCancel:
				backToMain();
				break;
		}
	}

	@Override
    public void onNewIntent(Intent intent) {
    	Log.d(TAG,"onNewIntent");
    	Log.d(TAG,"intent: "+intent.getAction());
    	
    	if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
        	Log.d(TAG,"tag discovered");
    		
			// if merchant device is NFC reader, and write mode is enabled, and new NFC TAG is discovered
	    	Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    	
	    	if(writeTag(toSend, detectedTag)) {
	    		backToMain();
	    	}
	    }
	}
	
	@Override
	public void onBackPressed() {
		backToMain();
	}
	
	private void backToMain(){
		//close this activity and open main activity with Password in Intent (to prevent opening of login activity)
		Intent newIntent = new Intent(this,MainActivity.class);
		newIntent.putExtra("Password", passExtra);
		startActivity(newIntent);
		finish();
	}

	@Override
	public void onNdefPushComplete(NfcEvent arg0) {
		backToMain();
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
}
