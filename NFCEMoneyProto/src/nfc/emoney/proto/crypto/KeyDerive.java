package nfc.emoney.proto.crypto;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class KeyDerive {
	private int keyLength;
	
	public KeyDerive(){
		keyLength = 256;
	}
	
	public byte[] Pbkdf2Derive(String password, String salt, int iteration){
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
}
