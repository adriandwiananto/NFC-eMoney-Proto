package nfc.emoney.proto.userdata;

import nfc.emoney.proto.crypto.Hash;
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
	private Byte[] aes_key;
	private long lIMEI;
	
	public AppData(Context context) {
		// TODO Auto-generated constructor stub
		ctx = context;
		Pref = PreferenceManager.getDefaultSharedPreferences(ctx);
		
		if(this.getACCN() != 0){
			if(this.getIMEI() != 0){
				//new class that responsible for key derivation and key store in session
			}
		}
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
	}
	
	public int getBalance(){
		
		//ambil dari Pref
		//derive key
		//decrypt balance
		//return balance
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
