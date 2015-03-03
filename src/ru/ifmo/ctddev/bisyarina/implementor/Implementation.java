package ru.ifmo.ctddev.bisyarina.implementor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by mariashka on 3/1/15.
 */
public class Implementation {
    private Class c;
    private String name;

    private List<Method> methods = new LinkedList<>();
    private List<String> imports = new ArrayList<>();

    public void implement(Class<?> token, File root) throws ImplementException {
        if (!token.isInterface() && !Modifier.isAbstract(token.getModifiers())) {
            throw new ImplementException("Class is not an interface or an abstract class");
        }
        c = token;
        this.name = c.getSimpleName() + "Impl";
        initImports();
        initInterfaceMethods();
        initSuperClassMethods(c);

        try (OutputStreamWriter writer =
                     new OutputStreamWriter(new FileOutputStream(getImplPath(root) + getName() + ".java"), "UTF-8")) {
            writer.write(toString());
        } catch (IOException e) {
            throw new ImplementException(e);
        }
    }

    private String getImplPath(File root) throws IOException {
        String pack = c.getPackage().getName().replace(".", "/");

        String path = root.getPath().concat("/" + pack + "/");
        Files.createDirectories(Paths.get(path));
        return path;
    }

    private void initInterfaceMethods() {
        Class[] interfaces = c.getInterfaces();
        for (Class anInterface : interfaces) {
            addInterfaceMethods(anInterface);
        }
    }

    private void addInterfaceMethods(Class cl) {
        Class[] interfaces = cl.getInterfaces();
        for (Class anInterface : interfaces) {
            addInterfaceMethods(anInterface);
        }
        Method[] m = cl.getDeclaredMethods();
        for (Method aM : m) {
            int modifiers = aM.getModifiers();
            if (Modifier.isProtected(modifiers) || Modifier.isPublic(modifiers)) {
                addMethod(aM);
            }
        }
    }

    private void initImports() {
        List<String> currImports = new ArrayList<>();
        currImports.add(c.getPackage().getName() + ".*");
        for (Method method : methods) {
            Class[] parameters = method.getParameterTypes();
            if (!method.getReturnType().isPrimitive()) {
                if (!method.getReturnType().isArray()) {
                    currImports.add(method.getReturnType().getCanonicalName());
                } else {
                    currImports.add(method.getReturnType().getComponentType().getCanonicalName());
                }
            }

            for (Class parameter : parameters) {
                if (!parameter.isPrimitive()) {
                    if (!parameter.isArray()) {
                        currImports.add(parameter.getCanonicalName());
                    } else {
                        currImports.add(parameter.getComponentType().getCanonicalName());
                    }
                }
            }
        }

        Collections.sort(currImports);
        for (String currImport : currImports) {
            if (imports.size() > 0) {
                if (!imports.get(imports.size() - 1).equals(currImport)) {
                    imports.add(currImport);
                }
            } else {
                imports.add(currImport);
            }
        }
    }

    private void initSuperClassMethods(Class c) {
        if (c == null) {
            return;
        }
        initSuperClassMethods(c.getSuperclass());
        Method[] m = c.getDeclaredMethods();
        for (Method aM : m) {
            int modifiers = aM.getModifiers();
            if (Modifier.isAbstract(modifiers)) {
                addMethod(aM);
            } else {
                if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
                    removeMethod(aM);
                }
            }
        }
    }

    private void removeMethod(Method m) {
        String currName = m.getName();
        Class[] currParameters = m.getParameterTypes();
        for (int j = 0; j < methods.size(); j++) {
            if (methods.get(j).getName().equals(currName) && equalParameters(currParameters, methods.get(j).getParameterTypes())) {
                methods.remove(j);
            }
        }
    }

    private void addMethod(Method m) {
        String currName = m.getName();
        Class[] currParameters = m.getParameterTypes();
        for (Method method : methods) {
            if (method.getName().equals(currName) && equalParameters(currParameters, method.getParameterTypes())) {
                return;
            }
        }
        methods.add(m);
    }

    private boolean equalParameters(Class<?>[] l, Class<?>[] r) {
        if (l.length != r.length) {
            return false;
        }
        for (int i = 0; i < l.length; i++) {
            if (!l[i].equals(r[i])) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        String file = "";
        file += ImplementationGenerator.toStringPackage(c.getPackage());
        for (String anImport : imports) {
            file += ImplementationGenerator.toStringImport(anImport) + "\n\n";
        }
        file += ImplementationGenerator.toStringClass(c, name) + " {\n\n";
        for (Method method : methods) {
            file += ImplementationGenerator.toStringMethod(method) + "\n\n";
        }
        file += "}";
        return file;
    }

    public String getName() {
        return name;
    }
}
