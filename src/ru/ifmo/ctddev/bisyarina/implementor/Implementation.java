package ru.ifmo.ctddev.bisyarina.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.*;
import java.lang.String;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * The {@link Implementation} class provides interface
 * to get implementation of class and interfaces, generating *.java or *.jar files
 */

public class Implementation implements JarImpler {
    /**
     * {@link java.lang.Class} to implement
     */
    private Class<?> c;
    /**
     * {@link java.lang.String} name of implementation
     */
    private String name;
    /**
     * {@link java.lang.String} of path to group of files of implementation
     */
    private String filePath;

    /**
     * Set of abstract methods from implemented interfaces and extended classes
     */
    private final NavigableSet<Method> methods = new TreeSet<>(new Comparator<Method>() {
        @Override
        public int compare(Method m1, Method m2) {
            return toComparingString(m1).compareTo(toComparingString(m2));
        }

        private String toComparingString(Method m1) {
            return m1.getName() + ImplementationGenerator.toStringParameterList(m1.getParameterTypes());
        }
    });
    /**
     * Array of constructors of class to implement
     */
    private Constructor[] constructors;

    /**
     * Produces <tt>.jar</tt> file implementing class or interface specified by provided <tt>token</tt>.
     * <p>
     * Generated class full name is same as full name of the type token with <tt>Impl</tt> suffix
     * added.
     *
     * @param token   type token to create implementation for.
     * @param jarFile target <tt>.jar</tt> file.
     * @throws ImplerException when implementation cannot be generated.
     */
    @Override
    public void implementJar(Class<?> token, File jarFile) throws ImplerException {
        File dir = new File("./temp");
        implement(token, dir);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler.run(null, null, null, filePath + ".java") < 0) {
            throw new ImplerException("Implementation failed");
        }

        try (FileOutputStream stream = new FileOutputStream(jarFile);
             JarOutputStream out = new JarOutputStream(stream, new Manifest());
             FileInputStream in = new FileInputStream(filePath + ".class")) {

            String jarName = c.getPackage().getName().replace(".", "/") + "/" + name + ".class";
            JarEntry jarAdd = new JarEntry(jarName);
            out.putNextEntry(jarAdd);

            byte[] buffer = new byte[1024];
            int r;
            while ((r = in.read(buffer, 0, buffer.length)) > 0) {
                out.write(buffer, 0, r);
            }
        } catch (IOException e) {
            throw new ImplerException(e.getLocalizedMessage());
        }

    }

    /**
     * Produces code implementing class or interface specified by provided <tt>token</tt>.
     * <p>
     * Generated source code is placed in subdirectory of the specified <tt>root</tt> directory
     * matching package name, name is generated by adding "Impl" suffix.
     * @param token type token to create implementation for.
     * @param root  root directory.
     * @throws info.kgeorgiy.java.advanced.implementor.ImplerException when implementation cannot be
     *                                                                 generated.
     */
    public void implement(Class<?> token, File root) throws ImplerException {
        if (Modifier.isFinal(token.getModifiers())) {
            throw new ImplerException("Final classes can't be implemented");
        }

        c = token;
        this.name = c.getSimpleName() + "Impl";

        initConstructor();
        if (!hasNonPrivateConstructor()) {
            throw new ImplerException("Can't be implemented - doesn't have non private constructor");
        }
        addInterfaceMethods(c);
        initSuperClassMethods(c);

        try {
            filePath = getImplPath(root) + name;
        } catch (IOException e) {
            throw new ImplerException(e.getLocalizedMessage());
        }
        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(filePath + ".java"), "UTF-8")) {
            writer.write(toString());
        } catch (IOException e) {
            throw new ImplerException(e.getLocalizedMessage());
        }

    }

    /**
     * Checks if implemented class has non-private constructors
     * @return boolean representation result of check
     */
    private boolean hasNonPrivateConstructor() {
        if (constructors.length == 0) {
            return true;
        }
        for (Constructor constructor : constructors) {
            if (!Modifier.isPrivate(constructor.getModifiers())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Initialises {@link #constructors}
     * with superclass constructors.
     */
    private void initConstructor() {
        constructors = c.getDeclaredConstructors();
    }

    /**
     * Creates directories specified by full name of implemented class.
     * Return template for files needed for implementation
     * (e.g. *.java, *.class, *.jar)
     * @param root directory where files must be placed
     * @return {@link java.lang.String} representing path of this group of files
     * @throws IOException if creating directories failed
     */
    private String getImplPath(File root) throws IOException {
        String fileSeparator = File.separator;
        String pack = c.getPackage().getName().replace(".", fileSeparator);
        String path = root.getPath().concat(fileSeparator + pack + fileSeparator);

        Files.createDirectories(Paths.get(path));
        return path;
    }

    /**
     *  Initialises {@link #methods} with
     *  all non-static, non-private and non-default methods of given class and all its interfaces
     * @param cl {@link java.lang.Class} to initialise methods with
     */
    private void addInterfaceMethods(Class<?> cl) {
        Class<?>[] interfaces = cl.getInterfaces();
        for (Class<?> anInterface : interfaces) {
            addInterfaceMethods(anInterface);
        }
        Method[] m = cl.getDeclaredMethods();
        for (Method aM : m) {
            int modifiers = aM.getModifiers();
            if (!Modifier.isPrivate(modifiers)) {
                if (!aM.isDefault() && !Modifier.isStatic(aM.getModifiers())) {
                    methods.add(aM);
                }
            }
        }
    }

    /**
     * Initialises {@link #methods} with
     * all non-static and non-private methods of given class and all its superclasses.
     * Removes methods that are implemented.
     *
     * @param cl {@link java.lang.Class} to initialise methods with
     */
    private void initSuperClassMethods(Class<?> cl) {
        if (cl == null) {
            return;
        }
        initSuperClassMethods(cl.getSuperclass());
        Method[] m = cl.getDeclaredMethods();
        for (Method aM : m) {
            int modifiers = aM.getModifiers();
            if (Modifier.isAbstract(modifiers)) {
                methods.add(aM);
            } else {
                if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                    methods.remove(aM);
                }
            }
        }
    }

    /**
     * Returns {@link java.lang.String} representation of default implementation
     * @return {@link java.lang.String} of implementation
     */
    public String toString() {
        String file = "";

        file += ImplementationGenerator.toStringPackage(c.getPackage());

        file += ImplementationGenerator.toStringClass(name, c) + " {" + ImplementationGenerator.lineSeparator;

        for (Constructor constructor : constructors) {
            file += ImplementationGenerator.toStringConstructor(name, constructor) + ImplementationGenerator.lineSeparator;
        }
        for (Method method : methods) {
            file += ImplementationGenerator.toStringMethod(method) + ImplementationGenerator.lineSeparator;
        }

        file += "}";
        return file;
    }

}


