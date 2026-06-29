package prefs;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class TestJavaTypes {

    @Test
    public void testJavaTypes() {
        showWrap(Integer.class);
        showWrap(int.class);
    }

    void showWrap(Class<?> c) {
        var wrapc = wrapPrimitives(c);
        System.out.println(c +" -> " + wrapc);
    }

    Class<?> wrapPrimitives(Class<?> c) {
        if (c == boolean.class)
            return Boolean.class;
        else if (c == int.class)
            return Integer.class;
        else if (c == float.class)
            return Float.class;
        else if (c == double.class)
            return Double.class;
        else if (c == short.class)
            return Short.class;
        else if (c == long.class)
            return Long.class;
        else if (c == byte.class)
            return Byte.class;
        else
            return c;
    }

}
