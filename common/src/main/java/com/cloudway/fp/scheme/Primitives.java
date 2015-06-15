/**
 * Cloudway Platform
 * Copyright (c) 2012-2013 Cloudway Technology, Inc.
 * All rights reserved.
 */

package com.cloudway.fp.scheme;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.BiPredicate;

import com.cloudway.fp.$;
import com.cloudway.fp.data.Either;
import com.cloudway.fp.data.Fn;
import com.cloudway.fp.data.Maybe;
import com.cloudway.fp.data.Tuple;
import com.cloudway.fp.data.Vector;
import com.cloudway.fp.scheme.numsys.Num;

import static com.cloudway.fp.control.Conditionals.with;
import static com.cloudway.fp.control.Syntax.do_;
import static com.cloudway.fp.scheme.LispError.*;
import static com.cloudway.fp.scheme.LispVal.*;
import static com.cloudway.fp.scheme.LispVal.Void;
import static java.lang.Character.toLowerCase;

@SuppressWarnings("unused")
public final class Primitives {
    private Primitives() {}

    @Name("boolean?")
    public static boolean isBoolean(LispVal val) {
        return val instanceof Bool;
    }

    @Name("symbol?")
    public static boolean isSymbol(LispVal val) {
        return val.isSymbol();
    }

    @Name("keyword?")
    public static boolean isKeyword(LispVal val) {
        return val instanceof KeySym;
    }

    @Name("char?")
    public static boolean isChar(LispVal val) {
        return val instanceof Char;
    }

    @Name("string?")
    public static boolean isString(LispVal val) {
        return val instanceof Text;
    }

    @Name("vector?")
    public static boolean isVector(LispVal val) {
        return val instanceof Vec;
    }

    @Name("promise?")
    public static boolean isPromise(LispVal val) {
        return val instanceof Promise;
    }

    @Name("list?")
    public static boolean isList(LispVal val) {
        return val.isList();
    }

    @Name("pair?")
    public static boolean isPair(LispVal val) {
        return val instanceof Pair;
    }

    @Name("null?")
    public static boolean isNull(LispVal val) {
        return val.isNil();
    }

    @Name("procedure?")
    public static boolean isProcedure(LispVal val) {
        return (val instanceof Prim) || (val instanceof Func);
    }

    // ---------------------------------------------------------------------

    @Name("eq?")
    public static boolean eq(LispVal x, LispVal y) {
        return x.equals(y);
    }

    @Name("eqv?")
    public static boolean eqv(LispVal x, LispVal y) {
        return x.equals(y);
    }

    @Name("equal?")
    public static boolean equal(LispVal x, LispVal y) {
        if (x == y) {
            return true;
        }

        return with(x, y).<Boolean>get()
            .when(Pair(xs -> Pair(ys -> listEqual(xs, ys))))
            .when(Vector(xs -> Vector(ys -> vectorEqual(xs, ys))))
            .orElseGet(() -> x.equals(y));
    }

    private static boolean listEqual(LispVal xs, LispVal ys) {
        while (xs.isPair() && ys.isPair()) {
            Pair px = (Pair)xs, py = (Pair)ys;
            if (!equal(px.head, py.head))
                return false;
            xs = px.tail;
            ys = py.tail;
        }
        return xs.equals(ys);
    }

    private static boolean vectorEqual(Vector<LispVal> x, Vector<LispVal> y) {
        if (x.size() != y.size())
            return false;

        Iterator<LispVal> i1 = x.iterator();
        Iterator<LispVal> i2 = y.iterator();
        while (i1.hasNext() && i2.hasNext()) {
            if (!equal(i1.next(), i2.next()))
                return false;
        }
        return true;
    }

    // ---------------------------------------------------------------------

    public static LispVal cons(LispVal x, LispVal y) {
        return new Pair(x, y);
    }

    public static LispVal car(Pair pair) {
        return pair.head;
    }

    public static LispVal cdr(Pair pair) {
        return pair.tail;
    }

    @Name("set-car!")
    public static void set_car(Pair pair, LispVal val) {
        pair.head = val;
    }

