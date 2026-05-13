package br.com.nfse.importadorpn.certificado;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class ExtratorCnpjCertificadoTest {
    @Test
    void extraiCnpjDeOtherNameIcpBrasil() {
        byte[] otherName = derSequence(
                derObjectIdentifier("2.16.76.1.3.3"),
                derExplicitContext0(derUtf8String("11222333000181")));

        Set<String> cnpjs = new ExtratorCnpjCertificado().extrairDeOtherName(otherName);

        assertThat(cnpjs).containsExactly("11222333000181");
    }

    @Test
    void ignoraNumeroSoltoSemOidDeCnpj() {
        byte[] otherName = derSequence(
                derObjectIdentifier("2.16.76.1.3.2"),
                derExplicitContext0(derUtf8String("11222333000181")));

        Set<String> cnpjs = new ExtratorCnpjCertificado().extrairDeOtherName(otherName);

        assertThat(cnpjs).isEmpty();
    }

    private static byte[] derSequence(byte[]... values) {
        return der(0x30, concat(values));
    }

    private static byte[] derExplicitContext0(byte[] value) {
        return der(0xA0, value);
    }

    private static byte[] derUtf8String(String value) {
        return der(0x0C, value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] derObjectIdentifier(String oid) {
        String[] parts = oid.split("\\.");
        int first = Integer.parseInt(parts[0]) * 40 + Integer.parseInt(parts[1]);
        byte[][] encoded = new byte[parts.length - 1][];
        encoded[0] = new byte[] {(byte) first};
        for (int i = 2; i < parts.length; i++) {
            encoded[i - 1] = base128(Integer.parseInt(parts[i]));
        }
        return der(0x06, concat(encoded));
    }

    private static byte[] base128(int value) {
        if (value < 128) {
            return new byte[] {(byte) value};
        }
        byte[] buffer = new byte[5];
        int index = buffer.length;
        buffer[--index] = (byte) (value & 0x7F);
        value >>>= 7;
        while (value > 0) {
            buffer[--index] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        return java.util.Arrays.copyOfRange(buffer, index, buffer.length);
    }

    private static byte[] der(int tag, byte[] value) {
        byte[] length = length(value.length);
        byte[] out = new byte[1 + length.length + value.length];
        out[0] = (byte) tag;
        System.arraycopy(length, 0, out, 1, length.length);
        System.arraycopy(value, 0, out, 1 + length.length, value.length);
        return out;
    }

    private static byte[] length(int length) {
        if (length < 128) {
            return new byte[] {(byte) length};
        }
        return new byte[] {(byte) 0x81, (byte) length};
    }

    private static byte[] concat(byte[]... values) {
        int size = 0;
        for (byte[] value : values) {
            size += value.length;
        }
        byte[] out = new byte[size];
        int pos = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, out, pos, value.length);
            pos += value.length;
        }
        return out;
    }
}
