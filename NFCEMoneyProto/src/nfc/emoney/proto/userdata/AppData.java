package nfc.emoney.proto.userdata;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import nfc.emoney.proto.crypto.AES256cipher;
import nfc.emoney.proto.crypto.Hash;
import nfc.emoney.proto.crypto.KeyDerive;
import nfc.emoney.proto.misc.Converter;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;

public class AppData {
	private final static String TAG = "{class} AppData";
	private SharedPreferences Pref;
	private Context ctx;
	private String IMEI;
	private byte[] aes_key, balance_key, log_key, trans_key;
	private long lIMEI;
	
	public AppData(Context context) {
		// TODO Auto-generated constructor stub
		ctx = context;
		Pref = PreferenceManager.getDefaultSharedPreferences(ctx);
	}

	public boolean deriveKey(String password){
		if(this.getPass().isEmpty()){
			return false;
		}
		String hashed = Converter.byteArrayToHexString(Hash.Sha256Hash(password.concat(String.valueOf(this.getIMEI()))));
		if(hashed.compareTo(this.getPass()) != 0){
			return false;
		}
		
		String salt = String.valueOf(this.getIMEI());
		KeyDerive key = new KeyDerive();
		Log.d(TAG,"Start deriving key");
		balance_key = key.Pbkdf2Derive(password, salt, 800);
		Log.d(TAG,"balancekey:"+Converter.byteArrayToHexString(balance_key));
		
		log_key = key.Pbkdf2Derive(password, salt, 900);
		Log.d(TAG,"logkey:"+Converter.byteArrayToHexString(log_key));
		
		trans_key = key.Pbkdf2Derive(password, salt, 1000);
		Log.d(TAG,"transkey:"+Converter.byteArrayToHexString(trans_key));
		Log.d(TAG,"Finish deriving key. Check the time!");
		return true;
	}
	
	public void setACCN(long lACCN) {
		// TODO Auto-generated method stub
		Editor edit = Pref.edit();
		edit.putLong("ACCN", lACCN);
		edit.commit();
	}
	
	public long getACCN() {
		// TODO Auto-generated method stub
		return Pref.getLong("ACCN", 0);
	}

	public void setIMEI() {
		TelephonyManager T = (TelephonyManager)ctx.getSystemService(Context.TELEPHONY_SERVICE);
		IMEI = T.getDeviceId();
		lIMEI = Long.parseLong(IMEI);
		Editor edit = Pref.edit();
		edit.putLong("HWID", lIMEI);
		edit.commit();
	}
	
	public long getIMEI(){
		return Pref.getLong("HWID", 0);
	}

	public void setPass(String newPass) {
		// TODO Auto-generated method stub
		
		//hash trus commit ke Pref
		String hashed = newPass.concat(String.valueOf(this.getIMEI()));
		Log.d(TAG,"Password to hashed:"+hashed);
		Editor edit = Pref.edit();
		edit.putString("Pass", Converter.byteArrayToHexString(Hash.Sha256Hash(hashed)));
		edit.commit();
	}
	
	public String getPass(){
		return Pref.getString("Pass", null);
	}

	public void setLATS(long LATS) {
		// TODO Auto-generated method stub
		Editor edit = Pref.edit();
		edit.putLong("LATS", LATS);
		edit.commit();
	}
	
	public long getLATS(){
		return Pref.getLong("LATS", 0);
	}

	public void setBalance(int Balance) {
		// TODO Auto-generated method stub
		
		//derive key
		//encrypt balance
		//commit ke sharedpref
		String ciphertext;
		byte[] encryptedBalanceArray = new byte[16];
		byte[] iv = new byte[16];
		byte[] balanceIv= new byte[32];

		SecureRandom random = new SecureRandom();		
		random.nextBytes(iv);
		
		System.arraycopy(iv, 0, balanceIv, 16, 16);
		
		try {
			encryptedBalanceArray = AES256cipher.encrypt(iv, balance_key, Converter.integerToByteArray(Balance));
			System.arraycopy(encryptedBalanceArray, 0, balanceIv, 0, 16);
			ciphertext = Converter.byteArrayToHexString(balanceIv);
			Log.d(TAG,"Balance to write to shared preferences:"+ciphertext);
			Editor edit = Pref.edit();
			edit.putString("Balance", ciphertext);
			edit.commit();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public String getBalance(){
		return Pref.getString("Balance", null);
	}
	
	public int getDecryptedBalance(){
		
		//ambil dari Pref
		//derive key
		//decrypt balance
		//return balance
		String ciphertext = Pref.getString("Balance", null);
		if(ciphertext.isEmpty()) {
			Log.d(TAG,"Encrypted balance from shared preferences is empty");
			return -1;
		}
		
		byte[] ciphertextArray = Converter.hexStringToByteArray(ciphertext);
		if(ciphertextArray.length != 32) {
			Log.d(TAG,"Encrypted balance (in byte array) length is not 32");
			return -2;
		}
		
		if(balance_key.length != 32){
			Log.d(TAG,"Balance key not yet derived!!");
			return -3;
		}
		
		byte[] iv = new byte[16];
		System.arraycopy(ciphertextArray, 16, iv, 0, 16);
		Log.d(TAG,"IV:"+Converter.byteArrayToHexString(iv));
		
		byte[] encryptedBalance = new byte[16];
		System.arraycopy(ciphertextArray, 0, encryptedBalance, 0, 16);
		Log.d(TAG,"encrypted balance:"+Converter.byteArrayToHexString(encryptedBalance));
		Log.d(TAG,"balance key:"+Converter.byteArrayToHexString(balance_key));
		
		byte[] balance = new byte[16];
		
		try {
			balance = AES256cipher.decrypt(iv, balance_key, encryptedBalance);
			return Converter.byteArrayToInteger(balance);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			Log.e(TAG, e.toString());
		}
		
		Log.d(TAG,"WTF! What a terrible failure! Uncaught exception.");
		return -4;
	}
	
	public void deleteAppData(){
		Editor edit = Pref.edit();
		edit.clear();
		edit.commit();
	}

	public void setKey(byte[] aesKey) {
		// TODO Auto-generated method stub
		// derive key
		// encrypt key
		// commit to pref
		
	}
	
//	public byte[] getKey(){
		//derive key
		//get key from pref
		//return decrypted
//	}
}