    @Name("set-cdr!")
    public static void set_cdr(Pair pair, LispVal val) {
        pair.tail = val;
    }

    @VarArgs
    public static LispVal list(LispVal args) {
        return args;
    }

    public static long length(LispVal lst) {
        long len = 0;
        while (lst.isPair()) {
            len++;
            lst = ((Pair)lst).tail;
        }

        if (lst.isNil()) {
            return len;
        } else {
            throw new TypeMismatch("pair", lst);
        }
    }

    public static LispVal list_ref(LispVal lst, int k) {
        while (k != 0 && lst.isPair()) {
            k--;
            lst = ((Pair)lst).tail;
        }

        if (k == 0 && lst.isPair()) {
            return ((Pair)lst).head;
        } else {
            throw new TypeMismatch("pair", lst);
        }
    }

    public static LispVal list_tail(LispVal lst, int k) {
        while (k != 0 && lst.isPair()) {
            k--;
            lst = ((Pair)lst).tail;
        }

        if (k == 0) {
            return lst;
        } else {
            throw new TypeMismatch("pair", lst);
        }
    }

    private static LispVal mem_helper(LispVal item, LispVal lst, BiPredicate<LispVal, LispVal> pred) {
        while (lst.isPair()) {
            Pair p = (Pair)lst;
            if (pred.test(item, p.head))
                return lst;
            lst = p.tail;
        }

        if (lst.isNil()) {
            return Bool.FALSE;
        } else {
            throw new TypeMismatch("pair", lst);
        }
    }

    public static LispVal memq(LispVal item, LispVal lst) {
        return mem_helper(item, lst, Primitives::eq);
    }

    public static LispVal memv(LispVal item, LispVal lst) {
        return mem_helper(item, lst, Primitives::eqv);
    }

    public static LispVal member(LispVal item, LispVal lst) {
        return mem_helper(item, lst, Primitives::equal);
    }

    private static LispVal ass_helper(LispVal obj, LispVal alist, BiPredicate<LispVal, LispVal> pred) {
        while (alist.isPair()) {
            Pair p = (Pair)alist;
            if (p.head.isPair()) {
                if (pred.test(obj, ((Pair)p.head).head))
                    return p.head;
            } else {
                throw new TypeMismatch("pair", p.head);
            }
            alist = p.tail;
        }

        if (alist.isNil()) {
            return Bool.FALSE;
        } else {
            throw new TypeMismatch("pair", alist);
        }
    }

    public static LispVal assq(LispVal obj, LispVal alist) {
        return ass_helper(obj, alist, Primitives::eq);
    }

    public static LispVal assv(LispVal obj, LispVal alist) {
        return ass_helper(obj, alist, Primitives::eqv);
    }

    public static LispVal assoc(LispVal obj, LispVal alist) {
        return ass_helper(obj, alist, Primitives::equal);
    }

    @VarArgs
    public static $<Evaluator, LispVal> append(Evaluator me, LispVal lists) {
        return with(lists).<$<Evaluator, LispVal>>get()
            .when(Nil(() -> me.pure(Nil)))
            .when(List(x -> me.pure(x)))
            .when(List((x, y) -> append(me, x, y)))
            .when(Cons((x, y, ys) ->
                do_(append(me, x, y), hd ->
                do_(append(me, ys), tl ->
                do_(append(me, hd, tl))))))
            .orElseGet(() -> me.throwE(new TypeMismatch("pair", lists)));
    }

    static $<Evaluator, LispVal> append(Evaluator me, LispVal x, LispVal y) {
        if (x.isNil()) {
            return me.pure(y);
        } else if (y.isNil()) {
            return me.pure(x);
        } else {
            return do_(reverse(me, x), rev -> reverseCons(me, rev, y));
        }
    }

    public static $<Evaluator, LispVal> reverse(Evaluator me, LispVal lst) {
        return reverseCons(me, lst, Nil);
    }

    private static $<Evaluator, LispVal> reverseCons(Evaluator me, LispVal hd, LispVal tl) {
        while (hd.isPair()) {
            Pair p = (Pair)hd;
            tl = new Pair(p.head, tl);
            hd = p.tail;
        }
        return hd.isNil() ? me.pure(tl) : me.throwE(new TypeMismatch("pair", hd));
    }

