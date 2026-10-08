package com.frostether.frostchain;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    public static final int MAX_DEPTH = 64;

    private Json() {
    }

    public static String write(Object obj) {
        StringBuilder sb = new StringBuilder();
        write(sb, obj);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object obj) {
        boolean z = true;
        if (obj != null) {
            if (!(obj instanceof String)) {
                if (!(obj instanceof Boolean) && !(obj instanceof Integer) && !(obj instanceof Long) && !(obj instanceof BigInteger)) {
                    if ((obj instanceof Double) || (obj instanceof Float)) {
                        double doubleValue = ((Number) obj).doubleValue();
                        if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
                            sb.append("null");
                            return;
                        } else if (doubleValue != Math.rint(doubleValue) || Math.abs(doubleValue) >= 1.0E15d) {
                            sb.append(doubleValue);
                            return;
                        } else {
                            sb.append((long) doubleValue);
                            return;
                        }
                    }
                    if (obj instanceof Map) {
                        sb.append('{');
                        boolean z2 = true;
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                            if (!z2) {
                                sb.append(',');
                            }
                            quote(sb, (String) entry.getKey());
                            sb.append(':');
                            write(sb, entry.getValue());
                            z2 = false;
                        }
                        sb.append('}');
                        return;
                    }
                    if (obj instanceof List) {
                        sb.append('[');
                        for (Object obj2 : (List) obj) {
                            if (!z) {
                                sb.append(',');
                            }
                            write(sb, obj2);
                            z = false;
                        }
                        sb.append(']');
                        return;
                    }
                    if (obj instanceof int[]) {
                        sb.append('[');
                        int[] iArr = (int[]) obj;
                        for (int i = 0; i < iArr.length; i++) {
                            if (i > 0) {
                                sb.append(',');
                            }
                            sb.append(iArr[i]);
                        }
                        sb.append(']');
                        return;
                    }
                    quote(sb, obj.toString());
                    return;
                }
                sb.append(obj);
                return;
            }
            quote(sb, (String) obj);
            return;
        }
        sb.append("null");
    }

    private static void quote(StringBuilder sb, String str) {
        sb.append('\"');
        for (int i = 0; i < str.length(); i++) {
            char charAt = str.charAt(i);
            switch (charAt) {
                case '\t':
                    sb.append("\\t");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                default:
                    if (charAt >= ' ' && charAt != 8232 && charAt != 8233 && charAt != '<' && charAt != '>') {
                        sb.append(charAt);
                    } else {
                        sb.append(String.format("\\u%04x", Integer.valueOf(charAt)));
                    }
                    break;
            }
        }
        sb.append('\"');
    }

    public static Object parse(String str) {
        Reader reader = new Reader(str);
        reader.ws();
        Object value = reader.value(0);
        reader.ws();
        if (reader.i != str.length()) {
            throw reader.err("trailing characters");
        }
        return value;
    }

    public static Map<String, Object> obj(String str) {
        Object parse = parse(str);
        if (parse instanceof Map) {
            return (Map) parse;
        }
        throw new IllegalArgumentException("expected a JSON object");
    }

    private static final class Reader {
        int i;
        final String s;

        Reader(String str) {
            this.s = str;
        }

        IllegalArgumentException err(String str) {
            return new IllegalArgumentException("JSON " + str + " at " + this.i);
        }

        void ws() {
            while (this.i < this.s.length()) {
                char charAt = this.s.charAt(this.i);
                if (charAt != ' ' && charAt != '\t' && charAt != '\n' && charAt != '\r') {
                    return;
                } else {
                    this.i++;
                }
            }
        }

        Object value(int i) {
            if (i > 64) {
                throw err("too deeply nested");
            }
            if (this.i >= this.s.length()) {
                throw err("unexpected end");
            }
            char charAt = this.s.charAt(this.i);
            switch (charAt) {
                case '\"':
                    return string();
                case '[':
                    return array(i);
                case 'f':
                    lit("false");
                    return Boolean.FALSE;
                case 'n':
                    lit("null");
                    return null;
                case 't':
                    lit("true");
                    return Boolean.TRUE;
                case '{':
                    return object(i);
                default:
                    if (charAt == '-' || (charAt >= '0' && charAt <= '9')) {
                        return number();
                    }
                    throw err("unexpected character '" + charAt + "'");
            }
        }

        void lit(String str) {
            if (!this.s.startsWith(str, this.i)) {
                throw err("bad literal");
            }
            this.i += str.length();
        }

        // object() and array() were rebuilt from the 0.3.0 bytecode: the decompiled versions never returned.
        Map<String, Object> object(int i) {
            LinkedHashMap<String, Object> linkedHashMap = new LinkedHashMap<>();
            this.i++;
            ws();
            if (this.i < this.s.length() && this.s.charAt(this.i) == '}') {
                this.i++;
                return linkedHashMap;
            }
            while (true) {
                ws();
                if (this.i >= this.s.length() || this.s.charAt(this.i) != '\"') {
                    throw err("expected key");
                }
                String string = string();
                ws();
                if (this.i >= this.s.length() || this.s.charAt(this.i) != ':') {
                    throw err("expected ':'");
                }
                this.i++;
                ws();
                linkedHashMap.put(string, value(i + 1));
                ws();
                if (this.i >= this.s.length()) {
                    throw err("unexpected end");
                }
                char charAt = this.s.charAt(this.i++);
                if (charAt == '}') {
                    return linkedHashMap;
                }
                if (charAt != ',') {
                    throw err("expected ',' or '}'");
                }
            }
        }

        List<Object> array(int i) {
            ArrayList<Object> arrayList = new ArrayList<>();
            this.i++;
            ws();
            if (this.i < this.s.length() && this.s.charAt(this.i) == ']') {
                this.i++;
                return arrayList;
            }
            while (true) {
                ws();
                arrayList.add(value(i + 1));
                ws();
                if (this.i >= this.s.length()) {
                    throw err("unexpected end");
                }
                char charAt = this.s.charAt(this.i++);
                if (charAt == ']') {
                    return arrayList;
                }
                if (charAt != ',') {
                    throw err("expected ',' or ']'");
                }
            }
        }

        String string() {
            this.i++;
            StringBuilder sb = new StringBuilder();
            while (this.i < this.s.length()) {
                String str = this.s;
                int i = this.i;
                this.i = i + 1;
                char charAt = str.charAt(i);
                if (charAt == '\"') {
                    return sb.toString();
                }
                if (charAt == '\\') {
                    if (this.i >= this.s.length()) {
                        throw err("bad escape");
                    }
                    String str2 = this.s;
                    int i2 = this.i;
                    this.i = i2 + 1;
                    switch (str2.charAt(i2)) {
                        case '\"':
                            sb.append('\"');
                            break;
                        case '/':
                            sb.append('/');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        case 'b':
                            sb.append('\b');
                            break;
                        case 'f':
                            sb.append('\f');
                            break;
                        case 'n':
                            sb.append('\n');
                            break;
                        case 'r':
                            sb.append('\r');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case 'u':
                            if (this.i + 4 > this.s.length()) {
                                throw err("bad \\u escape");
                            }
                            try {
                                sb.append((char) Integer.parseInt(this.s.substring(this.i, this.i + 4), 16));
                                this.i += 4;
                                break;
                            } catch (NumberFormatException e) {
                                throw err("bad \\u escape");
                            }
                        default:
                            throw err("bad escape");
                    }
                } else {
                    if (charAt < ' ') {
                        throw err("control character in string");
                    }
                    sb.append(charAt);
                }
            }
            throw err("unterminated string");
        }

        Object number() {
            Object obj;
            int i = this.i;
            if (this.s.charAt(this.i) == '-') {
                this.i++;
            }
            while (this.i < this.s.length() && Character.isDigit(this.s.charAt(this.i))) {
                this.i++;
            }
            boolean z = false;
            if (this.i < this.s.length() && this.s.charAt(this.i) == '.') {
                this.i++;
                while (this.i < this.s.length() && Character.isDigit(this.s.charAt(this.i))) {
                    this.i++;
                }
                z = true;
            }
            if (this.i < this.s.length() && (this.s.charAt(this.i) == 'e' || this.s.charAt(this.i) == 'E')) {
                this.i++;
                if (this.i < this.s.length() && (this.s.charAt(this.i) == '+' || this.s.charAt(this.i) == '-')) {
                    this.i++;
                }
                while (this.i < this.s.length() && Character.isDigit(this.s.charAt(this.i))) {
                    this.i++;
                }
                z = true;
            }
            String substring = this.s.substring(i, this.i);
            if (substring.equals("-") || substring.isEmpty()) {
                throw err("bad number");
            }
            try {
                if (z) {
                    obj = Double.valueOf(Double.parseDouble(substring));
                } else {
                    BigInteger bigInteger = new BigInteger(substring);
                    int bitLength = bigInteger.bitLength();
                    obj = bigInteger;
                    if (bitLength < 64) {
                        obj = Long.valueOf(bigInteger.longValue());
                    }
                }
                return obj;
            } catch (NumberFormatException e) {
                throw err("bad number");
            }
        }
    }

    public static String str(Map<String, Object> map, String str) {
        Object obj = map.get(str);
        if (obj instanceof String) {
            return (String) obj;
        }
        throw new IllegalArgumentException("missing text field '" + str + "'");
    }

    public static String str(Map<String, Object> map, String str, String str2) {
        Object obj = map.get(str);
        return obj instanceof String ? (String) obj : str2;
    }

    public static long num(Map<String, Object> map, String str) {
        Object obj = map.get(str);
        if (obj instanceof Long) {
            return ((Long) obj).longValue();
        }
        if (obj instanceof Integer) {
            return ((Integer) obj).intValue();
        }
        if ((obj instanceof String) && ((String) obj).matches("-?[0-9]{1,19}")) {
            return Long.parseLong((String) obj);
        }
        throw new IllegalArgumentException("missing number field '" + str + "'");
    }

    public static long num(Map<String, Object> map, String str, long j) {
        return (!map.containsKey(str) || map.get(str) == null) ? j : num(map, str);
    }

    public static boolean bool(Map<String, Object> map, String str, boolean z) {
        Object obj = map.get(str);
        return obj instanceof Boolean ? ((Boolean) obj).booleanValue() : z;
    }

    public static Map<String, Object> map(Map<String, Object> map, String str) {
        Object obj = map.get(str);
        if (obj instanceof Map) {
            return (Map) obj;
        }
        throw new IllegalArgumentException("missing object field '" + str + "'");
    }

    public static List<Object> list(Map<String, Object> map, String str) {
        Object obj = map.get(str);
        if (obj == null) {
            return new ArrayList();
        }
        if (obj instanceof List) {
            return (List) obj;
        }
        throw new IllegalArgumentException("field '" + str + "' must be a list");
    }

    public static Map<String, Object> o(Object... objArr) {
        LinkedHashMap linkedHashMap = new LinkedHashMap();
        int i = 0;
        while (true) {
            int i2 = i;
            if (i2 + 1 >= objArr.length) {
                return linkedHashMap;
            }
            linkedHashMap.put((String) objArr[i2], objArr[i2 + 1]);
            i = i2 + 2;
        }
    }
}
