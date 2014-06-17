package nfc.emoney.proto;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

import nfc.emoney.proto.crypto.AES256cipher;
import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.misc.Packet;
import nfc.emoney.proto.misc.Packet.ParseReceivedPacket;
import nfc.emoney.proto.userdata.AppData;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class Parkir extends Activity implements OnClickListener {

	private final static String TAG = "{class} Parkir";
	private static final boolean debugTextViewVisibility = false;
	
	private NfcAdapter nfcAdapter;
	boolean mWriteMode = false;
	private PendingIntent mNfcPendingIntent;
	private NdefMessage toSend;

	private AppData appdata;

	private String passExtra;
	private byte[] aes_key;
	
	private int mode = 0;
	private int sequence = 0;
	ParseReceivedPacket prp;
	
	Button bCancel;
	TextView tParkir;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.parkir);
	
		appdata = new AppData(getApplicationContext());
		if(appdata.getError() == true){
			Toast.makeText(this, "APPDATA ERROR!", Toast.LENGTH_LONG).show();
			finish();
		}

		Intent myIntent = getIntent();
		passExtra = myIntent.getStringExtra("Password");
		aes_key = myIntent.getByteArrayExtra("aesKey");
		
		if (appdata.getLicense().equalsIgnoreCase("no license")){
			Toast.makeText(this, "Please insert your License Number in Option menu", Toast.LENGTH_LONG).show();
			backToMain();
		}
		
		bCancel = (Button)findViewById(R.id.bParkirCancel);
		bCancel.setOnClickListener(this);
		tParkir = (TextView)findViewById(R.id.tParkirShow);

		//NFC init
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if (nfcAdapter == null) return; 
		
		//to prevent new activity creation after receiving NFC intent
		mNfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(Parkir.this, Parkir.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
	}
	
	@Override
    public void onResume(){
    	super.onResume();
    	Log.d(TAG,"onResume");
    	if(appdata.getParkingStatus() == false){
			parkirInPacket();
			mode = 0; //in
		} else {
			parkirOutPacket();
			mode = 1; //out
		}
    }
	
	@Override
	public void onClick(View v) {
		switch(v.getId()){
			case R.id.bParkirCancel:
				backToMain();
				break;
		}
	}

	@Override
    public void onNewIntent(Intent intent) {
    	Log.d(TAG,"onNewIntent");
    	Log.d(TAG,"intent: "+intent.getAction());
    	
    	if (mWriteMode && sequence == 0 && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
        	Log.d(TAG,"tag discovered");
    		
			// if merchant device is NFC reader, and write mode is enabled, and new NFC TAG is discovered
	    	Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
	    	
	    	if(writeTag(toSend, detectedTag)) {
	    		if(mode == 1){
	    			AlertDialog.Builder builder = new AlertDialog.Builder(this);
	    	        builder.setMessage("Parkir Out Success?");
	    	        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
	    	            public void onClick(DialogInterface dialogInterface, int i) {
	    	                appdata.setParkingSecurity(0);
	    	                appdata.setParkingStatus(false);
	    	                backToMain();
	    	            }
	    	        });
	    	        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
	    	            public void onClick(DialogInterface dialogInterface, int i) {
	    	                return;
	    	            }
	    	        });
	    	        builder.create().show();
	    		} else if (mode == 0){
	    			tParkir.setText("Please tap again the parking meter, after finish syncing" );
	    			sequence = 1;
	    		}
	    	}
	    } else if(mode == 0 && sequence == 1 && (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction()) || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction()))){
	    	processIntent(intent);
	    }
	}
	
	private void processIntent(Intent intent) {
		//debugging purpose
		Log.d(TAG,"process intent");
		
		Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
		NdefMessage[] msgs;
		if (rawMsgs != null) {
			//get payload of received NFC data
			msgs = new NdefMessage[rawMsgs.length];
			for (int i = 0; i < rawMsgs.length; i++) {
				msgs[i] = (NdefMessage) rawMsgs[i];
			}
			byte[] receivedPacket = msgs[0].getRecords()[0].getPayload();
			
			//parse received NFC data
			prp = new Packet(aes_key).new ParseReceivedPacket(receivedPacket);
			if(prp.getErrorCode() != 0 && prp.getErrorCode() != 3){
				Toast.makeText(getApplicationContext(), prp.getErrorMsg(), Toast.LENGTH_LONG).show();
				return;
			}

			byte[] plainPayload = prp.getReceivedPlainPayload();
			byte[] securityByte = Arrays.copyOfRange(plainPayload, 16, 20);
			
			appdata.setParkingSecurity(Converter.byteArrayToInteger(securityByte));
			appdata.setParkingStatus(true);
			backToMain();
        }
	}

	@Override
	public void onBackPressed() {
		backToMain();
	}
	
	private void parkirInPacket() {
		Packet packet = new Packet();
		long longACCN = appdata.getACCN();
		byte[] parkir = new byte[55];
		
		String platStr = Converter.strToHexRepresent(appdata.getLicense());
		byte[] platByte = Converter.hexStringToByteArray(platStr);
		int platLen = platByte.length;
		
		byte[] randomIV = new byte[16];
		SecureRandom random = new SecureRandom();		
		random.nextBytes(randomIV);

		parkir[0] = 55;	//Frame Length (1)
		parkir[1] = 3;	//parkir (3)
		parkir[2] = 0;	//in (0)
		Arrays.fill(parkir, 3, 7, (byte) 0);
		System.arraycopy(ByteBuffer.allocate(8).putLong(longACCN).array(),2, parkir, 7, 6); //ACCN(6)
		parkir[13] = (byte) platLen;
		System.arraycopy(platByte, 0, parkir, 14, platLen);
		Arrays.fill(parkir, 14+platLen, 27, (byte) 0); //fill remaining with 0
		Arrays.fill(parkir, 27, 39, (byte) 12); //PAD(12)
		System.arraycopy(randomIV, 0, parkir, 39, 16);
		
//		plainPacket = parkir;
		
		byte[] encAES = new byte[20];
		System.arraycopy(parkir, 7, encAES, 0, 20);
		
		byte[] cipherPayload = null;
		try {
			cipherPayload = AES256cipher.encrypt(randomIV, aes_key, encAES);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		byte[] decryptedCiphertext = null;
		try {
			decryptedCiphertext = AES256cipher.decrypt(randomIV, aes_key, cipherPayload);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		byte[] parkirFinal = new byte[55]; 
		if(Arrays.equals(encAES, decryptedCiphertext)) {
			System.arraycopy(parkir, 0, parkirFinal, 0, 7);
			System.arraycopy(cipherPayload, 0, parkirFinal, 7, 32);
			System.arraycopy(randomIV, 0, parkirFinal, 39, 16);
		}
		
		toSend = packet.createNDEFMessage("emoney/parkirData", parkirFinal);
		nfcAdapter.setNdefPushMessage(toSend, this);
		
		enableTagWriteMode();
	}
	
	private void parkirOutPacket() {
		Packet packet = new Packet();
		long longACCN = appdata.getACCN();
		byte[] parkir = new byte[55];
		
		String platStr = Converter.strToHexRepresent(appdata.getLicense());
		byte[] platByte = Converter.hexStringToByteArray(platStr);
		int platLen = platByte.length;
		
		byte[] randomIV = new byte[16];
		SecureRandom random = new SecureRandom();		
		random.nextBytes(randomIV);
		
		parkir[0] = 55;	//Frame Length (1)
		parkir[1] = 3;	//parkir (3)
		parkir[2] = 0;	//in (0)
		Arrays.fill(parkir, 3, 7, (byte) 0);
		System.arraycopy(ByteBuffer.allocate(8).putLong(longACCN).array(),2, parkir, 7, 6); //ACCN(6)
		parkir[13] = (byte) platLen;
		System.arraycopy(platByte, 0, parkir, 14, platLen);
		if(platLen < 9) Arrays.fill(parkir, 14+platLen, 23, (byte) 0); //fill remaining with 0
		System.arraycopy(Converter.integerToByteArray(appdata.getParkingSecurity()), 0, parkir, 23, 4);
		Arrays.fill(parkir, 27, 39, (byte) 12); //PAD(12)
		System.arraycopy(randomIV, 0, parkir, 39, 16);
		
//		plainPacket = parkir;
		
		byte[] encAES = new byte[20];
		System.arraycopy(parkir, 7, encAES, 0, 20);
		
		byte[] cipherPayload = null;
		try {
			cipherPayload = AES256cipher.encrypt(randomIV, aes_key, encAES);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		byte[] decryptedCiphertext = null;
		try {
			decryptedCiphertext = AES256cipher.decrypt(randomIV, aes_key, cipherPayload);
		} catch (Exception e) {
			e.printStackTrace();
		} 
		
		byte[] parkirFinal = new byte[55]; 
		if(Arrays.equals(encAES, decryptedCiphertext)) {
			System.arraycopy(parkir, 0, parkirFinal, 0, 7);
			System.arraycopy(cipherPayload, 0, parkirFinal, 7, 32);
			System.arraycopy(randomIV, 0, parkirFinal, 39, 16);
		}
		
		toSend = packet.createNDEFMessage("emoney/parkirData", parkirFinal);
		nfcAdapter.setNdefPushMessage(toSend, this);
		
		enableTagWriteMode();
	}
	
	private void backToMain(){
		//close this activity and open main activity with Password in Intent (to prevent opening of login activity)
		Intent newIntent = new Intent(this,MainActivity.class);
		newIntent.putExtra("Password", passExtra);
		startActivity(newIntent);
		finish();
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