    @VarArgs
    public static $<Evaluator, LispVal>
    map(Evaluator me, Env env, LispVal f, LispVal lst, LispVal more) {
        if (lst.isNil()) {
            return me.pure(LispVal.Nil);
        }

        if (lst.isPair()) {
            Pair p = (Pair)lst;
            if (more.isNil()) {
                return do_(me.apply(env, f, Pair.of(p.head)), x ->
                       do_(map(me, env, f, p.tail, Nil), xs ->
                       me.pure(cons(x, xs))));
            } else {
                return do_(cars_cdrs(me, more), (as, ds) ->
                       do_(me.apply(env, f, cons(p.head, as)), x ->
                       do_(map(me, env, f, p.tail, ds), xs ->
                       me.pure(cons(x, xs)))));
            }
        }

        return me.throwE(new TypeMismatch("pair", lst));
    }

    @VarArgs
    public static $<Evaluator, LispVal>
    flatmap(Evaluator me, Env env, LispVal f, LispVal lst, LispVal more) {
        if (lst.isNil()) {
            return me.pure(LispVal.Nil);
        }

        if (lst.isPair()) {
            Pair p = (Pair)lst;
            if (more.isNil()) {
                return do_(me.apply(env, f, Pair.of(p.head)), xs ->
                       do_(flatmap(me, env, f, p.tail, Nil), ys ->
                       append(me, xs, ys)));
            } else {
                return do_(cars_cdrs(me, more), (as, ds) ->
                       do_(me.apply(env, f, cons(p.head, as)), xs ->
                       do_(flatmap(me, env, f, p.tail, ds), ys ->
                       append(me, xs, ys))));
            }
        }

        return me.throwE(new TypeMismatch("pair", lst));
    }

    @VarArgs
    public static $<Evaluator, LispVal>
    filter(Evaluator me, Env env, LispVal pred, LispVal lst, LispVal more) {
        if (lst.isNil()) {
            return me.pure(LispVal.Nil);
        }

        if (lst.isPair()) {
            Pair p = (Pair)lst;
            if (more.isNil()) {
                return do_(me.apply(env, pred, Pair.of(p.head)), result ->
                       result.isTrue()
                         ? do_(filter(me, env, pred, p.tail, Nil), rest ->
                           do_(me.pure(cons(p.head, rest))))
                         : filter(me, env, pred, p.tail, Nil));
            } else {
                return do_(cars_cdrs(me, more), (as, ds) ->
                       do_(me.apply(env, pred, cons(p.head, as)), result ->
                       result.isTrue()
                         ? do_(filter(me, env, pred, p.tail, ds), rest ->
                           do_(me.pure(cons(p.head, rest))))
                         : filter(me, env, pred, p.tail, ds)));
            }
        }

        return me.throwE(new TypeMismatch("pair", lst));
    }

    @VarArgs
    public static $<Evaluator, LispVal>
    for_each(Evaluator me, Env env, LispVal proc, LispVal lst, LispVal more) {
        if (lst.isNil()) {
            return me.pure(Void.VOID);
        }

        if (lst.isPair()) {
            Pair p = (Pair)lst;
            if (more.isNil()) {
                return do_(me.apply(env, proc, Pair.of(p.head)), () ->
                       do_(for_each(me, env, proc, p.tail, Nil)));
            } else {
                return do_(cars_cdrs(me, more), (as, ds) ->
                       do_(me.apply(env, proc, cons(p.head, as)), () ->
                       do_(for_each(me, env, proc, p.tail, ds))));
            }
        }

        return me.throwE(new TypeMismatch("pair", lst));
    }

    public static $<Evaluator, LispVal>
    fold_right(Evaluator me, Env env, LispVal op, LispVal init, LispVal lst) {
        if (lst.isNil()) {
            return me.pure(init);
        }

        if (lst.isPair()) {
            Pair p = (Pair)lst;
            return do_(me.delay(() -> fold_right(me, env, op, init, p.tail)), rest ->
                   do_(me.apply(env, op, Pair.of(p.head, rest))));
        }

        return me.throwE(new TypeMismatch("pair", lst));
    }

