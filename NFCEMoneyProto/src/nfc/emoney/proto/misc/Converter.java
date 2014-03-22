package nfc.emoney.proto.misc;

import java.math.BigInteger;
import java.nio.ByteBuffer;

public class Converter {
	public static byte[] hexStringToByteArray(String dataString) {
		int len = dataString.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(dataString.charAt(i), 16) << 4)
									+ Character.digit(dataString.charAt(i+1), 16));
		}
		return data;
	}

	public static String byteArrayToHexString(byte[] dataByte) {
		return String.format("%0" + (dataByte.length*2) + "X", new BigInteger(1, dataByte));
	}
	
	public static byte[] integerToByteArray(int dataInt){
		return ByteBuffer.allocate(4).putInt(dataInt).array();
	}

	public static int byteArrayToInteger(byte[] dataByte){
		return ByteBuffer.wrap(dataByte).getInt();
	}
}
