package br.com.nfse.renomeador.text;

import java.util.ArrayList;
import java.util.List;

public final class TsvCodec {
    private TsvCodec() {
    }

    public static String join(String... values) {
        List<String> escaped = new ArrayList<>(values.length);
        for (String value : values) {
            escaped.add(escape(value));
        }
        return String.join("\t", escaped);
    }

    public static String[] split(String line, int expectedFields) {
        String[] raw = line.split("\t", -1);
        if (raw.length != expectedFields) {
            throw new IllegalArgumentException("Quantidade de campos invalida: esperado "
                    + expectedFields + ", recebido " + raw.length);
        }
        String[] values = new String[raw.length];
        for (int index = 0; index < raw.length; index++) {
            values[index] = unescape(raw[index]);
        }
        return values;
    }

    public static String escape(Object value) {
        String text = value == null ? "" : value.toString();
        StringBuilder escaped = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '\\' -> escaped.append("\\\\");
                case '\t' -> escaped.append("\\t");
                case '\r' -> escaped.append("\\r");
                case '\n' -> escaped.append("\\n");
                default -> escaped.append(current);
            }
        }
        return escaped.toString();
    }

    public static String unescape(String value) {
        StringBuilder unescaped = new StringBuilder(value.length());
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (!escaping) {
                if (current == '\\') {
                    escaping = true;
                } else {
                    unescaped.append(current);
                }
                continue;
            }
            switch (current) {
                case 't' -> unescaped.append('\t');
                case 'r' -> unescaped.append('\r');
                case 'n' -> unescaped.append('\n');
                case '\\' -> unescaped.append('\\');
                default -> {
                    unescaped.append('\\');
                    unescaped.append(current);
                }
            }
            escaping = false;
        }
        if (escaping) {
            unescaped.append('\\');
        }
        return unescaped.toString();
    }
}