    public static $<Evaluator, LispVal>
    fold_left(Evaluator me, Env env, LispVal op, LispVal init, LispVal lst) {
        $<Evaluator, LispVal> res = me.pure(init);
        while (lst.isPair()) {
            Pair p = (Pair)lst;
            res = me.bind(res, r -> me.apply(env, op, Pair.of(r, p.head)));
            lst = p.tail;
        }
        return lst.isNil() ? res : me.throwE(new TypeMismatch("pair", lst));
    }

    private static $<Evaluator, Tuple<LispVal, LispVal>> cars_cdrs(Evaluator me, LispVal lists) {
        LispVal cars = Nil, cdrs = Nil;

        while (lists.isPair()) {
            Pair p = (Pair)lists;
            if (p.head.isNil()) {
                break;
            }

            if (p.head.isPair()) {
                Pair sub = (Pair)p.head;
                cars = cons(sub.head, cars);
                cdrs = cons(sub.tail, cdrs);
            } else {
                return me.throwE(new TypeMismatch("pair", p.head));
            }

            lists = p.tail;
        }

        if (lists.isNil()) {
            return me.liftM2(Tuple::of, reverse(me, cars), reverse(me, cdrs));
        } else {
            return me.throwE(new TypeMismatch("pair", lists));
        }
    }

    // ---------------------------------------------------------------------

    @Name("symbol->string")
    public static String symbol2string(Symbol sym) {
        return sym.name;
    }

    @Name("string->symbol")
    public static Symbol string2symbol(Evaluator me, String str) {
        return me.getsym(str);
    }

    @Name("keyword->string")
    public static String keyword2string(KeySym sym) {
        return sym.name;
    }

    @Name("string->keyword")
    public static KeySym string2keyword(Evaluator me, String str) {
        return me.getkeysym(str);
    }

    // ---------------------------------------------------------------------

    @Name("char=?")
    public static boolean char_eq(char c1, char c2) {
        return c1 == c2;
    }

    @Name("char<?")
    public static boolean char_lt(char c1, char c2) {
        return c1 < c2;
    }

    @Name("char>?")
    public static boolean char_gt(char c1, char c2) {
        return c1 > c2;
    }

    @Name("char<=?")
    public static boolean char_le(char c1, char c2) {
        return c1 <= c2;
    }

    @Name("char>=?")
    public static boolean char_ge(char c1, char c2) {
        return c1 >= c2;
    }

    @Name("char-ci=?")
    public static boolean char_ci_eq(char c1, char c2) {
        return toLowerCase(c1) == toLowerCase(c2);
    }

    @Name("char-ci<?")
    public static boolean char_ci_lt(char c1, char c2) {
        return toLowerCase(c1) < toLowerCase(c2);
    }

    @Name("char-ci>?")
    public static boolean char_ci_gt(char c1, char c2) {
        return toLowerCase(c1) > toLowerCase(c2);
    }

    @Name("char-ci<=?")
    public static boolean char_ci_le(char c1, char c2) {
        return toLowerCase(c1) <= toLowerCase(c2);
    }

    @Name("char-ci>=?")
    public static boolean char_ci_ge(char c1, char c2) {
        return toLowerCase(c1) >= toLowerCase(c2);
    }

    @Name("char-alphabetic?")
    public static boolean char_alphabetic(char c) {
        return Character.isAlphabetic(c);
    }

    @Name("char-numeric?")
    public static boolean char_numeric(char c) {
        return Character.isDigit(c);
    }

    @Name("char-whitespace?")
    public static boolean char_whitespace(char c) {
        return Character.isWhitespace(c);
    }

    @Name("char-upper-case?")
    public static boolean char_upper_case(char c) {
        return Character.isUpperCase(c);
    }

    @Name("char-lower-case?")
    public static boolean char_lower_case(char c) {
        return Character.isLowerCase(c);
    }

    @Name("char->integer")
    public static int char2integer(char c) {
        return c;
    }

    @Name("integer->char")
    public static char integer2char(int n) {
        return (char)n;
    }

