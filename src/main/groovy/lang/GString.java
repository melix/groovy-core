/*
 * Copyright 2003-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package groovy.lang;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.io.UnsupportedEncodingException;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Represents a String which contains embedded values such as "hello there
 * ${user} how are you?" which can be evaluated lazily. Advanced users can
 * iterate over the text and values to perform special processing, such as for
 * performing SQL operations, the values can be substituted for ? and the
 * actual value objects can be bound to a JDBC statement. The lovely name of
 * this class was suggested by Jules Gosnell and was such a good idea, I
 * couldn't resist :)
 *
 * @author <a href="mailto:james@coredevelopers.net">James Strachan</a>
 * @version $Revision$
 */
public abstract class GString extends GroovyObjectSupport implements Comparable, CharSequence, Writable, Buildable, Serializable {

    static final long serialVersionUID = -2638020355892246323L;

    private static final String[] EMPTY_STRING_ARRAY = new String[]{""};

    /**
     * A GString containing a single empty String and no values.
     */
    public static final GString EMPTY = new GString(new Object[0]) {
        public String[] getStrings() {
            return EMPTY_STRING_ARRAY;
        }
    };


    /**
     * Tests if an object is a known immutable. The code is kept
     * simple for improved performance. Adding too many cases
     * can lead to slower execution if a gstring is used only
     * once.
     * @param c an object to test
     * @return true if it's a known immutable
     */
    private static boolean isImmutable(Object c) {
        return c==null
                || c instanceof String
                || c instanceof Integer
                || c instanceof Character
                || c instanceof Double
                || c instanceof Boolean
                || c instanceof Float
                || c instanceof Long
                || c instanceof Short
                || c instanceof Byte;
    }

    private final Object[] values;

    private transient boolean cacheable = false;
    private transient SoftReference<String> cachedValue = new SoftReference<String>(null);

    public GString(Object values) {
        this((Object[]) values);
    }

    public GString(Object[] values) {
        this.values = values;
    }

    // will be static in an instance

    public abstract String[] getStrings();

    protected int estimateLength() {
        return (1+values.length) << 3;
    }

    /**
     * Overloaded to implement duck typing for Strings
     * so that any method that can't be evaluated on this
     * object will be forwarded to the toString() object instead.
     */
    public Object invokeMethod(String name, Object args) {
        try {
            return super.invokeMethod(name, args);
        }
        catch (MissingMethodException e) {
            // lets try invoke the method on the real String
            return InvokerHelper.invokeMethod(toString(), name, args);
        }
    }

    public Object[] getValues() {
        return values;
    }

    public GString plus(GString that) {
        final String[] thisStringsArray = getStrings();
        final Object[] thisValuesArray = getValues();
        final String[] thatStringsArray = that.getStrings();
        final Object[] thatValuesArray = that.getValues();

        List<String> stringList = new ArrayList<String>(thisStringsArray.length+thatStringsArray.length);
        List<Object> valueList = new ArrayList<Object>(thisValuesArray.length+thatValuesArray.length);

        Collections.addAll(stringList, thisStringsArray);
        Collections.addAll(valueList, thisValuesArray);

        List<String> thatStrings = Arrays.asList(thatStringsArray);
        if (stringList.size() > valueList.size()) {
            thatStrings = new ArrayList<String>(thatStrings);
            // merge onto end of previous GString to avoid an empty bridging value
            String s = stringList.get(stringList.size() - 1);
            s += thatStrings.get(0);
            thatStrings.remove(0);
            stringList.set(stringList.size() - 1, s);
        }

        stringList.addAll(thatStrings);
        Collections.addAll(valueList, thatValuesArray);

        final String[] newStrings = stringList.toArray(new String[stringList.size()]);
        Object[] newValues = valueList.toArray();

        return new GString(newValues) {
            public String[] getStrings() {
                return newStrings;
            }
        };
    }

    public GString plus(String that) {
        String[] currentStrings = getStrings();
        String[] newStrings;
        Object[] newValues;

        boolean appendToLastString = currentStrings.length > getValues().length;

        if (appendToLastString) {
            newStrings = new String[currentStrings.length];
        } else {
            newStrings = new String[currentStrings.length + 1];
        }
        newValues = new Object[getValues().length];
        int lastIndex = currentStrings.length;
        System.arraycopy(currentStrings, 0, newStrings, 0, lastIndex);
        System.arraycopy(getValues(), 0, newValues, 0, getValues().length);
        if (appendToLastString) {
            newStrings[lastIndex - 1] += that;
        } else {
            newStrings[lastIndex] = that;
        }

        final String[] finalStrings = newStrings;
        return new GString(newValues) {

            public String[] getStrings() {
                return finalStrings;
            }
        };
    }

