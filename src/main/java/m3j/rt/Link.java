// Copyright (c) 2026 the m3j authors.
// SPDX-License-Identifier: MIT

package m3j.rt;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The link table of a program and the bootstrap methods of the calls
 * between units. The main class of a program registers, before it
 * initializes the units, which module classes export which interfaces;
 * the first execution of a call of an interface procedure then binds the
 * call site to the static method of the exporting module.
 */
public final class Link {
    private Link() {}

    private static final Map<String, List<Class<?>>> exporters = new ConcurrentHashMap<>();

    /** Records that the module class m exports the interface intf. */
    public static void export(String intf, Class<?> m) {
        exporters.computeIfAbsent(intf, k -> new ArrayList<>()).add(m);
    }

    /** Bootstrap of a call of procedure name of interface intf. */
    public static CallSite call(MethodHandles.Lookup lookup, String name, MethodType type, String intf)
            throws ReflectiveOperationException {
        for (Class<?> c : exporters.getOrDefault(intf, List.of())) {
            try {
                MethodHandle h = lookup.findStatic(c, name, type);
                return new ConstantCallSite(h);
            } catch (NoSuchMethodException e) {
                // try the next exporter
            }
        }
        throw new LinkageError("m3j: no module exports the procedure " + intf + "." + name
                               + type.toMethodDescriptorString());
    }

    /**
     * T.m (x, ...): the method name as the class owner (the class of T)
     * implements it, without dispatch on the class of x; the call site
     * type takes x first.
     */
    public static CallSite special(MethodHandles.Lookup lookup, String name, MethodType type, Class<?> owner)
            throws ReflectiveOperationException {
        MethodHandles.Lookup l = MethodHandles.privateLookupIn(owner, lookup);
        MethodHandle mh = l.findSpecial(owner, name, type.dropParameterTypes(0, 1), owner);
        return new ConstantCallSite(mh.asType(type));
    }

    /** Bootstrap of a call of an &lt;*EXTERNAL*&gt; procedure: a method of Externals. */
    public static CallSite external(MethodHandles.Lookup lookup, String name, MethodType type)
            throws ReflectiveOperationException {
        try {
            return new ConstantCallSite(MethodHandles.lookup().findStatic(Externals.class, name, type));
        } catch (NoSuchMethodException e) {
            throw new LinkageError("m3j: unknown external procedure " + name
                                   + type.toMethodDescriptorString());
        }
    }
}