    public static char char_upcase(char c) {
        return Character.toUpperCase(c);
    }

    public static char char_downcase(char c) {
        return Character.toLowerCase(c);
    }

    // ---------------------------------------------------------------------

    public static Text make_string(int k, Maybe<Character> c) {
        if (k < 0) throw new TypeMismatch("nonnegative-integer", Num.make(k));

        char[] chars = new char[k];
        Arrays.fill(chars, c.orElse(' '));
        return new MText(chars);
    }

    @VarArgs
    public static Text string(LispVal args) {
        StringBuilder buf = new StringBuilder();
        while (!args.isNil()) {
            Pair p = (Pair)args;
            if (!(p.head instanceof Char))
                throw new TypeMismatch("char", p.head);
            buf.append(((Char)p.head).value);
            args = p.tail;
        }

        if (args.isNil()) {
            return new MText(buf.toString());
        } else {
            throw new TypeMismatch("pair", args);
        }
    }

    public static int string_length(Text str) {
        return str.length();
    }

    public static char string_ref(Text str, int k) {
        return str.get(k);
    }

    @Name("string-set!")
    public static void string_set(Text t, int k, char c) {
        t.set(k, c);
    }

    @Name("string-fill!")
    public static void string_fill(Text t, char c) {
        t.fill(c);
    }

    public static Text substring(Text str, int start, int end) {
        return str.substring(start, end);
    }

    @VarArgs
    public static Text string_append(LispVal args) {
        StringBuilder buf = new StringBuilder();
        while (args.isPair()) {
            Pair p = (Pair)args;
            if (!(p.head instanceof Text))
                throw new TypeMismatch("string", p.head);
            ((Text)p.head).append(buf);
            args = p.tail;
        }

        if (!args.isNil()) {
            throw new TypeMismatch("pair", args);
        } else {
            return new MText(buf.toString());
        }
    }

    @Name("string->list")
    public static LispVal string2list(Text str) {
        return str.toList();
    }

    @Name("list->string")
    public static Text list2string(LispVal lst) {
        return string(lst);
    }

    public static Text string_copy(Text str) {
        return str.copy();
    }

    @Name("string=?")
    public static boolean string_eq(String x, String y) {
        return x.equals(y);
    }

    @Name("string<?")
    public static boolean string_lt(String x, String y) {
        return x.compareTo(y) < 0;
    }

    @Name("string<=?")
    public static boolean string_le(String x, String y) {
        return x.compareTo(y) <= 0;
    }

    @Name("string>?")
    public static boolean string_gt(String x, String y) {
        return x.compareTo(y) > 0;
    }

    @Name("string>=?")
    public static boolean string_ge(String x, String y) {
        return x.compareTo(y) >= 0;
    }

    @Name("string-ci=?")
    public static boolean string_ci_eq(String x, String y) {
        return x.compareToIgnoreCase(y) == 0;
    }

    @Name("string-ci<?")
    public static boolean string_ci_lt(String x, String y) {
        return x.compareToIgnoreCase(y) < 0;
    }

    @Name("string-ci<=?")
    public static boolean string_ci_le(String x, String y) {
        return x.compareToIgnoreCase(y) <= 0;
    }

    @Name("string-ci>?")
    public static boolean string_ci_gt(String x, String y) {
        return x.compareToIgnoreCase(y) > 0;
    }

    @Name("string-ci>=?")
    public static boolean string_ci_ge(String x, String y) {
        return x.compareToIgnoreCase(y) >= 0;
    }

    // ---------------------------------------------------------------------

    public static Vec make_vector(int k, Maybe<LispVal> fill) {
        return new Vec(Vector.iterate(k, __ -> fill.orElse(Num.ZERO)));
    }

    @VarArgs
    public static $<Evaluator, LispVal> vector(Evaluator me, LispVal args) {
        return me.map(args.toList(me), xs -> new Vec(Vector.fromList(xs)));
    }

    public static int vector_length(Vec vec) {
        return vec.value.size();
    }

    public static LispVal vector_ref(Vec vec, int k) {
        return vec.value.at(k);
    }

