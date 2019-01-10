/*
 * @Copyright: 2005-2017 www.2345.com. All rights reserved.
 */
package com.chrhc.mybatis.autodate;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author chenguijin
 * @version STest, v0.1 2018/12/26 17:14
 */
public class STest {

    @Test
    public void sTester() {
        String SP = ".";
        String s = "dto.updateAt";
        boolean contains = s.contains(SP);
        if (contains) {
            String[] split = s.split(SP);
            System.out.println("==>" + split);

            split = s.split("\\.");
            System.out.println("==>" + split);
        }
    }

    @Test
    public void regTest() {
        String reg = "^\\w+\\[\\w+]$";
        String pKey = "params[0]";
        System.out.println(Pattern.matches(reg, pKey));

        Pattern pattern = Pattern.compile(reg);
        Matcher matcher = pattern.matcher(pKey);
        if (matcher.find()) {
            int i = matcher.groupCount();
            System.out.println(i);
            for (int j = 0; j <= i; j++) {
                String group = matcher.group(j);
                System.out.println(group);
            }
        }

        pKey = "item[idx]";
        System.out.println(Pattern.matches(reg, pKey));
    }
}
