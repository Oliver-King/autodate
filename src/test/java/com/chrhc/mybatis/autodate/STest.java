/*
 * @Copyright: 2005-2017 www.2345.com. All rights reserved.
 */
package com.chrhc.mybatis.autodate;

import org.junit.Test;

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
}
