package br.com.nfse.importadorpn.certificado;

import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;

public final class ExtratorCnpjCertificado {
    private static final String OID_CNPJ_ICP_BRASIL = "2.16.76.1.3.3";
    private static final Pattern CNPJ = Pattern.compile("\\D*(\\d{14})\\D*");

    public Set<String> extrair(X509Certificate certificado) {
        Set<String> cnpjs = new LinkedHashSet<>();
        cnpjs.addAll(extrairDeSubjectAlternativeNames(certificado));
        cnpjs.addAll(extrairDeSubjectDn(certificado));
        return cnpjs;
    }

    Set<String> extrairDeOtherName(byte[] encodedOtherName) {
        try {
            DerNode otherName = DerNode.parse(encodedOtherName);
            if (!otherName.containsOid(OID_CNPJ_ICP_BRASIL)) {
                return Set.of();
            }
            return otherName.strings14Digitos();
        } catch (IllegalArgumentException exception) {
            return Set.of();
        }
    }

    private Set<String> extrairDeSubjectAlternativeNames(X509Certificate certificado) {
        Set<String> cnpjs = new LinkedHashSet<>();
        try {
            Collection<List<?>> nomes = certificado.getSubjectAlternativeNames();
            if (nomes == null) {
                return Set.of();
            }
            for (List<?> nome : nomes) {
                if (nome.size() < 2 || !(nome.get(0) instanceof Integer tipo) || tipo != 0) {
                    continue;
                }
                Object valor = nome.get(1);
                if (valor instanceof byte[] bytes) {
                    cnpjs.addAll(extrairDeOtherName(bytes));
                }
            }
        } catch (Exception ignored) {
            return Set.of();
        }
        return cnpjs;
    }

    private Set<String> extrairDeSubjectDn(X509Certificate certificado) {
        Set<String> cnpjs = new LinkedHashSet<>();
        String nome = certificado.getSubjectX500Principal().getName(X500Principal.RFC2253);
        try {
            LdapName ldapName = new LdapName(nome);
            for (Rdn rdn : ldapName.getRdns()) {
                String tipo = rdn.getType();
                if (!"CNPJ".equalsIgnoreCase(tipo)
                        && !"CN".equalsIgnoreCase(tipo)
                        && !OID_CNPJ_ICP_BRASIL.equals(tipo)
                        && !("OID." + OID_CNPJ_ICP_BRASIL).equalsIgnoreCase(tipo)) {
                    continue;
                }
                Matcher matcher = CNPJ.matcher(String.valueOf(rdn.getValue()));
                if (matcher.matches()) {
                    cnpjs.add(matcher.group(1));
                }
            }
        } catch (InvalidNameException ignored) {
            return Set.of();
        }
        return cnpjs;
    }

    private record DerNode(int tag, byte[] value, List<DerNode> children) {
        static DerNode parse(byte[] encoded) {
            DerReader reader = new DerReader(encoded);
            DerNode node = reader.readNode();
            if (reader.hasRemaining()) {
                throw new IllegalArgumentException("DER com bytes excedentes");
            }
            return node;
        }

        boolean containsOid(String oid) {
            if (tag == 0x06 && oid.equals(decodeOid(value))) {
                return true;
            }
            return children.stream().anyMatch(child -> child.containsOid(oid));
        }

        Set<String> strings14Digitos() {
            Set<String> strings = new LinkedHashSet<>();
            if (tag == 0x0C || tag == 0x13 || tag == 0x16 || tag == 0x1E) {
                String valor = tag == 0x1E
                        ? new String(value, StandardCharsets.UTF_16BE)
                        : new String(value, StandardCharsets.UTF_8);
                Matcher matcher = CNPJ.matcher(valor);
                if (matcher.matches()) {
                    strings.add(matcher.group(1));
                }
            }
            for (DerNode child : children) {
                strings.addAll(child.strings14Digitos());
            }
            return strings;
        }

        private static String decodeOid(byte[] value) {
            if (value.length == 0) {
                return "";
            }
            StringBuilder oid = new StringBuilder();
            int first = value[0] & 0xFF;
            oid.append(first / 40).append('.').append(first % 40);
            long current = 0;
            for (int i = 1; i < value.length; i++) {
                int b = value[i] & 0xFF;
                current = (current << 7) | (b & 0x7F);
                if ((b & 0x80) == 0) {
                    oid.append('.').append(current);
                    current = 0;
                }
            }
            return oid.toString();
        }
    }

    private static final class DerReader {
        private final byte[] encoded;
        private int pos;

        private DerReader(byte[] encoded) {
            this.encoded = encoded.clone();
        }

        private boolean hasRemaining() {
            return pos < encoded.length;
        }

        private DerNode readNode() {
            int tag = readByte();
            int length = readLength();
            if (length < 0 || pos + length > encoded.length) {
                throw new IllegalArgumentException("DER invalido");
            }
            byte[] value = java.util.Arrays.copyOfRange(encoded, pos, pos + length);
            pos += length;
            List<DerNode> children = isConstructed(tag) ? children(value) : List.of();
            return new DerNode(tag, value, children);
        }

        private static List<DerNode> children(byte[] value) {
            DerReader reader = new DerReader(value);
            java.util.ArrayList<DerNode> nodes = new java.util.ArrayList<>();
            while (reader.hasRemaining()) {
                nodes.add(reader.readNode());
            }
            return List.copyOf(nodes);
        }

        private static boolean isConstructed(int tag) {
            return (tag & 0x20) == 0x20;
        }

        private int readByte() {
            if (!hasRemaining()) {
                throw new IllegalArgumentException("DER truncado");
            }
            return encoded[pos++] & 0xFF;
        }

        private int readLength() {
            int first = readByte();
            if ((first & 0x80) == 0) {
                return first;
            }
            int bytes = first & 0x7F;
            if (bytes == 0 || bytes > 4) {
                throw new IllegalArgumentException("Tamanho DER invalido");
            }
            int length = 0;
            for (int i = 0; i < bytes; i++) {
                length = (length << 8) | readByte();
            }
            return length;
        }
    }
}