    public int getValueCount() {
        return values.length;
    }

    public Object getValue(int idx) {
        return values[idx];
    }

    public String toString() {

        String cached = cachedValue.get();
        if (cached !=null) {
            return cached;
        }

        GStringWriter buffer = new GStringWriter(estimateLength());
        try {
            writeTo(buffer);
        }
        catch (IOException e) {
            throw new StringWriterIOException(e);
        }
        String val = buffer.toString();
        if (cacheable) {
            cachedValue = new SoftReference<String>(val);
        }
        return val;
    }

    public Writer writeTo(Writer out) throws IOException {
        String cached = cachedValue.get();
        if (cached !=null) {
            out.write(cached);
            return out;
        }
        final String[] s = getStrings();
        final Object[] values = this.values;
        final int numberOfValues = values.length;
        boolean immutable = true;
        for (int i = 0, size = s.length; i < size; i++) {
            out.write(s[i]);
            if (i < numberOfValues) {
                final Object value = values[i];

                if (value instanceof Closure) {
                    final Closure c = (Closure) value;

                    if (c.getMaximumNumberOfParameters() == 0) {
                        InvokerHelper.write(out, c.call());
                    } else if (c.getMaximumNumberOfParameters() == 1) {
                        c.call(out);
                    } else {
                        throw new GroovyRuntimeException("Trying to evaluate a GString containing a Closure taking "
                                + c.getMaximumNumberOfParameters() + " parameters");
                    }
                    immutable = false;
                } else {
                    InvokerHelper.write(out, value);
                    immutable &= isImmutable(value);
                }
            }
        }
        cacheable = immutable;
        return out;
    }

    /* (non-Javadoc)
     * @see groovy.lang.Buildable#build(groovy.lang.GroovyObject)
     */

    public void build(final GroovyObject builder) {
        final String[] s = getStrings();
        final int numberOfValues = values.length;

        for (int i = 0, size = s.length; i < size; i++) {
            builder.getProperty("mkp");
            builder.invokeMethod("yield", new Object[]{s[i]});
            if (i < numberOfValues) {
                builder.getProperty("mkp");
                builder.invokeMethod("yield", new Object[]{values[i]});
            }
        }
    }

    public boolean equals(Object that) {
        if (that instanceof GString) {
            return equals((GString) that);
        }
        return false;
    }

    public boolean equals(GString that) {
        return toString().equals(that.toString());
    }

    public int hashCode() {
        return 37 + toString().hashCode();
    }

    public int compareTo(Object that) {
        return toString().compareTo(that.toString());
    }

    public char charAt(int index) {
        return toString().charAt(index);
    }

    public int length() {
        return toString().length();
    }

    public CharSequence subSequence(int start, int end) {
        return toString().subSequence(start, end);
    }

    /**
     * Turns a String into a regular expression pattern
     *
     * @return the regular expression pattern
     */
    public Pattern negate() {
        return StringGroovyMethods.bitwiseNegate(toString());
    }

    public byte[] getBytes() {
        return toString().getBytes();
    }

    public byte[] getBytes(String charset) throws UnsupportedEncodingException {
       return toString().getBytes(charset);
    }

    /**
     * An optimized writer that uses a StringBuilder internally instead of
     * a StringBuffer like the {@link StringWriter} does. Unlike the string writer
     * this one doesn't check bounds.
     */
    private static class GStringWriter extends Writer {

        private final StringBuilder builder;

        public GStringWriter(int size) {
            builder = new StringBuilder(size);
        }

        public void write(int c) {
            builder.append((char) c);
        }

        public void write(char cbuf[], int off, int len) {
            builder.append(cbuf, off, len);
        }

        public void write(String str) {
            builder.append(str);
        }

        public void write(String str, int off, int len)  {
            builder.append(str.substring(off, off + len));
        }

        public GStringWriter append(CharSequence csq) {
            if (csq == null)
                write("null");
            else
                write(csq.toString());
            return this;
        }

        public GStringWriter append(CharSequence csq, int start, int end) {
            CharSequence cs = (csq == null ? "null" : csq);
            write(cs.subSequence(start, end).toString());
            return this;
        }

        public GStringWriter append(char c) {
            write(c);
            return this;
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public String toString() {
            return builder.toString();
        }
    }
}