    @Name("vector-set!")
    public static void vector_set(Vec vec, int k, LispVal obj) {
        vec.value = vec.value.update(k, obj);
    }

    @Name("vector->list")
    public static LispVal vector2list(Vec vec) {
        return Pair.fromList(vec.value.asList());
    }

    @Name("list->vector")
    public static $<Evaluator, LispVal> list2vector(Evaluator me, LispVal lst) {
        return me.map(lst.toList(me), xs -> new Vec(Vector.fromList(xs)));
    }

    @Name("vector-fill!")
    public static void vector_fill(Vec vec, LispVal fill) {
        vec.value = vec.value.map(Fn.pure(fill));
    }

    // ---------------------------------------------------------------------

    public static LispVal box(LispVal value) {
        return new Box(value);
    }

    @Name("box?")
    public static boolean isBox(LispVal val) {
        return val instanceof Box;
    }

    public static LispVal unbox(LispVal val) {
        return (val instanceof Box) ? ((Box)val).value : val;
    }

    @Name("set-box!")
    public static void set_box(LispVal box, LispVal value) {
        if (box instanceof Box) {
            ((Box)box).value = value;
        } else {
            throw new TypeMismatch("box", box);
        }
    }

    // ---------------------------------------------------------------------

    public static Env scheme_report_environment(Evaluator me, int version) {
        if (version != 5)
            throw new TypeMismatch("<5>", Num.make(version));
        return me.getSchemeReportEnv();
    }

    public static Env null_environment(Evaluator me, int version) {
        if (version != 5)
            throw new TypeMismatch("<5>", Num.make(version));
        return me.getNullEnv();
    }

    public static Env interaction_environment(Evaluator me, Env env) {
        return me.getInteractionEnv(env);
    }

    public static $<Evaluator, LispVal> eval(Evaluator me, LispVal exp, Env env) {
        return me.eval(env, exp);
    }

    @VarArgs
    public static $<Evaluator, LispVal>
    apply(Evaluator me, Env env, LispVal func, LispVal args, LispVal rest) {
        if (rest.isNil()) {
            return me.apply(env, func, args);
        }

        args = Pair.of(args);
        while (rest.isPair()) {
            Pair p = (Pair)rest;
            if (p.tail.isNil()) {
                rest = p.head;
                break;
            } else {
                args = new Pair(p.head, args);
                rest = p.tail;
            }
        }

        LispVal last = rest;
        return do_(reverse(me, args), rev ->
            do_(append(me, rev, last), app ->
                me.apply(env, func, app)));
    }

    @Syntax
    public static $<Evaluator, Proc> delay(Evaluator me, Env ctx, LispVal args) {
        return me.map(me.analyzeSequence(ctx, args), proc ->
            env -> me.pure(new Promise(env, proc)));
    }

    public static $<Evaluator, LispVal> force(Evaluator me, LispVal t) {
        if (t instanceof Promise) {
            Promise p = (Promise)t;

            if (p.result != null) {
                return p.result;
            }

            return me.catchE(err -> p.result = me.throwE(err),
                             p.result = p.body.apply(p.env));
        } else {
            return me.pure(t);
        }
    }

    public static $<Evaluator, LispVal>
    call_with_current_continuation(Evaluator me, Env env, LispVal proc) {
        return me.callCC(env, proc);
    }

    @VarArgs
    public static $<Evaluator, LispVal> values(Evaluator me, LispVal args) {
        return me.values(args);
    }

    public static $<Evaluator, LispVal>
    call_with_values(Evaluator me, Env env, LispVal producer, LispVal consumer) {
        return me.callWithValues(env, producer, consumer);
    }

    public static $<Evaluator, LispVal>
    dynamic_wind(Evaluator me, Env env, LispVal before, LispVal thunk, LispVal after) {
        return me.dynamicWind(env, before, thunk, after);
    }

    @Syntax
    public static $<Evaluator, Proc> reset(Evaluator me, Env ctx, LispVal args) {
        return me.map(me.analyzeSequence(ctx, args), proc -> env -> me.reset(env, proc));
    }

