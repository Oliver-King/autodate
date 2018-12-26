
package com.chrhc.mybatis.autodate.util;

import java.util.Date;

/**
 * @author 605162215@qq.com
 * <p>
 * 2016年7月21日 下午12:59:33
 */
public class TypeUtil {

    private TypeUtil() {
    }

    /**
     * isSimpleType
     *
     * @param clazz clazz
     * @return r
     */
    public static boolean isSimpleType(Class<?> clazz) {

        boolean b = (clazz == String.class);
        if (!b) {
            b = (clazz == Date.class);
        }
        if (!b) {
            b = (clazz == int.class || clazz == Integer.class);
        }
        if (!b) {
            b = (clazz == long.class || clazz == Long.class);
        }
        if (!b) {
            b = (clazz == boolean.class || clazz == Boolean.class);
        }
        if (!b) {
            b = (clazz == byte.class || clazz == Byte.class);
        }
        if (!b) {
            b = (clazz == char.class || clazz == Character.class);
        }
        if (!b) {
            b = (clazz == short.class || clazz == Short.class);
        }
        if (!b) {
            b = (clazz == float.class || clazz == Float.class);
        }
        if (!b) {
            b = (clazz == double.class || clazz == Double.class);
        }

        return b;
    }
}
