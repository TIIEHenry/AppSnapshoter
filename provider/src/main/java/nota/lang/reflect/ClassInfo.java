package nota.lang.reflect;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;

public class ClassInfo {
    Class<?> mClass;
    HashMap<String, Method> methods;
    HashMap<String, Field> fields;

    public ClassInfo(Class<?> mClass, String className) {
        this.mClass = mClass;
        methods = new HashMap<>();
        fields = new HashMap<>();
    }

    public Method getCachedMethod(String key) {
        return methods.get(key);
    }

    public void addCachedMethod(String key, Method method) {
        methods.put(key, method);
    }

    public Field getCachedField(String key) {
        return fields.get(key);
    }

    public void addCachedField(String key, Field field) {
        fields.put(key, field);
    }
}
