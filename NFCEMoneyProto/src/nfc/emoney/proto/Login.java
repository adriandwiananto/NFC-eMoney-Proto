package nfc.emoney.proto;

import nfc.emoney.proto.crypto.Hash;
import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.userdata.AppData;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class Login extends Activity implements OnClickListener {
	TextView loginDebug;
	
	private final static String TAG = "{class} Login";
	private static final boolean debugTextViewVisibility = false;
	
	private AppData appdata;
	private String password;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login);
		
		appdata = new AppData(this);
		
		//debugging purpose
		Log.d(TAG,"Write debug textview!");
		loginDebug = (TextView)findViewById(R.id.tLoginDebug);
		loginDebug.setText("ACCN : "+String.valueOf(appdata.getACCN()));
		loginDebug.append("\nHWID : "+String.valueOf(appdata.getIMEI()));
		loginDebug.append("\nPassword : "+appdata.getPass());
		loginDebug.append("\nBalance : "+appdata.getBalance());
		loginDebug.append("\nLast Sync : "+String.valueOf(appdata.getLATS()));
		loginDebug.append("\nLast Sync(Readable) : "+ Converter.timestampToReadable(appdata.getLATS()));
		Log.d(TAG,"key:"+appdata.getEncryptedKey());
		loginDebug.append("\nWrapped Key+Iv : "+ appdata.getEncryptedKey());
		
		if(debugTextViewVisibility) {
        	loginDebug.setVisibility(View.VISIBLE);
        } else {
        	loginDebug.setVisibility(View.GONE);
        }
		
		//set button listener
		((Button)findViewById(R.id.bLoginProceed)).setOnClickListener(this);
		((Button)findViewById(R.id.bLoginCancel)).setOnClickListener(this);
	}

	@Override
    protected void onResume() {
        super.onResume();
    }
	
	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		Intent myIntent = new Intent(this, MainActivity.class);
		switch(v.getId()){
			case R.id.bLoginProceed:
				//hide soft keyboard
				InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
				inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
				
				//get string from edit text
				password = ((EditText)findViewById(R.id.eLoginPassword)).getText().toString();
				String passSalt = password.concat(String.valueOf(appdata.getIMEI()));
				
				//hash entered password and salt
				byte[] hashed = Hash.sha256Hash(passSalt);
				String hashedStr = Converter.byteArrayToHexString(hashed);
				
				//compare previously created hashed password with hashed password in appdata
				//if ok, close this activity and launch main activity with "Password" field in Intent
				if(hashedStr.compareTo(appdata.getPass()) == 0){
					myIntent.putExtra("Password", password);
					startActivity(myIntent);
					finish();
				}else{
					Toast.makeText(getApplicationContext(), "Wrong password!", Toast.LENGTH_SHORT).show();
				}
				break;
			case R.id.bLoginCancel:
				finish();
				break;
		}
	}
}
