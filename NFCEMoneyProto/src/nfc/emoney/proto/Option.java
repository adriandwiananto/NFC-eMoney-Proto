package nfc.emoney.proto;

import nfc.emoney.proto.crypto.Hash;
import nfc.emoney.proto.crypto.KeyDerive;
import nfc.emoney.proto.misc.Converter;
import nfc.emoney.proto.userdata.AppData;
import nfc.emoney.proto.userdata.LogDB;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

public class Option extends Activity implements OnClickListener{

	private byte[] aes_key, log_key, balance_key;
	private String curPassStr, newPassStr, confPassStr;
	private Activity currentActivity;
	EditText curPass, newPass, confPass;
	Button proceed, cancel;
	ProgressBar pOption;
	AppData appdata;
	LogDB ldb;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.option);
		
		Intent myIntent = getIntent();
		aes_key = myIntent.getByteArrayExtra("aesKey");
		log_key = myIntent.getByteArrayExtra("logKey");
		balance_key = myIntent.getByteArrayExtra("balanceKey");
		
		appdata = new AppData(this);
		ldb = new LogDB(this, log_key);
		currentActivity = Option.this;
		
		proceed = (Button)findViewById(R.id.bOptionProceed);
		proceed.setOnClickListener(this);
		cancel = (Button)findViewById(R.id.bOptionCancel);
		cancel.setOnClickListener(this);
		curPass = (EditText)findViewById(R.id.eOptionCurPass);
		newPass = (EditText)findViewById(R.id.eOptionNewPass);
		confPass = (EditText)findViewById(R.id.eOptionConfPass);
		pOption = (ProgressBar)findViewById(R.id.pOption);
	}

	@Override
	public void onClick(View v) {
		// TODO Auto-generated method stub
		switch(v.getId()){
			case R.id.bOptionProceed:
				InputMethodManager inputManager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE); 
				inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),InputMethodManager.HIDE_NOT_ALWAYS);
				
				curPassStr = curPass.getText().toString();
				newPassStr = newPass.getText().toString();
				confPassStr = confPass.getText().toString();
				
				if(newPassStr.length() < 1){
					Toast.makeText(getApplicationContext(), "Please input new password", Toast.LENGTH_SHORT).show();
					return;
				}
				
				if(newPassStr.compareTo(confPassStr) != 0){
					Toast.makeText(getApplicationContext(), "Incorrect new password confirmation", Toast.LENGTH_SHORT).show();
					return;
				}
				
				String passSalt = curPassStr.concat(String.valueOf(appdata.getIMEI()));
				byte[] hashed = Hash.Sha256Hash(passSalt);
				String hashedStr = Converter.byteArrayToHexString(hashed);
				
				if(hashedStr.compareTo(appdata.getPass()) != 0){
					Toast.makeText(getApplicationContext(), "Incorrect password", Toast.LENGTH_SHORT).show();
					return;
				}
				
				proceed.setEnabled(false);
				cancel.setEnabled(false);
				pOption.setVisibility(View.VISIBLE);
				Runnable runnable = new Runnable(){
					public void run(){
						Message msg = handler.obtainMessage();
						
						KeyDerive kd = new KeyDerive();
						kd.deriveKey(newPassStr, String.valueOf(appdata.getIMEI()));
						byte[] newBalance_key = kd.getBalanceKey();
						byte[] newLog_key = kd.getLogKey();
						byte[] newKeyEncryption_key = kd.getKeyEncryptionKey();
						
						ldb.changeLogKey(newLog_key);
						int balance = appdata.getDecryptedBalance(balance_key);
						int verifiedBalance = appdata.getDecryptedVerifiedBalance(balance_key);
						
						appdata.setPass(newPassStr);
						appdata.setBalance(balance, newBalance_key);
						appdata.setVerifiedBalance(verifiedBalance, newBalance_key);
						appdata.setKey(aes_key, newKeyEncryption_key);
						handler.sendMessage(msg);
					}
				};
				Thread changePassword = new Thread(runnable);
				changePassword.start();
				break;
			case R.id.bOptionCancel:
				finish();
				break;
		}
	}
	
	@SuppressLint("HandlerLeak")
	Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Toast.makeText(getApplicationContext(), "Password changed successfully!", Toast.LENGTH_LONG).show();
			pOption.setVisibility(View.GONE);
    		startActivity(new Intent(currentActivity, Login.class));
			currentActivity.finish();
		}
	};
}
