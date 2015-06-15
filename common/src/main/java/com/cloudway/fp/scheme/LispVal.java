/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.fp.scheme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.cloudway.fp.$;
import com.cloudway.fp.control.ConditionCase;
import com.cloudway.fp.data.Seq;
import com.cloudway.fp.data.Traversable;
import com.cloudway.fp.data.Vector;
import com.cloudway.fp.function.ExceptionSupplier;
import com.cloudway.fp.function.TriFunction;
import com.cloudway.fp.io.IO;
import com.cloudway.fp.io.IOConsumer;
import com.cloudway.fp.scheme.LispError.TypeMismatch;

/**
 * Represents a Lisp value.
 */
@SuppressWarnings("EqualsAndHashcode")
public interface LispVal {
    /**
     * Returns a string representation of the value.
     */
    default String show() {
        Printer pr = new Printer();
        pr.add(this);
        return pr.toString();
    }

    /**
     * General purpose display procedure that may handle circular references
     * and text substitution.
     */
    default void show(Printer pr) {
        pr.add(show());
    }

    /**
     * Returns true if this Lisp value represents an atom.
     */
    default boolean isSymbol() {
        return this instanceof Symbol;
    }

    /**
     * Returns true if this Lisp value represents a Pair.
     */
    default boolean isPair() {
        return this instanceof Pair;
    }

    /**
     * Returns true if this Lisp value represents a list.
     */
    default boolean isList() {
        return false;
    }

    /**
     * Returns true if this Lisp value represents a empty list.
     */
    default boolean isNil() {
        return this == Nil;
    }

    /**
     * Returns true if this Lisp value represents a false value.
     */
    default boolean isFalse() {
        return this == Bool.FALSE;
    }

    /**
     * Returns true if this Lisp value represents a true value.
     */
    default boolean isTrue() {
        return this != Bool.FALSE;
    }

    /**
     * Returns true if this Lisp value evaluating to itself.
     */
    default boolean isSelfEvaluating() {
        return false;
    }

    default LispVal getValue() {
        return this;
    }

    default LispVal map(Function<LispVal, LispVal> f) {
        LispVal rev = Nil;
        LispVal vals = this;
        while (vals.isPair()) {
            Pair p = (Pair)vals;
            rev = new Pair(f.apply(p.head), rev);
            vals = p.tail;
        }

        LispVal res = vals.isNil() ? Nil : f.apply(vals);
        while (rev.isPair()) {
            Pair p = (Pair)rev;
            res = new Pair(p.head, res);
            rev = p.tail;
        }

        return res;
    }

    @SuppressWarnings("unchecked")
    default $<Evaluator, LispVal>
    mapM(Evaluator m, Function<LispVal, $<Evaluator, ? extends LispVal>> f) {
        return ($<Evaluator, LispVal>)f.apply(this);
    }

    default boolean allMatch(Predicate<LispVal> p) {
        return p.test(this);
    }

    default $<Evaluator, Seq<LispVal>> toList(Evaluator m) {
        return m.throwE(new TypeMismatch("pair", this));
    }

    // -----------------------------------------------------------------------
    // Constructors

    class Symbol implements LispVal {
        private static final Pattern INVALID_SYMCHAR =
            Pattern.compile("[^a-zA-Z0-9!$%&*+\\-/:.<=>?@^_~]");

        public final String name;

        public Symbol(String name) {
            this.name = name;
        }

        @Override
        public String show() {
            if (name.isEmpty() || INVALID_SYMCHAR.matcher(name).find()) {
                return '|' + name + '|';
            } else {
                return name;
            }
        }

        public String toString() {
            return "#Symbol('" + name + "')";
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj.getClass() == this.getClass())
                return name.equals(((Symbol)obj).name);
            return false;
        }

