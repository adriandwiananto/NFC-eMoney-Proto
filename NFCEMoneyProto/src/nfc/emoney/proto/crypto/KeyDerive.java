package nfc.emoney.proto.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import nfc.emoney.proto.misc.Converter;
import android.util.Log;

public class KeyDerive {
	private int keyLength;
	private byte[] balance_key, log_key, keyEncryption_key;
	private final static String TAG = "{class} KeyDerive";
	
	/**
	 * Class for deriving key using password-based key derivation function
	 */
	public KeyDerive(){
		keyLength = 256;
	}
	
	/**
	 * Derive key from password, salt, and iteration using PBKDF2 key derivation function algorithm
	 * @param password
	 * @param salt
	 * @param iteration
	 * @return 32 bytes key
	 */
	private byte[] pbkdf2Derive(String password, String salt, int iteration){
		KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iteration, keyLength);
		SecretKeyFactory keyFactory;
		byte[] keyBytes = new byte[32];
		try {
			keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return keyBytes;
	}
	
	/**
	 * Derive balance key, log key, and key encryption key
	 * <br>To get these keys, use getter method for each key
	 * @param password
	 * @param IMEI
	 */
	public void deriveKey(String password, String IMEI){
		String salt = IMEI;
		Log.d(TAG,"Start deriving key");
		balance_key = pbkdf2Derive(password, salt, 800);
//		balance_key = pbkdf2Derive(password, salt, 400); //testing purpose
//		balance_key = pbkdf2Derive(password, salt, 1600); //testing purpose
		Log.d(TAG,"balancekey:"+Converter.byteArrayToHexString(balance_key));
		
		log_key = pbkdf2Derive(password, salt, 900);
//		log_key = pbkdf2Derive(password, salt, 450); //testing purpose
//		log_key = pbkdf2Derive(password, salt, 1800); //testing purpose
		Log.d(TAG,"logkey:"+Converter.byteArrayToHexString(log_key));
		
		keyEncryption_key = pbkdf2Derive(password, salt, 1000);
//		keyEncryption_key = pbkdf2Derive(password, salt, 500); //testing purpose
//		keyEncryption_key = pbkdf2Derive(password, salt, 2000); //testing purpose
		Log.d(TAG,"transkey:"+Converter.byteArrayToHexString(keyEncryption_key));
		Log.d(TAG,"Finish deriving key. Check the time!");
	}
	
	/**
	 * Getter method for balance key
	 * <br>Call deriveKey method first before calling this method
	 * @return 32 bytes balance key
	 */
	public byte[] getBalanceKey(){
		return balance_key;
	}

	/**
	 * Getter method for log key
	 * <br>Call deriveKey method first before calling this method
	 * @return 32 bytes log key
	 */
	public byte[] getLogKey(){
		return log_key;
	}
	
	/**
	 * Getter method for key encryption key
	 * <br>Call deriveKey method first before calling this method
	 * @return 32 bytes key encryption key
	 */
	public byte[] getKeyEncryptionKey(){
		return keyEncryption_key;
	}
}
