package com.example.productauth.service;

import org.bouncycastle.crypto.macs.CMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.security.MessageDigest;

@Service
public class SignatureVerificationService {

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
}