        public int hashCode() {
            return name.hashCode();
        }
    }

    final class KeySym extends Symbol {
        public KeySym(String name) {
            super(name);
        }

        @Override
        public boolean isSymbol() {
            return false;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            return super.show() + ":";
        }
    }

    interface Text extends LispVal {
        @Override
        default boolean isSelfEvaluating() {
            return true;
        }

        String value();

        int length();
        char get(int i);
        void set(int i, char c);

        void fill(char c);
        Text copy();
        Text substring(int start, int end);
        void append(StringBuilder dest);

        LispVal toList();
    }

    final class CText implements Text {
        public final String value;

        public CText(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public int length() {
            return value.length();
        }

        @Override
        public char get(int i) {
            return value.charAt(i);
        }

        @Override
        public void set(int i, char c) {
            throw new LispError("string-set!: cannot modify constant string");
        }

        @Override
        public void fill(char c) {
            throw new LispError("string-fill!: cannot modify constant string");
        }

        @Override
        public Text copy() {
            char[] chars = new char[value.length()];
            value.getChars(0, value.length(), chars, 0);
            return new MText(chars);
        }

        @Override
        public Text substring(int start, int end) {
            return new MText(value.substring(start, end));
        }

        @Override
        public void append(StringBuilder dest) {
            dest.append(value);
        }

        @Override
        public LispVal toList() {
            LispVal res = Nil;
            for (int i = value.length(); --i >= 0; ) {
                res = new Pair(new Char(value.charAt(i)), res);
            }
            return res;
        }

        @Override
        public String show() {
            StringBuilder buf = new StringBuilder(value.length() + 2);
            buf.append('"');
            for (int i = 0, len = value.length(); i < len; i++) {
                char c = value.charAt(i);
                switch (c) {
                case '\b': buf.append("\\b"); break;
                case '\t': buf.append("\\t"); break;
                case '\f': buf.append("\\f"); break;
                case '\r': buf.append("\\r"); break;
                case '\n': buf.append("\\n"); break;
                case '\\': buf.append("\\\\"); break;
                case '"':  buf.append("\\\""); break;
                default:   buf.append(c);
                }
            }
            buf.append('"');
            return buf.toString();
        }

        public String toString() {
            return "#Text(\"" + value + "\")";
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj instanceof CText)
                return value.equals(((CText)obj).value);
            return false;
        }
    }

    final class MText implements Text {
        public final char[] value;

        public MText(char[] value) {
            this.value = value;
        }

        public MText(String str) {
            int len = str.length();
            value = new char[len];
            str.getChars(0, len, value, 0);
        }

        @Override
        public String value() {
            return new String(value);
        }

        @Override
        public int length() {
            return value.length;
        }

        @Override
        public char get(int i) {
            return value[i];
        }

        @Override
        public void set(int i, char c) {
            value[i] = c;
        }

        @Override
        public void fill(char c) {
            Arrays.fill(value, c);
        }

        @Override
        public Text copy() {
            return new MText(value.clone());
        }

        @Override
        public Text substring(int start, int end) {
            if (start < 0)
                throw new LispError("substring: index out of range: " + start + " " + end);
            if (end > value.length)
                throw new LispError("substring: index out of range: " + start + " " + end);
            if (end < start)
                throw new LispError("substring: index out of range: " + start + " " + end);
            return new MText(Arrays.copyOfRange(value, start, end));
        }

        @Override
        public void append(StringBuilder dest) {
            dest.append(value);
        }

        @Override
        public LispVal toList() {
            LispVal res = Nil;
            for (int i = value.length; --i >= 0; ) {
                res = new Pair(new Char(value[i]), res);
            }
            return res;
        }

        @Override
        public String show() {
            StringBuilder buf = new StringBuilder(value.length + 2);
            buf.append('"');
            for (char c : value) {
                switch (c) {
                case '\b': buf.append("\\b"); break;
                case '\t': buf.append("\\t"); break;
                case '\f': buf.append("\\f"); break;
                case '\r': buf.append("\\r"); break;
                case '\n': buf.append("\\n"); break;
                case '\\': buf.append("\\\\"); break;
                case '"':  buf.append("\\\""); break;
                default:   buf.append(c);
                }
            }
            buf.append('"');
            return buf.toString();
        }

        @Override
        public String toString() {
            return "#Text(\"" + new String(value) + "\")";
        }
    }

    final class Num implements LispVal {
        public final Number value;

        public Num(Number value) {
            this.value = value;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            return value.toString();
        }

        public String toString() {
            return "#Num(" + value.getClass().getSimpleName() + ", " + value + ")";
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj instanceof Num)
                return value.equals(((Num)obj).value);
            return false;
        }
    }

    final class Bool implements LispVal {
        public static final Bool TRUE  = new Bool(true);
        public static final Bool FALSE = new Bool(false);

        public static Bool valueOf(boolean value) {
            return value ? TRUE : FALSE;
        }

        public final boolean value;

        private Bool(boolean value) {
            this.value = value;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            return value ? "#t" : "#f";
        }

        public String toString() {
            return "#Bool(" + value + ")";
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj instanceof Bool)
                return value == ((Bool)obj).value;
            return false;
        }
    }

    final class Char implements LispVal {
        public final char value;

        public Char(char value) {
            this.value = value;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            switch (value) {
            case '\0':      return "#\\nul";
            case '\u0007':  return "#\\alarm";
            case '\u0008':  return "#\\backspace";
            case '\u0009':  return "#\\tab";
            case '\n':      return "#\\newline";
            case '\u000b':  return "#\\vtab";
            case '\u000c':  return "#\\page";
            case '\r':      return "#\\return";
            case '\u001b':  return "#\\esc";
            case ' ':       return "#\\space";
            case '\u007f':  return "#\\delete";
            default:        return "#\\" + value;
            }
        }

        public String toString() {
            return "#Char(" + value + ")";
        }

        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (obj instanceof Char)
                return value == ((Char)obj).value;
            return false;
        }
    }

    LispVal Nil = new LispVal() {
        @Override
        public boolean isList() {
            return true;
        }

        @Override
        public $<Evaluator, LispVal>
        mapM(Evaluator m, Function<LispVal, $<Evaluator, ? extends LispVal>> f) {
            return m.pure(this);
        }

        @Override
        public boolean allMatch(Predicate<LispVal> p) {
            return true;
        }

        @Override
        public $<Evaluator, Seq<LispVal>> toList(Evaluator m) {
            return m.pure(Seq.nil());
        }

        @Override
        public String show() {
            return "()";
        }

        public String toString() {
            return "#Nil";
        }
    };

    final class Pair implements LispVal {
        public LispVal head, tail;

        public static LispVal of(LispVal x) {
            return new Pair(x, Nil);
        }

        public static LispVal of(LispVal x, LispVal y) {
            return new Pair(x, new Pair(y, Nil));
        }

        public static LispVal fromList(Seq<? extends LispVal> vals) {
            return vals.foldRight_(Nil, Pair::new);
        }

        public Pair(LispVal head, LispVal tail) {
            this.head = head;
            this.tail = tail;
        }

        @Override
        public boolean isList() {
            LispVal t = this;
            while (t.isPair()) {
                t = ((Pair)t).tail;
                if (t == this)
                    return false;
            }
            return t.isNil();
        }

        @Override
        public $<Evaluator, Seq<LispVal>> toList(Evaluator m) {
            Seq<LispVal> res = Seq.nil();

            LispVal t = this;
            while (t.isPair()) {
                Pair p = (Pair)t;
                res = Seq.cons(p.head, res);
                t = p.tail;
            }

            if (t.isNil()) {
                return m.pure(res.reverse());
            } else {
                return m.throwE(new TypeMismatch("pair", t));
            }
        }

        @Override
        public $<Evaluator, LispVal>
        mapM(Evaluator m, Function<LispVal, $<Evaluator, ? extends LispVal>> f) {
            return m.bind(f.apply(head), x ->
                   m.map(tail.mapM(m, f), y ->
                   new Pair(x, y)));
        }

        @Override
        public boolean allMatch(Predicate<LispVal> pred) {
            LispVal t = this;
            while (t.isPair()) {
                Pair p = (Pair)t;
                if (!pred.test(p.head))
                    return false;
                t = p.tail;
            }
            return t.isNil() || pred.test(t);
        }

        @Override
        public void show(Printer pr) {
            String abbrev = abbreviation();
            if (abbrev != null && tail.isPair()) {
                Pair second = (Pair)tail;
                if (second.tail.isNil()) {
                    pr.add(abbrev);
                    pr.add(second.head);
                    return;
                }
            }

            showList(pr);
        }

        private String abbreviation() {
            if (head.isSymbol()) {
                switch (((Symbol)head).name) {
                case "quote":
                    return "'";
                case "quasiquote":
                    return "`";
                case "unquote":
                    return ",";
                case "unquote-splicing":
                    return ",@";
                }
            }
            return null;
        }

        private void showList(Printer pr) {
            Seq<LispVal> refs = Seq.nil();

            pr.addReference(this);
            refs = Seq.cons(this, refs);
            pr.add("(");
            pr.add(head);

            LispVal t = tail;
            while (t.isPair()) {
                if (pr.isReference(t)) {
                    pr.add(" . ");
                    pr.add(t);
                    pr.add(")");
                    refs.forEach(pr::removeReference);
                    return;
                } else {
                    Pair p = (Pair)t;
                    pr.add(" ");
                    pr.addReference(t);
                    refs = Seq.cons(t, refs);
                    pr.add(p.head);
                    t = p.tail;
                }
            }

            if (!t.isNil()) {
                pr.add(" . ");
                pr.add(t);
            }

            pr.add(")");
            refs.forEach(pr::removeReference);
        }

        public String toString() {
            return "#Pair(" + head + " " + tail + ")";
        }
    }

    final class Vec implements LispVal {
        public Vector<LispVal> value;

        public Vec(Traversable<Vector.µ, LispVal> value) {
            this.value = (Vector<LispVal>)value;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public void show(Printer pr) {
            if (value.isEmpty()) {
                pr.add("#()");
            } else {
                pr.addReference(this);
                pr.add("#(");
                pr.add(value.head());
                value.tail().forEach(x -> {
                    pr.add(" ");
                    pr.add(x);
                });
                pr.add(")");
                pr.removeReference(this);
            }
        }

        public String toString() {
            return value.show(" ", "#Vector(", ")");
        }
    }

    final class Promise implements LispVal {
        public final Env  env;
        public final Proc body;
        public $<Evaluator, LispVal> result;

        public Promise(Env env, Proc body) {
            this.body = body;
            this.env = env;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            return "#<promise>";
        }

        @Override
        public String toString() {
            return "#Promise(" + body.show() + ")";
        }
    }

    final class MultiVal implements LispVal {
        public final LispVal value;

        public MultiVal(LispVal value) {
            this.value = value;
        }

        @Override
        public LispVal getValue() {
            return value.isPair() ? ((Pair)value).head : Void.VOID;
        }

        @Override
        public boolean isTrue() {
            return getValue().isTrue();
        }

        @Override
        public boolean isFalse() {
            return getValue().isFalse();
        }

        @Override
        public void show(Printer pr) {
            LispVal args = value;
            while (args.isPair()) {
                Pair p = (Pair)args;
                pr.add(p.head);
                if (p.tail.isNil())
                    break;
                pr.add("\n");
                args = p.tail;
            }
        }

        public String toString() {
            return "#MultiVal(" + value + ")";
        }
    }

    final class Box implements LispVal {
        public LispVal value;

        public Box(LispVal value) {
            this.value = value;
        }

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public void show(Printer pr) {
            pr.addReference(this);
            pr.add("#&");
            pr.add(value);
        }

        public String toString() {
            return "#Box(" + value + ")";
        }
    }

    final class Prim implements LispVal {
        public final String name;
        public final PProc  proc;

        public Prim(String name, PProc proc) {
            this.name = name;
            this.proc = proc;
        }

        @Override
        public String show() {
            return "#<primitive:" + name + ">";
        }

        public String toString() {
            return "#Primitive:" + name;
        }
    }

    final class Func implements LispVal {
        public final String  name;
        public final LispVal params;
        public final Env     closure;
        public final Proc    body;

        public Func(LispVal params, Proc body, Env closure) {
            this("", params, body, closure);
        }

        public Func(String name, LispVal params, Proc body, Env closure) {
            this.name    = name;
            this.params  = params;
            this.body    = body;
            this.closure = closure;
        }

        @Override
        public void show(Printer pr) {
            pr.add("#<procedure:");
            pr.add(name);
            pr.add(" (lambda ");
            pr.add(params);
            pr.add(" ...)>");
        }

        public String toString() {
            return "#Lambda(" + params.show() + " " + body.show() + ")";
        }
    }

    final class Macro implements LispVal {
        public final String name;
        public final LispVal pattern;
        public final Proc body;

        public Macro(String name, LispVal pattern, Proc body) {
            this.name    = name;
            this.pattern = pattern;
            this.body    = body;
        }

        @Override
        public String show() {
            return "#<macro:" + name + ">";
        }

        public String toString() {
            return "#Macro(" + pattern.show() + " " + body.show() + ")";
        }
    }

    final class PrimMacro implements LispVal {
        public final String name;
        public final BiFunction<Env, LispVal, $<Evaluator, Proc>> proc;

        public PrimMacro(String name, BiFunction<Env, LispVal, $<Evaluator, Proc>> proc) {
            this.name = name;
            this.proc = proc;
        }

        @Override
        public String show() {
            return "<macro:" + name + ">";
        }

        public String toString() {
            return "#PrimMacro:" + name;
        }
    }

    final class Void implements LispVal {
        public static final Void VOID = new Void();

        private Void() {}

        @Override
        public boolean isSelfEvaluating() {
            return true;
        }

        @Override
        public String show() {
            return "#<void>";
        }

        public String toString() {
            return "#Void";
        }
    }

    // -----------------------------------------------------------------------
    // Runtime procedure

    @FunctionalInterface
    interface Proc extends LispVal, Function<Env, $<Evaluator, LispVal>> {
        @Override
        default String show() {
            return "#<procedure>";
        }
    }

    @FunctionalInterface
    interface PProc extends BiFunction<Env, LispVal, $<Evaluator, LispVal>> {}

    // -----------------------------------------------------------------------
    // Deconstructions

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Datum(Function<LispVal, ? extends R> mapper) {
        return t -> t.isSelfEvaluating()
            ? () -> mapper.apply(t)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Symbol(Function<Symbol, ? extends R> mapper) {
        return t -> t instanceof Symbol
            ? () -> mapper.apply((Symbol)t)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Text(Function<Text, ? extends R> mapper) {
        return t -> t instanceof Text
            ? () -> mapper.apply((Text)t)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Num(Function<Number, ? extends R> mapper) {
        return t -> t instanceof Num
            ? () -> mapper.apply(((Num)t).value)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Bool(Function<Boolean, ? extends R> mapper) {
        return t -> t instanceof Bool
            ? () -> mapper.apply(((Bool)t).value)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Quoted(Function<LispVal, ? extends R> mapper) {
        return t -> {
            Pair p, pp;
            if ((t.isPair()) &&
                (p = (Pair)t).head.isSymbol() &&
                "quote".equals(((Symbol)p.head).name) &&
                p.tail.isPair() &&
                (pp = (Pair)p.tail).tail.isNil()) {
                return () -> mapper.apply(pp.head);
            }
            return null;
        };
    }

    static <R, X extends Throwable> ConditionCase<LispVal, R, X>
    Nil(ExceptionSupplier<R, X> mapper) {
        return t -> t.isNil() ? mapper : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Pair(Function<Pair, ? extends R> mapper) {
        return t -> t instanceof Pair
            ? () -> mapper.apply((Pair)t)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Cons(BiFunction<LispVal, LispVal, ? extends R> mapper) {
        return t -> {
            if (t.isPair()) {
                Pair p = (Pair)t;
                return () -> mapper.apply(p.head, p.tail);
            }
            return null;
        };
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Cons(TriFunction<LispVal, LispVal, LispVal, ? extends R> mapper) {
        return t -> {
            Pair p, pp;
            if (t.isPair() && ((p = (Pair)t).tail.isPair())) {
                pp = (Pair)p.tail;
                return () -> mapper.apply(p.head, pp.head, pp.tail);
            }
            return null;
        };
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    List(Function<LispVal, ? extends R> mapper) {
        return t -> {
            Pair p;
            if (t.isPair() && ((p = (Pair)t).tail.isNil())) {
                return () -> mapper.apply(p.head);
            }
            return null;
        };
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    List(BiFunction<LispVal, LispVal, ? extends R> mapper) {
        return t -> {
            Pair p, pp;
            if (t.isPair() &&
                ((p = (Pair)t).tail.isPair()) &&
                ((pp = (Pair)p.tail).tail.isNil())) {
                return () -> mapper.apply(p.head, pp.head);
            }
            return null;
        };
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    List(TriFunction<LispVal, LispVal, LispVal, ? extends R> mapper) {
        return t -> {
            Pair p, pp, ppp;
            if (t.isPair() &&
                (p = (Pair)t).tail.isPair() &&
                (pp = (Pair)p.tail).tail.isPair() &&
                (ppp = (Pair)pp.tail).tail.isNil()) {
                return () -> mapper.apply(p.head, pp.head, ppp.head);
            }
            return null;
        };
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Vector(Function<Vector<LispVal>, ? extends R> mapper) {
        return t -> t instanceof Vec
            ? () -> mapper.apply(((Vec)t).value)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Prim(Function<PProc, ? extends R> mapper) {
        return t -> t instanceof Prim
            ? () -> mapper.apply(((Prim)t).proc)
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Func(Function<Func, ? extends R> mapper) {
        return t -> t instanceof Func
            ? () -> mapper.apply(((Func)t))
            : null;
    }

    static <R> ConditionCase<LispVal, R, RuntimeException>
    Macro(Function<Macro, ? extends R> mapper) {
        return t -> t instanceof Macro
            ? () -> mapper.apply(((Macro)t))
            : null;
    }

    static <R, X extends Throwable> ConditionCase<LispVal, R, X>
    Void(ExceptionSupplier<R, X> mapper) {
        return t -> (t instanceof Void) ? mapper : null;
    }

    // -----------------------------------------------------------------------
    // Printer

    class Printer {
        private static class RefID {
            int ref, pos;
            boolean active;

            RefID(int ref, int pos, boolean active) {
                this.ref = ref;
                this.pos = pos;
                this.active = active;
            }
        }

        private final IdentityHashMap<LispVal, RefID> references = new IdentityHashMap<>();
        private final ArrayList<String> buffer = new ArrayList<>();
        private int nextRefId;

        public void addReference(LispVal ref) {
            RefID refid = references.get(ref);
            if (refid == null) {
                references.put(ref, new RefID(-1, buffer.size(), true));
            } else {
                refid.active = true;
            }
        }

        public void removeReference(LispVal ref) {
            RefID refid = references.get(ref);
            if (refid != null) {
                refid.active = false;
            }
        }

        public boolean isReference(LispVal val) {
            RefID refid = references.get(val);
            return refid != null && refid.active;
        }

        public void add(LispVal val) {
            RefID refid = references.get(val);
            if (refid != null && refid.active) {
                if (refid.ref == -1)
                    refid.ref = nextRefId++;
                add("#" + refid.ref + "#");
            } else {
                val.show(this);
            }
        }

        public void add(String literal) {
            buffer.add(literal);
        }

        public void print(IOConsumer<String> out) throws IOException {
            backfill();
            IO.forEach(buffer, out);
        }

        public String toString() {
            backfill();
            StringBuilder out = new StringBuilder();
            buffer.forEach(out::append);
            return out.toString();
        }

        private void backfill() {
            for (RefID refid : references.values()) {
                if (refid.ref != -1) {
                    String s = buffer.get(refid.pos);
                    buffer.set(refid.pos, "#" + refid.ref + "=" + s);
                }
            }
        }
    }
}