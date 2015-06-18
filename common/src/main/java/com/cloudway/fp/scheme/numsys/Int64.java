/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.fp.scheme.numsys;

import java.math.BigInteger;

import com.cloudway.fp.data.Rational;

public class Int64 extends Num {
    public static final Field<Int64> TAG = new Tag();

    static {
        Field.install(Int32.TAG, TAG, x -> new Int64(x.value));
    }

    public final long value;

    public Int64(long value) {
        this.value = value;
    }

    @Override
    public Field<Int64> tag() {
        return TAG;
    }

    @Override
    public boolean isZero() {
        return value == 0;
    }

    @Override
    public boolean isNegative() {
        return value < 0;
    }

    @Override
    public Num lower() {
        long r = value;
        if ((short)r == r)
            return new Int16((short)r);
        if ((int)r == r) {
            return new Int32((int)r);
        } else {
            return this;
        }
    }

    @Override
    public Object getObject() {
        return value;
    }

    @Override
    public Class<?> getObjectType() {
        long r = value;
        if ((short)r == r)
            return Short.class;
        if ((int)r == r)
            return Integer.class;
        return Long.class;
    }

    @Override
    public double toReal() {
        return value;
    }

    @Override
    public Num negate() {
        if (value == Long.MIN_VALUE) {
            return new BigInt(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
        } else {
            return new Int64(-value);
        }
    }

    @Override
    public String show() {
        return Long.toString(value);
    }

    @Override
    public String show(int radix) {
        return Long.toString(value, radix);
    }

    @Override
    public boolean eqv(Object obj) {
        if (obj == this)
            return true;
        return (obj instanceof Num) && ((Num)obj).equals(value);
    }

    @Override
    public boolean equals(long value) {
        return value == this.value;
    }

    @Override
    public boolean equals(Object obj) {
        return eqv(obj);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    public String toString() {
        return "#Int64(" + value + ")";
    }

    private static class Tag extends Field<Int64> {
        @Override
        public int compare(Int64 x, Int64 y) {
            return Long.compare(x.value, y.value);
        }

        @Override
        public Num add(Int64 x, Int64 y) {
            long a = x.value, b = y.value;
            long r = a + b;
            if (((a ^ r) & (b ^ r)) < 0) {
                return new BigInt(BigInteger.valueOf(a).add(BigInteger.valueOf(b)));
            } else if ((int)r == r) {
                return new Int32((int)r);
            } else {
                return new Int64(r);
            }
        }

        @Override
        public Num sub(Int64 x, Int64 y) {
            long a = x.value, b = y.value;
            long r = a - b;
            if (((a ^ b) & (a ^ r)) < 0) {
                return new BigInt(BigInteger.valueOf(a).subtract(BigInteger.valueOf(b)));
            } else if ((int)r == r) {
                return new Int32((int)r);
            } else {
                return new Int64(r);
            }
        }

        @Override
        public Num mul(Int64 x, Int64 y) {
            long a = x.value, b = y.value;
            long r = a * b;
            long ax = Math.abs(a);
            long ay = Math.abs(b);
            if ((ax | ay) >>> 31 != 0) {
                if (((b != 0) && (r / b != a) || (a == Long.MAX_VALUE && b == -1))) {
                    return new BigInt(BigInteger.valueOf(a).multiply(BigInteger.valueOf(b)));
                }
            }

            return (int)r == r ? new Int32((int)r) : new Int64(r);
        }

        @Override
        public Num div(Int64 x, Int64 y) {
            if (y.value == 1) {
                return x;
            } else {
                return new Ratio(Rational.valueOf(x.value, y.value)).lower();
            }
        }

        @Override
        public Num quotient(Int64 x, Int64 y) {
            return new Int64(x.value / y.value);
        }

        @Override
        public Num modulo(Int64 x, Int64 y) {
            long m = y.value;
            long r = x.value % m;
            if (r > 0 && m < 0 || r < 0 && m > 0)
                r += m;
            return new Int64(r);
        }

        @Override
        public Num remainder(Int64 x, Int64 y) {
            return new Int64(x.value % y.value);
        }
    }
}
