package com.example.productauth.service;

import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class SignatureVerificationService {

    public boolean matchesNtag424Sdm(String uid, String counterHex, String macInput, String incomingHexCmac,
            String baseHexKey) {
        try {
            byte[] baseKey = Hex.decode(baseHexKey);
            byte[] uidBytes = Hex.decode(uid);
            byte[] counterBytes = Hex.decode(counterHex);
            byte[] incomingCmac = Hex.decode(incomingHexCmac);
            if (baseKey.length != 16 || uidBytes.length != 7 || counterBytes.length != 3
                    || incomingCmac.length != 8) {
                return false;
            }

            byte[] sessionVector = new byte[6 + uidBytes.length + counterBytes.length];
            sessionVector[0] = 0x3C;
            sessionVector[1] = (byte) 0xC3;
            sessionVector[2] = 0x00;
            sessionVector[3] = 0x01;
            sessionVector[4] = 0x00;
            sessionVector[5] = (byte) 0x80;
            System.arraycopy(uidBytes, 0, sessionVector, 6, uidBytes.length);
            for (int i = 0; i < counterBytes.length; i++) {
                sessionVector[6 + uidBytes.length + i] = counterBytes[counterBytes.length - 1 - i];
            }

            byte[] sessionKey = calculateCmac(baseKey, sessionVector);
            byte[] fullCmac = calculateCmac(sessionKey, macInput.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[8];
            for (int i = 0; i < truncated.length; i++) {
                truncated[i] = fullCmac[1 + (i * 2)];
            }
            return MessageDigest.isEqual(truncated, incomingCmac);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public boolean matches(String uid, int counter, String storedHexKey, String incomingHexCmac) {
        try {
            byte[] key = Hex.decode(storedHexKey);
            if (key.length != 16) {
                return false;
            }

            byte[] uidBytes = Hex.decode(uid);
            byte[] counterBytes = ByteBuffer.allocate(4).putInt(counter).array();
            byte[] payload = new byte[uidBytes.length + 3];
            System.arraycopy(uidBytes, 0, payload, 0, uidBytes.length);
            System.arraycopy(counterBytes, 1, payload, uidBytes.length, 3);

            CMac cmac = new CMac(new org.bouncycastle.crypto.engines.AESEngine());
            cmac.init(new KeyParameter(key));
            cmac.update(payload, 0, payload.length);
            byte[] calculated = new byte[cmac.getMacSize()];
            cmac.doFinal(calculated, 0);

            byte[] expected = new byte[8];
            System.arraycopy(calculated, 0, expected, 0, expected.length);
            return MessageDigest.isEqual(expected, Hex.decode(incomingHexCmac));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private byte[] calculateCmac(byte[] key, byte[] message) {
        CMac cmac = new CMac(new org.bouncycastle.crypto.engines.AESEngine());
        cmac.init(new KeyParameter(key));
        cmac.update(message, 0, message.length);
        byte[] result = new byte[cmac.getMacSize()];
        cmac.doFinal(result, 0);
        return result;
    }
}