    @Syntax
    public static $<Evaluator, Proc> shift(Evaluator me, Env ctx, LispVal args) {
        return with(args).<$<Evaluator, Proc>>get()
            .when(Cons((id, body) ->
               id.isSymbol()
                 ? me.map(me.analyzeSequence(ctx, body), proc ->
                   env -> me.shift(env, (Symbol)id, proc))
                 : badSyntax(me, "shift", args)))
            .orElseGet(() -> badSyntax(me, "shift", args));
    }

    @VarArgs
    public static $<Evaluator, LispVal> error(Evaluator me, LispVal args) {
        LispVal caller = null;
        String  message;

        if (args.isPair() && ((Pair)args).head.isSymbol()) {
            caller = ((Pair)args).head;
            args = ((Pair)args).tail;
        }

        if (args.isPair()) {
            Pair p = (Pair)args;
            if (p.head instanceof Text) {
                message = ((Text)p.head).value();
                args = p.tail;
            } else {
                return me.throwE(new TypeMismatch("string", p.head));
            }
        } else {
            return me.throwE(new NumArgs(1, args));
        }

        Printer pr = new Printer();
        if (caller != null) {
            pr.add(caller);
            pr.add(": ");
        }
        pr.add(message);
        while (args.isPair()) {
            Pair p = (Pair)args;
            pr.add(" ");
            pr.add(p.head);
            args = p.tail;
        }

        return args.isNil()
            ? me.throwE(new LispError(pr.toString()))
            : me.throwE(new TypeMismatch("pair", args));
    }

    public static Either<LispError, LispVal> raise(LispVal obj) {
        return Either.left(new Condition(obj));
    }

    public static $<Evaluator, LispVal>
    with_exception_handler(Evaluator me, Env env, LispVal handler, LispVal thunk) {
        return me.catchE(e -> e instanceof Condition
                             ? me.apply(env, handler, Pair.of(((Condition)e).value))
                             : me.throwE(e),
                         me.apply(env, thunk, Nil));
    }

    @Syntax
    public static $<Evaluator, Proc> and(Evaluator me, Env ctx, LispVal args) {
        if (args.isNil()) {
            return me.pure(env -> me.pure(Bool.TRUE));
        }

        if (args.isPair()) {
            Pair p = (Pair)args;
            if (p.tail.isNil()) {
                return me.analyze(ctx, p.head);
            } else {
                return do_(me.analyze(ctx, p.head), first ->
                       do_(and(me, ctx, p.tail), rest ->
                       me.pure(env -> do_(first.apply(env), result ->
                                      result.isFalse() ? me.pure(result)
                                                       : rest.apply(env)))));
            }
        }

        return badSyntax(me, "and", args);
    }

    @Syntax
    public static $<Evaluator, Proc> or(Evaluator me, Env ctx, LispVal args) {
        if (args.isNil()) {
            return me.pure(env -> me.pure(Bool.FALSE));
        }

        if (args.isPair()) {
            Pair p = (Pair)args;
            if (p.tail.isNil()) {
                return me.analyze(ctx, p.head);
            } else {
                return do_(me.analyze(ctx, p.head), first ->
                       do_(or(me, ctx, p.tail), rest ->
                       me.pure(env -> do_(first.apply(env), result ->
                                      result.isTrue() ? me.pure(result)
                                                      : rest.apply(env)))));
            }
        }

        return badSyntax(me, "or", args);
    }

    public static boolean not(LispVal val) {
        return val == Bool.FALSE;
    }

    @Name("void") @VarArgs
    public static void void_(LispVal args) {
        // args ignored
    }

    public static Symbol gensym(Env env, Maybe<LispVal> prefix) {
        if (prefix.isAbsent()) {
            return env.newsym();
        }

        LispVal x = prefix.get();
        if (x instanceof Symbol) {
            return env.newsym(((Symbol)x).name);
        } else if (x instanceof Text) {
            return env.newsym(((Text)x).value());
        } else {
            throw new TypeMismatch("string or symbol", x);
        }
    }

    private static <A> $<Evaluator, A> badSyntax(Evaluator me, String name, LispVal val) {
        return me.throwE(new BadSpecialForm(name + ": bad syntax", val));
    }

