package nfc.emoney.proto.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class KeyDerive {
	private int keyLength;
	
	public KeyDerive(){
		keyLength = 256;
	}
	
	public byte[] Pbkdf2Derive(String password, String salt, int iteration){
		SecureRandom random = new SecureRandom();
		KeySpec keySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), iteration, keyLength);
		SecretKeyFactory keyFactory;
		try {
			keyFactory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
			byte[] keyBytes = keyFactory.generateSecret(keySpec).getEncoded();
			return keyBytes;
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InvalidKeySpecException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}
}
