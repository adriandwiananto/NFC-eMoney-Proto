package nfc.emoney.proto.userdata;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

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
	private final static String TAG = "[class]AppData";
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
		balance_key = key.Pbkdf2Derive(password, salt, 8000);
		log_key = key.Pbkdf2Derive(password, salt, 9000);
		trans_key = key.Pbkdf2Derive(password, salt, 10000);
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

	public void setBalance(int Bal) {
		// TODO Auto-generated method stub
		
		//derive key
		//encrypt balance
		//commit ke sharedpref
		String encBal;
		byte[] encBalArray = new byte[48];
		byte[] iv = new byte[16];
		SecureRandom random = new SecureRandom();		
		random.nextBytes(iv);
		try {
			encBalArray = AES256cipher.encrypt(iv, balance_key, Converter.integerToByteArray(Bal));
			System.arraycopy(iv, 0, encBalArray, 32, 16);
			encBal = Converter.byteArrayToHexString(encBalArray);
			Editor edit = Pref.edit();
			edit.putString("Balance", encBal);
			edit.commit();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public int getBalance(){
		
		//ambil dari Pref
		//derive key
		//decrypt balance
		//return balance
		String encBal = Pref.getString("Balance", null);
		byte[] iv = new byte[16];
		return 0;
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