    // ---------------------------------------------------------------------

    public static $<Evaluator, LispVal> macroexpand(Evaluator me, Env ctx, LispVal form) {
        return expand(me, ctx, form, false);
    }

    public static $<Evaluator, LispVal> macroexpand_1(Evaluator me, Env ctx, LispVal form) {
        return expand(me, ctx, form, true);
    }

    private static $<Evaluator, LispVal>
    expand(Evaluator me, Env ctx, LispVal form, boolean single) {
        return with(form).<$<Evaluator, LispVal>>get()
            .when(Datum  (__ -> me.pure(form)))
            .when(Symbol (__ -> me.pure(form)))
            .when(Nil    (() -> me.pure(form)))
            .when(Cons   ((first, rest) -> do_(expand(me, ctx, first, single), tag ->
                                           do_(expandList(me, ctx, tag, rest, single)))))
            .orElseGet(() -> me.throwE(new BadSpecialForm("Unrecognized special form", form)));
    }

    private static $<Evaluator, LispVal>
    expandList(Evaluator me, Env ctx, LispVal tag, LispVal rest, boolean single) {
        if (tag.isSymbol()) {
            switch(((Symbol)tag).name) {
            case "quote":
                return me.pure(new Pair(tag, rest));

            case "quasiquote":
                return with(rest).<$<Evaluator, LispVal>>get()
                  .when(List(datum ->
                    do_(me.evalUnquote(ctx.incrementQL(), datum), qexp ->
                    expand(me, ctx, qexp, single))))
                  .orElseGet(() -> badSyntax(me, "quasiquote", rest));

            default:
                Maybe<LispVal> var = ctx.lookupMacro((Symbol)tag);
                if (var.isPresent() && var.get() instanceof Macro) {
                    Macro mac = (Macro)var.get();
                    return Evaluator.match(ctx, mac.pattern, rest).<$<Evaluator, LispVal>>either(
                        err -> me.throwE(err),
                        eenv -> single ? mac.body.apply(eenv)
                                       : do_(mac.body.apply(eenv), mexp ->
                                         expand(me, ctx, mexp, false)));
                }
            }
        }

        return do_(rest.mapM(me, x -> expand(me, ctx, x, single)), els ->
               do_(me.pure(new Pair(tag, els))));
    }

    public static String dump(LispVal val) {
        return val.toString();
    }

    // ---------------------------------------------------------------------

    @Syntax
    public static $<Evaluator, Proc> require(Evaluator me, Env ctx, LispVal args) {
        if (args.isPair()) {
            Pair p = (Pair)args;
            if (p.head.isSymbol() && p.tail.isNil()) {
                return me.pure(env -> me.except(me.loadLib(env, (Symbol)p.head)));
            }
        }
        return me.throwE(new BadSpecialForm("require: bad syntax", args));
    }

    public static $<Evaluator, LispVal> load(Evaluator me, Env env, String name) {
        try (InputStream is = Files.newInputStream(Paths.get(name))) {
            Reader input = new InputStreamReader(is, StandardCharsets.UTF_8);
            return me.except(me.run(env, me.parse(name, input)));
        } catch (Exception ex) {
            return me.throwE(new LispError(ex));
        }
    }

    public static void import_library(Evaluator me, Env env, String cls) {
        try {
            me.loadPrimitives(env, Class.forName(cls));
        } catch (LispError ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LispError(ex);
        }
    }

    public static LispVal import_method(Evaluator me, String cls, String mth) {
        try {
            Method method = findMethod(Class.forName(cls), mth);
            return me.loadPrimitive(getPrimName(method), method);
        } catch (LispError ex) {
            throw ex;
        } catch (Exception ex) {
            throw new LispError(ex);
        }
    }

    private static Method findMethod(Class<?> c, String name) {
        for (Method method : c.getMethods())
            if (name.equals(method.getName()))
                return method;
        throw new LispError(name + ": method not found");
    }

    private static String getPrimName(Method method) {
        Name nameTag = method.getAnnotation(Name.class);
        return nameTag != null ? nameTag.value() : method.getName().replace('_', '-');
    }
}
